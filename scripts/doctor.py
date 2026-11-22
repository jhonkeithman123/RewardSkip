#!/usr/bin/env python3
"""RewardSkip source doctor - detects package/entry-point drift and optionally fixes it.

The bug this exists to catch: the Java package and AndroidManifest get renamed, but
assets/xposed_init still points at the old entry class. Vector/LSPosed then loads the
module but throws ClassNotFoundException and silently installs no hooks.

Usage:
    python scripts/doctor.py            # check only
    python scripts/doctor.py --fix      # auto-fix known files, warn on anything else
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
GRADLE = ROOT / "app" / "build.gradle"
XPOSED_INIT = ROOT / "app" / "src" / "main" / "assets" / "xposed_init"
JAVA_ROOT = ROOT / "app" / "src" / "main" / "java"


class Report:
    def __init__(self):
        self.errors = []
        self.warnings = []
        self.fixes = []

    def ok(self, msg):
        print("  [ok]   " + msg)

    def warn(self, msg):
        self.warnings.append(msg)
        print("  [warn] " + msg)

    def error(self, msg):
        self.errors.append(msg)
        print("  [ERROR] " + msg)

    def fixed(self, msg):
        self.fixes.append(msg)
        print("  [fixed] " + msg)


def read(path):
    return path.read_text(encoding="utf-8", errors="surrogateescape") if path.exists() else None


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def canonical_package(rep):
    """applicationId in Gradle is the source of truth; manifest is a fallback."""
    gradle = read(GRADLE) or ""
    m = re.search(r'applicationId\s+"([^"]+)"', gradle)
    gradle_pkg = m.group(1) if m else None
    manifest = read(MANIFEST) or ""
    m = re.search(r'<manifest[^>]*?\bpackage="([^"]+)"', manifest, re.S)
    manifest_pkg = m.group(1) if m else None

    if gradle_pkg and manifest_pkg and gradle_pkg != manifest_pkg:
        rep.warn(f"package mismatch: Gradle={gradle_pkg} manifest={manifest_pkg}; using Gradle")
    pkg = gradle_pkg or manifest_pkg
    if not pkg:
        rep.error("cannot determine package: no applicationId in app/build.gradle and no package in manifest")
        return None
    return pkg


def check(root=ROOT, fix=False):
    global ROOT, MANIFEST, GRADLE, XPOSED_INIT, JAVA_ROOT
    ROOT = pathlib.Path(root)
    MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
    GRADLE = ROOT / "app" / "build.gradle"
    XPOSED_INIT = ROOT / "app" / "src" / "main" / "assets" / "xposed_init"
    JAVA_ROOT = ROOT / "app" / "src" / "main" / "java"

    rep = Report()
    print("RewardSkip doctor - checking " + str(ROOT))
    pkg = canonical_package(rep)
    if not pkg:
        return False
    rep.ok("canonical package: " + pkg)

    # --- required files -------------------------------------------------
    for p in (MANIFEST, GRADLE, XPOSED_INIT, ROOT / "LICENSE", ROOT / "NOTICE"):
        if not p.exists():
            rep.error("missing file: " + str(p.relative_to(ROOT)))

    # --- manifest package -----------------------------------------------
    manifest = read(MANIFEST) or ""
    m = re.search(r'(<manifest[^>]*?\bpackage=")([^"]*)(")', manifest, re.S)
    if m and m.group(2) != pkg:
        if fix:
            write(MANIFEST, manifest[:m.start(2)] + pkg + manifest[m.end(2):])
            rep.fixed(f"manifest package -> {pkg}")
        else:
            rep.error(f"manifest package is {m.group(2)}, expected {pkg}")
    elif m:
        rep.ok("manifest package matches")

    # --- xposed_init entry point ----------------------------------------
    init = (read(XPOSED_INIT) or "").strip()
    if not init:
        rep.error("assets/xposed_init is empty")
    else:
        entry_simple = init.rsplit(".", 1)[-1]
        expected = pkg + "." + entry_simple
        entry_file = JAVA_ROOT / pathlib.Path(*pkg.split(".")) / (entry_simple + ".java")
        if init != expected or not entry_file.exists():
            if fix:
                write(XPOSED_INIT, expected + "\n")
                rep.fixed(f"xposed_init -> {expected}")
            else:
                rep.error(f"xposed_init points to {init}, expected {expected}")
        else:
            rep.ok("xposed_init entry class: " + init)
            src = read(entry_file) or ""
            if "IXposedHookLoadPackage" not in src:
                rep.error(f"{entry_simple} does not implement IXposedHookLoadPackage")

    # --- Java package declarations --------------------------------------
    java_files = list(JAVA_ROOT.rglob("*.java"))
    if not java_files:
        rep.error("no Java sources found")
    allowed = {pkg, pkg + ".hook"}
    drifted = []
    for f in java_files:
        text = read(f) or ""
        m = re.search(r'^\s*package\s+([\w.]+)\s*;', text, re.M)
        if not m:
            rep.warn("no package declaration in " + str(f.relative_to(ROOT)))
        elif m.group(1) not in allowed:
            drifted.append((f, m.group(1)))
    if drifted:
        for f, old in drifted:
            rep.error(f"{f.relative_to(ROOT)} declares package {old}")
        rep.warn("Java package drift requires a manual rename of package/import statements")
    else:
        rep.ok(f"Java packages consistent across {len(java_files)} files")

    # --- stale package references in text sources -----------------------
    stale = []
    patterns = ["cn.xylin.skiprewardad", "SkipRewardAd"]
    for f in ROOT.rglob("*"):
        if not f.is_file() or f.suffix.lower() in {".apk", ".idsig", ".jks", ".class", ".dex", ".png", ".zip"}:
            continue
        if (".git" in f.parts or "build" in f.parts or "__pycache__" in f.parts
                or f.name == "doctor.py"):
            continue
        text = read(f) or ""
        # A fork must keep naming the upstream project in attribution, links and
        # "inherited from upstream" notes. Only flag bare references elsewhere.
        attribution = ("fork of", "upstream", "http://", "https://",
                       "attribution", "inherited", "derivative", "licensed",
                       "original", "based on")
        for pat in patterns:
            if f.name in ("CHANGELOG.md", "NOTICE"):
                continue
            for line in text.splitlines():
                if pat not in line:
                    continue
                if any(k in line.lower() for k in attribution):
                    continue
                stale.append((f, pat))
                break
    for f, pat in stale:
        rep.warn(f"stale reference {pat!r} in {f.relative_to(ROOT)}")

    # --- Xposed metadata -------------------------------------------------
    for key in ("xposedmodule", "xposeddescription", "xposedminversion"):
        if key not in manifest:
            rep.error("manifest missing Xposed meta-data: " + key)
    if rep.ok and "xposedmodule" in manifest:
        rep.ok("Xposed meta-data present")

    print("")
    if rep.errors:
        print(f"FAILED: {len(rep.errors)} error(s), {len(rep.warnings)} warning(s)")
        return False
    print(f"OK: no errors ({len(rep.warnings)} warning(s), {len(rep.fixes)} fix(es))")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fix", action="store_true", help="auto-fix known files")
    ap.add_argument("--root", default=str(ROOT), help="repository root")
    args = ap.parse_args()
    sys.exit(0 if check(args.root, fix=args.fix) else 1)


if __name__ == "__main__":
    main()
