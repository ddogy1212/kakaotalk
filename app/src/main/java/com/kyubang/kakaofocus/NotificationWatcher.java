package com.kyubang.kakaofocus;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.LinkedHashSet;

/** Only notification metadata, never message bodies, is stored or matched. */
public class NotificationWatcher extends NotificationListenerService {
    private static final String KAKAO_PACKAGE = "com.kakao.talk";
    private static final String HIDDEN_TITLE = "android.hiddenConversationTitle";
    private static volatile NotificationWatcher current;
    private static volatile long lastRebindElapsed;

    static boolean isConnected() {
        return current != null;
    }

    static boolean refreshConfiguredRooms() {
        NotificationWatcher listener = current;
        if (listener == null) return false;
        listener.refreshSnapshot();
        return isConnected();
    }

    static boolean reconnectIfNeeded(Context context) {
        if (refreshConfiguredRooms()) return true;
        if (!WidgetUpdater.hasAccess(context)) return false;
        long now = SystemClock.elapsedRealtime();
        if (lastRebindElapsed != 0 && now - lastRebindElapsed < 30_000L) return false;
        lastRebindElapsed = now;
        try {
            requestRebind(new ComponentName(context, NotificationWatcher.class));
        } catch (RuntimeException ignored) {}
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
        } catch (RuntimeException ignored) {}
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
        try {
            rememberNotification(sbn);
        } catch (RuntimeException ignored) {
            // A malformed notification must not disconnect the whole listener.
        }
        refreshSnapshot();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && KAKAO_PACKAGE.equals(sbn.getPackageName())) refreshSnapshot();
    }

    private void refreshSnapshot() {
        if (current != this) return;
        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (RuntimeException failed) {
            // Temporarily inaccessible snapshot: preserve last known counts.
            RoomStore.setConnected(this, false);
            WidgetUpdater.updateAll(this);
            return;
        }
        int[] counts = new int[RoomStore.ROOM_COUNT];
        String[] rooms = new String[RoomStore.ROOM_COUNT];
        for (int i = 0; i < rooms.length; i++) rooms[i] = RoomStore.name(this, i);
        if (active != null) {
            for (StatusBarNotification sbn : active) {
                if (!isEligible(sbn, false)) continue;
                try {
                    rememberNotification(sbn);
                    matchEntry(sbn, rooms, counts, false);
                } catch (RuntimeException ignored) {
                    // A single bad notification must not hide all Kakao group chats.
                }
            }
            // Only fall back to summary notifications for rooms with no child match.
            for (StatusBarNotification sbn : active) {
                if (!isEligible(sbn, true)) continue;
                try {
                    matchEntry(sbn, rooms, counts, true);
                } catch (RuntimeException ignored) {}
            }
        }
        RoomStore.saveCounts(this, counts);
        RoomStore.setConnected(this, true);
        WidgetUpdater.updateAll(this);
    }

    private static boolean isEligible(StatusBarNotification sbn, boolean summary) {
        if (sbn == null || !KAKAO_PACKAGE.equals(sbn.getPackageName())) return false;
        Notification n = sbn.getNotification();
        return n != null && n.extras != null
                && (n.flags & Notification.FLAG_ONGOING_EVENT) == 0
                && ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) == summary;
    }

    private ShortcutInfo conversationShortcut(StatusBarNotification sbn) {
        if (Build.VERSION.SDK_INT < 31) return null;
        try {
            RankingMap rankingMap = getCurrentRanking();
            Ranking rank = new Ranking();
            if (rankingMap != null && rankingMap.getRanking(sbn.getKey(), rank))
                return rank.getConversationShortcutInfo();
        } catch (RuntimeException ignored) {}
        return null;
    }

    /** Select the most specific per-conversation ID. Never use a shared group key. */
    private String rawIdentity(StatusBarNotification sbn, ShortcutInfo shortcut) {
        Notification n = sbn.getNotification();
        if (n == null) return "";
        if (shortcut != null && shortcut.getId() != null
                && !shortcut.getId().isEmpty()) return "sc:" + shortcut.getId();
        String shortcutId = n.getShortcutId();
        if (shortcutId != null && !shortcutId.isEmpty()) return "sc:" + shortcutId;
        if (n.extras != null) {
            Object thread = n.extras.get("threadId");
            if (thread instanceof Number && ((Number) thread).longValue() > 0)
                return "th:" + thread.toString();
            if (thread instanceof String && !((String) thread).isEmpty())
                return "th:" + thread;
        }
        String tag = sbn.getTag();
        if (tag != null && !tag.isEmpty() && !tag.equals("chat_message"))
            return "tag:" + tag;
        return "";
    }

    private LinkedHashSet<String> potentialRoomNames(StatusBarNotification sbn,
                                                       ShortcutInfo shortcut) {
        Bundle e = sbn.getNotification().extras;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (shortcut != null && shortcut.getShortLabel() != null)
            addRoomName(names, shortcut.getShortLabel().toString());
        addRoomName(names, value(e, Notification.EXTRA_CONVERSATION_TITLE));
        addRoomName(names, value(e, HIDDEN_TITLE));
        addRoomName(names, value(e, Notification.EXTRA_SUB_TEXT));
        addRoomName(names, value(e, Notification.EXTRA_TITLE_BIG));
        addRoomName(names, value(e, Notification.EXTRA_SUMMARY_TEXT));
        // In a group, android.title often contains only the sender's name.
        // Treat sender titles as room names only for non-group notifications.
        if (!e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false))
            addRoomName(names, value(e, Notification.EXTRA_TITLE));
        return names;
    }

    private static void addRoomName(LinkedHashSet<String> names, String value) {
        if (!isGeneric(value)) names.add(value);
    }

    private void rememberNotification(StatusBarNotification sbn) {
        if (!isEligible(sbn, false)) return;
        Notification n = sbn.getNotification();
        ShortcutInfo shortcut = conversationShortcut(sbn);
        LinkedHashSet<String> candidates = potentialRoomNames(sbn, shortcut);
        for (String candidate : candidates) RoomStore.rememberTitle(this, candidate);

        String conversation = value(n.extras, Notification.EXTRA_CONVERSATION_TITLE);
        String hidden = value(n.extras, HIDDEN_TITLE);
        String title = value(n.extras, Notification.EXTRA_TITLE);
        String subText = value(n.extras, Notification.EXTRA_SUB_TEXT);
        boolean group = n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
                || !conversation.isEmpty() || !hidden.isEmpty();
        String token = RoomStore.conversationToken(this, rawIdentity(sbn, shortcut));
        String label = candidates.isEmpty() ? "" : candidates.iterator().next();
        if (!token.isEmpty())
            RoomStore.rememberConversation(this, token, label,
                    Math.max(1, sbn.getPostTime()), group);
        String note = (group ? "[단톡] " : "[알림] ")
                + "대화명:" + show(conversation.isEmpty() ? hidden : conversation)
                + " / 보조:" + show(subText)
                + " / 알림제목:" + show(title)
                + " / 대화식별자:" + (token.isEmpty() ? "미제공" : "확인됨");
        RoomStore.rememberNotificationMetadata(this, note);
    }

    private void matchEntry(StatusBarNotification sbn, String[] rooms,
                            int[] counts, boolean summaryOnly) {
        Notification n = sbn.getNotification();
        ShortcutInfo shortcut = conversationShortcut(sbn);
        LinkedHashSet<String> candidates = potentialRoomNames(sbn, shortcut);
        String token = RoomStore.conversationToken(this, rawIdentity(sbn, shortcut));
        for (int i = 0; i < RoomStore.activeRooms(this); i++) {
            if (rooms[i].isEmpty() || (summaryOnly && counts[i] > 0)) continue;
            String binding = RoomStore.boundConversation(this, i);
            boolean match = !token.isEmpty() && token.equals(binding);
            if (!match) for (String candidate : candidates) {
                if (sameRoom(rooms[i], candidate)) {
                    match = true;
                    break;
                }
            }
            if (!match) continue;
            counts[i] = Math.min(999, counts[i] + estimateCount(n.extras));
            RoomStore.noteMessageAt(this, i, sbn.getPostTime());
            break;
        }
    }

    private static boolean isGeneric(String title) {
        return title == null || title.trim().isEmpty()
                || title.equalsIgnoreCase("KakaoTalk") || title.equals("카카오톡");
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
        if (candidate.equals("[" + name + "]")
                || candidate.equals("(" + name + ")")
                || candidate.endsWith(" (" + name + ")")
                || candidate.endsWith(" [" + name + "]")) return true;
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
