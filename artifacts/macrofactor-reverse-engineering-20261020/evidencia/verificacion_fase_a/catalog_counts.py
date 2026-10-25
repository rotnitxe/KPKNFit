"""Cuenta familias, definiciones, configuraciones y taxonomía del catálogo v2 de KPKN (solo lectura).

Uso: python -I catalog_counts.py <exercise_catalog_v2.json>
"""
import hashlib
import json
import sys


def main():
    path = sys.argv[1]
    raw = open(path, "rb").read()
    print("sha256", hashlib.sha256(raw).hexdigest().upper(), "bytes", len(raw))
    data = json.loads(raw.decode("utf-8"))
    print("schemaVersion", data.get("schemaVersion"), "catalogRevision", data.get("catalogRevision"))
    fams = data.get("families", [])
    defs = [d for f in fams for d in f.get("definitions", [])]
    confs = [c for d in defs for c in d.get("configurations", [])]
    print("familias", len(fams), "definiciones", len(defs), "configuraciones", len(confs))
    muscles = set()
    patterns = set()
    joints = set()
    actions = set()
    equip = set()
    perf = set()
    for c in confs:
        p = c.get("profile", {}) or {}
        patterns.add(p.get("movementPatternId"))
        equip.add(p.get("equipmentId"))
        perf.add(p.get("performanceProfileId"))
        for key in ("primaryMuscles", "secondaryMuscles", "stabilizerMuscles"):
            for m in p.get(key, []) or []:
                if isinstance(m, dict):
                    muscles.add(m.get("muscleId") or m.get("id") or json.dumps(m, sort_keys=True)[:40])
                else:
                    muscles.add(m)
        for j in p.get("jointInvolvement", []) or []:
            if isinstance(j, dict):
                joints.add(j.get("jointId"))
                a = j.get("actions")
                if isinstance(a, list):
                    actions.update(a)
                elif a is not None:
                    actions.add(a)
    print("musculos", len(muscles), sorted(str(m) for m in muscles))
    print("patrones", len(patterns))
    print("articulaciones", len(joints), "acciones distintas", len(actions))
    print("equipos", len(equip), "performanceProfileId", len(perf))
    if confs:
        print("claves profile ejemplo", sorted((confs[0].get("profile") or {}).keys()))
        ji = (confs[0].get("profile") or {}).get("jointInvolvement")
        print("ejemplo jointInvolvement", json.dumps(ji, ensure_ascii=True)[:300])
        pm = (confs[0].get("profile") or {}).get("primaryMuscles")
        print("ejemplo primaryMuscles", json.dumps(pm, ensure_ascii=True)[:200])


if __name__ == "__main__":
    main()
