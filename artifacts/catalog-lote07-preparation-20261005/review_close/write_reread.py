# -*- coding: utf-8 -*-
import json
from pathlib import Path

L7 = Path(r"C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote07-preparation-20261005")
OUT = L7 / "review_close"


def load(author, name, definition):
    ficha = json.loads((L7 / "authors" / author / "fichas" / name).read_text(encoding="utf-8"))
    return ficha["definitions"][definition]


def fmt_joints(items):
    lines = []
    for item in items or []:
        if not isinstance(item, dict):
            continue
        actions = item.get("actions") or item.get("action") or ""
        role = item.get("role") or item.get("contribution") or ""
        why = item.get("why") or ""
        lines.append(f"- {item.get('id')}: role={role} actions={actions} why={why}")
    return lines


def dump_anatomy(title, body, sink):
    sink.append(f"# {title}")
    anat = body["anatomy"]
    sink.append("## base joints")
    sink.extend(fmt_joints(anat.get("joints")))
    sink.append("## base muscles")
    sink.extend(fmt_joints(anat.get("muscles")))
    sink.append("## overrides")
    for cid, ov in (anat.get("overrides") or {}).items():
        sink.append(f"### {cid}")
        if isinstance(ov, dict):
            sink.append("joints:")
            sink.extend(fmt_joints(ov.get("joints")))
            sink.append("muscles:")
            sink.extend(fmt_joints(ov.get("muscles")))
        else:
            sink.append(str(type(ov)))
    sink.append("")


ht = load("B_supported_hip", "lower_hip_extension_hip_thrust.json", "hip_thrust")
calf = load("E_lower_leg", "lower_plantar_flexion.json", "calf_raise")
tib = load("E_lower_leg", "lower_ankle_dorsiflexion.json", "calves_tibial_anterior")
sink = ["# PUBLIC HIP THRUST", ht["public"]["description"], ""]
for cid, cfg in ht["public"]["configurations"].items():
    sink.append(f"## {cid}")
    sink.append(cfg["description"])
    sink.append("setup: " + " | ".join(cfg.get("setupCues") or []))
    sink.append("exec: " + " | ".join(cfg.get("executionCues") or []))
    sink.append("")
dump_anatomy("HIP THRUST ANATOMY", ht, sink)
dump_anatomy("CALF ANATOMY", calf, sink)
dump_anatomy("TIBIAL ANATOMY", tib, sink)
text = "\n".join(sink)
(OUT / "reread_focus.md").write_text(text, encoding="utf-8")
print("chars", len(text), "lines", text.count("\n"))
