#!/usr/bin/env python3
"""One-time F1 migration: editorial briefs -> LEGACY fichas.

Every family gets ``curation/fichas/<familyId>.json`` whose definitions are
``status: LEGACY`` and carry only the ``public`` block (definition description and,
per configuration, description / setupCues / executionCues). The values come from
the family source files, which the retired ``editorial_briefs.json`` mirrored byte
for byte; this script *proves* that before writing: with ``--briefs`` it compares
both and refuses to continue on any mismatch.

After the migration ``python scripts/catalog_v2_apply_fichas.py --check`` must report
"nothing differs": applying the skeletons to the current families changes no byte,
which is the evidence that no content was lost.

Dry-run by default; ``--apply`` writes. Idempotent. Never overwrites a ficha that
already has CURATED definitions.

Already applied on 2026-10-01; kept as the audit trail of how the skeletons were born.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from catalog_v2_apply_fichas import (  # noqa: E402
    FICHA_SCHEMA_VERSION,
    FICHAS,
    FAMILIES,
    PUBLIC_CONFIGURATION_KEYS,
    STATUS_CURATED,
    STATUS_LEGACY,
    canonical_json,
)

BRIEFS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "editorial_briefs.json"


def skeleton(family: dict) -> dict:
    definitions = {}
    for definition in family["definitions"]:
        configurations = {}
        for configuration in definition["configurations"]:
            profile = configuration["profile"]
            configurations[configuration["id"]] = {key: profile[key] for key in PUBLIC_CONFIGURATION_KEYS}
        definitions[definition["id"]] = {
            "status": STATUS_LEGACY,
            "public": {"description": definition["description"], "configurations": configurations},
        }
    return {"schemaVersion": FICHA_SCHEMA_VERSION, "familyId": family["id"], "definitions": definitions}


def compare_with_briefs(families: list[dict], briefs: dict) -> list[str]:
    problems: list[str] = []
    brief_definitions = briefs["definitions"]
    source_ids = {definition["id"] for family in families for definition in family["definitions"]}
    if set(brief_definitions) != source_ids:
        problems.append(f"definition inventory differs: missing {sorted(source_ids - set(brief_definitions))} extra {sorted(set(brief_definitions) - source_ids)}")
    for family in families:
        for definition in family["definitions"]:
            brief = brief_definitions.get(definition["id"])
            if brief is None:
                continue
            if brief.get("description") != definition["description"]:
                problems.append(f"{definition['id']}: description differs from the brief")
            brief_configurations = brief.get("configurations", {})
            if set(brief_configurations) != {configuration["id"] for configuration in definition["configurations"]}:
                problems.append(f"{definition['id']}: configuration inventory differs from the brief")
                continue
            for configuration in definition["configurations"]:
                for key in PUBLIC_CONFIGURATION_KEYS:
                    if brief_configurations[configuration["id"]].get(key) != configuration["profile"][key]:
                        problems.append(f"{configuration['id']}: {key} differs from the brief")
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--apply", action="store_true", help="write the fichas (default: dry-run)")
    parser.add_argument("--briefs", type=Path, default=BRIEFS, help="editorial_briefs.json to cross-check (skipped when absent)")
    arguments = parser.parse_args(argv)

    payloads = [json.loads(path.read_text(encoding="utf-8")) for path in sorted(FAMILIES.glob("*.json"))]
    families = [payload["family"] for payload in payloads]
    if arguments.briefs.is_file():
        briefs = json.loads(arguments.briefs.read_text(encoding="utf-8"))
        problems = compare_with_briefs(families, briefs)
        if problems:
            print(f"briefs cross-check FAILED ({len(problems)} differences):")
            for problem in problems[:20]:
                print(f"  - {problem}")
            return 1
        print(f"briefs cross-check ok: {sum(len(f['definitions']) for f in families)} definitions identical to {arguments.briefs.name}")
    else:
        print(f"briefs cross-check skipped ({arguments.briefs.name} not found)")

    written = skipped = unchanged = 0
    for family in families:
        target = FICHAS / f"{family['id']}.json"
        payload = canonical_json(skeleton(family))
        if target.is_file():
            current = json.loads(target.read_text(encoding="utf-8"))
            if any(body.get("status") == STATUS_CURATED for body in current.get("definitions", {}).values()):
                skipped += 1
                continue
            if canonical_json(current) == payload:
                unchanged += 1
                continue
        if arguments.apply:
            FICHAS.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
        written += 1
    verb = "written" if arguments.apply else "would write"
    print(f"families={len(families)} {verb}={written} unchanged={unchanged} skipped_curated={skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
