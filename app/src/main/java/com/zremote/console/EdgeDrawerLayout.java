package com.zremote.console;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * 根布局：识别「从屏幕右侧向左滑动」的手势，用于拉出设置面板。
 *
 * 直接在 dispatchTouchEvent 这一层处理，而不是用 onInterceptTouchEvent：
 * 网页在某些情况下不会消费 ACTION_DOWN，一旦没有任何子视图接手这次手势，
 * 父布局的拦截回调就不会再被调用，侧滑会直接失效。
 */
public class EdgeDrawerLayout extends FrameLayout {

    public interface OnEdgeDragListener {
        void onEdgeDragStart();

        void onEdgeDragMove(float dx);

        void onEdgeDragEnd(float dx);
    }

    /**
     * 起手区域：屏幕右侧这个比例范围内都可以起手左滑。
     * 不只用最边缘的十几 dp，是因为手势导航下系统「返回」手势会抢占屏幕边缘。
     */
    private static final float START_ZONE_RATIO = 0.3f;

    private final int touchSlop;

    private OnEdgeDragListener listener;
    private boolean edgeEnabled = true;
    private boolean fromRight;
    private boolean hijacked;
    private float downX;
    private float downY;

    public EdgeDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnEdgeDragListener(OnEdgeDragListener l) {
        listener = l;
    }

    /** 面板已展开时关闭手势，避免和面板内部的滚动/点击冲突 */
    public void setEdgeEnabled(boolean enabled) {
        edgeEnabled = enabled;
        if (!enabled) {
            fromRight = false;
            hijacked = false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!edgeEnabled) {
            return super.dispatchTouchEvent(ev);
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hijacked = false;
                fromRight = ev.getX() >= getWidth() * START_ZONE_RATIO;
                downX = ev.getX();
                downY = ev.getY();
                // 右侧起手时即使没有子视图消费，也要留住这次手势，否则收不到后续 MOVE
                return super.dispatchTouchEvent(ev) || fromRight;

            case MotionEvent.ACTION_MOVE:
                if (!hijacked && fromRight) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (dx < -touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        hijacked = true;
                        cancelChildGesture(ev);
                        if (listener != null) {
                            listener.onEdgeDragStart();
                        }
                    }
                }
                if (hijacked) {
                    if (listener != null) {
                        listener.onEdgeDragMove(ev.getX() - downX);
                    }
                    return true;
                }
                return super.dispatchTouchEvent(ev) || fromRight;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean dragging = hijacked;
                hijacked = false;
                fromRight = false;
                if (dragging) {
                    if (listener != null) {
                        listener.onEdgeDragEnd(ev.getX() - downX);
                    }
                    return true;
                }
                return super.dispatchTouchEvent(ev);

            default:
                return super.dispatchTouchEvent(ev);
        }
    }

    /** 手势被接管后给子视图补一个 CANCEL，避免网页停在「按住/滚动中」的状态 */
    private void cancelChildGesture(MotionEvent ev) {
        MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }
}