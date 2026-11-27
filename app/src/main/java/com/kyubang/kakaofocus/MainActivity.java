package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private final EditText[] inputs = new EditText[RoomStore.ROOM_COUNT];
    private TextView statusView;
    private TextView previewView;
    private TextView titlesView;
    private Spinner roomCountPicker;
    private final int ink = Color.rgb(52, 41, 58);
    private final int pink = Color.rgb(185, 63, 126);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(250, 247, 250));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(28), dp(22), dp(20));
        scroll.addView(layout);
        setContentView(scroll);

        TextView heading = text("카톡 한눈에 6 💌", 25, true);
        heading.setTextColor(pink);
        layout.addView(heading);

        TextView description = text(
                "중요한 카톡방 1~4개의 알림만 확인해요.\n"
                + "홈 화면과 굿락의 작은 위젯에서 한눈에 볼 수 있어요.", 14, false);
        addWithTop(layout, description, 12);

        statusView = text("", 14, true);
        addWithTop(layout, statusView, 18);

        Button permission = button("1. 알림 접근 설정 열기");
        addWithTop(layout, permission, 12);
        permission.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });

        Button reconnect = button("🔄 알림 연결 확인 및 새로고침");
        addWithTop(layout, reconnect, 8);
        reconnect.setOnClickListener(v -> {
            boolean connected = NotificationWatcher.reconnectIfNeeded(this);
            renderStatus();
            Toast.makeText(this,
                    connected ? "현재 알림 다시 확인했어!" : "재연결 요청했어. 계속 안 되면 알림 접근 권한을 껐다 켜줘.",
                    Toast.LENGTH_LONG).show();
        });

        Button battery = button("🔋 배터리 제한 설정 열기");
        addWithTop(layout, battery, 8);
        battery.setOnClickListener(v -> {
            Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(appInfo);
        });

        addWithTop(layout, text("2. 감시할 카카오톡 채팅방", 18, true), 24);
        addWithTop(layout, text("표시할 채팅방 개수 (1~4개)", 15, true), 10);
        roomCountPicker = new Spinner(this);
        ArrayAdapter<String> roomCounts = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"1개", "2개", "3개", "4개"});
        roomCounts.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roomCountPicker.setAdapter(roomCounts);
        roomCountPicker.setSelection(RoomStore.activeRooms(this) - 1);
        addWithTop(layout, roomCountPicker, 5);
        addWithTop(layout, text("4개 선택 시 작은 굿락 위젯에서는 글씨가 더 작아질 수 있어.", 12, false), 5);
        addWithTop(layout, text(
                "카톡 알림에 표시되는 이름과 똑같이 입력해 주세요.\n"
                + "메시지 내용은 저장하지 않아요.", 13, false), 6);

        for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
            EditText edit = new EditText(this);
            edit.setSingleLine(true);
            edit.setTextSize(16);
            edit.setTextColor(ink);
            edit.setHint((i + 1) + "번째 톡방 이름");
            edit.setText(RoomStore.name(this, i));
            inputs[i] = edit;
            addWithTop(layout, edit, 12);
        }
        updateRoomFields();
        roomCountPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View selected,
                                                  int position, long id) {
                updateRoomFields();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button pick = button("📋 감지된 톡방에서 선택하기");
        addWithTop(layout, pick, 6);
        pick.setOnClickListener(v -> showRecentPicker());

        Button groupInfo = button("🔍 단톡방 감지 정보 확인");
        addWithTop(layout, groupInfo, 4);
        groupInfo.setOnClickListener(v -> showGroupDiagnostics());

        Button save = button("선택한 채팅방 저장");
        addWithTop(layout, save, 16);
        save.setOnClickListener(v -> saveRooms());

        addWithTop(layout, text("🎨 3. 위젯 디자인", 18, true), 24);
        addWithTop(layout, text("굿락 LockStar 잠금화면에서도 쓰도록 정렬과 모서리 여백을 조절할 수 있어.", 13, false), 7);

        Spinner themePicker = new Spinner(this);
        ArrayAdapter<String> themeItems = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"다크 · 검은색", "화이트 · 흰색", "반투명 · 어두운색"});
        themeItems.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themePicker.setAdapter(themeItems);
        themePicker.setSelection(RoomStore.theme(this));
        addWithTop(layout, themePicker, 8);

        Spinner sizePicker = new Spinner(this);
        ArrayAdapter<String> sizeItems = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"작게 · 9sp", "보통 · 10sp", "크게 · 11sp"});
        sizeItems.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sizePicker.setAdapter(sizeItems);
        sizePicker.setSelection(RoomStore.fontScale(this));
        addWithTop(layout, sizePicker, 8);

        addWithTop(layout, text("✏️ 글씨 정렬", 15, true), 12);
        Spinner alignPicker = new Spinner(this);
        ArrayAdapter<String> alignItems = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"왼쪽 정렬", "가운데 정렬", "오른쪽 정렬"});
        alignItems.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        alignPicker.setAdapter(alignItems);
        alignPicker.setSelection(RoomStore.align(this));
        addWithTop(layout, alignPicker, 5);

        addWithTop(layout, text("🛡️ 둥근 모서리 보호 여백", 15, true), 12);
        Spinner insetPicker = new Spinner(this);
        ArrayAdapter<String> insetItems = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"작게 · 10dp", "보통 · 14dp (추천)", "넓게 · 18dp"});
        insetItems.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        insetPicker.setAdapter(insetItems);
        insetPicker.setSelection(RoomStore.inset(this));
        addWithTop(layout, insetPicker, 5);
        addWithTop(layout, text("굿락에서 글씨가 테두리에 걸리면 '넓게'를 선택해. 너무 좁은 위젯에서는 방 이름이 짧게 보일 수 있어.", 12, false), 6);

        Button applyTheme = button("위젯 디자인 · 정렬 적용");
        addWithTop(layout, applyTheme, 9);
        applyTheme.setOnClickListener(v -> {
            RoomStore.saveAppearance(this, themePicker.getSelectedItemPosition(),
                    sizePicker.getSelectedItemPosition(),
                    alignPicker.getSelectedItemPosition(),
                    insetPicker.getSelectedItemPosition());
            WidgetUpdater.updateAll(this);
            renderStatus();
            Toast.makeText(this, "색상 · 크기 · 정렬 · 여백을 적용했어!", Toast.LENGTH_SHORT).show();
        });

        addWithTop(layout, text("📱 위젯 표시 미리보기", 18, true), 24);
        previewView = text("", 15, false);
        previewView.setPadding(dp(14), dp(12), dp(14), dp(12));
        previewView.setBackgroundColor(Color.rgb(239, 231, 241));
        addWithTop(layout, previewView, 8);

        addWithTop(layout, text("최근 확인된 알림의 이름 후보", 16, true), 24);
        addWithTop(layout, text("단톡방에서는 보낸 사람 이름과 채팅방 이름이 다를 수 있어. 위 '단톡방 감지 정보 확인'에서 이름이 어느 위치에 표시되는지도 확인해.", 12, false), 5);
        titlesView = text("", 13, false);
        addWithTop(layout, titlesView, 8);
        titlesView.setOnClickListener(v -> showRecentPicker());

        addWithTop(layout, text(
                "감지가 멈췄다면 '새로고침'을 누르고, 설정에서 배터리 사용을 '제한 없음'으로 변경해 주세요. "
                + "알림 접근 권한을 껐다가 다시 켜는 것도 도움이 됩니다.\n\n"
                + "알림 숫자는 현재 휴대폰에 남아 있는 알림 기준의 추정치예요. "
                + "실제 안 읽은 메시지 수와 다를 수 있어요. "
                + "알림이 지워지거나, 채팅방 이름이 알림에 없으면 감지하지 못할 수 있어요.\n\n"
                + "기기에만 저장됩니다. 방해금지 모드를 해제할 필요는 없어요.", 12, false), 22);
        renderStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        RecoveryScheduler.scheduleIfWidgets(this);
        NotificationWatcher.reconnectIfNeeded(this);
        renderStatus();
    }

    private void showGroupDiagnostics() {
        NotificationWatcher.refreshConfiguredRooms();
        List<String> info = RoomStore.notificationMetadata(this);
        StringBuilder details = new StringBuilder();
        details.append("카카오톡이 실제 알림에 제공한 대화 제목·보조 제목·알림 제목만 확인해. 메시지 내용은 저장하지 않아.\n\n");
        if (info.isEmpty()) {
            details.append("감지된 카톡 알림이 아직 없어. 단톡방에서 메시지가 온 후 새로고침하고 다시 확인해 줘.");
        } else {
            for (int i = 0; i < Math.min(10, info.size()); i++) {
                details.append(i + 1).append(". ").append(info.get(i)).append("\n\n");
            }
            details.append("※ 대화명이나 보조 제목에 실제 단톡방 이름이 보인다면 그 이름으로 등록해. ");
            details.append("셋 다 방 이름을 제공하지 않으면 이 앱에서 방을 확실하게 구분할 수 없어.");
        }
        new AlertDialog.Builder(this)
                .setTitle("단톡방 알림 감지 진단")
                .setMessage(details.toString())
                .setPositiveButton("확인", null)
                .show();
    }

    private void showRecentPicker() {
        List<String> recent = RoomStore.observedTitles(this);
        if (recent.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("아직 감지된 톡방이 없어")
                    .setMessage("알림 접근 권한을 켠 뒤 카카오톡 알림을 한 번 받으면 이름이 표시돼. 지금은 직접 입력해도 돼.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("등록할 톡방 이름 선택")
                .setItems(recent.toArray(new String[0]), (d, selectedIndex) -> {
                    String selectedName = recent.get(selectedIndex);
                    int active = roomCountPicker.getSelectedItemPosition() + 1;
                    String[] slots = new String[active];
                    for (int i = 0; i < active; i++) slots[i] = (i + 1) + "번 칸에 등록";
                    new AlertDialog.Builder(this)
                            .setTitle(selectedName)
                            .setItems(slots, (d2, slot) -> {
                                inputs[slot].setText(selectedName);
                                Toast.makeText(this,
                                        (slot + 1) + "번에 입력했어. 저장 버튼까지 눌러줘!",
                                        Toast.LENGTH_LONG).show();
                            })
                            .setNegativeButton("취소", null)
                            .show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void updateRoomFields() {
        if (roomCountPicker == null) return;
        int active = roomCountPicker.getSelectedItemPosition() + 1;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) inputs[i].setVisibility(i < active ? View.VISIBLE : View.GONE);
        }
    }

    private void saveRooms() {
        String[] rooms = new String[RoomStore.ROOM_COUNT];
        for (int i = 0; i < rooms.length; i++) {
            rooms[i] = inputs[i].getText().toString().trim();
        }
        Set<String> duplicates = new HashSet<>();
        int active = roomCountPicker.getSelectedItemPosition() + 1;
        for (int i = 0; i < active; i++) {
            String name = rooms[i];
            if (!name.isEmpty() && !duplicates.add(name)) {
                Toast.makeText(this, "같은 톡방을 중복 등록할 수 없어.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        RoomStore.saveRooms(this, rooms);
        RoomStore.saveActiveRooms(this, active);
        NotificationWatcher.refreshConfiguredRooms();
        WidgetUpdater.updateAll(this);
        renderStatus();
        Toast.makeText(this, active + "개 채팅방 설정을 저장했어!", Toast.LENGTH_SHORT).show();
    }

    private void renderStatus() {
        if (statusView == null) return;
        boolean granted = WidgetUpdater.hasAccess(this);
        boolean connected = NotificationWatcher.isConnected();
        statusView.setText(!granted ? "⚠ 알림 접근 권한이 필요해요"
                : connected ? "✓ 알림 감지 연결됨 (방해금지 유지 가능)"
                : "… 감지 서비스 연결 끊김 · 새로고침을 눌러줘");
        statusView.setTextColor(!granted ? Color.rgb(181, 79, 64) : pink);

        int selectedTheme = RoomStore.theme(this);
        int bg = selectedTheme == 1 ? Color.rgb(255, 249, 252)
                : selectedTheme == 2 ? Color.rgb(77, 69, 82) : Color.rgb(41, 36, 47);
        previewView.setBackgroundColor(bg);
        previewView.setTextColor(selectedTheme == 1 ? ink : Color.WHITE);
        previewView.setTextSize(9f + RoomStore.fontScale(this));
        previewView.setGravity(Gravity.CENTER_VERTICAL | WidgetUpdater.horizontalGravity(this));
        int safe = dp(WidgetUpdater.insetDp(this));
        previewView.setPadding(safe, dp(6), safe, dp(6));
        StringBuilder preview = new StringBuilder();
        int active = RoomStore.activeRooms(this);
        for (int i = 0; i < active; i++) {
            String name = RoomStore.name(this, i);
            if (name.isEmpty()) name = (i + 1) + "번째 톡방 미등록";
            preview.append(name).append("    ");
            preview.append(!granted ? "권한 필요" :
                    !connected ? "연결 확인" : WidgetUpdater.countAndTime(this, i));
            if (i < active - 1) preview.append("\n");
        }
        previewView.setText(preview.toString());

        List<String> recent = RoomStore.observedTitles(this);
        if (recent.isEmpty()) {
            titlesView.setText("아직 감지된 제목이 없어요. 카톡 알림이 온 뒤 이 화면을 다시 열어 확인하세요.");
        } else {
            StringBuilder titles = new StringBuilder();
            for (String title : recent) titles.append("• ").append(title).append("\n");
            titlesView.setText("이름이 실제 톡방명인지 확인한 뒤 선택해.\n\n"
                    + titles.toString().trim());
        }
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(ink);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private void addWithTop(LinearLayout parent, View view, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        parent.addView(view, lp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
