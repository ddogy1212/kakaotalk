package com.kyubang.kakaofocus;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import java.util.Calendar;

/**
 * On-demand, read-only KakaoTalk foreground usage. Does not save user activity.
 * Individual activity switches and missing OS events make this an estimate.
 */
final class KakaoUsageReader {
    static final String KAKAO_PACKAGE = "com.kakao.talk";
    private static final long ACTIVITY_SWITCH_GRACE_MS = 3000L;
    private KakaoUsageReader() {}

    static final class Day {
        final long start;
        final long end;
        long foregroundMs;
        int launches;

        Day(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    static final class Report {
        final Day[] days;
        boolean hasEvents;
        boolean unavailable;

        Report(Day[] days) { this.days = days; }

        long todayMs() { return days[6].foregroundMs; }
        int todayLaunches() { return days[6].launches; }

        long weekMs() {
            long value = 0;
            for (Day day : days) value += day.foregroundMs;
            return value;
        }

        int weekLaunches() {
            int value = 0;
            for (Day day : days) value += day.launches;
            return value;
        }
    }

    static boolean hasPermission(Context context) {
        AppOpsManager manager =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (manager == null) return false;
        int result;
        if (Build.VERSION.SDK_INT >= 29) {
            result = manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        } else {
            result = manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        }
        if (result == AppOpsManager.MODE_ALLOWED) return true;
        return result == AppOpsManager.MODE_DEFAULT
                && context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
    }

    static Report queryLastWeek(Context context) {
        long now = System.currentTimeMillis();
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);

        Day[] days = new Day[7];
        Calendar point = (Calendar) midnight.clone();
        point.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            long start = point.getTimeInMillis();
            point.add(Calendar.DAY_OF_YEAR, 1);
            days[i] = new Day(start, Math.min(point.getTimeInMillis(), now));
        }
        Report report = new Report(days);
        if (!hasPermission(context)) {
            report.unavailable = true;
            return report;
        }

        UsageStatsManager manager = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            report.unavailable = true;
            return report;
        }
        // Look back to determine whether a session was already running at the
        // start of the first day (without counting it as a new launch).
        long oldest = days[0].start;
        long lookback = Math.max(0L, oldest - 24L * 60L * 60L * 1000L);
        UsageEvents events;
        try {
            events = manager.queryEvents(lookback, now);
        } catch (RuntimeException unavailable) {
            report.unavailable = true;
            return report;
        }
        if (events == null) {
            report.unavailable = true;
            return report;
        }

        UsageEvents.Event event = new UsageEvents.Event();
        boolean foreground = false;
        long foregroundFrom = 0L;
        long lastStop = -1L;
        try {
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                long at = event.getTimeStamp();
                if (at > now) continue;
                int type = event.getEventType();
                boolean kakao = KAKAO_PACKAGE.equals(event.getPackageName());
                boolean opened = type == UsageEvents.Event.ACTIVITY_RESUMED;
                // STOPPED can arrive for an old activity after a new Kakao
                // activity has resumed. It must not end the new session.
                boolean closed = type == UsageEvents.Event.ACTIVITY_PAUSED;
                boolean screenOff = type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                        || type == UsageEvents.Event.KEYGUARD_SHOWN
                        || type == UsageEvents.Event.DEVICE_SHUTDOWN;

                if (kakao && (opened || closed)) report.hasEvents = true;
                if (screenOff || (!kakao && opened)) {
                    if (foreground) {
                        addInterval(days, foregroundFrom, at);
                        foreground = false;
                        lastStop = at;
                    }
                } else if (kakao && opened) {
                    if (!foreground) {
                        foreground = true;
                        foregroundFrom = at;
                        if (at >= oldest && at <= now
                                && (lastStop < 0 || at - lastStop >= ACTIVITY_SWITCH_GRACE_MS)) {
                            int index = indexAt(days, at);
                            if (index >= 0) days[index].launches++;
                        }
                    }
                } else if (kakao && closed && foreground) {
                    addInterval(days, foregroundFrom, at);
                    foreground = false;
                    lastStop = at;
                }
            }
            if (foreground) addInterval(days, foregroundFrom, now);
        } catch (RuntimeException badEvent) {
            report.unavailable = true;
        }
        return report;
    }

    private static int indexAt(Day[] days, long at) {
        for (int i = 0; i < days.length; i++)
            if (at >= days[i].start && at < days[i].end) return i;
        return -1;
    }

    /** Split foreground time across calendar days including midnight/DST boundaries. */
    private static void addInterval(Day[] days, long start, long end) {
        if (end <= start) return;
        // A missing screen-off/pause event must not create implausibly long
        // continuous activity time. Counts are approximate.
        end = Math.min(end, start + 6L * 60L * 60L * 1000L);
        for (Day day : days) {
            long overlap = Math.min(end, day.end) - Math.max(start, day.start);
            if (overlap > 0) day.foregroundMs += overlap;
        }
    }
}
