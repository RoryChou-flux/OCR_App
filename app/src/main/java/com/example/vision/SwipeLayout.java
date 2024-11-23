package com.example.vision;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

public class SwipeLayout extends FrameLayout {
    private ViewDragHelper dragHelper;
    private View contentView;
    private View deleteView;
    private float dragOffset;
    private boolean isOpen = false;
    private OnSwipeListener swipeListener;

    public interface OnSwipeListener {
        void onSwipeStateChanged(boolean isOpen);
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        this.swipeListener = listener;
    }

    public SwipeLayout(@NonNull Context context) {
        this(context, null);
    }

    public SwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dragHelper = ViewDragHelper.create(this, new ViewDragHelper.Callback() {
            @Override
            public boolean tryCaptureView(@NonNull View child, int pointerId) {
                return child == contentView;
            }

            @Override
            public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
                final int leftBound = -deleteView.getWidth();
                final int rightBound = 0;
                return Math.min(Math.max(left, leftBound), rightBound);
            }

            @Override
            public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
                dragOffset = (float) Math.abs(left) / deleteView.getWidth();
                requestLayout();
                deleteView.setAlpha(dragOffset);
            }

            @Override
            public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
                int finalLeft = xvel < 0 || (xvel == 0 && dragOffset > 0.5f) ?
                        -deleteView.getWidth() : 0;

                if (dragHelper.settleCapturedViewAt(finalLeft, releasedChild.getTop())) {
                    ViewCompat.postInvalidateOnAnimation(SwipeLayout.this);
                }

                boolean willBeOpen = finalLeft != 0;
                if (isOpen != willBeOpen) {
                    isOpen = willBeOpen;
                    if (swipeListener != null) {
                        swipeListener.onSwipeStateChanged(isOpen);
                    }
                }
            }

            @Override
            public int getViewHorizontalDragRange(@NonNull View child) {
                return deleteView.getWidth();
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() != 2) {
            throw new IllegalStateException("SwipeLayout must have exactly 2 children");
        }
        contentView = getChildAt(0);
        deleteView = getChildAt(1);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (contentView == null || deleteView == null) return;

        int contentLeft = contentView.getLeft();
        contentView.layout(contentLeft, 0,
                contentLeft + contentView.getMeasuredWidth(),
                contentView.getMeasuredHeight());

        deleteView.layout(right - deleteView.getMeasuredWidth(), 0,
                right, deleteView.getMeasuredHeight());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return dragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        dragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    public void computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public void close() {
        if (dragHelper.smoothSlideViewTo(contentView, 0, contentView.getTop())) {
            ViewCompat.postInvalidateOnAnimation(this);
            isOpen = false;
            if (swipeListener != null) {
                swipeListener.onSwipeStateChanged(false);
            }
        }
    }

    public boolean isOpen() {
        return isOpen;
    }
}