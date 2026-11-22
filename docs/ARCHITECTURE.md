# RewardSkip architecture

This document explains what happens inside RewardSkip from the moment a game launches to the
moment a reward ad is skipped. It is aimed at contributors and at anyone who needs to debug a
game that does not work yet.

For step-by-step instructions see [USAGE.md](USAGE.md). For the config-file format see
[RECIPE_SCHEMA.md](RECIPE_SCHEMA.md).

---

## Design goals

1. **No app-specific code where avoidable.** Hooks detect SDK classes by name and silently do
   nothing when they are absent. One APK works in every scoped app.
2. **Fail open.** A broken or unsupported hook must never prevent the game from starting.
3. **Observable.** One log line per launch tells you exactly which hooks loaded and which ad
   SDKs were found.
4. **Adaptable without a rebuild.** New SDKs can be added through a JSON recipe on the device.

---

## Injection lifecycle

```
game process starts
  |
  v
LSPosed / Vector injects the module DEX
  |
  v
assets/xposed_init  ->  com.opencode.rewardskip.Start
  |
  v
Start.handleLoadPackage(loadPackageParam)
  |   isFirstApplication only
  |   target = appInfo.processName (fallback: packageName)
  |   skip if target != load.processName (avoids hooking child processes twice)
  |
  v
hook Application.attach(Context)
  |   guard with hashCode() so we install once per process
  |
  v
Start.startHook(baseContext)
  |   14 built-in hook constructors via safeHook(...)
  |   1 GenericHook (data-driven recipes)
  |   SelfCheck.report(...)
  |   LaunchNotice.show(...)
  |
  v
game calls ad SDK -> hooks fire -> callbacks faked -> original show() cancelled
```

`safeHook()` wraps every hook constructor in `try/catch`. Even if one throws, the others still
install and the count in the self-check tells you how many succeeded.

---

## The hook base class

Every built-in hook extends `hook/BaseHook.java`, which provides:

| Helper | Purpose |
|---|---|
| `findClass(name)` | `XposedHelpers.findClassIfExists(name, context.getClassLoader())` — safe class lookup |
| `isTarget()` | Whether this hook should run in the current package (most return `true`) |
| `callMethod(obj, name, args...)` | Reflective call that swallows errors — used to fire callbacks |
| `isHooked(name)` | Per-hook dedupe set so the same listener class is never hooked twice |
| `delayedMode()` | Reads `instant`/`delayed` from XSharedPreferences, then `/data/local/tmp/skip_mode.txt` |
| `delayedFinish(...)` | Fires the callback sequence after ~5 s instead of immediately |
| `getHandler()` | Main-looper handler for delayed callbacks |

Because class lookup uses the **target app's** classloader, a hook for an SDK that the game does
not bundle simply returns without error. That is why `hooks=15/15` can be reported in a game
that only uses one SDK — "loaded" means "installed without throwing", not "matched".

---

## Mode resolution

`BaseHook.delayedMode()` resolves in this order:

1. `XSharedPreferences("com.opencode.rewardskip", "rewardskip")` → key `mode`
2. `/data/local/tmp/skip_mode.txt` → first line (`instant` or `delayed`)
3. default `instant`

The gear-icon settings screen writes both the module preferences and the file, so the mode works
whether or not the framework exposes cross-process preferences.

---

## Built-in hook catalogue

| Hook class | SDK | Class hooked | Cancelled method | Callbacks fired |
|---|---|---|---|---|
| `GoogleAdHook1` | AdMob | `RewardedAd` | `show` | `onAdShowedFullScreenContent`, `onUserEarnedReward`, `onAdDismissedFullScreenContent` |
| `ApplovinAdHook` | AppLovin MAX | `MaxRewardedAd` | `showAd` | `onAdDisplayed`, `onRewardedVideoStarted`, `onUserRewarded`, `onRewardedVideoCompleted`, `onAdHidden` |
| `LevelPlayHook` | Unity LevelPlay | `LevelPlayRewardedAd` | `showAd` | `onAdDisplayed`, `onRewarded`, `onAdClosed` |
| `MintegralAdHook` | Mintegral | `MBRewardVideoActivity` | lifecycle | reward handlers |
| `UnityAdHook1/2` | Unity Ads | `UnityAds` | `show` | reward callbacks |
| `GdtAdHook1/2` | GDT | `RewardVideoAD` | `show` | reward callbacks |
| `TTAdHook` | Pangle | `TTRewardVideoAd` | `showAd` | reward callbacks |
| `KsAdHook` | Kuaishou | `KsRewardVideoAd` | `show` | reward callbacks |
| `BaiduAdHook` | Baidu | `RewardVideoAd` | `show` | reward callbacks |
| `SigmobAdHook` | Sigmob | `WindRewardVideoAd` | `show` | reward callbacks |
| `VungleAdHook` | Vungle | `Vungle` | `playAd` | reward callbacks |
| `FusionAdHook` / `BaiduAdHook` | Fusion / Baidu | various | various | reward callbacks |
| `GenericHook` | any | from config | from config | from config |

Hooks inherited from upstream (`SkipRewardAd`) are less actively maintained; the AdMob,
AppLovin, LevelPlay and generic hooks are the ones most recently exercised.

### AdMob flow (worked example)

UNO (`com.matteljv.uno`) is a real example. Its reward ads are served by AdMob with a Facebook
mediation adapter. The flow is:

```
RewardedAd.load(context, adUnitId, adRequest, loadCallback)
  -> loadCallback.onAdLoaded(rewardedAd)        [we capture the ad object]
  -> rewardedAd.setFullScreenContentCallback(cb) [we capture cb]
  -> rewardedAd.show(activity, rewardListener)
       [our hook fires cb.onAdShowedFullScreenContent(),
                     rewardListener.onUserEarnedReward(DEFAULT_REWARD),
                     cb.onAdDismissedFullScreenContent(),
        then cancels the real show()]
```

Two bugs were fixed in this path during development:

- the callback was only captured when it was `null` (so no callbacks ever fired);
- load callbacks were deduplicated by the *interface* name, so only the first callback class in
  a process was hooked.

Both are corrected in the current source. `DEFAULT_REWARD` is `RewardItem.DEFAULT_REWARD`
(amount 1, empty type), which most games accept.

---

## Generic recipe engine

`hook/GenericHook.java` reads `/data/local/tmp/rewardskip_generic.json` once at app launch.
Each recipe declares:

- `class` — the ad class whose `showMethod` will be cancelled;
- `loadClass` / `loadMethod` — optional source of the ad object;
- `listenerSetter` — optional method that reveals the callback object;
- `callbacks` — method names to invoke on `listener`, `ad`, or a `show()` argument;
- `rewardClass` + `rewardField` / `rewardMethod` — optional default reward object.

The JSON reader (`Json.java`) is a small recursive-descent parser with no external dependency.
Lines beginning with `//` are ignored so the config can be commented.

Callback firing uses `BaseHook.callMethod`, so a missing method logs a warning instead of
crashing the game. Recipes are additive to the built-ins — **do not enable a recipe for an SDK
that a built-in hook already handles**, or both will fire.

See [RECIPE_SCHEMA.md](RECIPE_SCHEMA.md) for the full field reference.

---

## UI and diagnostics

- **LaunchNotice** posts a styled `Toast` to the main looper ~1.5 s after `Application.attach`,
  showing the app label, mode and hook count. It skips RewardSkip's own settings screen and can
  be disabled in settings.
- **SelfCheck** logs one line per process:
  ```
  RewardSkip self-check | pkg=<pkg> | entry=com.opencode.rewardskip.Start |
  hooks=15/15 | mode=instant | adSDKs=[AppLovin,AdMob]
  ```
  `adSDKs` is derived from which known SDK classes exist in the app, independent of whether
  hooks installed, so it answers "is this game even supported?" at a glance.

---

## Build pipeline

`scripts/build.py` (and the `build.bat` / `build.sh` wrappers) do:

1. `doctor.check(..., fix=True)` — validates package, manifest, `xposed_init`, Java packages and
   Xposed meta-data; auto-fixes known drift; refuses to build on errors.
2. `javac` — compiles the Xposed API stubs, then the module sources.
3. `d8` — converts `.class` files into `classes.dex`.
4. `aapt2 compile/link` — builds the base APK from resources, manifest and assets.
5. `zipalign` — aligns the APK.
6. `apksigner` — signs with `rewardskip.jks` (generated locally on first build if missing).
7. optional `--install` — runs `adb install -r` on the connected device.

No Gradle wrapper JAR, no network access and no committed keystore are required.

---

## Limitations

- A locally faked callback cannot defeat **server-side reward validation**. If a game reports an
  ad session ID to its own backend (UNO's `MattelBISDK` does), the backend may reject the reward.
- `hooks=N/N` counts constructor successes, not SDK matches. Use the `adSDKs=[...]` list and the
  live callback logs to judge whether a hook actually engaged.
- Hooking every installed app raises detection risk and overhead. Scope stays a deliberate,
  per-app choice in LSPosed/Vector.
