#!/usr/bin/env python3
"""Suggest minimal validation and evidence for KPKN parity work."""

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
    pwa_changed = any(
        item.startswith(prefix)
        for item in changed
        for prefix in (
            "services/",
            "components/",
            "stores/",
            "routes/",
            "workers/",
            "data/",
            "hooks/",
            "utils/",
        )
    )
    docs_only = not android_changed and not pwa_changed

    commands: list[str] = []
    reasons: list[str] = []
    manual_evidence: list[str] = []

    if docs_only:
        print("Suggested checks")
        print("================")
        print()
        print("Commands:")
        print("- No build command required for docs-only parity work.")
        print()
        print("Why:")
        print("- The change appears to be documentation or audit notes only.")
        print()
        print("Manual evidence:")
        print("- Re-open the referenced PWA and android-native anchors before updating any parity claim.")
        print("- If a doc mentions apps/mobile parity, verify that it still applies to android-native before reusing it.")
        return 0

    if android_changed:
        add_unique(commands, r"cd android-native")
        add_unique(commands, r".\gradlew.bat :app:compileDebugKotlin")
        reasons.append("Android Kotlin files changed, so compileDebugKotlin is the minimum useful safety check.")

    if pwa_changed:
        add_unique(commands, r"npx tsc --noEmit")
        reasons.append("PWA oracle files changed, so type-checking the source side helps protect the audit baseline.")

    if any(token in path for path in changed for token in ("navigation.kt", "mainactivity.kt", "androidmanifest.xml", "build.gradle", "build.gradle.kts")):
        add_unique(commands, r".\gradlew.bat :app:assembleDebug")
        reasons.append("Navigation or app wiring changed, so assembleDebug is safer than compile alone.")
        manual_evidence.append("Verify the audited route can be entered and exited through the intended Android flow.")

    if any(token in path for path in changed for token in (
        "/data/db/",
        "entities.kt",
        "daos.kt",
        "repository/",
        "/data/models/settings.kt",
        "/data/models/program.kt",
        "/data/models/session.kt",
        "/data/models/workoutlog.kt",
        "/data/models/nutritionmodels.kt",
    )):
        add_unique(commands, r".\gradlew.bat :app:assembleDebug")
        add_unique(commands, r".\gradlew.bat :app:testDebugUnitTest")
        reasons.append("Persistence or repository surfaces changed, so widen validation beyond compile.")
        manual_evidence.append("Confirm the edited screen or repository path can still read back the saved state.")

    if any(token in path for path in changed for token in (
        "services/ainutritionparser.ts",
        "registerfooddrawer.tsx",
        "localainutritionparserbridge.kt",
        "localaimanager.kt",
        "foodloggerdrawer.kt",
        "nutritionviewmodel.kt",
        "nutritionrepository.kt",
    )):
        add_unique(commands, r"npm run test:nutrition-logging")
        reasons.append("Nutrition parser or logging semantics changed, so the regression suite is worth running.")
        manual_evidence.append("Compare unresolved, estimated, and reviewed food states across PWA and Android.")

    if any(token in path for path in changed for token in (
        "/domain/auge/",
        "augeviewmodel.kt",
        "services/auge.ts",
        "services/recoveryservice.ts",
        "services/fatigueservice.ts",
        "services/volumecalculator.ts",
    )):
        manual_evidence.append("Compare at least one representative readiness or battery scenario against the PWA oracle.")

    if any(token in path for path in changed for token in (
        "/screens/programeditor/",
        "/screens/sessioneditor/",
        "/screens/workout/",
        "programrepository.kt",
        "utils/programhelpers.ts",
        "stores/programstore.ts",
        "stores/workoutstore.ts",
    )):
        manual_evidence.append("Create or edit a program, open a session, and verify the flow still reaches the intended next step.")

    if any(token in path for path in changed for token in (
        "/screens/home/",
        "/screens/profile/",
        "components/home/",
        "myringsview.tsx",
        "batteryringcard.tsx",
    )):
        manual_evidence.append("Verify the main card or dashboard task is still accessible, not just visually similar.")

    if any(token in path for path in changed for token in (
        "/screens/wikilab/",
        "wikilabprepopulate.kt",
        "wikilabrepository.kt",
        "wikilabdao.kt",
    )):
        manual_evidence.append("Open the affected WikiLab detail path and confirm the underlying data is present.")

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

    if manual_evidence:
        print()
        print("Manual evidence:")
        for item in manual_evidence:
            print(f"- {item}")

    print()
    print("Reminder:")
    print("- Treat historical apps/mobile parity docs as hints, not proof, for android-native claims.")
    print("- Prefer a smaller honest status such as partial over a broad unsupported parity claim.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
