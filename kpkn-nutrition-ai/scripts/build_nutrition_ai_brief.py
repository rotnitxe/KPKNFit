#!/usr/bin/env python3
"""Build a focused brief for KPKN nutrition AI work."""

from __future__ import annotations

import argparse


AREA_CONFIG = {
    "food-logger": {
        "match": ("FoodLoggerDrawer", "RegisterFoodDrawer", "nutritionscreen", "nutritionviewmodel"),
        "pwa": [
            "components/nutrition/RegisterFoodDrawer.tsx",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/nutrition/components/FoodLoggerDrawer.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionScreen.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionViewModel.kt",
        ],
        "invariants": [
            "Keep typing responsive and analysis explicit.",
            "Do not save raw parsed items directly; save resolved LoggedFood output.",
            "Make estimated and unresolved items honest in the UI.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "parser-bridge": {
        "match": ("aiNutritionParser", "LocalAiNutritionParserBridge", "FoodParser", "parseFreeFormNutrition"),
        "pwa": [
            "services/aiNutritionParser.ts",
            "tests/nutritionLoggingRegression.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/data/localai/LocalAiNutritionParserBridge.kt",
            "android-native/app/src/main/java/com/example/kpkn/domain/nutrition/FoodParser.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt",
        ],
        "invariants": [
            "Keep deterministic fallback available.",
            "Protect analysisEngine/modelVersion/requiresReview semantics.",
            "Avoid duplicate-or-dropped food merge bugs.",
        ],
        "validation": [
            r"npm run test:nutrition-logging",
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "local-runtime": {
        "match": ("LocalAiManager", "localAiService", "stageLocalAiModel", "checkLocalAiModel", "MainActivity"),
        "pwa": [
            "services/localAiService.ts",
            "MODELOS.md",
            "docs/local-ai-functiongemma-android.md",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/data/localai/LocalAiManager.kt",
            "android-native/app/src/main/java/com/example/kpkn/MainActivity.kt",
            "android-native/app/build.gradle.kts",
        ],
        "invariants": [
            "No-model behavior must still allow nutrition logging.",
            "Model-loading paths must match the app you are editing.",
            "Keep init idempotence, timeouts, and thread safety.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
    "persistence": {
        "match": ("NutritionRepository", "NutritionModels", "Entities", "Daos"),
        "pwa": [
            "components/nutrition/RegisterFoodDrawer.tsx",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/db/Entities.kt",
            "android-native/app/src/main/java/com/example/kpkn/data/db/Daos.kt",
        ],
        "invariants": [
            "NutritionLog persistence must remain write-through and reactive.",
            "LoggedFood values should reflect the final reviewed item state.",
            "Do not break Room-backed nutrition state while editing parser/UI code.",
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
    joined = "\n".join(normalize(item) for item in changed)
    for area, config in AREA_CONFIG.items():
        if area in areas:
            continue
        if any(token.lower() in joined.lower() for token in config["match"]):
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


def main() -> int:
    args = parse_args()
    areas = infer_areas(args.changed, args.area)
    if not areas:
        areas = ["food-logger", "parser-bridge", "local-runtime"]

    print("KPKN Nutrition AI Brief")
    print("=" * 24)
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

    print()
    print("Reminder:")
    print("- Verify whether any model-staging script or doc still targets legacy android/ instead of android-native/.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
