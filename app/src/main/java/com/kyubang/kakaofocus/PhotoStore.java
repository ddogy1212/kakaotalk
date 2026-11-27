package com.kyubang.kakaofocus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** User-selected pictures are downsampled and stored only inside the app. */
final class PhotoStore {
    private PhotoStore() {}

    private static File file(Context context, boolean pause) {
        return new File(context.getFilesDir(), pause ? "pause_widget_photo.jpg" : "compact_widget_photo.jpg");
    }

    static boolean hasPhoto(Context context, boolean pause) {
        return file(context, pause).exists();
    }

    static void remove(Context context, boolean pause) {
        file(context, pause).delete();
    }

    static boolean save(Context context, Uri uri, boolean pause) {
        if (uri == null) return false;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth < 1 || bounds.outHeight < 1) return false;
            int sample = 1;
            while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640)
                sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) return false;
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            double scale = Math.min(1.0, 320.0 / Math.max(width, height));
            Bitmap small = bitmap;
            if (scale < 1.0) {
                small = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, (int) Math.round(width * scale)),
                        Math.max(1, (int) Math.round(height * scale)), true);
                bitmap.recycle();
            }
            File dest = file(context, pause);
            File temp = new File(context.getFilesDir(), dest.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                if (!small.compress(Bitmap.CompressFormat.JPEG, 86, out)) {
                    small.recycle();
                    temp.delete();
                    return false;
                }
            }
            small.recycle();
            if (dest.exists() && !dest.delete()) { temp.delete(); return false; }
            if (!temp.renameTo(dest)) { temp.delete(); return false; }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    static Bitmap bitmap(Context context, boolean pause) {
        File source = file(context, pause);
        if (!source.exists()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(source.getAbsolutePath(), options);
    }
}
