#!/usr/bin/env python3
"""Tap rail nodes by a11y bounds for S1 / S3 / last; clean isthmus P0."""
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


def shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    print(f"saved {name} size={dest.stat().st_size}", flush=True)
    return dest


def dump():
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / "_ui_tmp.xml"), check=False)
    return ET.parse(OUT / "_ui_tmp.xml").getroot()


def parse_bounds(b: str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b or "")
    return tuple(map(int, m.groups())) if m else None


def rail_nodes() -> list[tuple[str, tuple[int, int, int, int]]]:
    """Return ordered (label, bounds) for Serie nodes on left rail."""
    root = dump()
    nodes = []
    for n in root.iter("node"):
        t = (n.attrib.get("text") or "").strip()
        d = (n.attrib.get("content-desc") or "").strip()
        b = parse_bounds(n.attrib.get("bounds", ""))
        if not b:
            continue
        # left rail only
        if b[2] > 200:
            continue
        label = t if re.fullmatch(r"S\d+", t) else None
        if not label and re.search(r"Serie S\d+", d):
            label = re.search(r"Serie (S\d+)", d).group(1)
        if label:
            nodes.append((label, b, d))
    # unique by y
    nodes.sort(key=lambda x: x[1][1])
    uniq = []
    for lab, b, d in nodes:
        if uniq and abs(uniq[-1][1][1] - b[1]) < 20:
            continue
        uniq.append((lab, b, d))
    print("rail:", [(l, b[1]) for l, b, _ in uniq], flush=True)
    return [(l, b) for l, b, _ in uniq]


def tap_bounds(b):
    x = (b[0] + b[2]) // 2
    y = (b[1] + b[3]) // 2
    print(f"  tap node {x},{y}", flush=True)
    sh(f"input tap {x} {y}")
    time.sleep(1.1)


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


def hide_ime():
    # Prefer IME done / tap outside card, never BACK (opens exit dialog)
    sh("input tap 540 200")  # header area
    time.sleep(0.4)
    # Also tap check on keyboard if present (bottom-right)
    sh("input tap 980 2250")
    time.sleep(0.5)


def dismiss_light_overlay_if_needed():
    p = shot("_probe.png")
    gr, nr = gray_neon(p)
    print(f"probe gray={gr:.3f} neon={nr:.3f}", flush=True)
    if gr > 0.45:
        return
    # exit dialog first light button
    im = Image.open(p).convert("RGB")
    w, h = im.size
    best = None
    best_score = 0
    for y in range(int(h * 0.38), int(h * 0.72), 3):
        run = []
        for x in range(int(w * 0.18), int(w * 0.82)):
            r, g, b = im.getpixel((x, y))
            if r > 160 and g > 160 and b > 160 and abs(r - g) < 20:
                run.append(x)
            else:
                if len(run) > best_score and len(run) > int(w * 0.35):
                    best_score = len(run)
                    best = (sum(run) // len(run), y)
                run = []
    if best:
        print(f"  overlay tap {best}", flush=True)
        sh(f"input tap {best[0]} {best[1]}")
        time.sleep(1.3)
    if nr > 0.035 and gr < 0.12:
        # readiness
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


def dock_progress() -> str | None:
    for n in dump().iter("node"):
        t = (n.attrib.get("text") or "").strip()
        m = re.fullmatch(r"(\d+)/(\d+)", t)
        if m:
            return t
    return None


def wait_anr_clear():
    root = dump()
    blob = " | ".join(
        f"{n.attrib.get('text') or ''}|{n.attrib.get('content-desc') or ''}" for n in root.iter("node")
    )
    if "isn't responding" in blob or "Close app" in blob:
        print("ANR present — Wait", flush=True)
        for lab in ("Wait", "Esperar"):
            for n in root.iter("node"):
                if (n.attrib.get("text") or "") == lab:
                    b = parse_bounds(n.attrib.get("bounds", ""))
                    if b:
                        sh(f"input tap {(b[0]+b[2])//2} {(b[1]+b[3])//2}")
                        time.sleep(4)
                        return
        sh("input tap 700 1280")
        time.sleep(4)


def capture_clean(name: str) -> Path:
    hide_ime()
    wait_anr_clear()
    dismiss_light_overlay_if_needed()
    time.sleep(0.5)
    p = shot(name)
    print(f"  {name} gray/neon={gray_neon(p)} dock={dock_progress()}", flush=True)
    return p


def main():
    adb("logcat", "-c", check=False)
    wait_anr_clear()
    dismiss_light_overlay_if_needed()
    nodes = rail_nodes()
    if len(nodes) < 3:
        raise SystemExit(f"rail too short: {nodes}")

    # S1 = first
    tap_bounds(nodes[0][1])
    capture_clean("40-isthmus-s1-clean.png")

    # S3 = third (index 2)
    nodes = rail_nodes()
    tap_bounds(nodes[2][1])
    capture_clean("41-isthmus-s3-clean.png")

    # last = last rail node
    nodes = rail_nodes()
    tap_bounds(nodes[-1][1])
    last = capture_clean("42-isthmus-last-clean.png")
    shutil.copyfile(last, OUT / "43-long-last-set-clean.png")

    # adjacent: previous node
    nodes = rail_nodes()
    if len(nodes) >= 2:
        tap_bounds(nodes[-2][1])
    capture_clean("44-adjacent-snap-clean.png")

    # +
    sh("input tap 80 1650")
    time.sleep(1.2)
    dismiss_light_overlay_if_needed()
    capture_clean("45-continuity-after-plus-clean.png")

    sh("input swipe 540 900 540 300 140")
    time.sleep(0.05)
    shot("46-transition-blur-attempt.png")
    time.sleep(0.7)
    capture_clean("47-after-vertical-swipe.png")

    log = adb("logcat", "-d", "-v", "brief", check=False).stdout or ""
    (OUT / "logcat-isthmus-p0-final.txt").write_text(log[-250000:], encoding="utf-8", errors="replace")
    fatals = len(re.findall(r"FATAL EXCEPTION", log))
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", log))
    summary = [
        f"serial={SERIAL}",
        f"fatals={fatals}",
        f"anrs_pkg={anrs}",
        f"final_dock={dock_progress()}",
    ]
    (OUT / "isthmus-p0-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
