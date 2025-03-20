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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PinwheelPlace implements IXposedHookLoadPackage, IXposedHookInitPackageResources {

    private static final String TAG = "Pinwheel Placer";
    private String sysUi = "com.android.systemui";
    private boolean failing = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        if (failing || !Objects.equals(param.packageName, sysUi)) return;
        log("Pinwheel went through the code hook!");

        final PinwheelBatteryDrawable[] batteryDrawable = new PinwheelBatteryDrawable[1];
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
                    batteryDrawable[0] = new PinwheelBatteryDrawable((Context) param.args[finalOrder ? 1:0], (int) param.args[finalOrder ? 0:1], ((Context) param.args[finalOrder ? 1:0]).getPackageManager().getResourcesForApplication(BuildConfig.APPLICATION_ID));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    batteryDrawable[0].colors = (int[]) getObjectField(param.thisObject, "colorLevels");
                    batteryDrawable[0].errorPaint = (Paint) getObjectField(param.thisObject, "errorPaint");
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "draw", Canvas.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    batteryDrawable[0].draw((Canvas) methodHookParam.args[0]);
                    return null;
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "getIntrinsicHeight", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    return batteryDrawable[0].getIntrinsicHeight();
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "getIntrinsicWidth", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    return batteryDrawable[0].getIntrinsicWidth();
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
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    batteryDrawable[0].onBoundsChange((Rect) methodHookParam.args[0]);
                    return null;
                }
            }));
            hooks.add(findAndHookMethod(batteryClass, "setColorFilter", ColorFilter.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    batteryDrawable[0].setColorFilter((ColorFilter) methodHookParam.args[0]);
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

                            batteryDrawable[0].setBatteryLevel(batteryLevel);
                            batteryDrawable[0].setCharging(charging);
                            /*if (batteryDrawable[0].animation) {
                                if (batteryDrawable[0].fillAnim.isRunning())
                                batteryDrawable[0].fillAnim.cancel();
                                else {
                                    batteryDrawable[0].fillAnim.setFloatValues(0, batteryLevel);
                                    batteryDrawable[0].fillAnim.setDuration(15L * batteryLevel);
                                    batteryDrawable[0].fillAnim.reverse();
                                }
                                if (batteryDrawable[0].rotatingState == STABLE || batteryDrawable[0].rotatingState == ENTER)
                                    batteryDrawable[0].rotatingState = EXIT;
                                else if (charging) {
                                    batteryDrawable[0].rotatingState = ENTER;
                                }
                            }*/
                            super.afterHookedMethod(param);
                        }
                    }));
            Class<?> viewController = findClass(sysUi + ".battery.BatteryMeterViewController", param.classLoader);
            hooks.add(hookMethod(viewController.getConstructors()[0], new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object stateCallback = getObjectField(param.thisObject, "mBatteryStateChangeCallback");
                    Method powerSaving = stateCallback.getClass().getMethod("onPowerSaveChanged", boolean.class);
                    hooks.add(hookMethod(powerSaving, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            batteryDrawable[0].setPowerSaveEnabled((boolean) param.args[0]);
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
                    batteryDrawable[0].setColors((int) param.args[0], (int) param.args[1], (int) param.args[2]);
                }
            }));
            hooks.add(findAndHookConstructor("com.android.settingslib.fuelgauge.BatteryStatus", param.classLoader,
                    Intent.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            int wattage = getIntField(param.thisObject, "maxChargingWattage");
                            batteryDrawable[0].setMicroWattage(wattage);
                        }
                    }));
        } catch (Exception e) {
            log("Pinwheel hook failed: " + e.getMessage() + ", " + e.getClass().getName());
            log("Pinwheel unhooks all methods and turns off...");
            for (XC_MethodHook.Unhook hook:
                 hooks) {
                hook.unhook();
            }
            failing = true;
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resParam) throws Throwable {
        if (failing || !resParam.packageName.equals(sysUi)) return;

        XModuleResources res = XModuleResources.createInstance("/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk", resParam.res);
        log("Pinwheel went through the resource hook!");
        resParam.res.setReplacement(sysUi, "dimen", "status_bar_battery_icon_width",
                res.fwd(resParam.res.getIdentifier("status_bar_battery_icon_height", "dimen", sysUi)));
        try {
            resParam.res.setReplacement(sysUi, "dimen", "status_bar_battery_unified_icon_width",
                    res.fwd(resParam.res.getIdentifier("status_bar_battery_unified_icon_height", "dimen", sysUi)));
        } catch (Exception e) {
            log("Pinwheel resource replacement failed: " + e.getMessage());
        }
    }
}

