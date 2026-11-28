package com.kyubang.kakaofocus;

import android.app.Notification;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fully offline private bookmark archive. No crawling, network calls, AI, or
 * access to Kakao chat databases. Only Kakao notification text and user shares.
 */
final class LinkArchiveStore extends SQLiteOpenHelper {
    static final String[] CATEGORIES = {"동영상", "학습·문서", "기사·뉴스", "쇼핑",
            "SNS·커뮤니티", "지도·장소", "개발·기술", "기타"};
    private static final Pattern LINK = Pattern.compile(
            "(?i)(?:https?://|www\\.)[^\\s<>\"'\\u0000-\\u001F]{3,}");
    private static final int MAX_EXTRACT = 64;

    static final class Entry {
        final long id, added;
        final String url, title, category, source, note;
        final boolean favorite;
        Entry(long id, String url, String title, String category,
              String source, String note, long added, boolean favorite) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.category = category;
            this.source = source;
            this.note = note;
            this.added = added;
            this.favorite = favorite;
        }
    }

    LinkArchiveStore(Context context) {
        super(context.getApplicationContext(), "kakao_links_v1.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS links (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "url TEXT NOT NULL UNIQUE, " +
                "title TEXT NOT NULL, category TEXT NOT NULL, " +
                "source TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', " +
                "added INTEGER NOT NULL, favorite INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_link_recent ON links(added DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_link_favorites ON links(favorite)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int old, int now) {}

    static String normalize(String input) {
        if (input == null) return "";
        String raw = input.trim();
        while (!raw.isEmpty()) {
            char last = raw.charAt(raw.length() - 1);
            if (".,;:!?。！，；：…>'\"".indexOf(last) >= 0) {
                raw = raw.substring(0, raw.length() - 1);
                continue;
            }
            if (last == ')' && count(raw, '(') < count(raw, ')')) {
                raw = raw.substring(0, raw.length() - 1);
                continue;
            }
            if (last == ']' && count(raw, '[') < count(raw, ']')) {
                raw = raw.substring(0, raw.length() - 1);
                continue;
            }
            if (last == '}' && count(raw, '{') < count(raw, '}')) {
                raw = raw.substring(0, raw.length() - 1);
                continue;
            }
            break;
        }
        if (raw.startsWith("www.")) raw = "https://" + raw;
        if (!(raw.startsWith("http://") || raw.startsWith("https://")
                || raw.startsWith("HTTP://") || raw.startsWith("HTTPS://"))) return "";
        if (raw.length() > 2048) return "";
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return "";
            if (scheme == null || (!scheme.equalsIgnoreCase("http")
                    && !scheme.equalsIgnoreCase("https"))) return "";
            if (uri.getRawUserInfo() != null) return "";
            int port = uri.getPort();
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            String result = scheme.toLowerCase(Locale.ROOT) + "://"
                    + host.toLowerCase(Locale.ROOT)
                    + (port < 0 ? "" : ":" + port)
                    + (path == null || path.isEmpty() ? "/" : path)
                    + (query == null ? "" : "?" + query);
            return result.length() <= 2048 ? result : "";
        } catch (Exception ignored) { return ""; }
    }

    private static int count(String text, char ch) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == ch) n++;
        return n;
    }

    static List<String> findLinks(String text) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return new ArrayList<>(found);
        Matcher match = LINK.matcher(text.substring(0, Math.min(text.length(), 40000)));
        while (match.find() && found.size() < MAX_EXTRACT) {
            String url = normalize(match.group());
            if (!url.isEmpty()) found.add(url);
        }
        return new ArrayList<>(found);
    }

    static String classify(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        Uri u = Uri.parse(url);
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("youtube.com") || host.equals("youtu.be")
                || host.contains("vimeo.com") || host.contains("tiktok.com")
                || host.contains("twitch.tv") || host.contains("netflix.com"))
            return "동영상";
        if (host.contains("docs.google") || host.contains("drive.google")
                || host.contains("classroom.google") || host.contains("notion.")
                || host.contains("quizlet.") || host.contains("scholar.google")
                || host.contains("arxiv.org") || host.contains("ebsi.") || host.contains("school")
                || lower.endsWith(".pdf") || lower.contains(".pdf?")
                || lower.endsWith(".pptx") || lower.endsWith(".docx"))
            return "학습·문서";
        if (host.contains("news.") || host.contains("yna.co.kr")
                || host.contains("bbc.") || host.contains("cnn.com")
                || host.contains("reuters.com") || host.contains("chosun.com")
                || host.contains("hani.co.kr") || host.contains("donga.com")
                || host.contains("pressian.com") || lower.contains("/article/"))
            return "기사·뉴스";
        if (host.contains("coupang.") || host.contains("amazon.")
                || host.contains("shopping.") || host.contains("smartstore.")
                || host.contains("aliexpress.") || host.contains("gmarket.")
                || host.contains("11st.") || host.contains("musinsa.")
                || host.contains("oliveyoung."))
            return "쇼핑";
        if (host.contains("instagram.") || host.contains("facebook.")
                || host.contains("reddit.") || host.contains("x.com")
                || host.contains("twitter.") || host.contains("threads.")
                || host.contains("discord.") || host.contains("cafe.naver")
                || host.contains("blog.naver") || host.contains("tistory."))
            return "SNS·커뮤니티";
        if (host.contains("maps.") || host.contains("map.")
                || host.contains("place.") || host.contains("kakao.com")
                || host.contains("naver.me") || host.contains("goo.gl/maps"))
            return "지도·장소";
        if (host.contains("github.") || host.contains("stackoverflow.")
                || host.contains("developer.") || host.contains("gitlab.")
                || host.contains("huggingface.") || host.contains("colab.")
                || host.contains("pypi.org"))
            return "개발·기술";
        return "기타";
    }

    static String suggestTitle(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return "저장한 링크";
            String domain = host.startsWith("www.") ? host.substring(4) : host;
            String category = classify(url);
            String prefix = category.equals("동영상") ? "영상"
                    : category.equals("학습·문서") ? "문서·학습"
                    : category.equals("기사·뉴스") ? "뉴스"
                    : category.equals("쇼핑") ? "상품"
                    : category.equals("지도·장소") ? "장소"
                    : category.equals("개발·기술") ? "개발 자료"
                    : category.equals("SNS·커뮤니티") ? "게시물" : "웹 링크";
            return prefix + " · " + domain;
        } catch (Exception ignored) { return "저장한 링크"; }
    }

    int saveText(String text, String source) {
        List<String> links = findLinks(text);
        if (links.isEmpty()) return 0;
        int inserted = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String url : links) {
                android.content.ContentValues row = new android.content.ContentValues();
                row.put("url", url);
                row.put("title", suggestTitle(url));
                row.put("category", classify(url));
                row.put("source", truncate(source, 65));
                row.put("note", "");
                row.put("added", System.currentTimeMillis());
                row.put("favorite", 0);
                if (db.insertWithOnConflict("links", null, row,
                        SQLiteDatabase.CONFLICT_IGNORE) != -1L) inserted++;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return inserted;
    }

    static String truncate(String text, int length) {
        if (text == null) return "";
        String raw = text.trim().replace('\n', ' ').replace('\r', ' ');
        return raw.length() > length ? raw.substring(0, length) + "…" : raw;
    }

    int saveNotification(Notification n, String chatName) {
        if (n == null || n.extras == null) return 0;
        Bundle x = n.extras;
        StringBuilder all = new StringBuilder();
        append(all, x.getCharSequence(Notification.EXTRA_TEXT));
        append(all, x.getCharSequence(Notification.EXTRA_BIG_TEXT));
        append(all, x.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence[] lines = x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) append(all, line);
        try {
            Parcelable[] raw = x.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (raw != null) {
                List<Notification.MessagingStyle.Message> messages =
                        Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw);
                for (Notification.MessagingStyle.Message m : messages)
                    if (m != null) append(all, m.getText());
            }
        } catch (RuntimeException ignored) {}
        return saveText(all.toString(),
                "카톡 알림" + (chatName == null || chatName.isEmpty()
                        ? "" : " · " + truncate(chatName, 45)));
    }

    private static void append(StringBuilder out, CharSequence text) {
        if (text != null && out.length() < 100000) out.append(text).append('\n');
    }

    List<Entry> search(String query, String category, boolean onlyFavorites,
                       int limit, int offset) {
        StringBuilder where = new StringBuilder("1=1");
        ArrayList<String> args = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            where.append(" AND (title LIKE ? OR url LIKE ? OR source LIKE ? OR category LIKE ? OR note LIKE ?)");
            String escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            // Since LIKE has no ESCAPE by default, use literal substring as supplied.
            String pattern = "%" + query.trim() + "%";
            for (int i = 0; i < 5; i++) args.add(pattern);
        }
        if (category != null && !category.isEmpty() && !category.equals("전체")) {
            where.append(" AND category=?");
            args.add(category);
        }
        if (onlyFavorites) where.append(" AND favorite=1");
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("links",
                new String[]{"_id", "url", "title", "category", "source",
                        "note", "added", "favorite"},
                where.toString(), args.toArray(new String[0]), null, null,
                "favorite DESC, added DESC", Math.max(1, limit) + " OFFSET " + Math.max(0, offset))) {
            while (c.moveToNext()) result.add(new Entry(c.getLong(0), c.getString(1),
                    c.getString(2), c.getString(3), c.getString(4),
                    c.getString(5), c.getLong(6), c.getInt(7) != 0));
        }
        return result;
    }

    int total() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM links", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    int favorites() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM links WHERE favorite=1", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    boolean update(long id, String title, String category, String note) {
        android.content.ContentValues row = new android.content.ContentValues();
        row.put("title", truncate(title, 140));
        row.put("category", category);
        row.put("note", truncate(note, 300));
        return getWritableDatabase().update("links", row, "_id=?",
                new String[]{Long.toString(id)}) > 0;
    }

    boolean favorite(long id, boolean favorite) {
        android.content.ContentValues row = new android.content.ContentValues();
        row.put("favorite", favorite ? 1 : 0);
        return getWritableDatabase().update("links", row, "_id=?",
                new String[]{Long.toString(id)}) > 0;
    }

    boolean delete(long id) {
        return getWritableDatabase().delete("links", "_id=?",
                new String[]{Long.toString(id)}) > 0;
    }
}
