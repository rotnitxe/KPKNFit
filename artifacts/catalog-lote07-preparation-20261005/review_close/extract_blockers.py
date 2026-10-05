# -*- coding: utf-8 -*-
"""Read-only extraction for the L7 close. Does not edit author bodies."""
import hashlib
import json
from pathlib import Path

ROOT = Path(r"C:\Users\valen\Documents\KPKNFit")
L7 = ROOT / "artifacts" / "catalog-lote07-preparation-20261005"
OUT = L7 / "review_close"
AUTHORS = {
    "A_kickbacks": L7 / "authors" / "A_kickbacks" / "HASH_MANIFEST.json",
    "B_supported_hip": L7 / "authors" / "B_supported_hip" / "HASH_MANIFEST.json",
    "C_abduction": L7 / "authors" / "C_abduction" / "HASH_MANIFEST.json",
    "D_adduction": L7 / "authors" / "D_adduction" / "hash_manifest.json",
    "E_lower_leg": L7 / "authors" / "E_lower_leg" / "hash_manifest.json",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def validate(name: str, manifest_path: Path) -> dict:
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    files = data.get("files") or {}
    missing = []
    mismatch = []
    ok = 0
    base = manifest_path.parent
    for rel, expected in files.items():
        path = base / rel
        if not path.is_file():
            missing.append(rel)
            continue
        actual = sha256(path)
        if actual != expected:
            mismatch.append({"path": rel, "expected": expected, "actual": actual})
        else:
            ok += 1
    return {
        "author": name,
        "manifest": str(manifest_path.relative_to(L7)),
        "manifestSha256": sha256(manifest_path),
        "listed": len(files),
        "ok": ok,
        "missing": missing,
        "mismatch": mismatch,
    }


def walk_text(node, prefix, sink):
    if isinstance(node, dict):
        for key, value in node.items():
            walk_text(value, f"{prefix}.{key}" if prefix else key, sink)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            walk_text(value, f"{prefix}[{index}]", sink)
    elif isinstance(node, str) and len(node) > 40:
        sink.append(f"\n## {prefix} ({len(node)} chars)\n{node}\n")


def main() -> None:
    reports = [validate(name, path) for name, path in AUTHORS.items()]
    (OUT / "manifest_validation.json").write_text(
        json.dumps(reports, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    for report in reports:
        print(
            f"{report['author']}: listed={report['listed']} ok={report['ok']} "
            f"missing={len(report['missing'])} mismatch={len(report['mismatch'])}"
        )

    pngs = []
    for path in (L7 / "authors").rglob("visual_queue.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        rows = data if isinstance(data, list) else data.get("items") or data.get("queue") or []
        if isinstance(data, dict) and not rows:
            rows = [data]
        for row in rows:
            if not isinstance(row, dict):
                continue
            for key in ("pngMappedByExactImplement", "pngs", "images"):
                for item in row.get(key) or []:
                    if isinstance(item, dict) and item.get("path"):
                        pngs.append(
                            {
                                "queue": str(path.relative_to(L7)),
                                "configuration": row.get("configuration") or row.get("id"),
                                "path": item["path"],
                                "sha256": item.get("sha256"),
                                "existsFlag": item.get("exists"),
                            }
                        )
    (OUT / "png_index.json").write_text(json.dumps(pngs, ensure_ascii=False, indent=2), encoding="utf-8")
    unique = {}
    for row in pngs:
        unique.setdefault(row["path"], row["sha256"])
    print(f"png rows={len(pngs)} unique={len(unique)}")

    targets = {
        "hip_thrust": L7 / "authors" / "B_supported_hip" / "fichas" / "lower_hip_extension_hip_thrust.json",
        "calf": L7 / "authors" / "E_lower_leg" / "fichas" / "lower_plantar_flexion.json",
        "tibial": L7 / "authors" / "E_lower_leg" / "fichas" / "lower_ankle_dorsiflexion.json",
    }
    for label, path in targets.items():
        body_file = json.loads(path.read_text(encoding="utf-8"))
        sink = [f"# {label} full long strings from {path.name}\n"]
        walk_text(body_file, "", sink)
        text = "".join(sink)
        (OUT / f"reread_{label}.md").write_text(text, encoding="utf-8")
        print(f"{label} dump chars={len(text)}")


if __name__ == "__main__":
    main()
