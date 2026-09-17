#!/usr/bin/env python3
"""Force-dismiss readiness by tap + pixel verify live card."""
from __future__ import annotations

import json
import re
import shutil
import sqlite3
import subprocess
import time
from pathlib import Path

from PIL import Image

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5554"
OUT = Path(__file__).resolve().parent
SRC = OUT / "kpkn-pull.db"
PKG = "com.example.kpkn"
PROG = "live-visual-prog"
SESS = "live-visual-sess"


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def sets(n, p):
    return [{"id": f"{p}-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(n)]


VARIANTS = {
    "superseries": [
        {
            "id": "ex-a",
            "name": "Press banca SS",
            "sets": sets(3, "exa"),
            "supersetId": "ss1",
            "supersetGroupRef": "ss1",
        },
        {
            "id": "ex-b",
            "name": "Remo SS",
            "sets": sets(3, "exb"),
            "supersetId": "ss1",
            "supersetGroupRef": "ss1",
        },
    ],
    "normal": [{"id": "ex", "name": "Remo normal", "sets": sets(5, "ex")}],
    "short": [{"id": "ex", "name": "Press corto", "sets": sets(1, "ex")}],
}


def build(key, exs) -> Path:
    sess = {
        "id": SESS,
        "name": f"DoD {key}",
        "dayOfWeek": 2,
        "assignedDays": [1, 2, 3, 4, 5, 6, 7],
        "exercises": exs,
    }
    if key == "superseries":
        sess["supersetGroups"] = [
            {
                "id": "ss1",
                "exerciseOrder": ["ex-a", "ex-b"],
                "restBetweenExercises": 45,
                "restAfterSuperset": 120,
                "rounds": 3,
            }
        ]
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
    db = OUT / f"kpkn-seed-{key}.db"
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
    return dest


def looks_like_readiness(path: Path) -> bool:
    im = Image.open(path).convert("RGB")
    w, h = im.size
    # Sample mid band for neon-ish cyan/purple/orange rings vs light-gray card
    bright_gray = 0
    neonish = 0
    samples = 0
    for y in range(int(h * 0.28), int(h * 0.55), 8):
        for x in range(int(w * 0.25), int(w * 0.75), 8):
            r, g, b = im.getpixel((x, y))
            samples += 1
            if r > 180 and g > 180 and b > 180 and abs(r - g) < 25 and abs(g - b) < 25:
                bright_gray += 1
            # cyan / purple / orange accents common on readiness rings
            if (g > 150 and b > 150 and r < 120) or (b > 160 and r > 100 and g < 120) or (
                r > 180 and g > 80 and b < 80
            ):
                neonish += 1
    gray_ratio = bright_gray / max(samples, 1)
    neon_ratio = neonish / max(samples, 1)
    print(f"  pixel gray={gray_ratio:.3f} neon={neon_ratio:.3f}", flush=True)
    # Readiness: neon rings dominate mid; live: large light card
    if neon_ratio > 0.04 and gray_ratio < 0.08:
        return True
    if gray_ratio < 0.03 and neon_ratio > 0.02:
        return True
    return False


def looks_like_live(path: Path) -> bool:
    return not looks_like_readiness(path)


def dismiss_until_live(max_tries: int = 8) -> bool:
    # Known check button region on 1080x2400-ish emulators
    taps = [
        (540, 2190),
        (540, 2140),
        (540, 2240),
        (539, 2189),
        (560, 2200),
    ]
    for i in range(max_tries):
        shot = pull_shot("_probe.png")
        if looks_like_live(shot):
            print(f"  live after {i} taps", flush=True)
            return True
        x, y = taps[i % len(taps)]
        print(f"  force tap {x},{y}", flush=True)
        sh(f"input tap {x} {y}")
        time.sleep(2.2)
    return looks_like_live(pull_shot("_probe.png"))


def open_variant(key: str) -> bool:
    print("OPEN", key, flush=True)
    db = build(key, VARIANTS[key])
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
    time.sleep(15)
    # Always attempt dismiss; Compose readiness often invisible to uiautomator
    ok = dismiss_until_live()
    print(f"  result live={ok}", flush=True)
    return ok


def fatals() -> int:
    r = adb("logcat", "-d", "-v", "brief", check=False)
    return len(re.findall(r"FATAL EXCEPTION", r.stdout or ""))


def main():
    lines = []
    if open_variant("superseries"):
        pull_shot("20-superseries.png")
        lines.append(f"superseries OK size={(OUT / '20-superseries.png').stat().st_size} fatals={fatals()}")
    else:
        pull_shot("20-superseries.png")
        lines.append("superseries FAIL still readiness")

    if open_variant("normal"):
        pull_shot("20-normal.png")
        sh("input swipe 900 1100 220 1100 280")
        time.sleep(1.0)
        pull_shot("12-set-adjacent-snap.png")
        # Tap + near rail
        sh("input tap 80 1680")
        time.sleep(1.5)
        if looks_like_readiness(pull_shot("_probe.png")):
            dismiss_until_live()
        pull_shot("23-continuity-after-plus.png")
        lines.append(f"normal extras OK fatals={fatals()}")
    else:
        lines.append("normal FAIL")

    (OUT / "force-dismiss-summary.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(lines), flush=True)


if __name__ == "__main__":
    main()
