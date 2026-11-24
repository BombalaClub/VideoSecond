package cn.videosecond.gate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Full-size question figure that can be magnified and panned inside the quiz window. */
final class QuizFigure extends View {
    private final Drawable drawing;
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;
    private float zoom = 1;
    private float fit;
    private float left;
    private float top;

    QuizFigure(Context context, int resource) {
        super(context);
        drawing = context.getDrawable(resource).mutate();
        drawing.setBounds(0, 0, drawing.getIntrinsicWidth(), drawing.getIntrinsicHeight());
        setBackgroundColor(0xffffffff);
        setClickable(true);
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomAt(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        pinch.setQuickScaleEnabled(false);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (!pinch.isInProgress()) {
                    left -= dx;
                    top -= dy;
                    constrain();
                    invalidate();
                }
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                zoomAt(zoom > 1.05f ? 1f / zoom : 2f, e.getX(), e.getY());
                return true;
            }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                return performClick();
            }
        });
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        fit = Math.min((float) w / drawing.getIntrinsicWidth(), (float) h / drawing.getIntrinsicHeight());
        zoom = 1;
        constrain();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(fit * zoom, fit * zoom);
        drawing.draw(canvas);
        canvas.restore();
    }

    void zoomBy(float factor) {
        zoomAt(factor, getWidth() / 2f, getHeight() / 2f);
    }

    private void zoomAt(float factor, float focusX, float focusY) {
        float next = Math.max(1, Math.min(6, zoom * factor));
        float ratio = next / zoom;
        left = focusX - (focusX - left) * ratio;
        top = focusY - (focusY - top) * ratio;
        zoom = next;
        constrain();
        invalidate();
    }

    private void constrain() {
        left = clamp(left, getWidth(), drawing.getIntrinsicWidth() * fit * zoom);
        top = clamp(top, getHeight(), drawing.getIntrinsicHeight() * fit * zoom);
    }

    private static float clamp(float offset, int viewport, float length) {
        if (length <= viewport) return (viewport - length) / 2;
        return Math.max(viewport - length, Math.min(0, offset));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        pinch.onTouchEvent(event);
        gestures.onTouchEvent(event);
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
