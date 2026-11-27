package com.kyubang.kakaofocus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** One picture only: the user-selected photo for the 1x1 Kakao pause widget. */
final class PhotoStore {
    private PhotoStore() {}

    private static File picture(Context context) {
        return new File(context.getFilesDir(), "pause_widget_photo.jpg");
    }

    static void remove(Context context) {
        picture(context).delete();
    }

    static Bitmap bitmap(Context context) {
        File file = picture(context);
        if (!file.exists()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    static Bitmap decodeForEdit(Context context, Uri uri) {
        if (uri == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth < 1 || bounds.outHeight < 1) return null;

            int sample = 1;
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap source;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                source = BitmapFactory.decodeStream(in, null, options);
            }
            if (source == null) return null;

            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in != null) orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (Exception ignored) {}

            Matrix fix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: fix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: fix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: fix.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: fix.postScale(-1f, 1f); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: fix.postScale(1f, -1f); break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    fix.postScale(-1f, 1f); fix.postRotate(270); break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    fix.postScale(-1f, 1f); fix.postRotate(90); break;
                default: return source;
            }
            Bitmap rotated = Bitmap.createBitmap(source, 0, 0,
                    source.getWidth(), source.getHeight(), fix, true);
            if (rotated != source) source.recycle();
            return rotated;
        } catch (OutOfMemoryError | Exception failure) {
            return null;
        }
    }

    static boolean saveCropped(Context context, Bitmap cropped) {
        if (cropped == null) return false;
        File target = picture(context);
        File tmp = new File(context.getFilesDir(), "pause_widget_photo.tmp");
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            if (!cropped.compress(Bitmap.CompressFormat.JPEG, 88, stream)) return false;
            stream.flush();
            stream.getFD().sync();
        } catch (Exception e) {
            tmp.delete();
            return false;
        }

        if (target.exists() && !target.delete()) { tmp.delete(); return false; }
        if (!tmp.renameTo(target)) { tmp.delete(); return false; }
        return true;
    }
}
