#!/usr/bin/env python3
"""Check physical file#line references embedded in generated Markdown reports."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REF_RE = re.compile(
    r"\[([^\[\]]+?\.jsonl)#L(\d+)(?:-L(\d+))?\]"
    r"\s*<!--\s*eventId\s*[:=]\s*([A-Za-z0-9._-]+)\s*-->"
)


def resolve_reference(root: Path, raw_path: str) -> Path:
    """Accept a flat export root and an export nested below ``KPKN/``."""
    candidates = (root / Path(raw_path), root / "KPKN" / Path(raw_path))
    return next((candidate for candidate in candidates if candidate.is_file()), candidates[0])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", type=Path)
    parser.add_argument("--root", type=Path, default=Path("."), help="export root containing referenced JSONL files")
    args = parser.parse_args()

    text = args.report.read_text(encoding="utf-8")
    failures: list[str] = []
    for match in REF_RE.finditer(text):
        raw_path, start_raw, end_raw, event_id = match.groups()
        start = int(start_raw)
        end = int(end_raw or start_raw)
        target = resolve_reference(args.root, raw_path.replace("/", "/"))
        if not target.is_file():
            failures.append(f"{raw_path}: file missing")
            continue
        lines = target.read_text(encoding="utf-8").splitlines()
        if start < 1 or end < start or end > len(lines):
            failures.append(f"{raw_path}#L{start}-L{end}: line range missing")
            continue
        if not any(event_id in lines[index - 1] for index in range(start, end + 1)):
            failures.append(f"{raw_path}#L{start}-L{end}: eventId {event_id} not found")

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        print(f"refs_invalid count={len(failures)}", file=sys.stderr)
        return 1
    print("READY refs_valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
