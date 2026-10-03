#!/usr/bin/env python3
"""Host-only StrictMode / fatal-signature scanner for archived logcat.txt files (round-2 QA, 2026-10-02).

A StrictMode *violation block* is a ``StrictMode policy violation; ...`` header line plus the stack lines
that follow it with the same pid/tid.  Android may print one logical user-code call as several blocks (each
disk access in ``getSharedPreferences`` is its own block), so the report gives both the raw block count and
the number of *distinct (pid, origin, entry)* groups.

Origin  = first ``com.example.kpkn`` frame in the stack (with class), else first third-party frame (e.g. coil3),
          else ``framework-only``.
Entry   = how the main thread got there: ``Application.attachBaseContext/onCreate`` (once per process, expected),
          ``Activity.attach`` (per Activity launch), ``Compose layout/draw`` etc.

    python -X utf8 strictmode_report.py <logcat.txt|dir> [...] [--json out.json]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

LINE = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(\S[^:]*?)\s*:\s?(.*)$")
FRAME = re.compile(r"^\s*at\s+(\S+)\((.*?)\)")
SIGNATURES = {
    "FATAL_EXCEPTION": re.compile(r"FATAL EXCEPTION"),
    "ANR": re.compile(r"\bANR in\b|Application Not Responding|am_anr"),
    "SQLiteException": re.compile(r"SQLiteException"),
    "IllegalStateException": re.compile(r"IllegalStateException"),
    "BringingDownServiceStillWaitingForStartForeground": re.compile(r"Bringing down service while still waiting for start foreground"),
    "ForegroundServiceDidNotStartInTimeException": re.compile(r"ForegroundServiceDidNotStartInTimeException"),
}


def parse_blocks(lines: list[str]) -> list[dict]:
    blocks: list[dict] = []
    current: dict | None = None
    for raw in lines:
        m = LINE.match(raw)
        if not m or m.group(5).strip() != "StrictMode":
            current = None if (m and m.group(5).strip() != "StrictMode") else current
            continue
        ts, pid, tid, _lvl, _tag, msg = m.groups()
        if msg.startswith("StrictMode policy violation"):
            kind_match = re.search(r"android\.os\.strictmode\.(\w+)", msg)
            kind = kind_match.group(1) if kind_match else msg.rsplit(":", 1)[-1].strip().split(".")[-1]
            dur = re.search(r"~duration=(\d+) ms", msg)
            current = {"ts": ts, "pid": int(pid), "tid": int(tid), "kind": kind,
                       "durationMs": int(dur.group(1)) if dur else None, "frames": []}
            blocks.append(current)
        elif current is not None and int(pid) == current["pid"] and int(tid) == current["tid"]:
            fm = FRAME.match(msg.replace("\t", " "))
            if fm:
                current["frames"].append(fm.group(1) + "(" + fm.group(2) + ")")
    return blocks


def classify(block: dict) -> tuple[str, str]:
    frames = block["frames"]
    origin = "framework-only"
    for f in frames:
        if f.startswith("com.example.kpkn"):
            origin = f.split("(")[0]
            origin = re.sub(r"\$.*?(?=\.[a-zA-Z_]+$)", "", origin)
            break
    else:
        for f in frames:
            if not f.startswith(("android.", "java.", "javax.", "libcore.", "dalvik.", "com.android.", "kotlin.", "kotlinx.",
                                 "androidx.compose.", "sun.", "jdk.")):
                origin = f.split("(")[0]
                break
    joined = "\n".join(frames)
    if "handleBindApplication" in joined or "LoadedApk.makeApplication" in joined or "Application.attachBaseContext" in joined \
            or "Instrumentation.callApplicationOnCreate" in joined:
        entry = "Application(bind/attach/onCreate: once per process)"
    elif "performLaunchActivity" in joined or "Activity.attach" in joined:
        entry = "Activity.attach/launch (per Activity launch)"
    elif "androidx.compose" in joined or "ViewRootImpl.performTraversals" in joined or "ViewGroup.dispatchGetDisplayList" in joined:
        entry = "Compose/View layout-draw (per frame/screen)"
    elif "ActivityThread$H.handleMessage" in joined:
        entry = "main looper message"
    else:
        entry = "other"
    return origin, entry


def bucket(origin: str, blocks_frames: list[str]) -> str:
    text = origin + "\n" + "\n".join(blocks_frames)
    if "LocaleManager" in text:
        return "LocaleManager"
    if "ProgramSnapshotStore" in text:
        return "ProgramSnapshotStore"
    if "FileKeyer" in text:
        return "FileKeyer/Coil"
    if "coil" in text.lower():
        return "FileKeyer/Coil"
    return "other"


def scan(path: Path) -> dict:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    blocks = parse_blocks(lines)
    by_bucket: Counter = Counter()
    by_bucket_entry: dict = defaultdict(Counter)
    groups: dict = defaultdict(lambda: {"blocks": 0, "samples": []})
    kinds: Counter = Counter()
    for b in blocks:
        origin, entry = classify(b)
        bk = bucket(origin, b["frames"])
        by_bucket[bk] += 1
        by_bucket_entry[bk][entry] += 1
        kinds[b["kind"]] += 1
        key = (b["pid"], bk, origin, entry)
        g = groups[key]
        g["blocks"] += 1
        if len(g["samples"]) < 1:
            g["samples"].append({"ts": b["ts"], "kind": b["kind"], "durationMs": b["durationMs"], "frames": b["frames"][:14]})
    sigs = {}
    for name, rx in SIGNATURES.items():
        hits = []
        for i, ln in enumerate(lines):
            if rx.search(ln):
                if name == "IllegalStateException":
                    ctx = " ".join(lines[i:i + 14])
                    if "com.example.kpkn" not in ctx:
                        continue
                hits.append(ln[:240])
        sigs[name] = {"count": len(hits), "first": hits[:3]}
    return {
        "path": str(path), "lines": len(lines),
        "strictModeLinesRaw": sum(1 for ln in lines if " StrictMode: " in ln),
        "violationBlocks": len(blocks), "violationKinds": dict(kinds),
        "diskReadBlocksByOrigin": {k: v for k, v in by_bucket.items()},
        "byOriginAndEntry": {k: dict(v) for k, v in by_bucket_entry.items()},
        "distinctProcessOriginEntryGroups": [
            {"pid": k[0], "bucket": k[1], "origin": k[2], "entry": k[3], "blocks": v["blocks"], "sample": v["samples"][0]}
            for k, v in sorted(groups.items(), key=lambda kv: -kv[1]["blocks"])
        ],
        "signatures": sigs,
    }


def collect(paths: list[str]) -> list[Path]:
    out: list[Path] = []
    for p in paths:
        path = Path(p)
        if path.is_dir():
            out.extend(sorted(path.rglob("logcat.txt")))
        elif path.exists():
            out.append(path)
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--json", type=Path)
    ap.add_argument("--brief", action="store_true", help="omit per-group samples")
    args = ap.parse_args(argv)
    results = [scan(p) for p in collect(args.paths)]
    if args.brief:
        for r in results:
            for g in r["distinctProcessOriginEntryGroups"]:
                g.pop("sample", None)
    text = json.dumps(results, indent=2, ensure_ascii=False)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
