package com.asyjaiz.pinwheeltester;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.PathParser;
import android.util.TypedValue;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import java.util.Arrays;

public class PinwheelBatteryDrawable extends Drawable {
    private static final String TAG = "Pinwheel";
    private Context context;
    protected boolean animation = true;
    private final boolean dualTone = true;
    private final int segmentCount = 8;
    private final Paint dualToneBackground;
    public Paint errorPaint;
    private final Paint fillPaint;
    private final Paint fillColorStrokePaint;
    private final Paint fillColorStrokeProtection;
    protected int[] colors;
    private int fillColor = Color.WHITE;
    private int levelColor;

    public Boolean invertFillIcon = false;
    private final Path[] segment = new Path[segmentCount];
    private final Path[] scaledSegment = new Path[segmentCount];
    private final RectF[] segBounds = new RectF[segmentCount];
    private final Path boltPath = new Path();
    private final Path scaledBolt = new Path();
    private final Path plusPath = new Path();
    private final Path scaledPlus = new Path();

    private int batteryLevel = 0;
    private int batteryLevelAnim = 0;
    public int criticalLevel = 10; // todo get

    private final int maxLevel = 100;
    private final float segmentLevel = (float) maxLevel / segmentCount; // Сколько процентов на сегмент

    protected final ValueAnimator fillAnim = ValueAnimator.ofFloat(0, batteryLevel);
    private final ValueAnimator rotationAnim = ValueAnimator.ofFloat(360f, 0f);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    private final int size = 20;
    private final float segmentStart = 252.25f; // 162.25 if top is 0, -17.75f if -180 to 180, 252.25 if start is 270
    private final float segmentOffset = 45.25f; //they are a bit asymmetrical, i know this. offset to next segment
    private final float segmentAngleSize = 33.25f;
    private final float innerArea = 0.21f;
    private float rotation = 0f;

    private int microWatt = 1000000; // temp
    private float wattMultiplier = microWatt / 10000000f;
    private final int duration10Watt = 2000;

    protected static final int ENTER = 0;
    protected static final int STABLE = 1;
    protected static final int EXIT = 2;
    protected int rotatingState = EXIT;

    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private float density;
    private final Matrix scaleMatrix = new Matrix();
    private Rect bounds = new Rect(0,0,0,0);

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }
    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    public PinwheelBatteryDrawable(Context context, int frameColor, Resources res) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
        intrinsicWidth = (int) (size * density);
        intrinsicHeight = (int) (size * density);

        TypedArray color_levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray color_values = res.obtainTypedArray(R.array.batterymeter_color_values);
        colors = new int[2 * color_levels.length()];
        for (int i = 0; i < color_levels.length(); i++) {
            colors[2 * i] = color_levels.getInt(i, 0);
            if (color_values.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                // todo colors[2 * i + 1] = Utils.getColorAttrDefaultColor(context, color_values.getThemeAttributeId(i, 0), 0);
            } else {
                colors[2 * i + 1] = color_values.getColor(i, 0);
            }
        }
        color_levels.recycle();
        color_values.recycle();

        dualToneBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        dualToneBackground.setColor(frameColor);
        dualToneBackground.setAlpha(85);
        dualToneBackground.setDither(true);
        dualToneBackground.setStyle(Paint.Style.FILL);

        errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setColor(Utils.getColorStateListDefaultColor(context, R.color.batterymeter_saver_color));
        errorPaint.setDither(true);
        errorPaint.setBlendMode(BlendMode.SRC);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(frameColor);
        fillPaint.setAlpha(255);
        fillPaint.setDither(true);

        fillColorStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillColorStrokePaint.setColor(frameColor);
        fillColorStrokePaint.setAlpha(255);
        fillColorStrokePaint.setDither(true);
        fillColorStrokePaint.setStrokeWidth(5f);
        fillColorStrokePaint.setStyle(Paint.Style.STROKE);
        fillColorStrokePaint.setBlendMode(BlendMode.SRC);
        fillColorStrokePaint.setStrokeMiter(5f);
        fillColorStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        fillColorStrokeProtection = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillColorStrokeProtection.setDither(true);
        fillColorStrokeProtection.setStrokeWidth(5f);
        fillColorStrokeProtection.setStyle(Paint.Style.STROKE);
        fillColorStrokeProtection.setBlendMode(BlendMode.CLEAR);
        fillColorStrokeProtection.setStrokeMiter(5f);
        fillColorStrokeProtection.setStrokeJoin(Paint.Join.ROUND);

        fillAnim.setInterpolator(new PathInterpolator(0.76f, 0f, 0.24f, 1f));
        fillAnim.addUpdateListener(animation -> {
            batteryLevelAnim = Math.round((float) animation.getAnimatedValue());
            postInvalidate();
        });
        fillAnim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled = false;
            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                super.onAnimationEnd(animation, isReverse);
                if (cancelled) {
                    cancelled = false;
                    if (isReverse) {
                        fillAnim.setFloatValues(batteryLevelAnim, batteryLevel);
                        fillAnim.setDuration(15L * (batteryLevel - batteryLevelAnim));
                        animation.start();
                    } else {
                        fillAnim.setFloatValues(batteryLevelAnim, 0);
                        fillAnim.setDuration(15L * batteryLevelAnim);
                        animation.start();
                    }
                } else if (charging && !changing || !charging && changing) {
                    fillAnim.setFloatValues(0, batteryLevel);
                    fillAnim.setDuration(15L * batteryLevel); // Длительность анимации в миллисекундах * watt
                    if (charging) {
                        changing = true;
                        chargeSpeed = (long) (duration10Watt / wattMultiplier);
                        rotationAnim.setInterpolator(new AccelerateInterpolator());
                        rotationAnim.setDuration(chargeSpeed * 2);
                        rotationAnim.setRepeatCount(1);
                        Log.d(TAG, "onAnimationEnd: Starting");
                        rotationAnim.start();
                        animation.start();
                    } else {
                        changing = false;
                        animation.start();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                cancelled = true;
            }
        });

        rotationAnim.addUpdateListener(animation -> {
            rotation = (float) animation.getAnimatedValue();
            postInvalidate();
        });
        LinearInterpolator linear = new LinearInterpolator();
        rotationAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                super.onAnimationRepeat(animation);
                if (rotatingState == ENTER) {
                    rotationAnim.cancel();
                    rotationAnim.setInterpolator(linear);
                    rotationAnim.setDuration(chargeSpeed);
                    rotationAnim.setRepeatCount(ValueAnimator.INFINITE);
                    rotatingState = STABLE;
                    rotationAnim.start();
                } else if (rotatingState == EXIT) {
                    //float rotateAddtitionalF = 1 / 1.5f;
                    rotationAnim.cancel();
                    rotationAnim.setInterpolator(new DecelerateInterpolator());
                    rotationAnim.setDuration(chargeSpeed * 2);
                    rotationAnim.setRepeatCount(0);
                    rotationAnim.start();
                }
            }
        });

        loadSegments();
        // loadBounds(segment);
    }

    private long chargeSpeed;
    public void setWattage(float wattage) {
        wattMultiplier = wattage / 10;
        chargeSpeed = (long) (duration10Watt / wattMultiplier);
        rotationAnim.setDuration(chargeSpeed);
    }

    public void setMicroWattage(int microWattage) {
        if (microWattage >= 0 && microWattage != microWatt) {
            microWatt = microWattage;
            wattMultiplier = microWattage / 10000000f;
            chargeSpeed = (long) (duration10Watt / wattMultiplier);
            rotationAnim.setDuration(chargeSpeed);
        }
    }

    private Boolean charging = false;
    private boolean changing = false;
    public void setCharging(Boolean charging) {
        this.charging = charging;
        if (animation) {
            if (Looper.myLooper() == Looper.getMainLooper())
                animateCharging();
            else
                mainHandler.post(this::animateCharging);
        } else {
            changing = charging;
            batteryLevelAnim = batteryLevel;
        }
        postInvalidate();
    }

    private void animateCharging() {
        if (fillAnim.isRunning())
            fillAnim.cancel();
        else {
            fillAnim.setFloatValues(0, batteryLevel);
            fillAnim.setDuration(15L * batteryLevel);
            fillAnim.reverse();
        }
        if (rotatingState == STABLE || rotatingState == ENTER)
            rotatingState = EXIT;
        else if (charging) {
            rotatingState = ENTER;
        }
    }

    private Boolean powerSaveEnabled = false;
    public void setPowerSaveEnabled(Boolean powerSaveEnabled) {
        this.powerSaveEnabled = powerSaveEnabled;
        postInvalidate();
    }

    private void postInvalidate() {
        unscheduleSelf(this::invalidateSelf);
        scheduleSelf(this::invalidateSelf, 0);
    }

    void setColors(int fgColor, int bgColor, int singleToneColor) { // todo: needs upd
        int fillColor = singleToneColor; // dualTone ? fgColor : singleToneColor;
        Log.d(TAG, "setColors: color is " + fillColor);

        this.fillColor = fillColor;
        fillPaint.setColor(fillColor);
        fillColorStrokePaint.setColor(fillColor);
        dualToneBackground.setColor(bgColor);

        levelColor = batteryColorForLevel(batteryLevel);

        postInvalidate();
    }

    private int getColorForLevel(int percent) {
        int thresh;
        int color = 0;
        int i = 0;

        while (i < colors.length) {
            thresh = colors[i];
            color = colors[i + 1];
            if (percent <= thresh) {
                // Respect tinting for "normal" level
                return (i == colors.length - 2) ? fillColor : color;
            }
            i += 2;
        }
        Log.d(TAG, "getColorForLevel: " + color);
        return color;
    }

    private int batteryColorForLevel(int level) {
        if (charging || powerSaveEnabled)
            return fillColor;
        else
            return getColorForLevel(level);
    }

    private void loadSegments() {
        segment[0] = new Path(PathParser.createPathFromPathData(
                "M10,7.9C8.4,6.3,7.5,2,8.4,0c0.9,1.3,2.2,2.3,3.6,3C10.5,4.2,9.9,5.8,10,7.9z" // 3.957x7.9
        ));
        segment[1] = new Path(PathParser.createPathFromPathData(
                "M11.5,8.5c-0.1-2.3,2.4-5.9,4.4-6.7c-0.2,1.6-0.1,3.2,0.4,4.6C14.4,6.3,12.9,7,11.5,8.5z"
        ));
        segment[2] = new Path(PathParser.createPathFromPathData(
                "M12.1,10C13.7,8.4,18,7.5,20,8.4c-1.3,0.9-2.3,2.2-3,3.6C15.8,10.5,14.2,9.9,12.1,10z"
        ));
        segment[3] = new Path(PathParser.createPathFromPathData(
                "M11.5,11.5c2.3-0.1,5.9,2.4,6.7,4.4c-1.6-0.2-3.2-0.1-4.6,0.4C13.7,14.4,13,12.9,11.5,11.5z"
        ));
        segment[4] = new Path(PathParser.createPathFromPathData(
                "M10,12.1c1.6,1.6,2.5,5.9,1.6,7.9c-0.9-1.3-2.2-2.3-3.6-3C9.5,15.8,10.1,14.2,10,12.1z"
        ));
        segment[5] = new Path(PathParser.createPathFromPathData(
                "M8.5,11.5c0.1,2.3-2.4,5.9-4.4,6.7c0.2-1.6,0.1-3.2-0.4-4.6C5.6,13.7,7.1,13,8.5,11.5z"
        ));
        segment[6] = new Path(PathParser.createPathFromPathData(
                "M7.9,10C6.3,11.6,2,12.5,0,11.6C1.3,10.7,2.3,9.4,3,8C4.2,9.5,5.8,10.1,7.9,10z"
        ));
        segment[7] = new Path(PathParser.createPathFromPathData(
                "M8.5,8.5C6.2,8.6,2.6,6.1,1.8,4.1C3.4,4.3,5,4.2,6.4,3.7C6.3,5.6,7,7.1,8.5,8.5z"
        ));
        for (int i = 0; i < segmentCount; i++) {
            scaledSegment[i] = new Path();
        }

        String boltPathString = "M5,17.5 V12 H3 L7,4.5 V10 h2 L5,17.5 z"; // context.getResources().getString(
                // com.android.internal.R.string.config_batterymeterBoltPath); // M5,17.5 V12 H3 L7,4.5 V10 h2 L5,17.5 z
        boltPath.set(PathParser.createPathFromPathData(boltPathString));
        String plusPathString = "M9,10l-2,0l0,-2l-2,0l0,2l-2,0l0,2l2,0l0,2l2,0l0,-2l2,0z"; // context.getResources().getString(
                // com.android.internal.R.string.config_batterymeterPowersavePath);
        plusPath.set(PathParser.createPathFromPathData(plusPathString));
    }

    private void loadBounds(Path[] segment) {
        for (int i = 0; i < segmentCount; i++) {
            segBounds[i] = new RectF();
            segment[i].computeBounds(segBounds[i], true);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        fillPaint.setColor(levelColor);
        Paint currentPaint = powerSaveEnabled ? errorPaint : fillPaint;
        canvas.saveLayer(null, null);
        canvas.save();
        if (rotationAnim.isRunning())
            canvas.rotate(rotation, bounds.centerX(), bounds.centerY());
        for (int i = 0; i < segmentCount; i++) {
            if (batteryLevelAnim >= (i + 1) * segmentLevel && !changing) {
                canvas.drawPath(scaledSegment[i], currentPaint);
            } else {
                if (dualTone)
                    canvas.drawPath(scaledSegment[i], dualToneBackground); // todo logics impl

                if (true) {//batteryLevelAnim > i * segmentLevel || changing) {
                    canvas.save();

                    float localProgress = (batteryLevelAnim - i * segmentLevel) / segmentLevel;

                    float startAngle = segmentStart + (segmentOffset * i);
                    float sweepAngle = localProgress * (segmentAngleSize);

                    if (changing) {
                        float centerX = bounds.centerX();
                        float centerY = bounds.centerY();
                        float maxRadius = bounds.width() / 2f;
                        float innerRadius = (innerArea + 0.20f) * maxRadius; // 0.20f is the space that the bolt takes up
                        float outerRadius = innerRadius + (maxRadius - innerRadius) * ((float) batteryLevelAnim / maxLevel);

                        Path innerClipPath = new Path();
                        innerClipPath.addCircle(centerX, centerY, innerRadius, Path.Direction.CW);
                        canvas.clipOutPath(innerClipPath);

                        Path outerClipPath = new Path();
                        outerClipPath.addCircle(centerX, centerY, outerRadius, Path.Direction.CW);
                        canvas.clipPath(outerClipPath);

                        sweepAngle = segmentAngleSize;
                    }

                    canvas.clipPath(scaledSegment[i]);
                    canvas.drawArc(new RectF(bounds), startAngle, sweepAngle, true, currentPaint);

                    canvas.restore();
                }
            }
        }
        canvas.restore();

        if (changing) {
            if (!invertFillIcon)
                canvas.drawPath(scaledBolt, fillPaint);
            canvas.clipOutPath(scaledBolt);
            canvas.drawPath(scaledBolt, invertFillIcon ? fillColorStrokePaint : fillColorStrokeProtection);
        } else if (powerSaveEnabled) {
            fillPaint.setColor(fillColor);
            canvas.drawPath(scaledPlus, fillPaint);
        }
        canvas.restore();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        this.bounds = bounds;
        if (bounds.isEmpty()) {
            scaleMatrix.setScale(1f, 1f);
        } else {
            scaleMatrix.setScale(((float) bounds.right / size), ((float) bounds.bottom / size));
        }

        for (int i = 0; i < segmentCount; i++) {
            segment[i].transform(scaleMatrix, scaledSegment[i]);
        }

        RectF boltBounds = new RectF();
        boltPath.computeBounds(boltBounds, true);
        scaleMatrix.preTranslate(-boltBounds.left + (size / 2.0f - boltBounds.width() / 2), -boltBounds.top + (size / 2.0f - boltBounds.height() / 2));
        boltPath.transform(scaleMatrix, scaledBolt);
        plusPath.transform(scaleMatrix, scaledPlus);

        float PROTECTION_STROKE_WIDTH = 3f;
        // Arbitrarily chosen for visibility at small sizes
        float PROTECTION_MIN_STROKE_WIDTH = 6f;
        float scaledStrokeWidth =
                Math.max((float) bounds.right / size * PROTECTION_STROKE_WIDTH, PROTECTION_MIN_STROKE_WIDTH);

        fillColorStrokePaint.setStrokeWidth(scaledStrokeWidth);
        fillColorStrokeProtection.setStrokeWidth(scaledStrokeWidth);

        // loadBounds(scaledSegment);
    }

    public void setBatteryLevel(int progress) { // todo upd
        invertFillIcon = false; //(progress >= 40) ? true : ((progress <= 22) ? false : invertFillIcon);
        batteryLevel = progress;
        batteryLevelAnim = progress;
        fillAnim.setFloatValues(0, progress);
        levelColor = batteryColorForLevel(progress);
        postInvalidate();
    }

    @Override
    public void setAlpha(int alpha) {
        dualToneBackground.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        dualToneBackground.setColorFilter(colorFilter);
        fillColorStrokePaint.setColorFilter(colorFilter);
        fillPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}