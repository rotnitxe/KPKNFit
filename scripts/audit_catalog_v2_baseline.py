#!/usr/bin/env python3
"""Recalculate the preflight evidence without importing it into v2."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "catalog" / "exercises" / "v2" / "curation" / "evidence" / "legacy"
ASSET = EVIDENCE / "exercise_database.json"
ALIASES = EVIDENCE / "exercise_id_aliases.json"
OUTPUT = ROOT / "catalog" / "exercises" / "v2" / "curation" / "baseline.json"


def main() -> int:
    raw = ASSET.read_bytes()
    exercises = json.loads(raw)
    aliases = json.loads(ALIASES.read_text(encoding="utf-8"))
    aspects = [aspect for exercise in exercises for aspect in exercise.get("technicalAspects") or []]
    options = [option for aspect in aspects for option in aspect.get("options") or []]
    effective_options = [option for option in options if option.get("modifiers")]
    singleton_aspects = sum(1 for aspect in aspects if len(aspect.get("options") or []) == 1)
    parents_without_effective_modifiers = sum(
        1 for exercise in exercises if not any(option.get("modifiers") for aspect in exercise.get("technicalAspects") or [] for option in aspect.get("options") or [])
    )
    cartesian_rows = sum(
        1
        for exercise in exercises
        for _ in [0]
        if (lambda axes: len(axes) > 1 and all(len(values) > 1 for values in axes))( [
            {option.get("id") for option in aspect.get("options") or []}
            for aspect in exercise.get("technicalAspects") or []
        ])
    )
    result = {
        "asset": str(ASSET.relative_to(ROOT)).replace("\\", "/"),
        "assetSha256": hashlib.sha256(raw).hexdigest(),
        "exerciseCount": len(exercises),
        "uniqueExerciseIds": len({exercise.get("id") for exercise in exercises}),
        "uniqueDisplayNames": len({exercise.get("name") for exercise in exercises}),
        "technicalAspectCount": len(aspects),
        "technicalOptionCount": len(options),
        "effectiveOptionCount": len(effective_options),
        "singletonAspectCount": singleton_aspects,
        "parentsWithoutEffectiveModifiers": parents_without_effective_modifiers,
        "parentsWithMultiAxisCartesianRisk": cartesian_rows,
        "aliasCount": len(aliases),
        "sourceRevision": "pre-v2-observation-2026-08-02",
        "notes": [
            "These values are evidence only; no legacy row is imported automatically.",
            "Cartesian risk is a diagnostic count of multi-axis option sets, not a generated exercise count.",
        ],
    }
    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
