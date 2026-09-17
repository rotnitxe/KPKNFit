#!/usr/bin/env python3
"""Capture clean isthmus P0 (S1 / S3 / last) + optional P1 on healthy emulator."""
from __future__ import annotations

import re
import shutil
import sqlite3
import json
import subprocess
import time
from pathlib import Path

from PIL import Image

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
OUT = Path(__file__).resolve().parent
SRC = OUT / "kpkn-pull.db"
PKG = "com.example.kpkn"
PROG = "live-visual-prog"
SESS = "live-visual-sess"


def detect_serial() -> str:
    r = subprocess.run([ADB, "devices"], capture_output=True, text=True, check=True)
    for line in r.stdout.splitlines():
        if line.startswith("emulator-") and line.endswith("\tdevice"):
            return line.split("\t")[0]
    raise SystemExit(f"No emulator device:\n{r.stdout}")


SERIAL = detect_serial()
print(f"SERIAL={SERIAL}", flush=True)


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def sets(n, p):
    return [{"id": f"{p}-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(n)]


def build_long() -> Path:
    exs = [{"id": "ex", "name": "Curl largo", "sets": sets(12, "ex")}]
    sess = {
        "id": SESS,
        "name": "DoD long isthmus",
        "dayOfWeek": 2,
        "assignedDays": [1, 2, 3, 4, 5, 6, 7],
        "exercises": exs,
    }
    prog = {
        "id": PROG,
        "name": "Live Visual DoD",
        "structure": "SIMPLE",
        "simpleProgramKind": "CYCLIC",
        "macrocycles": [
            {
                "id": "live-macro",
                "name": "M",
                "blocks": [
                    {
                        "id": "live-block",
                        "name": "B",
                        "mesocycles": [
                            {
                                "id": "live-meso",
                                "name": "Me",
                                "goal": "ACCUMULATION",
                                "weeks": [
                                    {
                                        "id": "live-visual-week",
                                        "name": "S1",
                                        "sessions": [sess],
                                        "executionKind": "TRAINING",
                                    }
                                ],
                            }
                        ],
                    }
                ],
            }
        ],
        "runState": {
            "runId": "live-run-1",
            "weekInstanceId": "live-visual-week",
            "status": "ACTIVE",
            "cycleNumber": 1,
        },
    }
    active = {
        "programId": PROG,
        "status": "ACTIVE",
        "currentMacrocycleIndex": 0,
        "currentBlockIndex": 0,
        "currentMesocycleIndex": 0,
        "currentWeekId": "live-visual-week",
        "currentMacrocycleId": "live-macro",
        "currentBlockId": "live-block",
        "currentMesocycleId": "live-meso",
        "currentWeekInstanceId": "live-visual-week",
        "currentCycleNumber": 1,
        "programRunId": "live-run-1",
    }
    settings = {
        "username": "Valen",
        "onboardingCompleted": True,
        "onboardingNameDone": True,
        "onboardingProgramDone": True,
        "onboardingNutritionDone": True,
        "hasSeenWelcome": True,
        "hasSeenHomeTour": True,
    }
    if not SRC.exists():
        raise SystemExit(f"Missing {SRC}")
    db = OUT / "kpkn-seed-long-isthmus.db"
    shutil.copyfile(SRC, db)
    c = sqlite3.connect(str(db))
    for t in ("programs", "active_program", "settings", "ongoing_workout"):
        c.execute(f"DELETE FROM {t}")
    c.execute(
        "INSERT INTO programs(id,name,data) VALUES (?,?,?)",
        (PROG, prog["name"], json.dumps(prog)),
    )
    c.execute("INSERT INTO active_program(rowId,data) VALUES (1,?)", (json.dumps(active),))
    c.execute("INSERT INTO settings(rowId,data) VALUES (1,?)", (json.dumps(settings),))
    c.commit()
    c.close()
    return db


def pull_shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    print(f"  saved {dest.name} size={dest.stat().st_size}", flush=True)
    return dest


def looks_like_readiness(path: Path) -> bool:
    im = Image.open(path).convert("RGB")
    w, h = im.size
    bright_gray = neonish = samples = 0
    for y in range(int(h * 0.28), int(h * 0.55), 8):
        for x in range(int(w * 0.25), int(w * 0.75), 8):
            r, g, b = im.getpixel((x, y))
            samples += 1
            if r > 180 and g > 180 and b > 180 and abs(r - g) < 25 and abs(g - b) < 25:
                bright_gray += 1
            if (g > 150 and b > 150 and r < 120) or (b > 160 and r > 100 and g < 120) or (
                r > 180 and g > 80 and b < 80
            ):
                neonish += 1
    gray_ratio = bright_gray / max(samples, 1)
    neon_ratio = neonish / max(samples, 1)
    print(f"  pixel gray={gray_ratio:.3f} neon={neon_ratio:.3f}", flush=True)
    if neon_ratio > 0.04 and gray_ratio < 0.08:
        return True
    if gray_ratio < 0.03 and neon_ratio > 0.02:
        return True
    return False


def looks_like_live(path: Path) -> bool:
    return not looks_like_readiness(path)


def dismiss_until_live(max_tries: int = 10) -> bool:
    taps = [
        (540, 2190),
        (540, 2140),
        (540, 2240),
        (539, 2189),
        (560, 2200),
        (540, 2100),
        (500, 2180),
    ]
    for i in range(max_tries):
        shot = pull_shot("_probe.png")
        if looks_like_live(shot):
            print(f"  live after {i} taps", flush=True)
            return True
        # also try white-button centroid
        im = Image.open(shot).convert("RGB")
        w, h = im.size
        cands = []
        for y in range(int(h * 0.78), int(h * 0.96)):
            for x in range(int(w * 0.35), int(w * 0.65)):
                r, g, b = im.getpixel((x, y))
                if r > 220 and g > 220 and b > 220:
                    cands.append((x, y))
        if cands:
            x = sum(c[0] for c in cands) // len(cands)
            y = sum(c[1] for c in cands) // len(cands)
        else:
            x, y = taps[i % len(taps)]
        print(f"  force tap {x},{y}", flush=True)
        sh(f"input tap {x} {y}")
        time.sleep(2.0)
    return looks_like_live(pull_shot("_probe.png"))


def fatals_anrs() -> tuple[int, int, str]:
    r = adb("logcat", "-d", "-v", "brief", check=False)
    text = r.stdout or ""
    fatals = len(re.findall(r"FATAL EXCEPTION", text))
    anrs = len(re.findall(r"ANR in|Application Not Responding", text))
    (OUT / "logcat-isthmus-p0.txt").write_text(text[-200000:], encoding="utf-8", errors="replace")
    return fatals, anrs, text


def open_long_live() -> bool:
    db = build_long()
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.8)
    adb("push", str(db), "/data/local/tmp/kpkn-seed.db")
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
    time.sleep(14)
    # dismiss permission dialogs if any
    for _ in range(3):
        sh("input keyevent 4", check=False)  # back — may help permission sheets
        time.sleep(0.4)
    ok = dismiss_until_live()
    print(f"  open live={ok}", flush=True)
    return ok


def swipe_next():
    # LTR content: swipe left to next set
    sh("input swipe 900 1100 220 1100 280")
    time.sleep(0.9)


def swipe_prev():
    sh("input swipe 220 1100 900 1100 280")
    time.sleep(0.9)


def main():
    summary = [f"serial={SERIAL}"]
    if not open_long_live():
        pull_shot("40-isthmus-s1-FAIL-readiness.png")
        summary.append("FAIL: could not dismiss readiness")
        (OUT / "isthmus-p0-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
        raise SystemExit(1)

    # Ensure first set: swipe prev a few times
    for _ in range(3):
        swipe_prev()
    time.sleep(0.5)
    pull_shot("40-isthmus-s1-clean.png")
    summary.append("captured 40-isthmus-s1-clean.png")

    # Move to S3 (index 2): two next swipes from S1
    swipe_next()
    swipe_next()
    time.sleep(0.6)
    pull_shot("41-isthmus-s3-clean.png")
    summary.append("captured 41-isthmus-s3-clean.png")

    # Advance toward last (12 sets → 9 more next from S3)
    for _ in range(10):
        swipe_next()
    time.sleep(0.8)
    pull_shot("42-isthmus-last-clean.png")
    summary.append("captured 42-isthmus-last-clean.png")
    # alias for long last DoD
    shutil.copyfile(OUT / "42-isthmus-last-clean.png", OUT / "43-long-last-set-clean.png")
    summary.append("copied 43-long-last-set-clean.png")

    # P1: adjacent snap — go back one then capture
    swipe_prev()
    time.sleep(0.5)
    pull_shot("44-adjacent-snap-clean.png")
    summary.append("captured 44-adjacent-snap-clean.png")

    # P1: continuity after + (tap left rail + area)
    sh("input tap 80 1680")
    time.sleep(1.4)
    if looks_like_readiness(pull_shot("_probe.png")):
        dismiss_until_live()
    pull_shot("45-continuity-after-plus-clean.png")
    summary.append("captured 45-continuity-after-plus-clean.png")

    # P1: vertical swipe blur attempt (header Haze)
    sh("input swipe 540 900 540 400 180")
    time.sleep(0.05)
    pull_shot("46-transition-blur-attempt.png")
    time.sleep(0.8)
    pull_shot("47-after-vertical-swipe.png")
    summary.append("captured blur attempt 46/47")

    fatals, anrs, _ = fatals_anrs()
    summary.append(f"fatals={fatals} anrs={anrs}")
    (OUT / "isthmus-p0-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(summary), flush=True)
    if fatals or anrs:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
