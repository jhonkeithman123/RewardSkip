package com.opencode.rewardskip;

import android.content.Context;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Lightweight runtime diagnostics: reports what this module could actually hook. */
public final class SelfCheck {
    private static final String TAG = "RewardSkip";

    /** ad SDK name -> class that must exist in the target app for its hook to engage */
    private static final String[][] SDK_CLASSES = {
        {"AppLovin", "com.applovin.mediation.ads.MaxRewardedAd"},
        {"LevelPlay", "com.unity3d.mediation.rewarded.LevelPlayRewardedAd"},
        {"Mintegral", "com.mbridge.msdk.reward.player.MBRewardVideoActivity"},
        {"AdMob", "com.google.android.gms.ads.rewarded.RewardedAd"},
        {"Pangle", "com.bytedance.sdk.openadsdk.TTRewardVideoAd"},
        {"GDT", "com.qq.e.ads.rewardvideo.RewardVideoAD"},
        {"Kuaishou", "com.kwad.sdk.api.KsAdSDKImpl"},
        {"Baidu", "com.baidu.mobads.sdk.api.RewardVideoAd"},
        {"Sigmob", "com.sigmob.windad.rewardVideo.WindRewardVideoAd"},
        {"Vungle", "com.vungle.warren.Vungle"},
        {"UnityAds", "com.unity3d.ads.UnityAds"},
    };

    private SelfCheck() {}

    public static void report(Context ctx, int hooksLoaded, int hooksTotal) {
        StringBuilder present = new StringBuilder();
        for (String[] entry : SDK_CLASSES) {
            try {
                if (XposedHelpers.findClassIfExists(entry[1], ctx.getClassLoader()) != null) {
                    if (present.length() > 0) present.append(',');
                    present.append(entry[0]);
                }
            } catch (Throwable ignore) {
            }
        }
        XposedBridge.log(TAG + " self-check | pkg=" + ctx.getPackageName()
                + " | entry=" + BuildInfo.ENTRY
                + " | hooks=" + hooksLoaded + "/" + hooksTotal
                + " | mode=" + mode()
                + " | adSDKs=[" + present + "]");
    }

    private static String mode() {
        try {
            java.io.File f = new java.io.File("/data/local/tmp/skip_mode.txt");
            if (f.exists() && f.canRead()) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
                String s = r.readLine();
                r.close();
                if (s != null && s.trim().equals("delayed")) return "delayed";
            }
        } catch (Throwable ignore) {
        }
        return "instant";
    }

    /** Derived from this class's own package so it cannot drift on a rename. */
    public static final class BuildInfo {
        public static final String ENTRY = SelfCheck.class.getName().replace(".SelfCheck", ".Start");
    }
}
