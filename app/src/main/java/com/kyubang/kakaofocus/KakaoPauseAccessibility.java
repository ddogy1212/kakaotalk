package com.kyubang.kakaofocus;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * User-enabled, local-only restriction. Observes package transitions only;
 * does not request window text, read messages, or inspect UI content.
 */
public final class KakaoPauseAccessibility extends AccessibilityService {
    private static KakaoPauseAccessibility connected;
    private static final long VISIT_MS = 10_000L;
    private long visitEnd = 0L;
    private String visitToken = "", visitLabel = "";
    private final Handler visitHandler = new Handler(Looper.getMainLooper());
    private final Runnable visitExpiry = () -> endVisit(true);
    private long lastRedirectElapsed = 0L;

    static boolean visitReady(android.content.Context c) {
        return connected != null && PauseState.accessibilityEnabled(c)
                && connected.visitEnd == 0L;
    }

    static boolean beginVisit(android.content.Context c, String token, String label) {
        if (!visitReady(c) || token.isEmpty()
                || !token.equals(MiniChatStore.token(c))
                || !label.equals(MiniChatStore.label(c))) return false;
        KakaoPauseAccessibility s = connected;
        s.visitToken = token;
        s.visitLabel = label;
        s.visitEnd = SystemClock.elapsedRealtime() + VISIT_MS;
        s.visitHandler.postDelayed(s.visitExpiry, VISIT_MS);
        return true;
    }

    static void cancelVisit() {
        if (connected != null) connected.endVisit(false);
    }

    private void endVisit(boolean goHome) {
        if (visitEnd == 0L) return;
        visitEnd = 0L;
        visitToken = "";
        visitLabel = "";
        visitHandler.removeCallbacks(visitExpiry);
        // No force-stop, task deletion, or extension of the existing pause timer.
        if (goHome) performGlobalAction(GLOBAL_ACTION_HOME);
    }
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences pausePrefs;

    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            long remaining = PauseState.remaining(KakaoPauseAccessibility.this);
            if (remaining <= 0L) {
                if (PauseState.endAt(KakaoPauseAccessibility.this) != 0L) {
                    PauseState.clearIfExpired(KakaoPauseAccessibility.this);
                }
                return;
            }
            PauseWidgetProvider.updateCountdown(KakaoPauseAccessibility.this, remaining);
            long next = remaining % 1000L;
            countdownHandler.postDelayed(this, next == 0L ? 1000L : next);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener pauseListener =
            (prefs, key) -> {
                if ("pause_end_wall".equals(key) || key == null) restartCountdown();
            };

    private void restartCountdown() {
        countdownHandler.removeCallbacks(countdownTick);
        countdownHandler.post(countdownTick);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        if (pausePrefs != null) pausePrefs.unregisterOnSharedPreferenceChangeListener(pauseListener);
        pausePrefs = PauseState.prefs(this);
        pausePrefs.registerOnSharedPreferenceChangeListener(pauseListener);
        PauseState.scheduleExpiry(this);
        PauseWidgetProvider.updateAll(this);
        restartCountdown();
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        endVisit(true);
        if (connected == this) connected = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        endVisit(true);
        if (connected == this) connected = null;
        if (pausePrefs != null) {
            pausePrefs.unregisterOnSharedPreferenceChangeListener(pauseListener);
            pausePrefs = null;
        }
        countdownHandler.removeCallbacks(countdownTick);
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (visitEnd != 0L) {
            if (SystemClock.elapsedRealtime() >= visitEnd
                    || !visitToken.equals(MiniChatStore.token(this))
                    || !visitLabel.equals(MiniChatStore.label(this))) {
                endVisit(true);
                return;
            }
            // Only a user-requested, non-renewable 10-second visit may suspend
            // the existing package-level pause. No window content is read.
            return;
        }
        if (!PauseState.active(this)) {
            if (PauseState.endAt(this) != 0L) PauseState.clearIfExpired(this);
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !"com.kakao.talk".contentEquals(pkg)) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirectElapsed < 600L) return;
        lastRedirectElapsed = now;
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    @Override public void onInterrupt() { endVisit(true); }
}
