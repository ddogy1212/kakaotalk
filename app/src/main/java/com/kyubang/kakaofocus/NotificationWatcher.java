package com.kyubang.kakaofocus;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.Map;

/**
 * Local-only notification listener.
 * Never persists notification body text, sender messages, contact data or raw notifications.
 */
public class NotificationWatcher extends NotificationListenerService {
    private static final String KAKAO_PACKAGE = "com.kakao.talk";
    private static NotificationWatcher current;
    private final Map<String, StatusBarNotification> active = new HashMap<>();

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        current = this;
        active.clear();
        try {
            StatusBarNotification[] snapshot = getActiveNotifications();
            if (snapshot != null) {
                for (StatusBarNotification item : snapshot) {
                    if (KAKAO_PACKAGE.equals(item.getPackageName())) {
                        active.put(item.getKey(), item);
                    }
                }
            }
        } catch (Exception ignored) {
            // Service may reconnect while Android is updating notifications.
        }
        RoomStore.setConnected(this, true);
        calculate();
    }

    @Override
    public void onListenerDisconnected() {
        current = null;
        active.clear();
        RoomStore.setConnected(this, false);
        WidgetUpdater.updateAll(this);
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!KAKAO_PACKAGE.equals(sbn.getPackageName())) return;
        active.put(sbn.getKey(), sbn);
        Notification n = sbn.getNotification();
        if (n != null && n.extras != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
            String observed = value(n.extras, Notification.EXTRA_CONVERSATION_TITLE);
            if (observed.isEmpty()) observed = value(n.extras, Notification.EXTRA_TITLE);
            RoomStore.rememberTitle(this, observed);
        }
        calculate();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!KAKAO_PACKAGE.equals(sbn.getPackageName())) return;
        active.remove(sbn.getKey());
        calculate();
    }

    static void refreshConfiguredRooms() {
        NotificationWatcher watcher = current;
        if (watcher != null) watcher.calculate();
    }

    private void calculate() {
        int[] counts = new int[RoomStore.ROOM_COUNT];
        String[] rooms = new String[RoomStore.ROOM_COUNT];
        for (int i = 0; i < rooms.length; i++) rooms[i] = RoomStore.name(this, i);

        for (StatusBarNotification sbn : active.values()) {
            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null) continue;
            if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;

            Bundle data = notification.extras;
            String conversation = value(data, Notification.EXTRA_CONVERSATION_TITLE);
            String title = value(data, Notification.EXTRA_TITLE);
            String subText = value(data, Notification.EXTRA_SUB_TEXT);

            for (int i = 0; i < rooms.length; i++) {
                if (rooms[i].trim().isEmpty()) continue;
                if (sameRoom(rooms[i], conversation) ||
                        sameRoom(rooms[i], title) ||
                        sameRoom(rooms[i], subText)) {
                    counts[i] = Math.min(999, counts[i] + estimateCount(data));
                    break;
                }
            }
        }
        RoomStore.saveCounts(this, counts);
        WidgetUpdater.updateAll(this);
    }

    private static boolean sameRoom(String configured, String actual) {
        if (actual.isEmpty()) return false;
        return configured.trim().replaceAll("\\s+", " ")
                .equals(actual.trim().replaceAll("\\s+", " "));
    }

    private static String value(Bundle data, String key) {
        CharSequence text = data.getCharSequence(key);
        return text == null ? "" : text.toString().trim();
    }

    private static int estimateCount(Bundle data) {
        // A MessagingStyle notification may provide several messages in one notification.
        // This is only a display estimate; the true unread count belongs to KakaoTalk.
        Parcelable[] messages = data.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) return Math.min(99, messages.length);
        CharSequence[] lines = data.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) return Math.min(99, lines.length);
        return 1;
    }
}
