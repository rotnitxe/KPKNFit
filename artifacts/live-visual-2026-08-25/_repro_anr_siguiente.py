#!/usr/bin/env python3
"""Reproduce ANR on Siguiente S1->S2->S3 with long isthmus seed."""
from __future__ import annotations

import re
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
    if neon / max(n, 1) > 0.035 and gray / max(n, 1) < 0.12:
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
    root = dump()
    blob = " | ".join(
        f"{n.attrib.get('text') or ''}|{n.attrib.get('content-desc') or ''}" for n in root.iter("node")
    )
    low = blob.lower()
    if "isn't responding" not in blob and "Close app" not in blob and "no responde" not in low:
        return False
    print("ANR_DIALOG", flush=True)
    for lab in ("Wait", "Esperar"):
        b = bounds_of(root, lambda n, lab=lab: (n.attrib.get("text") or "") == lab)
        if b:
            x, y = center(b)
            print(f"  wait tap {x},{y}", flush=True)
            sh(f"input tap {x} {y}")
            time.sleep(3)
            return True
    sh("input tap 700 1280")
    time.sleep(3)
    return True


def dismiss_until_live(tries: int = 14) -> bool:
    for i in range(tries):
        handle_anr()
        p = shot("_probe.png")
        if not readiness(p):
            print(f"LIVE try={i}", flush=True)
            return True
        print(f"readiness try={i}", flush=True)
        sh("input tap 540 2190")
        time.sleep(2.2)
    return False


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


def main():
    print("OPEN", flush=True)
    open_session(24)
    if not dismiss_until_live():
        shot("anr-repro-fail.png")
        raise SystemExit(1)
    sh("input keyevent 4", check=False)
    time.sleep(0.6)

    for step in range(2):
        handle_anr()
        root = dump()
        b = bounds_of(root, lambda n: (n.attrib.get("content-desc") or "") == "Siguiente")
        if not b:
            print(f"step{step}: swipe fallback", flush=True)
            sh("input swipe 900 1000 220 1000 280")
            time.sleep(1.5)
            continue
        x, y = center(b)
        print(f"step{step}: Siguiente {x},{y}", flush=True)
        t0 = time.time()
        sh(f"input tap {x} {y}")
        time.sleep(2.8)
        print(f"  elapsed={time.time() - t0:.1f}", flush=True)
        if handle_anr():
            print(f"  ANR after step{step}", flush=True)

    root = dump()
    texts = [(n.attrib.get("text") or "").strip() for n in root.iter("node")]
    interesting = [t for t in texts if t and re.search(r"Serie|S\d|/\d+|Curl|kg|Siguiente", t)]
    print("TEXTS:", interesting[:40], flush=True)
    shot("anr-repro.png")

    log = adb("logcat", "-d", "-v", "threadtime", check=False).stdout or ""
    (OUT / "logcat-anr-repro-full.txt").write_text(log[-300000:], encoding="utf-8", errors="replace")
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", log))
    skipped = re.findall(r"Skipped (\d+) frames", log)
    fatals = len(re.findall(r"FATAL EXCEPTION", log))
    print(f"SUMMARY anrs={anrs} fatals={fatals} skipped_frames={skipped}", flush=True)
    # Keep key lines
    keys = []
    for line in log.splitlines():
        if any(
            k in line
            for k in (
                "ANR in",
                "Input dispatching",
                "Skipped",
                "StrictMode",
                "Application Not Responding",
                "JIT",
                "compiling",
                "WorkoutV2",
            )
        ):
            keys.append(line)
    (OUT / "logcat-anr-repro.txt").write_text("\n".join(keys[-200:]) + "\n", encoding="utf-8")
    print("DONE", flush=True)


if __name__ == "__main__":
    main()
