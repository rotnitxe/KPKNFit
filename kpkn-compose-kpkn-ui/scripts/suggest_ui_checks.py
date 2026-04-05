#!/usr/bin/env python3
"""Suggest minimal validation and manual review for KPKN Compose UI changes."""

from __future__ import annotations

import argparse


def normalize(path: str) -> str:
    return path.replace("\\", "/").lower()


def add_unique(items: list[str], value: str) -> None:
    if value not in items:
        items.append(value)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--changed",
        nargs="+",
        required=True,
        help="Changed files to inspect.",
    )
    args = parser.parse_args()

    changed = [normalize(item) for item in args.changed]
    android_changed = any(item.startswith("android-native/") or "/android-native/" in item for item in changed)
    docs_only = not android_changed

    commands: list[str] = []
    reasons: list[str] = []
    manual_smoke: list[str] = []

    if docs_only:
        print("Suggested checks")
        print("================")
        print()
        print("Commands:")
        print("- No Android build command required for docs-only UI work.")
        print()
        print("Why:")
        print("- No android-native files were detected in the changed set.")
        print()
        print("Manual review:")
        print("- Re-open the nearest existing KPKN screen and confirm the guidance still matches current repo reality.")
        return 0

    add_unique(commands, r"cd android-native")
    add_unique(commands, r".\gradlew.bat :app:compileDebugKotlin")
    reasons.append("Compose or Android UI files changed, so compileDebugKotlin is the minimum useful safety check.")

    if any(token in path for path in changed for token in (
        "navigation.kt",
        "mainactivity.kt",
        "androidmanifest.xml",
        "build.gradle",
        "build.gradle.kts",
    )):
        add_unique(commands, r".\gradlew.bat :app:assembleDebug")
        reasons.append("Navigation or app wiring changed, so assembleDebug is safer than compile alone.")

    if any(token in path for path in changed for token in (
        "/ui/theme/",
        "/ui/components/",
        "theme.kt",
        "color.kt",
        "type.kt",
        "sharedcomponents.kt",
    )):
        add_unique(commands, r".\gradlew.bat :app:assembleDebug")
        reasons.append("Shared UI or theme layers changed, so widen validation because multiple screens may be affected.")

    if any(token in path for path in changed for token in (
        "viewmodel.kt",
        "/data/repository/",
        "/data/models/",
        "/data/db/",
    )):
        add_unique(commands, r".\gradlew.bat :app:testDebugUnitTest")
        reasons.append("UI changes also touched state or data layers, so compile alone is not enough.")

    if any(token in path for path in changed for token in (
        "/screens/home/",
        "homescreen.kt",
        "homeheadersection.kt",
        "homeringssection.kt",
    )):
        manual_smoke.append("Check that the greeting, hero hierarchy, and rings still feel like the main focus of Home.")

    if any(token in path for path in changed for token in (
        "/screens/nutrition/",
        "nutritionscreen.kt",
        "foodloggerdrawer.kt",
        "nutritionwizardview.kt",
        "nutritionplaneditormodal.kt",
    )):
        manual_smoke.append("Check that macro progress is readable, add-food remains obvious, and dense nutrition actions still fit a phone screen.")

    if any(token in path for path in changed for token in (
        "/screens/programdetail/",
        "compactherobanner.kt",
        "blockroadmap.kt",
        "dayview.kt",
    )):
        manual_smoke.append("Check that the hero, tabs, and training structure remain legible and do not flatten into generic cards.")

    if any(token in path for path in changed for token in (
        "/screens/programeditor/",
        "/screens/sessioneditor/",
        "/screens/workout/",
    )):
        manual_smoke.append("Check that the editing or workout flow has a clear next action and is not trying to mimic a desktop editor.")

    if any(token in path for path in changed for token in (
        "/screens/wikilab/",
        "biomechanicsscreen.kt",
        "musclegroupdetailscreen.kt",
        "jointdetailscreen.kt",
        "tendondetailscreen.kt",
    )):
        manual_smoke.append("Check that information density stays readable and drill-down paths remain understandable on mobile.")

    print("Suggested checks")
    print("================")
    print()
    print("Commands:")
    for command in commands:
        print(f"- {command}")

    print()
    print("Why:")
    for reason in reasons:
        print(f"- {reason}")

    if manual_smoke:
        print()
        print("Manual review:")
        for item in manual_smoke:
            print(f"- {item}")

    print()
    print("Reminder:")
    print("- Do not judge success only by compile; confirm hierarchy, copy, and mobile ergonomics.")
    print("- If the result feels like a web transplant, the UI translation is not done yet.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
