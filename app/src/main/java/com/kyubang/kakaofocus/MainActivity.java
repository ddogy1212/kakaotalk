package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
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

        TextView heading = text("카톡 한눈에 3 💌", 25, true);
        heading.setTextColor(pink);
        layout.addView(heading);

        TextView description = text(
                "카톡을 열지 않고 중요한 톡방 3개의 알림만 확인해요.\n"
                + "홈 화면 2×1 위젯에서 세 줄을 한눈에 볼 수 있어요.", 14, false);
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

        addWithTop(layout, text("2. 감시할 카카오톡 채팅방 이름", 18, true), 24);
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

        Button pick = button("📋 감지된 톡방에서 선택하기");
        addWithTop(layout, pick, 6);
        pick.setOnClickListener(v -> showRecentPicker());

        Button save = button("채팅방 3개 저장");
        addWithTop(layout, save, 16);
        save.setOnClickListener(v -> saveRooms());

        addWithTop(layout, text("🎨 3. 위젯 디자인", 18, true), 24);
        addWithTop(layout, text("2×1 크기는 유지하면서 색상과 글자 크기만 바꿔.", 13, false), 7);

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

        Button applyTheme = button("위젯 디자인 적용");
        addWithTop(layout, applyTheme, 9);
        applyTheme.setOnClickListener(v -> {
            RoomStore.saveAppearance(this, themePicker.getSelectedItemPosition(),
                    sizePicker.getSelectedItemPosition());
            WidgetUpdater.updateAll(this);
            renderStatus();
            Toast.makeText(this, "위젯 색상과 글자 크기를 적용했어!", Toast.LENGTH_SHORT).show();
        });

        addWithTop(layout, text("📱 위젯 표시 미리보기", 18, true), 24);
        previewView = text("", 15, false);
        previewView.setPadding(dp(14), dp(12), dp(14), dp(12));
        previewView.setBackgroundColor(Color.rgb(239, 231, 241));
        addWithTop(layout, previewView, 8);

        addWithTop(layout, text("최근 확인된 카톡 알림 제목", 16, true), 24);
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
                    String[] slots = {"1번 칸에 등록", "2번 칸에 등록", "3번 칸에 등록"};
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

    private void saveRooms() {
        String[] rooms = new String[RoomStore.ROOM_COUNT];
        for (int i = 0; i < rooms.length; i++) {
            rooms[i] = inputs[i].getText().toString().trim();
        }
        Set<String> duplicates = new HashSet<>();
        for (String name : rooms) {
            if (!name.isEmpty() && !duplicates.add(name)) {
                Toast.makeText(this, "같은 톡방을 중복 등록할 수 없어.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        RoomStore.saveRooms(this, rooms);
        NotificationWatcher.refreshConfiguredRooms();
        WidgetUpdater.updateAll(this);
        renderStatus();
        Toast.makeText(this, "저장 완료! 이제 위젯을 추가해 주세요.", Toast.LENGTH_SHORT).show();
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
        previewView.setTextSize(10f + RoomStore.fontScale(this));
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
            String name = RoomStore.name(this, i);
            if (name.isEmpty()) name = (i + 1) + "번째 톡방 미등록";
            preview.append(name).append("    ");
            preview.append(!granted ? "권한 필요" :
                    !connected ? "연결 확인" : WidgetUpdater.countAndTime(this, i));
            if (i < RoomStore.ROOM_COUNT - 1) preview.append("\n");
        }
        previewView.setText(preview.toString());

        List<String> recent = RoomStore.observedTitles(this);
        if (recent.isEmpty()) {
            titlesView.setText("아직 감지된 제목이 없어요. 카톡 알림이 온 뒤 이 화면을 다시 열어 확인하세요.");
        } else {
            StringBuilder titles = new StringBuilder();
            for (String title : recent) titles.append("• ").append(title).append("\n");
            titlesView.setText("이름을 누르거나 위의 선택 버튼으로 등록할 수 있어.\n\n"
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
