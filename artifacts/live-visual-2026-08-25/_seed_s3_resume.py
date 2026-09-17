#!/usr/bin/env python3
"""Build seed that resumes at S3 (set index 2) with readiness already reported."""
from __future__ import annotations

import json
import sqlite3
import time
from pathlib import Path

OUT = Path(__file__).resolve().parent
src = OUT / "kpkn-seed-long-isthmus.db"
dst = OUT / "kpkn-seed-long-s3.db"
dst.write_bytes(src.read_bytes())

con = sqlite3.connect(src)
prog_json = con.execute("select data from programs limit 1").fetchone()[0]
con.close()
prog = json.loads(prog_json)
session = None

def walk(obj):
    global session
    if isinstance(obj, dict):
        if obj.get("id") == "live-visual-sess":
            session = obj
        for v in obj.values():
            walk(v)
    elif isinstance(obj, list):
        for v in obj:
            walk(v)

walk(prog)
assert session is not None
ex = (session.get("exercises") or [])[0]
sets = ex.get("sets") or []
assert len(sets) >= 3
set_idx = 2
active_set = sets[set_idx]
step_key = f"{ex['id']}_working_{set_idx}"
# Common key patterns in app: workingStepKey(exerciseId, setIdx) 
# Check WorkoutStepRules if needed — often `${id}_${setIdx}` or with side.

ongoing = {
    "programId": "live-visual-prog",
    "session": session,
    "isPaused": False,
    "startTime": int(time.time() * 1000) - 60_000,
    "activeExerciseId": ex["id"],
    "activeSetId": active_set.get("id"),
    "activeSetIndex": set_idx,
    "activeExerciseIndex": 0,
    "activeStepKey": f"{ex['id']}_{set_idx}",
    "activeMode": "A",
    "completedSets": {},
    "readinessNeuralOverride": 100,
    "readinessMuscularOverride": 100,
    "readinessSpinalOverride": 100,
    "readinessMuscleOverrides": {},
    "preparationReports": {},
    "pacingAlertMode": "final",
}

con = sqlite3.connect(dst)
con.execute("DELETE FROM ongoing_workout")
con.execute(
    "INSERT INTO ongoing_workout(rowId, data) VALUES (1, ?)",
    (json.dumps(ongoing, ensure_ascii=False),),
)
con.commit()
con.close()
print("wrote", dst, "activeSetIndex", set_idx, "step", ongoing["activeStepKey"])
