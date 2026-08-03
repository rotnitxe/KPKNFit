#!/usr/bin/env python3
"""Materialize reviewable per-family source files from the canonical pilot.

The aggregated source remains the compiler input. These files are a review
surface and are regenerated deterministically; they are never loaded by the
Android runtime directly.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
OUT = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
MANIFEST = ROOT / "catalog" / "exercises" / "v2" / "source" / "manifest.json"


def canonical(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def main() -> int:
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    files: list[str] = []
    for family in sorted(source["families"], key=lambda item: item["id"]):
        filename = f"{family['id']}.json"
        files.append(filename)
        payload = {
            "schemaVersion": source["schemaVersion"],
            "catalogRevision": source["catalogRevision"],
            "ontologyRevision": source["ontologyRevision"],
            "family": family,
        }
        (OUT / filename).write_bytes(canonical(payload))
    expected_files = set(files)
    # The split directory is a generated review surface. Prune only JSON
    # families that are no longer present in the canonical aggregate; keeping
    # one behind makes reviewers believe it is approved source and can
    # reintroduce a retired duplicate during the next curation pass.
    for existing in OUT.glob("*.json"):
        if existing.name not in expected_files:
            existing.unlink()
    manifest = {
        "schemaVersion": source["schemaVersion"],
        "catalogRevision": source["catalogRevision"],
        "ontologyRevision": source["ontologyRevision"],
        "aggregatedSource": SOURCE.relative_to(ROOT).as_posix(),
        "families": files,
        "aggregatedCanonicalSha256": hashlib.sha256(canonical(source)).hexdigest(),
    }
    MANIFEST.write_bytes(canonical(manifest))
    print(f"families={len(files)}")
    print(f"manifest={MANIFEST}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
