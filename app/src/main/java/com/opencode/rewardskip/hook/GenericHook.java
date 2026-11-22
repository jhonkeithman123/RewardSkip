package com.opencode.rewardskip.hook;

import android.content.Context;

import com.opencode.rewardskip.Json;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Data-driven reward-ad hook. Recipes live in /data/local/tmp/rewardskip_generic.json
 * so a new game/SDK can be supported by editing one config line - no rebuild needed.
 *
 * Recipe shape:
 * {
 *   "name": "my sdk",
 *   "class": "com.foo.ads.RewardAd",          // ad class whose show method is cancelled
 *   "loadClass": "com.foo.ads.AdLoadCallback",// optional, captures the ad object
 *   "loadMethod": "onAdLoaded",
 *   "listenerSetter": "setListener",          // optional, captures the callback object
 *   "showMethod": "show",
 *   "rewardClass": "com.foo.ads.Reward",      // optional static reward source
 *   "rewardField": "DEFAULT",                 // static field, or use rewardMethod below
 *   "rewardMethod": "createDefault",          // static no-arg factory method
 *   "callbacks": [
 *     {"method": "onRewarded", "on": "listener", "arg": "reward"},
 *     {"method": "onClosed", "on": "listener"}
 *   ]
 * }
 *
 * "on" values: listener (default) | ad | arg
 * "arg" values: reward | ad | (omitted = no arguments)
 * "argIndex" selects the show() argument when "on" is "arg" (default 0).
 */
public class GenericHook extends BaseHook {
    private static final String CONFIG = "/data/local/tmp/rewardskip_generic.json";

    public GenericHook(Context ctx) {
        super(ctx);
    }

    @Override
    protected String targetPackageName() {
        return null;
    }

    @Override
    protected boolean isTarget() {
        return true;
    }

    @Override
    protected void runHook() throws Throwable {
        String text = readAll(CONFIG);
        if (text == null || text.length() == 0) return;
        Map<String, Object> root = Json.asObj(Json.parse(text));
        if (root == null) return;
        List<Object> recipes = Json.asArr(root.get("recipes"));
        if (recipes == null) return;

        for (Object item : recipes) {
            Map<String, Object> recipe = Json.asObj(item);
            if (recipe == null) continue;
            if (Boolean.FALSE.equals(recipe.get("enabled"))) continue;
            try {
                install(recipe);
            } catch (Throwable t) {
                XposedBridge.log("RewardSkip generic recipe '"
                        + Json.str(recipe, "name", "?") + "' failed: " + t);
            }
        }
    }

    private void install(Map<String, Object> recipe) {
        final String adName = Json.str(recipe, "class", "");
        final String showMethod = Json.str(recipe, "showMethod", "show");
        if (adName.length() == 0) return;
        final Class<?> adCls = findClass(adName);
        if (adCls == null) return;

        final State st = new State();

        // Optional static reward object (e.g. RewardItem.DEFAULT_REWARD)
        String rewardClass = Json.str(recipe, "rewardClass", "");
        String rewardField = Json.str(recipe, "rewardField", "");
        if (rewardClass.length() > 0) {
            try {
                Class<?> rc = findClass(rewardClass);
                if (rc != null) {
                    if (rewardField.length() > 0) {
                        st.reward = rc.getDeclaredField(rewardField).get(null);
                    } else {
                        String rewardMethod = Json.str(recipe, "rewardMethod", "");
                        if (rewardMethod.length() > 0) {
                            st.reward = rc.getDeclaredMethod(rewardMethod).invoke(null);
                        }
                    }
                }
            } catch (Throwable ignore) {
            }
        }

        // Capture the ad object from a load callback
        String loadName = Json.str(recipe, "loadClass", "");
        String loadMethod = Json.str(recipe, "loadMethod", "onAdLoaded");
        if (loadName.length() > 0) {
            Class<?> loadCls = findClass(loadName);
            if (loadCls != null) {
                XposedBridge.hookAllMethods(loadCls, loadMethod, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args.length > 0 && p.args[0] != null) st.ad = p.args[0];
                    }
                });
            }
        }

        // Capture the full-screen / reward callback object
        String setter = Json.str(recipe, "listenerSetter", "");
        if (setter.length() > 0) {
            XposedBridge.hookAllMethods(adCls, setter, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] != null) st.listener = p.args[0];
                }
            });
        }

        final List<Object> callbacks = Json.asArr(recipe.get("callbacks"));
        if (callbacks == null || callbacks.size() == 0) return;

        XposedBridge.hookAllMethods(adCls, showMethod, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                for (Object cbItem : callbacks) {
                    Map<String, Object> cb = Json.asObj(cbItem);
                    if (cb == null) continue;
                    String method = Json.str(cb, "method", "");
                    if (method.length() == 0) continue;

                    Object target;
                    String on = Json.str(cb, "on", "listener");
                    if ("ad".equals(on)) target = st.ad;
                    else if ("arg".equals(on)) {
                        int idx = intOf(cb.get("argIndex"), 0);
                        target = (idx >= 0 && idx < p.args.length) ? p.args[idx] : null;
                    } else target = st.listener;
                    if (target == null) continue;

                    String arg = Json.str(cb, "arg", "");
                    boolean ok;
                    if ("reward".equals(arg)) ok = callMethod(target, method, st.reward);
                    else if ("ad".equals(arg)) ok = callMethod(target, method, st.ad);
                    else ok = callMethod(target, method);
                    if (!ok) {
                        XposedBridge.log("RewardSkip generic: callback " + method
                                + " not found on " + target.getClass().getName());
                    }
                }
                p.setResult(null);
            }
        });
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Long) return ((Long) o).intValue();
        if (o instanceof Double) return ((Double) o).intValue();
        return def;
    }

    private static final class State {
        Object ad;
        Object listener;
        Object reward;
    }

    private static String readAll(String path) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new FileReader(path));
            String line;
            while ((line = r.readLine()) != null) {
                // tolerate // comments in the config file
                if (line.trim().startsWith("//")) continue;
                sb.append(line).append('\n');
            }
            r.close();
            return sb.toString();
        } catch (Throwable ignore) {
            return null;
        }
    }
}
