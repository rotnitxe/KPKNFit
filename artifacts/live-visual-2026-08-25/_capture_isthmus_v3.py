#!/usr/bin/env python3
"""Isthmus P0 capture with positive live-card detection + permission dismiss."""
from __future__ import annotations

import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5554"
OUT = Path(__file__).resolve().parent
PKG = "com.example.kpkn"
PROG = "live-visual-prog"
SESS = "live-visual-sess"
SEED = OUT / "kpkn-seed-long-isthmus.db"


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def dump():
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / "_ui_tmp.xml"), check=False)
    try:
        return ET.parse(OUT / "_ui_tmp.xml").getroot()
    except Exception:
        return ET.Element("hierarchy")


def ui_blob(root=None) -> str:
    root = root or dump()
    return " | ".join(
        f"{n.attrib.get('text') or ''}|{n.attrib.get('content-desc') or ''}" for n in root.iter("node")
    )


def bounds_of(root, pred):
    for n in root.iter("node"):
        if pred(n):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
            if m:
                return tuple(map(int, m.groups()))
    return None


def center(b):
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    print(f"  saved {name} size={dest.stat().st_size}", flush=True)
    return dest


def focus_pkg() -> str:
    r = sh("dumpsys window", check=False)
    m = re.search(r"mCurrentFocus=Window\{[^ ]+ u0 ([^/}+]+)", r.stdout or "")
    return m.group(1) if m else ""


def pixel_stats(path: Path) -> tuple[float, float]:
    im = Image.open(path).convert("RGB")
    w, h = im.size
    neon = gray = n = 0
    for y in range(int(h * 0.28), int(h * 0.55), 8):
        for x in range(int(w * 0.25), int(w * 0.75), 8):
            r, g, b = im.getpixel((x, y))
            n += 1
            if r > 180 and g > 180 and b > 180 and abs(r - g) < 25 and abs(g - b) < 25:
                gray += 1
            if (g > 150 and b > 150 and r < 120) or (b > 160 and r > 100 and g < 120) or (
                r > 180 and g > 80 and b < 80
            ):
                neon += 1
    return gray / max(n, 1), neon / max(n, 1)


def is_readiness(path: Path, blob: str = "") -> bool:
    gr, nr = pixel_stats(path)
    print(f"  gray={gr:.3f} neon={nr:.3f}", flush=True)
    blob_l = blob.lower()
    if "preparado" in blob_l or "reporta tu estado" in blob_l:
        return True
    return nr > 0.035 and gr < 0.12


def is_live_card(path: Path, blob: str = "") -> bool:
    """Positive detection: light-gray card dominates mid-band while app focused."""
    if PKG not in focus_pkg() and "permissioncontroller" not in focus_pkg().lower():
        # allow brief permissioncontroller, but not launcher
        if focus_pkg() and PKG not in focus_pkg():
            print(f"  focus={focus_pkg()} (not app)", flush=True)
            return False
    gr, nr = pixel_stats(path)
    blob_l = blob.lower()
    if "series efectivas" in blob_l or "calidad técnica" in blob_l or "calidad tecnica" in blob_l:
        return True
    if "curl largo" in blob_l and gr > 0.25:
        return True
    # Live card: high bright-gray mid, low neon (not readiness rings)
    if gr > 0.35 and nr < 0.03:
        return True
    return False


def handle_anr() -> bool:
    root = dump()
    blob = ui_blob(root)
    if "isn't responding" not in blob and "Close app" not in blob and "no responde" not in blob.lower():
        return False
    print("  ANR", flush=True)
    for lab in ("Wait", "Esperar"):
        b = bounds_of(root, lambda n, lab=lab: (n.attrib.get("text") or "") == lab)
        if b:
            x, y = center(b)
            sh(f"input tap {x} {y}")
            time.sleep(4)
            return True
    sh("input tap 700 1280")
    time.sleep(4)
    return True


def handle_permissions() -> bool:
    root = dump()
    blob = ui_blob(root)
    if "location" not in blob.lower() and "ubicación" not in blob.lower() and "ubicacion" not in blob.lower():
        if "Allow" not in blob and "Permitir" not in blob and "While using" not in blob:
            return False
    print("  permission dialog", flush=True)
    for lab in (
        "While using the app",
        "Only this time",
        "Allow",
        "Permitir",
        "Durante el uso de la app",
        "Solo esta vez",
    ):
        b = bounds_of(root, lambda n, lab=lab: (n.attrib.get("text") or "") == lab)
        if b:
            x, y = center(b)
            print(f"  tap perm {lab} {x},{y}", flush=True)
            sh(f"input tap {x} {y}")
            time.sleep(1.5)
            return True
    # Don't allow as last resort to unblock
    b = bounds_of(root, lambda n: "Don" in (n.attrib.get("text") or "") and "allow" in (n.attrib.get("text") or "").lower())
    if b:
        x, y = center(b)
        sh(f"input tap {x} {y}")
        time.sleep(1.2)
        return True
    return False


def tap_readiness_check(path: Path):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    cands = []
    for y in range(int(h * 0.78), int(h * 0.96)):
        for x in range(int(w * 0.38), int(w * 0.62)):
            r, g, b = im.getpixel((x, y))
            if r > 220 and g > 220 and b > 220:
                cands.append((x, y))
    if cands:
        x = sum(c[0] for c in cands) // len(cands)
        y = sum(c[1] for c in cands) // len(cands)
    else:
        x, y = 540, 2190
    print(f"  readiness tap {x},{y}", flush=True)
    sh(f"input tap {x} {y}")
    time.sleep(2.3)


def reach_live(tries: int = 20) -> bool:
    for i in range(tries):
        handle_anr()
        handle_permissions()
        # re-start if we lost the app to launcher
        foc = focus_pkg()
        if foc and PKG not in foc and "permission" not in foc.lower():
            print(f"  lost focus ({foc}); relaunch", flush=True)
            sh(
                "am start -W -a android.intent.action.VIEW "
                f"-d kpkn://workout/{PROG}/{SESS} "
                f"-n {PKG}/.MainActivity"
            )
            time.sleep(8)
        p = shot("_probe.png")
        blob = ui_blob()
        if is_live_card(p, blob):
            print(f"LIVE try={i} focus={focus_pkg()}", flush=True)
            return True
        if is_readiness(p, blob):
            tap_readiness_check(p)
            continue
        print(f"  waiting UI try={i} focus={focus_pkg()}", flush=True)
        time.sleep(1.5)
    return is_live_card(shot("_probe.png"), ui_blob())


def open_session(wait: float):
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.5)
    adb("push", str(SEED), "/data/local/tmp/kpkn-seed.db")
    sh(
        "run-as com.example.kpkn sh -c '"
        "cd databases && rm -f kpkn.db-wal kpkn.db-shm && "
        "cat /data/local/tmp/kpkn-seed.db > kpkn.db'"
    )
    adb("logcat", "-c", check=False)
    sh(
        "am start -W -a android.intent.action.VIEW "
        f"-d kpkn://workout/{PROG}/{SESS} "
        f"-n {PKG}/.MainActivity"
    )
    time.sleep(wait)


def clean_capture(name: str) -> Path | None:
    for attempt in range(8):
        handle_anr()
        handle_permissions()
        sh("input keyevent 4", check=False)
        time.sleep(0.35)
        p = shot("_probe.png")
        blob = ui_blob()
        if is_live_card(p, blob) and not is_readiness(p, blob):
            dest = OUT / name
            shutil.copyfile(p, dest)
            print(f"  CLEAN {name} attempt={attempt}", flush=True)
            return dest
        if is_readiness(p, blob):
            tap_readiness_check(p)
        else:
            reach_live(6)
    print(f"  FAIL clean {name}", flush=True)
    shot(name)
    return None


def main():
    print("WARMUP", flush=True)
    open_session(24)
    reach_live(18)
    time.sleep(4)

    print("CAPTURE", flush=True)
    open_session(12)
    if not reach_live(20):
        shot("40-isthmus-s1-FAIL.png")
        raise SystemExit(1)

    clean_capture("40-isthmus-s1-clean.png")

    for _ in range(2):
        sh("input swipe 900 1000 220 1000 300")
        time.sleep(1.0)
        handle_anr()
    clean_capture("41-isthmus-s3-clean.png")

    for i in range(10):
        sh("input swipe 900 1000 220 1000 260")
        time.sleep(0.75)
        if i % 4 == 3:
            handle_anr()
    last = clean_capture("42-isthmus-last-clean.png")
    if last:
        shutil.copyfile(last, OUT / "43-long-last-set-clean.png")

    sh("input swipe 220 1000 900 1000 300")
    time.sleep(1.0)
    clean_capture("44-adjacent-snap-clean.png")

    sh("input tap 80 1650")
    time.sleep(1.5)
    reach_live(8)
    clean_capture("45-continuity-after-plus-clean.png")

    sh("input swipe 540 900 540 320 150")
    time.sleep(0.05)
    shot("46-transition-blur-attempt.png")
    time.sleep(0.8)
    clean_capture("47-after-vertical-swipe.png")

    log = adb("logcat", "-d", "-v", "brief", check=False).stdout or ""
    (OUT / "logcat-isthmus-p0-retry.txt").write_text(log[-250000:], encoding="utf-8", errors="replace")
    fatals = len(re.findall(r"FATAL EXCEPTION", log))
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", log))
    summary = [f"serial={SERIAL}", f"fatals={fatals}", f"anrs_pkg={anrs}"]
    (OUT / "isthmus-p0-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
