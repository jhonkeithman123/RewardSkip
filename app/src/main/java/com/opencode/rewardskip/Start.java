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
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Start implements IXposedHookLoadPackage {
    private int hash;
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!load.isFirstApplication || !load.appInfo.processName.equals(load.processName)) {
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
    
    private synchronized void startHook(Context baseContext) {
        new GdtAdHook1(baseContext);
        new GdtAdHook2(baseContext);
        new FusionAdHook(baseContext);
        new TTAdHook(baseContext);
        new BaiduAdHook(baseContext);
        new SigmobAdHook(baseContext);
        new MintegralAdHook(baseContext);
        new UnityAdHook1(baseContext);
        new UnityAdHook2(baseContext);
        new KsAdHook(baseContext);
        new VungleAdHook(baseContext);
        new GoogleAdHook1(baseContext);
        new ApplovinAdHook(baseContext);
        new LevelPlayHook(baseContext);
    }
}
