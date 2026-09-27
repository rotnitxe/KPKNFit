#!/usr/bin/env python3
"""Apply the explicit editorial brief source without touching anatomy data.

Default invocation keeps the historical full application (every family, every
definition, stale `REVISION` stamping and the historical human editorial
`EVIDENCE_REF`). `--only-definitions` is the explicit scoped mode used for
additive or corrective copy work: it applies the same transformations to the
requested definitions only, keeps the catalog revision already declared by the
briefs and the family payloads, and never restamps evidence attribution.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
BRIEFS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "editorial_briefs.json"
REVISION = "v2-approved-2026-08-10-c"
EVIDENCE_REF = "editorial:catalog-v7.2-human-editorial-2026-08-10"
ONTOLOGY_REVISION = "wikilab-v3-2026-08-08"
DEFINITION_RATIONALE = "Descripción editorial dedicada al ejercicio; las variantes tienen copy propio por configuración."
CONFIGURATION_RATIONALE = "Configuración con descripción, beneficios y técnica redactados para su identidad y opción exactas."
FAMILY_RATIONALE = "Familia con copy editorial dedicado por definición y configuración; anatomía preservada."
PROFILE_COPY_KEYS = ("description", "benefits", "techniqueSummary", "variantRationale", "setupCues", "executionCues")


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def write_family(path: Path, payload: dict[str, Any]) -> None:
    temporary = path.with_name(f".{path.name}.codex-editorial-tmp")
    temporary.write_bytes(canonical_json(payload))
    temporary.replace(path)


def apply_configuration(
    definition: dict[str, Any],
    configuration: dict[str, Any],
    brief: dict[str, Any],
    *,
    revision: str = REVISION,
    evidence_ref: str = EVIDENCE_REF,
    stamp_evidence: bool = True,
) -> None:
    profile = configuration["profile"]
    profile["catalogRevision"] = revision
    for key in PROFILE_COPY_KEYS:
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
    identity["catalogRevision"] = revision
    if stamp_evidence:
        evidence = configuration.setdefault("evidence", {})
        refs = [ref for ref in evidence.get("evidenceRefs", []) if "catalog-v" not in ref]
        evidence["evidenceRefs"] = refs + [evidence_ref]
        evidence["rationale"] = CONFIGURATION_RATIONALE


def apply_family(
    payload: dict[str, Any],
    briefs: dict[str, Any],
    *,
    revision: str = REVISION,
    evidence_ref: str = EVIDENCE_REF,
    only_definitions: frozenset[str] | None = None,
    stamp_evidence: bool = True,
) -> int:
    family = payload["family"]
    payload["catalogRevision"] = revision
    applied = 0
    for definition in family["definitions"]:
        if only_definitions is not None and definition["id"] not in only_definitions:
            continue
        definition_id = definition["id"]
        definition_brief = briefs.get(definition_id)
        if not definition_brief:
            raise SystemExit(f"missing definition brief: {definition_id}")
        definition["description"] = definition_brief["description"]
        if stamp_evidence:
            definition["evidence"]["evidenceRefs"] = [evidence_ref]
            definition["evidence"]["rationale"] = DEFINITION_RATIONALE
        configuration_briefs = definition_brief.get("configurations", {})
        for configuration in definition["configurations"]:
            brief = configuration_briefs.get(configuration["id"])
            if not brief:
                raise SystemExit(f"missing configuration brief: {configuration['id']}")
            apply_configuration(
                definition,
                configuration,
                brief,
                revision=revision,
                evidence_ref=evidence_ref,
                stamp_evidence=stamp_evidence,
            )
            applied += 1
        if stamp_evidence:
            family["evidence"]["evidenceRefs"] = [evidence_ref]
            family["evidence"]["rationale"] = FAMILY_RATIONALE
    if only_definitions is None:
        payload["ontologyRevision"] = payload.get("ontologyRevision", ONTOLOGY_REVISION)
        return sum(len(definition["configurations"]) for definition in family["definitions"])
    return applied


def parse_only_definitions(raw: str | None) -> list[str] | None:
    if raw is None:
        return None
    requested = [item.strip() for item in raw.split(",")]
    if not requested or any(not item for item in requested):
        raise SystemExit("--only-definitions requires at least one non-empty definition id")
    duplicates = sorted({item for item in requested if requested.count(item) > 1})
    if duplicates:
        raise SystemExit(f"duplicate definition ids requested: {', '.join(duplicates)}")
    return requested


def validate_definition_brief(
    definition_id: str,
    definition: dict[str, Any],
    brief_definitions: dict[str, Any],
) -> None:
    brief = brief_definitions.get(definition_id)
    if not isinstance(brief, dict):
        raise SystemExit(f"missing definition brief: {definition_id}")
    if not isinstance(brief.get("description"), str) or not brief["description"]:
        raise SystemExit(f"invalid definition brief description: {definition_id}")
    configuration_briefs = brief.get("configurations")
    if not isinstance(configuration_briefs, dict):
        raise SystemExit(f"invalid definition brief configurations: {definition_id}")
    for configuration in definition["configurations"]:
        configuration_id = configuration["id"]
        configuration_brief = configuration_briefs.get(configuration_id)
        if not isinstance(configuration_brief, dict):
            raise SystemExit(f"missing configuration brief: {configuration_id}")
        for key in PROFILE_COPY_KEYS:
            if key not in configuration_brief:
                raise SystemExit(f"missing configuration brief field {key}: {configuration_id}")


def preflight_scoped(
    files: list[Path],
    requested: list[str],
    briefs: dict[str, Any],
    brief_definitions: dict[str, Any],
) -> tuple[str, dict[str, Path], dict[Path, dict[str, Any]]]:
    """Validate the whole selection before a single byte is written.

    Returns the revision already declared by the briefs, the family that owns
    every requested definition and the parsed payloads of those families only.
    Nothing is mutated here, so any `SystemExit` leaves the catalogue untouched.
    """
    current = briefs.get("catalogRevision")
    if not isinstance(current, str) or not current:
        raise SystemExit("editorial briefs catalogRevision must be a non-empty string")
    located: dict[str, list[tuple[Path, dict[str, Any]]]] = {item: [] for item in requested}
    payloads: dict[Path, dict[str, Any]] = {}
    for path in files:
        payload = json.loads(path.read_text(encoding="utf-8"))
        if payload.get("catalogRevision") != current:
            raise SystemExit(
                f"family revision mismatch: {payload.get('catalogRevision')} in {path.name} != briefs {current}"
            )
        payloads[path] = payload
        for definition in payload.get("family", {}).get("definitions", []):
            definition_id = definition.get("id")
            if definition_id in located:
                located[definition_id].append((path, definition))
    assignments: dict[str, Path] = {}
    for definition_id in requested:
        matches = located[definition_id]
        if not matches:
            raise SystemExit(f"unknown definition id: {definition_id}")
        if len(matches) > 1:
            names = ", ".join(sorted(path.name for path, _ in matches))
            raise SystemExit(f"definition id is not unique across families: {definition_id} ({names})")
        path, definition = matches[0]
        validate_definition_brief(definition_id, definition, brief_definitions)
        assignments[definition_id] = path
    return current, assignments, {path: payloads[path] for path in set(assignments.values())}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="curaduria_v6_catalogo_editorial.py",
        description="Apply the explicit editorial brief source without touching anatomy data.",
    )
    parser.add_argument(
        "--only-definitions",
        metavar="ID[,ID...]",
        default=None,
        help=(
            "Scoped mode: apply the briefs of these exact definition ids only. Keeps the catalog revision "
            "already declared by the briefs and the family payloads, leaves evidence refs, review status and "
            "family-level metadata untouched, and writes only the families that own a requested id."
        ),
    )
    return parser


def main(argv: list[str] | None = None, *, families_dir: Path | None = None, briefs_path: Path | None = None) -> int:
    arguments = build_parser().parse_args(argv)
    requested = parse_only_definitions(arguments.only_definitions)
    families = Path(families_dir) if families_dir is not None else FAMILIES
    briefs_file = Path(briefs_path) if briefs_path is not None else BRIEFS
    briefs = json.loads(briefs_file.read_text(encoding="utf-8"))
    if requested is None and briefs.get("catalogRevision") != REVISION:
        raise SystemExit(f"brief revision mismatch: {briefs.get('catalogRevision')}")
    brief_definitions = briefs.get("definitions")
    if not isinstance(brief_definitions, dict):
        raise SystemExit("editorial briefs definitions must be an object")
    files = sorted(families.glob("*.json"))
    if requested is None:
        total = 0
        for path in files:
            payload = json.loads(path.read_text(encoding="utf-8"))
            total += apply_family(payload, brief_definitions)
            write_family(path, payload)
        print(f"revision={REVISION}")
        print(f"families={len(files)} configurations={total}")
        return 0
    current, assignments, payloads = preflight_scoped(files, requested, briefs, brief_definitions)
    total = 0
    for path in sorted(payloads, key=lambda item: item.name):
        selected = frozenset(item for item, owner in assignments.items() if owner == path)
        total += apply_family(
            payloads[path],
            brief_definitions,
            revision=current,
            only_definitions=selected,
            stamp_evidence=False,
        )
        write_family(path, payloads[path])
    print(f"revision={current}")
    print(f"mode=scoped definitions={len(requested)} families={len(payloads)} configurations={total}")
    for definition_id in requested:
        print(f"definition={definition_id} family={assignments[definition_id].name}")
    for path in sorted(payloads, key=lambda item: item.name):
        print(f"family_file={path.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
