#!/usr/bin/env python3
"""Scan an installed app's APK for ad SDKs and generate a RewardSkip recipe.

The scanner uses only adb + Python's stdlib. It reports which ad SDKs a game uses,
lists reward-ad classes from the game's own code, and writes a ready-to-review
recipe file for the config-driven GenericHook.

Usage:
    python scripts/scan_target.py com.matteljv.uno
    python scripts/scan_target.py com.matteljv.uno --serial VKCMCUG6K7MFZD7P
    python scripts/scan_target.py com.matteljv.uno --out myrecipe.json
"""
import argparse
import json
import pathlib
import re
import shutil
import subprocess
import tempfile
import zipfile

ADB_CANDIDATES = [
    pathlib.Path.home() / "AppData" / "Local" / "Android" / "Sdk" / "platform-tools" / "adb.exe",
    pathlib.Path.home() / "Android" / "Sdk" / "platform-tools" / "adb",
    "adb",
]

# marker -> (display name, ad class, load callback class, listener setter, show method, callbacks)
KNOWN = {
    "com/google/android/gms/ads/rewarded/RewardedAd": (
        "AdMob", "com.google.android.gms.ads.rewarded.RewardedAd",
        "com.google.android.gms.ads.rewarded.RewardedAdLoadCallback",
        "setFullScreenContentCallback", "show", [
            {"method": "onAdShowedFullScreenContent", "on": "listener"},
            {"method": "onUserEarnedReward", "on": "arg", "argIndex": 1, "arg": "reward"},
            {"method": "onAdDismissedFullScreenContent", "on": "listener"},
        ],
        "com.google.android.gms.ads.rewarded.RewardItem", "DEFAULT_REWARD"),
    "com/applovin/mediation/ads/MaxRewardedAd": (
        "AppLovin MAX", "com.applovin.mediation.ads.MaxRewardedAd",
        "", "setListener", "showAd", [
            {"method": "onAdDisplayed", "on": "listener"},
            {"method": "onUserRewarded", "on": "listener", "arg": "reward"},
            {"method": "onAdHidden", "on": "listener"},
        ], "com.applovin.impl.mediation.MaxRewardImpl", "createDefault"),
    "com/unity3d/mediation/rewarded/LevelPlayRewardedAd": (
        "Unity LevelPlay", "com.unity3d.mediation.rewarded.LevelPlayRewardedAd",
        "", "setAdListener", "showAd", [
            {"method": "onAdDisplayed", "on": "listener"},
            {"method": "onRewarded", "on": "listener"},
            {"method": "onAdClosed", "on": "listener"},
        ], "", ""),
    "com/mbridge/msdk/reward/player/MBRewardVideoActivity": (
        "Mintegral", "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
        "", "setRewardVideoAdListener", "show", [
            {"method": "onRewardedVideoAdShowSuccess", "on": "listener"},
            {"method": "onRewardedVideoCompleted", "on": "listener"},
            {"method": "onRewardedVideoAdClosed", "on": "listener"},
        ], "", ""),
    "com/unity3d/ads/UnityAds": (
        "Unity Ads", "com.unity3d.ads.UnityAds", "", "", "show", [], "", ""),
    "com/vungle/warren/Vungle": (
        "Vungle", "com.vungle.warren.Vungle", "", "", "playAd", [], "", ""),
    "com/bytedance/sdk/openadsdk/TTRewardVideoAd": (
        "Pangle", "com.bytedance.sdk.openadsdk.TTRewardVideoAd", "", "", "show", [], "", ""),
    "com/qq/e/ads/rewardvideo/RewardVideoAD": (
        "GDT", "com.qq.e.ads.rewardvideo.RewardVideoAD", "", "", "show", [], "", ""),
    "com/kwad/sdk/api/KsRewardVideoAd": (
        "Kuaishou", "com.kwad.sdk.api.KsRewardVideoAd", "", "", "show", [], "", ""),
    "com/baidu/mobads/sdk/api/RewardVideoAd": (
        "Baidu", "com.baidu.mobads.sdk.api.RewardVideoAd", "", "", "show", [], "", ""),
    "com/sigmob/windad/rewardVideo/WindRewardVideoAd": (
        "Sigmob", "com.sigmob.windad.rewardVideo.WindRewardVideoAd", "", "", "show", [], "", ""),
}

CALLBACK_WORDS = [
    "onAdLoaded", "onAdDisplayed", "onAdHidden", "onAdClicked",
    "onAdShowedFullScreenContent", "onAdDismissedFullScreenContent",
    "onUserEarnedReward", "onUserRewarded", "onRewarded", "onRewardedVideoCompleted",
    "onRewardedVideoAdClosed", "onAdClosed", "onAdRewarded", "onVideoCompleted",
    "setListener", "setAdListener", "setRewardedAdListener", "setFullScreenContentCallback",
    "show", "showAd", "load", "loadAd",
]

SKIP_PREFIXES = (
    "android/", "java/", "javax/", "kotlin/", "androidx/", "dalvik/", "org/apache/",
    "com/google/android/gms/", "com/applovin/", "com/unity3d/", "com/mbridge/",
    "com/bytedance/", "com/qq/e/", "com/kwad/", "com/baidu/", "com/sigmob/",
    "com/vungle/", "com/facebook/",
)


def find_adb(explicit):
    if explicit:
        return str(explicit)
    for c in ADB_CANDIDATES:
        if isinstance(c, pathlib.Path) and c.exists():
            return str(c)
        if c == "adb" and shutil.which("adb"):
            return "adb"
    raise SystemExit("adb not found. Pass --adb /path/to/adb")


def run(adb, args, serial=None):
    cmd = [adb]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    p = subprocess.run(cmd, capture_output=True, text=True)
    return p.stdout.strip(), p.returncode


def pull_apks(adb, serial, pkg, work):
    out, rc = run(adb, ["shell", "pm", "path", pkg], serial)
    if rc != 0 or "package:" not in out:
        raise SystemExit("Package not found on device: " + pkg)
    paths = [l.strip().replace("package:", "") for l in out.splitlines() if "package:" in l]
    local = []
    for i, path in enumerate(paths):
        dst = work / ("apk_%d.apk" % i)
        subprocess.run([adb] + (["-s", serial] if serial else []) +
                       ["pull", path, str(dst)], capture_output=True)
        if dst.exists():
            local.append(dst)
    return local


def dex_strings(zf, name):
    data = zf.read(name)
    # ASCII-ish strings from the MUTF-8 pool; good enough for class/method names
    return set(re.findall(r"[ -~]{4,}", data.decode("latin-1")))


def scan(apk_paths, pkg):
    all_strings = set()
    for apk in apk_paths:
        with zipfile.ZipFile(apk) as zf:
            for n in zf.namelist():
                if re.match(r"classes\d*\.dex$", n):
                    all_strings |= dex_strings(zf, n)

    detected = []
    for marker, info in KNOWN.items():
        if any(marker.replace("/", ".") in s or marker in s for s in all_strings):
            detected.append((info[0], marker, info))

    # candidate reward classes from the game's own package
    pkg_path = pkg.replace(".", "/")
    candidates = set()
    for s in all_strings:
        for m in re.finditer(r"L([A-Za-z0-9_/$]+);", s):
            cls = m.group(1)
            if not cls.startswith(pkg_path):
                continue
            if cls.startswith(SKIP_PREFIXES):
                continue
            low = cls.lower()
            if ("reward" in low or "advert" in low or low.endswith("/ad") or
                    "/ads/" in low or "admanager" in low):
                candidates.add(cls.replace("/", "."))

    methods = sorted({w for w in CALLBACK_WORDS if any(w in s for s in all_strings)})
    return detected, sorted(candidates), methods


def make_recipe(pkg, detected, candidates, methods):
    recipes = []
    for name, marker, info in detected:
        display, adclass, loadclass, setter, show, callbacks, rewardcls, rewardfield = info
        if not callbacks:  # built-in only, no invented callbacks
            continue
        r = {
            "//": "%s detected in %s (built-in hook already covers it; enable only to override)" % (name, pkg),
            "name": name + " / " + pkg,
            "enabled": False,
            "class": adclass,
            "loadClass": loadclass,
            "loadMethod": "onAdLoaded",
            "listenerSetter": setter,
            "showMethod": show,
            "callbacks": callbacks,
        }
        if rewardcls:
            r["rewardClass"] = rewardcls
            if rewardfield == "createDefault":
                r["rewardMethod"] = "createDefault"
            elif rewardfield:
                r["rewardField"] = rewardfield
        recipes.append(r)

    for cls in candidates[:12]:
        recipes.append({
            "//": "custom candidate from %s - review method names before enabling" % pkg,
            "name": pkg + " custom",
            "enabled": False,
            "class": cls,
            "loadClass": "",
            "loadMethod": "onAdLoaded",
            "listenerSetter": "setListener",
            "showMethod": "show",
            "callbacks": [
                {"method": "onAdDisplayed", "on": "listener"},
                {"method": "onRewarded", "on": "listener"},
                {"method": "onAdClosed", "on": "listener"},
            ],
        })

    return {"version": 1, "recipes": recipes}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("package", help="installed package name to scan")
    ap.add_argument("--serial", help="adb device serial")
    ap.add_argument("--adb", help="path to adb executable")
    ap.add_argument("--out", help="write generated config to this file")
    args = ap.parse_args()

    adb = find_adb(args.adb)
    work = pathlib.Path(tempfile.mkdtemp(prefix="rs_scan_"))
    try:
        apks = pull_apks(adb, args.serial, args.package, work)
        detected, candidates, methods = scan(apks, args.package)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print("Scan: %s" % args.package)
    print("\nDetected ad SDKs:")
    for name, marker, _ in detected:
        print("  - %s (%s)" % (name, marker))
    if not detected:
        print("  (none of the known SDK markers matched)")

    print("\nReward-ad classes in the app's own package:")
    for c in candidates:
        print("  - " + c)
    if not candidates:
        print("  (none found)")

    print("\nRelevant methods present in DEX:")
    print("  " + ", ".join(methods))

    config = make_recipe(args.package, detected, candidates, methods)
    text = json.dumps(config, indent=2, ensure_ascii=False)
    if args.out:
        pathlib.Path(args.out).write_text(text, encoding="utf-8")
        print("\nWrote %s" % args.out)
    else:
        print("\n--- generated config ---")
        print(text)
    print("\nInstall on device:")
    print("  adb push <config> /data/local/tmp/rewardskip_generic.json")
    print("Then set \"enabled\": true on the recipes you want and restart the game.")


if __name__ == "__main__":
    main()
