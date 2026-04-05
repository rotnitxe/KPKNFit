#!/usr/bin/env python3
"""Compare the public PWA AUGE facade surface against Kotlin AUGE engine functions."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


EXPORT_BLOCK_RE = re.compile(r"export\s*\{(?P<body>.*?)\}\s*from", re.DOTALL)
EXPORT_DIRECT_RE = re.compile(r"export\s+(?:const|function)\s+([A-Za-z_][A-Za-z0-9_]*)")
KOTLIN_FUN_RE = re.compile(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")


def parse_ts_exports(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    names: set[str] = set()

    for match in EXPORT_BLOCK_RE.finditer(text):
        body = match.group("body")
        for raw in body.split(","):
            token = raw.strip()
            if not token:
                continue
            token = token.replace("\n", " ").strip()
            if token.startswith("type "):
                continue
            token = token.split(" as ")[0].strip()
            names.add(token)

    for match in EXPORT_DIRECT_RE.finditer(text):
        names.add(match.group(1))

    return names


def parse_kotlin_functions(paths: list[Path]) -> set[str]:
    names: set[str] = set()
    for path in paths:
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in KOTLIN_FUN_RE.finditer(text):
            names.add(match.group(1))
    return names


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--pwa-facade",
        default=r"C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\auge.ts",
        help="Path to the PWA AUGE facade.",
    )
    parser.add_argument(
        "--kotlin-files",
        nargs="*",
        default=[
            r"C:\Users\valen\Downloads\kpkn-fit-(beta-test)\android-native\app\src\main\java\com\example\kpkn\domain\auge\AugeFatigueEngine.kt",
            r"C:\Users\valen\Downloads\kpkn-fit-(beta-test)\android-native\app\src\main\java\com\example\kpkn\domain\auge\AugeRecoveryEngine.kt",
            r"C:\Users\valen\Downloads\kpkn-fit-(beta-test)\android-native\app\src\main\java\com\example\kpkn\data\repository\AugeRepository.kt",
        ],
        help="Kotlin files whose public functions should be compared.",
    )
    args = parser.parse_args()

    pwa_path = Path(args.pwa_facade)
    kotlin_paths = [Path(path) for path in args.kotlin_files]

    pwa_exports = parse_ts_exports(pwa_path)
    kotlin_functions = parse_kotlin_functions(kotlin_paths)

    matched = sorted(pwa_exports & kotlin_functions)
    pwa_only = sorted(pwa_exports - kotlin_functions)
    kotlin_only = sorted(kotlin_functions - pwa_exports)

    print(f"PWA facade: {pwa_path}")
    print("Kotlin files:")
    for path in kotlin_paths:
        print(f"- {path}")
    print()

    print(f"PWA exported symbols: {len(pwa_exports)}")
    print(f"Kotlin functions: {len(kotlin_functions)}")
    print(f"Exact-name matches: {len(matched)}")
    print()

    if matched:
        print("Exact-name matches:")
        for name in matched:
            print(f"- {name}")
        print()

    print("PWA facade symbols without an exact Kotlin function match:")
    for name in pwa_only:
        print(f"- {name}")
    print()

    print("Kotlin functions not exposed by the PWA facade:")
    for name in kotlin_only:
        print(f"- {name}")
    print()

    print("Interpretation:")
    print("- PWA-only entries may represent real parity gaps, wrapper-only APIs, async worker wrappers, or type/classifier exports.")
    print("- Kotlin-only entries may be internal/native-only helpers or renamed equivalents that still need manual review.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
