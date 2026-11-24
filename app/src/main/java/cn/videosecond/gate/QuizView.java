package cn.videosecond.gate;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Locale;

/** Child-facing quiz, shared by the protected-app overlay and the family's practice preview. */
public final class QuizView extends FrameLayout {
    private static final int PAPER = 0xfffbf7ed;
    private static final int INK = 0xff153f38;
    private static final int MUTED = 0xff52685e;
    private static final int LINE = 0xffd7d9c9;
    private static final int WHITE = 0xfffffdf7;
    private static final int MINT = 0xffe5eee3;
    private static final int SUN = 0xffe6a541;
    private static final int HINT = 0xffffecc8;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final boolean renewal;
    private final boolean preview;
    private final Runnable onCorrect;
    private final Runnable onExit;
    private final QuestionBank.Question question;
    private final int diagramId;
    private final String[] entries;
    private final Button[] fieldButtons;
    private final Button[] choiceButtons;
    private int currentField;
    private boolean replaceOnType;
    private int mistakes;
    private int hintLevel;
    private boolean wrong;
    private boolean finished;
    private boolean built;
    private boolean wide;

    private ScrollView scroll;
    private TextView answer;
    private TextView feedback;
    private Button hintButton;
    private Button submit;
    private LinearLayout diagramPage;

    public QuizView(Context context, boolean renewal, boolean preview, Runnable onCorrect, Runnable onExit) {
        super(context);
        this.renewal = renewal;
        this.preview = preview;
        this.onCorrect = onCorrect;
        this.onExit = onExit;
        question = QuestionBank.next(context, preview);
        entries = new String[question.fieldCount()];
        Arrays.fill(entries, "");
        fieldButtons = new Button[entries.length];
        choiceButtons = new Button[question.options.length];
        diagramId = question.diagram ? getResources().getIdentifier(
                String.format(Locale.ROOT, "ihc_q%03d", question.id), "drawable", context.getPackageName()) : 0;
        setBackgroundColor(PAPER);
        setClickable(true);
        setFocusableInTouchMode(true);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        boolean nextWide = width >= dp(550) && width > height * 1.12f;
        if (!built || wide != nextWide) {
            wide = nextWide;
            built = true;
            boolean restoreDiagram = diagramPage != null;
            build();
            if (restoreDiagram) openDiagram();
        }
        scroll.getLayoutParams().width = Math.min(width, dp(wide ? 960 : 536));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void build() {
        removeAllViews();
        diagramPage = null;
        scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER_HORIZONTAL));

        LinearLayout content = column();
        content.setPadding(dp(wide ? 28 : 20), dp(wide ? 12 : 20), dp(wide ? 28 : 20), dp(20));
        content.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(MATCH, WRAP));

        LinearLayout header = row();
        NotebookMark mark = new NotebookMark(getContext());
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = label(preview ? "答题预览" : renewal ? "30 分钟到了" : "打开小红书", 22, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, WRAP, 1);
        titleParams.leftMargin = dp(10);
        header.addView(title, titleParams);

        Button exit = button(preview ? "结束预览" : "回到桌面", 14, INK, PAPER, LINE);
        exit.setOnClickListener(v -> {
            if (!finished) onExit.run();
        });
        header.addView(exit, new LinearLayout.LayoutParams(WRAP, dp(48)));
        content.addView(header, full());
        TextView intro = label(preview ? "试做一道题，看看孩子会看到什么。" : renewal ? "休息一下，答对这道题就能继续刷。" : "先做一道题，答对就能去逛逛。", 15, MUTED, false);
        add(content, intro, 8);

        LinearLayout work = wide ? row() : column();
        work.setGravity(Gravity.TOP);
        add(content, work, wide ? 14 : 18);
        LinearLayout card = makeQuestionCard();
        LinearLayout keypad = makeKeypad();
        if (wide) {
            work.addView(card, new LinearLayout.LayoutParams(0, WRAP, 1.12f));
            LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(0, WRAP, 1);
            keyboardParams.leftMargin = dp(16);
            work.addView(keypad, keyboardParams);
        } else {
            work.addView(card, full());
            add(work, keypad, 10);
        }
        refresh();
    }

    private LinearLayout makeQuestionCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(12), dp(16), dp(16));
        card.setBackground(shape(WHITE, LINE, 20));
        LinearLayout tools = row();
        TextView topic = label("第 " + question.id + " 题 · " + question.topic, 13, INK, true);
        topic.setPadding(dp(10), dp(5), dp(10), dp(5));
        topic.setBackground(shape(MINT, 0, 8));
        LinearLayout topicArea = row();
        topicArea.addView(topic, new LinearLayout.LayoutParams(WRAP, WRAP));
        tools.addView(topicArea, new LinearLayout.LayoutParams(0, WRAP, 1));
        hintButton = button("看提示", 14, MUTED, WHITE, 0);
        hintButton.setOnClickListener(v -> {
            if (finished) return;
            hintLevel = Math.min(2, hintLevel + 1);
            refresh();
        });
        tools.addView(hintButton, new LinearLayout.LayoutParams(WRAP, dp(48)));
        card.addView(tools, full());

        TextView prompt = label(question.prompt, question.prompt.length() < 36 ? 22 : 19, INK, true);
        prompt.setLineSpacing(dp(3), 1f);
        add(card, prompt, 5);
        if (diagramId != 0) {
            ImageView figure = new ImageView(getContext());
            figure.setImageResource(diagramId);
            figure.setAdjustViewBounds(true);
            figure.setScaleType(ImageView.ScaleType.FIT_CENTER);
            figure.setMaxHeight(dp(wide ? 220 : 245));
            figure.setBackgroundColor(0xffffffff);
            figure.setContentDescription("查看第 " + question.id + " 题题图大图");
            figure.setFocusable(true);
            figure.setOnClickListener(view -> openDiagram());
            add(card, figure, 10);
            Button enlarge = button("放大题图", 14, MUTED, WHITE, LINE);
            enlarge.setMinHeight(dp(44));
            enlarge.setOnClickListener(view -> openDiagram());
            LinearLayout.LayoutParams enlargeParams = new LinearLayout.LayoutParams(WRAP, WRAP);
            enlargeParams.gravity = Gravity.END;
            card.addView(enlarge, enlargeParams);
        }

        feedback = label("", 14, INK, false);
        feedback.setPadding(dp(12), dp(9), dp(12), dp(9));
        feedback.setLineSpacing(dp(2), 1f);
        feedback.setBackground(shape(HINT, 0, 10));
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(card, feedback, 10);
        return card;
    }

    private LinearLayout makeKeypad() {
        LinearLayout pad = column();
        if (choiceButtons.length > 0) {
            for (int i = 0; i < choiceButtons.length; i++) {
                String option = question.options[i];
                Button choice = button(option, 18, INK, WHITE, LINE);
                choice.setMinHeight(dp(48));
                choice.setOnClickListener(v -> choose(option));
                choiceButtons[i] = choice;
                add(pad, choice, i == 0 ? 0 : 6);
            }
            submit = button("确认答案", 17, WHITE, INK, 0);
            submit.setMinHeight(dp(50));
            submit.setOnClickListener(v -> check());
            add(pad, submit, 8);
            return pad;
        }
        if (entries.length == 1) {
            LinearLayout field = row();
            field.setBackground(shape(WHITE, LINE, 10));
            field.setPadding(dp(14), dp(10), dp(14), dp(10));
            field.setMinimumHeight(dp(50));
            answer = label("", 21, MUTED, false);
            field.addView(answer, new LinearLayout.LayoutParams(0, WRAP, 1));
            if (!question.unit.isEmpty()) field.addView(label(question.unit, 16, MUTED, false));
            pad.addView(field, full());
        } else {
            makeFields(pad);
        }
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "删除", "0", "确认"};
        for (int r = 0; r < 4; r++) {
            LinearLayout line = row();
            for (int c = 0; c < 3; c++) {
                String key = keys[3 * r + c];
                boolean confirms = key.equals("确认");
                boolean deletes = key.equals("删除");
                Button cell = button(key, confirms || deletes ? 17 : 24, confirms ? WHITE : INK,
                        confirms ? INK : deletes ? MINT : WHITE, confirms || deletes ? 0 : LINE);
                if (confirms) {
                    submit = cell;
                    cell.setContentDescription("确认答案");
                    cell.setOnClickListener(v -> check());
                } else if (deletes) {
                    cell.setContentDescription("删除最后一位数字");
                    cell.setOnClickListener(v -> erase());
                } else {
                    cell.setOnClickListener(v -> digit(key));
                }
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, WRAP, 1);
                keyParams.leftMargin = c > 0 ? dp(7) : 0;
                cell.setMinHeight(dp(wide ? 48 : 50));
                line.addView(cell, keyParams);
            }
            add(pad, line, r == 0 ? 8 : 6);
        }
        return pad;
    }

    private void makeFields(LinearLayout pad) {
        // Two cells per row keep a five-position diagram usable on narrow phones.
        for (int i = 0; i < entries.length; i += 2) {
            LinearLayout line = row();
            for (int j = i; j < Math.min(i + 2, entries.length); j++) {
                final int fieldIndex = j;
                Button field = button("", 15, INK, WHITE, LINE);
                field.setMinHeight(dp(52));
                field.setOnClickListener(v -> selectField(fieldIndex));
                fieldButtons[j] = field;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1);
                if (j > i) params.leftMargin = dp(6);
                line.addView(field, params);
            }
            add(pad, line, i == 0 ? 0 : 6);
        }
    }

    private void selectField(int index) {
        currentField = index;
        replaceOnType = !entries[index].isEmpty();
        refresh();
    }

    private void choose(String option) {
        if (finished) return;
        entries[0] = option;
        wrong = false;
        refresh();
    }

    private void digit(String value) {
        if (finished) return;
        String old = entries[currentField];
        if (replaceOnType || "0".equals(old)) old = "";
        if (old.length() >= 12) return;
        entries[currentField] = old + value;
        replaceOnType = false;
        wrong = false;
        refresh();
    }

    private void erase() {
        String old = entries[currentField];
        if (finished || old.isEmpty()) return;
        entries[currentField] = old.substring(0, old.length() - 1);
        replaceOnType = false;
        wrong = false;
        refresh();
    }

    private int nextBlank() {
        for (int offset = 1; offset <= entries.length; offset++) {
            int i = (currentField + offset) % entries.length;
            if (entries[i].isEmpty()) return i;
        }
        return -1;
    }

    private void check() {
        if (finished || entries[currentField].isEmpty()) return;
        int blank = nextBlank();
        if (blank >= 0) {
            selectField(blank);
            return;
        }
        if (question.accepts(entries)) {
            finished = true;
            onCorrect.run();
            return;
        }
        wrong = true;
        mistakes++;
        hintLevel = Math.min(2, Math.max(mistakes, hintLevel));
        if (entries.length == 1) entries[0] = "";
        else replaceOnType = true;
        refresh();
        feedback.post(() -> {
            android.graphics.Rect bounds = new android.graphics.Rect();
            feedback.getDrawingRect(bounds);
            feedback.requestRectangleOnScreen(bounds, false);
        });
    }

    private void refresh() {
        if (choiceButtons.length > 0) {
            for (int i = 0; i < choiceButtons.length; i++) {
                boolean selected = question.options[i].equals(entries[0]);
                Button choice = choiceButtons[i];
                choice.setSelected(selected);
                choice.setText((selected ? "✓  " : "") + question.options[i]);
                choice.setBackground(ripple(selected ? MINT : WHITE, selected ? INK : LINE));
            }
        } else if (entries.length > 1) {
            for (int i = 0; i < entries.length; i++) {
                Button field = fieldButtons[i];
                boolean active = i == currentField;
                String title = question.labels.length == entries.length ? question.labels[i] : "第 " + (i + 1) + " 空";
                String value = entries[i].isEmpty() ? "待填写" : entries[i];
                field.setText(title + "  " + value);
                field.setContentDescription(title + "，" + value + (active ? "，正在填写" : "，点按修改"));
                field.setSelected(active);
                field.setBackground(ripple(active ? MINT : WHITE, active ? INK : LINE));
            }
        } else {
            boolean empty = entries[0].isEmpty();
            answer.setText(empty ? "输入答案" : entries[0]);
            answer.setTextColor(empty ? MUTED : INK);
            answer.setTypeface(Typeface.DEFAULT, empty ? Typeface.NORMAL : Typeface.BOLD);
            answer.setContentDescription(empty ? "尚未输入答案" : "已输入 " + entries[0] + question.unit);
        }
        boolean enabled = !entries[currentField].isEmpty();
        submit.setEnabled(enabled);
        submit.setAlpha(enabled ? 1f : .55f);
        String action = entries.length > 1 && nextBlank() >= 0 ? "下一空" : "确认";
        submit.setText(choiceButtons.length > 0 ? "确认答案" : action);
        submit.setContentDescription("确认".equals(action) ? "确认答案" : action);
        hintButton.setText(hintLevel == 0 ? "看提示" : "再想一步");
        hintButton.setVisibility(hintLevel < 2 ? VISIBLE : INVISIBLE);
        feedback.setVisibility(hintLevel == 0 ? GONE : VISIBLE);
        if (hintLevel > 0) {
            String text = (wrong ? "再试一次。 " : "") + (hintLevel == 1 ? question.firstHint : question.secondHint);
            if (!text.contentEquals(feedback.getText())) feedback.setText(text);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        char symbol = (char) event.getUnicodeChar();
        if (diagramPage != null || !built) return super.dispatchKeyEvent(event);
        boolean choice = choiceButtons.length > 0;
        boolean number = !choice && symbol >= '0' && symbol <= '9';
        boolean enter = key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean erase = !choice && key == KeyEvent.KEYCODE_DEL;
        String option = choice ? String.valueOf(Character.toUpperCase(symbol)) : "";
        boolean optionKey = choice && Arrays.asList(question.options).contains(option);
        if (number || erase || optionKey || (enter && !hasFocusedButton())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (number) digit(String.valueOf(symbol));
                else if (erase) erase();
                else if (optionKey) choose(option);
                else check();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean hasFocusedButton() {
        return findFocus() instanceof Button;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private Button button(String value, int sp, int textColor, int fill, int stroke) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setBackground(ripple(fill, stroke));
        return button;
    }

    private Drawable ripple(int fill, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x22808a75), shape(fill, stroke, 12), null);
    }

    private void openDiagram() {
        if (diagramPage != null || diagramId == 0) return;
        diagramPage = column();
        diagramPage.setBackgroundColor(PAPER);
        diagramPage.setClickable(true);
        diagramPage.setPadding(dp(12), dp(10), dp(12), dp(12));
        LinearLayout bar = row();
        bar.addView(label("第 " + question.id + " 题 · 题图", 18, INK, true),
                new LinearLayout.LayoutParams(0, WRAP, 1));
        Button close = button("返回题目", 16, INK, WHITE, LINE);
        close.setMinHeight(dp(48));
        close.setOnClickListener(v -> closeDiagram());
        bar.addView(close);
        diagramPage.addView(bar, full());
        QuizFigure figure = new QuizFigure(getContext(), diagramId);
        figure.setContentDescription("第 " + question.id + " 题题图，可放大后拖动查看");
        LinearLayout.LayoutParams area = new LinearLayout.LayoutParams(MATCH, 0, 1);
        area.topMargin = dp(8);
        diagramPage.addView(figure, area);
        LinearLayout scaleBar = row();
        scaleBar.setGravity(Gravity.CENTER);
        Button smaller = button("缩小", 16, INK, PAPER, LINE);
        smaller.setMinHeight(dp(48));
        smaller.setOnClickListener(v -> figure.zoomBy(1f / 1.65f));
        scaleBar.addView(smaller);
        Button larger = button("放大", 16, WHITE, INK, 0);
        larger.setMinHeight(dp(48));
        larger.setOnClickListener(v -> figure.zoomBy(1.65f));
        LinearLayout.LayoutParams largerParams = new LinearLayout.LayoutParams(WRAP, WRAP);
        largerParams.leftMargin = dp(12);
        scaleBar.addView(larger, largerParams);
        add(diagramPage, scaleBar, 8);
        scroll.setVisibility(INVISIBLE);
        addView(diagramPage, new FrameLayout.LayoutParams(MATCH, MATCH));
        close.requestFocus();
    }

    private void closeDiagram() {
        if (diagramPage == null) return;
        removeView(diagramPage);
        diagramPage = null;
        scroll.setVisibility(VISIBLE);
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radius));
        if (stroke != 0) background.setStroke(dp(1), stroke);
        return background;
    }

    private void add(LinearLayout group, View child, int top) {
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(top);
        group.addView(child, lp);
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(MATCH, WRAP);
    }

    private int dp(int units) {
        return Math.round(units * getResources().getDisplayMetrics().density);
    }

    private static final class NotebookMark extends View {
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);

        NotebookMark(Context context) {
            super(context);
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / 48f, getHeight() / 48f);
            pen.setColor(INK);
            canvas.drawRoundRect(0, 0, 48, 48, 14, 14, pen);
            canvas.rotate(-10, 24, 24);
            pen.setColor(WHITE);
            canvas.drawRoundRect(13, 10, 34, 37, 2.5f, 2.5f, pen);
            pen.setColor(SUN);
            canvas.drawRoundRect(13, 10, 18, 37, 2f, 2f, pen);
            pen.setColor(INK);
            pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeWidth(1.7f);
            canvas.drawLine(21, 19, 29, 19, pen);
            canvas.drawLine(21, 24, 28, 24, pen);
            canvas.restore();
        }
    }
}
