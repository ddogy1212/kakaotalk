package com.kyubang.kakaofocus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Only room names, estimated counts, and observed notification TITLES are saved locally. */
final class RoomStore {
    private static final String PREF = "focus_widget_local";
    static final int ROOM_COUNT = 4;
    private RoomStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static int activeRooms(Context c) {
        return Math.max(1, Math.min(ROOM_COUNT, prefs(c).getInt("active_rooms", 3)));
    }

    static void saveActiveRooms(Context c, int value) {
        prefs(c).edit().putInt("active_rooms", Math.max(1, Math.min(ROOM_COUNT, value))).apply();
    }

    static String name(Context c, int index) {
        return prefs(c).getString("room_" + index, "");
    }

    static int count(Context c, int index) {
        return prefs(c).getInt("count_" + index, 0);
    }

    static boolean connected(Context c) {
        return prefs(c).getBoolean("listener_connected", false);
    }

    static void setConnected(Context c, boolean connected) {
        prefs(c).edit().putBoolean("listener_connected", connected).apply();
    }

    static long lastMessageAt(Context c, int index) {
        return prefs(c).getLong("last_message_" + index, 0L);
    }

    static void noteMessageAt(Context c, int index, long timestamp) {
        if (timestamp <= 0) return;
        long old = lastMessageAt(c, index);
        if (timestamp > old && timestamp <= System.currentTimeMillis() + 60000L) {
            prefs(c).edit().putLong("last_message_" + index, timestamp).apply();
        }
    }

    static long lastSuccessfulScan(Context c) {
        return prefs(c).getLong("last_scan", 0L);
    }

    static int theme(Context c) {
        return Math.max(0, Math.min(2, prefs(c).getInt("widget_theme", 0)));
    }

    static int fontScale(Context c) {
        return Math.max(0, Math.min(2, prefs(c).getInt("widget_font", 1)));
    }

    static int align(Context c) {
        return Math.max(0, Math.min(2, prefs(c).getInt("widget_align", 0)));
    }

    /** 0 = 10dp, 1 = 14dp, 2 = 18dp. Default to middle for rounded LockStar corners. */
    static int inset(Context c) {
        return Math.max(0, Math.min(2, prefs(c).getInt("widget_inset", 1)));
    }

    static void saveAppearance(Context c, int theme, int fontScale, int align, int inset) {
        prefs(c).edit()
                .putInt("widget_theme", Math.max(0, Math.min(2, theme)))
                .putInt("widget_font", Math.max(0, Math.min(2, fontScale)))
                .putInt("widget_align", Math.max(0, Math.min(2, align)))
                .putInt("widget_inset", Math.max(0, Math.min(2, inset)))
                .apply();
    }

    static void saveRooms(Context c, String[] rooms) {
        SharedPreferences.Editor editor = prefs(c).edit();
        for (int i = 0; i < ROOM_COUNT; i++) {
            String value = (rooms[i] == null ? "" : rooms[i].trim());
            if (!value.equals(name(c, i))) {
                editor.putInt("count_" + i, 0);
                editor.putLong("last_message_" + i, 0L);
            }
            editor.putString("room_" + i, value);
        }
        editor.apply();
    }

    static void saveCounts(Context c, int[] counts) {
        SharedPreferences.Editor editor = prefs(c).edit();
        for (int i = 0; i < ROOM_COUNT; i++) {
            editor.putInt("count_" + i, Math.max(0, counts[i]));
        }
        editor.putLong("last_scan", System.currentTimeMillis());
        editor.apply();
    }

    static List<String> notificationMetadata(Context c) {
        List<String> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString("notification_metadata", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) entries.add(value);
            }
        } catch (Exception ignored) {}
        return entries;
    }

    /** Store metadata labels only: never a message body, sender message or conversation text. */
    static void rememberNotificationMetadata(Context c, String summary) {
        if (summary == null || summary.trim().isEmpty()) return;
        String line = summary.trim().replace("\\n", " ").replace("\\r", " ");
        List<String> recent = notificationMetadata(c);
        recent.remove(line);
        recent.add(0, line);
        JSONArray data = new JSONArray();
        for (int i = 0; i < Math.min(12, recent.size()); i++) data.put(recent.get(i));
        prefs(c).edit().putString("notification_metadata", data.toString()).apply();
    }

    static List<String> observedTitles(Context c) {
        List<String> titles = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString("observed", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String title = array.optString(i, "");
                if (!title.isEmpty()) titles.add(title);
            }
        } catch (Exception ignored) {
            // Recover from corrupted local preference state.
        }
        return titles;
    }

    static void rememberTitle(Context c, String value) {
        if (value == null) return;
        String title = value.trim().replace("\n", " ").replace("\r", " ");
        if (title.isEmpty()) return;
        List<String> existing = observedTitles(c);
        existing.remove(title);
        existing.add(0, title);
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(6, existing.size()); i++) {
            array.put(existing.get(i));
        }
        prefs(c).edit().putString("observed", array.toString()).apply();
    }
}
