#!/usr/bin/env python3
"""Build a focused brief for KPKN parity audits."""

from __future__ import annotations

import argparse


AREA_CONFIG = {
    "navigation": {
        "match": ("navigation", "mainactivity", "route", "router", "types.ts"),
        "docs": [
            "docs/parity/pwa-rn-master-matrix.md",
            "PLAN_MAESTRO_MIGRACION.md",
        ],
        "pwa": [
            "routes/navigation.ts",
            "routes/router.ts",
            "types.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/navigation/Navigation.kt",
            "android-native/app/src/main/java/com/example/kpkn/MainActivity.kt",
        ],
        "questions": [
            "Can the same route or user entry point be reached in android-native?",
            "Are route arguments and back behavior still coherent?",
            "Is a similarly named screen hiding a missing flow?",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
    "auge": {
        "match": ("auge", "recovery", "fatigue", "readiness", "volumecalculator"),
        "docs": [
            "PLAN_MAESTRO_MIGRACION.md",
            "implementation_plan.md",
        ],
        "pwa": [
            "services/auge.ts",
            "services/fatigueService.ts",
            "services/recoveryService.ts",
            "services/volumeCalculator.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/domain/auge/",
            "android-native/app/src/main/java/com/example/kpkn/data/models/AugeModels.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/repository/AugeRepository.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/auge/AugeViewModel.kt",
        ],
        "questions": [
            "Which formulas, thresholds, or normalization rules define this slice?",
            "Does Kotlin preserve the same derived score or readiness meaning?",
            "Is any missing metadata or placeholder state distorting parity?",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "training-flow": {
        "match": ("program", "sessioneditor", "workout", "split", "macrocycle"),
        "docs": [
            "program-editor-session-workout-migration.md",
            "PLAN_MAESTRO_MIGRACION.md",
        ],
        "pwa": [
            "stores/programStore.ts",
            "stores/workoutStore.ts",
            "utils/programHelpers.ts",
            "routes/navigation.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/Program.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/programeditor/",
            "android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/",
            "android-native/app/src/main/java/com/example/kpkn/screens/workout/",
        ],
        "questions": [
            "Can the same user job be completed from program creation through workout completion?",
            "Are drafts, saves, resume paths, and history writes preserved in meaning?",
            "Did Android adapt the flow natively, or did it drop important controls?",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
    "nutrition-ai": {
        "match": ("nutrition", "foodlogger", "ainutritionparser", "localai", "foodparser"),
        "docs": [
            "MODELOS.md",
            "docs/local-ai-functiongemma-android.md",
        ],
        "pwa": [
            "components/nutrition/RegisterFoodDrawer.tsx",
            "services/aiNutritionParser.ts",
            "services/localAiService.ts",
            "tests/nutritionLoggingRegression.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/nutrition/",
            "android-native/app/src/main/java/com/example/kpkn/data/localai/",
            "android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt",
        ],
        "questions": [
            "Do parser outputs preserve estimated, unresolved, and reviewed semantics?",
            "Does the Android path still work when the local model is absent?",
            "Is saved nutrition state equivalent in meaning to the PWA result?",
        ],
        "validation": [
            r"npx tsc --noEmit",
            r"npm run test:nutrition-logging",
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "persistence": {
        "match": ("repository", "/data/db/", "entities", "daos", "settings", "storage"),
        "docs": [
            "implementation_plan.md",
            "FILES_MANIFEST.md",
        ],
        "pwa": [
            "services/storageService.ts",
            "stores/settingsStore.ts",
            "stores/programStore.ts",
            "stores/workoutStore.ts",
            "stores/nutritionStore.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/data/db/",
            "android-native/app/src/main/java/com/example/kpkn/data/repository/",
            "android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt",
        ],
        "questions": [
            "Does the stored data preserve the same meaning and lifecycle?",
            "Can the Android surface recover the saved state that the PWA would recover?",
            "Did the migration drop fields, defaults, or resume behavior?",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:assembleDebug",
            r".\gradlew.bat :app:testDebugUnitTest",
        ],
    },
    "home-dashboard": {
        "match": ("home", "rings", "dashboard", "profile"),
        "docs": [
            "PORT_SUMMARY.md",
            "PLAN_MAESTRO_MIGRACION.md",
        ],
        "pwa": [
            "components/home/",
            "components/MyRingsView.tsx",
            "components/BatteryRingCard.tsx",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/home/",
            "android-native/app/src/main/java/com/example/kpkn/screens/profile/ProfileScreen.kt",
        ],
        "questions": [
            "Are the same high-value cards, metrics, and quick actions available?",
            "Were dense web surfaces translated into usable mobile flows rather than dropped?",
            "Is any missing card or drill-down blocking the intended user task?",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "wikilab": {
        "match": ("wikilab", "exercise", "muscle", "joint", "tendon", "pattern"),
        "docs": [
            "FILES_MANIFEST.md",
        ],
        "pwa": [
            "data/",
            "routes/navigation.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/data/WikiLabPrepopulate.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/db/WikiLabDao.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/repository/WikiLabRepository.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/wikilab/",
        ],
        "questions": [
            "Is the expected anatomy and exercise detail data actually present in Kotlin?",
            "Can the same detail navigation paths be reached?",
            "Did any static catalog subset get silently truncated?",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
    "build-and-platform": {
        "match": ("build.gradle", "manifest", "mainactivity", "localai", "plugin", "permission"),
        "docs": [
            "AGENTS.md",
            "MODELOS.md",
        ],
        "pwa": [
            "capacitor.config.json",
            "manifest.json",
        ],
        "android": [
            "android-native/app/build.gradle.kts",
            "android-native/app/src/main/AndroidManifest.xml",
            "android-native/app/src/main/java/com/example/kpkn/MainActivity.kt",
        ],
        "questions": [
            "Is the Android target wired for the same capability, even if the platform API differs?",
            "Did this change alter permissions, launch paths, or native bridge behavior?",
            "Are older docs pointing at the wrong Android target?",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
}


def normalize(path: str) -> str:
    return path.replace("\\", "/")


def infer_areas(changed: list[str], explicit: list[str]) -> list[str]:
    areas = list(explicit)
    joined = "\n".join(normalize(item).lower() for item in changed)
    for area, config in AREA_CONFIG.items():
        if area in areas:
            continue
        if any(token.lower() in joined for token in config["match"]):
            areas.append(area)
    return areas


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--changed", nargs="*", default=[], help="Changed paths.")
    parser.add_argument(
        "--area",
        action="append",
        choices=sorted(AREA_CONFIG),
        default=[],
        help="Force one or more areas.",
    )
    return parser.parse_args()


def print_unique_block(title: str, areas: list[str], key: str) -> None:
    seen: set[str] = set()
    print(title)
    for area in areas:
        for item in AREA_CONFIG[area][key]:
            if item not in seen:
                print(f"- {item}")
                seen.add(item)
    print()


def main() -> int:
    args = parse_args()
    areas = infer_areas(args.changed, args.area)
    if not areas:
        areas = ["navigation", "training-flow", "persistence"]

    print("KPKN Parity Brief")
    print("=" * 17)
    print()
    print("Areas:")
    for area in areas:
        print(f"- {area}")
    print()

    print_unique_block("Historical docs to consult:", areas, "docs")
    print_unique_block("PWA anchors:", areas, "pwa")
    print_unique_block("Android anchors:", areas, "android")

    print("Audit questions:")
    for area in areas:
        print(f"- [{area}]")
        for question in AREA_CONFIG[area]["questions"]:
            print(f"  - {question}")
    print()

    print_unique_block("Suggested validation:", areas, "validation")

    print("Deliverable should include:")
    deliverable = [
        "slice and target",
        "status per sub-surface",
        "code evidence used",
        "intentional native adaptations",
        "known gaps or drifts",
        "smallest next step",
    ]
    for item in deliverable:
        print(f"- {item}")
    print()

    if args.changed:
        print("Changed paths:")
        for item in args.changed:
            print(f"- {normalize(item)}")
        print()

    print("Reminder:")
    print("- Treat apps/mobile and old RN parity docs as context only unless the task explicitly targets React Native.")
    print("- Do not call a slice parity-complete unless android-native code supports the real user job.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
