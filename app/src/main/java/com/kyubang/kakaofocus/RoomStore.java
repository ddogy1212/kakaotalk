package com.kyubang.kakaofocus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Only room names, estimated counts, and observed notification TITLES are saved locally. */
final class RoomStore {
    private static final String PREF = "focus_widget_local";
    static final int ROOM_COUNT = 3;
    private RoomStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
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

    static void saveAppearance(Context c, int theme, int fontScale) {
        prefs(c).edit()
                .putInt("widget_theme", Math.max(0, Math.min(2, theme)))
                .putInt("widget_font", Math.max(0, Math.min(2, fontScale)))
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
