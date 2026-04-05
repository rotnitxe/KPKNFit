#!/usr/bin/env python3
"""Suggest minimal validation for KPKN nutrition AI changes."""

from __future__ import annotations

import argparse


def normalize(path: str) -> str:
    return path.replace("\\", "/").lower()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--changed", nargs="+", required=True, help="Changed files to inspect.")
    args = parser.parse_args()

    changed = [normalize(item) for item in args.changed]

    commands: list[str] = []
    reasons: list[str] = []
    manual_smoke: list[str] = []
    warnings: list[str] = []

    if any(token in path for path in changed for token in (
        "ainutritionparser",
        "registerfooddrawer",
        "nutritionloggingregression",
        "foodsearchservice",
        "foodparser",
    )):
        commands.append("npm run test:nutrition-logging")
        reasons.append("Parser behavior or the PWA oracle changed, so run the nutrition regression test.")

    if any("android-native/" in path for path in changed):
        commands.extend([
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ])
        reasons.append("android-native nutrition code changed, so compileDebugKotlin is the minimum Kotlin-side check.")

    if any(token in path for path in changed for token in (
        "localaimanager.kt",
        "mainactivity.kt",
        "nutritionrepository.kt",
        "/data/db/",
        "build.gradle",
        "build.gradle.kts",
    )):
        if r".\gradlew.bat :app:assembleDebug" not in commands:
            commands.append(r".\gradlew.bat :app:assembleDebug")
        reasons.append("Runtime, persistence, or build surfaces changed, so assembleDebug is safer than compile alone.")

    if any(token in path for path in changed for token in (
        "foodloggerdrawer.kt",
        "nutritionscreen.kt",
        "nutritionviewmodel.kt",
    )):
        manual_smoke.append("Open the nutrition logger, analyze a simple meal description, resolve at least one item, save, and verify the log appears.")

    if any(token in path for path in changed for token in (
        "localaimanager.kt",
        "localainutritionparserbridge.kt",
        "mainactivity.kt",
    )):
        manual_smoke.append("Check both the model-ready and no-model paths. The feature should degrade gracefully when the model is unavailable.")

    if any(token in path for path in changed for token in (
        "stagelocalaimodel.cjs",
        "checklocalaimodel.cjs",
        "modelos.md",
        "local-ai-functiongemma-android.md",
    )):
        warnings.append("Legacy docs/scripts may still target android/ or apps/mobile rather than android-native/. Verify the real asset path manually.")

    if not commands:
        print("No nutrition-AI-specific validation suggested from the provided paths.")
        return 0

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
        print("Manual smoke:")
        for item in manual_smoke:
            print(f"- {item}")

    if warnings:
        print()
        print("Warnings:")
        for item in warnings:
            print(f"- {item}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
