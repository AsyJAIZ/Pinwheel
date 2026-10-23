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

public class PinwheelBatteryDrawable extends Drawable {
    private static final String TAG = "Pinwheel";
    private Context context;
    protected boolean animation = false;
    boolean dualTone = true;
    private final int segmentCount = 8;
    private final Paint dualToneBackground;
    public Paint errorPaint;
    private final Paint fillPaint;
    private final Paint fillColorStrokePaint;
    private final Paint fillColorStrokeProtection;
    private final Paint fillStroke;

    private Drawable host;
    // Drawable хранит callback через WeakReference, поэтому нужно поле
    private final Drawable.Callback hostCb = new Drawable.Callback() {
        public void invalidateDrawable(Drawable who) { if (host != null) host.invalidateSelf(); }
        public void scheduleDrawable(Drawable who, Runnable r, long t) { if (host != null) host.scheduleSelf(r, t); }
        public void unscheduleDrawable(Drawable who, Runnable r) { if (host != null) host.unscheduleSelf(r); }
    };
    public void setHost(Drawable h) { host = h; setCallback(hostCb); }

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
    private float segmentStart = 252.25f; // 162.25 if top is 0, -17.75f if -180 to 180, 252.25 if start is 270
    private float segmentOffset = 45.25f; //they are a bit asymmetrical, i know this. offset to next segment
    private float segmentAngleSize = 33.25f;
    private float innerArea = 0.21f;
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

    public PinwheelBatteryDrawable(Context c, int f, Resources res) {
        this(c, f);

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

        errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setColor(Utils.getColorStateListDefaultColor(context, R.color.batterymeter_saver_color));
        errorPaint.setDither(true);
        errorPaint.setBlendMode(BlendMode.SRC);
        animation = true;
        dualTone = false;
        segmentStart = dualTone ? 252.25f : 257.5f; // 162.25 if top is 0, -17.75f if -180 to 180, 252.25 if start is 270
        segmentOffset = dualTone ? 45.25f : 42.5f; //they are a bit asymmetrical, i know this. offset to next segment
        segmentAngleSize = dualTone ? 33.25f : 25f; // dualTone is untested y'know
        innerArea = dualTone ? 0.21f : 0.26f;
    }

    public PinwheelBatteryDrawable(Context context, int frameColor) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
        intrinsicWidth = (int) (size * density);
        intrinsicHeight = (int) (size * density);

        segmentStart = dualTone ? 252.25f : 257.5f; // 162.25 if top is 0, -17.75f if -180 to 180, 252.25 if start is 270
        segmentOffset = dualTone ? 45.25f : 42.5f; //they are a bit asymmetrical, i know this. offset to next segment
        segmentAngleSize = dualTone ? 33.25f : 25f; // dualTone is untested y'know
        innerArea = dualTone ? 0.21f : 0.26f;

        dualToneBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        dualToneBackground.setColor(frameColor);
        dualToneBackground.setAlpha(85);
        dualToneBackground.setDither(true);
        dualToneBackground.setStyle(Paint.Style.FILL);

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

        fillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillStroke.setColor(frameColor);
        fillStroke.setAlpha(255);
        fillStroke.setDither(true);
        fillStroke.setStrokeWidth(1f);
        fillStroke.setStyle(Paint.Style.STROKE);
        fillStroke.setBlendMode(BlendMode.SRC);
        fillStroke.setStrokeMiter(1f);
        fillStroke.setStrokeJoin(Paint.Join.ROUND);

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
                } else if (charging && !fillInside || !charging && fillInside) {
                    fillAnim.setFloatValues(0, batteryLevel);
                    fillAnim.setDuration(15L * batteryLevel); // Длительность анимации в миллисекундах * watt
                    if (charging) {
                        fillInside = true;
                        chargeSpeed = (long) (duration10Watt / wattMultiplier);
                        rotationAnim.setInterpolator(new AccelerateInterpolator());
                        rotationAnim.setDuration(chargeSpeed * 2);
                        rotationAnim.setRepeatCount(1);
                        rotationAnim.start();
                        animation.start();
                    } else {
                        fillInside = false;
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
    }

    private long chargeSpeed;
    public void setWattage(float wattage) {
        mainHandler.post(() -> {
            wattMultiplier = wattage / 10;
            chargeSpeed = (long) (duration10Watt / wattMultiplier);
            rotationAnim.setDuration(chargeSpeed);
            });
    }

    public void setMicroWattage(int microWattage) {
        mainHandler.post(() -> {
            if (microWattage > 0 && microWattage != microWatt) {
                microWatt = microWattage;
                wattMultiplier = microWattage / 10000000f;
                chargeSpeed = (long) (duration10Watt / wattMultiplier);
                if (rotatingState == STABLE)
                    rotationAnim.setDuration(chargeSpeed);
            }
        });
    }

    private Boolean charging = false;
    private boolean fillInside = false;
    public void setCharging(Boolean charging) {
        if (this.charging == (boolean) charging) { postInvalidate(); return; }
        this.charging = charging;
        if (animation) {
            if (Looper.myLooper() == Looper.getMainLooper())
                animateCharging();
            else
                mainHandler.post(this::animateCharging);
        } else {
            fillInside = charging;
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
        // unscheduleSelf(this::invalidateSelf);
        // scheduleSelf(this::invalidateSelf, 0);
        invalidateSelf();
    }

    void setColors(int fgColor, int bgColor, int singleToneColor) { // todo: needs upd
        fillColor = dualTone ? fgColor : singleToneColor;

        fillPaint.setColor(fillColor);
        fillColorStrokePaint.setColor(fillColor);
        fillStroke.setColor(fillColor);
        dualToneBackground.setColor(bgColor);

        levelColor = batteryColorForLevel(batteryLevel);
        invalidateSelf();
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
                dualTone ? "M10,7.9C8.4,6.3,7.5,2,8.4,0c0.9,1.3,2.2,2.3,3.6,3C10.5,4.2,9.9,5.8,10,7.9z" : "M 10.005431,6.9017499 C 8.8097847,5.7061044 8.1372341,2.492807 8.8097847,0.99825005 9.4823354,1.969712 10.453798,2.7169905 11.499988,3.2400855 10.379069,4.1368197 9.9307025,5.3324652 10.005431,6.9017499 Z" // 3.957x7.9 dualtone
        ));
        segment[1] = new Path(PathParser.createPathFromPathData(
                dualTone ? "M11.5,8.5c-0.1-2.3,2.4-5.9,4.4-6.7c-0.2,1.6-0.1,3.2,0.4,4.6C14.4,6.3,12.9,7,11.5,8.5z" : "M 11.999802,7.8025715 C 11.92062,5.9814031 13.900151,3.1308784 15.483775,2.4974285 15.325412,3.7643283 15.404594,5.0312283 15.8005,6.1397657 14.296058,6.0605839 13.108338,6.6148532 11.999802,7.8025715 Z"
        ));
        segment[2] = new Path(PathParser.createPathFromPathData(
                dualTone ? "M12.1,10C13.7,8.4,18,7.5,20,8.4c-1.3,0.9-2.3,2.2-3,3.6C15.8,10.5,14.2,9.9,12.1,10z" : "M 12.6,10.002232 C 13.997468,8.6047639 17.753165,7.8186879 19.5,8.6047639 18.364557,9.3908398 17.491139,10.526283 16.879747,11.749068 15.831646,10.438941 14.434177,9.9148905 12.6,10.002232 Z"
        ));
        segment[3] = new Path(PathParser.createPathFromPathData(
                dualTone ? "M11.5,11.5c2.3-0.1,5.9,2.4,6.7,4.4c-1.6-0.2-3.2-0.1-4.6,0.4C13.7,14.4,13,12.9,11.5,11.5z" : "m 12,11.858445 c 1.956716,-0.08507 5.019403,2.041791 5.7,3.743284 -1.361194,-0.17015 -2.722388,-0.08507 -3.913433,0.340298 C 13.871642,14.325609 13.276119,13.04949 12,11.858445 Z"
        ));
        segment[4] = new Path(PathParser.createPathFromPathData(
                dualTone ? "M10,12.1c1.6,1.6,2.5,5.9,1.6,7.9c-0.9-1.3-2.2-2.3-3.6-3C9.5,15.8,10.1,14.2,10,12.1z" : "m 9.9945696,13.09825 c 1.1956454,1.195646 1.8681964,4.408943 1.1956454,5.9035 -0.67255,-0.971462 -1.6440125,-1.718741 -2.6902026,-2.241836 1.1209179,-0.896732 1.5692846,-2.092378 1.4945572,-3.661664 z"
        ));
        segment[5] = new Path(PathParser.createPathFromPathData(
                dualTone ? "M8.5,11.5c0.1,2.3-2.4,5.9-4.4,6.7c0.2-1.6,0.1-3.2-0.4-4.6C5.6,13.7,7.1,13,8.5,11.5z" : "m 8.0006517,12.19743 c 0.079181,1.821168 -1.9003491,4.67169 -3.4839733,5.30514 0.1583624,-1.266899 0.079181,-2.533799 -0.3167249,-3.642335 1.504443,0.07918 2.6921612,-0.475087 3.8006982,-1.662805 z"
        ));
        segment[6] = new Path(PathParser.createPathFromPathData(
                dualTone ? "M7.9,10C6.3,11.6,2,12.5,0,11.6C1.3,10.7,2.3,9.4,3,8C4.2,9.5,5.8,10.1,7.9,10z" : "M 7.4,9.9977675 C 6.0025316,11.395236 2.2468354,12.181312 0.5,11.395236 1.635443,10.60916 2.5088608,9.4737169 3.1202532,8.2509321 4.1683544,9.5610587 5.5658228,10.085109 7.4,9.9977675 Z"
        ));
        segment[7] = new Path(PathParser.createPathFromPathData(
                dualTone ? "M8.5,8.5C6.2,8.6,2.6,6.1,1.8,4.1C3.4,4.3,5,4.2,6.4,3.7C6.3,5.6,7,7.1,8.5,8.5z" : "M 8,8.1415545 C 6.0432836,8.2266291 2.980597,6.0997634 2.3,4.3982709 3.661194,4.5684202 5.0223881,4.4833455 6.2134328,4.0579724 6.1283582,5.6743903 6.7238806,6.9505097 8,8.1415545 Z"
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
            if (!dualTone)
                canvas.drawPath(scaledSegment[i], fillStroke);
            if (batteryLevelAnim >= (i + 1) * segmentLevel && !fillInside) {
                canvas.drawPath(scaledSegment[i], currentPaint);
            } else {
                if (dualTone)
                    canvas.drawPath(scaledSegment[i], dualToneBackground); // todo logics impl

                if (batteryLevelAnim > i * segmentLevel || fillInside) {
                    canvas.save();

                    float localProgress = (batteryLevelAnim - i * segmentLevel) / segmentLevel;

                    float startAngle = segmentStart + (segmentOffset * i);
                    float sweepAngle = localProgress * (segmentAngleSize);

                    if (fillInside) {
                        float centerX = bounds.centerX();
                        float centerY = bounds.centerY();
                        float maxRadius = bounds.width() / 2f;
                        float innerRadius = (innerArea + 0.20f) * maxRadius; // 0.20f is the space that the bolt takes up
                        float outerRadius = innerRadius + (maxRadius - innerRadius - (dualTone ? 0f : fillStroke.getStrokeWidth())) * ((float) batteryLevelAnim / maxLevel);

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

        if (charging) {
            if (!invertFillIcon)
                canvas.drawPath(scaledBolt, fillPaint);
            canvas.clipOutPath(scaledBolt);
            canvas.drawPath(scaledBolt, invertFillIcon ? fillColorStrokePaint : fillColorStrokeProtection);
        } else if (powerSaveEnabled) {
            fillPaint.setColor(fillColor);
            canvas.drawPath(scaledPlus, fillPaint);
            canvas.clipOutPath(scaledPlus);
            canvas.drawPath(scaledPlus, fillColorStrokeProtection);

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
        fillStroke.setStrokeWidth((float) bounds.right / size * 1.5f); // 1f
    }

    public void setBatteryLevel(int progress) { // todo upd
        invertFillIcon = false; //(progress >= 40) ? true : ((progress <= 22) ? false : invertFillIcon);
        batteryLevel = progress;
        batteryLevelAnim = progress;
        fillAnim.setFloatValues(0, progress);
        levelColor = batteryColorForLevel(progress);
        invalidateSelf();
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
        fillStroke.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
