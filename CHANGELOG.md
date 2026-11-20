# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-20

First release of **RewardSkip**, a renamed fork of LinYuLuo's SkipRewardAd.

### Added

- **Instant mode** (default) — cancels the rewarded ad and fires the full callback
  sequence (`onAdDisplayed`, `onRewardedVideoStarted`, `onUserRewarded`,
  `onRewardedVideoCompleted`, `onAdHidden`) so the reward is credited immediately.
- **Delayed mode** — lets the ad play for ~5 seconds before firing the close/reward
  callbacks. A lower-detection alternative for games that dislike instant skips.
- **AppLovin MAX hook** (`MaxRewardedAd`) including the Unity `MaxUnityAdManager`
  bridge used by many Unity games.
- **Unity LevelPlay hook** (`LevelPlayRewardedAd`).
- **Mintegral hook** (`MBRewardVideoActivity` + reward handlers).
- Mode selection through `/data/local/tmp/skip_mode.txt` (`instant` / `delayed`),
  documented as an integration point for external controllers such as RamGuard.
- Inherited upstream hooks for Google AdMob, GDT, Pangle, Kuaishou, Baidu, Sigmob,
  Unity Ads, Vungle and Fusion.
- Gear-icon settings activity shown in the framework's module list, with mode switching
  and a shortcut to the framework manager. Scope stays in the framework's Scope tab
  because it is stored in root-only framework data.
- `scripts/doctor.py` source doctor: validates package/entry-point consistency and can
  auto-fix the known files (`AndroidManifest.xml`, `app/build.gradle`, `assets/xposed_init`)
  with `--fix`. Warns about anything else instead of rewriting it. The build runs it as a
  preflight and refuses to emit an APK on errors; CI runs it in check-only mode.
- Runtime self-check line on injection reporting the target package, entry class, hook
  count, active mode and which ad SDKs were detected in the app.
- APK version is read from `app/build.gradle` instead of being duplicated in the build
  script.
- Standalone `scripts/build.py` builder (no Gradle wrapper JAR required) with
  SDK auto-detection and local signing-key generation.
- GitHub Actions workflow that builds and uploads the signed APK.

### Changed

- Renamed project from **SkipRewardAd** to **RewardSkip** and package from
  `cn.xylin.skiprewardad` to `com.opencode.rewardskip` everywhere.
- Rebranded app label, description, Gradle project name and translations.
- Bumped `compileSdk`/`targetSdk` from 32 to 34; version set to `1.0.0` (`1000000`).
- Added `NOTICE.md`-style attribution in `NOTICE` and a README fork notice, keeping
  the upstream GPL-2.0 license verbatim.
- Translated/cleaned inherited debug log strings and comments; removed a hook that
  forced debug logging on in production.

### Fixed

- Reward chests in some Unity games were not granted: the instant path now fires
  `onAdDisplayed` before the reward callback, which those games require.
- Removed the tightly-coupled fallback to RamGuard's private config directory from
  the mode reader; the module now has a single, documented mode interface.
- `assets/xposed_init` now points to `com.opencode.rewardskip.Start`; without this,
  Vector/LSPosed could load the APK but failed to find the renamed entry class,
  so no hooks were ever installed.
- Compatibility with older LSPosed (e.g. 1.9.2), which passes a null `appInfo` for
  `system_server`: the entry point now falls back to `packageName` instead of throwing
  before hooks are installed.

### Known limitations

- Hooks inherited from upstream are marked "untested against current ad SDK versions"
  in the source. AppLovin MAX, Unity LevelPlay and Mintegral are the maintained ones.
- Delayed mode credits the reward after the delay but does not force-close the real ad.

[1.0.0]: https://github.com/OPencode/RewardSkip/releases/tag/v1.0.0
