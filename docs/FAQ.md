# RewardSkip FAQ

Short answers to the questions that come up most often. See [USAGE.md](USAGE.md) for full
instructions and [ARCHITECTURE.md](ARCHITECTURE.md) for internals.

---

## General

**What does RewardSkip do?**
It hooks rewarded-video ad APIs inside selected apps and fires the "ad was watched" callbacks
without actually playing the ad, so the in-game reward is credited immediately.

**Is this the original SkipRewardAd?**
No. RewardSkip is a renamed, cleaned fork of LinYuLuo's SkipRewardAd, with a new package
(`com.opencode.rewardskip`) and GPL-2.0-or-later license. See [NOTICE](../NOTICE).

**Does it need internet?**
No. The module makes no network requests. The scanner script only talks to your device via adb.

**Does it work on stock (unrooted) Android?**
No. It requires LSPosed/Vector, which requires root and Zygisk.

---

## Installation and scope

**I installed the APK but nothing happens.**
Enable the module in Vector/LSPosed, select the game in its **Scope**, then force-stop and
reopen the game. Installing alone does nothing.

**Do I have to add every game manually?**
Yes. Scope is a deliberate per-app choice. Bulk-injecting every installed app raises detection
risk and can break apps. RamGuard can manage scope for you if you use it.

**Why is there a gear icon?**
It opens RewardSkip's own settings (mode and launch notice). Scope itself stays in the
framework because it is stored in root-only data.

---

## Modes and behaviour

**What is the difference between instant and delayed?**
`instant` cancels the ad immediately; `delayed` lets the real ad play ~5 seconds before firing
the callbacks. Delayed is a lower-detection option for games that dislike instant skips.

**How do I change the mode?**
Use the gear-icon settings, or write `instant`/`delayed` to
`/data/local/tmp/skip_mode.txt` and restart the game.

**Why do I still see a toast saying "hooks 15/15"?**
That is the launch notice confirming the module is active in that app. Turn it OFF in the gear
settings if you do not want it.

**What does `hooks=15/15` mean?**
All 15 hook constructors installed without throwing. It does **not** mean all 15 SDKs were found
in that game — check the `adSDKs=[...]` part of the same log line for that.

---

## Compatibility

**The ad still plays. Why?**
Either the game uses an SDK/callback path not covered by the built-in hooks, or AdAway/a hosts
blocklist is preventing the ad from loading at all. Check the self-check line and logcat
callbacks, then scan the game and add a recipe.

**The ad is skipped but I get no coins.**
The game likely validates the reward on its own server. UNO does this (`MattelBISDK` reports the
AdMob `adsessionid`). No local hook can satisfy server-side validation.

**A game crashes after enabling RewardSkip.**
Disable the module for that game in scope. If you are testing a custom recipe, set `enabled:
false` in the config, force-stop the game and reopen it.

**Does it work with AdAway?**
Ads must be able to **load** for the hook to engage. If your hosts file blocks the ad domains,
nothing will be skipped. Disable the blocklist while testing.

---

## Building

**How do I build the APK myself?**
Run `build.bat` (Windows) or `./build.sh` (Linux/macOS), or `python scripts/build.py`. See
[USAGE.md](USAGE.md#8-build-it-yourself).

**What do I need installed?**
Python 3, a JDK (`javac`, `keytool`) and Android SDK build-tools plus a platform. No Gradle
wrapper JAR or network access is required.

**Why does the build refuse to run?**
The preflight doctor found inconsistent package names, a stale `xposed_init` entry, or a missing
Xposed meta-data tag. Run `python scripts/doctor.py --fix`, then rebuild.

**Install fails with `SIGNATURE_MISMATCH`.**
The installed APK was signed with a different key. Uninstall it first, or reuse the original
`rewardskip.jks`. Back up that keystore — losing it means you cannot do an in-place update later.

---

## Recipes

**Do I need a recipe for every game?**
No. The built-in hooks auto-detect supported SDKs. Only add a recipe when a game uses an SDK or
callback API that is not covered.

**How do I find out which SDK a game uses?**
```sh
python scripts/scan_target.py <package> --out recipe.json
```
It reports detected SDKs, reward-ad classes and callback method names, and generates recipes.

**Where do recipes go and when do they load?**
Push to `/data/local/tmp/rewardskip_generic.json`. They load once at app launch — restart the
game after editing.

**Can I break a game with a bad recipe?**
A bad class/method name is logged and ignored; it will not crash the game. But an incorrect
callback sequence can cause wrong rewards. Review generated recipes before enabling them, and
never enable overlapping recipes for the same SDK.

---

## Legal

**Is this allowed by the games?**
Skipping ads may violate a game's terms of service. Use it at your own risk; account suspension
is possible. See the disclaimer in the [README](../README.md#disclaimer).
