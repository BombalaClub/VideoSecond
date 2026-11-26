package cn.videosecond.gate;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int PAPER = 0xfffbf7ed;
    private static final int INK = 0xff153f38;
    private static final int MUTED = 0xff50655c;
    private static final int RULE = 0xffd9d7c7;
    private static final int MUSTARD = 0xfff4bd65;
    private static final String PREVIEW_KEY = "preview";
    private static final String SUCCESS_KEY = "previewSuccess";
    private static final String ATTEMPT_KEY = "previewAttempt";

    private boolean preview;
    private boolean previewSuccess;
    private boolean receiverRegistered;
    private QuizView previewQuiz;
    private ScrollView page;
    private int lastStatus = -1;
    private boolean lastInstalled;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (GateService.STATUS_CHANGED.equals(intent.getAction())) refreshIfNeeded();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (state != null) previewSuccess = state.getBoolean(SUCCESS_KEY);
        if (state != null && state.getBoolean(PREVIEW_KEY)) {
            startPreview(state.getBundle(ATTEMPT_KEY));
        } else {
            refreshIfNeeded();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(GateService.STATUS_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        receiverRegistered = true;
        if (previewQuiz != null) previewQuiz.setHostActive(true);
        refreshIfNeeded();
    }

    @Override protected void onPause() {
        if (previewQuiz != null) previewQuiz.setHostActive(false);
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean(PREVIEW_KEY, preview);
        out.putBoolean(SUCCESS_KEY, previewSuccess);
        if (previewQuiz != null) out.putBundle(ATTEMPT_KEY, previewQuiz.saveAttempt());
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (preview) {
            endPreview(false);
        } else {
            super.onBackPressed();
        }
    }

    private int serviceStatus() {
        if (GateService.isConnected()) return 2;
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager != null) {
            ComponentName expected = new ComponentName(this, GateService.class);
            for (AccessibilityServiceInfo item : manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (item.getResolveInfo() == null) continue;
                ServiceInfo info = item.getResolveInfo().serviceInfo;
                if (info != null && expected.equals(new ComponentName(info.packageName, info.name))) {
                    return 1;
                }
            }
        }
        return 0;
    }

    private void refreshIfNeeded() {
        if (preview) return;
        int status = serviceStatus();
        boolean installed = getPackageManager().getLaunchIntentForPackage(GateService.TARGET_PACKAGE) != null;
        if (lastStatus == status && lastInstalled == installed && page != null) return;
        int scrollY = page == null ? 0 : page.getScrollY();
        lastStatus = status;
        lastInstalled = installed;
        showHome(status, installed, scrollY);
    }

    private void startPreview() {
        startPreview(null);
    }

    private void startPreview(Bundle restored) {
        preview = true;
        previewSuccess = false;
        page = null;
        previewQuiz = new QuizView(this, false, true, () -> endPreview(true), () -> endPreview(false), restored);
        setContentView(previewQuiz);
    }

    private void endPreview(boolean solved) {
        if (!preview) return;
        preview = false;
        previewQuiz = null;
        previewSuccess = solved;
        refreshIfNeeded();
        if (solved) page.announceForAccessibility("体验完成，答对后就可以使用小红书了。");
    }

    private void showHome(int status, boolean installed, int scrollY) {
        boolean active = status == 2;
        boolean useCompactLayout = getResources().getConfiguration().screenWidthDp < 340
                || getResources().getConfiguration().fontScale > 1.25f;
        page = new ScrollView(this);
        page.setBackgroundColor(PAPER);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        FrameLayout center = new FrameLayout(this);
        page.addView(center);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(22), dp(23), dp(22), dp(22));
        int screen = getResources().getDisplayMetrics().widthPixels;
        center.addView(column, new FrameLayout.LayoutParams(
                Math.min(screen, dp(580)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        top.addView(new HomeIllustration(this, HomeIllustration.Scene.MARK), markParams);
        TextView brand = label("先答一题", 19, INK, true);
        brand.setPadding(dp(9), 0, 0, 0);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = label(active ? "守护已开启" : status == 1 ? "等待系统连接" : "守护未开启",
                12, active ? INK : 0xff80531e, true);
        state.setPadding(dp(11), dp(6), dp(11), dp(6));
        state.setBackground(shape(active ? 0xffdeebd9 : 0xfff4e8cd, 40, 0));
        top.addView(state);
        column.addView(top, row(0));

        LinearLayout cover = new LinearLayout(this);
        cover.setBackground(shape(INK, 18, 0));
        cover.setPadding(dp(21), dp(21), dp(10), dp(22));
        cover.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout coverType = new LinearLayout(this);
        coverType.setOrientation(LinearLayout.VERTICAL);
        coverType.addView(label("三年级奥数 · 100 题", 14, 0xffdae7d4, true));
        TextView title = label("小红书", 37, PAPER, true);
        title.setIncludeFontPadding(false);
        coverType.addView(title, row(3));
        View underline = new View(this);
        underline.setBackgroundColor(MUSTARD);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(37), dp(3));
        lineParams.topMargin = dp(11);
        coverType.addView(underline, lineParams);
        cover.addView(coverType, new LinearLayout.LayoutParams(0, -2, 1));
        if (!useCompactLayout) {
            cover.addView(new HomeIllustration(this, HomeIllustration.Scene.NOTEBOOK),
                    new LinearLayout.LayoutParams(dp(123), dp(105)));
        }
        column.addView(cover, row(22));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView headingText = label("答题规则", 21, INK, true);
        if (Build.VERSION.SDK_INT >= 28) headingText.setAccessibilityHeading(true);
        heading.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1));
        Button history = action("答题记录", false);
        history.setTextSize(14);
        history.setMinHeight(dp(48));
        history.setOnClickListener(v -> startActivity(new Intent(this, QuestionHistoryActivity.class)));
        heading.addView(history);
        column.addView(heading, row(26));
        addRule(column, "打开时", "每次打开小红书，先答对一道题。", HomeIllustration.Scene.BOOK);
        addRule(column, "使用中", "每使用 30 分钟，再答对一道题。", HomeIllustration.Scene.CLOCK);
        addRule(column, "答错时", "根据提示再试一次，答对就能继续。", HomeIllustration.Scene.HINT);

        if (previewSuccess) {
            TextView feedback = label("体验完成 ✓  答对了，就能继续使用小红书。", 14, INK, false);
            feedback.setPadding(dp(12), dp(10), dp(12), dp(10));
            feedback.setBackground(shape(0xffe6eedf, 9, 0));
            column.addView(feedback, row(17));
        }
        if (active && !installed) {
            TextView note = label("这台设备上还未找到小红书，可以先体验答题。", 14, MUTED, false);
            column.addView(note, row(15));
        }

        String mainLabel = active ? (installed ? "打开小红书" : "试做一道题")
                : status == 1 ? "查看无障碍设置" : "开启小红书守护";
        Button main = action(mainLabel, true);
        main.setOnClickListener(v -> {
            if (active && installed) launchTarget();
            else if (active) startPreview();
            else if (status == 1) openSystemSettings();
            else requestConsent();
        });
        column.addView(main, row(23));
        if (!active || installed) {
            Button demo = action("试做一道题", false);
            demo.setOnClickListener(v -> startPreview());
            column.addView(demo, row(9));
        }
        if (active) {
            Button settings = action("管理守护权限", false);
            settings.setBackground(new RippleDrawable(ColorStateList.valueOf(0x15153f38), null, null));
            settings.setTextSize(14);
            settings.setTextColor(MUTED);
            settings.setOnClickListener(v -> openSystemSettings());
            column.addView(settings, row(3));
        }

        setContentView(page);
        final ScrollView rendered = page;
        if (scrollY > 0) rendered.post(() -> rendered.scrollTo(0, scrollY));
    }

    private void addRule(LinearLayout parent, String title, String explanation, HomeIllustration.Scene icon) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(12), 0, dp(12));
        line.addView(new HomeIllustration(this, icon), new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(13), 0, 0, 0);
        copy.addView(label(title, 16, INK, true));
        copy.addView(label(explanation, 14, MUTED, false));
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(line, row(0));
        View border = new View(this);
        border.setBackgroundColor(RULE);
        parent.addView(border, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void requestConsent() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("允许「先答一题」守护小红书？")
                .setMessage("开启后，应用会在本机读取前台窗口所属的应用标识，用来判断小红书何时打开、离开，以及何时满 30 分钟。\n\n"
                        + "到答题时间时，会在小红书上方显示题目。应用不读取屏幕文字、点击内容或聊天记录，也不联网。"
                        + "\n\n请在下一页选择「先答一题」并开启服务。你可以随时在同一位置关闭。")
                .setNegativeButton("暂不开启", null)
                .setPositiveButton("同意，去设置", (dialog, which) -> openSystemSettings())
                .show();
    }

    private void openSystemSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "请在手机设置的「无障碍」中找到「先答一题」。", Toast.LENGTH_LONG).show();
        }
    }

    private void launchTarget() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(GateService.TARGET_PACKAGE);
        if (launch != null) {
            try {
                startActivity(launch);
                return;
            } catch (ActivityNotFoundException ignored) {
                // The app may have been removed after the status page was drawn.
            }
        }
        Toast.makeText(this, "未找到可打开的小红书。", Toast.LENGTH_SHORT).show();
        refreshIfNeeded();
    }

    private Button action(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? PAPER : INK);
        button.setMinHeight(dp(50));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33ffffff : 0x1c153f38),
                shape(primary ? INK : PAPER, 11, primary ? 0 : 0xffadbbab), null));
        return button;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable shape(int color, int radius, int outline) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        if (outline != Color.TRANSPARENT) background.setStroke(dp(1), outline);
        return background;
    }

    private LinearLayout.LayoutParams row(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
