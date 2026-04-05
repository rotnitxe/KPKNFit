#!/usr/bin/env python3
"""Suggest narrow Android validation commands from changed paths."""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
from typing import Iterable


ROOT_BUILD_FILES = {
    "settings.gradle",
    "settings.gradle.kts",
    "build.gradle",
    "build.gradle.kts",
    "gradle.properties",
    "libs.versions.toml",
}

MODULE_BUILD_FILES = {
    "build.gradle",
    "build.gradle.kts",
    "proguard-rules.pro",
    "consumer-rules.pro",
}


def normalize_path(raw: str, cwd: Path) -> str:
    candidate = Path(raw)
    if candidate.is_absolute():
        try:
            candidate = candidate.relative_to(cwd)
        except ValueError:
            pass
    return str(candidate).replace("\\", "/").lstrip("./")


def infer_module(path_text: str) -> str | None:
    parts = [part for part in path_text.split("/") if part]
    if not parts:
        return None
    if "src" in parts:
        index = parts.index("src")
        if index > 0:
            return ":" + ":".join(parts[:index])
    if len(parts) >= 2 and parts[-1] in MODULE_BUILD_FILES:
        return ":" + ":".join(parts[:-1])
    return None


def categorize(path_text: str) -> set[str]:
    categories: set[str] = set()
    filename = Path(path_text).name

    if filename in ROOT_BUILD_FILES and "/" not in path_text:
        categories.add("root-gradle")
    if filename in MODULE_BUILD_FILES:
        categories.add("module-gradle")
    if "AndroidManifest.xml" in path_text:
        categories.add("manifest")
    if "/res/" in path_text:
        categories.add("resources")
    if "/navigation/" in path_text or "nav_graph" in filename:
        categories.add("navigation")
    if "/src/androidTest/" in path_text:
        categories.add("android-test")
    if "/src/test/" in path_text:
        categories.add("unit-test")
    if filename.endswith(".kt") or filename.endswith(".kts"):
        categories.add("kotlin")
    if "compose" in path_text.lower():
        categories.add("compose")
    if any(token in path_text.lower() for token in ("hilt", "dagger")):
        categories.add("di")
    if any(token in path_text.lower() for token in ("room", "schema")):
        categories.add("db")
    if any(token in path_text.lower() for token in ("proguard", "r8")):
        categories.add("release")
    return categories


def launcher(shell: str) -> str:
    return "gradlew.bat" if shell == "windows" else "./gradlew"


def add_recommendations(
    recommendations: dict[str, list[str]],
    module: str,
    categories: set[str],
    launch: str,
) -> None:
    first = recommendations["First checks"]
    expanded = recommendations["Expanded checks"]
    notes = recommendations["Notes"]

    if "root-gradle" in categories:
        first.append(f"{launch} :app:assembleDebug")
        expanded.append(f"{launch} test")
        notes.append("Root Gradle changes usually justify at least one representative consumer-module assemble.")
        return

    target = module or ":app"

    if {"manifest", "resources", "navigation", "compose", "module-gradle"} & categories:
        first.append(f"{launch} {target}:assembleDebug")
    elif "kotlin" in categories:
        first.append(f"{launch} {target}:compileDebugKotlin")

    if "unit-test" in categories:
        first.append(f"{launch} {target}:testDebugUnitTest")
    if "android-test" in categories:
        expanded.append(f"{launch} {target}:connectedDebugAndroidTest")
    if {"manifest", "resources", "compose"} & categories:
        expanded.append(f"{launch} {target}:lint")
    if {"release", "module-gradle"} & categories:
        expanded.append(f"{launch} {target}:assembleRelease")
    if {"di", "db", "navigation"} & categories:
        notes.append("Generated-code inputs changed; prefer assemble over compile-only tasks.")


def dedupe(items: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            ordered.append(item)
    return ordered


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", help="Changed file paths relative to the repo root.")
    parser.add_argument(
        "--shell",
        choices=("unix", "windows"),
        default="unix",
        help="Command style to print.",
    )
    args = parser.parse_args()

    cwd = Path.cwd()
    normalized = [normalize_path(raw, cwd) for raw in args.paths]
    categories_by_module: dict[str, set[str]] = defaultdict(set)
    root_categories: set[str] = set()

    for path_text in normalized:
        categories = categorize(path_text)
        module = infer_module(path_text)
        if module is None:
            root_categories |= categories
        else:
            categories_by_module[module] |= categories

    recommendations: dict[str, list[str]] = {
        "First checks": [],
        "Expanded checks": [],
        "Notes": [],
    }
    launch = launcher(args.shell)

    if root_categories:
        add_recommendations(recommendations, "", root_categories, launch)
    for module, categories in sorted(categories_by_module.items()):
        add_recommendations(recommendations, module, categories, launch)

    print("Detected change scope:")
    for path_text in normalized:
        print(f"- {path_text}")
    print()

    if root_categories:
        print(f"Root categories: {', '.join(sorted(root_categories))}")
    for module, categories in sorted(categories_by_module.items()):
        print(f"{module}: {', '.join(sorted(categories))}")
    print()

    for heading in ("First checks", "Expanded checks", "Notes"):
        values = dedupe(recommendations[heading])
        if not values:
            continue
        print(f"{heading}:")
        for value in values:
            print(f"- {value}")
        print()

    print("Adapt task names when the target module is a non-Android or custom-plugin module.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
