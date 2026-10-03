#!/usr/bin/env python3
"""Land reviewed CURATED fichas in the catalog: one command, fixed order, stops at the first failure.

    python scripts/catalog_v2_land.py --definitions pull_up,rack_chin
    python scripts/catalog_v2_land.py --definitions pull_up --no-pin     # leave the SHA pins alone

Steps (each is the documented script; this only chains them):

1. ``catalog_v2_sources.py check``     every cited source of those definitions has offline proof
2. ``catalog_v2_apply_fichas.py``      copy the ficha into ``source/families`` (copies, never writes prose)
3. ``merge_catalog_v2_families.py``    rebuild ``source/catalog_v2.json``
4. ``catalog_v2_gate.py --strict``     editorial gate (source == what the fichas produce, quality, sources)
5. ``catalog_v2_quality_audit.py``     strict audit of those definitions against the whole CURATED corpus
6. ``compile_exercise_catalog_v2.py --write``   Android asset + iOS copy
7. ``compile_exercise_catalog_v2.py --check``   the written assets are exactly the compile of the source
8. ``catalog_v2_pin_sha.py``           move the SHA-256 pins (backend test, Android audit test, STATUS.md)

Nothing is committed. A failure leaves the repository where that step left it; fix the ficha and run again.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from typing import Callable, Sequence

SCRIPTS = Path(__file__).resolve().parent
ROOT = SCRIPTS.parent

Runner = Callable[[Sequence[str]], int]


def steps(ids: str, pin: bool) -> list[tuple[str, list[str]]]:
    plan = [
        ("sources", ["catalog_v2_sources.py", "check", "--definitions", ids]),
        ("apply", ["catalog_v2_apply_fichas.py", f"--only-definitions={ids}"]),
        ("merge", ["merge_catalog_v2_families.py"]),
        ("gate", ["catalog_v2_gate.py", "--strict"]),
        ("audit", ["catalog_v2_quality_audit.py", "--strict", f"--definitions={ids}"]),
        ("compile-write", ["compile_exercise_catalog_v2.py", "--write"]),
        ("compile-check", ["compile_exercise_catalog_v2.py", "--check"]),
    ]
    if pin:
        plan.append(("pin-sha", ["catalog_v2_pin_sha.py"]))
    return plan


def run_script(command: Sequence[str]) -> int:
    return subprocess.run([sys.executable, str(SCRIPTS / command[0]), *command[1:]], cwd=ROOT).returncode


def main(argv: list[str] | None = None, *, runner: Runner = run_script) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--definitions", required=True, metavar="ID[,ID...]", help="definitions to land")
    parser.add_argument("--no-pin", action="store_true", help="do not move the SHA-256 pins")
    arguments = parser.parse_args(argv)
    ids = ",".join(item.strip() for item in arguments.definitions.split(",") if item.strip())
    if not ids:
        parser.error("--definitions needs at least one id")
    for name, command in steps(ids, pin=not arguments.no_pin):
        print(f"\n=== {name}: {' '.join(command)}", flush=True)
        code = runner(command)
        if code != 0:
            print(f"\nLAND FAILED at step '{name}' (exit {code}). Fix the ficha and run it again.", file=sys.stderr)
            return code or 1
    print(f"\nLAND OK: {ids}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
