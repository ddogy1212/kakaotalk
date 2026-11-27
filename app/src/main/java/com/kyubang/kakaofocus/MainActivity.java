package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.function.IntConsumer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int CHOOSE_PAUSE_IMAGE = 2071;
    private static final int EDIT_PAUSE_IMAGE = 2073;
    private static final int EXPORT_SETTINGS = 4081;
    private static final int IMPORT_SETTINGS = 4082;
    private static final int INK = Color.rgb(54, 35, 49);
    private static final int MUTED = Color.rgb(117, 87, 107);
    private static final int PINK = Color.rgb(196, 60, 127);

    private final EditText[] inputs = new EditText[RoomStore.ROOM_COUNT];
    private final EditText[] nicknameInputs = new EditText[RoomStore.ROOM_COUNT];
    private final LinearLayout[] roomCards = new LinearLayout[RoomStore.ROOM_COUNT];
    private LinearLayout roomsPanel, widgetsPanel, focusPanel;
    private Button roomsTab, widgetsTab, focusTab;
    private Spinner roomCount;
    private TextView statusView, previewDay, titlesView, pauseStatus, minutesLabel;
    private FrameLayout previewView, previewDayAnchor, compactPreviewFrame;
    private LinearLayout previewPositioner, previewRows, compactPreviewRows;
    private LinearLayout floatingPreviewDock, pageContainer;
    private ImageView pausePreview;

    // Draft changes affect previews only; persisted and pushed to widgets on Apply.
    private int draftTheme, draftAlign, draftChatSp, draftCompactSp, draftDaySp,
            draftInsetDp, draftDayTopDp, draftDayEndDp;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(255, 244, 250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(255, 244, 250));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(17), dp(25), dp(17), dp(25));
        pageContainer = page;
        scroll.addView(page);

        // Fixed design dock stays visible even when the slider list scrolls.
        FrameLayout screen = new FrameLayout(this);
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        floatingPreviewDock = new LinearLayout(this);
        floatingPreviewDock.setOrientation(LinearLayout.VERTICAL);
        floatingPreviewDock.setPadding(dp(12), dp(7), dp(12), dp(10));
        floatingPreviewDock.setBackground(round(Color.rgb(255, 246, 251), 17));
        floatingPreviewDock.setElevation(dp(8));
        floatingPreviewDock.setVisibility(View.GONE);
        FrameLayout.LayoutParams dockPlacement =
                new FrameLayout.LayoutParams(-1, dp(144), Gravity.BOTTOM);
        dockPlacement.setMargins(dp(10), 0, dp(10), dp(8));
        screen.addView(floatingPreviewDock, dockPlacement);
        setContentView(screen);

        TextView heading = text("카톡 한눈에 정식 💌", 26, true);
        heading.setTextColor(PINK);
        page.addView(heading);
        addWithTop(page, text("알림은 간단하게, 카톡 확인은 내가 정한 시간에.", 13, false), 6);

        statusView = text("", 13, true);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackground(round(Color.rgb(255, 228, 239), 14));
        addWithTop(page, statusView, 17);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tabGroup = new LinearLayout.LayoutParams(-1, dp(44));
        tabGroup.topMargin = dp(17);
        page.addView(tabs, tabGroup);
        roomsTab = tab(tabs, "톡방 관리");
        widgetsTab = tab(tabs, "위젯 꾸미기");
        focusTab = tab(tabs, "집중 차단");
        roomsTab.setOnClickListener(v -> showTab(0));
        widgetsTab.setOnClickListener(v -> showTab(1));
        focusTab.setOnClickListener(v -> showTab(2));

        roomsPanel = newPanel(page);
        widgetsPanel = newPanel(page);
        focusPanel = newPanel(page);
        buildRooms();
        buildWidgets();
        buildFocus();
        showTab(0);
        refreshUI();
    }

    private void buildRooms() {
        addWithTop(roomsPanel, text("📨 중요한 톡방 관리", 20, true), 0);
        addWithTop(roomsPanel, text("카카오톡 알림이 켜져 있어야 해. 방해금지 모드는 유지해도 돼.", 13, false), 7);
        Button permission = addButton(roomsPanel, "알림 접근 권한 열기", false);
        permission.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        Button refresh = addButton(roomsPanel, "🔄 연결 확인 · 새로고침", false);
        refresh.setOnClickListener(v -> {
            boolean okay = NotificationWatcher.reconnectIfNeeded(this);
            refreshUI();
            Toast.makeText(this, okay ? "알림을 다시 확인했어!" :
                    "재연결을 요청했어. 계속 안 되면 권한을 껐다 켜줘.", Toast.LENGTH_LONG).show();
        });
        Button battery = addButton(roomsPanel, "배터리 제한 설정 열기", false);
        battery.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        addWithTop(roomsPanel, text("표시할 톡방 개수", 15, true), 18);
        roomCount = spinner(new String[]{"1개", "2개", "3개", "4개"}, RoomStore.activeRooms(this) - 1);
        addWithTop(roomsPanel, roomCount, 6);
        addWithTop(roomsPanel, text("원래 이름은 알림 감지용이고, 별명은 위젯에만 보여. 별명은 필요한 톡방에만 넣으면 돼.", 12, false), 9);
        for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setBackground(round(Color.rgb(255, 247, 251), 14));
            roomCards[i] = card;
            addWithTop(roomsPanel, card, 10);

            TextView title = text("톡방 " + (i + 1), 14, true);
            title.setTextColor(PINK);
            card.addView(title);

            TextView actualLabel = text("카톡에서 표시되는 실제 이름 · 감지용", 12, false);
            addWithTop(card, actualLabel, 7);
            EditText edit = new EditText(this);
            edit.setTextColor(INK);
            edit.setTextSize(15);
            edit.setSingleLine(true);
            edit.setHint("실제 톡방 이름");
            edit.setText(RoomStore.name(this, i));
            edit.setPadding(dp(10), dp(8), dp(10), dp(8));
            edit.setBackground(round(Color.WHITE, 10));
            inputs[i] = edit;
            addWithTop(card, edit, 4);

            TextView nicknameLabel = text("짧은 별명 · 위젯 표시용 (선택)", 12, false);
            addWithTop(card, nicknameLabel, 10);
            EditText nickname = new EditText(this);
            nickname.setTextColor(INK);
            nickname.setTextSize(15);
            nickname.setSingleLine(true);
            nickname.setHint("예: 나예 / 엄마 / 반톡");
            nickname.setText(RoomStore.nickname(this, i));
            nickname.setPadding(dp(10), dp(8), dp(10), dp(8));
            nickname.setBackground(round(Color.WHITE, 10));
            nickname.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(24)
            });
            nicknameInputs[i] = nickname;
            addWithTop(card, nickname, 4);
        }
        updateVisibleInputs();
        roomCount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id) {
                updateVisibleInputs();
            }
            @Override public void onNothingSelected(AdapterView<?> adapter) {}
        });
        Button select = addButton(roomsPanel, "최근 감지된 톡방에서 선택", false);
        select.setOnClickListener(v -> showRecentPicker());
        Button save = addButton(roomsPanel, "톡방 설정 저장", true);
        save.setOnClickListener(v -> saveRooms());

        addWithTop(roomsPanel, text("단톡방 감지가 안 된다면", 16, true), 18);
        Button diagnostics = addButton(roomsPanel, "🔍 카톡 알림 제목 진단", false);
        diagnostics.setOnClickListener(v -> showGroupDiagnostics());
        titlesView = text("", 12, false);
        addWithTop(roomsPanel, titlesView, 9);
        addWithTop(roomsPanel, text("알림 내용은 저장하지 않아. 알림 제목은 톡방 구분용으로만 기기에 보관돼.", 11, false), 13);
    }

    private void buildWidgets() {
        draftTheme = RoomStore.theme(this);
        draftAlign = RoomStore.align(this);
        draftChatSp = RoomStore.chatSp(this);
        draftCompactSp = RoomStore.compactSp(this);
        draftDaySp = RoomStore.daySp(this);
        draftInsetDp = RoomStore.safeInsetDp(this);
        draftDayTopDp = RoomStore.dayTopDp(this);
        draftDayEndDp = RoomStore.dayEndDp(this);

        addWithTop(widgetsPanel, text("🎨 실시간 위젯 편집기", 20, true), 0);
        addWithTop(widgetsPanel, text(
                "아래쪽에 미리보기가 고정돼 있어. 슬라이더를 움직이면 즉시 바뀌고, 실제 위젯은 '적용'을 누를 때만 저장돼.",
                12, false), 7);

        TextView dockHeading = text("실시간 미리보기 · 2×1 / 1×1", 12, true);
        dockHeading.setTextColor(PINK);
        floatingPreviewDock.addView(dockHeading);
        LinearLayout previewPair = new LinearLayout(this);
        previewPair.setOrientation(LinearLayout.HORIZONTAL);
        previewPair.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pairPlacement =
                new LinearLayout.LayoutParams(-1, dp(99));
        pairPlacement.topMargin = dp(7);
        floatingPreviewDock.addView(previewPair, pairPlacement);

        previewView = new FrameLayout(this);
        previewPositioner = new LinearLayout(this);
        previewPositioner.setGravity(Gravity.CENTER);
        previewRows = new LinearLayout(this);
        previewRows.setOrientation(LinearLayout.VERTICAL);
        previewRows.setGravity(Gravity.CENTER_HORIZONTAL);
        previewPositioner.addView(previewRows, new LinearLayout.LayoutParams(-2, -2));
        previewView.addView(previewPositioner, new FrameLayout.LayoutParams(-1, -1));

        previewDayAnchor = new FrameLayout(this);
        previewDay = text("", 7, false);
        previewDay.setSingleLine(true);
        previewDay.setIncludeFontPadding(false);
        previewDayAnchor.addView(previewDay,
                new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        previewView.addView(previewDayAnchor, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams widePreview =
                new LinearLayout.LayoutParams(0, dp(94), 1f);
        previewPair.addView(previewView, widePreview);

        compactPreviewFrame = new FrameLayout(this);
        compactPreviewRows = new LinearLayout(this);
        compactPreviewRows.setOrientation(LinearLayout.VERTICAL);
        compactPreviewRows.setGravity(Gravity.CENTER);
        compactPreviewFrame.addView(compactPreviewRows,
                new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams smallPreview =
                new LinearLayout.LayoutParams(dp(94), dp(94));
        smallPreview.leftMargin = dp(8);
        previewPair.addView(compactPreviewFrame, smallPreview);

        addWithTop(widgetsPanel, text("위젯 테마", 14, true), 19);
        Spinner themePicker = spinner(new String[]{"다크", "화이트", "반투명"}, draftTheme);
        addWithTop(widgetsPanel, themePicker, 4);
        themePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapter, View selected,
                                                  int position, long id) {
                draftTheme = position;
                renderPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> adapter) {}
        });

        addWithTop(widgetsPanel, text("글씨 정렬", 14, true), 12);
        Spinner alignmentPicker = spinner(new String[]{"왼쪽", "가운데", "오른쪽"}, draftAlign);
        addWithTop(widgetsPanel, alignmentPicker, 4);
        alignmentPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapter, View selected,
                                                  int position, long id) {
                draftAlign = position;
                renderPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> adapter) {}
        });

        addDesignSlider(widgetsPanel, "2×1 톡방 글씨 크기", 7, 18, draftChatSp,
                value -> draftChatSp = value);
        addDesignSlider(widgetsPanel, "1×1 톡방 글씨 크기", 6, 16, draftCompactSp,
                value -> draftCompactSp = value);
        addDesignSlider(widgetsPanel, "수능 D-day 글씨 크기", 4, 12, draftDaySp,
                value -> draftDaySp = value);
        addDesignSlider(widgetsPanel, "둥근 테두리 보호 여백", 6, 26, draftInsetDp,
                value -> draftInsetDp = value);
        addDesignSlider(widgetsPanel, "수능 D-day 위쪽 여백", 2, 30, draftDayTopDp,
                value -> draftDayTopDp = value);
        addDesignSlider(widgetsPanel, "수능 D-day 오른쪽 여백", 2, 32, draftDayEndDp,
                value -> draftDayEndDp = value);

        addWithTop(widgetsPanel, text(
                "굿락의 실제 위젯 크기는 다를 수 있어. 글씨를 너무 키우면 일부가 잘릴 수 있으니 미리보기와 잠금화면을 모두 확인해 줘.",
                12, false), 14);

        Button apply = addButton(widgetsPanel, "✓ 이 디자인 적용", true);
        apply.setOnClickListener(v -> {
            RoomStore.saveWidgetDesign(this, draftTheme, draftAlign, draftChatSp,
                    draftCompactSp, draftDaySp, draftInsetDp, draftDayTopDp, draftDayEndDp);
            WidgetUpdater.updateAll(this);
            PauseWidgetProvider.updateAll(this);
            refreshUI();
            Toast.makeText(this, "선택한 크기·위치·색상·정렬을 위젯에 적용했어!",
                    Toast.LENGTH_SHORT).show();
        });

        addWithTop(widgetsPanel, text("💾 설정 백업 · 복원", 18, true), 24);
        addWithTop(widgetsPanel, text(
                "위젯 크기와 위치, 톡방·별명, 차단 시간, 사진을 ZIP 파일 하나로 저장할 수 있어.",
                12, false), 7);
        Button export = addButton(widgetsPanel, "⬇️ 내 설정 백업 파일 저장", false);
        export.setOnClickListener(v -> exportSettings());
        Button restore = addButton(widgetsPanel, "⬆️ 기존 백업 파일 불러오기", false);
        restore.setOnClickListener(v -> importSettings());
        addWithTop(widgetsPanel, text(
                "백업에는 알림 기록이나 메시지 내용이 들어가지 않아. 시스템 권한은 복원되지 않아.",
                11, false), 8);
    }

    private void addDesignSlider(LinearLayout panel, String label, int minimum, int maximum,
                                 int initial, IntConsumer onValueChanged) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 14, true);
        TextView number = text(initial + (label.contains("글씨") ? "sp" : "dp"), 14, true);
        number.setTextColor(PINK);
        number.setGravity(Gravity.END);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        heading.addView(number, new LinearLayout.LayoutParams(-2, -2));
        addWithTop(panel, heading, 17);

        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum - minimum);
        slider.setProgress(initial - minimum);
        slider.setSplitTrack(false);
        slider.setProgressTintList(ColorStateList.valueOf(PINK));
        slider.setThumbTintList(ColorStateList.valueOf(PINK));
        slider.setProgressBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(242, 207, 225)));
        slider.setPadding(dp(5), dp(6), dp(5), dp(6));
        addWithTop(panel, slider, 4);

        LinearLayout endpoints = new LinearLayout(this);
        endpoints.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = text(minimum + "", 11, false);
        TextView right = text(maximum + "", 11, false);
        right.setGravity(Gravity.END);
        endpoints.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        endpoints.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        addWithTop(panel, endpoints, 0);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int value = minimum + progress;
                number.setText(value + (label.contains("글씨") ? "sp" : "dp"));
                onValueChanged.accept(value);
                renderPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void buildFocus() {
        addWithTop(focusPanel, text("⏳ 카톡 집중 차단", 20, true), 0);
        addWithTop(focusPanel, text("사진형 1×1 위젯을 누르면 지정한 시간 동안 카톡 화면을 홈으로 돌려보내.", 13, false), 8);

        minutesLabel = text("", 18, true);
        minutesLabel.setTextColor(PINK);
        addWithTop(focusPanel, minutesLabel, 20);
        SeekBar duration = new SeekBar(this);
        duration.setMax(59);
        duration.setProgress(PauseState.durationMinutes(this) - 1);
        duration.setSplitTrack(false);
        duration.setProgressTintList(ColorStateList.valueOf(PINK));
        duration.setThumbTintList(ColorStateList.valueOf(PINK));
        duration.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(242, 207, 225)));
        // Match the thumb track to the end labels instead of using spaces.
        duration.setPadding(dp(5), dp(7), dp(5), dp(7));
        addWithTop(focusPanel, duration, 10);
        minutesLabel.setText("차단 시간 · " + (duration.getProgress() + 1) + "분");
        duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                minutesLabel.setText("차단 시간 · " + (value + 1) + "분");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                PauseState.saveDurationMinutes(MainActivity.this, bar.getProgress() + 1);
                refreshUI();
            }
        });
        LinearLayout durationLabels = new LinearLayout(this);
        durationLabels.setOrientation(LinearLayout.HORIZONTAL);
        durationLabels.setGravity(Gravity.CENTER_VERTICAL);
        TextView minLabel = text("1분", 12, false);
        TextView maxLabel = text("60분", 12, false);
        minLabel.setGravity(Gravity.START);
        maxLabel.setGravity(Gravity.END);
        durationLabels.addView(minLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        durationLabels.addView(maxLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        addWithTop(focusPanel, durationLabels, 0);
        addWithTop(focusPanel, text("슬라이더를 움직이면 선택한 시간이 바로 저장돼.", 11, false), 6);

        addWithTop(focusPanel, text("차단 중 사진 위 표시", 16, true), 17);
        Spinner displayMode = spinner(new String[]{
                "자세히 · 차단 중 + 큰 남은 시간",
                "간단히 · 작은 남은 시간만",
                "사진만 · 차단 중에도 글씨 숨김"
        }, PauseState.displayMode(this));
        addWithTop(focusPanel, displayMode, 6);
        Button saveDisplay = addButton(focusPanel, "차단 중 표시 방식 적용", false);
        saveDisplay.setOnClickListener(v -> {
            PauseState.saveDisplayMode(this, displayMode.getSelectedItemPosition());
            refreshUI();
            Toast.makeText(this, "차단 위젯 표시 방식을 변경했어.", Toast.LENGTH_SHORT).show();
        });
        addWithTop(focusPanel, text("사진을 아직 고르지 않았다면 상태 글씨가 표시돼. 차단 중 사진만 보기 모드에서는 남은 시간이 숨겨지지만 설정 화면에서 확인할 수 있어.", 12, false), 5);

        pauseStatus = text("", 13, true);
        pauseStatus.setPadding(dp(11), dp(10), dp(11), dp(10));
        pauseStatus.setBackground(round(Color.rgb(255, 226, 239), 10));
        addWithTop(focusPanel, pauseStatus, 12);

        addWithTop(focusPanel, text("차단 위젯 사진", 16, true), 19);
        addWithTop(focusPanel, text("갤러리에서 사진을 고르고 확대·이동·회전·영역 선택으로 자유롭게 잘라 적용해. 차단 중엔 사진 위에 남은 시간이 보여.", 12, false), 5);
        pausePreview = photoPreview();
        addWithTop(focusPanel, pausePreview, 9);
        Button choosePause = addButton(focusPanel, "🖼️ 갤러리에서 사진 선택 · 편집", false);
        choosePause.setOnClickListener(v -> pickPhoto());
        Button clearPause = addButton(focusPanel, "위젯 사진 초기화", false);
        clearPause.setOnClickListener(v -> {
            PhotoStore.remove(this);
            PauseWidgetProvider.updateAll(this);
            refreshUI();
        });

        addWithTop(focusPanel, text("차단 권한 설정", 16, true), 17);
        Button accessibility = addButton(focusPanel, "접근성에서 카톡 차단 서비스 켜기", true);
        accessibility.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addWithTop(focusPanel, text(
                "접근성 권한은 직접 켜야 해. 이 서비스는 카톡이 화면에 나타나는지만 확인하고 채팅 내용은 읽지 않아. "
                + "일반 위젯만으로 카톡 실행을 완전히 막을 수는 없으며, 차단 중에도 잠깐 화면이 보일 수 있어. "
                + "언제든 기기 설정에서 서비스를 끌 수 있어.", 12, false), 10);
        addWithTop(focusPanel, text(
                "일부 갤럭시에서는 설치한 앱의 '제한된 설정 허용'이 필요할 수도 있어. "
                + "이 권한은 신뢰하는 앱에만 부여해 줘.", 11, false), 9);
    }

    private void exportSettings() {
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.setType("application/zip");
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.putExtra(Intent.EXTRA_TITLE, "kakao-focus-settings.zip");
        startActivityForResult(save, EXPORT_SETTINGS);
    }

    private void importSettings() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.setType("application/zip");
        open.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(open, IMPORT_SETTINGS);
    }

    private void restoreWithConfirmation(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("저장된 설정을 불러올까?")
                .setMessage("현재 톡방 이름과 위젯 디자인, 차단 시간, 사진이 백업 파일의 내용으로 변경돼. 앱 권한은 별도로 다시 허용해야 해.")
                .setNegativeButton("취소", null)
                .setPositiveButton("복원하기", (dialog, which) -> {
                    try {
                        BackupStore.restoreFrom(this, uri);
                        Toast.makeText(this, "설정과 위젯 사진 복원 완료!", Toast.LENGTH_LONG).show();
                        recreate();
                    } catch (IOException | RuntimeException error) {
                        Toast.makeText(this, "복원 실패: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void pickPhoto() {
        Intent gallery;
        if (Build.VERSION.SDK_INT >= 33) {
            gallery = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            gallery = new Intent(Intent.ACTION_GET_CONTENT);
            gallery.addCategory(Intent.CATEGORY_OPENABLE);
        }
        gallery.setType("image/*");
        gallery.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(gallery, "갤러리에서 사진 선택"), CHOOSE_PAUSE_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == EXPORT_SETTINGS) {
            if (data == null || data.getData() == null) return;
            try {
                BackupStore.exportTo(this, data.getData());
                Toast.makeText(this, "설정 백업 ZIP 파일을 저장했어!", Toast.LENGTH_LONG).show();
            } catch (IOException | RuntimeException error) {
                Toast.makeText(this, "백업 실패: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == IMPORT_SETTINGS) {
            if (data != null && data.getData() != null)
                restoreWithConfirmation(data.getData());
        } else if (requestCode == CHOOSE_PAUSE_IMAGE) {
            if (data == null || data.getData() == null) return;
            Uri selected = data.getData();
            Intent edit = new Intent(this, CropActivity.class);
            edit.setDataAndType(selected, "image/*");
            edit.setClipData(ClipData.newUri(getContentResolver(), "선택한 위젯 사진", selected));
            edit.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(edit, EDIT_PAUSE_IMAGE);
        } else if (requestCode == EDIT_PAUSE_IMAGE) {
            PauseWidgetProvider.updateAll(this);
            refreshUI();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        RecoveryScheduler.scheduleIfWidgets(this);
        ExamDay.schedule(this);
        NotificationWatcher.reconnectIfNeeded(this);
        PauseState.scheduleExpiry(this);
        refreshUI();
    }

    private void refreshUI() {
        if (statusView == null) return;
        boolean granted = WidgetUpdater.hasAccess(this);
        boolean connected = NotificationWatcher.isConnected();
        statusView.setText(!granted ? "● 카톡 알림 접근 권한 필요" :
                connected ? "● 카톡 알림 감지 정상" : "● 연결 대기 중 · 새로고침 필요");
        statusView.setTextColor(granted && connected ? INK : PINK);
        if (pauseStatus != null) {
            boolean enabled = PauseState.accessibilityEnabled(this);
            if (!enabled) pauseStatus.setText("차단 서비스 꺼짐 · 접근성 권한을 켜줘");
            else if (PauseState.active(this)) {
                long minutes = (PauseState.remaining(this) + 59_999L) / 60_000L;
                pauseStatus.setText("차단 중 · 남은 시간 약 " + minutes + "분");
            } else pauseStatus.setText("준비 완료 · 위젯을 누르면 "
                    + PauseState.durationMinutes(this) + "분 제한");
        }
        if (pausePreview != null) updatePhotoPreview(pausePreview);
        renderPreview();
        updateTitles();
    }

    private void renderPreview() {
        if (previewView == null || previewRows == null || previewDay == null
                || compactPreviewFrame == null || compactPreviewRows == null) return;

        int background = draftTheme == 1 ? Color.rgb(255, 249, 252)
                : draftTheme == 2 ? Color.argb(179, 26, 23, 33)
                : Color.rgb(41, 36, 47);
        int ink = draftTheme == 1 ? INK : Color.rgb(240, 235, 243);
        int muted = draftTheme == 1 ? MUTED : Color.rgb(183, 174, 190);
        int accent = draftTheme == 1 ? Color.rgb(177, 43, 105)
                : Color.rgb(255, 182, 220);
        int alignment = Gravity.CENTER_VERTICAL | (draftAlign == 1
                ? Gravity.CENTER_HORIZONTAL : draftAlign == 2 ? Gravity.RIGHT : Gravity.LEFT);

        previewView.setBackground(round(background, 15));
        previewView.setPadding(dp(draftInsetDp), dp(2),
                dp(draftInsetDp), dp(2));
        previewPositioner.setGravity(alignment);
        previewRows.setGravity(alignment);
        previewRows.removeAllViews();

        previewDayAnchor.setPadding(0, dp(draftDayTopDp), dp(draftDayEndDp), 0);
        previewDay.setText(ExamDay.label());
        previewDay.setTextColor(accent);
        previewDay.setTextSize(draftDaySp);

        compactPreviewFrame.setBackground(round(background, 15));
        compactPreviewFrame.setPadding(dp(5), dp(3), dp(5), dp(3));
        compactPreviewRows.setGravity(alignment);
        compactPreviewRows.removeAllViews();

        boolean hasAccess = WidgetUpdater.hasAccess(this);
        boolean connected = NotificationWatcher.isConnected();
        int active = RoomStore.activeRooms(this);
        for (int i = 0; i < active; i++) {
            boolean missing = RoomStore.name(this, i).isEmpty();
            String name = missing ? (i + 1) + "번 톡방 등록"
                    : RoomStore.displayName(this, i);
            int count = RoomStore.count(this, i);
            String status = missing ? "" : !hasAccess ? "권한 필요"
                    : !connected ? "연결 확인" : WidgetUpdater.countAndTime(this, i);
            String lineText = name + (status.isEmpty() ? "" : " " + status);
            int color = missing ? muted : (count > 0 && hasAccess && connected ? accent : ink);

            TextView wideLine = text(lineText, draftChatSp, false);
            wideLine.setTextColor(color);
            wideLine.setGravity(alignment);
            wideLine.setSingleLine(true);
            wideLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
            wideLine.setIncludeFontPadding(false);
            previewRows.addView(wideLine, new LinearLayout.LayoutParams(-2, -2));

            String smallStatus = missing ? "" : !hasAccess ? "권한"
                    : !connected ? "연결" : active <= 2
                    ? WidgetUpdater.countAndTime(this, i)
                    : count > 0 ? "×" + count : "없음";
            TextView smallLine = text(name + (smallStatus.isEmpty() ? "" : " " + smallStatus),
                    draftCompactSp, false);
            smallLine.setTextColor(color);
            smallLine.setGravity(alignment);
            smallLine.setSingleLine(true);
            smallLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
            smallLine.setIncludeFontPadding(false);
            compactPreviewRows.addView(smallLine, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void updateTitles() {
        if (titlesView == null) return;
        List<String> names = RoomStore.observedTitles(this);
        if (names.isEmpty()) titlesView.setText("아직 감지한 카톡 알림 제목이 없어.");
        else {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(5, names.size()); i++) {
                if (i > 0) b.append(" · ");
                b.append(names.get(i));
            }
            titlesView.setText("최근 감지된 이름 후보: " + b);
        }
    }

    private void showGroupDiagnostics() {
        NotificationWatcher.refreshConfiguredRooms();
        List<String> info = RoomStore.notificationMetadata(this);
        StringBuilder b = new StringBuilder("카톡이 알림에 보낸 제목만 보여줘. 메시지 본문은 읽거나 저장하지 않아.\n\n");
        if (info.isEmpty()) b.append("알림 기록이 아직 없어. 단톡방에서 카톡을 한 번 받은 뒤 다시 확인해 줘.");
        else for (int i = 0; i < Math.min(10, info.size()); i++)
            b.append(i + 1).append(". ").append(info.get(i)).append("\n\n");
        new AlertDialog.Builder(this).setTitle("단톡방 감지 진단")
                .setMessage(b.toString()).setPositiveButton("확인", null).show();
    }

    private void showRecentPicker() {
        List<String> recent = RoomStore.observedTitles(this);
        if (recent.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("감지된 톡방이 없어")
                    .setMessage("카톡 알림이 도착한 뒤 이름을 선택하거나 직접 입력해 줘.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("등록할 톡방 선택")
                .setItems(recent.toArray(new String[0]), (dialog, item) -> {
                    int active = roomCount.getSelectedItemPosition() + 1;
                    String[] slots = new String[active];
                    for (int i = 0; i < active; i++) slots[i] = (i + 1) + "번째";
                    new AlertDialog.Builder(this).setTitle(recent.get(item))
                            .setItems(slots, (which, slot) -> {
                                inputs[slot].setText(recent.get(item));
                                Toast.makeText(this, "입력했어. 저장 버튼을 눌러줘.", Toast.LENGTH_SHORT).show();
                            }).setNegativeButton("취소", null).show();
                }).setNegativeButton("취소", null).show();
    }

    private void updateVisibleInputs() {
        if (roomCount == null) return;
        int active = roomCount.getSelectedItemPosition() + 1;
        for (int i = 0; i < roomCards.length; i++)
            if (roomCards[i] != null)
                roomCards[i].setVisibility(i < active ? View.VISIBLE : View.GONE);
    }

    private void saveRooms() {
        String[] names = new String[RoomStore.ROOM_COUNT];
        String[] aliases = new String[RoomStore.ROOM_COUNT];
        for (int i = 0; i < names.length; i++) {
            names[i] = inputs[i].getText().toString().trim();
            aliases[i] = nicknameInputs[i].getText().toString().trim();
            if (aliases[i].codePointCount(0, aliases[i].length()) > 12) {
                Toast.makeText(this, "별명은 최대 12글자까지 입력할 수 있어.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        int active = roomCount.getSelectedItemPosition() + 1;
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < active; i++) {
            if (!names[i].isEmpty() && !unique.add(names[i])) {
                Toast.makeText(this, "같은 톡방을 중복 등록할 수 없어.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        RoomStore.saveRooms(this, names);
        RoomStore.saveNicknames(this, aliases);
        RoomStore.saveActiveRooms(this, active);
        NotificationWatcher.refreshConfiguredRooms();
        WidgetUpdater.updateAll(this);
        refreshUI();
        Toast.makeText(this, active + "개 톡방과 별명 저장 완료!", Toast.LENGTH_SHORT).show();
    }

    private void showTab(int chosen) {
        roomsPanel.setVisibility(chosen == 0 ? View.VISIBLE : View.GONE);
        widgetsPanel.setVisibility(chosen == 1 ? View.VISIBLE : View.GONE);
        focusPanel.setVisibility(chosen == 2 ? View.VISIBLE : View.GONE);
        if (floatingPreviewDock != null)
            floatingPreviewDock.setVisibility(chosen == 1 ? View.VISIBLE : View.GONE);
        if (pageContainer != null)
            pageContainer.setPadding(dp(17), dp(25), dp(17), dp(chosen == 1 ? 166 : 25));
        Button[] tabs = {roomsTab, widgetsTab, focusTab};
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackground(round(i == chosen ? PINK : Color.WHITE, 12));
            tabs[i].setTextColor(i == chosen ? Color.WHITE : MUTED);
        }
    }

    private LinearLayout newPanel(LinearLayout root) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(18), dp(16), dp(20));
        p.setBackground(round(Color.WHITE, 20));
        p.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(13);
        root.addView(p, lp);
        return p;
    }

    private Button tab(LinearLayout root, String title) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.rightMargin = dp(5);
        root.addView(b, lp);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(bold ? INK : MUTED);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Spinner spinner(String[] labels, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setSelection(selected);
        return s;
    }

    private Button addButton(LinearLayout parent, String title, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(14);
        b.setMinHeight(0);
        b.setTextColor(primary ? Color.WHITE : INK);
        b.setBackground(round(primary ? PINK : Color.rgb(249, 228, 238), 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(45));
        p.topMargin = dp(9);
        parent.addView(b, p);
        return b;
    }

    private ImageView photoPreview() {
        ImageView view = new ImageView(this);
        view.setBackground(round(Color.rgb(249, 235, 244), 13));
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        view.setClipToOutline(true);
        view.setContentDescription("선택한 위젯 사진 미리보기");
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(94), dp(94)));
        return view;
    }

    private void updatePhotoPreview(ImageView view) {
        Bitmap picture = PhotoStore.bitmap(this);
        if (picture == null) view.setImageResource(android.R.drawable.ic_menu_gallery);
        else view.setImageBitmap(picture);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private void addWithTop(LinearLayout root, View view, int margin) {
        LinearLayout.LayoutParams p = view instanceof ImageView
                ? new LinearLayout.LayoutParams(dp(96), dp(96))
                : new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(margin);
        root.addView(view, p);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
