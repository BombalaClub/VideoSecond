package cn.videosecond.gate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small, resolution-independent drawings for the family settings page. */
final class HomeIllustration extends View {
    enum Scene { MARK, NOTEBOOK, BOOK, CLOCK, HINT }

    private static final int INK = 0xff153f38;
    private static final int CREAM = 0xfffbf7ed;
    private static final int GOLD = 0xffe6a541;
    private static final int SALMON = 0xffce7151;
    private final Scene scene;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    HomeIllustration(Context context, Scene scene) {
        super(context);
        this.scene = scene;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float width = scene == Scene.NOTEBOOK ? 130f : 36f;
        float height = scene == Scene.NOTEBOOK ? 110f : 36f;
        float scale = Math.min(getWidth() / width, getHeight() / height);
        canvas.translate((getWidth() - width * scale) / 2, (getHeight() - height * scale) / 2);
        canvas.scale(scale, scale);
        switch (scene) {
            case NOTEBOOK: notebook(canvas); break;
            case MARK: mark(canvas); break;
            default: ruleIcon(canvas);
        }
        canvas.restore();
    }

    private void notebook(Canvas canvas) {
        canvas.save();
        canvas.rotate(-10, 61, 55);
        fill(0xff103831);
        canvas.drawRoundRect(27, 18, 99, 101, 4, 4, paint);
        fill(0xffcf8f36);
        canvas.drawRoundRect(23, 13, 95, 97, 4, 4, paint);
        fill(SALMON);
        polygon(canvas, 78, 89, 85, 89, 85, 103, 81.5f, 99.5f, 78, 103);
        fill(0xffddcfaf);
        canvas.drawRoundRect(21, 11, 93, 94, 4, 4, paint);
        fill(CREAM);
        canvas.drawRoundRect(19, 9, 92, 91, 4, 4, paint);
        stroke(0xffd7e2d2, .75f);
        for (int y = 26; y < 89; y += 8) canvas.drawLine(22, y, 89, y, paint);
        for (int x = 26; x < 91; x += 8) canvas.drawLine(x, 23, x, 89, paint);
        fill(0xffe1d5bd);
        for (int x = 30; x < 88; x += 13) canvas.drawCircle(x, 17, 2.8f, paint);
        stroke(0xff857b5b, 2.6f);
        for (int x = 30; x < 88; x += 13) {
            path.reset();
            path.moveTo(x, 16);
            path.cubicTo(x - 1.4f, 3, x - 6, 3, x - 6, 11);
            canvas.drawPath(path, paint);
        }
        stroke(INK, 2.7f);
        canvas.drawLine(35, 38, 44, 38, paint);
        canvas.drawLine(39.5f, 33.5f, 39.5f, 42.5f, paint);
        canvas.drawLine(49, 46, 57, 46, paint);
        fill(0xfff7d38b);
        canvas.drawCircle(69, 60, 9.2f, paint);
        stroke(SALMON, 2.2f);
        path.reset();
        path.moveTo(63, 61);
        path.quadTo(66, 62, 67, 65);
        path.quadTo(72, 59, 77, 56);
        canvas.drawPath(path, paint);
        stroke(0xff88a193, 1.6f);
        canvas.drawLine(34, 75, 48, 75, paint);
        canvas.drawLine(34, 79, 43, 79, paint);
        canvas.restore();

        canvas.save();
        canvas.rotate(34, 96, 49);
        fill(0xff0b322b);
        polygon(canvas, 91, 20, 100, 20, 100, 70, 95.5f, 83, 91, 70);
        fill(0xffed9776);
        canvas.drawRoundRect(88, 13, 98, 24, 2, 2, paint);
        fill(0xffb6c3ae);
        canvas.drawRect(88, 21, 98, 28, paint);
        fill(GOLD);
        canvas.drawRect(88, 27, 98, 69, paint);
        fill(0xffefc46c);
        canvas.drawRect(88, 27, 91.8f, 69, paint);
        fill(0xffb8782a);
        canvas.drawRect(96, 27, 98, 69, paint);
        fill(0xffeac399);
        polygon(canvas, 88, 69, 98, 69, 93, 81);
        fill(INK);
        polygon(canvas, 91, 76, 95, 76, 93, 81);
        canvas.restore();
    }

    private void mark(Canvas canvas) {
        fill(INK);
        canvas.drawRoundRect(0, 0, 36, 36, 9, 9, paint);
        canvas.save();
        canvas.rotate(-8, 18, 18);
        fill(CREAM);
        canvas.drawRoundRect(8, 8, 26, 28, 2, 2, paint);
        stroke(0xff9ba89b, 1.3f);
        canvas.drawLine(12, 13, 21, 13, paint);
        canvas.drawLine(12, 17, 18, 17, paint);
        stroke(0xffad701a, 2.7f);
        path.reset();
        path.moveTo(15, 21);
        path.lineTo(18, 24);
        path.lineTo(28, 14);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private void ruleIcon(Canvas canvas) {
        fill(scene == Scene.HINT ? 0xfff5e7cc : scene == Scene.CLOCK ? 0xffe4ece0 : 0xfff3e3d7);
        canvas.drawRoundRect(0, 0, 36, 36, 10, 10, paint);
        stroke(INK, 1.7f);
        switch (scene) {
            case BOOK:
                canvas.drawRoundRect(9, 8, 24, 26, 2, 2, paint);
                canvas.drawLine(12.8f, 8.5f, 12.8f, 25, paint);
                canvas.drawLine(16.5f, 13.7f, 21, 13.7f, paint);
                break;
            case CLOCK:
                canvas.drawCircle(18, 19.2f, 8.3f, paint);
                canvas.drawLine(18, 14.4f, 18, 19.2f, paint);
                canvas.drawLine(18, 19.2f, 21, 21.2f, paint);
                canvas.drawLine(15.8f, 7.8f, 20.2f, 7.8f, paint);
                break;
            case HINT:
                path.reset();
                path.moveTo(14, 23);
                path.cubicTo(14, 20.1f, 10.8f, 19.1f, 10.8f, 15);
                path.cubicTo(10.8f, 6.8f, 25.2f, 6.8f, 25.2f, 15);
                path.cubicTo(25.2f, 19.1f, 22, 20.1f, 22, 23);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(15.7f, 26.5f, 20.3f, 26.5f, paint);
                break;
            default:
                break;
        }
    }

    private void polygon(Canvas canvas, float... points) {
        path.reset();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void stroke(int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }
}
