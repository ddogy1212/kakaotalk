package com.kyubang.kakaofocus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * KakaoTalk screen time dashboard. Reads Android usage history only when open.
 * No raw usage data is persisted, exported, uploaded or used for tracking others.
 */
public final class KakaoUsageActivity extends Activity {
    private static final int INK = Color.rgb(46, 37, 55);
    private static final int MUTED = Color.rgb(130, 118, 139);
    private static final int ROSE = Color.rgb(202, 67, 133);
    private static final int LAVENDER = Color.rgb(124, 95, 181);
    private static final int BG = Color.rgb(250, 247, 252);
    private static final int FAINT = Color.rgb(255, 237, 246);
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(19), dp(20), dp(34));
        scroll.addView(content);
        setContentView(scroll);
        draw();
    }

    @Override protected void onResume() {
        super.onResume();
        draw();
    }

    private void draw() {
        if (content == null) return;
        content.removeAllViews();
        header();

        if (!KakaoUsageReader.hasPermission(this)) {
            requestAccess();
            return;
        }
        KakaoUsageReader.Report report = KakaoUsageReader.queryLastWeek(this);
        if (report.unavailable) {
            LinearLayout notice = card(Color.WHITE, 22, 18);
            addTop(content, notice, 20);
            notice.addView(txt("사용 기록을 아직 읽을 수 없어", 17, true, INK));
            addTop(notice, txt("휴대폰 잠금이 풀려 있는지, 사용 기록 접근 권한이 켜져 있는지 확인해 줘.",
                    13, false, MUTED), 9);
            addTop(notice, action("사용 기록 설정 열기", true,
                    () -> openUsageSettings()), 16);
            return;
        }
        overview(report);
        graph(report);
        dayList(report);
        explanations(report);
    }

    private void header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(bar);
        TextView back = txt("‹", 32, false, INK);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 13));
        bar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());

        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1f);
        headingParams.leftMargin = dp(11);
        bar.addView(headings, headingParams);
        headings.addView(txt("SCREEN TIME", 10, true, ROSE));
        addTop(headings, txt("카톡 사용 분석", 24, true, INK), 2);

        TextView refresh = txt("↻", 28, true, ROSE);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(round(FAINT, 13));
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(42), dp(42)));
        refresh.setOnClickListener(v -> draw());

        addTop(content, txt("앱을 실제로 사용한 시간과 실행 횟수를 확인해.", 12, false, MUTED), 12);
    }

    private void requestAccess() {
        LinearLayout notice = card(Color.WHITE, 22, 20);
        addTop(content, notice, 21);

        TextView icon = txt("◷", 39, false, ROSE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(FAINT, 22));
        notice.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        addTop(notice, txt("사용 기록 접근 권한이 필요해", 19, true, INK), 16);
        addTop(notice, txt("Android가 기록한 카카오톡의 실행·종료 정보를 읽어 "
                        + "오늘 사용 시간과 최근 7일 변화를 계산해.",
                13, false, MUTED), 9);
        addTop(notice, txt("권한은 직접 켜야 해. 이 기능은 데이터를 서버로 전송하거나 "
                        + "다른 앱의 사용 시간을 화면에 표시하지 않아.",
                12, false, MUTED), 9);

        addTop(notice, action("사용 기록 접근 허용하기", true,
                () -> openUsageSettings()), 19);
        addTop(notice, action("권한을 켠 후 다시 확인", false,
                () -> draw()), 9);
        addTop(content, txt("설정 → 특별한 접근 → 사용 기록 접근에서 "
                        + "‘카톡 한눈에 정식’을 찾아 허용해 줘.",
                12, false, MUTED), 15);
    }

    private void openUsageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (RuntimeException unavailable) {
            // Some manufacturers omit the specific Settings page.
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void overview(KakaoUsageReader.Report report) {
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(40, 34, 57), Color.rgb(101, 54, 95)});
        gradient.setCornerRadius(dp(24));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(21), dp(19), dp(21), dp(20));
        panel.setBackground(gradient);
        addTop(content, panel, 19);

        panel.addView(txt("TODAY · 카카오톡", 11, true, Color.rgb(250, 188, 218)));
        addTop(panel, txt(formatDuration(report.todayMs()), 37, true, Color.WHITE), 6);
        addTop(panel, txt("오늘 사용 시간 · Android 사용 기록 기준", 11, false,
                Color.rgb(228, 207, 225)), 2);

        View line = new View(this);
        line.setBackgroundColor(Color.argb(65, 255, 255, 255));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(1));
        lineParams.topMargin = dp(19);
        lineParams.bottomMargin = dp(17);
        panel.addView(line, lineParams);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(metrics);
        metric(metrics, "오늘 실행", report.todayLaunches() + "회");
        View middle = new View(this);
        middle.setBackgroundColor(Color.argb(60, 255, 255, 255));
        LinearLayout.LayoutParams middleParams =
                new LinearLayout.LayoutParams(dp(1), dp(47));
        middleParams.leftMargin = dp(12);
        middleParams.rightMargin = dp(12);
        metrics.addView(middle, middleParams);
        metric(metrics, "7일 총사용", formatDuration(report.weekMs()));
    }

    private void metric(LinearLayout metrics, String label, String value) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        metrics.addView(block, new LinearLayout.LayoutParams(0, -2, 1f));
        block.addView(txt(label, 11, false, Color.rgb(233, 210, 227)));
        addTop(block, txt(value, 19, true, Color.WHITE), 5);
    }

    private void graph(KakaoUsageReader.Report report) {
        title("최근 7일 사용 시간", "WEEKLY TREND", 23);
        LinearLayout panel = card(Color.WHITE, 20, 18);
        addTop(content, panel, 12);
        panel.addView(txt("앱이 화면에 표시된 것으로 추정되는 시간", 11, false, MUTED));

        long max = 60000L;
        for (KakaoUsageReader.Day day : report.days)
            max = Math.max(max, day.foregroundMs);
        LinearLayout bars = new LinearLayout(this);
        bars.setGravity(Gravity.BOTTOM);
        addTop(panel, bars, 16);

        for (int i = 0; i < report.days.length; i++) {
            KakaoUsageReader.Day day = report.days[i];
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            bars.addView(column, new LinearLayout.LayoutParams(0, dp(151), 1f));

            String amount = day.foregroundMs == 0 ? "·"
                    : day.foregroundMs >= 3600000L
                    ? (day.foregroundMs / 3600000L) + "h"
                    : Math.max(1L, day.foregroundMs / 60000L) + "m";
            TextView number = txt(amount, 10, true, i == 6 ? ROSE : MUTED);
            number.setGravity(Gravity.CENTER);
            column.addView(number);

            int height = 7 + Math.round(85f * day.foregroundMs / max);
            View bar = new View(this);
            bar.setBackground(round(i == 6 ? ROSE : Color.rgb(229, 182, 219), 8));
            LinearLayout.LayoutParams barParams =
                    new LinearLayout.LayoutParams(dp(20), dp(height));
            barParams.topMargin = dp(6);
            column.addView(bar, barParams);

            TextView label = txt(weekday(day.start), 10, i == 6,
                    i == 6 ? ROSE : MUTED);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(11);
            column.addView(label, labelParams);
        }
        addTop(panel, txt("휴대폰이 보유한 사용 기록만 표시돼. 기록이 없는 날은 0으로 보일 수 있어.",
                11, false, MUTED), 12);
    }

    private void dayList(KakaoUsageReader.Report report) {
        title("일별 상세 기록", "DAILY BREAKDOWN", 24);
        LinearLayout card = card(Color.WHITE, 20, 17);
        addTop(content, card, 11);
        for (int i = report.days.length - 1; i >= 0; i--) {
            KakaoUsageReader.Day day = report.days[i];
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (i != 6) addTop(card, row, 12);
            else card.addView(row);

            TextView date = txt(i == 6 ? "오늘" : displayDate(day.start),
                    12, true, i == 6 ? ROSE : INK);
            row.addView(date, new LinearLayout.LayoutParams(0, -2, 1.0f));

            TextView count = txt(day.launches + "회 실행", 11, false, MUTED);
            count.setGravity(Gravity.END);
            row.addView(count, new LinearLayout.LayoutParams(0, -2, 1.0f));

            TextView time = txt(formatDuration(day.foregroundMs),
                    14, true, INK);
            time.setGravity(Gravity.END);
            row.addView(time, new LinearLayout.LayoutParams(0, -2, 1.25f));
            if (i > 0) {
                View separator = new View(this);
                separator.setBackgroundColor(Color.rgb(246, 237, 246));
                LinearLayout.LayoutParams separatorParams =
                        new LinearLayout.LayoutParams(-1, dp(1));
                separatorParams.topMargin = dp(12);
                card.addView(separator, separatorParams);
            }
        }
    }

    private void explanations(KakaoUsageReader.Report report) {
        title("측정 기준", "HOW IT WORKS", 25);
        LinearLayout note = card(FAINT, 19, 17);
        addTop(content, note, 10);
        note.addView(txt("사용 시간은 추정치야", 14, true, INK));
        addTop(note, txt("Android의 앱 화면 진입·이탈 및 화면 꺼짐 기록을 이용해 계산해. "
                        + "카카오톡 내부의 실제 대화 시간이나 메시지 읽음 상태를 의미하지 않아.",
                12, false, MUTED), 8);
        addTop(note, txt("앱 내부 화면을 옮길 때 실행 횟수가 중복될 수 있어 "
                        + "3초 이내 화면 전환을 하나로 묶었어. "
                        + "일부 휴대폰에서는 기록 보관 기간이 짧아 7일 전체가 나오지 않을 수 있어.",
                12, false, MUTED), 7);
        addTop(note, txt("이 데이터는 화면을 열 때 휴대전화 시스템에서 읽을 뿐, "
                        + "앱이 따로 저장하거나 공유하지 않아.",
                12, false, MUTED), 7);
        if (!report.hasEvents)
            addTop(note, txt("최근 카카오톡 화면 진입 기록이 확인되지 않았어. "
                            + "기기에 기록이 있는지 확인해 줘.",
                    12, true, ROSE), 10);
    }

    private void title(String title, String subtitle, int top) {
        LinearLayout label = new LinearLayout(this);
        label.setOrientation(LinearLayout.VERTICAL);
        addTop(content, label, top);
        label.addView(txt(subtitle, 10, true, ROSE));
        addTop(label, txt(title, 19, true, INK), 5);
    }

    private LinearLayout card(int color, int radiusDp, int paddingDp) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        block.setBackground(round(color, radiusDp));
        block.setElevation(dp(2));
        return block;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable style = new GradientDrawable();
        style.setColor(color);
        style.setCornerRadius(dp(radiusDp));
        return style;
    }

    private TextView txt(String text, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView action(String text, boolean primary, Runnable callback) {
        TextView button = txt(text, 14, true, primary ? Color.WHITE : INK);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(14), dp(12), dp(14));
        button.setBackground(round(primary ? ROSE : FAINT, 13));
        button.setOnClickListener(v -> callback.run());
        return button;
    }

    private void addTop(LinearLayout root, View element, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        root.addView(element, params);
    }

    private String weekday(long time) {
        return new SimpleDateFormat("E", Locale.KOREA).format(new Date(time));
    }

    private String displayDate(long time) {
        return new SimpleDateFormat("M/d E", Locale.KOREA).format(new Date(time));
    }

    private String formatDuration(long milliseconds) {
        long minutes = Math.max(0L, Math.round(milliseconds / 60000.0));
        if (minutes < 60) return minutes + "분";
        long hours = minutes / 60;
        long remain = minutes % 60;
        return remain == 0 ? hours + "시간" : hours + "시간 " + remain + "분";
    }

    private int dp(float length) {
        return Math.round(length * getResources().getDisplayMetrics().density);
    }
}
