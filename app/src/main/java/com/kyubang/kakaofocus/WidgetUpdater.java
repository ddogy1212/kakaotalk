package com.kyubang.kakaofocus;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

final class WidgetUpdater {
    private WidgetUpdater() {}

    static boolean hasAccess(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(context, NotificationWatcher.class));
    }

    static void updateAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, WidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        boolean granted = hasAccess(app);
        boolean connected = NotificationWatcher.isConnected();
        int[] viewIds = {R.id.room_row_1, R.id.room_row_2, R.id.room_row_3};
        for (int widgetId : ids) {
            RemoteViews views = new RemoteViews(app.getPackageName(), R.layout.widget_small);
            boolean anyAlert = false;
            for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
                String name = RoomStore.name(app, i);
                int count = RoomStore.count(app, i);
                String label;
                if (name.isEmpty()) {
                    label = "＋ " + (i + 1) + "번째 톡방 등록";
                } else if (!granted) {
                    label = name + ": 권한 필요";
                } else if (!connected) {
                    label = name + ": 감지 대기 중";
                } else if (count > 0) {
                    label = name + "에서 알림 ×" + count;
                    anyAlert = true;
                } else {
                    label = name + ": 알림 없음";
                }
                views.setTextViewText(viewIds[i], label);
                views.setTextColor(viewIds[i],
                        count > 0 && connected && granted ?
                                Color.rgb(255, 182, 220) : Color.rgb(240, 235, 243));
            }

            Intent destination;
            if (anyAlert) {
                destination = app.getPackageManager().getLaunchIntentForPackage("com.kakao.talk");
                if (destination == null) destination = new Intent(app, MainActivity.class);
            } else {
                destination = new Intent(app, MainActivity.class);
            }
            destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pending = PendingIntent.getActivity(
                    app, 0, destination,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, pending);
            manager.updateAppWidget(widgetId, views);
        }
    }
}
