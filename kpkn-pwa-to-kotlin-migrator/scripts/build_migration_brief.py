#!/usr/bin/env python3
"""Build a short KPKN migration brief from source and target paths."""

from __future__ import annotations

import argparse
from pathlib import Path


def normalize(path_text: str) -> str:
    return str(Path(path_text)).replace("\\", "/").lstrip("./")


def classify(paths: list[str]) -> set[str]:
    kinds: set[str] = set()
    for path in paths:
        lower = path.lower()
        if any(token in lower for token in ("/services/", "/domain/", "worker", "calculate", "engine")):
            kinds.add("logic")
        if any(token in lower for token in ("/components/", "/screens/", ".tsx", ".jsx", ".kt")):
            kinds.add("ui")
        if any(token in lower for token in ("/stores/", "/hooks/", "/viewmodel", "stateflow", "/repository/")):
            kinds.add("state")
        if any(token in lower for token in ("/routes/", "/navigation/")):
            kinds.add("navigation")
        if any(token in lower for token in ("/data/", "/models/", "/db/", "room", "datastore", "assets/")):
            kinds.add("data")
        if any(token in lower for token in ("notification", "sync", "localai", "google", "supabase", "background")):
            kinds.add("integration")
    return kinds


def landing_zones(kinds: set[str]) -> list[str]:
    zones: list[str] = []
    if "logic" in kinds:
        zones.append("`android-native/.../domain/` for formulas, engines, and pure calculations")
    if "state" in kinds:
        zones.append("`android-native/.../data/repository/` + `ViewModel`/`StateFlow` for feature state")
    if "ui" in kinds:
        zones.append("`android-native/.../screens/` for feature entry points and screen-level Compose")
    if "data" in kinds:
        zones.append("`android-native/.../data/` or `assets/` for catalogs, models, persistence inputs, and seeds")
    if "navigation" in kinds:
        zones.append("`android-native/.../navigation/Navigation.kt` for route translation")
    if "integration" in kinds:
        zones.append("`android-native/.../data/repository/` or device-facing packages for native integrations")
    return zones


def preserve_items(kinds: set[str]) -> list[str]:
    items = ["feature intent", "Spanish product terminology"]
    if "logic" in kinds:
        items.extend(["critical formulas", "defaults, thresholds, and derived outputs"])
    if "state" in kinds:
        items.append("state semantics and user-visible outcomes")
    if "data" in kinds:
        items.append("data model meaning and compatibility-sensitive fields")
    if "integration" in kinds:
        items.append("business behavior of sync, AI, notifications, or background work")
    return items


def translate_items(kinds: set[str]) -> list[str]:
    items = ["layout shape", "interaction density", "navigation boundaries"]
    if "ui" in kinds:
        items.extend(["panels/drawers into native screens or sheets", "desktop layout assumptions into mobile-first structure"])
    if "state" in kinds:
        items.append("React state ownership into repository/ViewModel/StateFlow")
    if "integration" in kinds:
        items.append("browser APIs into Android-native platform equivalents")
    return items


def validation_items(kinds: set[str]) -> list[str]:
    checks = [r"cd android-native && .\gradlew.bat :app:assembleDebug"]
    if "logic" in kinds or "state" in kinds or "data" in kinds:
        checks.append(r"cd android-native && .\gradlew.bat :app:testDebugUnitTest")
    if "ui" in kinds:
        checks.append("Manual native UX pass on the migrated screen/flow")
    if "logic" in kinds:
        checks.append("Representative parity comparison against the PWA for critical outputs")
    return checks


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", nargs="+", required=True, help="PWA/source paths")
    parser.add_argument("--target", nargs="*", default=[], help="Android/target paths")
    args = parser.parse_args()

    source_paths = [normalize(path) for path in args.source]
    target_paths = [normalize(path) for path in args.target]
    kinds = classify(source_paths + target_paths)

    print("# Migration Brief")
    print()
    print("## Source")
    for path in source_paths:
        print(f"- {path}")
    if target_paths:
        print()
        print("## Target")
        for path in target_paths:
            print(f"- {path}")

    print()
    print("## Slice classification")
    for kind in sorted(kinds):
        print(f"- {kind}")

    print()
    print("## Preserve")
    for item in preserve_items(kinds):
        print(f"- {item}")

    print()
    print("## Translate")
    for item in translate_items(kinds):
        print(f"- {item}")

    print()
    print("## Likely Android landing zones")
    for zone in landing_zones(kinds):
        print(f"- {zone}")

    print()
    print("## Validation")
    for check in validation_items(kinds):
        print(f"- {check}")

    print()
    print("## Notes")
    print("- Prefer vertical slices over giant rewrites.")
    print("- Keep Compose/UI thin if logic is being ported.")
    print("- Document intentional deviations from the PWA as native adaptation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
