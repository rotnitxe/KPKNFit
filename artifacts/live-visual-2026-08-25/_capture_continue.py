#!/usr/bin/env python3
"""Manual continuation: dismiss exit dialog, capture S1/S3/last without BACK key."""
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


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def dump():
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / "_ui_tmp.xml"), check=False)
    return ET.parse(OUT / "_ui_tmp.xml").getroot()


def blob() -> str:
    return " | ".join(
        f"{n.attrib.get('text') or ''}|{n.attrib.get('content-desc') or ''}" for n in dump().iter("node")
    )


def bounds(pred):
    for n in dump().iter("node"):
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
    print(f"saved {name} size={dest.stat().st_size}", flush=True)
    return dest


def tap_text(label: str) -> bool:
    b = bounds(lambda n: (n.attrib.get("text") or "") == label)
    if not b:
        return False
    x, y = center(b)
    print(f"tap {label} {x},{y}", flush=True)
    sh(f"input tap {x} {y}")
    time.sleep(1.2)
    return True


def gray_neon(path: Path) -> tuple[float, float]:
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


def dismiss_readiness_if_needed():
    p = shot("_probe.png")
    gr, nr = gray_neon(p)
    print(f"probe gray={gr:.3f} neon={nr:.3f}", flush=True)
    if not (nr > 0.035 and gr < 0.12):
        return
    im = Image.open(p).convert("RGB")
    w, h = im.size
    cands = [
        (x, y)
        for y in range(int(h * 0.78), int(h * 0.96))
        for x in range(int(w * 0.38), int(w * 0.62))
        if im.getpixel((x, y))[0] > 220
    ]
    if cands:
        x = sum(c[0] for c in cands) // len(cands)
        y = sum(c[1] for c in cands) // len(cands)
        sh(f"input tap {x} {y}")
        time.sleep(2.2)


def main():
    adb("logcat", "-c", check=False)
    b = blob()
    print("UI:", b[:400], flush=True)
    if "Continuar entrenando" in b or "deseas hacer" in b.lower():
        if not tap_text("Continuar entrenando"):
            tap_text("Continuar")
        time.sleep(1.0)

    dismiss_readiness_if_needed()

    # Ensure near first set
    for _ in range(4):
        sh("input swipe 220 1000 920 1000 280")
        time.sleep(0.55)
    time.sleep(0.8)
    shot("40-isthmus-s1-clean.png")

    for _ in range(2):
        sh("input swipe 920 1000 200 1000 280")
        time.sleep(0.95)
    time.sleep(0.7)
    shot("41-isthmus-s3-clean.png")

    for _ in range(9):
        sh("input swipe 920 1000 200 1000 250")
        time.sleep(0.65)
    time.sleep(0.9)
    shot("42-isthmus-last-clean.png")
    shutil.copyfile(OUT / "42-isthmus-last-clean.png", OUT / "43-long-last-set-clean.png")

    sh("input swipe 200 1000 920 1000 280")
    time.sleep(0.9)
    shot("44-adjacent-snap-clean.png")

    sh("input tap 80 1650")
    time.sleep(1.4)
    dismiss_readiness_if_needed()
    shot("45-continuity-after-plus-clean.png")

    sh("input swipe 540 900 540 300 140")
    time.sleep(0.05)
    shot("46-transition-blur-attempt.png")
    time.sleep(0.7)
    shot("47-after-vertical-swipe.png")

    log = adb("logcat", "-d", "-v", "brief", check=False).stdout or ""
    (OUT / "logcat-isthmus-p0-final.txt").write_text(log[-250000:], encoding="utf-8", errors="replace")
    fatals = len(re.findall(r"FATAL EXCEPTION", log))
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", log))
    summary = f"serial={SERIAL}\nfatals={fatals}\nanrs_pkg={anrs}\n"
    (OUT / "isthmus-p0-summary.txt").write_text(summary, encoding="utf-8")
    print("DONE\n" + summary, flush=True)


if __name__ == "__main__":
    main()
