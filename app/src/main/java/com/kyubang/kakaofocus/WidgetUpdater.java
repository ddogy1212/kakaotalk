package com.kyubang.kakaofocus;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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

    static int insetDp(Context context) {
        return RoomStore.safeInsetDp(context);
    }

    static int horizontalGravity(Context context) {
        int align = RoomStore.align(context);
        return align == 1 ? Gravity.CENTER_HORIZONTAL
                : align == 2 ? Gravity.RIGHT : Gravity.LEFT;
    }

    private static int dp(Context c, float amount) {
        return Math.round(amount * c.getResources().getDisplayMetrics().density);
    }

    /** Keep the notification number/time visible even when a chat title is long. */
    private static String fitLine(Context c, String name, String status,
                                  float sp, int availableDp) {
        if (status.isEmpty()) return name;
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        paint.setTextSize(sp * dm.scaledDensity);
        float budget = Math.max(25, availableDp) * dm.density;
        String tail = " " + status;
        if (paint.measureText(tail) > budget && status.contains(" · ")) {
            status = status.substring(0, status.indexOf(" · "));
            tail = "  " + status;
        }
        // Never let a long room title push the status beyond the rounded frame.
        float roomBudget = Math.max(0, budget - paint.measureText(tail) - dp(c, 1));
        CharSequence roomFit = TextUtils.ellipsize(name, paint, roomBudget,
                TextUtils.TruncateAt.END);
        return roomFit + tail;
    }

    static void updateAll(Context context) {
        CompactWidgetProvider.updateAll(context);
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, WidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        boolean granted = hasAccess(app);
        boolean connected = NotificationWatcher.isConnected();
        int[] rows = {R.id.room_row_1, R.id.room_row_2, R.id.room_row_3, R.id.room_row_4};
        int active = RoomStore.activeRooms(app);
        int theme = RoomStore.theme(app);
        int background = theme == 1 ? R.drawable.widget_bg_light
                : theme == 2 ? R.drawable.widget_bg_translucent : R.drawable.widget_bg;
        int ink = theme == 1 ? Color.rgb(51, 38, 52) : Color.rgb(240, 235, 243);
        int muted = theme == 1 ? Color.rgb(115, 104, 118) : Color.rgb(183, 174, 190);
        int alert = theme == 1 ? Color.rgb(177, 43, 105) : Color.rgb(255, 182, 220);
        int inset = insetDp(app);
        int gravity = Gravity.CENTER_VERTICAL | horizontalGravity(app);

        for (int widgetId : ids) {
            Bundle options = manager.getAppWidgetOptions(widgetId);
            // Some lock-screen hosts report different widget bounds from the launcher.
            int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
            int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 48);
            if (widthDp <= 0) widthDp = 110;
            if (heightDp <= 0) heightDp = 48;
            widthDp = Math.max(65, widthDp);

            // User controls font size explicitly. No silent scaling after Apply:
            // the preview and widget use the same selected value.
            float sp = RoomStore.chatSp(app);
            int topBottom = active == 4 || heightDp < 42 ? 1 : 2;
            int textBudgetDp = Math.max(35, widthDp - inset * 2 - 4);

            RemoteViews views = new RemoteViews(app.getPackageName(), R.layout.widget_small);
            views.setInt(R.id.widget_root, "setBackgroundResource", background);
            // Align the entire multiline block and each line to one shared axis.
            // Previously only the TextViews were aligned and the first row had
            // extra end padding, so center alignment visibly drifted.
            views.setInt(R.id.widget_chat_positioner, "setGravity", gravity);
            views.setInt(R.id.widget_chat_rows, "setGravity",
                    Gravity.CENTER_VERTICAL | horizontalGravity(app));
            views.setTextViewText(R.id.exam_d_day, ExamDay.label());
            views.setTextColor(R.id.exam_d_day, alert);
            views.setTextViewTextSize(R.id.exam_d_day,
                    TypedValue.COMPLEX_UNIT_SP, RoomStore.daySp(app));
            views.setViewPadding(R.id.exam_day_anchor, 0, dp(app, RoomStore.dayTopDp(app)),
                    dp(app, RoomStore.dayEndDp(app)), 0);
            views.setViewPadding(R.id.widget_root, dp(app, inset), dp(app, topBottom),
                    dp(app, inset), dp(app, topBottom));
            boolean anyAlert = false;
            for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
                views.setViewVisibility(rows[i], i < active ? View.VISIBLE : View.GONE);
                if (i >= active) continue;
                String room = RoomStore.name(app, i);
                String displayName = RoomStore.displayName(app, i);
                int count = RoomStore.count(app, i);
                String name;
                String status;
                if (room.isEmpty()) {
                    name = (i + 1) + "번 톡방 등록";
                    status = "";
                } else if (!granted) {
                    name = displayName;
                    status = "권한 필요";
                } else if (!connected) {
                    name = displayName;
                    status = "연결 확인";
                } else {
                    name = displayName;
                    status = countAndTime(app, i);
                    if (count > 0) anyAlert = true;
                }
                String displayed = fitLine(app, name, status, sp, textBudgetDp);
                views.setTextViewText(rows[i], displayed);
                views.setTextColor(rows[i], count > 0 && connected && granted ? alert
                        : (room.isEmpty() ? muted : ink));
                views.setTextViewTextSize(rows[i], TypedValue.COMPLEX_UNIT_SP, sp);
                views.setInt(rows[i], "setGravity", gravity);
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
