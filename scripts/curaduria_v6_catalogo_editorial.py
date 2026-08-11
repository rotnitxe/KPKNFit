#!/usr/bin/env python3
"""Apply the explicit editorial brief source without touching anatomy data."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
BRIEFS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "editorial_briefs.json"
REVISION = "v2-approved-2026-08-10-c"
EVIDENCE_REF = "editorial:catalog-v7.2-human-editorial-2026-08-10"


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def apply_configuration(definition: dict[str, Any], configuration: dict[str, Any], brief: dict[str, Any]) -> None:
    profile = configuration["profile"]
    profile["catalogRevision"] = REVISION
    for key in ("description", "benefits", "techniqueSummary", "variantRationale", "setupCues", "executionCues"):
        profile[key] = brief[key]
    profile["richMetadata"]["editorial"] = {
        "description": profile["description"],
        "benefits": profile["benefits"],
        "technique": profile["techniqueSummary"],
        "variantRationale": profile["variantRationale"],
    }
    coaching = profile["richMetadata"].setdefault("coaching", {})
    coaching["setup"] = profile["setupCues"]
    coaching["execution"] = profile["executionCues"]
    coaching["cues"] = [profile["techniqueSummary"]]
    coaching["commonMistakes"] = profile.get("commonMistakes", [])
    identity = profile["richMetadata"]["identity"]
    identity["catalogRevision"] = REVISION
    evidence = configuration.setdefault("evidence", {})
    refs = [ref for ref in evidence.get("evidenceRefs", []) if "catalog-v" not in ref]
    evidence["evidenceRefs"] = refs + [EVIDENCE_REF]
    evidence["rationale"] = "Configuración con descripción, beneficios y técnica redactados para su identidad y opción exactas."


def apply_family(payload: dict[str, Any], briefs: dict[str, Any]) -> int:
    family = payload["family"]
    payload["catalogRevision"] = REVISION
    for definition in family["definitions"]:
        definition_brief = briefs.get(definition["id"])
        if not definition_brief:
            raise SystemExit(f"missing definition brief: {definition['id']}")
        definition["description"] = definition_brief["description"]
        definition["evidence"]["evidenceRefs"] = [EVIDENCE_REF]
        definition["evidence"]["rationale"] = "Descripción editorial dedicada al ejercicio; las variantes tienen copy propio por configuración."
        configuration_briefs = definition_brief.get("configurations", {})
        for configuration in definition["configurations"]:
            brief = configuration_briefs.get(configuration["id"])
            if not brief:
                raise SystemExit(f"missing configuration brief: {configuration['id']}")
            apply_configuration(definition, configuration, brief)
        family["evidence"]["evidenceRefs"] = [EVIDENCE_REF]
        family["evidence"]["rationale"] = "Familia con copy editorial dedicado por definición y configuración; anatomía preservada."
    payload["ontologyRevision"] = payload.get("ontologyRevision", "wikilab-v3-2026-08-08")
    return sum(len(definition["configurations"]) for definition in family["definitions"])


def main() -> int:
    briefs = json.loads(BRIEFS.read_text(encoding="utf-8"))
    if briefs.get("catalogRevision") != REVISION:
        raise SystemExit(f"brief revision mismatch: {briefs.get('catalogRevision')}")
    brief_definitions = briefs.get("definitions")
    if not isinstance(brief_definitions, dict):
        raise SystemExit("editorial briefs definitions must be an object")
    total = 0
    files = sorted(FAMILIES.glob("*.json"))
    for path in files:
        payload = json.loads(path.read_text(encoding="utf-8"))
        total += apply_family(payload, brief_definitions)
        temporary = path.with_name(f".{path.name}.codex-editorial-tmp")
        temporary.write_bytes(canonical_json(payload))
        temporary.replace(path)
    print(f"revision={REVISION}")
    print(f"families={len(files)} configurations={total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
