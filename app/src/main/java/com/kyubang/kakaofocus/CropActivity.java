package com.kyubang.kakaofocus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Fully local photo editor for the pause-only widget. Opens the user-selected
 * gallery photo, supports arbitrary rotation and a freely movable square crop.
 */
public final class CropActivity extends Activity {
    private CropImageView editor;
    private boolean saved;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(27, 22, 32));
        getWindow().getDecorView().setSystemUiVisibility(0);

        Uri uri = getIntent().getData();
        Bitmap bitmap = PhotoStore.decodeForEdit(this, uri);
        if (bitmap == null) {
            Toast.makeText(this, "사진을 열지 못했어. 다른 사진을 선택해 줘.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(26, 23, 32));
        setContentView(root);

        TextView title = label("사진 자르기 · 회전", 20, true);
        title.setPadding(dp(17), dp(16), dp(17), dp(5));
        root.addView(title);
        TextView instructions = label("사진 드래그 · 두 손가락 확대 · 테두리 이동 · 모서리로 크기 조절", 11, false);
        instructions.setPadding(dp(17), 0, dp(17), dp(9));
        root.addView(instructions);

        editor = new CropImageView(this, bitmap);
        LinearLayout.LayoutParams imageSize = new LinearLayout.LayoutParams(-1, 0, 1f);
        imageSize.leftMargin = dp(8);
        imageSize.rightMargin = dp(8);
        root.addView(editor, imageSize);

        TextView angleText = label("회전 0°", 13, true);
        angleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(28));
        titleParams.topMargin = dp(10);
        root.addView(angleText, titleParams);

        SeekBar rotation = new SeekBar(this);
        rotation.setMax(360);
        rotation.setProgress(180);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(-1, dp(45));
        sliderParams.leftMargin = dp(14);
        sliderParams.rightMargin = dp(14);
        root.addView(rotation, sliderParams);
        rotation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int degrees = progress - 180;
                angleText.setText("회전 " + degrees + "°");
                editor.setPhotoAngle(degrees);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        LinearLayout adjust = row();
        adjust.addView(action("↺ 90°", () -> rotation.setProgress(
                (rotation.getProgress() - 90 + 360) % 360)), weighted());
        adjust.addView(action("↻ 90°", () -> rotation.setProgress(
                (rotation.getProgress() + 90) % 360)), weighted());
        adjust.addView(action("초기화", () -> {
            editor.resetCrop();
            rotation.setProgress(180);
        }), weighted());
        root.addView(adjust);

        LinearLayout footer = row();
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, dp(60));
        footerParams.leftMargin = dp(12);
        footerParams.rightMargin = dp(12);
        footerParams.bottomMargin = dp(12);
        footer.addView(action("취소", this::finish), weighted());

        Button confirm = action("이 영역으로 적용", this::save);
        confirm.setBackground(round(Color.rgb(217, 64, 136), 13));
        confirm.setTextColor(Color.WHITE);
        footer.addView(confirm, weighted());
        root.addView(footer, footerParams);
    }

    private void save() {
        if (saved || editor == null) return;
        Bitmap cropped = null;
        try {
            cropped = editor.exportCrop();
            if (!PhotoStore.saveCropped(this, cropped)) {
                Toast.makeText(this, "사진 저장에 실패했어. 다시 시도해 줘.", Toast.LENGTH_LONG).show();
                return;
            }
            saved = true;
            PauseWidgetProvider.updateAll(this);
            setResult(RESULT_OK, new Intent().putExtra("updated", true));
            Toast.makeText(this, "차단 위젯 사진을 적용했어!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (RuntimeException | OutOfMemoryError ex) {
            Toast.makeText(this, "사진 편집 중 문제가 생겼어. 다른 사진으로 시도해 줘.", Toast.LENGTH_LONG).show();
        } finally {
            if (cropped != null && !cropped.isRecycled()) cropped.recycle();
        }
    }

    @Override protected void onDestroy() {
        if (editor != null) editor.dispose();
        super.onDestroy();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(48), 1f);
        layout.leftMargin = dp(4);
        layout.rightMargin = dp(4);
        return layout;
    }

    private Button action(String name, Runnable callback) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(name);
        button.setTextColor(Color.rgb(247, 231, 242));
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setBackground(round(Color.rgb(66, 51, 71), 13));
        button.setOnClickListener(v -> callback.run());
        return button;
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
