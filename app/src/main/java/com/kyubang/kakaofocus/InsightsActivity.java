package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.Toast;
import android.text.InputType;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Modern, offline analytics dashboard. No network calls or message database access.
 * "Unread" is deliberately described as ACTIVE notification previews, not Kakao's
 * authoritative unread count. The 7-day history is notification arrival frequency.
 */
public final class InsightsActivity extends Activity {
    private static final int INK = Color.rgb(48, 36, 52);
    private static final int MUTED = Color.rgb(137, 121, 139);
    private static final int PINK = Color.rgb(212, 71, 130);
    private static final int BACKGROUND = Color.rgb(249, 247, 251);
    private static final int BLUSH = Color.rgb(251, 234, 242);
    private LinearLayout page;
    // Session-only credential: never stored in preferences, backup or source code.
    private static volatile String sessionHfToken = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(19), dp(20), dp(35));
        scroll.addView(page);
        setContentView(scroll);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        NotificationWatcher.refreshConfiguredRooms();
        render();
    }

    private void render() {
        if (page == null) return;
        page.removeAllViews();
        header();
        hero();
        addWeekChart();
        sectionTitle("톡방별 알림 요약", "ROOM DETAILS", 24);
        int active = RoomStore.activeRooms(this);
        int registered = 0;
        for (int i = 0; i < active; i++) {
            if (RoomStore.name(this, i).trim().isEmpty()) continue;
            registered++;
            addRoomCard(i);
        }
        addOtherChats();
        if (registered == 0 && OtherChatsStore.list(this).isEmpty()) {
            LinearLayout blank = card(Color.WHITE, 20, 18);
            blank.addView(text("아직 등록된 톡방이 없어", 16, true, INK));
            addTop(blank, text("톡방 관리에서 감지할 방 이름을 등록하면 여기서 분석할 수 있어.",
                    12, false, MUTED), 8);
            addTop(page, blank, 13);
        }
        privacyCard();
        addTop(page, text("알림 기록은 이 기기에만 저장돼. 과거 카톡 대화나 읽음 상태를 "
                + "가져오는 기능은 없어.", 11, false, MUTED), 18);
    }

    private void header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, false, INK);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 13));
        bar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(0, -2, 1f);
        headingParams.leftMargin = dp(12);
        bar.addView(heading, headingParams);
        heading.addView(text("KAKAO INSIGHTS", 10, true, PINK));
        addTop(heading, text("알림 인사이트", 24, true, INK), 1);
        TextView refresh = text("↻", 27, true, PINK);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(round(BLUSH, 13));
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(42), dp(42)));
        refresh.setOnClickListener(v -> {
            NotificationWatcher.refreshConfiguredRooms();
            render();
        });
        page.addView(bar);
        addTop(page, text("톡방별 알림 흐름과 지금 보이는 메시지를 한 화면에서.",
                12, false, MUTED), 12);
    }

    private void hero() {
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(42, 31, 49), Color.rgb(97, 53, 87)});
        gradient.setCornerRadius(dp(23));
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(20), dp(19), dp(20), dp(21));
        banner.setBackground(gradient);
        addTop(page, banner, 20);
        banner.addView(text("TODAY'S SNAPSHOT", 10, true, Color.rgb(245, 181, 216)));
        int current = 0, today = 0, week = 0;
        for (int i = 0; i < RoomStore.activeRooms(this); i++) {
            if (RoomStore.name(this, i).isEmpty()) continue;
            current += RoomStore.count(this, i);
            today += RoomInsightsStore.daily(this, i, 0);
            week += RoomInsightsStore.total(this, i, 7);
        }
        today += OtherChatsStore.total(this, 0);
        for (int d = 0; d < 7; d++) week += OtherChatsStore.total(this, d);
        LinearLayout numbers = new LinearLayout(this);
        numbers.setGravity(Gravity.CENTER_VERTICAL);
        addTop(banner, numbers, 13);
        metric(numbers, "현재 알림", current + "", "표시 중 · 추정", 1f);
        View separator = new View(this);
        separator.setBackgroundColor(Color.argb(65, 255, 255, 255));
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(dp(1), dp(57));
        separatorParams.leftMargin = dp(8);
        separatorParams.rightMargin = dp(12);
        numbers.addView(separator, separatorParams);
        metric(numbers, "오늘 감지", today + "", "알림 이벤트", 1f);
        addTop(banner, text("지난 7일 총 " + week
                        + "회 감지 · 위젯의 x값은 실제 안 읽은 메시지 수가 아닐 수 있어.",
                11, false, Color.rgb(238, 212, 229)), 15);
    }

    private void metric(LinearLayout row, String title, String amount,
                        String caption, float weight) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        row.addView(box, new LinearLayout.LayoutParams(0, -2, weight));
        box.addView(text(title, 12, false, Color.rgb(236, 215, 231)));
        addTop(box, text(amount, 35, true, Color.WHITE), 3);
        addTop(box, text(caption, 10, false, Color.rgb(229, 192, 215)), 1);
    }

    private void addWeekChart() {
        sectionTitle("최근 7일 알림 흐름", "WEEKLY ACTIVITY", 22);
        LinearLayout chartCard = card(Color.WHITE, 21, 18);
        addTop(page, chartCard, 12);
        int[] dayTotals = new int[7];
        int maximum = 1;
        for (int d = 0; d < 7; d++) {
            int ago = 6 - d;
            for (int i = 0; i < RoomStore.activeRooms(this); i++)
                if (!RoomStore.name(this, i).isEmpty())
                    dayTotals[d] += RoomInsightsStore.daily(this, i, ago);
            dayTotals[d] += OtherChatsStore.total(this, ago);
            maximum = Math.max(maximum, dayTotals[d]);
        }
        chartCard.addView(text("카카오톡 알림 수신 횟수 · 톡방 합계", 11, false, MUTED));
        LinearLayout chart = new LinearLayout(this);
        chart.setGravity(Gravity.BOTTOM);
        addTop(chartCard, chart, 11);
        for (int d = 0; d < 7; d++) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            chart.addView(column, new LinearLayout.LayoutParams(0, dp(127), 1f));

            TextView number = text(dayTotals[d] == 0 ? "·" : Integer.toString(dayTotals[d]),
                    10, true, d == 6 ? PINK : MUTED);
            number.setGravity(Gravity.CENTER);
            column.addView(number);
            LinearLayout.LayoutParams barParams =
                    new LinearLayout.LayoutParams(dp(18),
                            dp(6 + (int) (67f * dayTotals[d] / maximum)));
            barParams.topMargin = dp(6);
            View bar = new View(this);
            bar.setBackground(round(d == 6 ? PINK : Color.rgb(242, 191, 217), 8));
            column.addView(bar, barParams);
            TextView day = text(shortDate(6 - d), 10, d == 6, d == 6 ? PINK : MUTED);
            day.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(-1, -2);
            dayParams.topMargin = dp(8);
            column.addView(day, dayParams);
        }
        if (totalAll(7) == 0)
            addTop(chartCard, text("새 버전 설치 이후 감지된 알림부터 기록돼.",
                    11, false, MUTED), 10);
    }

    private static String shortDate(int daysAgo) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("E", Locale.KOREA).format(c.getTime());
    }

    private int totalAll(int days) {
        int total = 0;
        for (int i = 0; i < RoomStore.activeRooms(this); i++)
            if (!RoomStore.name(this, i).isEmpty())
                total += RoomInsightsStore.total(this, i, days);
        for (int d = 0; d < days && d < 7; d++) total += OtherChatsStore.total(this, d);
        return total;
    }

    private void addRoomCard(int index) {
        String name = RoomStore.displayName(this, index);
        LinearLayout card = card(Color.WHITE, 20, 17);
        addTop(page, card, 13);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(heading);
        TextView circle = text(String.format(Locale.KOREA, "%02d", index + 1),
                15, true, PINK);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(round(BLUSH, 14));
        heading.addView(circle, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(11);
        heading.addView(titles, nameParams);
        TextView nameView = text(name, 17, true, INK);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(nameView);
        addTop(titles, text("오늘 " + RoomInsightsStore.daily(this, index, 0)
                        + "회  ·  7일 " + RoomInsightsStore.total(this, index, 7) + "회",
                11, false, MUTED), 4);

        TextView current = text(RoomStore.formattedCount(this, RoomStore.count(this, index)),
                14, true, PINK);
        current.setPadding(dp(9), dp(7), dp(9), dp(7));
        current.setBackground(round(BLUSH, 12));
        heading.addView(current);

        addTop(card, text("7일간 알림 빈도", 11, true, MUTED), 17);
        LinearLayout bars = new LinearLayout(this);
        bars.setGravity(Gravity.CENTER_VERTICAL);
        addTop(card, bars, 9);
        int max = 1;
        for (int d = 0; d < 7; d++)
            max = Math.max(max, RoomInsightsStore.daily(this, index, d));
        for (int d = 6; d >= 0; d--) {
            int count = RoomInsightsStore.daily(this, index, d);
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, dp(9), 1f);
            if (d != 0) slot.rightMargin = dp(4);
            View background = new View(this);
            background.setBackground(round(Color.rgb(247, 235, 242), 6));
            bars.addView(background, slot);
            // Small bars follow the same oldest-to-newest order as the weekly graph.
            background.setBackground(round(count == 0
                    ? Color.rgb(248, 237, 243)
                    : d == 0 ? PINK : Color.rgb(232, 149, 190), 6));
            background.setAlpha(count == 0 ? 1f : 0.35f + .65f * count / max);
        }

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(248, 236, 243));
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(17);
        dividerParams.bottomMargin = dp(13);
        card.addView(divider, dividerParams);

        List<String> snippets = RoomInsightsStore.activePreviews(this, index);
        TextView previewHeader = text("알림 미리보기 요약", 13, true, INK);
        card.addView(previewHeader);
        addTop(card, text("지금 남아 있는 알림에서 발췌 · 최대 3개", 10, false, MUTED), 4);
        if (!RoomInsightsStore.previewsEnabled(this)) {
            addTop(card, text("미리보기 저장을 껐어. 아래 개인정보 설정에서 다시 켤 수 있어.",
                    12, false, MUTED), 11);
        } else if (!WidgetUpdater.hasAccess(this) || !NotificationWatcher.isConnected()) {
            addTop(card, text("알림 접근 권한이나 감지 서비스 연결을 확인해 줘.",
                    12, false, MUTED), 11);
        } else if (snippets.isEmpty()) {
            addTop(card, text(RoomStore.count(this, index) == 0
                    ? "현재 남아 있는 메시지 알림이 없어."
                    : "알림은 있지만 카카오톡이 문구를 제공하지 않았어.",
                    12, false, MUTED), 11);
        } else {
            for (String line : snippets.subList(0, Math.min(3, snippets.size()))) {
                LinearLayout bubble = new LinearLayout(this);
                bubble.setGravity(Gravity.TOP);
                bubble.setPadding(dp(11), dp(10), dp(11), dp(10));
                bubble.setBackground(round(Color.rgb(252, 247, 250), 11));
                LinearLayout.LayoutParams bubbleParams =
                        new LinearLayout.LayoutParams(-1, -2);
                bubbleParams.topMargin = dp(8);
                card.addView(bubble, bubbleParams);
                TextView dot = text("●", 9, false, PINK);
                LinearLayout.LayoutParams dotParams =
                        new LinearLayout.LayoutParams(dp(16), -2);
                dotParams.topMargin = dp(2);
                bubble.addView(dot, dotParams);
                TextView item = text(line, 12, false, INK);
                item.setMaxLines(3);
                item.setEllipsize(TextUtils.TruncateAt.END);
                bubble.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
            }
        }
        if (!snippets.isEmpty() && RoomInsightsStore.previewsEnabled(this)) {
            TextView extract = text("📝 기기 내 핵심 정리", 13, true, INK);
            extract.setGravity(Gravity.CENTER);
            extract.setPadding(dp(8), dp(13), dp(8), dp(13));
            extract.setBackground(round(Color.rgb(246, 232, 240), 12));
            addTop(card, extract, 12);
            extract.setOnClickListener(v -> showLocalSummary(name, snippets));

            TextView cloud = text("✨ AI로 요약 (선택 · 외부 서비스)", 13, true, PINK);
            cloud.setGravity(Gravity.CENTER);
            cloud.setPadding(dp(8), dp(13), dp(8), dp(13));
            cloud.setBackground(round(BLUSH, 12));
            addTop(card, cloud, 8);
            cloud.setOnClickListener(v -> showCloudSummaryDialog(name, snippets));
        }
        if (!RoomStore.boundConversation(this, index).isEmpty())
            addTop(card, text("연결 방식 · 단톡방 직접 연결", 10, false, PINK), 10);
    }

    private void privacyCard() {
        sectionTitle("개인정보 설정", "LOCAL ONLY", 25);
        LinearLayout box = card(Color.WHITE, 20, 18);
        addTop(page, box, 12);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(row);
        LinearLayout label = new LinearLayout(this);
        label.setOrientation(LinearLayout.VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        label.addView(text("알림 문구 미리보기", 14, true, INK));
        addTop(label, text("끄면 저장된 미리보기 문구를 즉시 삭제해.", 11, false, MUTED), 4);
        Switch toggle = new Switch(this);
        toggle.setThumbTintList(ColorStateList.valueOf(PINK));
        toggle.setChecked(RoomInsightsStore.previewsEnabled(this));
        row.addView(toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            RoomInsightsStore.setPreviewsEnabled(this, checked);
            if (checked) NotificationWatcher.refreshConfiguredRooms();
            render();
        });

        addTop(box, text("통계는 톡방별 알림 수신 횟수만 저장하며, 메시지 내용은 "
                + "내보내기 ZIP에 포함하지 않아.", 11, false, MUTED), 12);
        TextView clear = text("모든 인사이트 기록 삭제", 12, true, PINK);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(10), dp(13), dp(10), dp(13));
        clear.setBackground(round(BLUSH, 13));
        addTop(box, clear, 13);
        clear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("알림 통계와 미리보기 삭제")
                        .setMessage("지난 알림 횟수와 미리보기를 모두 지워. "
                                + "등록된 톡방이나 위젯 디자인은 그대로 유지해.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("기록 삭제", (dialog, which) -> {
                            RoomInsightsStore.clearAll(this);
                            OtherChatsStore.clearAll(this);
                            render();
                        }).show());
    }

    private void addOtherChats() {
        List<OtherChatsStore.Chat> others = OtherChatsStore.list(this);
        sectionTitle("그 외 카톡방 알림", "ALL KAKAO ROOMS", 24);
        LinearLayout group = card(Color.WHITE, 20, 17);
        addTop(page, group, 12);
        group.addView(text("등록하지 않은 카톡방도 자동 집계해. "
                + "대화별 식별자가 없으면 이름 미제공 알림으로 묶일 수 있어.",
                11, false, MUTED));
        if (others.isEmpty()) {
            addTop(group, text("아직 새로 감지한 다른 카톡방 알림이 없어.",
                    12, false, MUTED), 12);
            return;
        }
        int shown = 0;
        for (OtherChatsStore.Chat chat : others) {
            if (shown++ >= 15) break;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            addTop(group, row, 13);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView label = text(chat.label, 14, true, INK);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            left.addView(label);
            addTop(left, text("오늘 " + chat.days[0] + "회 · 최근 7일 "
                    + chat.total() + "회", 11, false, MUTED), 4);
            TextView total = text(chat.total() + "회", 13, true, PINK);
            total.setPadding(dp(9), dp(6), dp(9), dp(6));
            total.setBackground(round(BLUSH, 11));
            row.addView(total);
        }
        if (others.size() > 15)
            addTop(group, text("상위 15개만 표시 · 나머지는 전체 통계에 합산",
                    11, false, MUTED), 13);
    }

    private void showLocalSummary(String name, List<String> lines) {
        StringBuilder body = new StringBuilder();
        String[] keywords = {"제출","마감","시험","수행","과제","숙제",
                "준비물","공지","내일","모임","시간","일정"};
        java.util.ArrayList<String> priority = new java.util.ArrayList<>();
        java.util.ArrayList<String> other = new java.util.ArrayList<>();
        for (String raw : lines) {
            String line = RoomInsightsStore.cleanPreview(raw);
            if (line.isEmpty()) continue;
            boolean important = false;
            for (String key : keywords) if (line.contains(key)) {important = true; break;}
            if (important) priority.add(line); else other.add(line);
        }
        int count=0;
        for (String line : priority) {
            if (count++ >= 4) break;
            body.append("• ").append(line).append("\n\n");
        }
        for (String line : other) {
            if (count++ >= 4) break;
            body.append("• ").append(line).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle(name + " · 기기 내 핵심 정리")
                .setMessage("알림 문구 중 중요 키워드를 우선 보여줘. "
                        + "AI가 내용을 재구성한 요약은 아니야.\n\n" + body)
                .setPositiveButton("확인", null).show();
    }

    private void showCloudSummaryDialog(String name, List<String> lines) {
        if (sessionHfToken.isEmpty()) {
            EditText keyInput = new EditText(this);
            keyInput.setHint("Hugging Face 액세스 토큰 (이번 실행에만 유지)");
            keyInput.setSingleLine(true);
            keyInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            LinearLayout holder = new LinearLayout(this);
            holder.setOrientation(LinearLayout.VERTICAL);
            holder.setPadding(dp(18), dp(8), dp(18), 0);
            holder.addView(keyInput);
            new AlertDialog.Builder(this).setTitle("외부 AI 요약 연결")
                    .setMessage("Hugging Face 계정의 액세스 토큰이 필요해. "
                            + "무료 크레딧 유무와 API 비용은 계정마다 달라. "
                            + "앱은 결제를 대신 처리하지 않아.\n"
                            + "토큰은 이번 앱 실행 중 메모리에만 보관하고 파일에는 저장하지 않아.")
                    .setView(holder)
                    .setNegativeButton("취소", null)
                    .setPositiveButton("토큰 적용", (dialog, which) -> {
                        String token = keyInput.getText().toString().trim();
                        if (token.isEmpty()) {
                            Toast.makeText(this, "토큰을 입력해 줘.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        sessionHfToken = token;
                        showCloudSummaryDialog(name, lines);
                    }).show();
            return;
        }
        StringBuilder preview = new StringBuilder();
        for (String text : lines) {
            if (preview.length() > 5200) break;
            preview.append("• ").append(text).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("외부 AI에 알림 전송")
                .setMessage("아래 문구를 Hugging Face 추론 서버에 전송할까? "
                        + "대화 상대의 메시지가 포함될 수 있어. "
                        + "API 정책에 따라 처리·보관될 수 있으니 민감한 내용은 전송하지 마.\n\n"
                        + preview)
                .setNegativeButton("취소", null)
                .setNeutralButton("토큰 초기화", (d, w) -> {sessionHfToken = "";})
                .setPositiveButton("동의하고 AI 요약", (dialog, which) ->
                        runCloudSummary(name, lines)).show();
    }

    private void runCloudSummary(String name, List<String> lines) {
        final String token = sessionHfToken;
        Toast.makeText(this, "선택한 알림 문구만 전송해 요약 중이야.", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String result = HfSummaryClient.summarize(token, name, lines);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(this)
                            .setTitle(name + " · AI 요약")
                            .setMessage("외부 AI가 제공한 결과야. 원문과 비교해 확인해 줘.\n\n"
                                    + result).setPositiveButton("확인", null).show();
                });
            } catch (Exception failed) {
                String error = failed.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(this).setTitle("AI 요약을 완료하지 못했어")
                            .setMessage(error == null ? "API 연결을 확인해 줘." : error)
                            .setPositiveButton("확인", null)
                            .setNeutralButton("기기 내 정리 보기",
                                    (d, w) -> showLocalSummary(name, lines)).show();
                });
            }
        }, "hf-kakao-summary").start();
    }

    private void sectionTitle(String title, String eyebrow, int top) {
        LinearLayout label = new LinearLayout(this);
        label.setOrientation(LinearLayout.VERTICAL);
        addTop(page, label, top);
        label.addView(text(eyebrow, 10, true, PINK));
        addTop(label, text(title, 19, true, INK), 4);
    }

    private LinearLayout card(int bg, int radius, int padding) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        layout.setBackground(round(bg, radius));
        layout.setElevation(dp(2));
        return layout;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView text(String text, int sizeSp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addTop(LinearLayout parent, View child, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(marginDp);
        parent.addView(child, lp);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
