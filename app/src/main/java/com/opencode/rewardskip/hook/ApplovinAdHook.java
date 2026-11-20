package com.opencode.rewardskip.hook;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class ApplovinAdHook extends BaseHook {
    private Object listener, maxAd, defReward;
    public ApplovinAdHook(Context ctx) { super(ctx); }

    @Override
    protected void runHook() throws Throwable {
        claza = findClass("com.applovin.mediation.ads.MaxRewardedAd");
        if (claza == null) return;
        try {
            Class<?> rc = findClass("com.applovin.impl.mediation.MaxRewardImpl");
            defReward = rc.getDeclaredMethod("createDefault").invoke(null);
            try {
                java.lang.reflect.Field f = defReward.getClass().getDeclaredField("amount");
                f.setAccessible(true);
                f.setInt(defReward, 1);
            } catch (Throwable ignore) {}
        } catch (Throwable ignore) {
            return;
        }

        XposedBridge.hookAllMethods(claza, "setListener", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length == 1 && param.args[0] != null) {
                    listener = param.args[0];
                    if (isHooked(listener.getClass().getName() + "-ap")) return;
                    XposedBridge.hookAllMethods(listener.getClass(), "onAdLoaded", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            if (p.args.length >= 1 && p.args[0] != null) maxAd = p.args[0];
                        }
                    });
                }
            }
        });

        XposedBridge.hookAllMethods(claza, "showAd", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (listener == null || maxAd == null) return;
                if (delayedMode()) {
                    delayedFinish(listener, maxAd, defReward);
                } else {
                    // fire the full callback sequence in order — the chest reward
                    // logic needs onAdDisplayed before it credits the reward
                    callMethod(listener, "onAdDisplayed", maxAd);
                    callMethod(listener, "onRewardedVideoStarted", maxAd);
                    if (defReward != null) callMethod(listener, "onUserRewarded", maxAd, defReward);
                    callMethod(listener, "onRewardedVideoCompleted", maxAd);
                    callMethod(listener, "onAdHidden", maxAd);
                    param.setResult(null);
                }
            }
        });
    }

    @Override protected String targetPackageName() { return null; }
    @Override protected boolean isTarget() { return true; }
}
