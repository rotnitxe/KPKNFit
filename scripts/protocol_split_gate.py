#!/usr/bin/env python3
"""Static gate for protocol split declarations.

The protocol compiler must resolve every declared split to an actual template;
an unknown value may not silently fall back to ``ul_x4``.
"""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROTOCOLS = ROOT / "android-native/app/src/main/java/com/example/kpkn/data/protocols/ProtocolLibrary.kt"
SPLITS = ROOT / "android-native/app/src/main/java/com/example/kpkn/data/splits/SplitTemplates.kt"
ENGINE = ROOT / "android-native/app/src/main/java/com/example/kpkn/domain/training/ProgramProtocolEngine.kt"

ALIASES = {
    "upper-lower": "ul_x4",
    "ul": "ul_x4",
    "4-day": "ul_x4",
    "4day": "ul_x4",
    "ppl": "ppl_x6",
    "fullbody": "fullbody_x3",
    "full-body": "fullbody_x3",
    "texas_method": "texas_method",
    "texas": "texas_method",
}


def matching_parentheses(text: str, opening: int) -> str:
    depth = 0
    quote = False
    escaped = False
    for index in range(opening, len(text)):
        char = text[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quote = False
            continue
        if char == '"':
            quote = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[opening + 1 : index]
    raise ValueError("unclosed Kotlin constructor")


def protocol_defaults(source: str) -> list[tuple[str, str | None]]:
    result: list[tuple[str, str | None]] = []
    for match in re.finditer(r"\bProtocol\s*\(", source):
        body = matching_parentheses(source, source.find("(", match.start()))
        protocol_id = re.search(r"\bid\s*=\s*\"([^\"]+)\"", body)
        if protocol_id is None:
            continue
        split = re.search(r"\bdefaultSplit\s*=\s*\"([^\"]+)\"", body)
        result.append((protocol_id.group(1), split.group(1) if split else None))
    return result


def main() -> int:
    failures: list[str] = []
    protocol_source = PROTOCOLS.read_text(encoding="utf-8")
    split_source = SPLITS.read_text(encoding="utf-8")
    engine_source = ENGINE.read_text(encoding="utf-8")

    split_ids = set(re.findall(r"\bSplitTemplate\s*\(\s*\"([^\"]+)\"", split_source))
    defaults = protocol_defaults(protocol_source)
    if not defaults:
        failures.append("protocol_library_empty")

    for protocol_id, declared in defaults:
        if declared is None:
            failures.append(f"missing_default_split:{protocol_id}")
            continue
        resolved = declared if declared in split_ids else ALIASES.get(declared.lower())
        if resolved not in split_ids:
            failures.append(f"unknown_default_split:{protocol_id}:{declared}")

    if "else -> raw" in engine_source:
        failures.append("silent_unknown_split_fallback")
    if 'firstOrNull { it.id == "ul_x4" }?.pattern' in engine_source:
        failures.append("silent_ul_x4_pattern_fallback")

    if failures:
        print(f"status=BLOCKED failures={len(failures)}")
        for failure in failures:
            print(f"- {failure}")
        return 2

    print(f"status=READY protocols={len(defaults)} splits={len(split_ids)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
