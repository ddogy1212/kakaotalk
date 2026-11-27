package com.kyubang.kakaofocus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;

public final class CompactWidgetProvider extends AppWidgetProvider {
    @Override public void onEnabled(Context context) {
        RecoveryScheduler.scheduleIfWidgets(context);
        NotificationWatcher.reconnectIfNeeded(context);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RecoveryScheduler.scheduleIfWidgets(context);
        NotificationWatcher.reconnectIfNeeded(context);
        updateAll(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                    int id, android.os.Bundle options) {
        updateAll(context);
    }

    @Override public void onDisabled(Context context) {
        RecoveryScheduler.scheduleIfWidgets(context);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CompactWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        boolean granted = WidgetUpdater.hasAccess(context);
        boolean connected = NotificationWatcher.isConnected();
        int active = RoomStore.activeRooms(context);
        int theme = RoomStore.theme(context);
        int bg = theme == 1 ? R.drawable.widget_bg_light
                : theme == 2 ? R.drawable.widget_bg_translucent : R.drawable.widget_bg;
        int ink = theme == 1 ? Color.rgb(51, 38, 52) : Color.WHITE;
        int accent = theme == 1 ? Color.rgb(178, 49, 113) : Color.rgb(255, 182, 220);
        float sp = RoomStore.compactSp(context);
        int[] rows = {R.id.compact_row_1, R.id.compact_row_2,
                R.id.compact_row_3, R.id.compact_row_4};
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_compact);
            views.setInt(R.id.compact_root, "setBackgroundResource", bg);
            boolean anyAlert = false;
            for (int i = 0; i < rows.length; i++) {
                views.setViewVisibility(rows[i], i < active ? View.VISIBLE : View.GONE);
                if (i >= active) continue;
                String actualName = RoomStore.name(context, i);
                String name = RoomStore.displayName(context, i);
                int count = RoomStore.count(context, i);
                String label;
                if (name.isEmpty()) {
                    label = (i + 1) + "번 방 등록";
                } else if (!granted) {
                    label = name + " 권한 필요";
                } else if (!connected) {
                    label = name + " 연결 확인";
                } else {
                    // When very small, omit time to keep the count legible.
                    label = name + " " + (active <= 2
                            ? WidgetUpdater.countAndTime(context, i)
                            : count > 0 ? "×" + count : "없음");
                    if (count > 0) anyAlert = true;
                }
                views.setTextViewText(rows[i], label);
                views.setTextColor(rows[i],
                        count > 0 && connected && granted ? accent : ink);
                views.setTextViewTextSize(rows[i], TypedValue.COMPLEX_UNIT_SP, sp);
                views.setInt(rows[i], "setGravity",
                        Gravity.CENTER_VERTICAL | WidgetUpdater.horizontalGravity(context));
            }
            Intent intent = anyAlert
                    ? context.getPackageManager().getLaunchIntentForPackage("com.kakao.talk") : null;
            if (intent == null) intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent open = PendingIntent.getActivity(context, 420, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.compact_root, open);
            manager.updateAppWidget(id, views);
        }
    }
}
