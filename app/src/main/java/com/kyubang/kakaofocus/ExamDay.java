package com.kyubang.kakaofocus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

final class ExamDay {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final LocalDate EXAM = LocalDate.of(2027, 11, 18);
    static final String ACTION_MIDNIGHT = "com.kyubang.kakaofocus.REFRESH_EXAM_DAY";

    private ExamDay() {}

    static String label() {
        long days = ChronoUnit.DAYS.between(LocalDate.now(KOREA), EXAM);
        return days > 0 ? "수능 D-" + days
                : days == 0 ? "수능 D-DAY" : "수능 D+" + Math.abs(days);
    }

    static void schedule(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        if (ids == null || ids.length == 0) {
            cancel(context);
            return;
        }
        ZonedDateTime next = LocalDate.now(KOREA).plusDays(1)
                .atStartOfDay(KOREA).plusMinutes(1);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    next.toInstant().toEpochMilli(), tickIntent(context));
        }
    }

    static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(context));
    }

    private static PendingIntent tickIntent(Context context) {
        Intent intent = new Intent(context, WidgetProvider.class);
        intent.setAction(ACTION_MIDNIGHT);
        return PendingIntent.getBroadcast(context, 610, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
