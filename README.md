# RewardSkip

A Xposed / LSPosed (Vector) module that skips rewarded video ads and still credits the reward.

> **This is a fork of [SkipRewardAd](https://github.com/LinYuLuo/SkipRewardAd) by LinYuLuo.**
> The upstream project is licensed under GPL-2.0. RewardSkip is a derivative work distributed
> under the same license. See [NOTICE](NOTICE) for attribution and a list of changes.

RewardSkip is useful on low-end devices where playing a full 30-second rewarded ad just to
collect a small in-game reward is a waste of time and battery.

See [CHANGELOG.md](CHANGELOG.md) for release notes.

---

## Features

- **Instant mode (default)** — cancels the ad and fires the full reward callback sequence
  immediately, so the reward is credited in milliseconds.
- **Delayed mode** — lets the real ad play for about 5 seconds, then fires the close/reward
  callbacks. Lower detection footprint, at the cost of a short wait.
- Hooks for multiple ad SDKs, including:
  - AppLovin MAX (`MaxRewardedAd`) — including the Unity `MaxUnityAdManager` bridge
  - Unity LevelPlay (`LevelPlayRewardedAd`)
  - Mintegral (`MBRewardVideoActivity` and reward handlers)
  - Google AdMob, GDT, Pangle/穿山甲, Kuaishou, Baidu, Sigmob, Unity Ads, Vungle, Fusion
- No UI, no network access, no permissions.
- Works with **Vector** (the LSPosed successor) as well as classic LSPosed / Xposed.

> Some hooks are inherited from upstream and are untested against current SDK versions —
> they are marked as such in the source. AppLovin MAX, Unity LevelPlay and Mintegral are the
> actively maintained ones.

---

## How it works

The module hooks `Application.attach()` and installs each ad-SDK hook into the target app's
classloader. When a rewarded ad is about to be shown, it fires the SDK's own success
callbacks (`onAdDisplayed`, `onRewardedVideoStarted`, `onUserRewarded`,
`onRewardedVideoCompleted`, `onAdHidden`, or the equivalent for the SDK) in the expected
order, so the game believes the ad was watched.

Firing `onAdDisplayed` is essential: some games (notably reward chests in Unity titles) only
grant the reward after they have seen the "ad displayed" event.

---

## Requirements

- Rooted Android device with Magisk
- Zygisk enabled and **Vector** (or LSPosed) installed and active
- Android 5.0 (API 21) or newer, arm64 or arm

---

## Installation

1. Install the `RewardSkip.apk` like a normal app.
2. Open Vector / LSPosed → **Modules** → enable **RewardSkip**.
3. In the module's **Scope** (or "Applications") list, select the games you want it to affect.
   Only selected apps are hooked.
4. Force-stop and reopen the games.

> After changing scope, Vector caches the selection in memory. A full app restart is usually
> enough; a reboot guarantees it.

---

## Mode configuration

RewardSkip reads a single world-readable file:

```
/data/local/tmp/skip_mode.txt
```

Contents:

| Value     | Behaviour                                              |
|-----------|--------------------------------------------------------|
| `instant` | (default / missing file) skip the ad and credit now    |
| `delayed` | let the ad play ~5 s, then fire the close/reward callbacks |

Example, from a root shell:

```sh
echo delayed > /data/local/tmp/skip_mode.txt
chmod 644 /data/local/tmp/skip_mode.txt
```

Restart the game after changing the mode. If no file exists, **instant** is used.

### Using RamGuard

If you also use [RamGuard](https://github.com/OPencode/RamGuard), its hidden notification
menu can switch this mode and manage RewardSkip's scope for you. RamGuard writes the private
config and mirrors it into the file above, so you never need to touch a shell.

---

## Building

### Option A — Gradle / Android Studio

Open the repository in Android Studio and build the `app` module, or run `./gradlew
:app:assembleRelease`. A Gradle wrapper properties file is included; generate the wrapper JAR
with `gradle wrapper` if it is missing.

### Option B — command line (no wrapper JAR needed)

`scripts/build.py` drives the Android SDK tools directly. It auto-detects the SDK from
`ANDROID_HOME`, `ANDROID_SDK_ROOT` or the default install location, generates a local signing
key, and produces a signed APK:

```sh
python scripts/build.py
# or
python scripts/build.py --sdk /path/to/android-sdk --out RewardSkip.apk
```

Requires: JDK (with `javac`/`keytool`) and Android SDK build-tools + platform installed.

---

## Project structure

```
app/src/main/java/com/opencode/rewardskip/
  Start.java                  entry point, installs hooks on Application.attach
  hook/BaseHook.java          shared helper; mode-file parsing and delayed callbacks
  hook/*Hook.java             one file per ad SDK
stubs/                        minimal Xposed API stubs used by scripts/build.py
scripts/build.py              standalone APK builder
```

---

## Troubleshooting

- **"No module activated"** — make sure Vector/LSPosed sees the app and that the module is
  enabled in its Modules tab.
- **Reward not granted** — the ad SDK may use a callback path this fork does not hook yet.
  Check logcat for the ad SDK's callback names and open an issue.
- **Nothing happens in delayed mode** — confirm `/data/local/tmp/skip_mode.txt` contains
  exactly `delayed` and is readable by the game process (`chmod 644`).

---

## Disclaimer

This project is for personal use and experimentation. Skipping ads may violate the terms of
service of the games or ad networks involved. Use it at your own risk. The authors are not
responsible for any account suspension, data loss or damage caused by its use.

---

## License

```
Copyright (C) 2026 RewardSkip contributors

This program is free software; you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.
```

Full text in [LICENSE](LICENSE). Upstream attribution in [NOTICE](NOTICE).
