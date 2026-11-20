package com.opencode.rewardskip.hook;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MintegralAdHook extends BaseHook {
    private Object fakeReward, tempListener, tempParam;
    private XC_MethodHook setListenerHook, callbackHook;

    public MintegralAdHook(Context ctx) { super(ctx); }

    @Override
    protected void runHook() throws Throwable {
        claza = findClass("com.mbridge.msdk.reward.player.MBRewardVideoActivity");
        clazb = findClass("com.mbridge.msdk.out.RewardInfo");
        if (claza == null || clazb == null) return;

        try {
            fakeReward = XposedHelpers.newInstance(clazb, true, 0);
        } catch (Throwable t) {
            try { fakeReward = XposedHelpers.newInstance(clazb); } catch (Throwable ignore) {}
        }

        XposedHelpers.findAndHookMethod(claza, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (tempListener == null || tempParam == null) return;
                Runnable fire = new Runnable() {
                    @Override public void run() {
                        callMethod(tempListener, "onVideoComplete", tempParam);
                        callMethod(tempListener, "onAdClose", tempParam, fakeReward);
                        ((Activity) param.thisObject).finish();
                    }
                };
                if (delayedMode()) getHandler().postDelayed(fire, 5000);
                else fire.run();
            }
        });

        hook(findClass("com.mbridge.msdk.out.MBBidRewardVideoHandler"));
        hook(findClass("com.mbridge.msdk.out.MBRewardVideoHandler"));
    }

    private void hook(Class<?> clazz) {
        if (clazz == null) return;
        if (callbackHook == null) {
            callbackHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] != null) {
                        tempListener = param.thisObject;
                        tempParam = param.args[0];
                    }
                }
            };
        }
        if (setListenerHook == null) {
            setListenerHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length <= 0 || param.args[0] == null) return;
                    for (Object obj : param.args) {
                        if (obj != null && !isHooked(obj.getClass().getName())) {
                            XposedBridge.hookAllMethods(obj.getClass(), "onLoadSuccess", callbackHook);
                        }
                    }
                }
            };
        }
        XposedBridge.hookAllMethods(clazz, "setRewardVideoListener", setListenerHook);
    }

    @Override protected boolean isTarget() { return true; }
    @Override protected String targetPackageName() { return null; }
}
