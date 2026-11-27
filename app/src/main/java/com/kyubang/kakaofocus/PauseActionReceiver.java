package com.kyubang.kakaofocus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Unexported: host can invoke the immutable PendingIntent but other apps cannot send it. */
public final class PauseActionReceiver extends BroadcastReceiver {
    static final String ACTION_START = "com.kyubang.kakaofocus.START_TEN_MIN_PAUSE";
    static final String ACTION_EXPIRE = "com.kyubang.kakaofocus.EXPIRE_TEN_MIN_PAUSE";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_START.equals(intent.getAction())) {
            PauseState.startTenMinutes(context);
        } else if (ACTION_EXPIRE.equals(intent.getAction())) {
            PauseState.clearIfExpired(context);
        }
    }
}
