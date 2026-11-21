package com.kyubang.kakaofocus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

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

        TextView heading = text("카톡 한눈에 💌", 25, true);
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

        Button save = button("채팅방 3개 저장");
        addWithTop(layout, save, 16);
        save.setOnClickListener(v -> saveRooms());

        addWithTop(layout, text("📱 위젯 표시 미리보기", 18, true), 24);
        previewView = text("", 15, false);
        previewView.setPadding(dp(14), dp(12), dp(14), dp(12));
        previewView.setBackgroundColor(Color.rgb(239, 231, 241));
        addWithTop(layout, previewView, 8);

        addWithTop(layout, text("최근 확인된 카톡 알림 제목", 16, true), 24);
        titlesView = text("", 13, false);
        addWithTop(layout, titlesView, 8);

        addWithTop(layout, text(
                "알림 숫자는 현재 휴대폰에 남아 있는 알림 기준의 추정치예요. "
                + "실제 안 읽은 메시지 수와 다를 수 있어요. "
                + "알림이 지워지거나, 채팅방 이름이 알림에 없으면 감지하지 못할 수 있어요.\n\n"
                + "기기에만 저장됩니다. 방해금지 모드를 해제할 필요는 없어요.", 12, false), 22);
        renderStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus();
    }

    private void saveRooms() {
        String[] rooms = new String[RoomStore.ROOM_COUNT];
        for (int i = 0; i < rooms.length; i++) {
            rooms[i] = inputs[i].getText().toString().trim();
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
        boolean connected = RoomStore.connected(this);
        statusView.setText(!granted ? "⚠ 알림 접근 권한이 필요해요"
                : connected ? "✓ 알림 감지 연결됨 (방해금지 유지 가능)"
                : "… 권한 허용됨 · 알림 감지 연결 대기 중");
        statusView.setTextColor(!granted ? Color.rgb(181, 79, 64) : pink);

        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < RoomStore.ROOM_COUNT; i++) {
            String name = RoomStore.name(this, i);
            if (name.isEmpty()) name = (i + 1) + "번째 톡방 미등록";
            int count = RoomStore.count(this, i);
            preview.append(!granted ? name + ": 권한 필요" :
                    !connected ? name + ": 감지 대기 중" :
                    count > 0 ? name + "에서 알림 ×" + count : name + ": 알림 없음");
            if (i < RoomStore.ROOM_COUNT - 1) preview.append("\n");
        }
        previewView.setText(preview.toString());

        List<String> recent = RoomStore.observedTitles(this);
        if (recent.isEmpty()) {
            titlesView.setText("아직 감지된 제목이 없어요. 카톡 알림이 온 뒤 이 화면을 다시 열어 확인하세요.");
        } else {
            StringBuilder titles = new StringBuilder();
            for (String title : recent) titles.append("• ").append(title).append("\n");
            titlesView.setText(titles.toString().trim());
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
