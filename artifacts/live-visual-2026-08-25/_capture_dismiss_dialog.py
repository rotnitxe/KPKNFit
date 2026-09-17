#!/usr/bin/env python3
"""Find and tap Continuar entrenando via pixels; then capture isthmus P0."""
from __future__ import annotations

import re
import shutil
import subprocess
import time
from pathlib import Path

from PIL import Image

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5554"
OUT = Path(__file__).resolve().parent


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    print(f"saved {name} size={dest.stat().st_size}", flush=True)
    return dest


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


def find_light_dialog_button(path: Path) -> tuple[int, int] | None:
    """Find topmost wide light-gray horizontal button in mid dialog band."""
    im = Image.open(path).convert("RGB")
    w, h = im.size
    best = None
    best_score = 0
    # Dialog body roughly 0.35..0.75 height
    for y in range(int(h * 0.38), int(h * 0.72), 3):
        run = []
        for x in range(int(w * 0.18), int(w * 0.82)):
            r, g, b = im.getpixel((x, y))
            if r > 160 and g > 160 and b > 160 and abs(r - g) < 20 and abs(g - b) < 20:
                run.append(x)
            else:
                if len(run) > best_score and len(run) > int(w * 0.35):
                    best_score = len(run)
                    best = (sum(run) // len(run), y)
                run = []
        if len(run) > best_score and len(run) > int(w * 0.35):
            best_score = len(run)
            best = (sum(run) // len(run), y)
    return best


def dismiss_exit_dialog(max_tries: int = 8) -> bool:
    for i in range(max_tries):
        p = shot("_probe.png")
        gr, nr = gray_neon(p)
        print(f"  try={i} gray={gr:.3f} neon={nr:.3f}", flush=True)
        # Clean live: high gray mid card
        if gr > 0.45 and nr < 0.03:
            print("  already clean live", flush=True)
            return True
        btn = find_light_dialog_button(p)
        if not btn:
            print("  no light button found", flush=True)
            # fallback taps
            for y in (1080, 1140, 1200, 1260):
                sh(f"input tap 540 {y}")
                time.sleep(0.35)
            time.sleep(0.8)
            continue
        x, y = btn
        print(f"  tap light btn {x},{y}", flush=True)
        sh(f"input tap {x} {y}")
        time.sleep(1.5)
    p = shot("_probe.png")
    gr, nr = gray_neon(p)
    return gr > 0.45


def swipe_next():
    sh("input swipe 920 1000 200 1000 280")
    time.sleep(0.9)


def swipe_prev():
    sh("input swipe 200 1000 920 1000 280")
    time.sleep(0.9)


def main():
    adb("logcat", "-c", check=False)
    if not dismiss_exit_dialog():
        print("FAIL dismiss exit dialog", flush=True)
        shot("40-isthmus-s1-FAIL-dialog.png")
        raise SystemExit(1)

    # go to first
    for _ in range(5):
        swipe_prev()
    time.sleep(0.6)
    p = shot("40-isthmus-s1-clean.png")
    print("S1 gray/neon", gray_neon(p), flush=True)

    swipe_next()
    swipe_next()
    time.sleep(0.5)
    p = shot("41-isthmus-s3-clean.png")
    print("S3 gray/neon", gray_neon(p), flush=True)

    for _ in range(9):
        swipe_next()
    time.sleep(0.6)
    p = shot("42-isthmus-last-clean.png")
    print("LAST gray/neon", gray_neon(p), flush=True)
    shutil.copyfile(p, OUT / "43-long-last-set-clean.png")

    swipe_prev()
    shot("44-adjacent-snap-clean.png")

    sh("input tap 80 1650")
    time.sleep(1.3)
    # if readiness
    p = shot("_probe.png")
    gr, nr = gray_neon(p)
    if nr > 0.035 and gr < 0.12:
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
            time.sleep(2)
    # if exit dialog again
    dismiss_exit_dialog(4)
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
