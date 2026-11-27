package com.kyubang.kakaofocus;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class WidgetUpdater {
    private WidgetUpdater() {}

    static boolean hasAccess(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(context, NotificationWatcher.class));
    }

    static String timeLabel(long time) {
        if (time <= 0) return "";
        Calendar now = Calendar.getInstance();
        Calendar at = Calendar.getInstance();
        at.setTimeInMillis(time);
        boolean today = now.get(Calendar.YEAR) == at.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == at.get(Calendar.DAY_OF_YEAR);
        return new SimpleDateFormat(today ? "HH:mm" : "M/d", Locale.KOREA)
                .format(new Date(time));
    }

    static String countAndTime(Context context, int index) {
        int count = RoomStore.count(context, index);
        String when = timeLabel(RoomStore.lastMessageAt(context, index));
        return (count > 0 ? "×" + count : "없음")
                + (when.isEmpty() ? "" : " · " + when);
    }

    static void updateAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, WidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        boolean granted = hasAccess(app);
        boolean connected = NotificationWatcher.isConnected();
        int[] names = {R.id.room_name_1, R.id.room_name_2, R.id.room_name_3};
        int[] statuses = {R.id.room_status_1, R.id.room_status_2, R.id.room_status_3};
        int theme = RoomStore.theme(app);
        int background = theme == 1 ? R.drawable.widget_bg_light
                : theme == 2 ? R.drawable.widget_bg_translucent : R.drawable.widget_bg;
        int ink = theme == 1 ? Color.rgb(51, 38, 52) : Color.rgb(240, 235, 243);
        int muted = theme == 1 ? Color.rgb(115, 104, 118) : Color.rgb(183, 174, 190);
        int alert = theme == 1 ? Color.rgb(177, 43, 105) : Color.rgb(255, 182, 220);
        float size = 9f + RoomStore.fontScale(app);

        for (int widgetId : ids) {
            RemoteViews views = new RemoteViews(app.getPackageName(), R.layout.widget_small);
            views.setInt(R.id.widget_root, "setBackgroundResource", background);
            boolean anyAlert = false;
            for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
                String room = RoomStore.name(app, i);
                int count = RoomStore.count(app, i);
                String caption;
                String status;
                if (room.isEmpty()) {
                    caption = (i + 1) + "번 톡방 등록";
                    status = "";
                } else if (!granted) {
                    caption = room;
                    status = "권한 필요";
                } else if (!connected) {
                    caption = room;
                    status = "연결 확인";
                } else {
                    caption = room;
                    status = countAndTime(app, i);
                    if (count > 0) anyAlert = true;
                }
                views.setTextViewText(names[i], caption);
                views.setTextViewText(statuses[i], status);
                views.setTextColor(names[i], ink);
                views.setTextColor(statuses[i],
                        count > 0 && connected && granted ? alert : muted);
                views.setTextViewTextSize(names[i], TypedValue.COMPLEX_UNIT_SP, size);
                views.setTextViewTextSize(statuses[i], TypedValue.COMPLEX_UNIT_SP, Math.max(8f, size - 1f));
            }

            Intent destination = anyAlert
                    ? app.getPackageManager().getLaunchIntentForPackage("com.kakao.talk") : null;
            if (destination == null) destination = new Intent(app, MainActivity.class);
            destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pending = PendingIntent.getActivity(app, 0, destination,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, pending);
            manager.updateAppWidget(widgetId, views);
        }
    }
}
