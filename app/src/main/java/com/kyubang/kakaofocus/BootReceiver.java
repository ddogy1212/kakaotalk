package com.kyubang.kakaofocus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reinstates optional widget health checks after the device restarts. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            RecoveryScheduler.scheduleIfWidgets(context);
            NotificationWatcher.reconnectIfNeeded(context);
            ExamDay.schedule(context);
            PauseState.scheduleExpiry(context);
            PauseWidgetProvider.updateAll(context);
            MiniChatWidgetProvider.updateAll(context);
            WidgetUpdater.updateAll(context);
        }
    }
}
