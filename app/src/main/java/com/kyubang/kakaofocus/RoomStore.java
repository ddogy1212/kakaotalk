package com.kyubang.kakaofocus;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Collections;
import java.util.Comparator;
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

    /** Optional short widget-only label. Matching always uses name(), not displayName(). */
    static String nickname(Context c, int index) {
        return prefs(c).getString("nickname_" + index, "");
    }

    static String displayName(Context c, int index) {
        String alias = nickname(c, index).trim();
        return alias.isEmpty() ? name(c, index) : alias;
    }

    static void saveNicknames(Context c, String[] aliases) {
        SharedPreferences.Editor editor = prefs(c).edit();
        for (int i = 0; i < ROOM_COUNT; i++) {
            String value = aliases != null && i < aliases.length && aliases[i] != null
                    ? aliases[i].trim() : "";
            editor.putString("nickname_" + i, value);
        }
        editor.apply();
    }

    /** Hashed stable notification identity, never message text or raw Kakao IDs. */
    static String conversationToken(Context c, String identity) {
        if (identity == null || identity.isEmpty()) return "";
        SharedPreferences p = prefs(c);
        String salt = p.getString("conversation_salt", "");
        if (salt.isEmpty()) {
            salt = UUID.randomUUID().toString();
            p.edit().putString("conversation_salt", salt).commit();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + "|" + identity)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder token = new StringBuilder("c");
            for (int i = 0; i < 12; i++)
                token.append(String.format(java.util.Locale.ROOT, "%02x", bytes[i] & 0xff));
            return token.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return "";
        }
    }

    static String boundConversation(Context c, int index) {
        return prefs(c).getString("conversation_binding_" + index, "");
    }

    static void bindConversation(Context c, int index, String token) {
        if (index < 0 || index >= ROOM_COUNT) return;
        if (token == null) token = "";
        SharedPreferences.Editor editor = prefs(c).edit();
        // A single conversation cannot silently count towards two configured rooms.
        for (int i = 0; i < ROOM_COUNT; i++)
            if (i != index && token.equals(boundConversation(c, i)))
                editor.remove("conversation_binding_" + i);
        editor.putString("conversation_binding_" + index, token == null ? "" : token).apply();
    }

    static final class Conversation {
        final String token;
        final String label;
        final long when;
        final boolean group;
        Conversation(String token, String label, long when, boolean group) {
            this.token = token;
            this.label = label;
            this.when = when;
            this.group = group;
        }
    }

    /** Minimal on-device history of recent Kakao notification identities, no bodies. */
    static List<Conversation> recentConversations(Context c) {
        List<Conversation> found = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString("recent_conversations", "[]"));
            long cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L;
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String token = item.optString("id", "");
                String label = item.optString("name", "");
                long time = item.optLong("time", 0);
                if (!token.startsWith("c") || token.length() != 25 || time < cutoff)
                    continue;
                found.add(new Conversation(token, label, time, item.optBoolean("group", false)));
            }
        } catch (Exception ignored) {}
        Collections.sort(found, (a, b) -> Long.compare(b.when, a.when));
        return found;
    }

    static void rememberConversation(Context c, String token, String label,
                                     long when, boolean group) {
        if (token == null || token.isEmpty() || when <= 0) return;
        List<Conversation> old = recentConversations(c);
        JSONArray array = new JSONArray();
        try {
            org.json.JSONObject fresh = new org.json.JSONObject();
            fresh.put("id", token);
            fresh.put("name", label == null ? "" : label.trim().substring(
                    0, Math.min(80, label.trim().length())));
            fresh.put("time", Math.min(System.currentTimeMillis(), when));
            fresh.put("group", group);
            array.put(fresh);
            for (Conversation entry : old) {
                if (entry.token.equals(token) || array.length() >= 16) continue;
                org.json.JSONObject item = new org.json.JSONObject();
                item.put("id", entry.token);
                item.put("name", entry.label);
                item.put("time", entry.when);
                item.put("group", entry.group);
                array.put(item);
            }
            prefs(c).edit().putString("recent_conversations", array.toString()).apply();
        } catch (org.json.JSONException ignored) {}
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

    static int chatSp(Context c) {
        return clamp(prefs(c).getInt("chat_sp", 9 + fontScale(c)), 7, 18);
    }

    static int compactSp(Context c) {
        return clamp(prefs(c).getInt("compact_sp", 8), 6, 16);
    }

    static int daySp(Context c) {
        return clamp(prefs(c).getInt("day_sp", 5), 4, 12);
    }

    static int safeInsetDp(Context c) {
        return clamp(prefs(c).getInt("safe_inset_dp", 10 + inset(c) * 4), 6, 26);
    }

    static int dayTopDp(Context c) {
        return clamp(prefs(c).getInt("day_top_dp", 9), 2, 30);
    }

    static int dayEndDp(Context c) {
        return clamp(prefs(c).getInt("day_end_dp", 12), 2, 32);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static void saveWidgetDesign(Context c, int theme, int align,
                                 int chatSp, int compactSp, int daySp, int insetDp,
                                 int dayTopDp, int dayEndDp) {
        prefs(c).edit()
                .putInt("widget_theme", clamp(theme, 0, 2))
                .putInt("widget_align", clamp(align, 0, 2))
                .putInt("chat_sp", clamp(chatSp, 7, 18))
                .putInt("compact_sp", clamp(compactSp, 6, 16))
                .putInt("day_sp", clamp(daySp, 4, 12))
                .putInt("safe_inset_dp", clamp(insetDp, 6, 26))
                .putInt("day_top_dp", clamp(dayTopDp, 2, 30))
                .putInt("day_end_dp", clamp(dayEndDp, 2, 32))
                .apply();
    }

    /** Legacy backups (schema 1/2) retain their older appearance settings. */
    static void saveAppearance(Context c, int theme, int fontScale, int align, int inset) {
        int safeFont = clamp(fontScale, 0, 2);
        int safeInset = clamp(inset, 0, 2);
        prefs(c).edit()
                .putInt("widget_theme", clamp(theme, 0, 2))
                .putInt("widget_font", safeFont)
                .putInt("widget_align", clamp(align, 0, 2))
                .putInt("widget_inset", safeInset)
                .putInt("chat_sp", 9 + safeFont)
                .putInt("compact_sp", 8)
                .putInt("day_sp", 5)
                .putInt("safe_inset_dp", 10 + safeInset * 4)
                .putInt("day_top_dp", 9)
                .putInt("day_end_dp", 12)
                .apply();
    }


    /** 0 = x0/x1/x2; 1 = 0/1/2. Default preserves zero-count behavior. */
    static int countFormat(Context c) {
        return clamp(prefs(c).getInt("count_format", 0), 0, 1);
    }

    static boolean showNotificationTime(Context c) {
        return prefs(c).getBoolean("show_notification_time", true);
    }

    static String formattedCount(int count, int format) {
        return (format == 1 ? "" : "x") + Math.max(0, count);
    }

    static String formattedCount(Context c, int count) {
        return formattedCount(count, countFormat(c));
    }

    static void saveCountPresentation(Context c, int format, boolean showTime) {
        prefs(c).edit().putInt("count_format", clamp(format, 0, 1))
                .putBoolean("show_notification_time", showTime).apply();
    }

    /** Three fully editable, named local design presets (no room content). */
    static final class Preset {
        final String name;
        final int[] design;
        Preset(String name, int[] design) { this.name = name; this.design = design; }
    }

    static void savePreset(Context c, int slot, String title, int[] design) {
        if (slot < 0 || slot >= 3 || design == null || design.length != 10)
            throw new IllegalArgumentException("잘못된 프리셋 정보");
        String name = (title == null ? "" : title.trim());
        if (name.isEmpty()) name = "디자인 " + (slot + 1);
        if (name.length() > 20) name = name.substring(0, 20);
        JSONArray values = new JSONArray();
        for (int value : design) values.put(value);
        org.json.JSONObject object = new org.json.JSONObject();
        try {
            object.put("name", name);
            object.put("values", values);
            prefs(c).edit().putString("design_preset_" + slot, object.toString()).apply();
        } catch (org.json.JSONException err) {
            throw new IllegalArgumentException("프리셋 저장 실패", err);
        }
    }

    static Preset loadPreset(Context c, int slot) {
        if (slot < 0 || slot >= 3) return null;
        String raw = prefs(c).getString("design_preset_" + slot, "");
        if (raw.isEmpty()) return null;
        try {
            org.json.JSONObject object = new org.json.JSONObject(raw);
            JSONArray values = object.getJSONArray("values");
            if (values.length() != 10) return null;
            int[] design = new int[10];
            for (int i = 0; i < design.length; i++) design[i] = values.getInt(i);
            if (design[0] < 0 || design[0] > 2 || design[1] < 0 || design[1] > 2
                    || design[2] < 7 || design[2] > 18 || design[3] < 6 || design[3] > 16
                    || design[4] < 4 || design[4] > 12 || design[5] < 6 || design[5] > 26
                    || design[6] < 2 || design[6] > 30 || design[7] < 2 || design[7] > 32
                    || design[8] < 0 || design[8] > 1 || design[9] < 0 || design[9] > 1)
                return null;
            return new Preset(object.optString("name", "디자인 " + (slot + 1)), design);
        } catch (org.json.JSONException | IllegalArgumentException invalid) {
            return null;
        }
    }

    /** A short-lived, body-free trace: turns itself off after 15 minutes. */
    static void startDiagnostic(Context c) {
        prefs(c).edit().putLong("diag_started", System.currentTimeMillis())
                .putString("diag_trace", "[]").apply();
    }

    static void stopDiagnostic(Context c) {
        prefs(c).edit().remove("diag_started").apply();
    }

    static boolean isDiagnosticActive(Context c) {
        long start = prefs(c).getLong("diag_started", 0L);
        long age = System.currentTimeMillis() - start;
        return start > 0 && age >= 0 && age < 15L * 60L * 1000L;
    }

    static void traceDiagnostic(Context c, String stage, String detail) {
        if (!isDiagnosticActive(c)) return;
        try {
            JSONArray old = new JSONArray(prefs(c).getString("diag_trace", "[]"));
            JSONArray recent = new JSONArray();
            org.json.JSONObject event = new org.json.JSONObject();
            event.put("at", System.currentTimeMillis());
            event.put("stage", stage);
            event.put("detail", detail);
            recent.put(event);
            for (int i = 0; i < Math.min(39, old.length()); i++) {
                org.json.JSONObject earlier = old.optJSONObject(i);
                if (earlier != null) recent.put(earlier);
            }
            prefs(c).edit().putString("diag_trace", recent.toString()).apply();
        } catch (org.json.JSONException | RuntimeException ignored) {}
    }

    static List<String> diagnosticEvents(Context c) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("diag_trace", "[]"));
            java.text.SimpleDateFormat clock = new java.text.SimpleDateFormat(
                    "HH:mm:ss", java.util.Locale.KOREA);
            for (int i = 0; i < Math.min(40, arr.length()); i++) {
                org.json.JSONObject event = arr.optJSONObject(i);
                if (event == null) continue;
                long at = event.optLong("at", 0);
                String when = at > 0 ? clock.format(new java.util.Date(at)) : "--:--";
                result.add(when + " · " + event.optString("stage", "")
                        + " · " + event.optString("detail", ""));
            }
        } catch (org.json.JSONException ignored) {}
        return result;
    }

    static void saveRooms(Context c, String[] rooms) {
        SharedPreferences.Editor editor = prefs(c).edit();
        for (int i = 0; i < ROOM_COUNT; i++) {
            String value = (rooms[i] == null ? "" : rooms[i].trim());
            if (!value.equals(name(c, i))) {
                editor.putInt("count_" + i, 0);
                editor.putLong("last_message_" + i, 0L);
                // Never leak a previous room's alias when the tracked room changes.
                editor.putString("nickname_" + i, "");
                editor.remove("conversation_binding_" + i);
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
