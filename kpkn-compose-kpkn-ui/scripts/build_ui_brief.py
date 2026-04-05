#!/usr/bin/env python3
"""Build a focused brief for KPKN Compose UI work."""

from __future__ import annotations

import argparse


AREA_CONFIG = {
    "home": {
        "match": ("homescreen", "homeheader", "homerings", "homeviewmodel", "battery", "rings"),
        "pwa": [
            "components/home/",
            "services/auge.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/home/",
            "android-native/app/src/main/java/com/example/kpkn/screens/auge/",
        ],
        "goals": [
            "Keep the top of the screen emotionally strong and immediately useful.",
            "Preserve rings and readiness emphasis without turning the screen into decoration-first UI.",
            "Keep quick actions reachable and obvious.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "nutrition": {
        "match": ("nutritionscreen", "foodlogger", "mealgroup", "nutritionwizard", "nutritionplan"),
        "pwa": [
            "components/nutrition/",
            "services/aiNutritionParser.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/nutrition/",
            "android-native/app/src/main/java/com/example/kpkn/data/localai/",
        ],
        "goals": [
            "Keep macro progress prominent without making the screen feel like a spreadsheet.",
            "Preserve food logging speed and clarity.",
            "Use sheets and sections to keep dense nutrition work mobile-friendly.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "program-detail": {
        "match": ("programdetail", "compactherobanner", "blockroadmap", "dayview", "loopsview", "protocolsview"),
        "pwa": [
            "components/programs/",
            "utils/programHelpers.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/programdetail/",
        ],
        "goals": [
            "Keep the hero and structure navigation feeling powerful, not administrative.",
            "Preserve task flow from overview to drill-down to start workout.",
            "Avoid flattening training structure into undifferentiated lists.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
    "program-editor": {
        "match": ("programeditor", "programcreatorwizard", "splitselectorsheet", "sessioneditor", "workoutscreen"),
        "pwa": [
            "components/",
            "program-editor-session-workout-migration.md",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/programeditor/",
            "android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/",
            "android-native/app/src/main/java/com/example/kpkn/screens/workout/",
        ],
        "goals": [
            "Keep editing flows sectional and mobile-first.",
            "Make progress and save actions obvious.",
            "Do not force desktop editor density onto a phone screen.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
            r".\gradlew.bat :app:assembleDebug",
        ],
    },
    "wikilab": {
        "match": ("wikilab", "biomechanics", "musclegroup", "jointdetail", "tendondetail", "patterndetail"),
        "pwa": [
            "data/",
            "routes/navigation.ts",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/wikilab/",
        ],
        "goals": [
            "Keep information-rich anatomy surfaces readable on mobile.",
            "Make educational structure clear without overwhelming the user.",
            "Preserve discoverability of related drill-down paths.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "settings-profile": {
        "match": ("settingsscreen", "profilescreen"),
        "pwa": [
            "components/SettingsComponent.tsx",
            "components/",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/screens/settings/SettingsScreen.kt",
            "android-native/app/src/main/java/com/example/kpkn/screens/profile/ProfileScreen.kt",
        ],
        "goals": [
            "Keep utilitarian screens clean, clear, and native.",
            "Do not over-decorate simple management surfaces.",
            "Preserve Spanish copy clarity and easy scanning.",
        ],
        "validation": [
            r"cd android-native",
            r".\gradlew.bat :app:compileDebugKotlin",
        ],
    },
    "shared-ui": {
        "match": ("ui/theme", "sharedcomponents", "kpknsnackbar", "theme.kt", "color.kt", "type.kt"),
        "pwa": [
            "memory/CONVENTIONS.md",
        ],
        "android": [
            "android-native/app/src/main/java/com/example/kpkn/ui/theme/",
            "android-native/app/src/main/java/com/example/kpkn/ui/components/",
        ],
        "goals": [
            "Improve the shared layer without forcing a whole design-system rewrite.",
            "Keep changes compatible with existing feature-local accents.",
            "Avoid abstractions that erase KPKN screen character.",
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
        areas = ["home", "shared-ui"]

    print("KPKN Compose UI Brief")
    print("=====================")
    print()
    print("Areas:")
    for area in areas:
        print(f"- {area}")
    print()

    print_unique_block("PWA intent anchors:", areas, "pwa")
    print_unique_block("Android anchors:", areas, "android")

    print("Visual goals:")
    for area in areas:
        print(f"- [{area}]")
        for goal in AREA_CONFIG[area]["goals"]:
            print(f"  - {goal}")
    print()

    print_unique_block("Suggested validation:", areas, "validation")

    print("Review focus:")
    review_focus = [
        "primary action visibility",
        "mobile hierarchy and section rhythm",
        "Spanish copy clarity",
        "whether the result feels like KPKN rather than a web transplant",
    ]
    for item in review_focus:
        print(f"- {item}")
    print()

    if args.changed:
        print("Changed paths:")
        for item in args.changed:
            print(f"- {normalize(item)}")
        print()

    print("Reminder:")
    print("- Use the nearest local KPKN screen as the first visual reference, not a generic Compose sample.")
    print("- If the current theme layer is imperfect, improve locally before attempting a broad theme refactor.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
