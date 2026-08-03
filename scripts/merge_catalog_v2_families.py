#!/usr/bin/env python3
"""Reconstruct the aggregated canonical source from the per-family review files.

The split script materializes one JSON file per family as the editorial review
surface.  This script is its deterministic inverse: it reads every
families/*.json payload and rebuilds source/catalog_v2.json with the exact
canonical serialization used by the rest of the pipeline
(json.dumps(ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").

The manifest is rewritten from the reconstructed source so split and merge are
stable round-trip operations: split -> merge -> split reproduces identical
bytes, which the gate relies on to prove the review surface did not drift.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from split_catalog_v2_source import canonical

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
MANIFEST = ROOT / "catalog" / "exercises" / "v2" / "source" / "manifest.json"


def main() -> int:
    payloads = []
    for path in sorted(FAMILIES.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        payloads.append((path.name, payload))
    if not payloads:
        raise SystemExit(f"No family files found under {FAMILIES}")
    schema_version = payloads[0][1]["schemaVersion"]
    catalog_revision = payloads[0][1]["catalogRevision"]
    ontology_revision = payloads[0][1]["ontologyRevision"]
    for name, payload in payloads[1:]:
        if payload["schemaVersion"] != schema_version:
            raise SystemExit(f"schemaVersion mismatch in {name}")
        if payload["catalogRevision"] != catalog_revision:
            raise SystemExit(f"catalogRevision mismatch in {name}")
        if payload["ontologyRevision"] != ontology_revision:
            raise SystemExit(f"ontologyRevision mismatch in {name}")

    families = [payload["family"] for _, payload in payloads]
    family_ids = [family["id"] for family in families]
    if len(family_ids) != len(set(family_ids)):
        duplicates = sorted({fid for fid in family_ids if family_ids.count(fid) > 1})
        raise SystemExit(f"Duplicate family ids: {duplicates}")
    source = {
        "schemaVersion": schema_version,
        "catalogRevision": catalog_revision,
        "ontologyRevision": ontology_revision,
        "families": sorted(families, key=lambda item: item["id"]),
    }
    source_bytes = canonical(source)
    SOURCE.write_bytes(source_bytes)
    manifest = {
        "schemaVersion": schema_version,
        "catalogRevision": catalog_revision,
        "ontologyRevision": ontology_revision,
        "aggregatedSource": SOURCE.relative_to(ROOT).as_posix(),
        "families": [f"{family_id}.json" for family_id in sorted(family_ids)],
        "aggregatedCanonicalSha256": hashlib.sha256(source_bytes).hexdigest(),
    }
    MANIFEST.write_bytes(canonical(manifest))
    print(f"families={len(families)}")
    print(f"source={SOURCE}")
    print(f"manifest={MANIFEST}")
    print(f"canonicalSha256={hashlib.sha256(source_bytes).hexdigest()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
