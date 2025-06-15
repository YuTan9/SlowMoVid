package edu.sjsu.android.videoplayer;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawingView extends View {

    public enum Tool { DRAW, ERASER }
    public enum Shape { FREE, LINE, CIRCLE }

    private Paint paint;
    private Tool currentTool = Tool.DRAW;
    private Shape currentShape = Shape.FREE;
    private Path currentPath;
    private float startX, startY;
    private int currentColor = Color.RED;
    private float strokeWidth = 5f;

    private boolean enabled = false;

    private class Stroke {
        Paint paint;
        Path path;
        Shape shape;
        float sx, sy, ex, ey;

        Stroke(Paint paint, Path path, Shape shape, float sx, float sy, float ex, float ey) {
            this.paint = new Paint(paint);
            this.path = new Path(path);
            this.shape = shape;
            this.sx = sx; this.sy = sy; this.ex = ex; this.ey = ey;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void initPaint() {
        paint = new Paint();
        paint.setColor(currentColor);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Stroke s : strokes) {
            if (s.shape == Shape.FREE) {
                canvas.drawPath(s.path, s.paint);
            } else if (s.shape == Shape.LINE) {
                canvas.drawLine(s.sx, s.sy, s.ex, s.ey, s.paint);
            } else if (s.shape == Shape.CIRCLE) {
                float radius = (float) Math.hypot(s.ex - s.sx, s.ey - s.sy);
                canvas.drawCircle(s.sx, s.sy, radius, s.paint);
            }
        }

        // Draw current shape in progress
        if (currentPath != null) {
            if (currentShape == Shape.FREE) {
                canvas.drawPath(currentPath, paint);
            } else if (currentShape == Shape.LINE) {
                canvas.drawLine(startX, startY, lastX, lastY, paint);
            } else if (currentShape == Shape.CIRCLE) {
                float r = (float) Math.hypot(lastX - startX, lastY - startY);
                canvas.drawCircle(startX, startY, r, paint);
            }
        }
    }

    float lastX, lastY;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!enabled) return false;

        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initPaint();
                if (currentTool == Tool.ERASER) {
                    eraseStrokeAt(x, y);
                    invalidate();
                    return true;
                }

                currentPath = new Path();
                currentPath.moveTo(x, y);
                startX = x;
                startY = y;
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (currentTool == Tool.DRAW && currentPath != null) {
                    if (currentShape == Shape.FREE) {
                        currentPath.lineTo(x, y);
                    }
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (currentTool == Tool.DRAW && currentPath != null) {
                    if (currentShape == Shape.FREE) {
                        currentPath.lineTo(x, y);
                        strokes.add(new Stroke(paint, currentPath, currentShape, 0, 0, 0, 0));
                    } else {
                        strokes.add(new Stroke(paint, currentPath, currentShape, startX, startY, x, y));
                    }
                    currentPath = null;
                    invalidate();
                }
                return true;
        }
        return false;
    }

    private void eraseStrokeAt(float x, float y) {
        for (int i = strokes.size() - 1; i >= 0; i--) {
            Stroke s = strokes.get(i);
            RectF bounds = new RectF();
            if (s.shape == Shape.FREE) {
                s.path.computeBounds(bounds, true);
            } else {
                bounds.set(Math.min(s.sx, s.ex), Math.min(s.sy, s.ey),
                        Math.max(s.sx, s.ex), Math.max(s.sy, s.ey));
            }
            bounds.inset(-30, -30); // fuzziness
            if (bounds.contains(x, y)) {
                strokes.remove(i);
                break;
            }
        }
    }

    public void setTool(Tool tool) {
        this.currentTool = tool;
    }

    public void setColor(int color) {
        this.currentColor = color;
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
    }

    public void setShape(Shape shape) {
        this.currentShape = shape;
    }

    public void clearAll() {
        strokes.clear();
        invalidate();
    }

    public void setDrawingEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
