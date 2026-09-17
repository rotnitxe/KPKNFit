#!/usr/bin/env python3
"""Recapture variants stuck on readiness; verify live stage via UI dump."""
from __future__ import annotations

import json
import re
import shutil
import sqlite3
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree as ET

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5554"
OUT = Path(__file__).resolve().parent
SRC = OUT / "kpkn-pull.db"
PKG = "com.example.kpkn"
PROG = "live-visual-prog"
SESS = "live-visual-sess"

NEED = {
    "approx": [
        {
            "id": "ex",
            "name": "Sentadilla A",
            "sets": [{"id": f"ex-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(3)],
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
            "sets": [{"id": f"ex-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(3)],
            "isUnilateral": True,
            "unilateralMode": "UNILATERAL_PAIRED",
            "unilateralSideOrder": "LEFT_RIGHT",
        }
    ],
    "superseries": [
        {
            "id": "ex-a",
            "name": "Press banca SS",
            "sets": [{"id": f"exa-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(3)],
            "supersetId": "ss1",
            "supersetGroupRef": "ss1",
        },
        {
            "id": "ex-b",
            "name": "Remo SS",
            "sets": [{"id": f"exb-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(3)],
            "supersetId": "ss1",
            "supersetGroupRef": "ss1",
        },
    ],
    "normal": [{"id": "ex", "name": "Remo normal", "sets": [{"id": f"ex-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(5)]}],
    "long": [{"id": "ex", "name": "Curl largo", "sets": [{"id": f"ex-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(12)]}],
}


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def dump_ui() -> str:
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / "_ui_tmp.xml"), check=False)
    p = OUT / "_ui_tmp.xml"
    return p.read_text(encoding="utf-8", errors="ignore") if p.exists() else ""


def bounds_center(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_node(xml: str, pred) -> tuple[int, int] | None:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None
    for n in root.iter("node"):
        if pred(n.attrib):
            return bounds_center(n.attrib.get("bounds", ""))
    return None


def is_readiness(xml: str) -> bool:
    low = xml.lower()
    return "reporta tu estado" in low or "qué tan preparado" in low or "que tan preparado" in low


def is_live_stage(xml: str) -> bool:
    if is_readiness(xml):
        return False
    low = xml.lower()
    markers = ("series efectivas", "reps", "rpe", "preparación de movilidad", "espacio cardio", "treadmill")
    return any(m in low for m in markers)


def dismiss_readiness(max_tries: int = 6) -> bool:
    for i in range(max_tries):
        xml = dump_ui()
        if not is_readiness(xml):
            print(f"  readiness gone after try {i}", flush=True)
            return True
        pt = find_node(
            xml,
            lambda a: "confirmar y entrenar" in (a.get("content-desc") or "").lower()
            or "confirmar y entrenar" in (a.get("text") or "").lower(),
        )
        if pt is None:
            # Fallback: large bottom check circle (common emulator coords)
            pt = (540, 2190)
            print(f"  fallback tap {pt}", flush=True)
        else:
            print(f"  tap Confirmar {pt}", flush=True)
        sh(f"input tap {pt[0]} {pt[1]}")
        time.sleep(2.5)
    return not is_readiness(dump_ui())


def build(key: str, exs) -> Path:
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


def open_live(key: str, exs) -> bool:
    print("OPEN", key, flush=True)
    db = build(key, exs)
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
    time.sleep(14)
    ok = dismiss_readiness()
    time.sleep(2)
    xml = dump_ui()
    (OUT / f"ui-tree-{key}.xml").write_text(xml, encoding="utf-8")
    live = is_live_stage(xml)
    print(f"  live={live} readiness={is_readiness(xml)}", flush=True)
    return live


def screencap(name: str):
    sh(f"screencap -p /sdcard/{name}")
    adb("pull", f"/sdcard/{name}", str(OUT / name))
    print(f"  saved {name} size={(OUT / name).stat().st_size}", flush=True)


def fatals() -> int:
    r = adb("logcat", "-d", "-v", "brief", "*:E", check=False)
    return len(re.findall(r"FATAL EXCEPTION", r.stdout or ""))


def main():
    summary = []
    for key in ("approx", "unilateral", "superseries"):
        live = open_live(key, NEED[key])
        screencap(f"20-{key}.png")
        if live and key == "approx":
            # Approximations often land on first warmup — swipe/tap first A node if present
            sh("input tap 80 420", check=False)
            time.sleep(1)
            screencap("20-approx.png")
        summary.append(f"{key}: live={live} fatals={fatals()}")

    # Extras from normal + long
    if open_live("normal", NEED["normal"]):
        screencap("20-normal.png")
        sh("input swipe 900 1100 220 1100 280")
        time.sleep(1)
        screencap("12-set-adjacent-snap.png")
        # Peek expand (bottom grabber area)
        sh("input swipe 540 2300 540 1600 350")
        time.sleep(1)
        screencap("24-normal-peek-context.png")
        # + continuity
        sh("input tap 80 1750")
        time.sleep(1.5)
        screencap("23-continuity-after-plus.png")
        summary.append(f"normal-extras: live=True fatals={fatals()}")

    if open_live("long", NEED["long"]):
        screencap("20-long.png")
        sh("input swipe 200 1100 900 1100 280")
        time.sleep(0.8)
        screencap("21-long-first-set.png")
        for _ in range(10):
            sh("input swipe 900 1100 220 1100 200")
            time.sleep(0.3)
        screencap("22-long-last-set.png")
        summary.append(f"long-extras: live=True fatals={fatals()}")

    (OUT / "recapture-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
