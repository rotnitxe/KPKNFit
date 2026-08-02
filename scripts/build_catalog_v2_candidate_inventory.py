"""Create a review-only inventory from the current Android catalog.

The output is deliberately not a v2 runtime source. It records candidates so
the curator can account for every existing row without silently importing it.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "catalog" / "exercises" / "v2" / "curation" / "evidence" / "legacy"
DEFAULT_ASSET = EVIDENCE / "exercise_database.json"
DEFAULT_ALIASES = EVIDENCE / "exercise_id_aliases.json"
DEFAULT_OUTPUT = ROOT / "catalog" / "exercises" / "v2" / "curation" / "candidate_inventory.json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--asset", type=Path, default=DEFAULT_ASSET)
    parser.add_argument("--aliases", type=Path, default=DEFAULT_ALIASES)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    exercises = json.loads(args.asset.read_text(encoding="utf-8"))
    aliases = json.loads(args.aliases.read_text(encoding="utf-8"))
    aliases_by_target: dict[str, list[str]] = {}
    for alias, target in aliases.items():
        aliases_by_target.setdefault(target, []).append(alias)

    inventory = {
        "purpose": "review-only candidate inventory; not a runtime catalog",
        "sourceAsset": str(args.asset.relative_to(ROOT)).replace("\\", "/"),
        "sourceCount": len(exercises),
        "candidates": [
            {
                "sourceId": exercise["id"],
                "sourceName": exercise["name"],
                "description": exercise.get("description"),
                "technicalAspectIds": [aspect["id"] for aspect in exercise.get("technicalAspects") or []],
                "aliasIds": sorted(aliases_by_target.get(exercise["id"], [])),
                "decision": "UNREVIEWED",
                "decisionRationale": None,
            }
            for exercise in sorted(exercises, key=lambda item: item["id"])
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote={args.output}")
    print(f"candidates={len(inventory['candidates'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
