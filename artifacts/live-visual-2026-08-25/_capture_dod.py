#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5554"
OUT = Path(__file__).resolve().parent
SEED = OUT / "kpkn-seed.db"
PKG = "com.example.kpkn"
PROG = "live-visual-prog"
SESS = "live-visual-sess"


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run([ADB, "-s", SERIAL, *args], check=check, capture_output=True, text=True)


def sh(cmd: str, check: bool = True) -> subprocess.CompletedProcess:
    return adb("shell", cmd, check=check)


def pull(remote: str, name: str) -> Path:
    dest = OUT / name
    adb("pull", remote, str(dest))
    return dest


def dump_ui(name: str) -> Path:
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    return pull("/sdcard/ui.xml", name)


def texts(path: Path) -> list[str]:
    return re.findall(r'text="([^"]*)"', path.read_text(encoding="utf-8", errors="ignore"))


def shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    p = pull(f"/sdcard/{name}", name)
    print("shot", name, p.stat().st_size, flush=True)
    return p


def swipe(x1, y1, x2, y2, ms=350):
    sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")
    time.sleep(0.85)


def open_live():
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.6)
    adb("push", str(SEED), "/data/local/tmp/kpkn-seed.db")
    sh(
        "run-as com.example.kpkn sh -c '"
        "cd databases && rm -f kpkn.db-wal kpkn.db-shm && "
        "cat /data/local/tmp/kpkn-seed.db > kpkn.db'"
    )
    adb("logcat", "-c", check=False)
    # Deep link into live workout
    sh(
        "am start -W -a android.intent.action.VIEW "
        f"-d kpkn://workout/{PROG}/{SESS} "
        f"-n {PKG}/.MainActivity"
    )
    time.sleep(6.0)


def capture(tag: str):
    shot(f"20-{tag}.png")
    dump_ui(f"ui-tree-{tag}.xml")
    ts = texts(OUT / f"ui-tree-{tag}.xml")
    (OUT / f"texts-{tag}.txt").write_text("\n".join(ts), encoding="utf-8")
    print(tag, "=>", ts[:18], flush=True)
    return ts


def main():
    print("open live via deep link", flush=True)
    open_live()
    shot("00-home-seeded.png")
    entry = capture("live-entry")
    joined = " | ".join(entry)
    if "Allow" in joined:
        sh("input tap 540 1450")
        time.sleep(1.5)
        capture("live-entry")

    capture("short-or-first")
    swipe(900, 1100, 220, 1100)
    capture("adjacent-snap")

    targets = [
        ("normal", "Remo normal"),
        ("long", "Curl largo"),
        ("approx", "aproximaci"),
        ("mobility", "movilidad"),
        ("unilateral", "unilateral"),
        ("superseries", "banca SS"),
        ("cardio", "cardio"),
    ]
    for key, needle in targets:
        found = False
        for _ in range(14):
            path = dump_ui(f"ui-seek-{key}.xml")
            blob = " | ".join(texts(path)).lower()
            if needle.lower() in blob:
                found = True
                break
            swipe(540, 1450, 540, 520, 420)
        capture(key)
        print(f"  found={found}", flush=True)
        if key == "long":
            swipe(220, 1100, 900, 1100)
            capture("long-first-set")
            for _ in range(10):
                swipe(900, 1100, 220, 1100, 250)
            capture("long-last-set")
        if key == "normal":
            capture("normal-peek")

    swipe(540, 1550, 540, 420, 160)
    time.sleep(0.05)
    shot("30-transition-blur-attempt.png")
    time.sleep(1.0)
    shot("31-after-transition.png")
    capture("final")

    log = adb("logcat", "-d", "-v", "brief", "*:E", check=False)
    (OUT / "logcat-live-nav.txt").write_text(log.stdout or "", encoding="utf-8", errors="ignore")
    fatals = [
        ln
        for ln in (log.stdout or "").splitlines()
        if "FATAL EXCEPTION" in ln or "ANR in com.example.kpkn" in ln
    ]
    (OUT / "logcat-fatals-summary.txt").write_text(
        "\n".join(fatals) if fatals else "NO FATAL EXCEPTION / NO ANR",
        encoding="utf-8",
    )
    print("fatals:", fatals or "none", flush=True)
    print("DONE", flush=True)


if __name__ == "__main__":
    main()
