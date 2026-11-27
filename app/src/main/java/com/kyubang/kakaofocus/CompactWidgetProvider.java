package com.kyubang.kakaofocus;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public final class CompactWidgetProvider extends AppWidgetProvider {
    @Override public void onEnabled(Context context) {
        RecoveryScheduler.scheduleIfWidgets(context);
        NotificationWatcher.reconnectIfNeeded(context);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RecoveryScheduler.scheduleIfWidgets(context);
        NotificationWatcher.reconnectIfNeeded(context);
        WidgetUpdater.updateAll(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                    int id, android.os.Bundle options) {
        WidgetUpdater.updateAll(context);
    }

    @Override public void onDisabled(Context context) {
        RecoveryScheduler.scheduleIfWidgets(context);
    }
}
