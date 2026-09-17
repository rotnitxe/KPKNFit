#!/usr/bin/env python3
"""Open long session, dismiss ANR/readiness (text-safe), dump UI, capture S1/S3/last."""
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
    return ET.parse(OUT / "_ui_tmp.xml").getroot()


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


def readiness(path: Path) -> bool:
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
    gr, nr = gray / max(n, 1), neon / max(n, 1)
    print(f"  gray={gr:.3f} neon={nr:.3f}", flush=True)
    if nr > 0.035 and gr < 0.12:
        return True
    try:
        blob = " ".join(
            (n.attrib.get("text") or "") + " " + (n.attrib.get("content-desc") or "")
            for n in dump().iter("node")
        ).lower()
        if "preparado" in blob or "reporta tu estado" in blob:
            return True
    except Exception:
        pass
    return False


def handle_anr() -> bool:
    """Only dismiss when UI text confirms ANR — never pixel-guess (card is light)."""
    root = dump()
    blob = " | ".join(
        f"{n.attrib.get('text') or ''}|{n.attrib.get('content-desc') or ''}" for n in root.iter("node")
    )
    if "isn't responding" not in blob and "Close app" not in blob and "no responde" not in blob.lower():
        return False
    print("  ANR via UI text", flush=True)
    for lab in ("Wait", "Esperar"):
        b = bounds_of(root, lambda n, lab=lab: (n.attrib.get("text") or "") == lab)
        if b:
            x, y = center(b)
            print(f"  tap {lab} {x},{y}", flush=True)
            sh(f"input tap {x} {y}")
            time.sleep(4)
            return True
    sh("input tap 700 1280")
    time.sleep(4)
    return True


def dismiss_until_live(tries: int = 16) -> bool:
    for i in range(tries):
        handle_anr()
        p = shot("_probe.png")
        if not readiness(p):
            print(f"LIVE try={i}", flush=True)
            return True
        im = Image.open(p).convert("RGB")
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
        time.sleep(2.4)
    return not readiness(shot("_probe.png"))


def print_ui():
    root = dump()
    print("=== UI ===", flush=True)
    for n in root.iter("node"):
        t = (n.attrib.get("text") or "").strip()
        d = (n.attrib.get("content-desc") or "").strip()
        c = n.attrib.get("clickable")
        b = n.attrib.get("bounds")
        if t or d:
            print(f"c={c} T={t!r} D={d!r} {b}", flush=True)


def open_session(wait: float):
    if not SEED.exists():
        raise SystemExit(f"missing seed {SEED}")
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.6)
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


def clean_capture(name: str) -> Path:
    for _ in range(5):
        handle_anr()
        sh("input keyevent 4", check=False)  # back / hide IME
        time.sleep(0.4)
        p = shot("_probe.png")
        if readiness(p):
            dismiss_until_live(6)
            continue
        dest = OUT / name
        shutil.copyfile(p, dest)
        print(f"  CLEAN {name}", flush=True)
        return dest
    return shot(name)


def main():
    # Warm-up
    print("WARMUP", flush=True)
    open_session(26)
    dismiss_until_live(14)
    time.sleep(5)
    print_ui()

    print("CAPTURE", flush=True)
    open_session(14)
    if not dismiss_until_live(14):
        shot("40-isthmus-s1-FAIL.png")
        raise SystemExit(1)
    time.sleep(2)
    sh("input keyevent 4", check=False)
    time.sleep(0.5)
    print_ui()

    clean_capture("40-isthmus-s1-clean.png")

    # swipe to mid (S3 ≈ index 2): two left-swipes on pager mid-card
    for i in range(2):
        sh("input swipe 900 1000 220 1000 300")
        time.sleep(1.0)
        handle_anr()
        sh("input keyevent 4", check=False)
    time.sleep(0.8)
    clean_capture("41-isthmus-s3-clean.png")

    # to last: ~9 more swipes
    for i in range(10):
        sh("input swipe 900 1000 220 1000 260")
        time.sleep(0.7)
        if i % 4 == 3:
            handle_anr()
            sh("input keyevent 4", check=False)
    time.sleep(1.0)
    last = clean_capture("42-isthmus-last-clean.png")
    shutil.copyfile(last, OUT / "43-long-last-set-clean.png")

    # adjacent
    sh("input swipe 220 1000 900 1000 300")
    time.sleep(1.0)
    clean_capture("44-adjacent-snap-clean.png")

    # +
    sh("input tap 80 1650")
    time.sleep(1.5)
    dismiss_until_live(6)
    clean_capture("45-continuity-after-plus-clean.png")

    # blur
    sh("input swipe 540 900 540 320 150")
    time.sleep(0.05)
    shot("46-transition-blur-attempt.png")
    time.sleep(0.8)
    clean_capture("47-after-vertical-swipe.png")

    log = adb("logcat", "-d", "-v", "brief", check=False).stdout or ""
    (OUT / "logcat-isthmus-p0-retry.txt").write_text(log[-250000:], encoding="utf-8", errors="replace")
    fatals = len(re.findall(r"FATAL EXCEPTION", log))
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", log))
    summary = [
        f"serial={SERIAL}",
        f"fatals={fatals}",
        f"anrs_pkg={anrs}",
        "files=40,41,42,43,44,45,46,47",
    ]
    (OUT / "isthmus-p0-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
