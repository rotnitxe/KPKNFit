#!/usr/bin/env python3
"""Flag risky Android change surfaces from file paths."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


RISK_RULES = [
    ("Gradle/shared build logic", ("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts", "libs.versions.toml", "gradle.properties")),
    ("Manifest", ("AndroidManifest.xml",)),
    ("Navigation", ("navigation/", "nav_graph")),
    ("Resources", ("src/main/res/", "src/debug/res/", "src/release/res/", "/res/")),
    ("Compose UI", ("compose", "/ui/", "screen.kt", "theme.kt")),
    ("Generated code inputs", ("hilt", "dagger", "room", "schema", "databinding", "safe-args", "ksp", "kapt")),
    ("Release/shrinker", ("proguard", "r8", "consumer-rules.pro")),
    ("Tests", ("src/test/", "src/androidTest/")),
]


def get_paths(args: argparse.Namespace) -> list[str]:
    if args.paths:
        return [str(Path(path)).replace("\\", "/") for path in args.paths]
    if not args.from_git:
        return []
    result = subprocess.run(
        ["git", "diff", "--name-only", "--relative"],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line.strip().replace("\\", "/") for line in result.stdout.splitlines() if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", help="Changed file paths relative to the repo root.")
    parser.add_argument(
        "--from-git",
        action="store_true",
        help="Read changed paths from `git diff --name-only --relative`.",
    )
    args = parser.parse_args()

    paths = get_paths(args)
    if not paths:
        print("No paths provided.")
        return 0

    print("Changed paths:")
    for path in paths:
        print(f"- {path}")
    print()

    matched_any = False
    for label, needles in RISK_RULES:
        matching = [path for path in paths if any(needle.lower() in path.lower() for needle in needles)]
        if not matching:
            continue
        matched_any = True
        print(f"{label}:")
        for match in matching:
            print(f"- {match}")
        if label == "Gradle/shared build logic":
            print("- Review version catalogs, convention plugins, and the narrowest assemble path that exercises the change.")
        elif label == "Manifest":
            print("- Review merged-manifest implications, exported flags, placeholders, and flavor-specific manifests.")
        elif label == "Navigation":
            print("- Review graph registration, argument names/types, deep links, and destination ownership.")
        elif label == "Resources":
            print("- Review qualifiers, string/resource names, previews, and resource references that may break `R` generation.")
        elif label == "Compose UI":
            print("- Review state ownership, effect keys, previews, and whether logic drifted into UI code.")
        elif label == "Generated code inputs":
            print("- Review plugin wiring, annotations, schema outputs, and upstream inputs rather than generated files.")
        elif label == "Release/shrinker":
            print("- Review release-only tasks, keep rules, reflection-sensitive code, and variant packaging.")
        elif label == "Tests":
            print("- Prefer the narrowest unit or instrumentation task covering the touched path.")
        print()

    if not matched_any:
        print("No high-risk Android surfaces detected from file names alone.")
    else:
        print("Use the findings above to widen validation only where the blast radius justifies it.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
