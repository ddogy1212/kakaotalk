package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int CHOOSE_PAUSE_IMAGE = 2071;
    private static final int CHOOSE_COMPACT_IMAGE = 2072;
    private static final int INK = Color.rgb(54, 35, 49);
    private static final int MUTED = Color.rgb(117, 87, 107);
    private static final int PINK = Color.rgb(196, 60, 127);

    private final EditText[] inputs = new EditText[RoomStore.ROOM_COUNT];
    private LinearLayout roomsPanel, widgetsPanel, focusPanel;
    private Button roomsTab, widgetsTab, focusTab;
    private Spinner roomCount;
    private TextView statusView, previewView, titlesView, pauseStatus, minutesLabel;
    private ImageView pausePreview, compactPreview;

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
        scroll.addView(page);
        setContentView(scroll);

        TextView heading = text("카톡 한눈에 7 💌", 26, true);
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
        addWithTop(roomsPanel, text("아래 이름은 카카오톡 알림에 표시되는 이름과 일치해야 해.", 12, false), 9);
        for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
            EditText edit = new EditText(this);
            edit.setTextColor(INK);
            edit.setTextSize(15);
            edit.setSingleLine(true);
            edit.setHint((i + 1) + "번 톡방 이름");
            edit.setText(RoomStore.name(this, i));
            edit.setPadding(dp(12), dp(9), dp(12), dp(9));
            edit.setBackground(round(Color.rgb(251, 244, 249), 12));
            inputs[i] = edit;
            addWithTop(roomsPanel, edit, 9);
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
        addWithTop(widgetsPanel, text("🎨 위젯 디자인", 20, true), 0);
        addWithTop(widgetsPanel, text("2×1 알림 + 수능 D-day, 1×1 알림, 1×1 사진 차단 위젯을 따로 추가할 수 있어.", 13, false), 7);
        addWithTop(widgetsPanel, text("위젯 배경", 14, true), 16);
        Spinner theme = spinner(new String[]{"다크", "화이트", "반투명"},
                RoomStore.theme(this));
        addWithTop(widgetsPanel, theme, 4);
        addWithTop(widgetsPanel, text("글씨 크기", 14, true), 12);
        Spinner size = spinner(new String[]{"작게", "보통", "크게"},
                RoomStore.fontScale(this));
        addWithTop(widgetsPanel, size, 4);
        addWithTop(widgetsPanel, text("글씨 정렬", 14, true), 12);
        Spinner align = spinner(new String[]{"왼쪽", "가운데", "오른쪽"},
                RoomStore.align(this));
        addWithTop(widgetsPanel, align, 4);
        addWithTop(widgetsPanel, text("둥근 모서리 보호 여백", 14, true), 12);
        Spinner inset = spinner(new String[]{"10dp", "14dp · 추천", "18dp"},
                RoomStore.inset(this));
        addWithTop(widgetsPanel, inset, 4);
        Button apply = addButton(widgetsPanel, "위젯 디자인 저장", true);
        apply.setOnClickListener(v -> {
            RoomStore.saveAppearance(this, theme.getSelectedItemPosition(),
                    size.getSelectedItemPosition(), align.getSelectedItemPosition(),
                    inset.getSelectedItemPosition());
            WidgetUpdater.updateAll(this);
            PauseWidgetProvider.updateAll(this);
            refreshUI();
            Toast.makeText(this, "디자인을 적용했어!", Toast.LENGTH_SHORT).show();
        });

        addWithTop(widgetsPanel, text("1×1 알림 위젯 사진", 16, true), 19);
        addWithTop(widgetsPanel, text("사진을 배경으로 깔고 그 위에 톡방 알림을 표시해. 안 고르면 기본 배경이야.", 12, false), 5);
        compactPreview = photoPreview();
        addWithTop(widgetsPanel, compactPreview, 9);
        Button chooseCompact = addButton(widgetsPanel, "🖼️ 알림 위젯 사진 선택", false);
        chooseCompact.setOnClickListener(v -> pickPhoto(CHOOSE_COMPACT_IMAGE));
        Button clearCompact = addButton(widgetsPanel, "알림 위젯 사진 초기화", false);
        clearCompact.setOnClickListener(v -> {
            PhotoStore.remove(this, false);
            CompactWidgetProvider.updateAll(this);
            refreshUI();
        });

        addWithTop(widgetsPanel, text("2×1 위젯 미리보기", 16, true), 19);
        previewView = text("", 12, false);
        previewView.setPadding(dp(14), dp(12), dp(14), dp(12));
        addWithTop(widgetsPanel, previewView, 8);
        addWithTop(widgetsPanel, text("수능 D-day는 2×1에만 표시되며, 2027년 11월 18일을 기준으로 해.", 12, false), 8);
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
        addWithTop(focusPanel, duration, 8);
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
        addWithTop(focusPanel, text("1분                                             60분", 12, false), 0);
        pauseStatus = text("", 13, true);
        pauseStatus.setPadding(dp(11), dp(10), dp(11), dp(10));
        pauseStatus.setBackground(round(Color.rgb(255, 226, 239), 10));
        addWithTop(focusPanel, pauseStatus, 12);

        addWithTop(focusPanel, text("차단 위젯 사진", 16, true), 19);
        addWithTop(focusPanel, text("평소에는 사진만 보여주고, 차단 중에는 남은 시간이 사진 위에 표시돼.", 12, false), 5);
        pausePreview = photoPreview();
        addWithTop(focusPanel, pausePreview, 9);
        Button choosePause = addButton(focusPanel, "🖼️ 차단 위젯 사진 선택", false);
        choosePause.setOnClickListener(v -> pickPhoto(CHOOSE_PAUSE_IMAGE));
        Button clearPause = addButton(focusPanel, "차단 위젯 사진 초기화", false);
        clearPause.setOnClickListener(v -> {
            PhotoStore.remove(this, true);
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

    private void pickPhoto(int requestCode) {
        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
        gallery.setType("image/*");
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(gallery, "위젯에 넣을 사진 선택"), requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode != CHOOSE_PAUSE_IMAGE && requestCode != CHOOSE_COMPACT_IMAGE) return;
        boolean pause = requestCode == CHOOSE_PAUSE_IMAGE;
        boolean success = PhotoStore.save(this, data.getData(), pause);
        if (success) {
            PauseWidgetProvider.updateAll(this);
            CompactWidgetProvider.updateAll(this);
            refreshUI();
        }
        Toast.makeText(this, success ? "사진을 위젯에 적용했어!" :
                "사진을 처리하지 못했어. 다른 사진으로 다시 시도해줘.", Toast.LENGTH_LONG).show();
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
        statusView.setTextColor(granted && connected ? Color.rgb(71, 132, 91) : PINK);
        if (pauseStatus != null) {
            boolean enabled = PauseState.accessibilityEnabled(this);
            if (!enabled) pauseStatus.setText("차단 서비스 꺼짐 · 접근성 권한을 켜줘");
            else if (PauseState.active(this)) {
                long minutes = (PauseState.remaining(this) + 59_999L) / 60_000L;
                pauseStatus.setText("차단 중 · 남은 시간 약 " + minutes + "분");
            } else pauseStatus.setText("준비 완료 · 위젯을 누르면 "
                    + PauseState.durationMinutes(this) + "분 제한");
        }
        if (pausePreview != null) updatePhotoPreview(pausePreview, true);
        if (compactPreview != null) updatePhotoPreview(compactPreview, false);
        renderPreview();
        updateTitles();
    }

    private void renderPreview() {
        if (previewView == null) return;
        int theme = RoomStore.theme(this);
        previewView.setBackground(round(theme == 1 ? Color.WHITE
                : theme == 2 ? Color.rgb(91, 84, 99) : Color.rgb(42, 36, 48), 14));
        previewView.setTextColor(theme == 1 ? INK : Color.WHITE);
        previewView.setTextSize(9f + RoomStore.fontScale(this));
        previewView.setGravity(Gravity.CENTER_VERTICAL | WidgetUpdater.horizontalGravity(this));
        int side = dp(WidgetUpdater.insetDp(this));
        previewView.setPadding(side, dp(9), side, dp(9));
        StringBuilder preview = new StringBuilder(ExamDay.label());
        for (int i = 0; i < RoomStore.activeRooms(this); i++) {
            String name = RoomStore.name(this, i);
            if (name.isEmpty()) name = (i + 1) + "번 톡방 미등록";
            preview.append("\n").append(name).append("  ");
            preview.append(WidgetUpdater.hasAccess(this) && NotificationWatcher.isConnected()
                    ? WidgetUpdater.countAndTime(this, i) : "연결 확인");
        }
        previewView.setText(preview.toString());
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
        for (int i = 0; i < inputs.length; i++)
            if (inputs[i] != null) inputs[i].setVisibility(i < active ? View.VISIBLE : View.GONE);
    }

    private void saveRooms() {
        String[] names = new String[RoomStore.ROOM_COUNT];
        for (int i = 0; i < names.length; i++) names[i] = inputs[i].getText().toString().trim();
        int active = roomCount.getSelectedItemPosition() + 1;
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < active; i++) {
            if (!names[i].isEmpty() && !unique.add(names[i])) {
                Toast.makeText(this, "같은 톡방을 중복 등록할 수 없어.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        RoomStore.saveRooms(this, names);
        RoomStore.saveActiveRooms(this, active);
        NotificationWatcher.refreshConfiguredRooms();
        WidgetUpdater.updateAll(this);
        refreshUI();
        Toast.makeText(this, active + "개 톡방 설정 저장 완료!", Toast.LENGTH_SHORT).show();
    }

    private void showTab(int chosen) {
        roomsPanel.setVisibility(chosen == 0 ? View.VISIBLE : View.GONE);
        widgetsPanel.setVisibility(chosen == 1 ? View.VISIBLE : View.GONE);
        focusPanel.setVisibility(chosen == 2 ? View.VISIBLE : View.GONE);
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

    private void updatePhotoPreview(ImageView view, boolean pause) {
        Bitmap picture = PhotoStore.bitmap(this, pause);
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
