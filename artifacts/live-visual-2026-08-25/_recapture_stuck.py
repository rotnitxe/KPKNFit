#!/usr/bin/env python3
from __future__ import annotations

import json
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


NEED = {
    "short": [{"id": "ex", "name": "Press corto", "sets": sets(1, "ex")}],
    "normal": [{"id": "ex", "name": "Remo normal", "sets": sets(5, "ex")}],
    "long": [{"id": "ex", "name": "Curl largo", "sets": sets(12, "ex")}],
    "approx": [
        {
            "id": "ex",
            "name": "Sentadilla A",
            "sets": sets(3, "ex"),
            "warmupSets": [
                {"id": "a1", "percentageOfWorkingWeight": 0.4, "targetReps": 8},
                {"id": "a2", "percentageOfWorkingWeight": 0.6, "targetReps": 5},
                {"id": "a3", "percentageOfWorkingWeight": 0.8, "targetReps": 3},
            ],
        }
    ],
    "unilateral": [
        {
            "id": "ex",
            "name": "Zancada UNI",
            "sets": sets(3, "ex"),
            "isUnilateral": True,
            "unilateralMode": "UNILATERAL_PAIRED",
            "unilateralSideOrder": "LEFT_RIGHT",
        }
    ],
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


def dismiss_readiness():
    sh("screencap -p /sdcard/tmp.png")
    adb("pull", "/sdcard/tmp.png", str(OUT / "tmp-ready.png"))
    im = Image.open(OUT / "tmp-ready.png").convert("RGB")
    w, h = im.size
    cands = []
    for y in range(int(h * 0.78), int(h * 0.96)):
        for x in range(int(w * 0.35), int(w * 0.65)):
            r, g, b = im.getpixel((x, y))
            if r > 220 and g > 220 and b > 220:
                cands.append((x, y))
    if not cands:
        print("no readiness button found", flush=True)
        return
    cx = sum(c[0] for c in cands) // len(cands)
    cy = sum(c[1] for c in cands) // len(cands)
    print("tap readiness", cx, cy, flush=True)
    sh(f"input tap {cx} {cy}")
    time.sleep(4)


def main():
    for key, exs in NEED.items():
        print("RECapture", key, flush=True)
        db = build(key, exs)
        sh(f"am force-stop {PKG}", check=False)
        time.sleep(0.6)
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
        time.sleep(12)
        dismiss_readiness()
        sh(f"screencap -p /sdcard/20-{key}.png")
        adb("pull", f"/sdcard/20-{key}.png", str(OUT / f"20-{key}.png"))
        print(key, (OUT / f"20-{key}.png").stat().st_size, flush=True)
        if key == "normal":
            sh("input swipe 900 1100 220 1100 300")
            time.sleep(1)
            sh("screencap -p /sdcard/12-set-adjacent-snap.png")
            adb("pull", "/sdcard/12-set-adjacent-snap.png", str(OUT / "12-set-adjacent-snap.png"))
            sh("input tap 80 1750")
            time.sleep(1.2)
            sh("screencap -p /sdcard/23-continuity-after-plus.png")
            adb(
                "pull",
                "/sdcard/23-continuity-after-plus.png",
                str(OUT / "23-continuity-after-plus.png"),
            )
        if key == "long":
            sh("input swipe 200 1100 900 1100 300")
            time.sleep(0.8)
            sh("screencap -p /sdcard/21-long-first-set.png")
            adb("pull", "/sdcard/21-long-first-set.png", str(OUT / "21-long-first-set.png"))
            for _ in range(9):
                sh("input swipe 900 1100 220 1100 220")
                time.sleep(0.35)
            sh("screencap -p /sdcard/22-long-last-set.png")
            adb("pull", "/sdcard/22-long-last-set.png", str(OUT / "22-long-last-set.png"))
    print("DONE", flush=True)


if __name__ == "__main__":
    main()
