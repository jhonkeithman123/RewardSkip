package com.opencode.rewardskip.hook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class BaseHook {
    protected Context context;
    protected Class<?> claza, clazb;
    private StringBuffer buffer;
    private Handler handler;
    private Set<String> clsSet;
    
    public BaseHook(Context ctx) {
        context = ctx;
        try {
            if (isTarget()) {
                runHook();
            }
        } catch (Throwable e) {
            log(e.getMessage());
        }
    }
    
    protected abstract void runHook() throws Throwable;
    
    protected abstract String targetPackageName();
    
    protected boolean isTarget() {
        return targetPackageName().equals(context.getPackageName());
    }
    
    protected boolean isDebug() {
        return false;
    }
    
    protected final boolean isHooked(String clsName) {
        if (clsSet == null) {
            clsSet = Collections.newSetFromMap(new ConcurrentHashMap<>(6));
        } else if (clsSet.contains(clsName)) {
            return true;
        }
        clsSet.add(clsName);
        return false;
    }
    
    protected final boolean callMethod(Object target, String methodName, Object... params) {
        try {
            XposedHelpers.callMethod(target, methodName, params);
            return true;
        } catch (Throwable ignore) {
        }
        return false;
    }
    
    protected final void log(Object... params) {
        splitLog("", params);
    }
    
    protected final void splitLog(String splitStr, Object... params) {
        if (!isDebug()) {
            return;
        }
        if (buffer == null) {
            buffer = new StringBuffer();
        } else {
            buffer.setLength(0);
        }
        int index = 1;
        int count = params.length;
        for (Object param : params) {
            buffer.append(param);
            if (index >= count) {
                break;
            }
            buffer.append(splitStr);
            index++;
        }
        XposedBridge.log(buffer.toString());
    }
    
    protected final Handler getHandler() {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        return handler;
    }
    
    protected final Class<?> findClass(String name) {
        return XposedHelpers.findClassIfExists(name, context.getClassLoader());
    }

    /** true = play the ad ~5s then close it; false = skip instantly (default) */
    protected final boolean delayedMode() {
        String[] paths = {
            "/data/local/tmp/skip_mode.txt"
        };
        for (String p : paths) {
            try {
                java.io.File f = new java.io.File(p);
                if (f.exists() && f.canRead()) {
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
                    String s = r.readLine();
                    r.close();
                    return s != null && s.trim().equals("delayed");
                }
            } catch (Throwable ignore) {}
        }
        return false;
    }

    /** Fire the reward + close callbacks after a short delay, letting the real ad play */
    protected final void delayedFinish(final Object listener, final Object ad, final Object reward) {
        getHandler().postDelayed(new Runnable() {
            @Override public void run() {
                callMethod(listener, "onAdDisplayed", ad);
                callMethod(listener, "onRewardedVideoStarted", ad);
                if (reward != null) callMethod(listener, "onUserRewarded", ad, reward);
                callMethod(listener, "onRewardedVideoCompleted", ad);
                callMethod(listener, "onAdHidden", ad);
            }
        }, 5000);
    }
}
