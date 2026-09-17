#!/usr/bin/env python3
"""Per-variant single-session seeds + long wait after deep link."""
from __future__ import annotations

import json
import shutil
import sqlite3
import subprocess
import time
from pathlib import Path

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
    "mobility": [
        {
            "id": "ex",
            "name": "Peso muerto M",
            "sets": sets(2, "ex"),
            "mobilityConfig": {"mode": "ENFOCADO", "totalMinutes": 5},
            "mobilitySeries": [
                {
                    "id": "m1",
                    "name": "Cadera 90/90",
                    "sets": 2,
                    "durationSeconds": 45,
                    "unit": "SECONDS",
                }
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
    "cardio": [
        {
            "id": "ex",
            "name": "Cinta cardio",
            "sets": [{"id": "c0", "targetDuration": 600}],
            "trainingMode": "TIME",
            "cardioDetails": {
                "type": "TREADMILL",
                "intensity": "MEDIA",
                "targetDurationSeconds": 600,
                "supportsDistance": True,
                "requiresGps": False,
                "metBase": 0.0,
                "intervalBlocks": [],
                "intervalRounds": 1,
            },
        }
    ],
}


def build_seed(key: str) -> Path:
    exercises = VARIANTS[key]
    sess = {
        "id": SESS,
        "name": f"DoD {key}",
        "dayOfWeek": 2,
        "assignedDays": [1, 2, 3, 4, 5, 6, 7],
        "exercises": exercises,
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


def open_live(db: Path):
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.7)
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
    # Dismiss pre-workout readiness sheet (white check CTA ~bottom center).
    sh("input tap 539 2189")
    time.sleep(3.5)


def shot(name: str):
    sh(f"screencap -p /sdcard/{name}")
    adb("pull", f"/sdcard/{name}", str(OUT / name))
    print("shot", name, (OUT / name).stat().st_size, flush=True)


def dump(name: str):
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / name), check=False)


def swipe(a, b, c, d, ms=300):
    sh(f"input swipe {a} {b} {c} {d} {ms}")
    time.sleep(0.8)


def main():
    lines = []
    for key in VARIANTS:
        print("VARIANT", key, flush=True)
        db = build_seed(key)
        open_live(db)
        shot(f"20-{key}.png")
        dump(f"ui-tree-{key}.xml")
        log = adb("logcat", "-d", check=False)
        fatals = [
            ln
            for ln in (log.stdout or "").splitlines()
            if "FATAL EXCEPTION" in ln or "ANR in com.example.kpkn" in ln
        ]
        size = (OUT / f"20-{key}.png").stat().st_size
        lines.append(f"{key}: size={size} fatals={len(fatals)}")
        print(lines[-1], flush=True)
        if key == "normal":
            swipe(900, 1100, 220, 1100)
            shot("12-set-adjacent-snap.png")
            dump("ui-tree-adjacent.xml")
            sh("input tap 80 1750")
            time.sleep(1.2)
            shot("23-continuity-after-plus.png")
        if key == "long":
            swipe(200, 1100, 900, 1100)
            shot("21-long-first-set.png")
            for _ in range(9):
                swipe(900, 1100, 220, 1100, 220)
            shot("22-long-last-set.png")
        if key == "short":
            swipe(540, 1500, 540, 450, 160)
            time.sleep(0.05)
            shot("30-transition-blur-attempt.png")
            time.sleep(0.9)
            shot("31-after-transition.png")
    (OUT / "capture-summary.txt").write_text("\n".join(lines), encoding="utf-8")
    # keep last seed as default
    shutil.copyfile(OUT / "kpkn-seed-normal.db", OUT / "kpkn-seed.db")
    print("DONE", flush=True)


if __name__ == "__main__":
    main()
