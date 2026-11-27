package com.kyubang.kakaofocus;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationWatcher extends NotificationListenerService {
    private static final String KAKAO_PACKAGE = "com.kakao.talk";
    private static volatile NotificationWatcher current;
    private static volatile long lastRebindElapsed;

    static boolean isConnected() {
        return current != null;
    }

    /** Refresh immediately if the system has connected our listener. */
    static boolean refreshConfiguredRooms() {
        NotificationWatcher listener = current;
        if (listener == null) return false;
        listener.refreshSnapshot();
        return isConnected();
    }

    /** System-owned notification listeners cannot be started as regular services. */
    static boolean reconnectIfNeeded(Context context) {
        if (refreshConfiguredRooms()) return true;
        if (!WidgetUpdater.hasAccess(context)) return false;
        long now = SystemClock.elapsedRealtime();
        if (lastRebindElapsed != 0 && now - lastRebindElapsed < 30_000L) return false;
        lastRebindElapsed = now;
        try {
            requestRebind(new ComponentName(context, NotificationWatcher.class));
        } catch (RuntimeException ignored) {
            // The platform may decline the request. The settings screen can help.
        }
        return false;
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        current = this;
        lastRebindElapsed = 0;
        RoomStore.setConnected(this, true);
        refreshSnapshot();
    }

    @Override public void onListenerDisconnected() {
        if (current == this) current = null;
        RoomStore.setConnected(this, false);
        WidgetUpdater.updateAll(this);
        try {
            requestRebind(new ComponentName(this, NotificationWatcher.class));
        } catch (RuntimeException ignored) {
            // A new bind is best-effort and depends on Android.
        }
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        if (current == this) current = null;
        RoomStore.setConnected(this, false);
        WidgetUpdater.updateAll(this);
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && KAKAO_PACKAGE.equals(sbn.getPackageName())) refreshSnapshot();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && KAKAO_PACKAGE.equals(sbn.getPackageName())) refreshSnapshot();
    }

    private void refreshSnapshot() {
        if (current != this) return;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            int[] counts = new int[RoomStore.ROOM_COUNT];
            String[] rooms = new String[RoomStore.ROOM_COUNT];
            for (int i = 0; i < rooms.length; i++) rooms[i] = RoomStore.name(this, i);
            if (active != null) for (StatusBarNotification sbn : active) {
                if (!KAKAO_PACKAGE.equals(sbn.getPackageName())) continue;
                Notification notification = sbn.getNotification();
                if (notification == null || notification.extras == null) continue;
                if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;

                Bundle extras = notification.extras;
                String conversation = value(extras, Notification.EXTRA_CONVERSATION_TITLE);
                String title = value(extras, Notification.EXTRA_TITLE);
                String subText = value(extras, Notification.EXTRA_SUB_TEXT);
                String observed = !conversation.isEmpty() ? conversation
                        : (!subText.isEmpty() && !"KakaoTalk".equalsIgnoreCase(subText)
                            && !"카카오톡".equals(subText)) ? subText : title;
                if (!observed.isEmpty()) RoomStore.rememberTitle(this, observed);

                for (int i = 0; i < RoomStore.activeRooms(this); i++) {
                    if (rooms[i].isEmpty()) continue;
                    boolean match;
                    if (!conversation.isEmpty()) {
                        match = sameRoom(rooms[i], conversation);
                    } else {
                        match = sameRoom(rooms[i], title) || sameRoom(rooms[i], subText);
                    }
                    if (match) {
                        counts[i] = Math.min(999, counts[i] + estimateCount(extras));
                        // Keep only the most recent notification timestamp, never message text.
                        RoomStore.noteMessageAt(this, i, sbn.getPostTime());
                        break;
                    }
                }
            }
            RoomStore.saveCounts(this, counts);
            RoomStore.setConnected(this, true);
            WidgetUpdater.updateAll(this);
        } catch (RuntimeException problem) {
            if (current == this) current = null;
            RoomStore.setConnected(this, false);
            WidgetUpdater.updateAll(this);
            try {
                requestRebind(new ComponentName(this, NotificationWatcher.class));
            } catch (RuntimeException ignored) {}
        }
    }

    private static boolean sameRoom(String configured, String actual) {
        return actual != null && !actual.isEmpty()
                && configured.trim().replaceAll("\\s+", " ")
                    .equals(actual.trim().replaceAll("\\s+", " "));
    }

    private static String value(Bundle extras, String key) {
        CharSequence text = extras.getCharSequence(key);
        return text == null ? "" : text.toString().trim();
    }

    private static int estimateCount(Bundle extras) {
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) return Math.min(99, messages.length);
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) return Math.min(99, lines.length);
        return 1;
    }
}
