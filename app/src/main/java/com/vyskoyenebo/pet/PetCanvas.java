package com.vyskoyenebo.pet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** One decoder thread and a bounded cache; no all-frame bitmap allocation. */
final class PetCanvas extends View {
    interface Listener {
        void ready(FrameSource source, FrameSource.Clip clip);
        void error(String message);
    }
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final HandlerThread decoder = new HandlerThread("PetFrames");
    private final Handler work;
    private final AnimationClock clock = new AnimationClock();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private final LinkedHashMap<Integer, Bitmap> cache = new LinkedHashMap<>(24, 0.75f, true);
    private final Listener listener;
    private volatile int formGeneration, frameGeneration;
    private volatile boolean released;
    private int cacheGeneration = -1, cachedBytes;
    private int renderLongSide = 512, frontIndex = -1;
    private boolean playing, pending, loading;
    private String activeForm = "", wantedMotion = "";
    private FrameSource source;
    private FrameSource.Clip clip;
    private Bitmap front;

    PetCanvas(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setContentDescription("桌宠");
        decoder.start();
        work = new Handler(decoder.getLooper());
    }
    void selection(String form, String motion) {
        wantedMotion = motion;
        if (form.equals(activeForm) && (source != null || loading)) {
            if (source != null) selectMotion(motion);
            return;
        }
        activeForm = form;
        source = null; clip = null; front = null; pending = false; loading = true;
        frontIndex = -1;
        int ticket = ++formGeneration;
        frameGeneration++;
        clock.reset(SystemClock.uptimeMillis());
        ui.removeCallbacks(tick);
        invalidate();
        work.post(() -> {
            try {
                FrameSource opened = new FrameSource(getContext().getAssets(), form);
                ui.post(() -> {
                    if (released || ticket != formGeneration) return;
                    loading = false; source = opened;
                    selectMotion(wantedMotion);
                });
            } catch (Exception error) { fallback(ticket, "无法加载形态：" + error.getMessage()); }
        });
    }
    private void selectMotion(String requested) {
        FrameSource.Clip next = source.find(requested);
        if (next == null) next = source.find(source.defaultClip);
        if (clip == next) { listener.ready(source, clip); return; }
        clip = next;
        resetFrames();
        clock.reset(SystemClock.uptimeMillis());
        listener.ready(source, clip);
        requestFrame();
        schedule();
    }
    void renderSize(int pixels) {
        int next = Math.max(1, Math.min(1024, pixels));
        if (next == renderLongSide) return;
        renderLongSide = next;
        resetFrames();
        if (clip != null) requestFrame();
    }
    private void resetFrames() { frameGeneration++; frontIndex = -1; pending = false; }
    void playing(boolean enabled) {
        if (released || enabled == playing) return;
        playing = enabled;
        long now = SystemClock.uptimeMillis();
        if (enabled) clock.resume(now); else clock.pause(now);
        schedule();
    }
    private void schedule() {
        ui.removeCallbacks(tick);
        if (playing && clip != null && clip.frames.length > 1 && !released)
            ui.postDelayed(tick, Math.max(33, 1000 / clip.fps));
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (released || !playing) return;
            requestFrame();
            schedule();
        }
    };
    private void requestFrame() {
        if (released || source == null || clip == null || pending) return;
        int index = AnimationClock.frame(clock.elapsed(SystemClock.uptimeMillis()), clip.fps, clip.frames.length);
        if (index == frontIndex && front != null) return;
        pending = true;
        int ticket = frameGeneration, formTicket = formGeneration, size = renderLongSide;
        FrameSource opened = source;
        FrameSource.Clip motion = clip;
        work.post(() -> {
            if (released || ticket != frameGeneration) return;
            try {
                if (cacheGeneration != ticket) { cache.clear(); cachedBytes = 0; cacheGeneration = ticket; }
                Bitmap bitmap = cache.get(index);
                if (bitmap == null) {
                    bitmap = opened.decode(motion, index, size);
                    cache.put(index, bitmap); cachedBytes += bitmap.getByteCount();
                    while (cache.size() > 1 && (cache.size() > 24 || cachedBytes > 4 * 1024 * 1024)) {
                        Map.Entry<Integer, Bitmap> oldest = cache.entrySet().iterator().next();
                        cachedBytes -= oldest.getValue().getByteCount();
                        cache.remove(oldest.getKey());
                    }
                }
                Bitmap result = bitmap;
                ui.post(() -> {
                    if (released || ticket != frameGeneration) return;
                    pending = false; frontIndex = index; front = result; invalidate();
                });
            } catch (Exception error) { fallback(formTicket, "动画帧读取失败：" + error.getMessage()); }
        });
    }
    private void fallback(int ticket, String message) {
        ui.post(() -> {
            if (released || ticket != formGeneration) return;
            clip = null; source = null; loading = false; pending = false;
            frameGeneration++;
            ui.removeCallbacks(tick);
            listener.error(message + "，已显示静态备用图。");
            work.post(() -> {
                cache.clear(); cachedBytes = 0; cacheGeneration = -1;
                try (InputStream in = getContext().getAssets().open("pet.png")) {
                    Bitmap image = BitmapFactory.decodeStream(in);
                    ui.post(() -> { if (!released && ticket == formGeneration) { front = image; invalidate(); } });
                } catch (Exception ignored) { /* The visible error remains on the control page. */ }
            });
        });
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (front == null) return;
        float scale = Math.min((float)getWidth() / front.getWidth(), (float)getHeight() / front.getHeight());
        float w = front.getWidth() * scale, h = front.getHeight() * scale;
        destination.set((getWidth()-w)/2, (getHeight()-h)/2, (getWidth()+w)/2, (getHeight()+h)/2);
        canvas.drawBitmap(front, null, destination, paint);
    }
    @Override public boolean performClick() { return super.performClick(); }
    void close() {
        released = true; playing = false; frameGeneration++; formGeneration++;
        ui.removeCallbacksAndMessages(null);
        front = null; source = null; clip = null;
        work.post(() -> { cache.clear(); cachedBytes = 0; });
        decoder.quitSafely();
    }
}
