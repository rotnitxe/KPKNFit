#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import subprocess
import time
from pathlib import Path

from PIL import Image

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5554"
OUT = Path(__file__).resolve().parent
PKG = "com.example.kpkn"

spec = importlib.util.spec_from_file_location("stuck", OUT / "_recapture_stuck.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def pull(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    return dest


def ready(path: Path) -> bool:
    im = Image.open(path).convert("RGB")
    w, h = im.size
    neon = 0
    n = 0
    for y in range(int(h * 0.25), int(h * 0.55), 6):
        for x in range(int(w * 0.2), int(w * 0.8), 6):
            r, g, b = im.getpixel((x, y))
            n += 1
            if (g > 150 and b > 150 and r < 120) or (b > 160 and r > 100 and g < 120) or (
                r > 180 and g > 80 and b < 80
            ):
                neon += 1
    return neon / max(n, 1) > 0.025


def dismiss():
    for y in (2190, 2140, 2240, 2189, 2100):
        if not ready(pull("_probe.png")):
            return True
        sh(f"input tap 540 {y}")
        time.sleep(2.2)
    return not ready(pull("_probe.png"))


def main():
    db = m.build("long", m.NEED["long"])
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.8)
    adb("push", str(db), "/data/local/tmp/kpkn-seed.db")
    sh(
        "run-as com.example.kpkn sh -c '"
        "cd databases && rm -f kpkn.db-wal kpkn.db-shm && "
        "cat /data/local/tmp/kpkn-seed.db > kpkn.db'"
    )
    sh(
        "am start -W -a android.intent.action.VIEW "
        "-d kpkn://workout/live-visual-prog/live-visual-sess "
        f"-n {PKG}/.MainActivity"
    )
    time.sleep(15)
    print("dismiss", dismiss(), flush=True)
    pull("20-long.png")
    pull("21-long-first-set.png")
    for _ in range(11):
        sh("input tap 980 720")
        time.sleep(0.4)
    pull("22-long-last-set.png")
    print(
        "sizes",
        (OUT / "20-long.png").stat().st_size,
        (OUT / "21-long-first-set.png").stat().st_size,
        (OUT / "22-long-last-set.png").stat().st_size,
        flush=True,
    )


if __name__ == "__main__":
    main()
