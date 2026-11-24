package cn.videosecond.gate;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** Detects the foreground on the primary display. Only window identity is used. */
public final class GateService extends AccessibilityService {
    public static final String STATUS_CHANGED = "cn.videosecond.gate.STATUS_CHANGED";
    public static final String TARGET_PACKAGE = "com.xingin.xhs";
    private static final long WATCHDOG_MS = 2500;
    private static volatile boolean connected;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SessionGate gate = new SessionGate();
    private final SparseArray<String> windowPackages = new SparseArray<>();
    private final Rect targetBounds = new Rect();
    private final Runnable check = () -> refresh(null);
    private final BroadcastReceiver displayReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                gate.observe(SessionGate.Presence.AWAY, SystemClock.uptimeMillis());
                applyState(false);
            } else {
                refresh(null);
            }
        }
    };

    private PowerManager power;
    private KeyguardManager keyguard;
    private WindowManager windowManager;
    private int lastAppWindow = -1;
    private int uncertainChecks;
    private QuestionWindow question;
    private long viewQuestionId;
    private long retryOverlayAfter;
    private boolean running;
    private boolean attached;

    public static boolean isConnected() { return connected; }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (running) return;
        windowManager = getSystemService(WindowManager.class);
        power = getSystemService(PowerManager.class);
        keyguard = getSystemService(KeyguardManager.class);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(displayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(displayReceiver, filter);
        }
        running = true;
        setConnected(true);
        refresh(null);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!running || event == null) return;
        int kind = event.getEventType();
        if (kind == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                kind == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            uncertainChecks = 0;
            refresh(event);
        }
    }

    private void refresh(AccessibilityEvent event) {
        if (!running) return;
        SessionGate.Presence observed = observeForeground(event);
        // Keep the existing decision during a transition with no readable app window.
        // A short retry resolves it without briefly exposing a pending question.
        gate.observe(observed == null ? gate.presence() : observed, SystemClock.uptimeMillis());
        applyState(observed == null);
    }

    private SessionGate.Presence observeForeground(AccessibilityEvent event) {
        if (!power.isInteractive() || keyguard.isKeyguardLocked()) return SessionGate.Presence.AWAY;
        boolean stateEvent = event != null &&
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        String eventPackage = stateEvent && event.getPackageName() != null
                ? event.getPackageName().toString() : "";
        String eventClass = stateEvent && event.getClassName() != null
                ? event.getClassName().toString() : "";
        if ("com.android.systemui".equals(eventPackage) &&
                eventClass.toLowerCase(Locale.ROOT).contains("recents")) {
            return SessionGate.Presence.AWAY;
        }
        List<AccessibilityWindowInfo> windows = getWindows();
        AccessibilityWindowInfo app = null;
        AccessibilityWindowInfo panel = null;
        int bestRank = -1;
        int eventWindowType = -1;
        try {
            for (AccessibilityWindowInfo w : windows) {
                if (stateEvent && event.getWindowId() == w.getId()) eventWindowType = w.getType();
                if (w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    int rank = w.isFocused() ? 3 : w.isActive() ? 2 : w.getId() == lastAppWindow ? 1 : 0;
                    if (rank > bestRank) {
                        bestRank = rank;
                        app = w;
                    }
                } else if (w.getType() == AccessibilityWindowInfo.TYPE_SYSTEM &&
                        (w.isFocused() || w.isActive()) &&
                        (panel == null || w.getLayer() > panel.getLayer())) {
                    panel = w;
                }
                // Our accessibility overlay and input-method windows cannot identify
                // the underlying app, even when they own keyboard/touch focus.
            }
            if (panel != null && (app == null || panel.getLayer() > app.getLayer())) {
                // A modal shade may be the only exposed window. Use the last
                // confirmed target bounds until an unrelated app or lock clears them.
                Rect appBounds = new Rect(targetBounds);
                if (app != null) app.getBoundsInScreen(appBounds);
                if (fullyCovers(panel, appBounds)) return SessionGate.Presence.SYSTEM_PANEL;
            }
            if (app != null) {
                int id = app.getId();
                String pkg = windowPackages.get(id);
                if (stateEvent && event.getWindowId() == id && !eventPackage.isEmpty()) {
                    pkg = eventPackage;
                }
                if (pkg == null) pkg = readRootPackage(app);
                if (pkg != null && !pkg.isEmpty()) {
                    if (windowPackages.size() > 32) windowPackages.clear();
                    windowPackages.put(id, pkg);
                    lastAppWindow = id;
                    if (TARGET_PACKAGE.equals(pkg)) app.getBoundsInScreen(targetBounds);
                    if (!targetBounds.isEmpty() && isSystemDialogHost(pkg) &&
                            gate.stage() != SessionGate.Stage.OUTSIDE) {
                        // Some system dialogs are TYPE_APPLICATION. If target pixels are
                        // still exposed around the dialog, retain a pending target gate.
                        return fullyCovers(app, targetBounds) ? SessionGate.Presence.SYSTEM_PANEL :
                                SessionGate.Presence.TARGET;
                    }
                    return presenceOf(pkg);
                }
            }
        } finally {
            for (AccessibilityWindowInfo window : windows) window.recycle();
        }

        // Some OEM transitions publish the package event before any interactive roots.
        // Events from our own overlay must never be taken for an app switch.
        if (stateEvent && !eventPackage.isEmpty() &&
                (eventWindowType == -1 || eventWindowType == AccessibilityWindowInfo.TYPE_APPLICATION)) {
            if (getPackageName().equals(eventPackage)) {
                return (getPackageName() + ".MainActivity").equals(eventClass)
                        ? SessionGate.Presence.AWAY : null;
            }
            // Unresolved System UI / IME events cannot prove that the target has left.
            if (!isSystemDialogHost(eventPackage) && !isDefaultKeyboard(eventPackage)) {
                return presenceOf(eventPackage);
            }
        }
        return null;
    }

    private static SessionGate.Presence presenceOf(String pkg) {
        return TARGET_PACKAGE.equals(pkg) ? SessionGate.Presence.TARGET : SessionGate.Presence.AWAY;
    }

    private static String readRootPackage(AccessibilityWindowInfo window) {
        AccessibilityNodeInfo root = Build.VERSION.SDK_INT >= 33 ? window.getRoot(0) : window.getRoot();
        if (root == null) return null;
        try {
            CharSequence pkg = root.getPackageName();
            return pkg == null ? null : pkg.toString();
        } finally {
            root.recycle();
        }
    }

    private static boolean fullyCovers(AccessibilityWindowInfo window, Rect appBounds) {
        Rect bounds = new Rect();
        window.getBoundsInScreen(bounds);
        return !appBounds.isEmpty() && bounds.contains(appBounds);
    }

    private static boolean isSystemDialogHost(String pkg) {
        return "android".equals(pkg) || "com.android.systemui".equals(pkg) ||
                "com.android.permissioncontroller".equals(pkg) || "com.google.android.permissioncontroller".equals(pkg) ||
                "com.android.packageinstaller".equals(pkg) || "com.google.android.packageinstaller".equals(pkg);
    }

    private boolean isDefaultKeyboard(String pkg) {
        String keyboard = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName ime = keyboard == null ? null : ComponentName.unflattenFromString(keyboard);
        return ime != null && pkg.equals(ime.getPackageName());
    }

    private void applyState(boolean uncertain) {
        // Keep an existing opaque question attached across transient panels. A shade
        // can report fullscreen bounds while only partly expanded or translucent.
        boolean retainUnderPanel = attached && gate.hasQuestion() &&
                gate.presence() == SessionGate.Presence.SYSTEM_PANEL;
        if (gate.shouldShowQuestion() || retainUnderPanel) {
            showQuestion();
        } else {
            detachQuestion();
            // A transient system panel should preserve the student's actual question and answer.
            if (!gate.hasQuestion()) {
                question = null;
            }
            if (gate.stage() == SessionGate.Stage.OUTSIDE) targetBounds.setEmpty();
        }
        handler.removeCallbacks(check);
        long delay = Long.MAX_VALUE;
        if (uncertain) {
            delay = uncertainChecks < 5 ? 160 : 5000;
            uncertainChecks = Math.min(5, uncertainChecks + 1);
        } else {
            uncertainChecks = 0;
        }
        if (gate.stage() != SessionGate.Stage.OUTSIDE) delay = Math.min(delay, WATCHDOG_MS);
        if (gate.presence() == SessionGate.Presence.TARGET && gate.stage() == SessionGate.Stage.READING) {
            delay = Math.min(delay, gate.remaining());
        }
        if (delay != Long.MAX_VALUE) handler.postDelayed(check, Math.max(1, delay));
    }

    private void showQuestion() {
        long id = gate.questionId();
        if (attached && viewQuestionId == id) return;
        if (SystemClock.uptimeMillis() < retryOverlayAfter) return;
        if (question == null || viewQuestionId != id) {
            detachQuestion();
            viewQuestionId = id;
            question = new QuestionWindow(this, id);
            question.addView(new QuizView(this, gate.isRenewal(), false, () -> answer(id), () -> leave(id)));
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 0, PixelFormat.OPAQUE);
        lp.setTitle("小红书答题");
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        try {
            windowManager.addView(question, lp);
            attached = true;
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException | SecurityException e) {
            // The service token may have been revoked during teardown. Never grant on failure.
            retryOverlayAfter = SystemClock.uptimeMillis() + 1000;
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    private void answer(long id) {
        if (!running || !attached) return;
        SessionGate.Presence observed = observeForeground(null);
        long now = SystemClock.uptimeMillis();
        gate.observe(observed == null ? gate.presence() : observed, now);
        gate.grant(id, now, attached && viewQuestionId == id);
        applyState(observed == null);
    }

    private void leave(long id) {
        if (!running || !attached || !gate.hasQuestion() || id != gate.questionId()) return;
        // Keep the opaque question present until the foreground observation confirms departure.
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
            Toast.makeText(this, "请用系统主页键或手势返回桌面", Toast.LENGTH_SHORT).show();
        }
        handler.removeCallbacks(check);
        handler.postDelayed(check, 140);
    }

    private final class QuestionWindow extends FrameLayout {
        private final long id;
        QuestionWindow(Context context, long id) {
            super(context);
            this.id = id;
            setBackgroundColor(0xfffbf7ed);
        }
        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) leave(id);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }

    private void detachQuestion() {
        if (attached) {
            try {
                windowManager.removeViewImmediate(question);
            } catch (IllegalArgumentException ignored) {
                // Window manager already removed it when the connection ended.
            }
            attached = false;
        }
    }

    private void setConnected(boolean value) {
        connected = value;
        sendBroadcast(new Intent(STATUS_CHANGED).setPackage(getPackageName()));
    }

    private void stopMonitoring() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(check);
        unregisterReceiver(displayReceiver);
        detachQuestion();
        question = null;
        targetBounds.setEmpty();
        gate.observe(SessionGate.Presence.AWAY, SystemClock.uptimeMillis());
        windowPackages.clear();
        lastAppWindow = -1;
        setConnected(false);
    }

    @Override public void onInterrupt() { /* No spoken or haptic feedback to interrupt. */ }
    @Override public boolean onUnbind(Intent intent) {
        stopMonitoring();
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }
}
