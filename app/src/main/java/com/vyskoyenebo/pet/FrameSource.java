package com.vyskoyenebo.pet;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class FrameSource {
    static final class Clip {
        final String key, title;
        final int width, height, fps;
        final String[] frames;
        Clip(String key, JSONObject data) throws Exception {
            this.key = key;
            title = data.getString("title");
            JSONArray size = data.getJSONArray("size");
            width = size.getInt(0); height = size.getInt(1); fps = data.getInt("fps");
            JSONArray paths = data.getJSONArray("frames");
            if (width < 1 || height < 1 || width > 1024 || height > 1024
                    || fps < 1 || fps > 30 || paths.length() < 1 || paths.length() > 240)
                throw new IOException("动画尺寸或帧数无效");
            frames = new String[paths.length()];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = paths.getString(i);
                if (frames[i].contains("..") || frames[i].startsWith("/")
                        || frames[i].contains("\\") || frames[i].contains(":"))
                    throw new IOException("动画路径无效");
            }
        }
    }
    final List<Clip> clips = new ArrayList<>();
    final String defaultClip;
    private final AssetManager assets;
    private final String directory;

    FrameSource(AssetManager assets, String id) throws Exception {
        this.assets = assets;
        directory = Forms.directory(id);
        JSONObject manifest;
        try (InputStream in = assets.open(directory + "manifest.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > 65536) throw new IOException("动画说明文件过大");
            }
            manifest = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
        if (!"desktop-pet-frames-v1".equals(manifest.getString("format")))
            throw new IOException("不支持的动画格式");
        defaultClip = manifest.getString("default_clip");
        JSONObject items = manifest.getJSONObject("clips");
        if (items.length() > 32) throw new IOException("动作数量过多");
        java.util.Iterator<String> keys = items.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            clips.add(new Clip(key, items.getJSONObject(key)));
        }
        if (find(defaultClip) == null) throw new IOException("缺少默认动作");
    }
    Clip find(String key) { for (Clip clip : clips) if (clip.key.equals(key)) return clip; return null; }
    Bitmap decode(Clip clip, int index, int target) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        options.inSampleSize = 1;
        while (Math.max(clip.width, clip.height) / (options.inSampleSize * 2) >= target)
            options.inSampleSize *= 2;
        Bitmap decoded;
        try (InputStream in = assets.open(directory + clip.frames[index])) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null || !decoded.hasAlpha()) throw new IOException("动画帧损坏或缺少透明通道");
        double ratio = (double)target / Math.max(clip.width, clip.height);
        int width = Math.max(1, (int)Math.round(clip.width * ratio));
        int height = Math.max(1, (int)Math.round(clip.height * ratio));
        if (decoded.getWidth() == width && decoded.getHeight() == height) return decoded;
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }
}
