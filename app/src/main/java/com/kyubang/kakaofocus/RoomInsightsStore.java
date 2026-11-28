package com.kyubang.kakaofocus;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Private, on-device insights for configured Kakao rooms only.
 * Daily stats count matched incoming notification events, NOT unread Kakao messages.
 * Active previews come only from notifications still visible to the listener.
 */
final class RoomInsightsStore {
    private static final String PREF = "kakao_insights_private";
    private static final int DAYS_SAVED = 14;
    private static final int PREVIEW_LIMIT = 20;
    private RoomInsightsStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String dayKey(int daysAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                .format(calendar.getTime());
    }

    static boolean previewsEnabled(Context c) {
        return prefs(c).getBoolean("previews_enabled", true);
    }

    static synchronized void setPreviewsEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("previews_enabled", enabled).apply();
        if (!enabled) clearPreviews(c);
    }

    static synchronized void notePosted(Context c, int roomIndex, String eventToken) {
        if (roomIndex < 0 || roomIndex >= RoomStore.ROOM_COUNT
                || eventToken == null || eventToken.isEmpty()) return;
        SharedPreferences p = prefs(c);
        String last = p.getString("last_event_" + roomIndex, "");
        long now = System.currentTimeMillis();
        long lastAt = p.getLong("last_event_at_" + roomIndex, 0L);
        // One Kakao notification can be posted several times while Android groups it.
        if (eventToken.equals(last) && now - lastAt < 120_000L) return;
        JSONObject stats;
        try {
            stats = new JSONObject(p.getString("daily_" + roomIndex, "{}"));
        } catch (JSONException corrupted) {
            stats = new JSONObject();
        }
        String today = dayKey(0);
        try {
            stats.put(today, Math.min(100000, stats.optInt(today, 0) + 1));
            JSONObject trimmed = new JSONObject();
            for (int i = 0; i < DAYS_SAVED; i++) {
                String key = dayKey(i);
                if (stats.has(key)) trimmed.put(key, stats.optInt(key, 0));
            }
            p.edit().putString("daily_" + roomIndex, trimmed.toString())
                    .putString("last_event_" + roomIndex, eventToken)
                    .putLong("last_event_at_" + roomIndex, now).apply();
        } catch (JSONException ignored) {}
    }

    static int daily(Context c, int room, int daysAgo) {
        if (room < 0 || room >= RoomStore.ROOM_COUNT
                || daysAgo < 0 || daysAgo >= DAYS_SAVED) return 0;
        try {
            JSONObject stats = new JSONObject(prefs(c).getString("daily_" + room, "{}"));
            return Math.max(0, stats.optInt(dayKey(daysAgo), 0));
        } catch (JSONException invalid) {
            return 0;
        }
    }

    static int total(Context c, int room, int days) {
        int sum = 0;
        for (int i = 0; i < Math.min(days, DAYS_SAVED); i++)
            sum += daily(c, room, i);
        return sum;
    }

    /** Ensure snippets are replaced, not accumulated, when a notification is dismissed. */
    static synchronized void replaceActivePreviews(Context c, List<String>[] byRoom) {
        if (!previewsEnabled(c)) {
            clearPreviews(c);
            return;
        }
        SharedPreferences.Editor editor = prefs(c).edit();
        for (int room = 0; room < RoomStore.ROOM_COUNT; room++) {
            JSONArray snippets = new JSONArray();
            List<String> lines = byRoom != null && room < byRoom.length
                    ? byRoom[room] : null;
            if (lines != null) {
                for (String text : lines) {
                    String clean = cleanPreview(text);
                    if (clean.isEmpty()) continue;
                    boolean duplicated = false;
                    for (int i = 0; i < snippets.length(); i++)
                        if (clean.equals(snippets.optString(i))) duplicated = true;
                    if (!duplicated) snippets.put(clean);
                    if (snippets.length() >= PREVIEW_LIMIT) break;
                }
            }
            editor.putString("active_preview_" + room, snippets.toString());
        }
        editor.apply();
    }

    static String cleanPreview(String raw) {
        if (raw == null) return "";
        String value = raw.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        // Truncate each locally displayed preview; never save entire conversations.
        if (value.length() > 120) value = value.substring(0, 120) + "…";
        return value;
    }

    static List<String> activePreviews(Context c, int room) {
        List<String> lines = new ArrayList<>();
        if (!previewsEnabled(c) || room < 0 || room >= RoomStore.ROOM_COUNT)
            return lines;
        try {
            JSONArray data = new JSONArray(
                    prefs(c).getString("active_preview_" + room, "[]"));
            for (int i = 0; i < Math.min(PREVIEW_LIMIT, data.length()); i++) {
                String entry = cleanPreview(data.optString(i, ""));
                if (!entry.isEmpty()) lines.add(entry);
            }
        } catch (JSONException ignored) {}
        return lines;
    }

    static synchronized void clearPreviews(Context c) {
        SharedPreferences.Editor editor = prefs(c).edit();
        for (int i = 0; i < RoomStore.ROOM_COUNT; i++)
            editor.remove("active_preview_" + i);
        editor.apply();
    }

    static synchronized void clearRoom(Context c, int room) {
        if (room < 0 || room >= RoomStore.ROOM_COUNT) return;
        prefs(c).edit().remove("daily_" + room)
                .remove("last_event_" + room)
                .remove("last_event_at_" + room)
                .remove("active_preview_" + room).apply();
    }

    static synchronized void clearAll(Context c) {
        SharedPreferences p = prefs(c);
        boolean keepPreviewChoice = previewsEnabled(c);
        p.edit().clear().putBoolean("previews_enabled", keepPreviewChoice).apply();
    }
}
