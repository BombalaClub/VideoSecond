package cn.videosecond.gate;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Read-only review of locally scored questions. Only a successful latest result opens a problem. */
public final class QuestionHistoryActivity extends Activity {
    private static final int PAPER = 0xfffbf7ed, CARD = 0xfffffdf7, INK = 0xff153f38;
    private static final int MUTED = 0xff52655d, RULE = 0xffd9d7c7, MINT = 0xffe4eddf;
    private static final int OCHRE = 0xff81521d, SAND = 0xffffedcd;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private int openedId, listPosition, listOffset, detailOffset;
    private boolean showingFigure;
    private ListView list;
    private ScrollView detail;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            openedId = saved.getInt("question");
            showingFigure = saved.getBoolean("figure");
            listPosition = saved.getInt("listPosition");
            listOffset = saved.getInt("listOffset");
            detailOffset = saved.getInt("detailOffset");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        rememberPosition();
        if (openedId != 0) showQuestion(openedId, showingFigure);
        else showList();
    }

    private void rememberPosition() {
        if (list != null && list.getChildCount() > 0) {
            listPosition = list.getFirstVisiblePosition();
            listOffset = list.getChildAt(0).getTop() - list.getPaddingTop();
        }
        if (detail != null) detailOffset = detail.getScrollY();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        rememberPosition();
        out.putInt("question", openedId);
        out.putBoolean("figure", showingFigure);
        out.putInt("listPosition", listPosition);
        out.putInt("listOffset", listOffset);
        out.putInt("detailOffset", detailOffset);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (showingFigure) showQuestion(openedId, false);
        else if (openedId != 0) showList();
        else super.onBackPressed();
    }

    private LinearLayout page(String title, String backLabel) {
        FrameLayout canvas = new FrameLayout(this);
        canvas.setBackgroundColor(PAPER);
        LinearLayout body = column();
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(600));
        canvas.addView(body, new FrameLayout.LayoutParams(width, MATCH, Gravity.CENTER_HORIZONTAL));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(8), dp(16), dp(7));
        TextView heading = text(title, 22, INK, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        bar.addView(heading, new LinearLayout.LayoutParams(0, WRAP, 1));
        bar.addView(button(backLabel, v -> onBackPressed()));
        body.addView(bar);
        View divider = new View(this);
        divider.setBackgroundColor(RULE);
        body.addView(divider, MATCH, dp(1));
        setContentView(canvas);
        setTitle(title);
        return body;
    }

    private void showList() {
        openedId = 0;
        detailOffset = 0;
        showingFigure = false;
        detail = null;
        LinearLayout page = page("答题记录", "返回首页");
        int total = QuestionBank.size(this), right = 0, wrong = 0;
        QuestionProgress.Entry[] entries = new QuestionProgress.Entry[total];
        for (int i = 0; i < total; i++) {
            entries[i] = QuestionProgress.get(this, i + 1);
            if (entries[i].correct()) right++;
            else if (entries[i].attempted()) wrong++;
        }
        list = new ListView(this);
        list.setPadding(dp(16), 0, dp(16), dp(12));
        list.setDivider(new ColorDrawable(RULE));
        list.setDividerHeight(dp(1));
        list.setHeaderDividersEnabled(false);
        list.addHeaderView(summary(total, right, wrong), null, false);
        list.setAdapter(new RecordRows(entries));
        list.setOnItemClickListener((parent, view, position, itemId) -> {
            int id = position - list.getHeaderViewsCount() + 1;
            rememberPosition();
            showQuestion(id, false);
        });
        page.addView(list, MATCH, MATCH);
        restoreViewport(list, listPosition, listOffset);
    }

    private void restoreViewport(ListView view, int position, int offset) {
        view.setSelectionFromTop(position, offset);
        // Android snaps selection to an enabled row in keyboard mode. Scroll to the
        // original visual position even if that question, or the summary, is locked.
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                    int oldL, int oldT, int oldR, int oldB) {
                view.removeOnLayoutChangeListener(this);
                if (view.isInTouchMode() || view.getChildCount() == 0) return;
                int step = view.getHeight() / 2;
                if (step < 1) return;
                while (position < view.getFirstVisiblePosition() || position > view.getLastVisiblePosition()) {
                    int first = view.getFirstVisiblePosition();
                    int top = view.getChildAt(0).getTop();
                    int direction = position < first ? -1 : 1;
                    if (!view.canScrollList(direction)) break;
                    view.scrollListBy(direction * step);
                    if (view.getChildCount() == 0) return;
                    if (view.getFirstVisiblePosition() == first && view.getChildAt(0).getTop() == top) break;
                }
                View anchor = view.getChildAt(position - view.getFirstVisiblePosition());
                if (anchor != null) view.scrollListBy(anchor.getTop() - view.getPaddingTop() - offset);
            }
        });
    }

    private View summary(int total, int right, int wrong) {
        LinearLayout outer = column();
        outer.setPadding(0, dp(16), 0, dp(10));
        LinearLayout card = column();
        card.setBackground(shape(INK, 15));
        card.setPadding(dp(18), dp(14), dp(18), dp(15));
        card.addView(text("已答对 " + right + " / " + total + " 题", 21, CARD, true));
        TextView other = text("已答错 " + wrong + " 题　·　未作答 " + (total - right - wrong) + " 题",
                14, 0xffd7e5d6, false);
        card.addView(other, below(4));
        outer.addView(card);
        outer.addView(text("点开已答对的题目，可以查看原题。", 14, MUTED, false), below(12));
        return outer;
    }

    private final class RecordRows extends BaseAdapter {
        private final QuestionProgress.Entry[] entries;
        RecordRows(QuestionProgress.Entry[] entries) { this.entries = entries; }
        @Override public int getCount() { return entries.length; }
        @Override public QuestionProgress.Entry getItem(int i) { return entries[i]; }
        @Override public long getItemId(int i) { return i + 1L; }
        @Override public boolean hasStableIds() { return true; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return entries[i].correct(); }
        @Override public View getView(int i, View recycled, ViewGroup parent) {
            RecordRow row = recycled == null ? new RecordRow() : (RecordRow) recycled;
            row.bind(i + 1, entries[i]);
            return row;
        }
    }

    private final class RecordRow extends LinearLayout {
        final TextView number, time, status, arrow;
        RecordRow() {
            super(QuestionHistoryActivity.this);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(62));
            setPadding(dp(9), dp(9), dp(6), dp(9));
            LinearLayout left = column();
            number = text("", 17, INK, true);
            time = text("", 13, MUTED, false);
            left.addView(number);
            left.addView(time);
            addView(left, new LinearLayout.LayoutParams(0, WRAP, 1));
            status = text("", 13, MUTED, true);
            status.setPadding(dp(9), dp(3), dp(9), dp(3));
            addView(status);
            arrow = text("›", 22, INK, false);
            arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            addView(arrow, dp(22), WRAP);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            for (View child : new View[]{left, status, arrow}) {
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        void bind(int id, QuestionProgress.Entry entry) {
            String result = entry.correct() ? "已答对" : entry.attempted() ? "已答错" : "未作答";
            String title = "第 " + id + " 题";
            number.setText(title);
            status.setText(result);
            status.setTextColor(entry.correct() ? INK : entry.attempted() ? OCHRE : MUTED);
            status.setBackground(shape(entry.correct() ? MINT : entry.attempted() ? SAND : 0xffeeeee5, 7));
            arrow.setVisibility(entry.correct() ? VISIBLE : INVISIBLE);
            String elapsed = entry.correct() ? "用时 " + duration(entry.lastCorrectMillis) : "";
            time.setText(elapsed);
            time.setVisibility(entry.correct() ? VISIBLE : GONE);
            setContentDescription(title + "，" + result + (entry.correct() ? "，" + elapsed + "，查看原题" : ""));
        }
    }

    private void showQuestion(int id, boolean expand) {
        QuestionBank.Question question = QuestionBank.forReview(this, id);
        if (question == null) {
            showList();
            return;
        }
        openedId = id;
        list = null;
        int resource = question.diagram ? getResources().getIdentifier(
                String.format(Locale.ROOT, "ihc_q%03d", id), "drawable", getPackageName()) : 0;
        showingFigure = expand && resource != 0;
        if (showingFigure) {
            showFigure(id, resource);
            return;
        }

        LinearLayout body = page("第 " + id + " 题", "返回列表");
        detail = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(20), dp(16), dp(20));
        detail.addView(content);
        body.addView(detail, MATCH, MATCH);
        String elapsed = duration(QuestionProgress.get(this, id).lastCorrectMillis);
        content.addView(text("已答对　·　用时 " + elapsed, 15, INK, true));
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(20));
        GradientDrawable cardShape = shape(CARD, 14);
        cardShape.setStroke(dp(1), RULE);
        card.setBackground(cardShape);
        content.addView(card, below(12));
        card.addView(text(question.topic, 14, MUTED, true));
        TextView prompt = text(question.prompt, 20, INK, true);
        prompt.setLineSpacing(dp(4), 1f);
        card.addView(prompt, below(10));
        if (resource != 0) {
            ImageView image = new ImageView(this);
            image.setImageResource(resource);
            image.setBackgroundColor(0xffffffff);
            image.setAdjustViewBounds(true);
            image.setMaxHeight(dp(290));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("放大第 " + id + " 题题图");
            View.OnClickListener open = v -> {
                rememberPosition();
                showQuestion(id, true);
            };
            image.setOnClickListener(open);
            card.addView(image, below(14));
            card.addView(button("放大题图", open), below(6));
        }
        if (question.options.length > 0) {
            card.addView(text("选项　" + TextUtils.join("　", question.options), 16, MUTED, false), below(14));
        } else if (question.labels.length > 0) {
            card.addView(text("填空　" + TextUtils.join("、", question.labels), 16, MUTED, false), below(14));
        } else if (!question.unit.isEmpty()) {
            card.addView(text("答案单位　" + question.unit, 15, MUTED, false), below(14));
        }
        final ScrollView current = detail;
        if (detailOffset > 0) current.post(() -> current.scrollTo(0, detailOffset));
    }

    private void showFigure(int id, int resource) {
        detail = null;
        LinearLayout body = page("第 " + id + " 题 · 题图", "返回题目");
        QuizFigure figure = new QuizFigure(this, resource);
        figure.setContentDescription("第 " + id + " 题题图，可缩放或拖动");
        body.addView(figure, new LinearLayout.LayoutParams(MATCH, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(5), 0, dp(6));
        controls.addView(button("缩小", v -> figure.zoomBy(1f / 1.65f)));
        controls.addView(button("放大", v -> figure.zoomBy(1.65f)));
        body.addView(controls);
    }

    private static String duration(long millis) {
        if (millis < 1000) return "不到 1 秒";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + " 秒";
        long rest = seconds % 60;
        return (seconds / 60) + " 分" + (rest == 0 ? "" : " " + rest + " 秒");
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private Button button(String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(INK);
        button.setTextSize(15);
        button.setMinHeight(dp(48));
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22153f38), shape(PAPER, 9), null));
        button.setOnClickListener(action);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        return result;
    }

    private LinearLayout.LayoutParams below(int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH, WRAP);
        params.topMargin = dp(gap);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
