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
        if (sbn == null || !KAKAO_PACKAGE.equals(sbn.getPackageName())) return;
        // A notification may be replaced or grouped very quickly. Keep metadata from
        // the posted event as well as the currently active snapshot.
        Notification n = sbn.getNotification();
        if (n != null && n.extras != null) rememberCandidates(n.extras);
        refreshSnapshot();
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

            if (active != null) {
                // Prefer individual messages to avoid double counting grouped notifications.
                for (StatusBarNotification sbn : active) {
                    Notification n = sbn.getNotification();
                    if (!KAKAO_PACKAGE.equals(sbn.getPackageName()) || n == null
                            || n.extras == null || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0)
                        continue;
                    rememberCandidates(n.extras);
                    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    matchEntry(sbn, rooms, counts, false);
                }

                // Some KakaoTalk versions put a room label only on a summary.
                // Use a summary only if no individual message matched this room.
                for (StatusBarNotification sbn : active) {
                    Notification n = sbn.getNotification();
                    if (!KAKAO_PACKAGE.equals(sbn.getPackageName()) || n == null
                            || n.extras == null
                            || (n.flags & Notification.FLAG_GROUP_SUMMARY) == 0) continue;
                    matchEntry(sbn, rooms, counts, true);
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

    private void matchEntry(StatusBarNotification sbn, String[] rooms,
                            int[] counts, boolean summaryOnly) {
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String conversation = value(e, Notification.EXTRA_CONVERSATION_TITLE);
        String subText = value(e, Notification.EXTRA_SUB_TEXT);
        String title = value(e, Notification.EXTRA_TITLE);
        String bigTitle = value(e, Notification.EXTRA_TITLE_BIG);
        String summaryText = value(e, Notification.EXTRA_SUMMARY_TEXT);

        for (int i = 0; i < RoomStore.activeRooms(this); i++) {
            if (rooms[i].isEmpty() || (summaryOnly && counts[i] > 0)) continue;
            // Android / KakaoTalk releases may choose different metadata slots for
            // a group room. Never infer a room name from the body of a message.
            boolean match = sameRoom(rooms[i], conversation)
                    || sameRoom(rooms[i], subText)
                    || sameRoom(rooms[i], title)
                    || sameRoom(rooms[i], bigTitle)
                    || sameRoom(rooms[i], summaryText);
            if (!match) continue;
            counts[i] = Math.min(999, counts[i] + estimateCount(e));
            RoomStore.noteMessageAt(this, i, sbn.getPostTime());
            break;
        }
    }

    private void rememberCandidates(Bundle e) {
        String conversation = value(e, Notification.EXTRA_CONVERSATION_TITLE);
        String subText = value(e, Notification.EXTRA_SUB_TEXT);
        String title = value(e, Notification.EXTRA_TITLE);
        String bigTitle = value(e, Notification.EXTRA_TITLE_BIG);
        String summaryText = value(e, Notification.EXTRA_SUMMARY_TEXT);

        if (!isGeneric(conversation)) RoomStore.rememberTitle(this, conversation);
        if (!isGeneric(subText)) RoomStore.rememberTitle(this, subText);
        if (!isGeneric(bigTitle)) RoomStore.rememberTitle(this, bigTitle);
        if (!isGeneric(title)) RoomStore.rememberTitle(this, title);

        String record = (e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
                    ? "[단체알림] " : "")
                + "대화명:" + show(conversation)
                + " / 보조:" + show(subText)
                + " / 제목:" + show(title)
                + (!summaryText.isEmpty() ? " / 요약:" + show(summaryText) : "");
        RoomStore.rememberNotificationMetadata(this, record);
    }

    private static boolean isGeneric(String title) {
        return title == null || title.trim().isEmpty()
                || title.equalsIgnoreCase("KakaoTalk")
                || title.equals("카카오톡");
    }

    private static String show(String value) {
        return value.isEmpty() ? "(없음)" : value;
    }

    private static String normalize(String name) {
        return name.trim().replace('\u00a0', ' ')
                .replace("\u200b", "").replace("\ufe0f", "")
                .replaceAll("\\s+", " ");
    }

    private static boolean sameRoom(String configured, String actual) {
        if (actual == null || actual.trim().isEmpty()) return false;
        String name = normalize(configured);
        String candidate = normalize(actual);
        if (name.isEmpty()) return false;
        if (name.equals(candidate)) return true;

        // Safely recognize explicit "[room]" and "Sender (room)" styles.
        if (candidate.equals("[" + name + "]")
                || candidate.equals("(" + name + ")")
                || candidate.endsWith(" (" + name + ")")
                || candidate.endsWith(" [" + name + "]")) return true;
        // Android sometimes appends a notification count to the chat title.
        return candidate.matches(java.util.regex.Pattern.quote(name) + " \\(\\d{1,4}\\)");
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
