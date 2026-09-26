package com.icebrowser.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * 边缘滑动返回 / 前进手势容器。
 *
 * 只在屏幕左右边缘 24dp 内起始的横向滑动才会被拦截，
 * 因此不会和网页自身的横向滚动、图片轮播冲突。
 * 拖动过程中实时平移子 View，松手后超过阈值就触发回调并回弹。
 */
public class IceSwipeLayout extends FrameLayout {

    public interface Callback {
        /** 是否可以返回（决定手势方向是否被响应）。 */
        boolean canGoBack();

        boolean canGoForward();

        void onSwipeBack();

        void onSwipeForward();
    }

    private static final int EDGE_DP = 24;
    private static final int TRIGGER_DP = 80;

    private Callback callback;
    private boolean enabled = true;

    private final int edgePx;
    private final int triggerPx;
    private final int touchSlop;

    private boolean dragging;
    private float downX;
    private float downY;
    private int activePointerId = -1;
    /** 正在被拖动平移的子 View（多 WebView 叠放时是当前可见的那个）。 */
    private View dragChild;

    public IceSwipeLayout(Context context) {
        this(context, null);
    }

    public IceSwipeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IceSwipeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        edgePx = (int) (EDGE_DP * density);
        triggerPx = (int) (TRIGGER_DP * density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    public void setGestureEnabled(boolean value) {
        this.enabled = value;
        if (!value) reset();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!enabled || callback == null) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                activePointerId = ev.getPointerId(0);
                dragging = false;
                return false;

            case MotionEvent.ACTION_MOVE: {
                if (activePointerId == -1) return false;
                int idx = ev.findPointerIndex(activePointerId);
                if (idx < 0) return false;
                float x = ev.getX(idx);
                float y = ev.getY(idx);
                float dx = x - downX;
                float dy = y - downY;

                if (Math.abs(dy) > Math.abs(dx)) return false;
                if (Math.abs(dx) < touchSlop) return false;

                boolean fromLeftEdge = downX <= edgePx;
                boolean fromRightEdge = downX >= getWidth() - edgePx;

                boolean wantBack = fromLeftEdge && dx > 0 && callback.canGoBack();
                boolean wantForward = fromRightEdge && dx < 0 && callback.canGoForward();

                if (wantBack || wantForward) {
                    // 容器里可能叠放着多个 WebView，只有当前可见的那个需要平移
                    dragChild = activeChild();
                    if (dragChild == null) return false;
                    dragging = true;
                    return true;
                }
                return false;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                reset();
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!dragging || callback == null) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                int idx = ev.findPointerIndex(activePointerId);
                if (idx < 0) return false;
                if (dragChild == null) return false;
                float dx = ev.getX(idx) - downX;
                dragChild.setTranslationX(dx);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (dragChild != null) {
                    float dx = dragChild.getTranslationX();
                    if (dx > triggerPx && callback.canGoBack()) {
                        animateOut(dragChild, 1, new Runnable() {
                            @Override public void run() {
                                callback.onSwipeBack();
                            }
                        });
                    } else if (dx < -triggerPx && callback.canGoForward()) {
                        animateOut(dragChild, -1, new Runnable() {
                            @Override public void run() {
                                callback.onSwipeForward();
                            }
                        });
                    } else {
                        animateBack(dragChild);
                    }
                }
                dragging = false;
                dragChild = null;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (dragChild != null) animateBack(dragChild);
                dragging = false;
                dragChild = null;
                return true;
            default:
                return false;
        }
    }

    /**
     * 当前需要平移的子 View。
     * 多标签时容器里叠放着多个 WebView，仅当前标签是 VISIBLE，
     * 因此优先取第一个可见子 View，取不到再退回第一个子 View。
     */
    private View activeChild() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) return child;
        }
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    private void animateOut(final View child, int direction, final Runnable after) {
        int distance = getWidth() / 4 * direction;
        child.animate()
                .translationX(distance)
                .setDuration(120)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (after != null) after.run();
                        child.animate().translationX(0).setDuration(160).start();
                    }
                })
                .start();
    }

    private void animateBack(View child) {
        child.animate().translationX(0).setDuration(140).start();
    }

    private void reset() {
        dragging = false;
        activePointerId = -1;
        View child = dragChild != null ? dragChild : activeChild();
        if (child != null && child.getTranslationX() != 0) {
            child.setTranslationX(0);
        }
        dragChild = null;
    }
}