package com.kyubang.kakaofocus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Configuration is local; message previews and sent requests are held in RAM only. */
final class MiniChatStore {
    static final class Line {
        final String text, id;
        final long time;
        final boolean outgoing;
        Line(String text, String id, long time, boolean outgoing) {
            this.text=text; this.id=id; this.time=time; this.outgoing=outgoing;
        }
    }
    private static final List<Line> history = new ArrayList<>();
    private static final java.util.Set<String> unread = new java.util.HashSet<>();
    private static final java.util.Set<String> seen = new java.util.HashSet<>();
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mini_chat_local", Context.MODE_PRIVATE);
    }
    static String token(Context c) { return prefs(c).getString("token", ""); }
    static String label(Context c) { return prefs(c).getString("label", ""); }
    static void bind(Context c, String token, String label) {
        clear();
        prefs(c).edit().putString("token", token).putString("label", label).apply();
    }
    static boolean autoClose(Context c) { return prefs(c).getBoolean("close", true); }
    static boolean showCount(Context c) { return prefs(c).getBoolean("count", true); }
    static int opacity(Context c) { return prefs(c).getInt("opacity", 95); }
    static int font(Context c) { return prefs(c).getInt("font", 14); }
    static int width(Context c) { return prefs(c).getInt("width", 85); }
    static void save(Context c, boolean close, boolean count, int opacity, int font, int width) {
        prefs(c).edit().putBoolean("close",close).putBoolean("count",count)
            .putInt("opacity",opacity).putInt("font",font).putInt("width",width).apply();
    }
    static synchronized void clear() { history.clear(); unread.clear(); seen.clear(); }
    static synchronized void add(Line line) {
        if (line.text == null || line.text.trim().isEmpty() || !seen.add(line.id)) return;
        history.add(line);
        if (!line.outgoing) unread.add(line.id);
        history.sort((a,b)->Long.compare(a.time,b.time));
        while(history.size()>40) { Line old=history.remove(0); unread.remove(old.id); }
        // Bound memory even during a very long session; retain current visible IDs.
        if(seen.size()>2000) { seen.clear(); for(Line l:history) seen.add(l.id); }
    }
    static synchronized List<Line> lines() { return new ArrayList<>(history); }
    static synchronized int count() { return unread.size(); }
    static synchronized void acknowledge() { unread.clear(); }
    private static File photo(Context c) { return new File(c.getFilesDir(),"mini_chat_photo.jpg"); }
    static Bitmap bitmap(Context c) { return BitmapFactory.decodeFile(photo(c).getAbsolutePath()); }
    static void removePhoto(Context c) { photo(c).delete(); }
    static boolean savePhoto(Context c, Uri uri) {
        Bitmap input=PhotoStore.decodeForEdit(c,uri);
        if(input==null) return false;
        Bitmap square=null,small=null;
        File tmp=new File(c.getFilesDir(),"mini_chat_photo.tmp");
        try {
            int side=Math.min(input.getWidth(),input.getHeight());
            square=Bitmap.createBitmap(input,(input.getWidth()-side)/2,(input.getHeight()-side)/2,side,side);
            small=Bitmap.createScaledBitmap(square,256,256,true);
            try(FileOutputStream out=new FileOutputStream(tmp)) {
                if(!small.compress(Bitmap.CompressFormat.JPEG,90,out)) return false;
            }
            return tmp.renameTo(photo(c));
        } catch(Exception e) { return false; }
        finally {
            tmp.delete();
            if(small!=null && small!=square && small!=input) small.recycle();
            if(square!=null && square!=input) square.recycle();
            input.recycle();
        }
    }
}
