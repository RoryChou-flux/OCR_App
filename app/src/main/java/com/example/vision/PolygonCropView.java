package com.example.vision;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class PolygonCropView extends View {
    private final Paint mPointPaint;
    private final Paint mLinePaint;
    private final Paint mFillPaint;
    private final Paint mTextPaint;  // 新增：用于绘制文字
    private final Path mPath;
    private final List<PointF> mPoints;
    private static final float POINT_RADIUS = 30f;
    private static final float TOUCH_RADIUS = 50f;
    private int selectedPointIndex = -1;
    private Bitmap mBitmap;
    private final Matrix mMatrix = new Matrix();
    private final float[] mMatrixValues = new float[9];

    // 修改点的顺序为：左上、左下、右下、右上
    private static final String[] POINT_HINTS = {"左上", "左下", "右下", "右上"};

    public PolygonCropView(Context context) {
        this(context, null);
    }

    public PolygonCropView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mPoints = new ArrayList<>();
        mPath = new Path();

        // 设置点的绘制样式
        mPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPointPaint.setColor(Color.WHITE);
        mPointPaint.setStyle(Paint.Style.FILL);
        mPointPaint.setShadowLayer(5.0f, 0.0f, 0.0f, Color.BLACK);

        // 设置线的绘制样式
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setColor(Color.WHITE);
        mLinePaint.setStrokeWidth(3f);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setShadowLayer(5.0f, 0.0f, 0.0f, Color.BLACK);

        // 设置填充样式
        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setColor(Color.parseColor("#3F51B5"));
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setAlpha(100);

        // 设置文字样式
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(40f);
        mTextPaint.setShadowLayer(5.0f, 0.0f, 0.0f, Color.BLACK);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setBitmap(@Nullable Bitmap bitmap) {
        mBitmap = bitmap;
        if (bitmap != null) {
            setupMatrix();
        }
        invalidate();
    }

    private void setupMatrix() {
        if (mBitmap == null || getWidth() == 0 || getHeight() == 0) return;

        mMatrix.reset();

        float scale;
        float dx = 0, dy = 0;

        // 计算缩放比例和偏移量，以适应视图大小
        if (mBitmap.getWidth() * getHeight() > getWidth() * mBitmap.getHeight()) {
            scale = (float) getWidth() / mBitmap.getWidth();
            dy = (getHeight() - mBitmap.getHeight() * scale) * 0.5f;
        } else {
            scale = (float) getHeight() / mBitmap.getHeight();
            dx = (getWidth() - mBitmap.getWidth() * scale) * 0.5f;
        }

        mMatrix.setScale(scale, scale);
        mMatrix.postTranslate(dx, dy);
        mMatrix.getValues(mMatrixValues);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupMatrix();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (mBitmap != null) {
            canvas.drawBitmap(mBitmap, mMatrix, null);
        }

        // 绘制多边形填充
        if (mPoints.size() > 2) {
            mPath.reset();
            mPath.moveTo(mPoints.get(0).x, mPoints.get(0).y);
            for (int i = 1; i < mPoints.size(); i++) {
                mPath.lineTo(mPoints.get(i).x, mPoints.get(i).y);
            }
            mPath.close();
            canvas.drawPath(mPath, mFillPaint);
        }

        // 绘制连接线
        if (mPoints.size() > 1) {
            mPath.reset();
            mPath.moveTo(mPoints.get(0).x, mPoints.get(0).y);
            for (int i = 1; i < mPoints.size(); i++) {
                mPath.lineTo(mPoints.get(i).x, mPoints.get(i).y);
            }
            if (mPoints.size() > 2) {
                mPath.close();
            }
            canvas.drawPath(mPath, mLinePaint);
        }

        // 绘制点和提示文字
        for (int i = 0; i < mPoints.size(); i++) {
            PointF point = mPoints.get(i);
            canvas.drawCircle(point.x, point.y, POINT_RADIUS, mPointPaint);
            // 在点的上方绘制提示文字
            canvas.drawText(POINT_HINTS[i], point.x, point.y - POINT_RADIUS - 10, mTextPaint);
        }

        // 如果还没有选完所有点，显示下一个点的提示
        if (mPoints.size() < 4) {
            String hint = "请选择" + POINT_HINTS[mPoints.size()];
            canvas.drawText(hint, getWidth() / 2f, 100, mTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                selectedPointIndex = findNearestPoint(x, y);
                if (selectedPointIndex == -1 && mPoints.size() < 4) {
                    mPoints.add(new PointF(x, y));
                    selectedPointIndex = mPoints.size() - 1;
                    invalidate();
                    notifyPointsChanged();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (selectedPointIndex != -1) {
                    mPoints.get(selectedPointIndex).set(x, y);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                selectedPointIndex = -1;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    public interface OnPointsChangeListener {
        void onPointsChanged(int pointCount);
    }

    private OnPointsChangeListener pointsChangeListener;

    public void setOnPointsChangeListener(@Nullable OnPointsChangeListener listener) {
        this.pointsChangeListener = listener;
    }

    private void notifyPointsChanged() {
        if (pointsChangeListener != null) {
            pointsChangeListener.onPointsChanged(mPoints.size());
        }
    }

    private int findNearestPoint(float x, float y) {
        for (int i = 0; i < mPoints.size(); i++) {
            final PointF point = mPoints.get(i);
            final float dx = x - point.x;
            final float dy = y - point.y;
            if (Math.sqrt(dx * dx + dy * dy) < TOUCH_RADIUS) {
                return i;
            }
        }
        return -1;
    }

    public void reset() {
        mPoints.clear();
        invalidate();
        notifyPointsChanged();
    }

    public boolean isValidShape() {
        if (mPoints.size() != 4 || mBitmap == null) {
            return false;
        }

        for (PointF point : mPoints) {
            if (point == null ||
                    point.x < 0 || point.x > getWidth() ||
                    point.y < 0 || point.y > getHeight()) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public PointF[] getScaledPoints() {
        if (!isValidShape()) {
            return new PointF[0];
        }

        PointF[] scaledPoints = new PointF[4];
        final float scaleX = mBitmap.getWidth() / (mMatrixValues[Matrix.MSCALE_X] * mBitmap.getWidth());
        final float scaleY = mBitmap.getHeight() / (mMatrixValues[Matrix.MSCALE_Y] * mBitmap.getHeight());

        for (int i = 0; i < 4; i++) {
            PointF point = mPoints.get(i);
            float x = (point.x - mMatrixValues[Matrix.MTRANS_X]) * scaleX;
            float y = (point.y - mMatrixValues[Matrix.MTRANS_Y]) * scaleY;
            scaledPoints[i] = new PointF(x, y);
        }

        return scaledPoints;
    }
}
