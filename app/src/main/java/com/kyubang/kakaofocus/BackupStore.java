package com.kyubang.kakaofocus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Explicit, user-selected, offline ZIP backup of settings and one crop photo.
 * Never exports notifications, message bodies, listener permissions or pause state.
 */
final class BackupStore {
    private static final String SETTINGS = "settings.json";
    private static final String PHOTO = "pause_photo.jpg";
    private static final int MAX_SETTINGS = 64 * 1024;
    private static final int MAX_PHOTO = 3 * 1024 * 1024;
    private BackupStore() {}

    static void exportTo(Context context, Uri destination) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("format", "kakao-focus-backup");
            json.put("schema", 3);
            json.put("activeRooms", RoomStore.activeRooms(context));
            JSONArray rooms = new JSONArray();
            for (int i = 0; i < RoomStore.ROOM_COUNT; i++)
                rooms.put(RoomStore.name(context, i));
            json.put("rooms", rooms);
            JSONArray nicknames = new JSONArray();
            for (int i = 0; i < RoomStore.ROOM_COUNT; i++)
                nicknames.put(RoomStore.nickname(context, i));
            json.put("nicknames", nicknames);
            json.put("theme", RoomStore.theme(context));
            json.put("fontScale", RoomStore.fontScale(context));
            json.put("align", RoomStore.align(context));
            json.put("inset", RoomStore.inset(context));
            json.put("pauseMinutes", PauseState.durationMinutes(context));
            json.put("pauseDisplayMode", PauseState.displayMode(context));
            json.put("chatSp", RoomStore.chatSp(context));
            json.put("compactSp", RoomStore.compactSp(context));
            json.put("daySp", RoomStore.daySp(context));
            json.put("safeInsetDp", RoomStore.safeInsetDp(context));
            json.put("dayTopDp", RoomStore.dayTopDp(context));
            json.put("dayEndDp", RoomStore.dayEndDp(context));
        } catch (JSONException impossible) {
            throw new IOException("설정 파일을 준비하지 못했어.", impossible);
        }

        OutputStream target = context.getContentResolver().openOutputStream(destination, "w");
        if (target == null) throw new IOException("선택한 파일을 쓸 수 없어.");
        try (OutputStream stream = target; ZipOutputStream zip = new ZipOutputStream(stream)) {
            zip.putNextEntry(new ZipEntry(SETTINGS));
            byte[] config = json.toString(2).getBytes(StandardCharsets.UTF_8);
            if (config.length > MAX_SETTINGS) throw new IOException("설정 데이터가 너무 커.");
            zip.write(config);
            zip.closeEntry();

            File photo = new File(context.getFilesDir(), "pause_widget_photo.jpg");
            if (photo.exists()) {
                if (photo.length() > MAX_PHOTO) throw new IOException("위젯 사진이 너무 커.");
                zip.putNextEntry(new ZipEntry(PHOTO));
                try (FileInputStream input = new FileInputStream(photo)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = input.read(buf)) != -1) zip.write(buf, 0, n);
                }
                zip.closeEntry();
            }
        } catch (JSONException ex) {
            throw new IOException("설정 데이터를 인코딩할 수 없어.", ex);
        }
    }

    static void restoreFrom(Context context, Uri source) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(source);
        if (input == null) throw new IOException("선택한 파일을 읽을 수 없어.");
        byte[] settings = null, photo = null;
        int entries = 0;
        try (InputStream sourceStream = input; ZipInputStream zip = new ZipInputStream(sourceStream)) {
            ZipEntry item;
            while ((item = zip.getNextEntry()) != null) {
                if (++entries > 8) throw new IOException("백업 항목이 너무 많아.");
                if (item.isDirectory()) continue;
                String entryName = item.getName();
                if (SETTINGS.equals(entryName)) {
                    if (settings != null) throw new IOException("설정 파일이 중복됐어.");
                    settings = readLimited(zip, MAX_SETTINGS);
                } else if (PHOTO.equals(entryName)) {
                    if (photo != null) throw new IOException("사진이 중복됐어.");
                    photo = readLimited(zip, MAX_PHOTO);
                } else {
                    throw new IOException("알 수 없는 파일이 포함된 백업이야.");
                }
                zip.closeEntry();
            }
        } catch (RuntimeException problem) {
            throw new IOException("파일을 정상적으로 읽지 못했어.", problem);
        }
        if (settings == null) throw new IOException("설정 파일이 없는 백업이야.");

        String[] rooms = new String[RoomStore.ROOM_COUNT];
        String[] aliases = new String[RoomStore.ROOM_COUNT];
        final int active, theme, font, align, inset, minutes, mode, schema;
        final int chatSp, compactSp, daySp, insetDp, dayTopDp, dayEndDp;
        try {
            JSONObject json = new JSONObject(new String(settings, StandardCharsets.UTF_8));
            schema = json.getInt("schema");
            if (!"kakao-focus-backup".equals(json.optString("format"))
                    || (schema != 1 && schema != 2 && schema != 3))
                throw new IOException("지원하지 않는 백업 형식이야.");
            JSONArray array = json.getJSONArray("rooms");
            if (array.length() != RoomStore.ROOM_COUNT)
                throw new IOException("저장된 톡방 수가 올바르지 않아.");
            for (int i = 0; i < rooms.length; i++) {
                rooms[i] = array.getString(i).trim();
                if (rooms[i].length() > 300) throw new IOException("톡방 이름이 너무 길어.");
            }
            if (schema >= 2) {
                JSONArray storedAliases = json.getJSONArray("nicknames");
                if (storedAliases.length() != RoomStore.ROOM_COUNT)
                    throw new IOException("별명 설정 개수가 올바르지 않아.");
                for (int i = 0; i < aliases.length; i++) {
                    aliases[i] = storedAliases.getString(i).trim();
                    if (aliases[i].codePointCount(0, aliases[i].length()) > 12)
                        throw new IOException("별명이 너무 길어.");
                }
            } else {
                // Backward compatible with previous v2.1/v2.0 backup archives.
                for (int i = 0; i < aliases.length; i++) aliases[i] = "";
            }
            active = json.getInt("activeRooms");
            theme = json.getInt("theme");
            font = json.getInt("fontScale");
            align = json.getInt("align");
            inset = json.getInt("inset");
            minutes = json.getInt("pauseMinutes");
            mode = json.getInt("pauseDisplayMode");
            if (schema == 3) {
                chatSp = json.getInt("chatSp");
                compactSp = json.getInt("compactSp");
                daySp = json.getInt("daySp");
                insetDp = json.getInt("safeInsetDp");
                dayTopDp = json.getInt("dayTopDp");
                dayEndDp = json.getInt("dayEndDp");
                if (chatSp < 7 || chatSp > 18 || compactSp < 6 || compactSp > 16
                        || daySp < 4 || daySp > 12 || insetDp < 6 || insetDp > 26
                        || dayTopDp < 2 || dayTopDp > 30 || dayEndDp < 2 || dayEndDp > 32)
                    throw new IOException("백업된 위젯 크기·위치 설정이 올바르지 않아.");
            } else {
                chatSp = 9 + font;
                compactSp = 8;
                daySp = 5;
                insetDp = 10 + inset * 4;
                dayTopDp = 9;
                dayEndDp = 12;
            }
            if (active < 1 || active > 4 || theme < 0 || theme > 2
                    || font < 0 || font > 2 || align < 0 || align > 2
                    || inset < 0 || inset > 2 || minutes < 1 || minutes > 60
                    || mode < 0 || mode > 2)
                throw new IOException("백업 설정 값이 올바르지 않아.");
        } catch (JSONException | IllegalArgumentException bad) {
            throw new IOException("백업 설정 형식을 읽을 수 없어.", bad);
        }

        // Verify photo before touching existing preferences.
        Bitmap savedImage = null;
        if (photo != null) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(photo, 0, photo.length, bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1
                    || bounds.outWidth > 2048 || bounds.outHeight > 2048)
                throw new IOException("백업 사진 크기가 올바르지 않아.");
            savedImage = BitmapFactory.decodeByteArray(photo, 0, photo.length);
            if (savedImage == null) throw new IOException("사진을 복원할 수 없어.");
        }

        try {
            // Settings are only applied after the entire archive was validated.
            if (savedImage != null) {
                if (!PhotoStore.saveCropped(context, savedImage))
                    throw new IOException("위젯 사진을 저장하지 못했어.");
            } else PhotoStore.remove(context);
        } finally {
            if (savedImage != null && !savedImage.isRecycled()) savedImage.recycle();
        }
        RoomStore.saveRooms(context, rooms);
        RoomStore.saveNicknames(context, aliases);
        RoomStore.saveActiveRooms(context, active);
        RoomStore.saveAppearance(context, theme, font, align, inset);
        if (schema == 3) {
            RoomStore.saveWidgetDesign(context, theme, align, chatSp, compactSp,
                    daySp, insetDp, dayTopDp, dayEndDp);
        }
        PauseState.saveDurationMinutes(context, minutes);
        PauseState.saveDisplayMode(context, mode);
        NotificationWatcher.refreshConfiguredRooms();
        WidgetUpdater.updateAll(context);
        PauseWidgetProvider.updateAll(context);
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count, total = 0;
        while ((count = in.read(chunk)) != -1) {
            total += count;
            if (total > max) throw new IOException("백업 내용이 허용 크기를 초과했어.");
            result.write(chunk, 0, count);
        }
        return result.toByteArray();
    }
}
