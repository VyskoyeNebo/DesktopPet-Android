package com.vyskoyenebo.pet;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.hardware.input.InputManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;

public final class PetService extends Service {
    static final String START = "com.vyskoyenebo.pet.START";
    static final String UPDATE = "com.vyskoyenebo.pet.UPDATE";
    static final String TOGGLE = "com.vyskoyenebo.pet.TOGGLE";
    static final String RECOVER = "com.vyskoyenebo.pet.RECOVER";
    static final String STOP = "com.vyskoyenebo.pet.STOP";
    private static final String CHANNEL = "pet_controls";
    private static final int NOTIFICATION = 192;
    static volatile boolean running, visible;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private WindowManager.LayoutParams params;
    private PetCanvas pet;
    private PetPrefs prefs;
    private boolean attached, dragging, screenOn = true, receiverRegistered;
    private int areaW, areaH, sourceW = 475, sourceH = 512;
    private int startX, startY;
    private float downX, downY;
    private GestureDetector gestures;
    private Context overlayContext;

    @Override public void onCreate() {
        super.onCreate();
        prefs = new PetPrefs(this);
        prefs.through(false);
        overlayContext = this;
        if (Build.VERSION.SDK_INT >= 30) {
            Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) overlayContext = createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        manager = overlayContext.getSystemService(WindowManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "桌宠控制", NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION, notification());
        running = true;
        PowerManager power = getSystemService(PowerManager.class);
        screenOn = (power == null || power.isInteractive()) && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
        receiverRegistered = true;
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? STOP : intent.getAction();
        if (STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (!Settings.canDrawOverlays(this)) {
            toast("请先在应用中开启悬浮窗权限");
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            ensureView();
            if (TOGGLE.equals(action)) setVisible(!visible);
            else if (RECOVER.equals(action)) {
                prefs.through(false); prefs.position(0.75f, 0.7f);
                setVisible(true); applySettings();
                toast("已找回桌宠，可以正常触控");
            } else {
                if (START.equals(action)) setVisible(true);
                applySettings();
            }
            notifyState();
        } catch (RuntimeException error) {
            toast("无法显示桌宠：" + error.getMessage());
            stopSelf();
        }
        // Relaunch explicitly from the control page, rather than a background restart loop.
        return START_NOT_STICKY;
    }
    private void ensureView() {
        if (pet != null) return;
        params = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= 28)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        pet = new PetCanvas(overlayContext, new PetCanvas.Listener() {
            @Override public void ready(FrameSource source, FrameSource.Clip clip) {
                sourceW = clip.width; sourceH = clip.height;
                updateGeometry();
            }
            @Override public void error(String message) { toast(message); }
        });
        pet.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent event) {
                if (!dragging) {
                    pet.performClick();
                    try { startActivity(new Intent(PetService.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)); }
                    catch (RuntimeException error) { toast("请从桌面图标打开桌宠控制"); }
                }
                return true;
            }
            @Override public void onLongPress(MotionEvent event) {
                if (dragging) return;
                prefs.fixed(!prefs.fixed());
                toast(prefs.fixed() ? "位置已固定，轻点可打开控制" : "已取消固定，可以拖动");
                notifyState();
            }
        });
        pet.setOnTouchListener(this::touch);
        updateGeometry();
    }
    private boolean touch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX(); downY = event.getRawY();
            startX = params.x; startY = params.y; dragging = false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !prefs.fixed()) {
            float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
            if (Math.hypot(dx, dy) > ViewConfiguration.get(this).getScaledTouchSlop()) dragging = true;
            if (dragging) {
                pet.playing(false);
                params.x = PetGeometry.clamp(startX + Math.round(dx), params.width, areaW);
                params.y = PetGeometry.clamp(startY + Math.round(dy), params.height, areaH);
                updateWindow();
            }
        }
        gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (dragging) prefs.position(PetGeometry.fraction(params.x, params.width, areaW),
                PetGeometry.fraction(params.y, params.height, areaH));
            dragging = false;
            updatePlayback();
        }
        return true;
    }
    private void applySettings() {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.alpha = 1f;
        if (prefs.through()) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (Build.VERSION.SDK_INT >= 31) {
                InputManager input = getSystemService(InputManager.class);
                params.alpha = Math.min(0.75f, input.getMaximumObscuringOpacityForTouch());
            }
        }
        pet.selection(prefs.form(), prefs.motion(prefs.form()));
        updateGeometry();
        updatePlayback();
    }
    private void updateGeometry() {
        if (pet == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowMetrics metrics = manager.getCurrentWindowMetrics();
            Insets safe = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            areaW = Math.max(1, metrics.getBounds().width() - safe.left - safe.right);
            areaH = Math.max(1, metrics.getBounds().height() - safe.top - safe.bottom);
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            manager.getDefaultDisplay().getMetrics(metrics);
            areaW = Math.max(1, metrics.widthPixels);
            int statusId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            int status = statusId == 0 ? 0 : getResources().getDimensionPixelSize(statusId);
            areaH = Math.max(1, metrics.heightPixels - status);
        }
        int pixels = Math.round(prefs.size(prefs.form()) * getResources().getDisplayMetrics().density);
        int[] fitted = PetGeometry.fit(sourceW, sourceH, pixels, areaW, areaH);
        params.width = fitted[0]; params.height = fitted[1];
        params.x = PetGeometry.position(prefs.x(), params.width, areaW);
        params.y = PetGeometry.position(prefs.y(), params.height, areaH);
        pet.renderSize(Math.max(params.width, params.height));
        updateWindow();
    }
    private void updateWindow() {
        if (!attached) return;
        try { manager.updateViewLayout(pet, params); }
        catch (RuntimeException error) { toast("悬浮窗权限或屏幕状态已变化，请重新启动桌宠"); stopSelf(); }
    }
    private void setVisible(boolean show) {
        visible = show;
        if (show && !attached) { manager.addView(pet, params); attached = true; }
        else if (!show && attached) { manager.removeView(pet); attached = false; }
        updatePlayback();
    }
    private void updatePlayback() { if (pet != null) pet.playing(visible && screenOn && !dragging); }
    private PendingIntent action(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, PetService.class).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("桌宠 · " + Forms.TITLES[Forms.index(prefs.form())])
            .setContentText(prefs.through() ? "点击穿透已开启 · 可随时恢复触控" : "轻点打开控制 · 拖动调整位置")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null, visible ? "隐藏" : "显示", action(TOGGLE, 1)).build())
            .addAction(new Notification.Action.Builder(null, "恢复触控", action(RECOVER, 2)).build())
            .addAction(new Notification.Action.Builder(null, "关闭", action(STOP, 3)).build())
            .build();
    }
    private void notifyState() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            PowerManager power = getSystemService(PowerManager.class);
            screenOn = (power == null || power.isInteractive()) && !getSystemService(KeyguardManager.class).isKeyguardLocked();
            updatePlayback();
        }
    };
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        handler.postDelayed(this::updateGeometry, 150);
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        running = false; visible = false;
        if (pet != null) {
            pet.playing(false);
            if (attached) { try { manager.removeViewImmediate(pet); } catch (RuntimeException ignored) { } }
            attached = false;
            pet.close();
        }
        if (receiverRegistered) unregisterReceiver(screenReceiver);
        prefs.through(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
