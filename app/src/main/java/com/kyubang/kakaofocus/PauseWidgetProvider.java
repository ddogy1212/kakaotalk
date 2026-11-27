package com.kyubang.kakaofocus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

public final class PauseWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                    int widgetId, android.os.Bundle options) {
        updateAll(context);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PauseWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        boolean enabled = PauseState.accessibilityEnabled(context);
        long remaining = PauseState.remaining(context);
        int theme = RoomStore.theme(context);
        int bg = theme == 1 ? R.drawable.widget_bg_light
                : theme == 2 ? R.drawable.widget_bg_translucent : R.drawable.widget_bg;
        int base = theme == 1 ? Color.rgb(45, 37, 49) : Color.WHITE;
        int accent = theme == 1 ? Color.rgb(180, 53, 112) : Color.rgb(255, 182, 220);

        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_pause);
            views.setInt(R.id.pause_root, "setBackgroundResource", bg);
            views.setTextColor(R.id.pause_title, base);
            views.setTextColor(R.id.pause_hint, accent);
            views.setTextColor(R.id.pause_countdown, accent);

            PendingIntent target;
            if (remaining > 0L) {
                views.setTextViewText(R.id.pause_title, "카톡 차단 중");
                views.setTextViewText(R.id.pause_hint, "10분 후 자동 해제");
                views.setViewVisibility(R.id.pause_countdown, View.VISIBLE);
                views.setChronometer(R.id.pause_countdown,
                        SystemClock.elapsedRealtime() + remaining,
                        null, true);
                views.setChronometerCountDown(R.id.pause_countdown, true);
                Intent details = new Intent(context, MainActivity.class);
                target = PendingIntent.getActivity(context, 510, details,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                views.setViewVisibility(R.id.pause_countdown, View.GONE);
                views.setTextViewText(R.id.pause_title, "카톡 10분");
                views.setTextViewText(R.id.pause_hint,
                        enabled ? "눌러서 차단" : "권한 설정 필요");
                if (enabled) {
                    Intent start = new Intent(context, PauseActionReceiver.class);
                    start.setAction(PauseActionReceiver.ACTION_START);
                    target = PendingIntent.getBroadcast(context, 511, start,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                } else {
                    Intent setup = new Intent(context, MainActivity.class);
                    target = PendingIntent.getActivity(context, 512, setup,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                }
            }
            views.setOnClickPendingIntent(R.id.pause_root, target);
            manager.updateAppWidget(id, views);
        }
    }
}
