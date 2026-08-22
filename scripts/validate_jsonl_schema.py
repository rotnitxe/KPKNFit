#!/usr/bin/env python3
"""Validate exported KPKN JSONL lines against the v2 diagnostic contract."""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

AREAS = {"voice", "workout", "nutrition", "app"}
REQUIRED = {
    "schemaVersion",
    "eventId",
    "sequence",
    "timestamp",
    "elapsedMs",
    "area",
    "event",
    "screen",
    "sessionId",
    "traceId",
    "process",
}


def iter_jsonl(paths: list[Path]):
    for root in paths:
        candidates = [root] if root.is_file() else sorted(root.rglob("*.jsonl"))
        for file in candidates:
            try:
                lines = file.read_text(encoding="utf-8").splitlines()
            except OSError as exc:
                yield file, 0, None, f"read failed: {exc}"
                continue
            for number, raw in enumerate(lines, 1):
                if not raw.strip():
                    continue
                try:
                    yield file, number, json.loads(raw), None
                except json.JSONDecodeError as exc:
                    yield file, number, None, f"invalid JSON: {exc.msg}"


def validate_event(event: object) -> list[str]:
    if not isinstance(event, dict):
        return ["line is not an object"]
    errors: list[str] = []
    missing = sorted(REQUIRED - event.keys())
    if missing:
        errors.append("missing " + ", ".join(missing))
    if event.get("schemaVersion") != 2:
        errors.append("schemaVersion must be 2")
    if event.get("area") not in AREAS:
        errors.append(f"invalid area {event.get('area')!r}")
    if "sequence" in event and (
        not isinstance(event["sequence"], int) or isinstance(event["sequence"], bool)
    ):
        errors.append("sequence must be an integer")
    for key in ("eventId", "timestamp", "event", "screen", "sessionId", "traceId", "process"):
        if key in event and not isinstance(event[key], str):
            errors.append(f"{key} must be a string")
    if "elapsedMs" in event and (not isinstance(event["elapsedMs"], int) or isinstance(event["elapsedMs"], bool)):
        errors.append("elapsedMs must be an integer")
    if event.get("process") != "main" and not str(event.get("process", "")).startswith(":"):
        errors.append("process must be main or a colon-prefixed process")

    def check_finite(value: object, path: str) -> None:
        if isinstance(value, float) and not math.isfinite(value):
            errors.append(f"non-finite number at {path}")
        elif isinstance(value, dict):
            for key, child in value.items():
                check_finite(child, f"{path}.{key}")
        elif isinstance(value, list):
            for index, child in enumerate(value):
                check_finite(child, f"{path}[{index}]")

    check_finite(event, "$")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args()
    failures = 0
    for file, line, event, error in iter_jsonl(args.paths):
        problems = [error] if error else validate_event(event)
        if problems:
            failures += 1
            print(f"{file}#L{line}: " + "; ".join(problems), file=sys.stderr)
    if failures:
        print(f"INVALID lines={failures}", file=sys.stderr)
        return 1
    print("READY schemaVersion=2")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
