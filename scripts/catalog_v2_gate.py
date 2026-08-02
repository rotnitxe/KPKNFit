#!/usr/bin/env python3
"""Single report for the v2 cutover gates.

Diagnostic mode never mutates the tree. --strict is the CI/cutover gate:
unresolved editorial or legacy conditions fail the command instead of being
hidden behind warnings.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
INVENTORY = ROOT / "catalog" / "exercises" / "v2" / "curation" / "candidate_inventory.json"
ANDROID = ROOT / "android-native" / "app" / "src" / "main"


def source_gate() -> list[str]:
    failures: list[str] = []
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    definitions = [d for family in source["families"] for d in family["definitions"]]
    configurations = [c for definition in definitions for c in definition["configurations"]]
    generic_markers = (
        "configuración specialty",
        "configuración parent",
        "identidad técnica",
        "es un ejercicio dentro de un patrón",
        "configuración documentada",
        "el perfil fija la ejecución",
        "no se combinan opciones",
    )
    placeholder_pattern = re.compile(r"(?i)(?<![a-záéíóúüñ])(?:unknown|pendiente|placeholder|todo|n/a)(?![a-záéíóúüñ])")
    if len({d["id"] for d in definitions}) != len(definitions):
        failures.append("duplicate_definition_id")
    if len({c["id"] for c in configurations}) != len(configurations):
        failures.append("duplicate_configuration_id")
    for family in source["families"]:
        if family["evidence"]["reviewStatus"] != "APPROVED":
            failures.append(f"family_not_approved:{family['id']}")
        for definition in family["definitions"]:
            if definition["evidence"]["reviewStatus"] != "APPROVED":
                failures.append(f"definition_not_approved:{definition['id']}")
            if any(marker in definition["description"].lower() for marker in generic_markers):
                failures.append(f"generic_description:{definition['id']}")
            if placeholder_pattern.search(definition["description"]):
                failures.append(f"placeholder_description:{definition['id']}")
            axes = definition["optionAxes"]
            configuration_signatures: set[tuple[tuple[str, str], ...]] = set()
            configuration_ids = {configuration["id"] for configuration in definition["configurations"]}
            if definition["defaultConfigurationId"] not in configuration_ids:
                failures.append(f"invalid_default:{definition['id']}")
            for axis in axes:
                values = {c["selectedOptions"][axis] for c in definition["configurations"]}
                if len(values) == 1:
                    failures.append(f"singleton_axis:{definition['id']}:{axis}")
            for configuration in definition["configurations"]:
                signature = tuple(sorted(configuration["selectedOptions"].items()))
                if signature in configuration_signatures:
                    failures.append(f"duplicate_configuration_options:{configuration['id']}")
                configuration_signatures.add(signature)
                if any(marker in configuration["displaySummary"].lower() for marker in generic_markers):
                    failures.append(f"generic_display_summary:{configuration['id']}")
                if configuration["evidence"]["reviewStatus"] != "APPROVED":
                    failures.append(f"configuration_not_approved:{configuration['id']}")
                profile = configuration["profile"]
                serialized = json.dumps(configuration, ensure_ascii=False)
                if placeholder_pattern.search(serialized):
                    failures.append(f"placeholder_metadata:{configuration['id']}")
                if profile.get("automationEligible") is not True:
                    failures.append(f"automation_ineligible:{configuration['id']}")
                rich = profile.get("richMetadata")
                if not isinstance(rich, dict):
                    failures.append(f"rich_metadata_missing:{configuration['id']}")
                elif rich.get("evidenceConfidence") not in {"MEDIUM", "HIGH"}:
                    failures.append(f"rich_metadata_low_confidence:{configuration['id']}")
    return failures


def inventory_gate() -> list[str]:
    if not INVENTORY.exists():
        return ["candidate_inventory_missing"]
    inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    failures: list[str] = []
    candidates = inventory.get("candidates")
    if not isinstance(candidates, list) or not candidates:
        return ["candidate_inventory_empty"]
    allowed = {"PARENT", "CONFIGURATION", "SPECIALTY", "DISCARD"}
    for candidate in candidates:
        decision = candidate.get("decision")
        if decision not in allowed:
            failures.append(f"candidate_unresolved:{candidate.get('sourceId', '<missing>')}")
        if decision in allowed and not str(candidate.get("decisionRationale") or "").strip():
            failures.append(f"candidate_missing_rationale:{candidate.get('sourceId', '<missing>')}")
    return failures


def legacy_consumer_gate() -> list[str]:
    failures: list[str] = []
    patterns = {
        "runtime_alias_asset": re.compile(r"exercise_id_aliases\.json|\bEXERCISE_ID_ALIASES\b"),
        "legacy_variant_flow": re.compile(r"VariantFlowResultCache|VariantFlowSheet"),
        "legacy_technical_aspects": re.compile(r"technicalAspects"),
        "direct_global_exercise_map": re.compile(r"\bEXERCISE_DATABASE(?:_BY_ID)?\b"),
        "name_based_catalog_resolution": re.compile(r"\.name\.equals\(exerciseName"),
        # Only the catalog detail surface is prohibited from falling back to
        # placeholder values; unrelated diagnostics/protocol screens may use
        # their own domain-specific N/A notation.
        "placeholder_ui_fallback": re.compile(r"\?:\s*[\"'](?:N/A|unknown|pendiente|TODO)[\"']", re.IGNORECASE),
    }
    for path in ANDROID.rglob("*.kt"):
        if any(part in {"build", ".gradle"} for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8")
        for label, pattern in patterns.items():
            if label == "placeholder_ui_fallback" and path.name != "ExerciseDetailScreen.kt":
                continue
            if pattern.search(text):
                failures.append(f"{label}:{path.relative_to(ROOT)}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    failures = source_gate() + inventory_gate() + legacy_consumer_gate()
    if failures:
        print(f"status=BLOCKED failures={len(failures)}")
        for failure in failures:
            print(f"- {failure}")
        return 2 if args.strict else 0
    print("status=READY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
