#!/usr/bin/env python3
"""Private copies of the fichas for parallel authors, and the splice back into the shared fichas.

Several authors (people or agents) curate at once. Each one works on a private copy, so a
half-written definition never leaks into somebody else's lint::

    python scripts/catalog_v2_splice_fichas.py snapshot --to <dir>
    # edit <dir>/<familyId>.json, lint with: catalog_v2_ficha_lint.py --fichas-dir <dir> ...
    python scripts/catalog_v2_splice_fichas.py splice --from <dir> --definitions a,b

``snapshot`` copies every ficha and records, per definition, the hash of its body at copy time.
With ``--into <dir>`` both commands act on another fichas directory instead of the shared one: the
coordinator gathers several private copies into one integration copy, lints it as a whole and only
splices into the shared fichas once the lote is approved.

``splice`` copies the requested definition bodies from the private copy into the shared fichas. It
refuses (and writes nothing) when a shared body changed after the snapshot, because somebody else
touched it, unless ``--force``. Touched files are written as canonical JSON (indent 2, sorted keys,
UTF-8, LF). ``--check`` reports what would change without writing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FICHAS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "fichas"
# No .json suffix: every catalog tool globs *.json in a fichas directory and must not mistake it for a ficha.
BASE_NAME = ".splice_base"


class SpliceError(ValueError):
    pass


def body_hash(body: Any) -> str:
    canonical = json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def canonical_text(payload: Any) -> str:
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, indent=2) + "\n"


def load_fichas(directory: Path) -> dict[Path, dict[str, Any]]:
    return {path: json.loads(path.read_text(encoding="utf-8")) for path in sorted(directory.glob("*.json"))}


def definition_index(fichas: dict[Path, dict[str, Any]]) -> dict[str, Path]:
    index: dict[str, Path] = {}
    for path, ficha in fichas.items():
        for definition_id in (ficha.get("definitions") or {}):
            if definition_id in index:
                raise SpliceError(f"definition {definition_id} appears in {index[definition_id].name} and {path.name}")
            index[definition_id] = path
    return index


def snapshot(shared: Path, private: Path, *, force: bool = False) -> int:
    if private.exists() and any(private.glob("*.json")) and not force:
        raise SpliceError(f"{private} already holds fichas; pass --force to overwrite the snapshot")
    private.mkdir(parents=True, exist_ok=True)
    fichas = load_fichas(shared)
    base: dict[str, dict[str, str]] = {}
    for path, ficha in fichas.items():
        shutil.copyfile(path, private / path.name)
        for definition_id, body in (ficha.get("definitions") or {}).items():
            base[definition_id] = {"file": path.name, "sha256": body_hash(body)}
    (private / BASE_NAME).write_text(canonical_text({"source": str(shared), "definitions": base}), encoding="utf-8", newline="\n")
    return len(fichas)


def splice(shared: Path, private: Path, definition_ids: list[str], *, force: bool = False, check: bool = False) -> list[str]:
    base_path = private / BASE_NAME
    if not base_path.is_file():
        raise SpliceError(f"{private} has no {BASE_NAME}: create it with `snapshot`")
    base = json.loads(base_path.read_text(encoding="utf-8"))["definitions"]
    shared_fichas = load_fichas(shared)
    private_fichas = load_fichas(private)
    shared_index = definition_index(shared_fichas)
    private_index = definition_index(private_fichas)
    problems: list[str] = []
    for definition_id in definition_ids:
        if definition_id not in private_index:
            problems.append(f"{definition_id}: not in the private copy")
        elif definition_id not in shared_index:
            problems.append(f"{definition_id}: not in the shared fichas")
        elif private_index[definition_id].name != shared_index[definition_id].name:
            problems.append(f"{definition_id}: lives in {private_index[definition_id].name} privately but in {shared_index[definition_id].name}")
        elif definition_id not in base:
            problems.append(f"{definition_id}: missing from the snapshot base")
        else:
            current = body_hash(shared_fichas[shared_index[definition_id]]["definitions"][definition_id])
            if current != base[definition_id]["sha256"] and not force:
                problems.append(f"{definition_id}: the shared body changed after the snapshot (someone else edited it); use --force only if that edit may be overwritten")
    if problems:
        raise SpliceError("splice refused, nothing was written:\n" + "\n".join(f"  - {problem}" for problem in problems))
    changed: list[str] = []
    touched: set[Path] = set()
    for definition_id in definition_ids:
        shared_path = shared_index[definition_id]
        new_body = private_fichas[private_index[definition_id]]["definitions"][definition_id]
        if shared_fichas[shared_path]["definitions"][definition_id] != new_body:
            shared_fichas[shared_path]["definitions"][definition_id] = new_body
            touched.add(shared_path)
            changed.append(definition_id)
    if not check:
        for path in sorted(touched):
            path.write_text(canonical_text(shared_fichas[path]), encoding="utf-8", newline="\n")
    return changed


def main(argv: list[str] | None = None, *, fichas_dir: Path | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(prog="catalog_v2_splice_fichas.py", description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)
    snap = commands.add_parser("snapshot", help="copy the shared fichas into a private directory")
    snap.add_argument("--to", required=True, type=Path)
    snap.add_argument("--force", action="store_true", help="overwrite an existing snapshot")
    put = commands.add_parser("splice", help="copy definition bodies from a private copy into the shared fichas")
    put.add_argument("--from", dest="source", required=True, type=Path)
    put.add_argument("--definitions", required=True, help="comma-separated definition ids")
    put.add_argument("--force", action="store_true", help="overwrite shared bodies that changed after the snapshot")
    put.add_argument("--check", action="store_true", help="report what would change and write nothing")
    for command in (snap, put):
        command.add_argument(
            "--into",
            type=Path,
            help="act on this fichas directory instead of the shared one (an integration copy that gathers several authors)",
        )
    arguments = parser.parse_args(argv)
    shared = Path(fichas_dir) if fichas_dir is not None else (arguments.into or FICHAS)
    try:
        if arguments.command == "snapshot":
            count = snapshot(shared, arguments.to, force=arguments.force)
            print(f"snapshot: {count} fichas -> {arguments.to}")
            return 0
        requested = [item.strip() for item in arguments.definitions.split(",") if item.strip()]
        if not requested:
            parser.error("--definitions needs at least one id")
        changed = splice(shared, arguments.source, requested, force=arguments.force, check=arguments.check)
    except SpliceError as error:
        print(str(error), file=sys.stderr)
        return 2
    verb = "would change" if arguments.check else "spliced"
    print(f"{verb}: {len(changed)} of {len(requested)} definitions" + (f" ({', '.join(changed)})" if changed else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
