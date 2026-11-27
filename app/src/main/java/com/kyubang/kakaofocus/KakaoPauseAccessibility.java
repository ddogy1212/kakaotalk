package com.kyubang.kakaofocus;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * User-enabled, local-only restriction. Observes package transitions only;
 * does not request window text, read messages, or inspect UI content.
 */
public final class KakaoPauseAccessibility extends AccessibilityService {
    private long lastRedirectElapsed = 0L;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !PauseState.active(this)) return;
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
