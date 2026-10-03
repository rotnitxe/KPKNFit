#!/usr/bin/env python3
"""Side-effect-free authoring loop for fichas: render in memory, run the gate and the audit.

A ficha author (a person or an agent) edits ``curation/fichas/<familyId>.json`` and
runs::

    python scripts/catalog_v2_ficha_lint.py --definitions seal_row,pull_up

Nothing under ``catalog/``, ``android-native/`` or ``ios-native/`` is written. The
tool

1. validates the ficha and renders it in memory with ``catalog_v2_apply_fichas``
   (the same preflight the real applier runs, so an applicable ficha here is an
   applicable ficha there);
2. swaps the rendered families into a throw-away copy of ``source/catalog_v2.json``;
3. runs the ``catalog_v2_gate`` source gate on that copy and keeps only the failures
   that mention the selected definitions or their configurations;
4. runs ``catalog_v2_quality_audit`` (strict) scoped to the selected definitions, which
   compares their text against every CURATED definition in the catalogue.

Exit code: 0 clean, 2 blocking findings, 1 unusable input (bad ficha, unknown id).
Without ``--definitions`` every CURATED definition of every ficha is linted.

Several authors working at once each lint a private copy of the fichas, so one author's
half-written definitions never leak into another's results::

    python scripts/catalog_v2_splice_fichas.py snapshot --to <dir>
    python scripts/catalog_v2_ficha_lint.py --fichas-dir <dir> --definitions <ids> --examples 100
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from pathlib import Path
from typing import Any

try:
    import catalog_v2_apply_fichas as apply_fichas
    import catalog_v2_gate as gate
    import catalog_v2_quality_audit as audit
except ModuleNotFoundError:  # loaded by file path (tests) without scripts/ on sys.path
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import catalog_v2_apply_fichas as apply_fichas
    import catalog_v2_gate as gate
    import catalog_v2_quality_audit as audit

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
FAMILIES = apply_fichas.FAMILIES
FICHAS = apply_fichas.FICHAS

_TOKEN = re.compile(r"[^A-Za-z0-9_\-]+")


def curated_definition_ids(fichas_dir: Path) -> list[str]:
    ids: list[str] = []
    for path in sorted(fichas_dir.glob("*.json")):
        ficha = json.loads(path.read_text(encoding="utf-8"))
        for definition_id, body in (ficha.get("definitions") or {}).items():
            if isinstance(body, dict) and body.get("status") == apply_fichas.STATUS_CURATED:
                ids.append(definition_id)
    return sorted(ids)


def preview_source(source: dict[str, Any], rendered_families: list[dict[str, Any]]) -> dict[str, Any]:
    """``source`` with the rendered families swapped in (matched by family id)."""
    replacements = {family["id"]: family for family in rendered_families}
    families = [replacements.get(family["id"], family) for family in source["families"]]
    return {**source, "families": families}


def selection_ids(source: dict[str, Any], definition_ids: list[str]) -> set[str]:
    """The definition ids plus the ids of their configurations: the vocabulary of gate failures."""
    wanted = set(definition_ids)
    ids = set(wanted)
    for family in source["families"]:
        for definition in family["definitions"]:
            if definition["id"] in wanted:
                ids.update(configuration["id"] for configuration in definition["configurations"])
    return ids


def mentions(failure: str, ids: set[str]) -> bool:
    return any(token in ids for token in _TOKEN.split(failure) if token)


def gate_failures(preview_path: Path, fichas_dir: Path, ids: set[str]) -> list[str]:
    original = (gate.SOURCE, gate.FICHAS)
    gate.SOURCE, gate.FICHAS = preview_path, fichas_dir
    try:
        failures = gate.source_gate()
    finally:
        gate.SOURCE, gate.FICHAS = original
    # The audit step below reports quality findings in full detail; the gate's copy would only duplicate them.
    return [failure for failure in failures if not failure.startswith("quality_audit:") and mentions(failure, ids)]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="catalog_v2_ficha_lint.py",
        description="Preview fichas in memory and run the gate and the quality audit on them. Writes nothing in the repo.",
    )
    parser.add_argument(
        "--definitions",
        metavar="ID[,ID...]",
        help="definition ids to lint (default: every CURATED definition)",
    )
    parser.add_argument("--examples", type=int, default=3, help="print N example findings per audit check")
    parser.add_argument(
        "--fichas-dir",
        type=Path,
        help="lint the fichas of this directory (a private copy of curation/fichas) instead of the shared one",
    )
    return parser


def main(
    argv: list[str] | None = None,
    *,
    families_dir: Path | None = None,
    fichas_dir: Path | None = None,
    source_path: Path | None = None,
) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    arguments = build_parser().parse_args(argv)
    families_dir = Path(families_dir) if families_dir is not None else FAMILIES
    fichas_dir = Path(fichas_dir) if fichas_dir is not None else (arguments.fichas_dir or FICHAS)
    source_path = Path(source_path) if source_path is not None else SOURCE
    try:
        requested = apply_fichas.parse_only_definitions(arguments.definitions)
        if requested is None:
            requested = curated_definition_ids(fichas_dir)
            if not requested:
                print("no CURATED definitions to lint", file=sys.stderr)
                return 1
        plan, assignments = apply_fichas.build_plan(families_dir, fichas_dir, requested)
    except apply_fichas.FichaError as error:
        print(str(error), file=sys.stderr)
        return 1
    except (OSError, json.JSONDecodeError) as error:
        print(f"unreadable input: {error}", file=sys.stderr)
        return 1

    source = json.loads(source_path.read_text(encoding="utf-8"))
    preview = preview_source(source, [item.rendered["family"] for item in plan])
    ids = selection_ids(preview, requested)
    configurations = sum(item.applied for item in plan)
    print(f"== preview: definitions={len(requested)} families={len(plan)} configurations={configurations}")
    for definition_id in requested:
        print(f"   {definition_id} <- {assignments[definition_id].name}")

    with tempfile.TemporaryDirectory(prefix="kpkn-ficha-lint-") as directory:
        preview_path = Path(directory) / "catalog_v2.preview.json"
        preview_path.write_text(json.dumps(preview, ensure_ascii=False), encoding="utf-8")
        failures = gate_failures(preview_path, fichas_dir, ids)
        print(f"== gate (only findings about the selection): {len(failures)}")
        for failure in failures[:40]:
            print(f"   BLOCK {failure}")
        if len(failures) > 40:
            print(f"   ... and {len(failures) - 40} more")
        print("== audit")
        audit_code = audit.main(
            [
                "--source",
                str(preview_path),
                "--fichas",
                str(fichas_dir),
                "--definitions",
                ",".join(requested),
                "--strict",
                "--examples",
                str(arguments.examples),
            ]
        )
    blocked = bool(failures) or audit_code != 0
    print(f"== RESULT: {'BLOCKED' if blocked else 'OK'} (gate={len(failures)} audit_exit={audit_code})")
    return 2 if blocked else 0


if __name__ == "__main__":
    raise SystemExit(main())
