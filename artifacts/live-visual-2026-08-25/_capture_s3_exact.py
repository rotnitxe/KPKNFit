#!/usr/bin/env python3
"""Warm S3: settle live S1, wait JIT, tap rail ~S3 Y, capture clean."""
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
SEED = OUT / "kpkn-seed-long-isthmus.db"


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def dump():
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / "_ui_tmp.xml"), check=False)
    return ET.parse(OUT / "_ui_tmp.xml").getroot()


def shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    print(f"  {name} {dest.stat().st_size}", flush=True)
    return dest


def wait_anr_blind(n=4):
    for i in range(n):
        sh("input tap 540 1331")
        time.sleep(7)
        p = shot(f"_w{i}.png")
        if p.stat().st_size > 250000:
            print("recovered", flush=True)
            return


def to_live():
    for i in range(18):
        try:
            root = dump()
        except Exception:
            time.sleep(2)
            continue
        blob = " ".join(f"{n.attrib.get('text') or ''}|{n.attrib.get('content-desc') or ''}" for n in root.iter("node"))
        if "isn't responding" in blob:
            wait_anr_blind(3)
            continue
        low = blob.lower()
        if "preparado" in low or "reporta tu estado" in low:
            sh("input tap 900 620")
            time.sleep(0.6)
            sh("input tap 540 2190")
            time.sleep(2.2)
            continue
        if "siguiente" in low or "curl" in low:
            print(f"LIVE {i}", flush=True)
            return True
        time.sleep(1)
    return False


def pill_y(path: Path):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    best_y, best = None, 0
    for y in range(int(h * 0.12), int(h * 0.88), 2):
        score = 0
        for x in range(8, min(90, w)):
            r, g, b = im.getpixel((x, y))
            if r > 220 and g > 220 and b > 220:
                score += 1
        if score > best:
            best, best_y = score, y
    return best_y


def main():
    # Warm process
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.5)
    adb("push", str(SEED), "/data/local/tmp/kpkn-seed.db")
    sh("run-as com.example.kpkn sh -c 'cd databases && rm -f kpkn.db-wal kpkn.db-shm && cat /data/local/tmp/kpkn-seed.db > kpkn.db'")
    adb("logcat", "-c", check=False)
    sh("am start -W -a android.intent.action.VIEW -d kpkn://workout/live-visual-prog/live-visual-sess -n com.example.kpkn/.MainActivity")
    print("warm open 25s", flush=True)
    time.sleep(25)
    wait_anr_blind(3)
    to_live()
    print("JIT settle 20s", flush=True)
    time.sleep(20)

    # Soft relaunch without wipe: just view intent again keeps process warm
    adb("logcat", "-c", check=False)
    sh("am start -W -a android.intent.action.VIEW -d kpkn://workout/live-visual-prog/live-visual-sess -n com.example.kpkn/.MainActivity")
    time.sleep(8)
    wait_anr_blind(2)
    if not to_live():
        shot("44-isthmus-s3-FAIL.png")
        raise SystemExit(1)
    sh("input keyevent 4", check=False)
    time.sleep(1)

    # Tap rail at S3 band (~513 from prior notes); also try content-desc
    try:
        root = dump()
        for n in root.iter("node"):
            if (n.attrib.get("content-desc") or "") == "Serie 3":
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
                if m:
                    x = (int(m.group(1)) + int(m.group(3))) // 2
                    y = (int(m.group(2)) + int(m.group(4))) // 2
                    print(f"Serie3 a11y {x},{y}", flush=True)
                    sh(f"input tap {x} {y}")
                    break
        else:
            print("Serie3 coord tap 54,513", flush=True)
            sh("input tap 54 513")
    except Exception:
        sh("input tap 54 513")

    time.sleep(3)
    # If ANR, wait
    p = shot("_probe.png")
    if p.stat().st_size < 200000:
        wait_anr_blind(4)
        time.sleep(5)
        to_live()
        sh("input tap 54 513")
        time.sleep(3)
        p = shot("_probe.png")

    dest = OUT / "44-isthmus-s3-exact-clean.png"
    shutil.copyfile(p, dest)
    py = pill_y(dest)
    try:
        root = dump()
        texts = [(n.attrib.get("text") or "").strip() for n in root.iter("node") if (n.attrib.get("text") or "").strip()]
        interesting = [t for t in texts if re.search(r"Serie|/|Curl|kg|S\d", t)]
    except Exception:
        interesting = []
    log = adb("logcat", "-d", "-v", "brief", check=False).stdout or ""
    (OUT / "logcat-s3-exact.txt").write_text(log[-200000:], encoding="utf-8", errors="replace")
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", log))
    fatals = len(re.findall(r"FATAL EXCEPTION", log))
    skipped = [int(x) for x in re.findall(r"Skipped (\d+) frames", log)]
    summary = [f"pillY={py}", f"anrs={anrs}", f"fatals={fatals}", f"max_skip={max(skipped) if skipped else 0}", f"texts={interesting[:12]}"]
    (OUT / "s3-exact-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(summary), flush=True)
    # Strict: want pill near 513 and no ANR in window after warm relaunch ideally
    if anrs > 0:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
