#!/usr/bin/env python3
"""Compact, read-only view of the catalog v2 for authoring a ficha.

    python scripts/catalog_v2_show.py <definitionId> [<definitionId> ...] [--public]
    python scripts/catalog_v2_show.py --family <familyId> [--public]
    python scripts/catalog_v2_show.py --list [--status LEGACY|CURATED]

Shows what the compiled source currently carries: configurations, equipment, movement pattern,
muscle and joint involvement and, with --public, the user-facing copy. For a LEGACY definition this
is the inherited content to review (never to trust); for a CURATED one it is the applied ficha.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Iterator

ROOT = Path(__file__).resolve().parents[1]
V2 = ROOT / "catalog" / "exercises" / "v2"
FAMILIES = V2 / "source" / "families"
FICHAS = V2 / "curation" / "fichas"


def load_families(families_dir: Path = FAMILIES) -> Iterator[dict[str, Any]]:
    for path in sorted(families_dir.glob("*.json")):
        yield json.loads(path.read_text(encoding="utf-8"))["family"]


def ficha_statuses(fichas_dir: Path = FICHAS) -> dict[str, str]:
    statuses: dict[str, str] = {}
    for path in sorted(fichas_dir.glob("*.json")):
        ficha = json.loads(path.read_text(encoding="utf-8"))
        for definition_id, body in (ficha.get("definitions") or {}).items():
            statuses[definition_id] = body.get("status", "LEGACY")
    return statuses


def describe(family: dict[str, Any], definition: dict[str, Any], status: str, public: bool) -> list[str]:
    lines = [
        "=" * 100,
        f"family={family['id']} ({family['canonicalName']})  definition={definition['id']} ({definition['canonicalName']})  status={status}",
        f"optionAxes={definition['optionAxes']} default={definition['defaultConfigurationId']} kind={definition.get('kind')}",
        f"searchTerms={definition.get('searchTerms')}",
    ]
    if public:
        lines.append(f"DESCRIPTION: {definition['description']}")
    for configuration in definition["configurations"]:
        profile = configuration["profile"]
        lines.append("-" * 100)
        lines.append(f"config={configuration['id']} options={configuration['selectedOptions']} summary={configuration['displaySummary']!r}")
        lines.append(
            f"  equipment={profile['equipmentId']} pattern={profile['movementPatternId']} laterality={profile['laterality']} "
            f"chain={profile['kineticChain']} loadMode={profile['loadMode']} region={profile['bodyRegion']}"
        )
        lines.append(f"  P={profile['primaryMuscles']} S={profile['secondaryMuscles']} STAB={profile['stabilizerMuscles']}")
        joints = "; ".join(f"{joint['jointId']}:{joint['role']}[{'|'.join(joint['actions'])}]" for joint in profile["jointInvolvement"])
        lines.append(f"  joints: {joints}")
        if public:
            lines.append(f"  DESC: {profile['description']}")
            lines.append(f"  SETUP: {profile['setupCues']}")
            lines.append(f"  EXEC: {profile['executionCues']}")
    return lines


def main(argv: list[str] | None = None, *, families_dir: Path | None = None, fichas_dir: Path | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("definitions", nargs="*", help="definition ids to show")
    parser.add_argument("--family", help="show every definition of this family id")
    parser.add_argument("--list", action="store_true", help="inventory: family, definition, configurations, status")
    parser.add_argument("--status", choices=("LEGACY", "CURATED"), help="with --list: only this status")
    parser.add_argument("--public", action="store_true", help="include the user-facing copy")
    arguments = parser.parse_args(argv)
    families = list(load_families(families_dir or FAMILIES))
    statuses = ficha_statuses(fichas_dir or FICHAS)

    if arguments.list:
        for family in families:
            for definition in family["definitions"]:
                status = statuses.get(definition["id"], "LEGACY")
                if arguments.status and status != arguments.status:
                    continue
                equipment = sorted({configuration["profile"]["equipmentId"] for configuration in definition["configurations"]})
                print(f"{family['id']}\t{definition['id']}\t{status}\tconfigs={len(definition['configurations'])}\tequipment={','.join(equipment)}")
        return 0

    wanted = set(arguments.definitions)
    if not wanted and not arguments.family:
        parser.error("give definition ids, --family or --list")
    found: set[str] = set()
    for family in families:
        for definition in family["definitions"]:
            if definition["id"] in wanted or family["id"] == arguments.family:
                found.add(definition["id"])
                print("\n".join(describe(family, definition, statuses.get(definition["id"], "LEGACY"), arguments.public)))
    missing = sorted(wanted - found)
    if missing or (arguments.family and not found):
        print(f"not found: {missing or arguments.family}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
