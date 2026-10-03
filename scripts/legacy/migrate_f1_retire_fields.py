#!/usr/bin/env python3
"""One-time F1 migration: retire unused catalog fields from the family sources.

Strips every field listed in ``catalog_v2_retired_fields.RETIRED_FIELD_PATHS``
from ``catalog/exercises/v2/source/families/*.json`` and re-derives the anatomy
mirrors (``jointActions``, ``preservesIntent``) with ``catalog_v2_derived``.

Dry run by default; ``--apply`` rewrites the family files with the canonical
writer.  Idempotent: running it on an already migrated source changes nothing.
After applying, rebuild the aggregate with ``merge_catalog_v2_families.py`` and
recompile with ``compile_exercise_catalog_v2.py --write``.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from catalog_v2_derived import derive_joint_actions, derive_preserves_intent  # noqa: E402
from catalog_v2_retired_fields import find_retired, strip_retired  # noqa: E402

ROOT = SCRIPTS.parent
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def migrate_family(payload: dict) -> tuple[dict, dict[str, int]]:
    counts = {"retired_fields": len(find_retired(payload)), "intent": 0, "joint_actions": 0}
    migrated = strip_retired(payload)
    for definition in migrated["family"]["definitions"]:
        for configuration in definition["configurations"]:
            profile = configuration["profile"]
            rich = profile["richMetadata"]
            intent = derive_preserves_intent(profile["movementPatternId"], profile["primaryMuscles"])
            if rich["replacement"].get("preservesIntent") != intent:
                rich["replacement"]["preservesIntent"] = intent
                counts["intent"] += 1
            actions = derive_joint_actions(profile["jointInvolvement"])
            if rich["anatomy"].get("jointActions") != actions:
                rich["anatomy"]["jointActions"] = actions
                counts["joint_actions"] += 1
    return migrated, counts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--apply", action="store_true", help="rewrite the family files (default: dry run)")
    arguments = parser.parse_args()

    totals = {"files": 0, "changed": 0, "retired_fields": 0, "intent": 0, "joint_actions": 0}
    for path in sorted(FAMILIES.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        migrated, counts = migrate_family(payload)
        totals["files"] += 1
        for key, value in counts.items():
            totals[key] += value
        new_bytes = canonical_json(migrated)
        if new_bytes != canonical_json(payload):
            totals["changed"] += 1
            if arguments.apply:
                temporary = path.with_suffix(".json.tmp")
                temporary.write_bytes(new_bytes)
                temporary.replace(path)
    print(json.dumps(totals, sort_keys=True))
    if not arguments.apply:
        print("dry run: pass --apply to rewrite the family files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
