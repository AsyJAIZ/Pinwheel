package com.asyjaiz.pinwheeltester;

import static com.asyjaiz.pinwheeltester.PinwheelBatteryDrawable.ENTER;
import static com.asyjaiz.pinwheeltester.PinwheelBatteryDrawable.EXIT;
import static com.asyjaiz.pinwheeltester.PinwheelBatteryDrawable.STABLE;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getFloatField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.content.Context;
import android.content.Intent;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.util.Log;
import android.util.TypedValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PinwheelPlace implements IXposedHookLoadPackage, IXposedHookInitPackageResources {

    private static final String TAG = "Pinwheeler";
    private String sysUi = "com.android.systemui";
    private boolean failing = false;
    private final Map<Object, PinwheelBatteryDrawable> batteryInstances = Collections.synchronizedMap(new WeakHashMap<>());
    private Class<?> powerSaveHookedClass;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        if (failing || !Objects.equals(param.packageName, sysUi)) return;
        log("Pinwheel went through the code hook!");

        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();

        Class<?> batteryClass = findClass("com.android.settingslib.graph.ThemedBatteryDrawable", param.classLoader);
        try {
            Constructor<?> batteryConstructor;
            boolean order = false;
            try {
                batteryConstructor = batteryClass.getConstructor(Context.class, int.class);
            } catch (NoSuchMethodException e) {
                batteryConstructor = batteryClass.getConstructor(int.class, Context.class);
                order = true;
            }
            boolean finalOrder = order;
            hooks.add(hookMethod(batteryConstructor, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    batteryInstances.put(param.thisObject, new PinwheelBatteryDrawable((Context) param.args[finalOrder ? 1:0], (int) param.args[finalOrder ? 0:1]));
                    log("Pinwheel new drawable " + System.identityHashCode(param.thisObject)
                        + "\n" + Log.getStackTraceString(new Throwable()));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    PinwheelBatteryDrawable d = batteryInstances.get(param.thisObject);
                    if (d == null) return; // XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    d.colors = (int[]) getObjectField(param.thisObject, "colorLevels");
                    d.errorPaint = (Paint) getObjectField(param.thisObject, "errorPaint");
                    d.setHost((Drawable) param.thisObject);
                    //batteryDrawable[0].dualTone = (boolean) getObjectField(param.thisObject, "dualTone");
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "draw", Canvas.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam p) throws Throwable {
                    PinwheelBatteryDrawable d = batteryInstances.get(p.thisObject);
                    if (d == null) return XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    d.draw((Canvas) p.args[0]);
                    return null;
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "getIntrinsicHeight", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam p) throws Throwable {
                    PinwheelBatteryDrawable d = batteryInstances.get(p.thisObject);
                    if (d == null) return XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    return d.getIntrinsicHeight();
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "getIntrinsicWidth", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam p) throws Throwable {
                    PinwheelBatteryDrawable d = batteryInstances.get(p.thisObject);
                    if (d == null) return XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    return d.getIntrinsicWidth();
                }
            }));
            hooks.add(findAndHookMethod(sysUi + ".battery.AccessorizedBatteryDrawable", param.classLoader, "getIntrinsicWidth", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    return (int) ((getBooleanField(methodHookParam.thisObject, "displayShield") ? 26.0f : 20.0f) * getFloatField(methodHookParam.thisObject, "density"));
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "onBoundsChange", Rect.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam p) throws Throwable {
                    PinwheelBatteryDrawable d = batteryInstances.get(p.thisObject);
                    if (d == null) return XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    d.onBoundsChange((Rect) p.args[0]);
                    return null;
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "setColorFilter", ColorFilter.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam p) throws Throwable {
                    PinwheelBatteryDrawable d = batteryInstances.get(p.thisObject);
                    if (d == null) return XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    d.setColorFilter((ColorFilter) p.args[0]);
                    return null;
                }
            }));
            hooks.add(findAndHookMethod(sysUi + ".battery.BatteryMeterView", param.classLoader,
                    "onBatteryLevelChanged",
                    int.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            int batteryLevel = (int) param.args[0];
                            boolean charging = (boolean) param.args[1];

                            PinwheelBatteryDrawable d = forView(param.thisObject);
                            if (d == null) return;
                            d.setBatteryLevel(batteryLevel);
                            d.setCharging(charging);
                            super.afterHookedMethod(param);
                        }
                    }));
            /*hooks.add(findAndHookMethod(sysUi + ".battery.BatteryMeterView", param.classLoader,
                "onPowerSaveChanged", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    PinwheelBatteryDrawable d = forView(p.thisObject);
                    if (d != null) d.setPowerSaveEnabled((boolean) p.args[0]);
                }
            }));*/
            Class<?> viewController = findClass(sysUi + ".battery.BatteryMeterViewController", param.classLoader);
            hooks.add(hookMethod(viewController.getConstructors()[0], new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam constructorParam) throws Throwable {
                    super.afterHookedMethod(constructorParam);
                    PinwheelBatteryDrawable d = forView(getObjectField(constructorParam.thisObject, "mView"));
                    if (d == null) return;

                    Object stateCallback = getObjectField(constructorParam.thisObject, "mBatteryStateChangeCallback");
                    synchronized (PinwheelPlace.this) {         // метод хукаем один раз на класс
                        if (powerSaveHookedClass == stateCallback.getClass()) return;
                        powerSaveHookedClass = stateCallback.getClass();
                    }

                    Method powerSaving = powerSaveHookedClass.getMethod("onPowerSaveChanged", boolean.class);
                    hooks.add(hookMethod(powerSaving, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            PinwheelBatteryDrawable t = forView(getObjectField(constructorParam.thisObject, "mView"));
                            if (t != null) t.setPowerSaveEnabled((boolean) param.args[0]);
                            super.afterHookedMethod(param);
                        }
                    }));
                }
            }));
            hooks.add(findAndHookMethod(sysUi + ".battery.BatteryMeterView", param.classLoader,
                    "updateColors", int.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    PinwheelBatteryDrawable d = batteryInstances.get(((DrawableWrapper) getObjectField(param.thisObject, "mDrawable")).getDrawable());
                    if (d != null) d.setColors((int) param.args[0], (int) param.args[1], (int) param.args[2]);
                }
            }));
            hooks.add(findAndHookConstructor("com.android.settingslib.fuelgauge.BatteryStatus", param.classLoader,
                    Intent.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            int wattage = getIntField(param.thisObject, "maxChargingWattage");
                            synchronized (batteryInstances) {
                                batteryInstances.forEach((o, b) -> b.setMicroWattage(wattage));
                            }
                        }
                    }));
        } catch (Throwable e) {
            log("Pinwheel hook failed: " + e.getMessage() + ", " + e.getClass().getName());
            log("Pinwheeler unhooks all methods and turns off...");
            for (XC_MethodHook.Unhook hook:
                 hooks) {
                hook.unhook();
            }
            failing = true;
        }
    }

    private PinwheelBatteryDrawable forView(Object view) {
        Drawable inner = ((DrawableWrapper) getObjectField(view, "mDrawable")).getDrawable();
        return batteryInstances.get(inner);
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resParam) throws Throwable {
        if (failing || !resParam.packageName.equals(sysUi)) return;

        //XModuleResources res = XModuleResources.createInstance("/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk", resParam.res);
        log("Pinwheel went through the resource hook!");
        float spSize = 15.0f;
        resParam.res.setReplacement(sysUi, "dimen", "status_bar_battery_icon_width",
                new XResources.DimensionReplacement(spSize, TypedValue.COMPLEX_UNIT_SP)); // res.fwd(resParam.res.getIdentifier("status_bar_battery_icon_height", "dimen", sysUi))
        resParam.res.setReplacement(sysUi, "dimen", "status_bar_battery_icon_height",
                new XResources.DimensionReplacement(spSize, TypedValue.COMPLEX_UNIT_SP));
        /*try {
            resParam.res.setReplacement(sysUi, "dimen", "status_bar_battery_unified_icon_width",
                    res.fwd(resParam.res.getIdentifier("status_bar_battery_unified_icon_height", "dimen", sysUi)));
        } catch (Throwable e) {
            log("Pinwheel resource replacement failed: " + e.getMessage());
        }*/
    }
}

