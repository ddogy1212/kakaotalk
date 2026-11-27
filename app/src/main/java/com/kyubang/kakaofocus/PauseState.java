package com.kyubang.kakaofocus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

final class PauseState {
    static int durationMinutes(Context context) {
        return Math.max(1, Math.min(60, prefs(context).getInt("duration_minutes", 10)));
    }

    static void saveDurationMinutes(Context context, int minutes) {
        prefs(context).edit().putInt("duration_minutes",
                Math.max(1, Math.min(60, minutes))).apply();
        PauseWidgetProvider.updateAll(context);
    }
    /** While active: 0 = full countdown, 1 = tiny countdown, 2 = picture only. */
    static int displayMode(Context context) {
        return Math.max(0, Math.min(2, prefs(context).getInt("pause_display_mode", 0)));
    }

    static void saveDisplayMode(Context context, int value) {
        prefs(context).edit()
                .putInt("pause_display_mode", Math.max(0, Math.min(2, value)))
                .apply();
        PauseWidgetProvider.updateAll(context);
    }

    private PauseState() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences("kakao_ten_minute_pause", Context.MODE_PRIVATE);
    }

    static boolean accessibilityEnabled(Context context) {
        String names = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (names == null) return false;
        for (String value : names.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component != null && component.getPackageName().equals(context.getPackageName())
                    && component.getClassName().equals(KakaoPauseAccessibility.class.getName())) {
                return true;
            }
        }
        return false;
    }

    static long endAt(Context context) {
        return prefs(context).getLong("pause_end_wall", 0L);
    }

    static long remaining(Context context) {
        return Math.max(0L, endAt(context) - System.currentTimeMillis());
    }

    static boolean active(Context context) {
        return remaining(context) > 0L;
    }

    static void startTenMinutes(Context context) {
        // Never claim to block the app unless the user explicitly enabled the service.
        if (!accessibilityEnabled(context) || active(context)) return;
        prefs(context).edit()
                .putLong("pause_end_wall", System.currentTimeMillis() + durationMinutes(context) * 60_000L)
                .apply();
        scheduleExpiry(context);
        PauseWidgetProvider.updateAll(context);
    }

    static void scheduleExpiry(Context context) {
        long remaining = remaining(context);
        if (remaining <= 0) {
            clearIfExpired(context);
            return;
        }
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + remaining, expirationIntent(context));
    }

    static void clearIfExpired(Context context) {
        if (active(context)) return;
        prefs(context).edit().remove("pause_end_wall").apply();
        PauseWidgetProvider.updateAll(context);
    }

    private static PendingIntent expirationIntent(Context context) {
        Intent event = new Intent(context, PauseActionReceiver.class);
        event.setAction(PauseActionReceiver.ACTION_EXPIRE);
        return PendingIntent.getBroadcast(context, 501, event,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
