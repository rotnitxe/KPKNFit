#!/usr/bin/env python3
"""Recover WorkoutLive* sources from agent transcripts (Write + Shell heredocs)."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\agent-transcripts")
OUT = Path(r"C:\Users\valen\Documents\KPKNFit\artifacts\live-visual-2026-08-25\_recovered")
OUT.mkdir(parents=True, exist_ok=True)

# Prefer chronological order; later overwrites earlier.
TRANSCRIPTS = sorted(ROOT.rglob("*.jsonl"), key=lambda p: p.stat().st_mtime)

wanted_suffixes = (
    "WorkoutLiveStageLayout.kt",
    "WorkoutLiveMetrics.kt",
    "WorkoutLiveMetricsTest.kt",
    "WorkoutLivePeek.kt",
    "WorkoutLiveFormat.kt",
    "WorkoutLiveTimeline.kt",
    "WorkoutLiveWorkingPages.kt",
    "WorkoutLiveCards.kt",
    "WorkoutLiveVisualTokens.kt",
    "2026-08-25_live-workout-visual-language.md",
)

recovered: dict[str, tuple[str, str]] = {}  # basename -> (source, contents)


def maybe_keep(path: str, contents: str, source: str) -> None:
    base = Path(path).name
    if not any(base.endswith(s) or base == s for s in wanted_suffixes):
        if "WorkoutLive" not in base and "live-workout-visual" not in base:
            return
    if not contents or len(contents) < 40:
        return
    prev = recovered.get(base)
    if prev and len(prev[1]) > len(contents) * 1.2 and "StageLayout" in base:
        # Prefer longer StageLayout unless source is newer ANR agent
        if "e4fba36a" not in source and "33640c69" not in source:
            return
    recovered[base] = (source, contents)
    print(f"KEEP {base} from {source} len={len(contents)}")


# Extract from Write tool_use
for tp in TRANSCRIPTS:
    with tp.open(encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                obj = json.loads(line)
            except Exception:
                continue
            content = (obj.get("message") or {}).get("content")
            if not isinstance(content, list):
                continue
            for part in content:
                if not isinstance(part, dict) or part.get("type") != "tool_use":
                    continue
                name = part.get("name")
                inp = part.get("input") or {}
                if name == "Write" and "contents" in inp:
                    maybe_keep(inp.get("path", ""), inp["contents"], f"{tp.parent.name}/{tp.name}:Write")
                if name == "Shell":
                    cmd = inp.get("command") or ""
                    # python pathlib write of content = r'''...'''
                    m = re.search(
                        r"Path\([^\)]*(WorkoutLive[^'\\\"\)]+\.kt)[^\)]*\).*?write_text\((?:content|contents|fixed|text)",
                        cmd,
                        re.S,
                    )
                    # heredoc content = r'''...''' or """..."""
                    for m2 in re.finditer(
                        r"(WorkoutLive[A-Za-z0-9_]+\.kt).*?content\s*=\s*r?'''(.*?)'''",
                        cmd,
                        re.S,
                    ):
                        maybe_keep(m2.group(1), m2.group(2), f"{tp.parent.name}/{tp.name}:ShellHeredoc")
                    for m2 in re.finditer(
                        r"(WorkoutLive[A-Za-z0-9_]+\.kt).*?content\s*=\s*r?\"\"\"(.*?)\"\"\"",
                        cmd,
                        re.S,
                    ):
                        maybe_keep(m2.group(1), m2.group(2), f"{tp.parent.name}/{tp.name}:ShellHeredoc3")
                    # plan file
                    for m2 in re.finditer(
                        r"(2026-08-25_live-workout-visual-language\.md).*?content\s*=\s*r?'''(.*?)'''",
                        cmd,
                        re.S,
                    ):
                        maybe_keep(m2.group(1), m2.group(2), f"{tp.parent.name}/{tp.name}:Plan")

# Also extract final Read tool results from e4fba36a if present
for tp in TRANSCRIPTS:
    if "e4fba36a" not in str(tp) and "33640c69" not in str(tp):
        continue
    with tp.open(encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                obj = json.loads(line)
            except Exception:
                continue
            if obj.get("role") != "tool":
                # Cursor format may nest differently
                pass
            content = (obj.get("message") or {}).get("content")
            if not isinstance(content, list):
                continue
            for part in content:
                if not isinstance(part, dict):
                    continue
                text = part.get("text") or ""
                # tool result dumps sometimes include file contents after path header
                if "WorkoutLiveStageLayout.kt" in text and "package com.example.kpkn" in text:
                    idx = text.find("package com.example.kpkn")
                    maybe_keep("WorkoutLiveStageLayout.kt", text[idx:], f"{tp.name}:ReadDump")

for base, (source, contents) in recovered.items():
    dest = OUT / base
    dest.write_text(contents.replace("\r\n", "\n"), encoding="utf-8")
    print(f"WROTE {dest} ({dest.stat().st_size} bytes) via {source}")

print("TOTAL", len(recovered))
print("FILES", sorted(recovered))
