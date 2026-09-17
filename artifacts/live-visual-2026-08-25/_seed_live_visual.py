#!/usr/bin/env python3
"""Seed Room DB for live-visual DoD screenshots without wiping unrelated catalogs."""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

OUT = Path(__file__).resolve().parent
DB = OUT / "kpkn-seed.db"
SRC = OUT / "kpkn-pull.db"

PROG_ID = "live-visual-prog"
WEEK_ID = "live-visual-week"
SESS_ID = "live-visual-sess"
# HomeViewModel: Monday=1 … Sunday=7. Today (user_info) is Tuesday → 2.
DAY = 2


def sets(n: int, prefix: str) -> list[dict]:
    return [
        {"id": f"{prefix}-s{i}", "targetReps": 8 if i < n - 1 else 10, "targetRPE": 7.0}
        for i in range(n)
    ]


def exercise(**kwargs) -> dict:
    base = {
        "id": kwargs.pop("id"),
        "name": kwargs.pop("name"),
        "sets": kwargs.pop("sets", sets(3, kwargs.get("id", "ex"))),
    }
    base.update(kwargs)
    return base


exercises = [
    # short (1 working node)
    exercise(
        id="ex-short",
        name="Press corto",
        sets=sets(1, "ex-short"),
    ),
    # normal (5 nodes)
    exercise(
        id="ex-normal",
        name="Remo normal",
        sets=sets(5, "ex-normal"),
        warmupSets=[
            {"id": "ex-normal-wu1", "percentageOfWorkingWeight": 0.5, "targetReps": 8},
            {"id": "ex-normal-wu2", "percentageOfWorkingWeight": 0.7, "targetReps": 5},
        ],
        mobilityConfig={"mode": "ENFOCADO", "totalMinutes": 3},
        mobilitySeries=[
            {
                "id": "ex-normal-mob1",
                "name": "Movilidad hombro",
                "sets": 1,
                "reps": "10",
                "unit": "REPS",
            }
        ],
    ),
    # long (12+ nodes)
    exercise(
        id="ex-long",
        name="Curl largo",
        sets=sets(12, "ex-long"),
    ),
    # approximation-heavy (warmup as A nodes) — separate for clear capture
    exercise(
        id="ex-approx",
        name="Sentadilla aproximación",
        sets=sets(3, "ex-approx"),
        warmupSets=[
            {"id": "ex-approx-a1", "percentageOfWorkingWeight": 0.4, "targetReps": 8},
            {"id": "ex-approx-a2", "percentageOfWorkingWeight": 0.6, "targetReps": 5},
            {"id": "ex-approx-a3", "percentageOfWorkingWeight": 0.8, "targetReps": 3},
        ],
    ),
    # mobility-first
    exercise(
        id="ex-mobility",
        name="Peso muerto movilidad",
        sets=sets(2, "ex-mobility"),
        mobilityConfig={"mode": "ENFOCADO", "totalMinutes": 5},
        mobilitySeries=[
            {
                "id": "ex-mob-hip",
                "name": "Cadera 90/90",
                "sets": 2,
                "durationSeconds": 45,
                "unit": "SECONDS",
            },
            {
                "id": "ex-mob-tspine",
                "name": "Rotación T-spine",
                "sets": 1,
                "reps": "8",
                "unit": "REPS",
            },
        ],
    ),
    # unilateral
    exercise(
        id="ex-uni",
        name="Zancada unilateral",
        sets=sets(3, "ex-uni"),
        isUnilateral=True,
        unilateralMode="UNILATERAL_PAIRED",
        unilateralSideOrder="LEFT_RIGHT",
    ),
    # superseries pair
    exercise(
        id="ex-ss-a",
        name="Press banca SS",
        sets=sets(3, "ex-ss-a"),
        supersetId="ss-live-1",
        supersetGroupRef="ss-live-1",
    ),
    exercise(
        id="ex-ss-b",
        name="Remo SS",
        sets=sets(3, "ex-ss-b"),
        supersetId="ss-live-1",
        supersetGroupRef="ss-live-1",
    ),
    # cardio
    exercise(
        id="ex-cardio",
        name="Cinta cardio",
        sets=[{"id": "ex-cardio-s0", "targetDuration": 600}],
        trainingMode="TIME",
        cardioDetails={
            "type": "TREADMILL",
            "intensity": "MEDIA",
            "targetDurationSeconds": 600,
            "supportsDistance": True,
            "requiresGps": False,
            "metBase": 0.0,
            "intervalBlocks": [],
            "intervalRounds": 1,
        },
    ),
]

session = {
    "id": SESS_ID,
    "name": "DoD Live Variants",
    "dayOfWeek": DAY,
    "assignedDays": [1, 2, 3, 4, 5, 6, 7],
    "exercises": exercises,
    "supersetGroups": [
        {
            "id": "ss-live-1",
            "exerciseOrder": ["ex-ss-a", "ex-ss-b"],
            "restBetweenExercises": 45,
            "restAfterSuperset": 120,
            "rounds": 3,
        }
    ],
}

program = {
    "id": PROG_ID,
    "name": "Live Visual DoD",
    "structure": "SIMPLE",
    "simpleProgramKind": "CYCLIC",
    "coverImage": "gradient://ember",
    "macrocycles": [
        {
            "id": "live-macro",
            "name": "Macrociclo base",
            "blocks": [
                {
                    "id": "live-block",
                    "name": "Ciclo base",
                    "mesocycles": [
                        {
                            "id": "live-meso",
                            "name": "Mesociclo 1",
                            "goal": "ACCUMULATION",
                            "weeks": [
                                {
                                    "id": WEEK_ID,
                                    "name": "Semana 1",
                                    "sessions": [session],
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
        "weekInstanceId": WEEK_ID,
        "status": "ACTIVE",
        "cycleNumber": 1,
    },
}

active = {
    "programId": PROG_ID,
    "status": "ACTIVE",
    "currentMacrocycleIndex": 0,
    "currentBlockIndex": 0,
    "currentMesocycleIndex": 0,
    "currentWeekId": WEEK_ID,
    "currentMacrocycleId": "live-macro",
    "currentBlockId": "live-block",
    "currentMesocycleId": "live-meso",
    "currentWeekInstanceId": WEEK_ID,
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

import shutil

shutil.copyfile(SRC, DB)
# Drop WAL companions so seed is self-contained
for extra in (OUT / "kpkn-seed.db-wal", OUT / "kpkn-seed.db-shm"):
    if extra.exists():
        extra.unlink()

conn = sqlite3.connect(str(DB))
conn.execute("DELETE FROM programs")
conn.execute("DELETE FROM active_program")
conn.execute("DELETE FROM settings")
conn.execute("DELETE FROM ongoing_workout")
conn.execute(
    "INSERT INTO programs (id, name, data) VALUES (?, ?, ?)",
    (PROG_ID, program["name"], json.dumps(program, ensure_ascii=False)),
)
conn.execute(
    "INSERT INTO active_program (rowId, data) VALUES (1, ?)",
    (json.dumps(active, ensure_ascii=False),),
)
conn.execute(
    "INSERT INTO settings (rowId, data) VALUES (1, ?)",
    (json.dumps(settings, ensure_ascii=False),),
)
conn.commit()
conn.close()

meta = {
    "programId": PROG_ID,
    "sessionId": SESS_ID,
    "weekId": WEEK_ID,
    "route": f"workout/{PROG_ID}/{SESS_ID}",
    "deepLinkHint": f"kpkn://workout/{PROG_ID}/{SESS_ID}",
}
(OUT / "seed-meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
print("Wrote", DB, "bytes", DB.stat().st_size)
print(meta)
