#!/usr/bin/env python3
"""Suggest the narrowest useful validation steps for KPKN AUGE changes."""

from __future__ import annotations

import argparse
from pathlib import Path


def normalize(path_text: str) -> str:
    return str(Path(path_text)).replace("\\", "/").lstrip("./")


def categorize(path_text: str) -> set[str]:
    lower = path_text.lower()
    categories: set[str] = set()

    if "/services/auge.ts" in lower:
        categories.add("pwa-facade")
    if "/services/recoveryservice.ts" in lower or "/services/fatigueservice.ts" in lower:
        categories.add("pwa-engine")
    if "/services/volumecalculator.ts" in lower:
        categories.add("pwa-volume")
    if "/services/computeworkerservice.ts" in lower:
        categories.add("pwa-worker")
    if "/domain/auge/" in lower:
        categories.add("android-engine")
    if "/data/models/augemodels.kt" in lower:
        categories.add("android-models")
    if "/data/repository/augerepository.kt" in lower:
        categories.add("android-repo")
    if "/screens/auge/" in lower:
        categories.add("android-ui")
    if "/domain/training/volumecalculator.kt" in lower:
        categories.add("android-volume")

    return categories


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", help="Changed files relevant to AUGE work.")
    args = parser.parse_args()

    normalized = [normalize(path) for path in args.paths]
    categories: set[str] = set()
    for path in normalized:
        categories |= categorize(path)

    print("Detected paths:")
    for path in normalized:
        print(f"- {path}")
    print()

    print("Detected AUGE categories:")
    for category in sorted(categories):
        print(f"- {category}")
    print()

    print("Recommended checks:")
    print(r"- cd android-native && .\gradlew.bat :app:assembleDebug")

    if {"android-engine", "android-models", "android-repo", "android-volume"} & categories:
        print(r"- cd android-native && .\gradlew.bat :app:testDebugUnitTest")

    if {"pwa-facade", "pwa-engine", "pwa-volume", "pwa-worker"} & categories:
        print("- npx tsc --noEmit")

    if {"pwa-facade", "pwa-engine", "android-engine", "android-models"} & categories:
        print("- Run representative parity spot checks for the changed behavior.")

    if {"android-repo", "android-ui"} & categories:
        print("- Manually verify the affected AUGE screen or flow after the engine-level checks.")

    if {"pwa-facade", "android-engine"} & categories:
        print("- Run scripts/compare_auge_surface.py to inspect surface drift.")

    print()
    print("Notes:")
    if "android-ui" in categories and "android-engine" not in categories:
        print("- UI-only changes do not prove engine parity; avoid claiming algorithmic fixes.")
    if "android-engine" in categories and "android-ui" not in categories:
        print("- Engine-only changes still need at least one representative consumer scenario or parity note.")
    if "pwa-worker" in categories:
        print("- Preserve compute behavior, not the Worker API shape.")
    if {"pwa-volume", "android-volume"} & categories:
        print("- Volume normalization changes can indirectly drift AUGE outputs.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
