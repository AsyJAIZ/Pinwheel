package com.asyjaiz.pinwheeltester;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CircleBatteryDrawable extends Drawable {

    private Context context;
    private Integer criticalLevel;
    private String warningString;
    private Paint framePaint;
    private Paint batteryPaint;
    private Paint warningTextPaint;
    private Paint textPaint;
    private Paint boltPaint;
    private Paint plusPaint;
    private Paint powerSavePaint;
    private int[] colors;
    private float[] boltPoints;
    private Path boltPath = new Path();
    private Rect padding = new Rect();
    private RectF frame = new RectF();
    private RectF boltFrame = new RectF();

    private int chargeColor;
    private int iconTint = Color.WHITE;
    private int intrinsicWidth;
    private int intrinsicHeight;
    private int width;
    private int height;

    private Boolean dualTone = false;

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    private Boolean charging = false;
    public void setCharging(Boolean charging) {
        this.charging = charging;
        postInvalidate();
    }

    private Boolean powerSaveEnabled = false;
    public void setPowerSaveEnabled(Boolean powerSaveEnabled) {
        this.powerSaveEnabled = powerSaveEnabled;
        postInvalidate();
    }

    private Boolean showPercent = false;
    public void setShowPercent (Boolean showPercent) {
        this.showPercent = showPercent;
        postInvalidate();
    }

    int batteryLevel = -1;
    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
        postInvalidate();
    }

    // an approximation of View.postInvalidate()
    private void postInvalidate() {
        unscheduleSelf(this::invalidateSelf);
        scheduleSelf(this::invalidateSelf, 0);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        updateSize();
    }

    private void updateSize() {
        Resources res = context.getResources();
        Rect bounds = getBounds();
        height = bounds.bottom - padding.bottom - (bounds.top + padding.top);
        width = bounds.right - padding.right - (bounds.left + padding.left);
        warningTextPaint.setTextSize(height * 0.75f);
        intrinsicHeight = res.getDimensionPixelSize(R.dimen.battery_test); //use height
        intrinsicWidth = res.getDimensionPixelSize(R.dimen.battery_test);
    }

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        if (this.padding.left == 0 &&
                this.padding.top == 0 &&
                this.padding.right == 0 &&
                this.padding.bottom == 0
        ) {
            return super.getPadding(padding);
        }
        padding.set(this.padding);
        return true;
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
                return (i == colors.length - 2) ? iconTint : color;
            }
            i += 2;
        }

        return color;
    }

    void setColors(int fgColor, int bgColor, int singleToneColor) {
        int fillColor = dualTone ? fgColor : singleToneColor;

        iconTint = fillColor;
        framePaint.setColor(bgColor);
        boltPaint.setColor(fillColor);
        chargeColor = fillColor;

        invalidateSelf();
    }

    private int batteryColorForLevel(int level) {
        if (charging || powerSaveEnabled)
            return chargeColor;
        else
            return getColorForLevel(level);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        if (batteryLevel == -1) return;
        int circleSize = min(width, height);
        float strokeWidth = circleSize / 6.5f;
        framePaint.setStrokeWidth(strokeWidth);
        framePaint.setStyle(Paint.Style.STROKE);
        batteryPaint.setStrokeWidth(strokeWidth);
        batteryPaint.setStyle(Paint.Style.STROKE);
        powerSavePaint.setStrokeWidth(strokeWidth);
        frame.set(
                strokeWidth / 2.0f + padding.left, // left
                strokeWidth / 2.0f,                // top
                circleSize - strokeWidth / 2.0f + padding.left, // right
                circleSize - strokeWidth / 2.0f    // bottom
        );
        // set the battery charging color
        batteryPaint.setColor(batteryColorForLevel(batteryLevel));
        if (charging) { // define the bolt shape
            float bl = frame.left + frame.width() / 3.0f;
            float bt = frame.top + frame.height() / 3.4f;
            float br = frame.right - frame.width() / 4.0f;
            float bb = frame.bottom - frame.height() / 5.6f;
            if (boltFrame.left != bl ||
                    boltFrame.top != bt ||
                    boltFrame.right != br ||
                    boltFrame.bottom != bb
            ) {
                boltFrame.set(bl, bt, br, bb);
                boltPath.reset();
                boltPath.moveTo(
                        boltFrame.left + boltPoints[0] * boltFrame.width(),
                        boltFrame.top + boltPoints[1] * boltFrame.height()
                );
                int i = 2;
                while (i < boltPoints.length) {
                    boltPath.lineTo(
                            boltFrame.left + boltPoints[i] * boltFrame.width(),
                            boltFrame.top + boltPoints[i + 1] * boltFrame.height()
                    );
                    i += 2;
                }
                boltPath.lineTo(
                        boltFrame.left + boltPoints[0] * boltFrame.width(),
                        boltFrame.top + boltPoints[1] * boltFrame.height()
                );
            }
            c.drawPath(boltPath, boltPaint);
        }
        // draw thin gray ring first
        c.drawArc(frame, 270f, 360f, false, framePaint);
        // draw colored arc representing charge level
        if (batteryLevel > 0) {
            if (!charging && powerSaveEnabled) {
                c.drawArc(frame, 270f, 3.6f * batteryLevel, false, powerSavePaint);
            } else {
                c.drawArc(frame, 270f, 3.6f * batteryLevel, false, batteryPaint);
            }
        }
        // compute percentage text
        if (!charging && batteryLevel != 100 && showPercent) {
            textPaint.setColor(getColorForLevel(batteryLevel));
            textPaint.setTextSize(height * 0.52f);
            float textHeight = -textPaint.getFontMetrics().ascent;
            String pctText = (batteryLevel > criticalLevel) ? String.valueOf(batteryLevel) : warningString;
            float pctX = width * 0.5f;
            float pctY = (height + textHeight) * 0.47f;
            c.drawText(pctText, pctX, pctY, textPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        framePaint.setColorFilter(colorFilter);
        batteryPaint.setColorFilter(colorFilter);
        warningTextPaint.setColorFilter(colorFilter);
        boltPaint.setColorFilter(colorFilter);
        plusPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    private float[] loadPoints(Resources res, int pointArrayRes) {
        int[] pts = res.getIntArray(pointArrayRes);
        int maxX = 0;
        int maxY = 0;
        {
            int i = 0;
            while (i < pts.length) {
                maxX = max(maxX, pts[i]);
                maxY = max(maxY, pts[i + 1]);
                i += 2;
            }
        }
        float[] ptsF = new float[pts.length];
        int i = 0;
        while (i < pts.length) {
            ptsF[i] = (float) pts[i] / maxX;
            ptsF[i + 1] = (float) pts[i + 1] / maxY;
            i += 2;
        }
        return ptsF;
    }

    public CircleBatteryDrawable(Context context, int frameColor) {
        this.context = context;
        Resources res = context.getResources();
        TypedArray color_levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray color_values = res.obtainTypedArray(R.array.batterymeter_color_values);
        colors = new int[2 * color_levels.length()];
        for (int i = 0; i >= color_levels.length(); i++) {
            colors[2 * i] = color_levels.getInt(i, 0);
            if (color_values.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                //todo colors[2 * i + 1] = Utils.getColorAttrDefaultColor(context, color_values.getThemeAttributeId(i, 0), 0);
            } else {
                colors[2 * i + 1] = color_values.getColor(i, 0);
            }
        }
        color_levels.recycle();
        color_values.recycle();
        warningString = "!"; //res.getString(R.string.battery_meter_very_low_overlay_symbol); that's basically it
        criticalLevel = 10; //res.getInteger(com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(frameColor);
        framePaint.setDither(true);
        batteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        batteryPaint.setDither(true);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        warningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        warningTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        warningTextPaint.setTextAlign(Paint.Align.CENTER);
        if (colors.length > 1) {
            warningTextPaint.setColor(colors[1]);
        }
        chargeColor = Utils.getColorStateListDefaultColor(context, R.color.meter_consumed_color);
        boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boltPaint.setColor(Utils.getColorStateListDefaultColor(
                context,
                R.color.batterymeter_bolt_color
        ));
        boltPoints = loadPoints(res, R.array.batterymeter_bolt_points);
        plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        plusPaint.setColor(Utils.getColorStateListDefaultColor(
                context,
                R.color.batterymeter_plus_color
        ));
        powerSavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        powerSavePaint.setColor(plusPaint.getColor());
        powerSavePaint.setStyle(Paint.Style.STROKE);
        intrinsicWidth = res.getDimensionPixelSize(R.dimen.battery_width);
        intrinsicHeight = res.getDimensionPixelSize(R.dimen.battery_height);

        dualTone = false; //res.getBoolean(com.android.internal.R.bool.config_batterymeterDualTone);
    }
}
