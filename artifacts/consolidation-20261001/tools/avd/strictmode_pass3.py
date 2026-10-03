#!/usr/bin/env python3
"""Host-only pass-3 StrictMode aggregation (2026-10-02).

Wraps ``strictmode_report.scan`` and re-buckets DiskRead (and any other) violation blocks by *origin*:
LocaleManager, ProgramSnapshotStore, Coil/FileKeyer, WorkoutMedia* (any com.example.kpkn.*WorkoutMedia* frame),
other-kpkn (first kpkn frame elsewhere), third-party/framework-only.

    python -X utf8 strictmode_pass3.py --since 20261002T122550 --json out.json [--root <device-evidence>]

``--since`` is a UTC timestamp prefix of the evidence directory names (``YYYYMMDDTHHMMSS``); every logcat.txt under
``<root>\\{instrumentation,<driver>}\\<flavor>\\<dir>\\logcat.txt`` whose directory name sorts >= it is scanned.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import strictmode_report as sm

DEFAULT_ROOT = Path(r"C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\device-evidence")


def origin_bucket(block: dict) -> tuple[str, str]:
    """Return (bucket, first-origin-frame)."""
    origin, _entry = sm.classify(block)
    text = origin + "\n" + "\n".join(block["frames"])
    first_kpkn = next((f.split("(")[0] for f in block["frames"] if f.startswith("com.example.kpkn")), None)
    if "LocaleManager" in text:
        return "LocaleManager", origin
    if "ProgramSnapshotStore" in text:
        return "ProgramSnapshotStore", origin
    if "FileKeyer" in text or "coil" in text.lower():
        return "Coil/FileKeyer", origin
    if re.search(r"WorkoutMedia", text):
        return "WorkoutMedia*", origin
    if first_kpkn:
        return "other-kpkn", first_kpkn
    return "other-non-kpkn(" + origin.split(".")[0] + ")", origin


def scan_one(path: Path) -> dict:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    blocks = sm.parse_blocks(lines)
    by_bucket: Counter = Counter()
    by_kind_bucket: dict = defaultdict(Counter)
    samples: dict = {}
    for b in blocks:
        bk, origin = origin_bucket(b)
        by_bucket[bk] += 1
        by_kind_bucket[b["kind"]][bk] += 1
        if bk not in samples:
            samples[bk] = {"ts": b["ts"], "kind": b["kind"], "origin": origin, "frames": b["frames"][:12]}
    base = sm.scan(path)
    return {
        "path": str(path),
        "violationBlocks": len(blocks),
        "violationKinds": base["violationKinds"],
        "byBucket": dict(by_bucket),
        "byKindAndBucket": {k: dict(v) for k, v in by_kind_bucket.items()},
        "diskReadBlocks": sum(1 for b in blocks if b["kind"] == "DiskReadViolation"),
        "diskReadByBucket": dict(Counter(origin_bucket(b)[0] for b in blocks if b["kind"] == "DiskReadViolation")),
        "samples": samples,
        "signatures": {k: v["count"] for k, v in base["signatures"].items()},
        "signatureFirst": {k: v["first"] for k, v in base["signatures"].items() if v["count"]},
        "mainActivityDisplayed": sum(1 for ln in lines if "Displayed com.example.kpkn/.MainActivity" in ln),
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--since", required=True)
    ap.add_argument("--until", default="99999999T999999")
    ap.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    ap.add_argument("--json", type=Path)
    args = ap.parse_args()
    per = []
    for p in sorted(args.root.rglob("logcat.txt")):
        rel = p.relative_to(args.root)
        parts = rel.parts
        if parts[0] in ("strictmode-report", "avd-logs") or len(parts) < 3:
            continue
        run_dir = parts[-2]
        if not (args.since <= run_dir[:15] <= args.until):
            continue
        r = scan_one(p)
        r["driver"] = parts[0]
        r["flavor"] = parts[1] if len(parts) > 3 else None
        r["dir"] = run_dir
        per.append(r)
    agg: Counter = Counter()
    sig: Counter = Counter()
    disk: Counter = Counter()
    kinds: Counter = Counter()
    for r in per:
        agg.update(r["byBucket"])
        disk.update(r["diskReadByBucket"])
        sig.update(r["signatures"])
        kinds.update(r["violationKinds"])
    out = {
        "since": args.since, "until": args.until, "logcats": len(per),
        "totalViolationBlocks": sum(r["violationBlocks"] for r in per),
        "violationKinds": dict(kinds),
        "diskReadBlocks": sum(r["diskReadBlocks"] for r in per),
        "diskReadByBucket": dict(disk),
        "allKindsByBucket": dict(agg),
        "signatures": dict(sig),
        "mainActivityDisplayed": sum(r["mainActivityDisplayed"] for r in per),
        "perLogcat": per,
    }
    text = json.dumps(out, indent=2, ensure_ascii=False)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(text, encoding="utf-8")
        brief = {k: v for k, v in out.items() if k != "perLogcat"}
        sys.stdout.write(json.dumps(brief, indent=2, ensure_ascii=False) + "\n")
    else:
        sys.stdout.write(text + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
