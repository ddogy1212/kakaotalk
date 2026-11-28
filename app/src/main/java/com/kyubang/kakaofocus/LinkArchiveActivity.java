package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Private offline, searchable link collection with manual and share imports. */
public final class LinkArchiveActivity extends Activity {
    private static final int INK = Color.rgb(39, 37, 57);
    private static final int MUTED = Color.rgb(121, 117, 140);
    private static final int PINK = Color.rgb(198, 76, 138);
    private static final int BG = Color.rgb(248, 247, 252);
    private static final int WHITE = Color.WHITE;
    private static final int LIGHT = Color.rgb(251, 231, 242);

    private LinkArchiveStore store;
    private LinearLayout page, list, categoryBar;
    private TextView countLabel, favoriteBadge, toggleFavorite;
    private EditText search;
    private String category = "전체";
    private boolean favoritesOnly = false;
    private int displayed = 0;
    private static final int PAGE_SIZE = 40;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        store = new LinkArchiveStore(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(17), dp(18), dp(37));
        scroll.addView(page);
        setContentView(scroll);
        makeLayout();
        if (savedInstanceState == null) takeSharedIntent(getIntent());
        refresh(true);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        takeSharedIntent(intent);
        refresh(true);
    }

    @Override protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }

    private void takeSharedIntent(Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_SEND.equals(intent.getAction())
                || !"text/plain".equals(intent.getType())) return;
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (shared == null || shared.length() == 0) return;
        try {
            int added = store.saveText(shared.toString(), "카톡·다른 앱에서 공유");
            Toast.makeText(this, added > 0
                    ? added + "개 새 링크를 박물관에 저장했어!"
                    : (LinkArchiveStore.findLinks(shared.toString()).isEmpty()
                        ? "공유된 글에서 웹 링크를 찾지 못했어."
                        : "이미 저장된 링크들이야."),
                    Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "링크 저장 중 문제가 발생했어.", Toast.LENGTH_LONG).show();
        }
        // Never accidentally re-import the same share after activity recreation.
        setIntent(new Intent(this, LinkArchiveActivity.class));
    }

    private void makeLayout() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header);
        TextView back = label("‹", 31, false, INK);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(WHITE, 13));
        header.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        back.setOnClickListener(v -> finish());

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlePlacement = new LinearLayout.LayoutParams(0, -2, 1f);
        titlePlacement.leftMargin = dp(12);
        header.addView(titleStack, titlePlacement);
        titleStack.addView(label("LINK MUSEUM", 10, true, PINK));
        addTop(titleStack, label("카톡 링크 박물관", 23, true, INK), 3);

        TextView info = label("ⓘ", 26, true, PINK);
        info.setGravity(Gravity.CENTER);
        info.setBackground(round(LIGHT, 13));
        header.addView(info, new LinearLayout.LayoutParams(dp(43), dp(43)));
        info.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("링크 박물관은 이렇게 작동해")
                .setMessage("① 알림 접근 권한이 켜져 있다면 앞으로 도착하는 "
                        + "카카오톡 알림에 포함된 웹 링크를 자동으로 저장해.\n\n"
                        + "② 알림에 표시되지 않은 예전 대화나 숨겨진 링크는 가져올 수 없어. "
                        + "카톡의 '공유하기' → '카톡 한눈에 정식'을 선택하거나 "
                        + "직접 붙여넣으면 저장할 수 있어.\n\n"
                        + "③ 자동 라벨은 주소의 도메인·확장자로 판별하는 "
                        + "오프라인 규칙이야. 링크 내용을 AI로 읽는 건 아니야.\n\n"
                        + "④ 링크와 이름·즐겨찾기·메모는 휴대폰 내부에 계속 저장돼. "
                        + "중복 URL은 하나만 보관해. 삭제하면 되돌릴 수 없어.")
                .setPositiveButton("확인", null).show());

        GradientDrawable bannerBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(38, 34, 58), Color.rgb(91, 53, 103)});
        bannerBg.setCornerRadius(dp(23));
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(19), dp(19), dp(19), dp(19));
        banner.setBackground(bannerBg);
        addTop(page, banner, 20);
        banner.addView(label("YOUR SAVED CORNER OF THE INTERNET", 10,
                true, Color.rgb(249, 189, 222)));
        LinearLayout figures = new LinearLayout(this);
        figures.setGravity(Gravity.CENTER_VERTICAL);
        addTop(banner, figures, 13);

        LinearLayout first = new LinearLayout(this);
        first.setOrientation(LinearLayout.VERTICAL);
        figures.addView(first, new LinearLayout.LayoutParams(0, -2, 1f));
        countLabel = label("0", 36, true, WHITE);
        first.addView(countLabel);
        addTop(first, label("보관한 링크", 11, false, Color.rgb(239, 215, 235)), 4);

        LinearLayout second = new LinearLayout(this);
        second.setOrientation(LinearLayout.VERTICAL);
        figures.addView(second, new LinearLayout.LayoutParams(0, -2, 1f));
        favoriteBadge = label("0", 36, true, WHITE);
        second.addView(favoriteBadge);
        addTop(second, label("즐겨찾기", 11, false, Color.rgb(239, 215, 235)), 4);
        addTop(banner, label("모든 기록은 이 휴대폰에만 · API / 로그인 필요 없음",
                11, false, Color.rgb(233, 205, 223)), 16);

        LinearLayout actions = new LinearLayout(this);
        addTop(page, actions, 17);
        TextView paste = action("＋ 링크 추가", true, () -> showAddDialog(""));
        actions.addView(paste, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView clipboard = action("붙여넣기", false, this::pasteClipboard);
        LinearLayout.LayoutParams clipP = new LinearLayout.LayoutParams(0, dp(48), 1f);
        clipP.leftMargin = dp(9);
        actions.addView(clipboard, clipP);

        TextView searchTitle = label("찾고 싶은 링크", 16, true, INK);
        addTop(page, searchTitle, 23);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("제목, 주소, 분류, 저장 출처, 메모 검색");
        search.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(10));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setBackground(round(WHITE, 14));
        addTop(page, search, 9);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh(true);
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER_VERTICAL);
        addTop(page, options, 17);
        TextView filterLabel = label("카테고리", 15, true, INK);
        options.addView(filterLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        toggleFavorite = label("☆ 즐겨찾기만", 12, true, MUTED);
        toggleFavorite.setGravity(Gravity.CENTER);
        toggleFavorite.setPadding(dp(11), dp(9), dp(11), dp(9));
        options.addView(toggleFavorite);
        toggleFavorite.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            refresh(true);
        });

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        addTop(page, horizontal, 10);
        categoryBar = new LinearLayout(this);
        categoryBar.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(categoryBar);
        buildFilters();

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        addTop(page, heading, 19);
        TextView headingTitle = label("저장한 링크", 16, true, INK);
        heading.addView(headingTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView newest = label("최신순 · 즐겨찾기 우선", 11, false, MUTED);
        heading.addView(newest);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        addTop(page, list, 8);
    }

    private void buildFilters() {
        categoryBar.removeAllViews();
        String[] categories = new String[LinkArchiveStore.CATEGORIES.length + 1];
        categories[0] = "전체";
        System.arraycopy(LinkArchiveStore.CATEGORIES, 0, categories, 1,
                LinkArchiveStore.CATEGORIES.length);
        for (String candidate : categories) {
            boolean selected = candidate.equals(category);
            TextView chip = label(candidate, 12, selected, selected ? WHITE : INK);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13), dp(10), dp(13), dp(10));
            chip.setBackground(round(selected ? PINK : WHITE, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(39));
            params.rightMargin = dp(7);
            categoryBar.addView(chip, params);
            chip.setOnClickListener(v -> {
                category = candidate;
                buildFilters();
                refresh(true);
            });
        }
    }

    private void refresh(boolean resetPage) {
        if (store == null || list == null) return;
        if (resetPage) displayed = PAGE_SIZE;
        countLabel.setText(String.valueOf(store.total()));
        favoriteBadge.setText(String.valueOf(store.favorites()));
        toggleFavorite.setText(favoritesOnly ? "★ 즐겨찾기만" : "☆ 즐겨찾기만");
        toggleFavorite.setTextColor(favoritesOnly ? PINK : MUTED);
        toggleFavorite.setBackground(round(favoritesOnly ? LIGHT : WHITE, 12));

        List<LinkArchiveStore.Entry> rows = store.search(
                search.getText().toString(), category, favoritesOnly, displayed + 1, 0);
        boolean more = rows.size() > displayed;
        list.removeAllViews();
        if (rows.isEmpty()) {
            LinearLayout blank = new LinearLayout(this);
            blank.setOrientation(LinearLayout.VERTICAL);
            blank.setGravity(Gravity.CENTER);
            blank.setPadding(dp(18), dp(31), dp(18), dp(31));
            blank.setBackground(round(WHITE, 20));
            addTop(list, blank, 5);
            TextView icon = label("🔖", 31, false, PINK);
            icon.setGravity(Gravity.CENTER);
            blank.addView(icon);
            TextView title = label(store.total() == 0 ? "첫 링크를 저장해 봐!" : "검색 결과가 없어",
                    17, true, INK);
            title.setGravity(Gravity.CENTER);
            addTop(blank, title, 13);
            TextView desc = label(store.total() == 0
                    ? "카톡 알림에서 링크가 감지되거나, 링크를 직접 공유하면 여기 모여."
                    : "검색어나 카테고리를 변경해 봐.",
                    12, false, MUTED);
            desc.setGravity(Gravity.CENTER);
            addTop(blank, desc, 9);
            return;
        }
        for (int i = 0; i < Math.min(displayed, rows.size()); i++)
            addEntry(rows.get(i));
        if (more) {
            TextView moreButton = action("더 보기 (+40)", false, () -> {
                displayed += PAGE_SIZE;
                refresh(false);
            });
            addTop(list, moreButton, 13);
        }
    }

    private void addEntry(LinkArchiveStore.Entry e) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(15), dp(14), dp(14));
        card.setBackground(round(WHITE, 17));
        card.setElevation(dp(1));
        addTop(list, card, 10);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(heading);
        String emblem = emblem(e.category);
        TextView icon = label(emblem, 24, false, PINK);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(LIGHT, 13));
        heading.addView(icon, new LinearLayout.LayoutParams(dp(45), dp(45)));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(0, -2, 1f);
        mainParams.leftMargin = dp(11);
        heading.addView(main, mainParams);
        TextView title = label(e.title, 15, true, INK);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        main.addView(title);
        addTop(main, label(e.category + "  ·  " + time(e.added),
                11, false, MUTED), 6);

        TextView favorite = label(e.favorite ? "★" : "☆", 27, true, e.favorite ? PINK : MUTED);
        favorite.setGravity(Gravity.CENTER);
        heading.addView(favorite, new LinearLayout.LayoutParams(dp(42), dp(44)));
        favorite.setOnClickListener(v -> {
            store.favorite(e.id, !e.favorite);
            refresh(true);
        });

        TextView url = label(e.url, 11, false, Color.rgb(106, 99, 151));
        url.setMaxLines(2);
        url.setEllipsize(TextUtils.TruncateAt.END);
        addTop(card, url, 11);
        if (!e.note.trim().isEmpty()) {
            TextView note = label("메모 · " + e.note, 12, false, INK);
            note.setMaxLines(3);
            note.setEllipsize(TextUtils.TruncateAt.END);
            addTop(card, note, 8);
        }
        addTop(card, label(e.source, 10, false, MUTED), 9);

        LinearLayout bar = new LinearLayout(this);
        addTop(card, bar, 13);
        TextView open = action("열기 ↗", true, () -> openUrl(e.url));
        bar.addView(open, new LinearLayout.LayoutParams(0, dp(43), 1f));
        TextView edit = action("✎ 이름·분류 수정", false, () -> showEditDialog(e));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(43), 1.3f);
        editParams.leftMargin = dp(7);
        bar.addView(edit, editParams);
        TextView remove = action("삭제", false, () -> confirmDelete(e));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(43), .65f);
        removeParams.leftMargin = dp(7);
        bar.addView(remove, removeParams);
    }

    private void showAddDialog(String initial) {
        EditText input = new EditText(this);
        input.setTextSize(14);
        input.setTextColor(INK);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setHint("https://... 여러 개 넣어도 자동으로 분리해!");
        input.setText(initial);
        LinearLayout pad = new LinearLayout(this);
        pad.setPadding(dp(18), dp(3), dp(18), 0);
        pad.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("링크 추가")
                .setMessage("웹 주소나 카톡에서 복사한 메시지를 붙여넣어 줘.")
                .setView(pad)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (d, which) -> {
                    int added = store.saveText(input.getText().toString(), "직접 추가");
                    Toast.makeText(this, added > 0
                            ? added + "개 링크 저장 완료!"
                            : "새 링크가 없어. 이미 저장됐는지 확인해 줘.",
                            Toast.LENGTH_SHORT).show();
                    refresh(true);
                }).show();
    }

    private void pasteClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "클립보드에 복사한 내용이 없어.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence value = clip.getItemAt(0).coerceToText(this);
        showAddDialog(value == null ? "" : value.toString());
    }

    private void showEditDialog(LinkArchiveStore.Entry e) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), 0, dp(18), 0);
        layout.addView(label("이름", 12, true, INK));
        EditText title = new EditText(this);
        title.setText(e.title);
        title.setTextSize(15);
        title.setSingleLine(true);
        addTop(layout, title, 3);
        addTop(layout, label("자동 라벨 · 직접 변경 가능", 12, true, INK), 12);
        Spinner categoryPicker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                LinkArchiveStore.CATEGORIES);
        categoryPicker.setAdapter(adapter);
        for (int i = 0; i < LinkArchiveStore.CATEGORIES.length; i++) {
            if (LinkArchiveStore.CATEGORIES[i].equals(e.category)) {
                categoryPicker.setSelection(i);
                break;
            }
        }
        addTop(layout, categoryPicker, 4);
        addTop(layout, label("나만 보는 메모 (검색 가능)", 12, true, INK), 13);
        EditText note = new EditText(this);
        note.setText(e.note);
        note.setTextSize(13);
        note.setMinLines(2);
        note.setHint("예: 수학 수행 자료, 다음 주까지 읽기");
        addTop(layout, note, 4);

        new AlertDialog.Builder(this)
                .setTitle("링크 수정")
                .setView(layout)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) name = LinkArchiveStore.suggestTitle(e.url);
                    store.update(e.id, name,
                            categoryPicker.getSelectedItem().toString(),
                            note.getText().toString());
                    refresh(true);
                }).show();
    }

    private void confirmDelete(LinkArchiveStore.Entry e) {
        new AlertDialog.Builder(this)
                .setTitle("이 링크를 삭제할까?")
                .setMessage(e.title + "\n\n삭제한 링크는 복구할 수 없어.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, which) -> {
                    store.delete(e.id);
                    refresh(true);
                }).show();
    }

    private void openUrl(String address) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address))
                    .addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (RuntimeException e) {
            Toast.makeText(this, "이 링크를 열 앱이 없어.", Toast.LENGTH_LONG).show();
        }
    }

    private static String emblem(String category) {
        switch (category) {
            case "동영상": return "▶";
            case "학습·문서": return "📚";
            case "기사·뉴스": return "📰";
            case "쇼핑": return "🛍";
            case "SNS·커뮤니티": return "💬";
            case "지도·장소": return "📍";
            case "개발·기술": return "⌘";
            default: return "🔗";
        }
    }

    private String time(long stamp) {
        return new SimpleDateFormat("M월 d일", Locale.KOREA).format(new Date(stamp));
    }

    private TextView action(String title, boolean primary, Runnable event) {
        TextView button = label(title, 12, true, primary ? WHITE : INK);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(primary ? PINK : LIGHT, 12));
        button.setPadding(dp(8), dp(9), dp(8), dp(9));
        button.setOnClickListener(v -> event.run());
        return button;
    }

    private TextView label(String title, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void addTop(LinearLayout group, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        group.addView(child, lp);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
