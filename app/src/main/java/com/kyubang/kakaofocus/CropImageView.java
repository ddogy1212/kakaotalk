package com.kyubang.kakaofocus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Square widget crop with free-position crop box, corner resize, image pan,
 * two-finger zoom, and arbitrary rotation controlled by the editor slider.
 */
final class CropImageView extends View {
    private static final int NONE = 0, PAN_IMAGE = 1, MOVE_CROP = 2,
            RESIZE_CROP = 3, PINCH_IMAGE = 4;
    private final Paint picturePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix imageMatrix = new Matrix();
    private final RectF crop = new RectF();
    private Bitmap bitmap;
    private float zoom = 1f, panX = 0f, panY = 0f, degrees = 0f;
    private float downX, downY, initialX, initialY, initialZoom, initialDistance;
    private float anchorX, anchorY;
    private int resizeCorner = 0, mode = NONE;
    private boolean initialized;

    CropImageView(Context context, Bitmap source) {
        super(context);
        bitmap = source;
        setBackgroundColor(Color.rgb(23, 20, 29));
        shade.setColor(0xB9000000);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(dp(2f));
        frame.setColor(0xFFFFB6DC);
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(dp(0.7f));
        guide.setColor(0x99FFFFFF);
        handle.setColor(Color.WHITE);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!initialized && w > 0 && h > 0) resetCrop();
    }

    void resetCrop() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float side = Math.min(getWidth(), getHeight()) * 0.66f;
        float centerX = getWidth() / 2f, centerY = getHeight() / 2f;
        crop.set(centerX - side / 2, centerY - side / 2,
                centerX + side / 2, centerY + side / 2);
        zoom = 1f;
        panX = panY = degrees = 0f;
        initialized = true;
        invalidate();
    }

    void setRotation(float angle) {
        degrees = angle;
        invalidate();
    }

    private void computeImageMatrix() {
        imageMatrix.reset();
        if (bitmap == null || crop.isEmpty()) return;
        float base = Math.max(crop.width() / bitmap.getWidth(),
                crop.height() / bitmap.getHeight());
        imageMatrix.postTranslate(-bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f);
        imageMatrix.postScale(base * zoom, base * zoom);
        imageMatrix.postRotate(degrees);
        imageMatrix.postTranslate(getWidth() / 2f + panX, getHeight() / 2f + panY);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || !initialized) return;
        computeImageMatrix();
        canvas.drawBitmap(bitmap, imageMatrix, picturePaint);
        canvas.drawRect(0, 0, getWidth(), crop.top, shade);
        canvas.drawRect(0, crop.bottom, getWidth(), getHeight(), shade);
        canvas.drawRect(0, crop.top, crop.left, crop.bottom, shade);
        canvas.drawRect(crop.right, crop.top, getWidth(), crop.bottom, shade);

        canvas.drawRect(crop, frame);
        for (int i = 1; i < 3; i++) {
            float x = crop.left + crop.width() * i / 3f;
            float y = crop.top + crop.height() * i / 3f;
            canvas.drawLine(x, crop.top, x, crop.bottom, guide);
            canvas.drawLine(crop.left, y, crop.right, y, guide);
        }
        float r = dp(5f);
        canvas.drawCircle(crop.left, crop.top, r, handle);
        canvas.drawCircle(crop.right, crop.top, r, handle);
        canvas.drawCircle(crop.right, crop.bottom, r, handle);
        canvas.drawCircle(crop.left, crop.bottom, r, handle);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int type = event.getActionMasked();
        if (type == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            initialX = panX;
            initialY = panY;
            float tol = dp(23f);
            int corner = nearestCorner(downX, downY, tol);
            if (corner != -1) {
                mode = RESIZE_CROP;
                resizeCorner = corner;
                anchorX = corner == 0 || corner == 3 ? crop.right : crop.left;
                anchorY = corner == 0 || corner == 1 ? crop.bottom : crop.top;
            } else if (onCropBorder(downX, downY, dp(16f))) {
                mode = MOVE_CROP;
            } else {
                mode = PAN_IMAGE;
            }
            return true;
        }
        if (type == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            mode = PINCH_IMAGE;
            initialDistance = distance(event);
            initialZoom = zoom;
            downX = (event.getX(0) + event.getX(1)) / 2f;
            downY = (event.getY(0) + event.getY(1)) / 2f;
            initialX = panX;
            initialY = panY;
            return true;
        }
        if (type == MotionEvent.ACTION_MOVE) {
            if (mode == PINCH_IMAGE && event.getPointerCount() > 1) {
                if (initialDistance > 0f)
                    zoom = Math.max(0.3f, Math.min(12f, initialZoom *
                            distance(event) / initialDistance));
                float cx = (event.getX(0) + event.getX(1)) / 2f;
                float cy = (event.getY(0) + event.getY(1)) / 2f;
                panX = initialX + cx - downX;
                panY = initialY + cy - downY;
            } else if (event.getPointerCount() == 1) {
                float x = event.getX(), y = event.getY();
                if (mode == PAN_IMAGE) {
                    panX = initialX + x - downX;
                    panY = initialY + y - downY;
                } else if (mode == MOVE_CROP) {
                    float dx = x - downX, dy = y - downY;
                    float width = crop.width(), height = crop.height();
                    crop.offsetTo(clamp(crop.left + dx, dp(14f),
                                    getWidth() - dp(14f) - width),
                            clamp(crop.top + dy, dp(14f),
                                    getHeight() - dp(14f) - height));
                    downX = x; downY = y;
                } else if (mode == RESIZE_CROP) {
                    float sx = resizeCorner == 0 || resizeCorner == 3 ? -1f : 1f;
                    float sy = resizeCorner == 0 || resizeCorner == 1 ? -1f : 1f;
                    float maxX = sx < 0 ? anchorX - dp(14f)
                            : getWidth() - dp(14f) - anchorX;
                    float maxY = sy < 0 ? anchorY - dp(14f)
                            : getHeight() - dp(14f) - anchorY;
                    float maxSide = Math.max(dp(55f), Math.min(maxX, maxY));
                    float requested = Math.max(Math.abs(x - anchorX), Math.abs(y - anchorY));
                    float side = clamp(requested, dp(55f), maxSide);
                    float endX = anchorX + sx * side, endY = anchorY + sy * side;
                    crop.set(Math.min(anchorX, endX), Math.min(anchorY, endY),
                            Math.max(anchorX, endX), Math.max(anchorY, endY));
                }
            }
            invalidate();
            return true;
        }
        if (type == MotionEvent.ACTION_POINTER_UP) {
            mode = NONE;
            return true;
        }
        if (type == MotionEvent.ACTION_UP || type == MotionEvent.ACTION_CANCEL) {
            mode = NONE;
            return true;
        }
        return true;
    }

    private int nearestCorner(float x, float y, float tolerance) {
        float[] xs = {crop.left, crop.right, crop.right, crop.left};
        float[] ys = {crop.top, crop.top, crop.bottom, crop.bottom};
        for (int i = 0; i < 4; i++)
            if (Math.hypot(x - xs[i], y - ys[i]) <= tolerance) return i;
        return -1;
    }

    private boolean onCropBorder(float x, float y, float tol) {
        boolean withinX = x >= crop.left - tol && x <= crop.right + tol;
        boolean withinY = y >= crop.top - tol && y <= crop.bottom + tol;
        return (withinX && (Math.abs(y - crop.top) < tol ||
                Math.abs(y - crop.bottom) < tol))
                || (withinY && (Math.abs(x - crop.left) < tol ||
                Math.abs(x - crop.right) < tol));
    }

    private float distance(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    private float clamp(float v, float low, float high) {
        return Math.max(low, Math.min(v, high));
    }

    Bitmap exportCrop() {
        if (bitmap == null || !initialized || crop.width() < 1f) return null;
        final int size = 384;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.rgb(23, 20, 29));
        computeImageMatrix();
        Matrix output = new Matrix(imageMatrix);
        output.postTranslate(-crop.left, -crop.top);
        output.postScale(size / crop.width(), size / crop.height());
        canvas.drawBitmap(bitmap, output, picturePaint);
        return out;
    }

    void dispose() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
    }
}
