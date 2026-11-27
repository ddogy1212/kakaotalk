package com.kyubang.kakaofocus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * A battery-conscious, inexact background health check.
 * Android can defer these alarms in Doze and battery-saving modes.
 */
final class RecoveryScheduler {
    static final String ACTION_CHECK = "com.kyubang.kakaofocus.ACTION_HEALTH_CHECK";
    private RecoveryScheduler() {}

    static void scheduleIfWidgets(Context context) {
        AppWidgetManager wm = AppWidgetManager.getInstance(context);
        int[] ids = wm.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        int[] mini = wm.getAppWidgetIds(new ComponentName(context, CompactWidgetProvider.class));
        if ((ids == null || ids.length == 0) && (mini == null || mini.length == 0)) {
            cancel(context);
            return;
        }
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        alarm.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
                AlarmManager.INTERVAL_HALF_HOUR, intent(context));
    }

    static void cancel(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(intent(context));
    }

    private static PendingIntent intent(Context context) {
        Intent request = new Intent(context, WidgetProvider.class);
        request.setAction(ACTION_CHECK);
        return PendingIntent.getBroadcast(context, 782, request,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
