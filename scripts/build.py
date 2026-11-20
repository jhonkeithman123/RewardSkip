#!/usr/bin/env python3
"""Build a signed RewardSkip Xposed-module APK from source.

No Gradle wrapper JAR is required: this script uses the Android SDK command
line tools directly (javac, d8, aapt2, zipalign, apksigner, keytool).

Usage:
    python scripts/build.py                 # build RewardSkip.apk in repo root
    python scripts/build.py --sdk /path/to/sdk
    python scripts/build.py --out custom.apk
"""
import argparse
import os
import pathlib
import shutil
import subprocess
import sys
import zipfile
import re

import doctor

ROOT = pathlib.Path(__file__).resolve().parent.parent


def sdk_dirs(explicit=None):
    if explicit:
        yield pathlib.Path(explicit)
        return
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            yield pathlib.Path(os.environ[var])
    home = pathlib.Path.home()
    yield home / "AppData" / "Local" / "Android" / "Sdk"       # Windows
    yield home / "Android" / "Sdk"                              # macOS / Linux


def find_sdk(explicit=None):
    for sdk in sdk_dirs(explicit):
        if (sdk / "platform-tools" / "adb.exe").exists() or (sdk / "platform-tools" / "adb").exists():
            return sdk
    raise SystemExit("Android SDK not found. Pass --sdk or set ANDROID_HOME.")


def newest(dirpath, pattern="*"):
    candidates = sorted(pathlib.Path(dirpath).glob(pattern), reverse=True)
    if not candidates:
        raise SystemExit(f"No entries found in {dirpath}")
    return candidates[0]


def exe(tool, sdk):
    suffixes = (".exe", ".bat", "") if os.name == "nt" else ("",)
    for bt in sorted((sdk / "build-tools").glob("*"), reverse=True):
        for suffix in suffixes:
            p = bt / (tool + suffix)
            if p.exists():
                return str(p)
    raise SystemExit(f"Missing {tool} in any build-tools directory")


def run(cmd, **kwargs):
    print("+", " ".join(str(c) for c in cmd), flush=True)
    subprocess.run([str(c) for c in cmd], check=True, **kwargs)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sdk", help="Android SDK root")
    ap.add_argument("--out", default=str(ROOT / "RewardSkip.apk"))
    ap.add_argument("--keystore", default=str(ROOT / "rewardskip.jks"))
    ap.add_argument("--storepass", default="rewardskip")
    ap.add_argument("--alias", default="rewardskip")
    args = ap.parse_args()

    # self-healing preflight: detect and auto-fix package / entry-point drift
    # (e.g. a stale assets/xposed_init that silently disables every hook)
    if not doctor.check(ROOT, fix=True):
        raise SystemExit("Preflight failed - refusing to build a broken APK")

    sdk = find_sdk(args.sdk)
    bt = newest(sdk / "build-tools")
    platforms = sorted((sdk / "platforms").glob("android-*"),
                       key=lambda p: int(p.name.split("-")[-1]), reverse=True)
    if not platforms:
        raise SystemExit("No Android platform installed")
    android_jar = platforms[0] / "android.jar"

    work = ROOT / "build"
    if work.exists():
        shutil.rmtree(work)
    for sub in ("stub_classes", "classes", "dex", "gen", "apk"):
        (work / sub).mkdir(parents=True, exist_ok=True)

    javac = "javac.exe" if os.name == "nt" else "javac"
    java = "java.exe" if os.name == "nt" else "java"
    keytool = "keytool.exe" if os.name == "nt" else "keytool"
    d8 = exe("d8", sdk)
    aapt2 = exe("aapt2", sdk)
    zipalign = exe("zipalign", sdk)
    apksigner = exe("apksigner", sdk)

    # 1. minimal Xposed API stubs -> compiled stubs (no Gradle dependency needed)
    stub_src = list((ROOT / "stubs").rglob("*.java"))
    run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-cp", android_jar, "-d", work / "stub_classes", *stub_src])

    # 2. module sources
    src = list((ROOT / "app" / "src" / "main" / "java").rglob("*.java"))
    run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-cp", f"{android_jar};{work / 'stub_classes'}" if os.name == "nt"
                else f"{android_jar}:{work / 'stub_classes'}",
         "-d", work / "classes", *src])

    # 3. classes -> DEX
    classes = list((work / "classes").rglob("*.class"))
    run([d8, "--lib", android_jar, "--lib", work / "stub_classes",
         "--output", work / "dex", *classes])

    # keep APK version in sync with app/build.gradle
    gradle_text = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    vc = re.search(r'versionCode\s+(\d+)', gradle_text)
    vn = re.search(r'versionName\s+"([^"]+)"', gradle_text)
    version_code = vc.group(1) if vc else "1000000"
    version_name = vn.group(1) if vn else "1.0.0"

    # 4. resources / manifest -> base APK
    res_flat = work / "apk" / "res.zip"
    run([aapt2, "compile", "--dir", ROOT / "app" / "src" / "main" / "res",
         "-o", res_flat])
    base = work / "apk" / "base.apk"
    run([aapt2, "link",
         "--manifest", ROOT / "app" / "src" / "main" / "AndroidManifest.xml",
         "-I", android_jar,
         "-R", res_flat,
         "--auto-add-overlay",
         "-A", ROOT / "app" / "src" / "main" / "assets",
         "--java", work / "gen",
         "-o", base,
         "--min-sdk-version", "21",
         "--target-sdk-version", "34",
         "--version-code", version_code,
         "--version-name", version_name])

    # 5. inject classes.dex
    with zipfile.ZipFile(base, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(work / "dex" / "classes.dex", "classes.dex")

    aligned = work / "apk" / "aligned.apk"
    run([zipalign, "-f", "-p", "4", base, aligned])

    # 6. signing key (generated locally, never committed)
    ks = pathlib.Path(args.keystore)
    if not ks.exists():
        run([keytool, "-genkeypair", "-alias", args.alias,
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-keystore", ks, "-storepass", args.storepass,
             "-keypass", args.storepass, "-dname", "CN=RewardSkip"])

    out = pathlib.Path(args.out)
    run([apksigner, "sign", "--ks", ks,
         "--ks-pass", f"pass:{args.storepass}",
         "--key-pass", f"pass:{args.storepass}",
         "--out", out, aligned])
    print(f"\nBuilt {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
