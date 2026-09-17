#!/usr/bin/env python3
"""Capture S1/S3/last assuming workout already opened (warm path)."""
from __future__ import annotations

import hashlib
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
S = "emulator-5554"
OUT = Path(__file__).resolve().parent


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", S, *a], check=check, capture_output=True, text=True)


def sh(c, check=True):
    return adb("shell", c, check=check)


def shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    return dest


def dump():
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / "_ui_tmp.xml"), check=False)
    try:
        return ET.parse(OUT / "_ui_tmp.xml").getroot()
    except Exception:
        return ET.Element("hierarchy")


def tap_cd(label: str) -> bool:
    for n in dump().iter("node"):
        if (n.attrib.get("content-desc") or "") == label or (n.attrib.get("text") or "") == label:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                print("tap", label, (x1 + x2) // 2, (y1 + y2) // 2, flush=True)
                sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
                time.sleep(1.2)
                return True
    return False


def gn(path: Path) -> tuple[float, float]:
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


def anr() -> bool:
    blob = " ".join((n.attrib.get("text") or "") for n in dump().iter("node"))
    return "isn't responding" in blob or "Close app" in blob


def pill(path: Path) -> tuple[int | None, int]:
    im = Image.open(path).convert("RGB")
    best = 0
    by = None
    for y in range(280, 1450, 2):
        bri = sum(1 for x in range(55, 120) if im.getpixel((x, y))[0] > 220)
        if bri > best:
            best = bri
            by = y
    return by, best


def main():
    adb("logcat", "-c", check=False)
    for i in range(14):
        if anr():
            print("ANR", flush=True)
            tap_cd("Wait") or sh("input tap 700 1280")
            time.sleep(4)
            continue
        p = shot("_probe.png")
        gr, nr = gn(p)
        print("p", i, gr, nr, flush=True)
        if gr > 0.4 and nr < 0.03:
            break
        if nr > 0.03:
            im = Image.open(p).convert("RGB")
            w, h = im.size
            cands = [
                (x, y)
                for y in range(int(h * 0.78), int(h * 0.96))
                for x in range(int(w * 0.38), int(w * 0.62))
                if im.getpixel((x, y))[0] > 220
            ]
            if cands:
                x = sum(a for a, _ in cands) // len(cands)
                y = sum(b for _, b in cands) // len(cands)
                sh(f"input tap {x} {y}")
                time.sleep(2.3)
            else:
                sh("input tap 540 2190")
                time.sleep(2.3)
        else:
            time.sleep(1)
    else:
        raise SystemExit("no live")

    time.sleep(2)
    p = shot("40-isthmus-s1-clean.png")
    print("S1", pill(p), gn(p), hashlib.md5(p.read_bytes()).hexdigest()[:8], flush=True)
    shutil.copyfile(p, OUT / "40-isthmus-s1-clean-KEEP.png")

    for _ in range(2):
        if anr():
            tap_cd("Wait")
            time.sleep(4)
        if not tap_cd("Siguiente"):
            sh("input tap 1005 655")
            time.sleep(1.4)
        time.sleep(1.3)
    time.sleep(1)
    p = shot("41-isthmus-s3-clean.png")
    print("S3", pill(p), gn(p), "anr", anr(), flush=True)
    py, _ = pill(p)
    if abs((py or 0) - 513) > 100 and not anr():
        sh("input tap 89 513")
        time.sleep(2)
        p = shot("41-isthmus-s3-clean.png")
        print("S3b", pill(p), gn(p), flush=True)

    if anr():
        tap_cd("Wait")
        time.sleep(4)
    sh("input tap 89 1368")
    time.sleep(2.2)
    p = shot("42-isthmus-last-clean.png")
    print("LAST", pill(p), gn(p), "anr", anr(), flush=True)
    shutil.copyfile(p, OUT / "43-long-last-set-clean.png")

    for name, exp in [
        ("40-isthmus-s1-clean.png", 321),
        ("41-isthmus-s3-clean.png", 513),
        ("42-isthmus-last-clean.png", 1368),
    ]:
        py, sc = pill(OUT / name)
        print(f"FINAL {name} pillY={py} exp={exp} d={None if py is None else py - exp}", flush=True)

    log = adb("logcat", "-d", "-v", "brief", check=False).stdout or ""
    (OUT / "logcat-isthmus-p0-final.txt").write_text(log[-200000:], encoding="utf-8", errors="replace")
    fatals = len(re.findall(r"FATAL EXCEPTION", log))
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", log))
    print(f"fatals={fatals} anrs={anrs}", flush=True)
    (OUT / "isthmus-p0-summary.txt").write_text(
        f"serial={S}\nfatals={fatals}\nanrs_pkg={anrs}\n", encoding="utf-8"
    )


if __name__ == "__main__":
    main()
