#!/usr/bin/env python3
"""Suggest minimal Android validation for KPKN program/session/workout flow changes."""

from __future__ import annotations

import argparse


def normalize(path: str) -> str:
    return path.replace("\\", "/").lower()


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
    android_changed = any("/android-native/" in item or item.startswith("android-native/") for item in changed)

    if not android_changed:
        print("No android-native paths detected.")
        print("No Android build validation suggested.")
        return 0

    commands: list[str] = [r"cd android-native", r".\gradlew.bat :app:compileDebugKotlin"]
    reasons: list[str] = ["Android source changed, so compileDebugKotlin is the minimum useful check."]
    manual_smoke: list[str] = []

    if any(token in path for path in changed for token in ("mainactivity.kt", "navigation.kt", "build.gradle", "build.gradle.kts")):
        commands.append(r".\gradlew.bat :app:assembleDebug")
        reasons.append("Routing or app wiring changed, so assembleDebug is safer than compile alone.")

    if any(token in path for path in changed for token in (
        "programrepository.kt",
        "/data/models/program.kt",
        "/data/models/session.kt",
        "/data/models/workoutlog.kt",
        "/data/db/",
    )):
        if r".\gradlew.bat :app:assembleDebug" not in commands:
            commands.append(r".\gradlew.bat :app:assembleDebug")
        commands.append(r".\gradlew.bat :app:testDebugUnitTest")
        reasons.append("Repository, model, or persistence surfaces changed, so expand validation beyond compile.")

    if any(token in path for path in changed for token in (
        "/screens/workout/",
        "workoutscreen.kt",
        "workoutviewmodel.kt",
        "finishworkout",
    )):
        manual_smoke.append("Start a workout, log a set, verify the rest timer, finish the workout, and confirm ongoing state clears.")

    if any(token in path for path in changed for token in (
        "/screens/sessioneditor/",
        "sessioneditorscreen.kt",
        "sessioneditorviewmodel.kt",
        "exercisedatabase.kt",
    )):
        manual_smoke.append("Open a session, add or edit an exercise and set, save, and reopen the same session branch.")

    if any(token in path for path in changed for token in (
        "/screens/programeditor/",
        "programeditorviewmodel.kt",
        "programeditorscreen.kt",
        "programcreatorwizard.kt",
        "splitselectorsheet.kt",
        "splittemplates.kt",
    )):
        manual_smoke.append("Create or edit a program, save it, and reopen it to verify structure and split changes persisted.")

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

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
