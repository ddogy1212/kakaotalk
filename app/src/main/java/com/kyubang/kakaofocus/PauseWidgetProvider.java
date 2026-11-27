package com.kyubang.kakaofocus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
        int minutes = PauseState.durationMinutes(context);
        int theme = RoomStore.theme(context);
        int bg = theme == 1 ? R.drawable.widget_bg_light
                : theme == 2 ? R.drawable.widget_bg_translucent : R.drawable.widget_bg;
        int base = theme == 1 ? Color.rgb(45, 37, 49) : Color.WHITE;
        int accent = theme == 1 ? Color.rgb(180, 53, 112) : Color.rgb(255, 182, 220);
        Bitmap bitmap = PhotoStore.bitmap(context);
        boolean hasPhoto = bitmap != null;
        int displayMode = hasPhoto ? PauseState.displayMode(context) : 0;

        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_pause);
            views.setInt(R.id.pause_root, "setBackgroundResource", bg);
            views.setViewVisibility(R.id.pause_photo, hasPhoto ? View.VISIBLE : View.GONE);
            if (hasPhoto) views.setImageViewBitmap(R.id.pause_photo, bitmap);
            views.setTextColor(R.id.pause_title, hasPhoto ? Color.WHITE : base);
            views.setTextColor(R.id.pause_hint, hasPhoto ? Color.WHITE : accent);
            views.setTextColor(R.id.pause_countdown, hasPhoto ? Color.WHITE : accent);

            PendingIntent target;
            if (remaining > 0) {
                // When the user chose "photo only", do not cover the picture with text.
                views.setViewVisibility(R.id.pause_text_panel,
                        displayMode == 2 ? View.GONE : View.VISIBLE);
                views.setViewVisibility(R.id.pause_title,
                        displayMode == 1 || displayMode == 2 ? View.GONE : View.VISIBLE);
                views.setViewVisibility(R.id.pause_hint,
                        displayMode == 1 || displayMode == 2 ? View.GONE : View.VISIBLE);
                views.setInt(R.id.pause_text_panel, "setBackgroundColor",
                        hasPhoto ? Color.argb(displayMode == 1 ? 65 : 110, 0, 0, 0)
                                : Color.TRANSPARENT);
                views.setTextViewText(R.id.pause_title, "카톡 차단 중");
                views.setTextViewText(R.id.pause_hint, "끝나면 자동 해제");
                views.setViewVisibility(R.id.pause_countdown,
                        displayMode == 2 ? View.GONE : View.VISIBLE);
                views.setTextViewTextSize(R.id.pause_countdown,
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        displayMode == 1 ? 9f : 14f);
                if (displayMode != 2) {
                    views.setChronometer(R.id.pause_countdown,
                            SystemClock.elapsedRealtime() + remaining,
                            null, true);
                    views.setChronometerCountDown(R.id.pause_countdown, true);
                }
                Intent details = new Intent(context, MainActivity.class);
                target = PendingIntent.getActivity(context, 510, details,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                views.setViewVisibility(R.id.pause_title, View.VISIBLE);
                views.setViewVisibility(R.id.pause_hint, View.VISIBLE);
                views.setViewVisibility(R.id.pause_countdown, View.GONE);
                // When a photo is set, the idle widget is just the selected image.
                boolean showText = !hasPhoto || !enabled;
                views.setViewVisibility(R.id.pause_text_panel,
                        showText ? View.VISIBLE : View.GONE);
                views.setInt(R.id.pause_text_panel, "setBackgroundColor",
                        hasPhoto ? Color.argb(110, 0, 0, 0) : Color.TRANSPARENT);
                views.setTextViewText(R.id.pause_title, "카톡 " + minutes + "분");
                views.setTextViewText(R.id.pause_hint,
                        enabled ? "누르면 시작" : "권한 설정 필요");
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
        if (bitmap != null) bitmap.recycle();
    }
}
