#!/usr/bin/env python3
"""Re-pin the shared catalog SHA-256 after an intentional catalog change.

The hash is pinned in three places on purpose (backend test, Android audit test, STATUS.md): a catalog
edit that nobody pinned must fail loudly. This script moves the three pins in one step, and only when
the compiled assets are exactly the compile of the merged source, so a pin can never bless an unchecked
asset.

    python scripts/catalog_v2_pin_sha.py           # re-pin
    python scripts/catalog_v2_pin_sha.py --check   # exit 1 if a pin differs from the compiled hash
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
MANIFEST = ROOT / "catalog" / "exercises" / "v2" / "source" / "manifest.json"
HASH = r"[0-9a-f]{64}"

PINS: tuple[tuple[Path, re.Pattern[str]], ...] = (
    (
        ROOT / "backend" / "tests" / "test_exercises_catalog_v2.py",
        re.compile(rf'(verify_shared_catalog_artifacts\(\),\s*")({HASH})(")'),
    ),
    (
        ROOT / "android-native" / "app" / "src" / "test" / "java" / "com" / "example" / "kpkn" / "domain" / "exercises" / "catalogv2" / "AprendeCatalogAuditTest.kt",
        re.compile(rf'(assertEquals\(")({HASH})(", report\.sourceSha256\))'),
    ),
    (
        ROOT / "catalog" / "exercises" / "v2" / "curation" / "STATUS.md",
        re.compile(rf"(SHA-256 can\S+ compartido\s+`)({HASH})(`)"),
    ),
)


def compiled_sha() -> str:
    """The hash of the checked compile; refuses when the assets are stale against the merged source."""
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "compile_exercise_catalog_v2.py"), "--check"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        raise SystemExit(f"refused: compile --check failed, run merge + compile --write first\n{result.stdout}{result.stderr}")
    match = re.search(rf"canonicalSha256=({HASH})", result.stdout)
    if match is None:
        raise SystemExit("refused: compile --check did not report canonicalSha256")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("aggregatedCanonicalSha256") != match.group(1):
        raise SystemExit("refused: manifest hash differs from the compiled hash, run merge_catalog_v2_families.py")
    return match.group(1)


def read(path: Path) -> str:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return handle.read()


def write(path: Path, text: str) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        handle.write(text)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--check", action="store_true", help="only verify that the pins match")
    arguments = parser.parse_args(argv)
    sha = compiled_sha()
    stale = 0
    for path, pattern in PINS:
        text = read(path)
        matches = pattern.findall(text)
        if len(matches) != 1:
            raise SystemExit(f"refused: expected exactly one pin in {path.relative_to(ROOT)}, found {len(matches)}")
        current = matches[0][1]
        if current == sha:
            print(f"ok       {path.relative_to(ROOT)}")
            continue
        stale += 1
        if arguments.check:
            print(f"STALE    {path.relative_to(ROOT)} pins {current[:12]}..., compiled {sha[:12]}...")
            continue
        write(path, pattern.sub(lambda found: f"{found.group(1)}{sha}{found.group(3)}", text, count=1))
        print(f"re-pinned {path.relative_to(ROOT)} {current[:12]}... -> {sha[:12]}...")
    print(f"canonicalSha256={sha}")
    return 1 if arguments.check and stale else 0


if __name__ == "__main__":
    raise SystemExit(main())
