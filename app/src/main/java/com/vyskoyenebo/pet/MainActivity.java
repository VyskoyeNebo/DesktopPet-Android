package com.vyskoyenebo.pet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int INK = 0xFF193C36, MUTED = 0xFF657C75, ACCENT = 0xFF356F65;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PetPrefs prefs;
    private PetCanvas preview;
    private TextView status, permissionStatus, previewTitle, errorText;
    private Spinner formPicker, motionPicker, sizePicker;
    private Switch fixedSwitch, throughSwitch;
    private Button startButton, stopButton;
    private final List<FrameSource.Clip> motions = new ArrayList<>();
    private boolean binding, pendingStart;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new PetPrefs(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF4F7F6);
        LinearLayout page = column();
        page.setPadding(dp(22), dp(22), dp(22), dp(28));
        FrameLayout centered = new FrameLayout(this);
        scroll.addView(centered);
        centered.addView(page, new FrameLayout.LayoutParams(
            Math.min(getResources().getDisplayMetrics().widthPixels, dp(600)), -2,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = safe.top; bottom = safe.bottom; left = safe.left; right = safe.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            page.setPadding(dp(22) + left, dp(22) + top, dp(22) + right, dp(28) + bottom);
            return insets;
        });
        TextView eyebrow = text("DESKTOP PET  /  ANDROID", 11, ACCENT, true);
        eyebrow.setLetterSpacing(0.12f); page.addView(eyebrow);
        page.addView(text("口袋桌宠", 30, INK, true), space(0, 8));
        page.addView(text("五个形态，待在屏幕里的一点陪伴。", 14, MUTED, false), space(0, 5));
        status = text("尚未启动", 12, ACCENT, true);
        page.addView(status, space(0, 12));

        FrameLayout stage = new FrameLayout(this);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFE0EEEA, 0xFFF2EDF5});
        gradient.setCornerRadius(dp(24)); stage.setBackground(gradient);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(-1, dp(258));
        stageParams.topMargin = dp(18); page.addView(stage, stageParams);
        previewTitle = text("Q版蕾米 · 192 dp", 12, ACCENT, true);
        FrameLayout.LayoutParams previewLabel = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        previewLabel.setMargins(dp(18), dp(16), dp(18), 0); stage.addView(previewTitle, previewLabel);
        preview = new PetCanvas(this, new PetCanvas.Listener() {
            @Override public void ready(FrameSource source, FrameSource.Clip clip) {
                motions.clear(); motions.addAll(source.clips);
                String[] titles = new String[motions.size()]; int selected = 0;
                for (int i = 0; i < motions.size(); i++) {
                    titles[i] = motions.get(i).title;
                    if (motions.get(i).key.equals(clip.key)) selected = i;
                }
                prefs.motion(prefs.form(), clip.key);
                binding = true;
                motionPicker.setAdapter(adapter(titles));
                motionPicker.setSelection(selected); motionPicker.setEnabled(true);
                binding = false; errorText.setVisibility(View.GONE);
            }
            @Override public void error(String message) {
                errorText.setText(message); errorText.setVisibility(View.VISIBLE);
                motionPicker.setEnabled(false);
            }
        });
        preview.renderSize(dp(208));
        FrameLayout.LayoutParams canvasParams = new FrameLayout.LayoutParams(-1, dp(208), Gravity.BOTTOM);
        canvasParams.setMargins(dp(18), dp(40), dp(18), dp(8)); stage.addView(preview, canvasParams);

        LinearLayout permissionCard = card(page, "先允许它出现在屏幕上");
        permissionStatus = text("需要开启悬浮窗权限", 13, MUTED, false);
        permissionCard.addView(permissionStatus, space(0, 8));
        Button grant = button("悬浮窗授权", false);
        permissionCard.addView(grant, space(48, 12));
        grant.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))); }
            catch (RuntimeException error) { Toast.makeText(this, "请在系统设置中允许本应用显示悬浮窗", Toast.LENGTH_LONG).show(); }
        });

        LinearLayout choices = card(page, "选择你的桌宠");
        choices.addView(text("形态", 12, MUTED, true), space(0, 12));
        formPicker = new Spinner(this); formPicker.setAdapter(adapter(Forms.TITLES));
        choices.addView(formPicker, space(48, 3));
        choices.addView(text("动作", 12, MUTED, true), space(0, 10));
        motionPicker = new Spinner(this); motionPicker.setAdapter(adapter(new String[]{"正在载入动作…"}));
        choices.addView(motionPicker, space(48, 3));
        choices.addView(text("大小 · 显示长边", 12, MUTED, true), space(0, 10));
        sizePicker = new Spinner(this); sizePicker.setAdapter(adapter(Forms.SIZE_TITLES));
        choices.addView(sizePicker, space(48, 3));
        errorText = text("", 12, 0xFF9C463E, false); errorText.setVisibility(View.GONE);
        choices.addView(errorText, space(0, 8));

        LinearLayout options = card(page, "让它按你的方式待着");
        fixedSwitch = toggle("固定位置"); options.addView(fixedSwitch, space(52, 8));
        throughSwitch = toggle("点击穿透"); options.addView(throughSwitch, space(52, 0));
        options.addView(text("穿透后，可从通知栏或重新打开本应用恢复交互。Android 12 及以上的穿透模式会略微变淡。", 12, MUTED, false), space(0, 6));
        options.addView(text("轻点桌宠打开控制；长按可固定或取消固定。隐藏、拖动和熄屏时暂停动画，不添加整体上下浮动。", 12, MUTED, false), space(0, 10));
        Button notifications = button("通知栏控制设置", false);
        options.addView(notifications, space(46, 12));
        notifications.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));

        startButton = button("启动桌宠", true); page.addView(startButton, space(54, 22));
        stopButton = button("关闭桌宠", false); page.addView(stopButton, space(48, 10));
        startButton.setOnClickListener(v -> startPet());
        stopButton.setOnClickListener(v -> { stopService(new Intent(this, PetService.class)); refreshStatus(); });
        page.addView(text("纯离线 · 高清透明素材 · 按需播放", 11, MUTED, false), space(0, 20));
        formPicker.setOnItemSelectedListener(new Selected() {
            @Override public void selected(int pos) {
                if (binding || Forms.IDS[pos].equals(prefs.form())) return;
                prefs.form(Forms.IDS[pos]); syncSelection(); settingsChanged();
            }
        });
        motionPicker.setOnItemSelectedListener(new Selected() {
            @Override public void selected(int pos) {
                if (binding || pos >= motions.size()) return;
                String next = motions.get(pos).key;
                if (next.equals(prefs.motion(prefs.form()))) return;
                prefs.motion(prefs.form(), next);
                preview.selection(prefs.form(), next); settingsChanged();
            }
        });
        sizePicker.setOnItemSelectedListener(new Selected() {
            @Override public void selected(int pos) {
                if (binding || Forms.SIZES[pos] == prefs.size(prefs.form())) return;
                prefs.size(prefs.form(), Forms.SIZES[pos]);
                updatePreviewTitle(); settingsChanged();
            }
        });
        fixedSwitch.setOnCheckedChangeListener((button, value) -> {
            if (!binding) { prefs.fixed(value); settingsChanged(); }
        });
        throughSwitch.setOnCheckedChangeListener((button, value) -> {
            if (!binding) { prefs.through(value); settingsChanged(); }
        });
    }
    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先点击“悬浮窗授权”并开启权限", Toast.LENGTH_LONG).show(); return;
        }
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("notifications_asked", false);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !asked) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notifications_asked", true).apply();
            pendingStart = true; requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33); return;
        }
        beginService();
    }
    private void beginService() {
        try {
            String action = PetService.running ? PetService.RECOVER : PetService.START;
            startForegroundService(new Intent(this, PetService.class).setAction(action));
            handler.postDelayed(this::refreshStatus, 350);
        } catch (RuntimeException error) { Toast.makeText(this, "启动失败：" + error.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 33 && pendingStart) { pendingStart = false; beginService(); }
    }
    private void settingsChanged() {
        if (PetService.running) {
            try { startService(new Intent(this, PetService.class).setAction(PetService.UPDATE)); }
            catch (RuntimeException error) { Toast.makeText(this, "请重新启动桌宠应用设置", Toast.LENGTH_SHORT).show(); }
        }
    }
    private void syncSelection() {
        binding = true;
        formPicker.setSelection(Forms.index(prefs.form()));
        int selected = 2;
        for (int i = 0; i < Forms.SIZES.length; i++) if (Forms.SIZES[i] == prefs.size(prefs.form())) selected = i;
        sizePicker.setSelection(selected);
        fixedSwitch.setChecked(prefs.fixed()); throughSwitch.setChecked(prefs.through());
        binding = false;
        motions.clear(); motionPicker.setEnabled(false);
        preview.selection(prefs.form(), prefs.motion(prefs.form()));
        updatePreviewTitle();
    }
    private void updatePreviewTitle() { setTextIfChanged(previewTitle, Forms.TITLES[Forms.index(prefs.form())] + " · " + prefs.size(prefs.form()) + " dp"); }
    private void refreshStatus() {
        boolean running = PetService.running;
        setTextIfChanged(status, running ? (PetService.visible ? "● 桌宠正在陪你" : "● 已隐藏 · 可从通知栏显示") : "○ 尚未启动");
        setTextIfChanged(startButton, running ? "找回桌宠并恢复交互" : "启动桌宠");
        stopButton.setEnabled(running); throughSwitch.setEnabled(running);
        setTextIfChanged(permissionStatus, Settings.canDrawOverlays(this) ? "悬浮窗权限已开启，可以启动桌宠。" : "开启后，桌宠可以显示在桌面和其他应用上。由你手动启动和关闭。");
        preview.playing(!running);
        binding = true;
        fixedSwitch.setChecked(prefs.fixed()); throughSwitch.setChecked(prefs.through());
        binding = false;
    }
    private final Runnable statusTick = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000); }
    };
    @Override public void onResume() {
        super.onResume(); syncSelection(); preview.playing(true);
        handler.post(statusTick);
    }
    @Override public void onPause() {
        handler.removeCallbacksAndMessages(null); preview.playing(false); super.onPause();
    }
    @Override public void onDestroy() { preview.close(); super.onDestroy(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void setTextIfChanged(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout.LayoutParams space(int height, int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height == 0 ? -2 : dp(height));
        p.topMargin = dp(margin); return p;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3), 1); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private LinearLayout card(LinearLayout page, String title) {
        LinearLayout card = column(); card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable shape = rounded(Color.WHITE, 20); shape.setStroke(dp(1), 0xFFE1E9E5); card.setBackground(shape);
        card.addView(text(title, 16, INK, true)); page.addView(card, space(0, 16)); return card;
    }
    private Button button(String label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setTextSize(14); button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        button.setBackground(rounded(primary ? ACCENT : 0xFFE7EFEC, 14));
        button.setStateListAnimator(null); return button;
    }
    private Switch toggle(String label) {
        Switch control = new Switch(this); control.setText(label); control.setTextSize(14); control.setTextColor(INK);
        control.setThumbTintList(ColorStateList.valueOf(ACCENT)); control.setShowText(false); return control;
    }
    private ArrayAdapter<String> adapter(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); return adapter;
    }
    private abstract static class Selected implements android.widget.AdapterView.OnItemSelectedListener {
        abstract void selected(int position);
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) { selected(pos); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
