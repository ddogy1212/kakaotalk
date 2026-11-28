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
    private long lastRedirectElapsed = 0L;
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
        if (pausePrefs != null) pausePrefs.unregisterOnSharedPreferenceChangeListener(pauseListener);
        pausePrefs = PauseState.prefs(this);
        pausePrefs.registerOnSharedPreferenceChangeListener(pauseListener);
        PauseState.scheduleExpiry(this);
        PauseWidgetProvider.updateAll(this);
        restartCountdown();
    }

    @Override public void onDestroy() {
        if (pausePrefs != null) {
            pausePrefs.unregisterOnSharedPreferenceChangeListener(pauseListener);
            pausePrefs = null;
        }
        countdownHandler.removeCallbacks(countdownTick);
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
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

    @Override public void onInterrupt() {
        // Nothing to release; only package-change events are consumed.
    }
}
