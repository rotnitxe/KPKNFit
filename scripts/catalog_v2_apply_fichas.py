#!/usr/bin/env python3
"""Deterministic applier: ``curation/fichas/<familyId>.json`` -> ``source/families/<familyId>.json``.

The ficha is the single human authoring surface of an exercise: public copy,
anatomy, internal technique and the visual brief used to draw it. This tool
**only copies**. It never writes prose, never guesses a muscle and never
touches ids, options, fatigue metrics, evidence or the catalog revision.

What it writes, per definition of a ficha:

* every status: ``definition.description`` and, per configuration,
  ``profile.description / setupCues / executionCues`` (the ``public`` block);
* ``CURATED`` only: the anatomy (muscle roles and joint involvement, with the
  per-configuration ``overrides``) and, when declared, the movement pattern. Every
  field that is a pure function of the anatomy is re-derived with
  ``catalog_v2_derived`` so it can never drift.

Properties the pipeline relies on:

* **strict preflight** - the whole selection is validated before one byte is written;
* **idempotent** - applying twice changes nothing, and unchanged family files are not rewritten;
* **apply-changes-nothing** - ``--check`` (and the gate) fail when a family file differs from
  what its ficha produces, so a hand edit of the source can not survive.

Usage::

    python scripts/catalog_v2_apply_fichas.py                          # every family
    python scripts/catalog_v2_apply_fichas.py --only-definitions a,b   # scoped
    python scripts/catalog_v2_apply_fichas.py --check                  # verify, never write
"""

from __future__ import annotations

import argparse
import copy
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

try:
    from catalog_v2_derived import (
        derive_joint_actions,
        derive_joint_involvement,
        derive_muscle_lists,
        derive_preserves_intent,
        derive_relevant_joints,
    )
    from catalog_v2_ontology import JOINT_IDS, MOVEMENT_PATTERN_IDS, MUSCLE_IDS, ROLES
except ModuleNotFoundError:  # loaded by file path (tests) without scripts/ on sys.path
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from catalog_v2_derived import (
        derive_joint_actions,
        derive_joint_involvement,
        derive_muscle_lists,
        derive_preserves_intent,
        derive_relevant_joints,
    )
    from catalog_v2_ontology import JOINT_IDS, MOVEMENT_PATTERN_IDS, MUSCLE_IDS, ROLES

ROOT = Path(__file__).resolve().parents[1]
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
FICHAS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "fichas"

FICHA_SCHEMA_VERSION = 1
STATUS_LEGACY = "LEGACY"
STATUS_CURATED = "CURATED"
STATUSES = (STATUS_LEGACY, STATUS_CURATED)
OVERRIDE_REMOVE = "NONE"

PUBLIC_CONFIGURATION_KEYS = ("description", "setupCues", "executionCues")
LEGACY_BLOCKS = frozenset({"status", "public"})
CURATED_BLOCKS = frozenset({"status", "public", "technique", "anatomy", "visual", "sources", "pattern"})
CURATED_REQUIRED_BLOCKS = ("public", "technique", "anatomy", "visual", "sources")


class FichaError(ValueError):
    """A ficha (or its relation to the family files) can not be applied."""


# ---------------------------------------------------------------------------
# I/O
# ---------------------------------------------------------------------------


def canonical_json(value: Any) -> bytes:
    """The serialization every family file already uses (LF, sorted keys, 2-space indent)."""
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise FichaError(f"{path.name}: invalid JSON at line {error.lineno} column {error.colno}") from error


def write_family(path: Path, payload: dict[str, Any]) -> None:
    temporary = path.with_name(f".{path.name}.fichas-tmp")
    temporary.write_bytes(canonical_json(payload))
    temporary.replace(path)


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------


def _is_text(value: Any) -> bool:
    return isinstance(value, str) and value.strip() != ""


def _is_text_list(value: Any) -> bool:
    return isinstance(value, list) and len(value) >= 1 and all(_is_text(item) for item in value)


def _check_public(definition_id: str, public: Any, errors: list[str]) -> None:
    if not isinstance(public, dict):
        errors.append(f"{definition_id}: public must be an object")
        return
    unknown = sorted(set(public) - {"description", "configurations"})
    if unknown:
        errors.append(f"{definition_id}: unknown public keys {unknown}")
    if not _is_text(public.get("description")):
        errors.append(f"{definition_id}: public.description must be non-empty text")
    configurations = public.get("configurations")
    if not isinstance(configurations, dict) or not configurations:
        errors.append(f"{definition_id}: public.configurations must be a non-empty object")
        return
    for configuration_id, entry in configurations.items():
        if not isinstance(entry, dict):
            errors.append(f"{configuration_id}: public entry must be an object")
            continue
        unknown = sorted(set(entry) - set(PUBLIC_CONFIGURATION_KEYS))
        if unknown:
            errors.append(f"{configuration_id}: unknown public keys {unknown}")
        if not _is_text(entry.get("description")):
            errors.append(f"{configuration_id}: public description must be non-empty text")
        for key in ("setupCues", "executionCues"):
            if not _is_text_list(entry.get(key)):
                errors.append(f"{configuration_id}: public {key} must be a non-empty list of text")


def _check_entries(
    label: str,
    entries: Any,
    ontology: frozenset[str],
    *,
    joints: bool,
    allow_remove: bool,
    errors: list[str],
) -> None:
    if not isinstance(entries, list):
        errors.append(f"{label}: must be a list")
        return
    seen: set[str] = set()
    roles = ROLES + ((OVERRIDE_REMOVE,) if allow_remove else ())
    for index, entry in enumerate(entries):
        where = f"{label}[{index}]"
        if not isinstance(entry, dict):
            errors.append(f"{where}: must be an object")
            continue
        entry_id = entry.get("id")
        if entry_id not in ontology:
            errors.append(f"{where}: id outside the ontology: {entry_id!r}")
            continue
        if entry_id in seen:
            errors.append(f"{where}: repeated id {entry_id}")
        seen.add(entry_id)
        role = entry.get("role")
        if role not in roles:
            errors.append(f"{where}: invalid role {role!r}")
        if joints and role != OVERRIDE_REMOVE and not _is_text_list(entry.get("actions")):
            errors.append(f"{where}: a joint needs a non-empty actions list")


def _check_anatomy(definition_id: str, anatomy: Any, configuration_ids: set[str], errors: list[str]) -> None:
    if not isinstance(anatomy, dict):
        errors.append(f"{definition_id}: anatomy must be an object")
        return
    for key, ontology, joints in (("muscles", MUSCLE_IDS, False), ("joints", JOINT_IDS, True)):
        entries = anatomy.get(key)
        if not isinstance(entries, list) or not entries:
            errors.append(f"{definition_id}: anatomy.{key} must be a non-empty list")
            continue
        _check_entries(f"{definition_id}: anatomy.{key}", entries, ontology, joints=joints, allow_remove=False, errors=errors)
    overrides = anatomy.get("overrides", {})
    if not isinstance(overrides, dict):
        errors.append(f"{definition_id}: anatomy.overrides must be an object")
        return
    for configuration_id, override in overrides.items():
        if configuration_id not in configuration_ids:
            errors.append(f"{definition_id}: anatomy.overrides names an unknown configuration {configuration_id}")
            continue
        if not isinstance(override, dict) or set(override) - {"muscles", "joints"}:
            errors.append(f"{configuration_id}: an override only accepts muscles and joints")
            continue
        for key, ontology, joints in (("muscles", MUSCLE_IDS, False), ("joints", JOINT_IDS, True)):
            if key in override:
                _check_entries(
                    f"{configuration_id}: override.{key}",
                    override[key],
                    ontology,
                    joints=joints,
                    allow_remove=True,
                    errors=errors,
                )


def _check_pattern(definition_id: str, pattern: Any, errors: list[str]) -> None:
    if not isinstance(pattern, dict):
        errors.append(f"{definition_id}: pattern must be an object")
        return
    if pattern.get("movementPatternId") not in MOVEMENT_PATTERN_IDS:
        errors.append(f"{definition_id}: pattern.movementPatternId outside the runtime patterns: {pattern.get('movementPatternId')!r}")
    unknown = sorted(set(pattern) - {"movementPatternId", "why", "sources"})
    if unknown:
        errors.append(f"{definition_id}: unknown pattern keys {unknown}")


def effective_anatomy(anatomy: dict[str, Any], configuration_id: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Base anatomy of the definition with the override of one configuration applied.

    An override entry replaces the base entry with the same id (keeping its position),
    a new id is appended, and ``role: NONE`` removes the entry.
    """
    muscles = [dict(entry) for entry in anatomy["muscles"]]
    joints = [dict(entry) for entry in anatomy["joints"]]
    override = (anatomy.get("overrides") or {}).get(configuration_id) or {}
    return _merge(muscles, override.get("muscles", [])), _merge(joints, override.get("joints", []))


def _merge(base: list[dict[str, Any]], patches: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    result = list(base)
    for patch in patches:
        position = next((index for index, entry in enumerate(result) if entry["id"] == patch["id"]), None)
        if patch["role"] == OVERRIDE_REMOVE:
            if position is not None:
                del result[position]
            continue
        if position is None:
            result.append(dict(patch))
        else:
            result[position] = dict(patch)
    return result


def _check_effective_anatomy(definition: dict[str, Any], anatomy: dict[str, Any], errors: list[str]) -> None:
    for configuration in definition["configurations"]:
        muscles, joints = effective_anatomy(anatomy, configuration["id"])
        if not any(entry["role"] == "PRIMARY" for entry in muscles):
            errors.append(f"{configuration['id']}: the effective anatomy has no PRIMARY muscle")
        if not joints:
            errors.append(f"{configuration['id']}: the effective anatomy has no joint")
    overrides = anatomy.get("overrides") or {}
    base_ids = {
        "muscles": {entry["id"] for entry in anatomy["muscles"]},
        "joints": {entry["id"] for entry in anatomy["joints"]},
    }
    for configuration_id, override in overrides.items():
        for key in ("muscles", "joints"):
            for patch in override.get(key, []):
                if patch.get("role") == OVERRIDE_REMOVE and patch["id"] not in base_ids[key]:
                    errors.append(f"{configuration_id}: override removes {patch['id']} which the base anatomy does not list")


def validate_ficha(payload: dict[str, Any], ficha: Any) -> list[str]:
    """Every reason why ``ficha`` can not be applied to the family ``payload`` (empty = applicable)."""
    family = payload["family"]
    family_id = family["id"]
    errors: list[str] = []
    if not isinstance(ficha, dict):
        return [f"{family_id}: the ficha must be a JSON object"]
    if ficha.get("schemaVersion") != FICHA_SCHEMA_VERSION:
        errors.append(f"{family_id}: schemaVersion must be {FICHA_SCHEMA_VERSION}")
    if ficha.get("familyId") != family_id:
        errors.append(f"{family_id}: familyId is {ficha.get('familyId')!r}")
    unknown = sorted(set(ficha) - {"schemaVersion", "familyId", "definitions"})
    if unknown:
        errors.append(f"{family_id}: unknown ficha keys {unknown}")
    bodies = ficha.get("definitions")
    if not isinstance(bodies, dict):
        return errors + [f"{family_id}: definitions must be an object"]
    expected_ids = [definition["id"] for definition in family["definitions"]]
    missing = sorted(set(expected_ids) - set(bodies))
    extra = sorted(set(bodies) - set(expected_ids))
    if missing:
        errors.append(f"{family_id}: ficha is missing definitions {missing}")
    if extra:
        errors.append(f"{family_id}: ficha has unknown definitions {extra}")
    for definition in family["definitions"]:
        definition_id = definition["id"]
        body = bodies.get(definition_id)
        if not isinstance(body, dict):
            continue
        status = body.get("status")
        if status not in STATUSES:
            errors.append(f"{definition_id}: status must be one of {STATUSES}, got {status!r}")
            continue
        allowed = CURATED_BLOCKS if status == STATUS_CURATED else LEGACY_BLOCKS
        unknown = sorted(set(body) - allowed)
        if unknown:
            errors.append(f"{definition_id}: {status} ficha does not accept {unknown}")
        configuration_ids = {configuration["id"] for configuration in definition["configurations"]}
        public = body.get("public")
        _check_public(definition_id, public, errors)
        published = (public or {}).get("configurations") if isinstance(public, dict) else None
        if isinstance(published, dict):
            if set(published) != configuration_ids:
                missing = sorted(configuration_ids - set(published))
                extra = sorted(set(published) - configuration_ids)
                errors.append(f"{definition_id}: configuration inventory mismatch (missing {missing}, extra {extra})")
        if status != STATUS_CURATED:
            continue
        for block in CURATED_REQUIRED_BLOCKS:
            if block not in body:
                errors.append(f"{definition_id}: a CURATED ficha requires {block}")
        anatomy_errors: list[str] = []
        if "anatomy" in body:
            _check_anatomy(definition_id, body["anatomy"], configuration_ids, anatomy_errors)
            if not anatomy_errors:
                _check_effective_anatomy(definition, body["anatomy"], anatomy_errors)
        errors.extend(anatomy_errors)
        if "pattern" in body:
            _check_pattern(definition_id, body["pattern"], errors)
    return errors


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------


def _apply_pattern(profile: dict[str, Any], pattern: dict[str, Any] | None) -> None:
    if pattern is None:
        return
    profile["movementPatternId"] = pattern["movementPatternId"]
    profile["richMetadata"]["biomechanics"]["movementPatternId"] = pattern["movementPatternId"]


def _apply_anatomy(profile: dict[str, Any], anatomy: dict[str, Any], configuration_id: str) -> None:
    muscles, joints = effective_anatomy(anatomy, configuration_id)
    lists = derive_muscle_lists(muscles)
    involvement = derive_joint_involvement(joints)
    rich = profile["richMetadata"]
    for field_name, muscle_ids in lists.items():
        profile[field_name] = list(muscle_ids)
        rich["anatomy"][field_name] = list(muscle_ids)
    profile["jointInvolvement"] = copy.deepcopy(involvement)
    rich["anatomy"]["jointInvolvement"] = copy.deepcopy(involvement)
    rich["anatomy"]["jointActions"] = derive_joint_actions(involvement)
    rich["biomechanics"]["relevantJoints"] = derive_relevant_joints(involvement)
    rich["replacement"]["preservesIntent"] = derive_preserves_intent(profile["movementPatternId"], lists["primaryMuscles"])


def render_family(
    payload: dict[str, Any],
    ficha: dict[str, Any],
    only_definitions: Iterable[str] | None = None,
) -> tuple[dict[str, Any], int]:
    """Pure: the family payload that results from applying ``ficha`` and how many configurations it touched."""
    selected = None if only_definitions is None else frozenset(only_definitions)
    rendered = copy.deepcopy(payload)
    applied = 0
    for definition in rendered["family"]["definitions"]:
        if selected is not None and definition["id"] not in selected:
            continue
        body = ficha["definitions"][definition["id"]]
        definition["description"] = body["public"]["description"]
        curated = body["status"] == STATUS_CURATED
        for configuration in definition["configurations"]:
            entry = body["public"]["configurations"][configuration["id"]]
            profile = configuration["profile"]
            profile["description"] = entry["description"]
            profile["setupCues"] = list(entry["setupCues"])
            profile["executionCues"] = list(entry["executionCues"])
            if curated:
                _apply_pattern(profile, body.get("pattern"))
                _apply_anatomy(profile, body["anatomy"], configuration["id"])
            applied += 1
    return rendered, applied


def changed_paths(old: Any, new: Any, prefix: str = "") -> list[str]:
    """Leaf paths where ``old`` and ``new`` differ (list items are labelled by id when they have one)."""
    if isinstance(old, dict) and isinstance(new, dict):
        paths: list[str] = []
        for key in sorted(set(old) | set(new)):
            child = f"{prefix}.{key}" if prefix else key
            if key not in old or key not in new:
                paths.append(child)
            else:
                paths.extend(changed_paths(old[key], new[key], child))
        return paths
    if isinstance(old, list) and isinstance(new, list):
        if len(old) != len(new):
            return [prefix]
        paths = []
        for index, (left, right) in enumerate(zip(old, new)):
            label = left.get("id") if isinstance(left, dict) and isinstance(left.get("id"), str) else index
            paths.extend(changed_paths(left, right, f"{prefix}[{label}]"))
        return paths
    return [] if old == new else [prefix]


def check_family(
    payload: dict[str, Any],
    ficha: dict[str, Any],
    only_definitions: Iterable[str] | None = None,
) -> list[str]:
    """Paths of ``payload`` that differ from what ``ficha`` produces (empty = in sync)."""
    rendered, _ = render_family(payload, ficha, only_definitions)
    return changed_paths(payload, rendered)


# ---------------------------------------------------------------------------
# Planning and CLI
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class PlannedFamily:
    path: Path
    rendered: dict[str, Any]
    drift: list[str]
    applied: int


def parse_only_definitions(raw: str | None) -> list[str] | None:
    if raw is None:
        return None
    requested = [item.strip() for item in raw.split(",")]
    if not requested or any(not item for item in requested):
        raise FichaError("--only-definitions requires at least one non-empty definition id")
    duplicates = sorted({item for item in requested if requested.count(item) > 1})
    if duplicates:
        raise FichaError(f"duplicate definition ids requested: {', '.join(duplicates)}")
    return requested


def build_plan(
    families_dir: Path,
    fichas_dir: Path,
    requested: list[str] | None = None,
) -> tuple[list[PlannedFamily], dict[str, Path]]:
    """Validate the whole selection and render it. Nothing is written here, so any
    ``FichaError`` leaves the catalog untouched."""
    files = sorted(families_dir.glob("*.json"))
    if not files:
        raise FichaError(f"no family files under {families_dir}")
    payloads = {path: load_json(path) for path in files}
    owners: dict[str, list[Path]] = {}
    for path, payload in payloads.items():
        for definition in payload["family"]["definitions"]:
            owners.setdefault(definition["id"], []).append(path)
    assignments: dict[str, Path] = {}
    if requested is not None:
        for definition_id in requested:
            matches = owners.get(definition_id, [])
            if not matches:
                raise FichaError(f"unknown definition id: {definition_id}")
            if len(matches) > 1:
                raise FichaError(f"definition id is not unique across families: {definition_id}")
            assignments[definition_id] = matches[0]
        selected_paths = sorted(set(assignments.values()), key=lambda item: item.name)
    else:
        selected_paths = files
    errors: list[str] = []
    fichas: dict[Path, dict[str, Any]] = {}
    for path in selected_paths:
        family_id = payloads[path]["family"]["id"]
        ficha_path = fichas_dir / f"{family_id}.json"
        if not ficha_path.is_file():
            errors.append(f"{family_id}: no ficha at {ficha_path.name}")
            continue
        ficha = load_json(ficha_path)
        problems = validate_ficha(payloads[path], ficha)
        errors.extend(problems)
        if not problems:
            fichas[path] = ficha
    if errors:
        shown = "\n".join(f"  - {error}" for error in errors[:25])
        more = f"\n  ... and {len(errors) - 25} more" if len(errors) > 25 else ""
        raise FichaError(f"ficha preflight failed ({len(errors)} problems), nothing was written:\n{shown}{more}")
    plan: list[PlannedFamily] = []
    for path in selected_paths:
        only = None
        if requested is not None:
            only = [definition_id for definition_id, owner in assignments.items() if owner == path]
        rendered, applied = render_family(payloads[path], fichas[path], only)
        plan.append(PlannedFamily(path, rendered, changed_paths(payloads[path], rendered), applied))
    return plan, assignments


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="catalog_v2_apply_fichas.py",
        description="Copy the fichas (public copy, anatomy, pattern) into the family source files.",
    )
    parser.add_argument(
        "--only-definitions",
        metavar="ID[,ID...]",
        default=None,
        help="Scoped mode: apply the fichas of these exact definition ids only; only the families that own them are written.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Never write: exit 1 when a family file differs from what its ficha produces.",
    )
    return parser


def main(
    argv: list[str] | None = None,
    *,
    families_dir: Path | None = None,
    fichas_dir: Path | None = None,
) -> int:
    arguments = build_parser().parse_args(argv)
    try:
        requested = parse_only_definitions(arguments.only_definitions)
        plan, assignments = build_plan(
            Path(families_dir) if families_dir is not None else FAMILIES,
            Path(fichas_dir) if fichas_dir is not None else FICHAS,
            requested,
        )
    except FichaError as error:
        raise SystemExit(str(error)) from error
    drifted = [item for item in plan if item.drift]
    mode = "check" if arguments.check else ("scoped" if requested is not None else "full")
    if not arguments.check:
        for item in drifted:
            write_family(item.path, item.rendered)
    print(f"mode={mode} families={len(plan)} configurations={sum(item.applied for item in plan)}")
    if requested is not None:
        for definition_id in requested:
            print(f"definition={definition_id} family={assignments[definition_id].name}")
    for item in drifted:
        verb = "differs" if arguments.check else "written"
        print(f"family_file={item.path.name} {verb} paths={len(item.drift)}")
        if arguments.check:
            for path in item.drift[:10]:
                print(f"  drift {path}")
    print(f"changed={len(drifted)} unchanged={len(plan) - len(drifted)}")
    return 1 if (arguments.check and drifted) else 0


if __name__ == "__main__":
    raise SystemExit(main())
