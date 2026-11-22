# RewardSkip usage guide

A practical, end-to-end guide: install, configure, verify, and extend RewardSkip.

---

## 1. Requirements

- Rooted Android device with **Magisk** and **Zygisk** enabled
- **LSPosed** or its successor **Vector** installed and active
- Android 5.0 (API 21) or newer, arm or arm64
- The game you want to affect must be selected in the framework's scope (see step 3)

RewardSkip does not need network access, contacts, storage or any Android permission.

---

## 2. Install the module

1. Copy `RewardSkip.apk` to the device and install it like a normal app, or use the builder's
   install option:
   ```sh
   python scripts/build.py --install
   ```
2. Open **Vector / LSPosed → Modules**, find **RewardSkip**, and enable it.
3. Force-stop and reopen the game.

> After changing scope, Vector can cache the selection in memory. A full app restart is usually
> enough; rebooting guarantees it.

---

## 3. Set the scope

In **Vector / LSPosed → Modules → RewardSkip → Scope**, check every game you want hooked.
Only selected apps are injected. Scope cannot be changed from RewardSkip's own settings screen
because the framework stores it in root-only data.

If you use [RamGuard](https://github.com/OPencode/RamGuard), its hidden menu can manage
RewardSkip's scope for you.

---

## 4. Choose a skip mode

Two modes are available:

| Mode | Behaviour | When to use |
|---|---|---|
| `instant` (default) | Cancels the ad and fires all reward callbacks immediately | Speed; most games accept it |
| `delayed` | Lets the real ad play ~5 s, then fires the callbacks | Games that dislike instant skips |

### Option A — gear-icon settings (recommended)

Enable the module, then tap the **gear icon** next to RewardSkip in the framework's module list:

- **SKIP MODE** → `INSTANT` or `DELAYED (~5s)`
- **LAUNCH NOTICE** → `ON` or `OFF`
- **OPEN FRAMEWORK MANAGER** shortcut

### Option B — root shell

```sh
echo delayed > /data/local/tmp/skip_mode.txt
chmod 644 /data/local/tmp/skip_mode.txt
```

Restart the game after changing the mode. If the file is missing, `instant` is used.

---

## 5. Launch notice

By default, when a scoped app opens you will see a short toast:

```
RewardSkip active  •  UNO  •  instant  •  hooks 15/15
```

This confirms the module is actually running inside that app. Turn it off in the gear-icon
settings if you find it intrusive. The toast appears about 1.5 seconds after launch and does not
block the game.

---

## 6. Verify it works

### Quick check — self-check log

Open a scoped game, then check the framework log (Vector/LSPosed manager → Logs, or
`/data/adb/lspd/log/`):

```
RewardSkip self-check | pkg=com.matteljv.uno | entry=com.opencode.rewardskip.Start |
hooks=15/15 | mode=instant | adSDKs=[AppLovin,LevelPlay,Mintegral,AdMob,UnityAds]
```

- `hooks=15/15` — all hook constructors installed without throwing
- `adSDKs=[...]` — SDK classes actually found in that app
- If the list is empty, that game has no SDK RewardSkip recognises

### Definitive check — ad behaviour

Trigger a rewarded ad. With instant mode the ad should close in well under a second and the
reward should be credited. In logcat you will see the SDK's callbacks fired back-to-back, e.g.
for AdMob:

```
onAdShowedFullScreenContent
onUserEarnedReward
onAdDismissedFullScreenContent
```

If all three appear within a few milliseconds and no full-screen ad activity was launched, the
hook worked.

---

## 7. Add support for a new game

The built-in hooks cover AppLovin, AdMob, LevelPlay, Mintegral and several others. If a game uses
a different SDK or a changed callback API, add a recipe instead of editing Java.

### Step 1 — scan the game

```sh
python scripts/scan_target.py com.example.game --out recipe.json
```

The scanner pulls the installed APK and reports:

- detected ad SDKs (with the marker class)
- reward-ad classes from the game's own package
- relevant callback method names found in the DEX

It writes a JSON config with recipes, all **disabled** by default.

### Step 2 — review the recipe

Open `recipe.json`. For each recipe you want to try:

- set `"enabled": true`
- confirm `class` is the ad class whose `show`-type method starts the ad
- confirm `showMethod` (e.g. `show`, `showAd`, `playAd`)
- confirm `listenerSetter` reveals the callback object
- adjust the `callbacks` list: `method` names must match the SDK's real listener methods

Full field reference: [RECIPE_SCHEMA.md](RECIPE_SCHEMA.md).

### Step 3 — install and test

```sh
adb push recipe.json /data/local/tmp/rewardskip_generic.json
```

Force-stop and reopen the game. Watch the framework log for:

```
RewardSkip generic recipe '<name>' failed: ...
RewardSkip generic: callback <method> not found on <class>
```

Those messages tell you exactly which class or method name needs fixing. No APK rebuild needed.

> Recipes are **additive**. Do not enable one for an SDK already handled by a built-in hook, or
> callbacks may fire twice.

---

## 8. Build it yourself

### One-click wrappers

Windows:
```bat
build.bat
build.bat --install
build.bat --install --serial VKCMCUG6K7MFZD7P
```

Linux / macOS:
```sh
./build.sh
./build.sh --install
```

### Direct builder

```sh
python scripts/build.py                          # RewardSkip.apk in repo root
python scripts/build.py --sdk /path/to/Sdk       # custom SDK location
python scripts/build.py --out custom.apk         # custom output name
python scripts/build.py --install --serial X     # build and install on device X
```

Requirements: Python 3, a JDK (`javac`, `keytool`) and Android SDK build-tools + a platform.
The script auto-detects the SDK from `ANDROID_HOME`, `ANDROID_SDK_ROOT` or the default install
location. A signing key is generated locally on first build if `rewardskip.jks` is missing —
keep that file private and back it up, because APK updates must use the same key.

The build runs a self-check first (`scripts/doctor.py`) and refuses to emit an APK if the
package name, manifest, Java packages or `xposed_init` entry point disagree.

---

## 9. Troubleshooting

| Symptom | Likely cause | What to do |
|---|---|---|
| No toast on game launch | Module not enabled, game not in scope, or notice turned off | Check framework Modules/Scope and the LAUNCH NOTICE setting |
| `adSDKs=[]` in self-check | Game bundles a SDK RewardSkip does not know | Run the scanner and add a recipe |
| `hooks=15/15` but ad still plays | SDK callback path differs from the built-in hook | Check logcat callbacks; add/adjust a recipe |
| Ad skips but no coins | Game validates rewards server-side | Not fixable locally — see below |
| Delayed mode does nothing | Mode file missing/unreadable or not restarted | `chmod 644 /data/local/tmp/skip_mode.txt`, then restart game |
| Build fails on `xposed_init` | Drift after a rename | `python scripts/doctor.py --fix`, then rebuild |
| Install fails `SIGNATURE_MISMATCH` | Different signing key than the installed APK | Uninstall the old APK first, or reuse the original `rewardskip.jks` |

### Server-side reward validation

Some games report an ad session ID to their own backend. UNO, for example, logs `MattelBISDK`
events containing the AdMob `adsessionid`. Even if all local callbacks fire perfectly, the
backend can notice that the ad was never really watched and refuse to credit the reward. There is
no local hook that can fix that case — the reward is decided off-device.

---

## 10. Uninstall / roll back

1. Disable RewardSkip in Vector/LSPosed (or remove the target apps from its scope).
2. Force-stop the games so the injected DEX is unloaded.
3. Uninstall the RewardSkip APK.
4. Optional: remove `/data/local/tmp/skip_mode.txt` and
   `/data/local/tmp/rewardskip_generic.json`.

Deleting the module or clearing its scope immediately restores normal ad behaviour; no system
files are modified.

---

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md) — internals, hook lifecycle and callback flows
- [RECIPE_SCHEMA.md](RECIPE_SCHEMA.md) — every config field explained
- [FAQ.md](FAQ.md) — short answers to common questions
- [../CHANGELOG.md](../CHANGELOG.md) — release notes
