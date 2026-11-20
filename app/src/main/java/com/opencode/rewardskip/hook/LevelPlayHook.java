package com.opencode.rewardskip.hook;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class LevelPlayHook extends BaseHook {
    private Object listener, adInfo;

    public LevelPlayHook(Context ctx) { super(ctx); }

    @Override
    protected void runHook() throws Throwable {
        claza = findClass("com.unity3d.mediation.rewarded.LevelPlayRewardedAd");
        if (claza == null) return;

        XposedBridge.hookAllMethods(claza, "setListener", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length == 1 && param.args[0] != null) {
                    listener = param.args[0];
                    if (!isHooked(listener.getClass().getName() + "-lp")) {
                        XposedBridge.hookAllMethods(listener.getClass(), "onAdLoaded", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                                if (p.args.length >= 1 && p.args[0] != null) adInfo = p.args[0];
                            }
                        });
                    }
                }
            }
        });

        XposedBridge.hookAllMethods(claza, "showAd", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (listener == null || adInfo == null) return;
                Object reward = null;
                try { reward = XposedHelpers.callMethod(param.thisObject, "getReward"); } catch (Throwable ignore) {}
                if (delayedMode()) {
                    delayedFinish(listener, adInfo, reward);
                } else {
                    callMethod(listener, "onAdDisplayed", adInfo);
                    if (reward != null) callMethod(listener, "onAdRewarded", reward);
                    callMethod(listener, "onAdClosed", adInfo);
                    param.setResult(null);
                }
            }
        });
    }

    @Override protected String targetPackageName() { return null; }
    @Override protected boolean isTarget() { return true; }
}
