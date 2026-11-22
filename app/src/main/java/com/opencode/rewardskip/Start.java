package com.opencode.rewardskip;

import android.app.Application;
import android.content.Context;

import com.opencode.rewardskip.hook.ApplovinAdHook;
import com.opencode.rewardskip.hook.BaiduAdHook;
import com.opencode.rewardskip.hook.FusionAdHook;
import com.opencode.rewardskip.hook.GdtAdHook1;
import com.opencode.rewardskip.hook.GdtAdHook2;
import com.opencode.rewardskip.hook.GoogleAdHook1;
import com.opencode.rewardskip.hook.KsAdHook;
import com.opencode.rewardskip.hook.LevelPlayHook;
import com.opencode.rewardskip.hook.MintegralAdHook;
import com.opencode.rewardskip.hook.SigmobAdHook;
import com.opencode.rewardskip.hook.TTAdHook;
import com.opencode.rewardskip.hook.UnityAdHook1;
import com.opencode.rewardskip.hook.UnityAdHook2;
import com.opencode.rewardskip.hook.VungleAdHook;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Start implements IXposedHookLoadPackage {
    private int hash;
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!load.isFirstApplication) {
            return;
        }
        // Older LSPosed (e.g. 1.9.2) passes a null appInfo when injected into
        // system_server; Vector/API 102 always populates it. Fall back to
        // packageName instead of throwing before any hooks can be installed.
        String target = (load.appInfo != null && load.appInfo.processName != null)
                ? load.appInfo.processName : load.packageName;
        if (target == null || !target.equals(load.processName)) {
            return;
        }
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (hash == hashCode()) {
                    return;
                }
                hash = hashCode();
                startHook((Context) param.args[0]);
            }
        });
    }
    
    private int loadedHooks;

    private void safeHook(Runnable r, String name) {
        try {
            r.run();
            loadedHooks++;
        } catch (Throwable t) {
            XposedBridge.log("RewardSkip: hook " + name + " failed: " + t);
        }
    }

    private synchronized void startHook(final Context baseContext) {
        loadedHooks = 0;
        safeHook(() -> new GdtAdHook1(baseContext), "GdtAdHook1");
        safeHook(() -> new GdtAdHook2(baseContext), "GdtAdHook2");
        safeHook(() -> new FusionAdHook(baseContext), "FusionAdHook");
        safeHook(() -> new TTAdHook(baseContext), "TTAdHook");
        safeHook(() -> new BaiduAdHook(baseContext), "BaiduAdHook");
        safeHook(() -> new SigmobAdHook(baseContext), "SigmobAdHook");
        safeHook(() -> new MintegralAdHook(baseContext), "MintegralAdHook");
        safeHook(() -> new UnityAdHook1(baseContext), "UnityAdHook1");
        safeHook(() -> new UnityAdHook2(baseContext), "UnityAdHook2");
        safeHook(() -> new KsAdHook(baseContext), "KsAdHook");
        safeHook(() -> new VungleAdHook(baseContext), "VungleAdHook");
        safeHook(() -> new GoogleAdHook1(baseContext), "GoogleAdHook1");
        safeHook(() -> new ApplovinAdHook(baseContext), "ApplovinAdHook");
        safeHook(() -> new LevelPlayHook(baseContext), "LevelPlayHook");
        SelfCheck.report(baseContext, loadedHooks, 14);
        // Let the user know the module is active in this app/game.
        LaunchNotice.show(baseContext, loadedHooks, 14);
    }
}
