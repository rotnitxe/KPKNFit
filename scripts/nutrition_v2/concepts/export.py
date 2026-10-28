"""Exporta concepts_export.json (F2 §6): python -I export.py --concepts concepts.jsonl --ambiguous ambiguous.jsonl
--pack paquete.sqlite --out salida.json [--macros-only] [--gzip]. Solo biblioteca estandar."""
import argparse
import gzip
import hashlib
import json
import os
import re
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "catalog"))
import nutrients  # noqa: E402

MACROS = ("energy", "protein", "fat", "carbs", "fiber", "sugars", "sodium")
NUTRIENT_ID_KT = os.path.normpath(os.path.join(
    HERE, "..", "..", "..", "android-native", "app", "src", "main", "java", "com", "example", "kpkn",
    "domain", "nutrition", "nutrients", "NutrientId.kt"))


def check_nutrient_keys(kt_path=NUTRIENT_ID_KT):
    """Falla (ValueError) si NUTRIENTS no coincide con NutrientId.kt. Sin archivo Kotlin: se omite."""
    if not os.path.exists(kt_path):
        return False
    with open(kt_path, encoding="utf-8") as f:
        found = re.findall(r'^\s*[A-Z0-9_]+\((\d+),\s*"([a-z0-9_]+)"', f.read(), re.M)
    kt = [(int(i), k) for i, k in found]
    py = [(n.id, n.key) for n in nutrients.NUTRIENTS]
    if kt != py:
        raise ValueError("NUTRIENTS no coincide con NutrientId.kt: %s" % sorted(set(kt) ^ set(py)))
    return True


def read_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def load_records(db, refs, keys):
    """{ref: ficha} para las fichas pedidas; faltantes -> ausentes del resultado."""
    key_of = {n.id: n.key for n in nutrients.NUTRIENTS}
    out = {}
    for rid in sorted(refs):
        row = db.execute("SELECT name, density_g_per_ml FROM record WHERE id=?", (rid,)).fetchone()
        if row is None:
            continue
        per100 = {}
        for nid, amount in db.execute(
                "SELECT nutrient_id, amount FROM nutrient_value WHERE record_id=? ORDER BY nutrient_id", (rid,)):
            k = key_of.get(nid)
            if k is None or (keys is not None and k not in keys) or amount is None:
                continue
            per100[k] = amount
        portions = [{"label": (le or l), "grams": g} for l, le, g in db.execute(
            "SELECT label, label_es, grams FROM portion WHERE record_id=? AND grams IS NOT NULL ORDER BY seq", (rid,))]
        out[rid] = {"name": row[0], "per100g": per100, "portions": portions, "densityGPerMl": row[1]}
    return out


def derive(concept, records, keys):
    """Ficha derivada de un plato por componentes, o None si ningun componente tiene ficha."""
    comps = concept.get("components") or []
    total = sum(c["grams"] for c in comps)
    with_rec = [c for c in comps if c.get("ref") and not c.get("noMatch")]
    if not with_rec or total <= 0:
        return None
    per100 = {}
    all_keys = set.intersection(*[set(records[c["ref"]]["per100g"]) for c in with_rec])
    for k in sorted(all_keys):
        if keys is not None and k not in keys:
            continue
        per100[k] = round(sum(c["grams"] * records[c["ref"]]["per100g"][k] / 100.0 for c in with_rec) / total * 100.0, 4)
    return {"name": concept.get("name") or concept["id"], "per100g": per100, "portions": [], "densityGPerMl": None}


def build(concepts, ambiguous, db, concepts_sha, macros_only=False):
    """Devuelve (export, informe). Lanza ValueError si hay refs inexistentes."""
    keys = set(MACROS) if macros_only else None
    refs, order = set(), []
    for c in concepts:
        for v in c.get("variants") or []:
            if v.get("ref") and not v.get("noMatch"):
                refs.add(v["ref"])
        for k in c.get("components") or []:
            if k.get("ref"):
                refs.add(k["ref"])
    records = load_records(db, refs, keys)
    missing = sorted(refs - set(records))
    if missing:
        raise ValueError("refs inexistentes en el paquete (%d): %s" % (len(missing), ", ".join(missing[:20])))
    # las derivadas necesitan el vector completo de los componentes solo para las claves pedidas
    derived, warnings, no_match_components = 0, [], 0
    out_concepts = []
    for c in concepts:
        c = json.loads(json.dumps(c))
        comps = c.get("components") or []
        no_match_components += sum(1 for k in comps if k.get("noMatch") or not k.get("ref"))
        has_variant_ref = any(v.get("ref") and not v.get("noMatch") for v in c.get("variants") or [])
        if comps and not has_variant_ref:
            rec = derive(c, records, keys)
            if rec is not None:
                records["kpkn_" + c["id"]] = rec
                c["variants"] = list(c.get("variants") or []) + [
                    {"state": None, "form": "NA", "query": "", "ref": "kpkn_" + c["id"]}]
                derived += 1
                has_variant_ref = True
        if not has_variant_ref:
            warnings.append("concepto sin ninguna ficha: %s" % c["id"])
        out_concepts.append(c)
    export = {"generation": (db.execute("SELECT value FROM metadata WHERE key='generation'").fetchone() or [""])[0],
              "conceptsSha256": concepts_sha, "concepts": out_concepts,
              "records": dict(sorted(records.items())), "ambiguous": ambiguous}
    report = {"concepts": len(out_concepts), "records": len(records), "derived": derived,
              "noMatchComponents": no_match_components, "warnings": warnings}
    return export, report


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--concepts", required=True)
    ap.add_argument("--ambiguous", required=True)
    ap.add_argument("--pack", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--macros-only", action="store_true")
    ap.add_argument("--gzip", action="store_true")
    args = ap.parse_args(argv)
    try:
        check_nutrient_keys()
        with open(args.concepts, "rb") as f:
            sha = hashlib.sha256(f.read()).hexdigest()
        concepts = read_jsonl(args.concepts)
        ambiguous = read_jsonl(args.ambiguous)
        db = sqlite3.connect("file:" + args.pack.replace("\\", "/") + "?mode=ro", uri=True)
        try:
            export, rep = build(concepts, ambiguous, db, sha, args.macros_only)
        finally:
            db.close()
    except (ValueError, OSError, sqlite3.Error) as e:
        print("ERROR: %s" % e, file=sys.stderr)
        return 1
    data = json.dumps(export, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if args.gzip:
        with open(args.out, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
            gz.write(data)
    else:
        with open(args.out, "wb") as f:
            f.write(data)
    print("%d conceptos, %d fichas (%d derivadas), %d componentes noMatch, %d avisos" % (
        rep["concepts"], rep["records"], rep["derived"], rep["noMatchComponents"], len(rep["warnings"])))
    for w in rep["warnings"][:50]:
        print("  aviso: " + w)
    return 0


if __name__ == "__main__":
    for s in (sys.stdout, sys.stderr):
        if hasattr(s, "reconfigure"):
            s.reconfigure(encoding="utf-8")
    sys.exit(main())
