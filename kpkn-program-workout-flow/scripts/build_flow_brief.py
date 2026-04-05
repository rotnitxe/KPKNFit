#!/usr/bin/env python3
"""Build a focused migration/debug brief for KPKN program/session/workout tasks."""

from __future__ import annotations

import argparse


AREA_CONFIG = {
    "program-editor": {
        "match": (
            "programeditor",
            "ProgramEditor",
            "ProgramCreatorWizard",
            "SplitSelector",
            "Program.kt",
            "SplitTemplates.kt",
        ),
        "pwa": [
            "App.tsx",
            "components/ProgramEditor.tsx",
            "components/program-editor/ProgramEditorAdvanced.tsx",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/programeditor/ProgramEditorScreen.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/programeditor/ProgramEditorViewModel.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/programeditor/ProgramCreatorWizard.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/programeditor/SplitSelectorSheet.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/Program.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/splits/SplitTemplates.kt",
        ],
        "invariants": [
            "Keep existing program and nested structure IDs stable during normal edits.",
            "Do not silently wipe goals, events, or weeks when changing split or structure.",
            "Wizard output must persist as a valid Program, not just render correctly.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "session-editor": {
        "match": (
            "sessioneditor",
            "SessionEditor",
            "AdvancedExercisePicker",
            "Session.kt",
            "ExerciseDatabase.kt",
        ),
        "pwa": [
            "App.tsx",
            "components/SessionEditor.tsx",
            "components/AdvancedExercisePickerModal.tsx",
            "components/session-editor/SessionEditorHeader.tsx",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModel.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/exercises/ExerciseDatabase.kt",
        ],
        "invariants": [
            "Persist edits back into the correct program/week/session branch.",
            "Preserve sensible set defaults when cloning or adding sets.",
            "Keep exercise picker insertions compatible with ExerciseDatabase metadata.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "workout": {
        "match": (
            "workout",
            "WorkoutScreen",
            "WorkoutViewModel",
            "WorkoutSession",
            "FinishWorkout",
            "WorkoutLog.kt",
        ),
        "pwa": [
            "App.tsx",
            "components/WorkoutSession.tsx",
            "components/FinishWorkoutModal.tsx",
            "components/workout/WorkoutDrawer.tsx",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt",
        ],
        "invariants": [
            "Keep ongoing workout state and visible workout UI in sync.",
            "Do not break completed-set key semantics unless every consumer changes too.",
            "Finish flow is incomplete unless history is written and ongoing workout is cleared.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
    "wiring": {
        "match": (
            "MainActivity.kt",
            "Navigation.kt",
            "ProgramRepository.kt",
        ),
        "pwa": [
            "App.tsx",
            "program-editor-session-workout-migration.md",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/MainActivity.kt",
            "android-native/app/src/main/java/com/example/kpkn/navigation/Navigation.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt",
        ],
        "invariants": [
            "Route arguments must match route definitions and screen factories.",
            "Repository writes must remain coherent with ongoing workout and history state.",
            "Do not change navigation return behavior casually.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:assembleDebug",
            r".\gradlew.bat :app:testDebugUnitTest",
        ],
    },
}


def normalize(path: str) -> str:
    return path.replace("\\", "/")


def infer_areas(changed: list[str], explicit: list[str]) -> list[str]:
    areas = list(explicit)
    joined = "\n".join(normalize(item) for item in changed)
    for area, config in AREA_CONFIG.items():
        if area in areas:
            continue
        if any(token.lower() in joined.lower() for token in config["match"]):
            areas.append(area)
    return areas


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--changed",
        nargs="*",
        default=[],
        help="Changed files or paths that describe the task surface.",
    )
    parser.add_argument(
        "--area",
        action="append",
        choices=sorted(AREA_CONFIG),
        default=[],
        help="Force one or more flow areas into the brief.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    areas = infer_areas(args.changed, args.area)
    if not areas:
        areas = ["program-editor", "session-editor", "workout"]

    print("KPKN Program/Workout Flow Brief")
    print("=" * 32)
    print()
    print("Areas:")
    for area in areas:
        print(f"- {area}")

    print()
    print("PWA anchors:")
    seen: set[str] = set()
    for area in areas:
        for item in AREA_CONFIG[area]["pwa"]:
            if item not in seen:
                print(f"- {item}")
                seen.add(item)

    print()
    print("Android anchors:")
    seen.clear()
    for area in areas:
        for item in AREA_CONFIG[area]["android"]:
            if item not in seen:
                print(f"- {item}")
                seen.add(item)

    print()
    print("Critical invariants:")
    for area in areas:
        print(f"- [{area}]")
        for invariant in AREA_CONFIG[area]["invariants"]:
            print(f"  - {invariant}")

    print()
    print("Suggested validation:")
    seen.clear()
    for area in areas:
        for command in AREA_CONFIG[area]["validation"]:
            if command not in seen:
                print(f"- {command}")
                seen.add(command)

    if args.changed:
        print()
        print("Changed paths:")
        for item in args.changed:
            print(f"- {normalize(item)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
