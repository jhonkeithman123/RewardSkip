package com.opencode.rewardskip.hook;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * NOTE: untested against current ad SDK versions
 */
public class FusionAdHook extends BaseHook {
    public FusionAdHook(Context ctx) {
        super(ctx);
    }
    @Override
    protected void runHook() throws Throwable {
        claza = findClass("com.leyou.fusionsdk.FusionAdSDK");
        clazb = findClass("com.leyou.fusionsdk.ads.rewardvideo.RewardVideoAdListener");
        if (claza == null || clazb == null) {
            return;
        }
        XposedBridge.hookAllMethods(claza, "loadRewardVideoAd", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (Object obj : param.args) {
                    if (!clazb.isInstance(obj)) {
                        continue;
                    }
                    callMethod(obj, "onReward", "");
                    log("Fusion - reward granted");
                    param.setResult(null);
                    return;
                }
            }
        });
    }
    
    @Override
    protected String targetPackageName() {
        return null;
    }
    
    @Override
    protected boolean isTarget() {
        return true;
    }
    
}
