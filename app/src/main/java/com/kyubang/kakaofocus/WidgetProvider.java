package com.kyubang.kakaofocus;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class WidgetProvider extends AppWidgetProvider {
    @Override public void onEnabled(Context context) {
        RecoveryScheduler.scheduleIfWidgets(context);
        ExamDay.schedule(context);
        NotificationWatcher.reconnectIfNeeded(context);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RecoveryScheduler.scheduleIfWidgets(context);
        ExamDay.schedule(context);
        NotificationWatcher.reconnectIfNeeded(context);
        WidgetUpdater.updateAll(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, android.os.Bundle options) {
        WidgetUpdater.updateAll(context);
    }

    @Override public void onDisabled(Context context) {
        RecoveryScheduler.scheduleIfWidgets(context);
        ExamDay.cancel(context);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (ExamDay.ACTION_MIDNIGHT.equals(intent.getAction())) {
            WidgetUpdater.updateAll(context);
            ExamDay.schedule(context);
            return;
        }
        if (RecoveryScheduler.ACTION_CHECK.equals(intent.getAction())) {
            // This is a best-effort rebind request, not an unrestricted background service.
            NotificationWatcher.reconnectIfNeeded(context);
            WidgetUpdater.updateAll(context);
            return;
        }
        super.onReceive(context, intent);
    }
}
