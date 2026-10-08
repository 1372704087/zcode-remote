package com.zremote.console;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 扫码取景框：半透明遮罩挖孔 + 四角框 + 上下往复的扫描线。
 */
public class ScanOverlayView extends View {

    /** 取景框边长占屏宽的比例 */
    private static final float FRAME_RATIO = 0.68f;
    /** 四角框臂长占取景框边长的比例 */
    private static final float CORNER_ARM_RATIO = 0.15f;

    private static final int MASK_COLOR = 0xA8000000;
    private static final int CORNER_COLOR = 0xFFFFFFFF;

    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 光晕：垂直方向由中间向上下淡出到全透明 */
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 光晕的水平遮罩：两端淡出到全透明（DST_IN） */
    private final Paint glowFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path maskPath = new Path();
    private final Path cornerPath = new Path();
    private final RectF frame = new RectF();

    private final float density;
    private float lineY;
    /** 光晕半高 */
    private float glowHalf;
    private ValueAnimator animator;

    public ScanOverlayView(Context context) {
        this(context, null);
    }

    public ScanOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        maskPaint.setStyle(Paint.Style.FILL);
        maskPaint.setColor(MASK_COLOR);

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setColor(CORNER_COLOR);
        cornerPaint.setStrokeWidth(3 * density);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setStrokeJoin(Paint.Join.ROUND);

        glowPaint.setStyle(Paint.Style.FILL);
        glowFadePaint.setStyle(Paint.Style.FILL);
        glowFadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        linePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float side = Math.min(w * FRAME_RATIO, h * 0.52f);
        float left = (w - side) / 2f;
        // 略微上移，给底部提示留出空间
        float top = (h - side) / 2f - h * 0.055f;
        frame.set(left, top, left + side, top + side);

        // 实线两端渐隐，模仿微信的样式
        linePaint.setShader(new LinearGradient(
                frame.left, 0f, frame.right, 0f,
                new int[]{0x00FFFFFF, 0x59FFFFFF, 0xFFFFFFFF, 0x59FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.12f, 0.5f, 0.88f, 1f},
                Shader.TileMode.CLAMP));

        // 光晕：垂直方向由中间向上下淡出到全透明，消除可见的胶囊边界
        glowHalf = 9 * density;
        glowPaint.setShader(new LinearGradient(
                0f, -glowHalf, 0f, glowHalf,
                new int[]{0x00FFFFFF, 0x45FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));

        // 光晕：水平方向两端同样淡出到全透明
        glowFadePaint.setShader(new LinearGradient(
                frame.left, 0f, frame.right, 0f,
                new int[]{0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.16f, 0.84f, 1f},
                Shader.TileMode.CLAMP));

        buildCorners();
        startLineAnimation();
    }

    private void buildCorners() {
        float arm = frame.width() * CORNER_ARM_RATIO;
        cornerPath.reset();
        // 左上
        cornerPath.moveTo(frame.left, frame.top + arm);
        cornerPath.lineTo(frame.left, frame.top);
        cornerPath.lineTo(frame.left + arm, frame.top);
        // 右上
        cornerPath.moveTo(frame.right - arm, frame.top);
        cornerPath.lineTo(frame.right, frame.top);
        cornerPath.lineTo(frame.right, frame.top + arm);
        // 右下
        cornerPath.moveTo(frame.right, frame.bottom - arm);
        cornerPath.lineTo(frame.right, frame.bottom);
        cornerPath.lineTo(frame.right - arm, frame.bottom);
        // 左下
        cornerPath.moveTo(frame.left + arm, frame.bottom);
        cornerPath.lineTo(frame.left, frame.bottom);
        cornerPath.lineTo(frame.left, frame.bottom - arm);
    }

    private void startLineAnimation() {
        if (animator != null) {
            animator.cancel();
        }
        float pad = 8 * density;
        animator = ValueAnimator.ofFloat(frame.top + pad, frame.bottom - pad);
        animator.setDuration(2200);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            lineY = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 遮罩：整屏减去取景框（EVEN_ODD 实现挖孔）
        maskPath.reset();
        maskPath.setFillType(Path.FillType.EVEN_ODD);
        maskPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        maskPath.addRect(frame, Path.Direction.CW);
        canvas.drawPath(maskPath, maskPaint);

        // 四角框
        canvas.drawPath(cornerPath, cornerPaint);

        // 光晕：垂直渐变铺底，再叠一层水平渐变遮罩，让四周都淡出到透明
        int layer = canvas.saveLayer(frame.left, lineY - glowHalf,
                frame.right, lineY + glowHalf, null);
        canvas.translate(0f, lineY);
        canvas.drawRect(frame.left, -glowHalf, frame.right, glowHalf, glowPaint);
        canvas.drawRect(frame.left, -glowHalf, frame.right, glowHalf, glowFadePaint);
        canvas.restoreToCount(layer);

        // 扫描实线
        float lineH = 2.4f * density;
        canvas.drawRoundRect(frame.left, lineY - lineH / 2f,
                frame.right, lineY + lineH / 2f, lineH / 2f, lineH / 2f, linePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }
}