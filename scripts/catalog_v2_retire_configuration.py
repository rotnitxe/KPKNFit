#!/usr/bin/env python3
"""Retire a configuration (a variant the product owner rejected) from the catalogue sources.

    python scripts/catalog_v2_retire_configuration.py <configurationId> [--check]

The configuration leaves its family source (``source/families/<familyId>.json``) and every entry of
its definition ficha that names it (``public.configurations``, ``anatomy.overrides``,
``visual.byVariant``). When no remaining configuration uses its implement, the implement also
leaves ``visual.byImplement`` and ``visual.promptCore``. The tool refuses to retire the default
configuration or the last value of an option axis, so the definition stays coherent.

It is a structural change, so it only edits sources: afterwards run the usual chain
(``merge_catalog_v2_families.py``, ``compile_exercise_catalog_v2.py --write``,
``catalog_v2_gate.py --strict``, ``catalog_v2_pin_sha.py``), update the pinned counts in the
tests, and map the id in ``RETIRED_CONFIGURATION_REPLACEMENTS`` (Android resolver) so selections
saved before the retirement keep resolving.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

try:
    import catalog_v2_apply_fichas as apply_fichas
except ModuleNotFoundError:  # loaded by file path (tests) without scripts/ on sys.path
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import catalog_v2_apply_fichas as apply_fichas


class RetireError(ValueError):
    pass


def retire(configuration_id: str, families_dir: Path, fichas_dir: Path, *, check: bool = False) -> dict[str, Any]:
    owner: tuple[Path, dict[str, Any], dict[str, Any]] | None = None
    for path in sorted(families_dir.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        for definition in payload["family"]["definitions"]:
            if any(configuration["id"] == configuration_id for configuration in definition["configurations"]):
                owner = (path, payload, definition)
    if owner is None:
        raise RetireError(f"unknown configuration: {configuration_id}")
    family_path, payload, definition = owner
    if definition["defaultConfigurationId"] == configuration_id:
        raise RetireError(f"{configuration_id} is the default of {definition['id']}: choose another default first")
    retired = next(configuration for configuration in definition["configurations"] if configuration["id"] == configuration_id)
    remaining = [configuration for configuration in definition["configurations"] if configuration["id"] != configuration_id]
    for axis in definition["optionAxes"]:
        if axis in retired["selectedOptions"] and not any(axis in configuration["selectedOptions"] for configuration in remaining):
            raise RetireError(f"retiring {configuration_id} would leave axis {axis} of {definition['id']} without values")
    definition["configurations"] = remaining

    ficha_path = fichas_dir / f"{payload['family']['id']}.json"
    ficha = json.loads(ficha_path.read_text(encoding="utf-8"))
    body = ficha["definitions"][definition["id"]]
    removed = []
    if body.get("public", {}).get("configurations", {}).pop(configuration_id, None) is not None:
        removed.append("public.configurations")
    if (body.get("anatomy") or {}).get("overrides", {}).pop(configuration_id, None) is not None:
        removed.append("anatomy.overrides")
    visual = body.get("visual") or {}
    if (visual.get("byVariant") or {}).pop(configuration_id, None) is not None:
        removed.append("visual.byVariant")
    implement = retired["selectedOptions"].get("implement") or retired["profile"].get("equipmentId")
    still_used = any(
        (configuration["selectedOptions"].get("implement") or configuration["profile"].get("equipmentId")) == implement
        for configuration in remaining
    )
    if implement and not still_used:
        for block in ("byImplement", "promptCore"):
            if (visual.get(block) or {}).pop(implement, None) is not None:
                removed.append(f"visual.{block}.{implement}")
    if not check:
        apply_fichas.write_family(family_path, payload)
        ficha_path.write_bytes(apply_fichas.canonical_json(ficha))
    return {"definition": definition["id"], "family": family_path.name, "ficha": ficha_path.name, "removedFrom": removed}


def main(argv: list[str] | None = None, *, families_dir: Path | None = None, fichas_dir: Path | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(prog="catalog_v2_retire_configuration.py", description=__doc__.split("\n\n")[0])
    parser.add_argument("configuration", help="configuration id to retire")
    parser.add_argument("--check", action="store_true", help="validate and report without writing")
    arguments = parser.parse_args(argv)
    try:
        result = retire(
            arguments.configuration,
            Path(families_dir) if families_dir is not None else apply_fichas.FAMILIES,
            Path(fichas_dir) if fichas_dir is not None else apply_fichas.FICHAS,
            check=arguments.check,
        )
    except RetireError as error:
        print(str(error), file=sys.stderr)
        return 2
    verb = "would retire" if arguments.check else "retired"
    print(f"{verb} {arguments.configuration} from {result['definition']} ({result['family']}); ficha {result['ficha']}: {', '.join(result['removedFrom']) or 'nothing'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
