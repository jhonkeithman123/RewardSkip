package com.opencode.rewardskip;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import de.robv.android.xposed.XposedBridge;

/**
 * Shows a brief, styled toast when a scoped app is launched with RewardSkip
 * active, so the user can tell at a glance that the module is running in it.
 *
 * Configurable from SettingsActivity ("show_notice", default on). The hook reads
 * module preferences through XSharedPreferences when the framework supports it,
 * with a plain-file fallback for older frameworks.
 */
public final class LaunchNotice {
    private static final String PREFS_PACKAGE = "com.opencode.rewardskip";
    private static final String PREFS_NAME = "rewardskip";
    private static final String KEY_NOTICE = "show_notice";
    private static final String NOTICE_FILE = "/data/local/tmp/rewardskip_notice.txt";

    private LaunchNotice() {}

    /** Called once per target process, after all hooks have been installed. */
    public static void show(final Context ctx, final int hooksLoaded, final int hooksTotal) {
        if (ctx == null || ctx.getPackageName().equals(LaunchNotice.class.getPackage().getName())) return;
        if (!enabled()) return;

        final CharSequence label = appLabel(ctx);
        final String mode = delayedMode() ? "delayed ~5s" : "instant";

        try {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        TextView view = new TextView(ctx);
                        view.setText("RewardSkip active  •  " + label
                                + "  •  " + mode + "  •  hooks " + hooksLoaded + "/" + hooksTotal);
                        view.setTextColor(Color.parseColor("#F2F4FF"));
                        view.setTextSize(13);
                        view.setPadding(dp(ctx, 18), dp(ctx, 11), dp(ctx, 18), dp(ctx, 11));
                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(Color.parseColor("#1C2030"));
                        bg.setCornerRadius(dp(ctx, 20));
                        view.setBackground(bg);

                        Toast toast = Toast.makeText(ctx, "", Toast.LENGTH_LONG);
                        toast.setView(view);
                        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(ctx, 80));
                        toast.show();
                    } catch (Throwable t) {
                        log("notice view failed: " + t);
                    }
                }
            }, 1500);
        } catch (Throwable t) {
            log("notice post failed: " + t);
        }
    }

    private static CharSequence appLabel(Context ctx) {
        try {
            ApplicationInfo info = ctx.getApplicationInfo();
            CharSequence l = ctx.getPackageManager().getApplicationLabel(info);
            if (l != null && l.length() > 0) return l;
        } catch (Throwable ignore) {
        }
        return ctx.getPackageName();
    }

    /** true = show the notice (default), false = stay silent. */
    private static boolean enabled() {
        try {
            Class<?> cls = Class.forName("de.robv.android.xposed.XSharedPreferences");
            Object prefs = cls.getConstructor(String.class, String.class)
                    .newInstance(PREFS_PACKAGE, PREFS_NAME);
            boolean v = (Boolean) cls.getMethod("getBoolean", String.class, boolean.class)
                    .invoke(prefs, KEY_NOTICE, true);
            return v;
        } catch (Throwable ignore) {
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(NOTICE_FILE));
            String s = r.readLine();
            r.close();
            if (s != null) {
                String v = s.trim().toLowerCase();
                return !(v.equals("off") || v.equals("0") || v.equals("false"));
            }
        } catch (Throwable ignore) {
        }
        return true;
    }

    private static boolean delayedMode() {
        try {
            Class<?> cls = Class.forName("de.robv.android.xposed.XSharedPreferences");
            Object prefs = cls.getConstructor(String.class, String.class)
                    .newInstance(PREFS_PACKAGE, PREFS_NAME);
            String m = (String) cls.getMethod("getString", String.class, String.class)
                    .invoke(prefs, "mode", "instant");
            if (m != null) return m.trim().equals("delayed");
        } catch (Throwable ignore) {
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.FileReader("/data/local/tmp/skip_mode.txt"));
            String s = r.readLine();
            r.close();
            return s != null && s.trim().equals("delayed");
        } catch (Throwable ignore) {
        }
        return false;
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    private static void log(String message) {
        try {
            XposedBridge.log("RewardSkip: " + message);
        } catch (Throwable ignore) {
        }
    }
}
