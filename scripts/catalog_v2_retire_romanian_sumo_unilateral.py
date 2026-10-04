#!/usr/bin/env python3
"""Retire only the four rejected single-leg sumo RDL configurations.

Dry check (default):
  python scripts/catalog_v2_retire_romanian_sumo_unilateral.py --plan PLAN.json
Apply that exact checked content later:
  python scripts/catalog_v2_retire_romanian_sumo_unilateral.py --write --plan PLAN.json

Repeated --fichas-dir includes shared and/or private/integration copies. This
tool never compiles, pins, edits snapshot base hashes, or changes anatomy. The
surviving bilateral configurations keep their IDs, profiles, implement entries
and default; only the now-singleton stance selector is removed. All input and
output hashes are checked before any file is written. Reapplying an unchanged
plan is a no-op. Run the normal merge/compiler/gate/pin chain separately.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

if str(Path(__file__).resolve().parent) not in sys.path:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog_v2_apply_fichas as apply_fichas

DEFINITION = "romanian_sumo_deadlift"
REPLACEMENT_DEFINITION = "romanian_deadlift"
IMPLEMENTS = ("barbell", "smith_machine", "dumbbells", "hex_bar")
REPLACEMENTS = {
    f"{DEFINITION}__unilateral__{implement}": f"{REPLACEMENT_DEFINITION}__unilateral__{implement}"
    for implement in IMPLEMENTS
}
SURVIVORS = {f"{DEFINITION}__bilateral__{implement}" for implement in IMPLEMENTS}
PLAN_VERSION = 1


class RetirementError(ValueError):
    pass


def digest(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def transform_family(payload: dict[str, Any]) -> dict[str, Any]:
    """Pure transform; refuse extra/missing variants or an altered default."""
    updated = copy.deepcopy(payload)
    definitions = {definition["id"]: definition for definition in updated["family"]["definitions"]}
    if DEFINITION not in definitions or REPLACEMENT_DEFINITION not in definitions:
        raise RetirementError("source and replacement definitions must both exist in hinge_rdl")
    definition = definitions[DEFINITION]
    ids = [configuration["id"] for configuration in definition["configurations"]]
    if len(ids) != len(set(ids)) or set(ids) not in (SURVIVORS, SURVIVORS | set(REPLACEMENTS)):
        raise RetirementError("sumo RDL must contain exactly the four bilateral variants, with all or none of the four retired variants")
    if definition["defaultConfigurationId"] not in SURVIVORS:
        raise RetirementError("the bilateral default must be retained")
    replacements = {configuration["id"]: configuration for configuration in definitions[REPLACEMENT_DEFINITION]["configurations"]}
    for old_id, new_id in REPLACEMENTS.items():
        target = replacements.get(new_id)
        implement = old_id.rsplit("__", 1)[1]
        if target is None or target["profile"].get("equipmentId") != implement or target["profile"].get("laterality") != "UNILATERAL":
            raise RetirementError(f"replacement must preserve implement and unilateral support: {new_id}")
    if definition["optionAxes"] not in (["implement", "stance"], ["implement"]):
        raise RetirementError("unexpected sumo RDL axes")
    definition["configurations"] = [configuration for configuration in definition["configurations"] if configuration["id"] in SURVIVORS]
    definition["optionAxes"] = [axis for axis in definition["optionAxes"] if axis != "stance"]
    for configuration in definition["configurations"]:
        if configuration["profile"].get("laterality") != "BILATERAL":
            raise RetirementError(f"survivor is not bilateral: {configuration['id']}")
        selected = configuration["selectedOptions"]
        if selected.get("stance", "bilateral") != "bilateral":
            raise RetirementError(f"unexpected survivor stance: {configuration['id']}")
        selected.pop("stance", None)
        rich_display = configuration["profile"].get("richMetadata", {}).get("display", {})
        if "selectedOptions" in rich_display:
            rich_display["selectedOptions"].pop("stance", None)
        if set(selected) != {"implement"}:
            raise RetirementError(f"unexpected survivor options: {configuration['id']}")
    return updated


def transform_ficha(payload: dict[str, Any]) -> dict[str, Any]:
    updated = copy.deepcopy(payload)
    try:
        body = updated["definitions"][DEFINITION]
    except KeyError as error:
        raise RetirementError("ficha must contain romanian_sumo_deadlift") from error
    entries = (
        body.get("public", {}).get("configurations", {}),
        (body.get("anatomy") or {}).get("overrides", {}),
        (body.get("visual") or {}).get("byVariant", {}),
    )
    for entry in entries:
        for configuration_id in REPLACEMENTS:
            entry.pop(configuration_id, None)
    # The implements remain in use by the four bilateral survivors.
    return updated


def prepare(families_dir: Path, fichas_dirs: list[Path]) -> tuple[dict[str, Any], dict[Path, bytes]]:
    paths = [(families_dir / "hinge_rdl.json", transform_family)]
    paths.extend((directory / "hinge_rdl.json", transform_ficha) for directory in fichas_dirs)
    if len({path.resolve() for path, _ in paths}) != len(paths):
        raise RetirementError("duplicate source/ficha path")
    files = []
    outputs = {}
    for path, transform in paths:
        path = path.resolve()
        original = path.read_bytes()
        payload = json.loads(original.decode("utf-8"))
        transformed = transform(payload)
        # No formatting-only write on an already retired input.
        result = original if payload == transformed else apply_fichas.canonical_json(transformed)
        outputs[path] = result
        files.append({"path": str(path), "beforeSha256": digest(original), "afterSha256": digest(result), "changed": result != original})
    plan = {
        "planVersion": PLAN_VERSION,
        "definitionId": DEFINITION,
        "replacements": REPLACEMENTS,
        "survivingIds": sorted(SURVIVORS),
        "removedAxis": "stance",
        "files": files,
    }
    return plan, outputs


def apply_checked(plan: dict[str, Any]) -> int:
    """Validate every file before writing; detect stale, mixed and tampered plans."""
    if plan.get("planVersion") != PLAN_VERSION or plan.get("definitionId") != DEFINITION or plan.get("replacements") != REPLACEMENTS:
        raise RetirementError("unsupported or altered retirement plan")
    records = plan.get("files", [])
    if not records or len({record["path"] for record in records}) != len(records):
        raise RetirementError("empty or duplicate plan files")
    pending: list[tuple[Path, bytes]] = []
    for index, record in enumerate(records):
        path = Path(record["path"])
        if path.name != "hinge_rdl.json":
            raise RetirementError(f"unexpected target file: {path}")
        original = path.read_bytes()
        current = digest(original)
        if current == record["afterSha256"]:
            continue  # already applied; idempotent even after an interrupted batch
        if current != record["beforeSha256"]:
            raise RetirementError(f"input hash changed; repeat dry check: {path}")
        transformed = (transform_family if index == 0 else transform_ficha)(json.loads(original.decode("utf-8")))
        result = apply_fichas.canonical_json(transformed)
        if digest(result) != record["afterSha256"]:
            raise RetirementError(f"output hash mismatch: {path}")
        pending.append((path, result))
    for path, content in pending:
        temporary = path.with_name(f".{path.name}.retire-rdl-tmp")
        temporary.write_bytes(content)
        temporary.replace(path)
    return len(pending)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--families-dir", type=Path, default=apply_fichas.FAMILIES)
    parser.add_argument("--fichas-dir", type=Path, action="append", help="repeat for every shared/private ficha directory")
    parser.add_argument("--plan", type=Path, help="save dry-check hashes, or read approved hashes with --write")
    parser.add_argument("--write", action="store_true", help="apply an existing plan after all hash checks")
    parser.add_argument("--check", action="store_true", help="explicit dry check (also the default)")
    arguments = parser.parse_args(argv)
    try:
        if arguments.write:
            if arguments.check or arguments.plan is None:
                raise RetirementError("--write requires --plan and cannot accompany --check")
            if arguments.fichas_dir or arguments.families_dir != apply_fichas.FAMILIES:
                raise RetirementError("--write uses the exact paths recorded in --plan; omit directory arguments")
            plan = json.loads(arguments.plan.read_text(encoding="utf-8"))
            changed = apply_checked(plan)
            print(f"Applied retirement plan: {changed} files changed; 4 retired configuration mappings retained")
        else:
            plan, _ = prepare(arguments.families_dir, arguments.fichas_dir or [apply_fichas.FICHAS])
            if arguments.plan:
                if arguments.plan.resolve() in {Path(record["path"]).resolve() for record in plan["files"]}:
                    raise RetirementError("plan path must not overwrite a source/ficha")
                arguments.plan.parent.mkdir(parents=True, exist_ok=True)
                arguments.plan.write_bytes(apply_fichas.canonical_json(plan))
            print(json.dumps(plan, ensure_ascii=False, indent=2))
    except (RetirementError, OSError, KeyError, json.JSONDecodeError) as error:
        print(str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
