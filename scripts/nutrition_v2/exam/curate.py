#!/usr/bin/env python3
"""Curacion de las descripciones crudas del examen de nutricion v2.

Contrato: docs/nutrition-v2/EXAMEN.md (secciones 2, 3 y 9).

    python curate.py --raw C:\\kw\\nutri-examen\\raw --spec C:\\kw\\nutri-examen\\spec\\matriz.json \\
                     --out C:\\kw\\nutri-examen

Lee raw/*.jsonl, valida enums, descarta invalidos y duplicados (exactos y casi duplicados por
Jaccard de trigramas de caracteres >= 0,8), filtra por largo, selecciona hasta `target` por
fenomeno principal conservando la mezcla de regiones, asigna ids E0001... deterministas, parte
DEV/TEST (40/60) estratificado por fenomeno principal y escribe:

    cases/all.jsonl  cases/dev.jsonl  cases/test.jsonl  work/reserve.jsonl  work/curation-report.json

Solo biblioteca estandar. Con la misma semilla el resultado es identico.
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import random
import re
import sys
import unicodedata

DEFAULT_SEED = 20261027
NEAR_DUP_THRESHOLD = 0.8
MIN_WORDS = 1
MAX_WORDS = 60

REGIONS = ("CL", "AR", "MX", "PE", "CO", "ES")
STYLES = ("telefono", "dictado", "apurado", "detallado")
MEAL_TYPES = ("BREAKFAST", "LUNCH", "DINNER", "SNACK")
PHENOMENA = tuple("F%02d" % n for n in range(1, 17))
REQUIRED_KEYS = ("tmpId", "text", "mealType", "region", "style", "phenomena")


# --------------------------------------------------------------------------- texto

def normalize(text):
    """Minusculas, sin tildes, sin puntuacion y con espacios simples."""
    decomposed = unicodedata.normalize("NFKD", str(text).lower())
    plain = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    plain = re.sub(r"[^a-z0-9]+", " ", plain)
    return plain.strip()


def trigrams(norm_text):
    if len(norm_text) < 3:
        return frozenset([norm_text]) if norm_text else frozenset()
    return frozenset(norm_text[i:i + 3] for i in range(len(norm_text) - 2))


def jaccard(a, b):
    if not a and not b:
        return 1.0
    union = len(a | b)
    return len(a & b) / union if union else 0.0


def word_count(text):
    return len(str(text).split())


def has_letter(text):
    return any(ch.isalpha() for ch in str(text))


def natural_key(value):
    """'G3-055' -> ('G', 3, '-', 55, '')... orden natural estable."""
    parts = re.split(r"(\d+)", str(value))
    return tuple((0, int(p)) if p.isdigit() else (1, p) for p in parts if p != "")


# --------------------------------------------------------------------------- entrada

def read_jsonl(path):
    rows = []
    with open(path, "r", encoding="utf-8-sig") as stream:
        for number, line in enumerate(stream, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append((number, json.loads(line)))
            except ValueError:
                rows.append((number, None))
    return rows


def write_jsonl(path, rows):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")


def validate(item):
    """Devuelve (registro_limpio, None) o (None, motivo)."""
    if not isinstance(item, dict):
        return None, "linea no es un objeto JSON"
    for key in REQUIRED_KEYS:
        if key not in item:
            return None, "falta la clave %s" % key
    if not isinstance(item["tmpId"], str) or not item["tmpId"].strip():
        return None, "tmpId invalido"
    if not isinstance(item["text"], str) or not item["text"].strip():
        return None, "text vacio o no es cadena"
    if item["mealType"] not in MEAL_TYPES:
        return None, "mealType invalido: %r" % (item["mealType"],)
    if item["region"] not in REGIONS:
        return None, "region invalida: %r" % (item["region"],)
    if item["style"] not in STYLES:
        return None, "style invalido: %r" % (item["style"],)
    phenomena = item["phenomena"]
    if not isinstance(phenomena, list) or not phenomena:
        return None, "phenomena vacio o no es lista"
    cleaned = []
    for code in phenomena:
        if code not in PHENOMENA:
            return None, "fenomeno invalido: %r" % (code,)
        if code not in cleaned:
            cleaned.append(code)
    record = {
        "tmpId": item["tmpId"].strip(),
        "text": item["text"].strip(),
        "mealType": item["mealType"],
        "region": item["region"],
        "style": item["style"],
        "phenomena": cleaned,
    }
    if isinstance(item.get("persona"), str):
        record["persona"] = item["persona"]
    return record, None


def load_raw(raw_dir):
    """Lista ordenada (archivo, tmpId natural) de registros validos + descartes por validacion."""
    valid = []
    discards = []
    files = sorted(name for name in os.listdir(raw_dir) if name.lower().endswith(".jsonl"))
    for name in files:
        for number, item in read_jsonl(os.path.join(raw_dir, name)):
            if item is None:
                discards.append({"file": name, "line": number, "tmpId": None, "text": None,
                                 "reason": "JSON invalido"})
                continue
            record, reason = validate(item)
            if reason is not None:
                discards.append({"file": name, "line": number,
                                 "tmpId": item.get("tmpId") if isinstance(item, dict) else None,
                                 "text": item.get("text") if isinstance(item, dict) else None,
                                 "reason": reason})
                continue
            record["_file"] = name
            record["_line"] = number
            valid.append(record)
    valid.sort(key=lambda r: (r["_file"], natural_key(r["tmpId"]), r["_line"]))
    return valid, discards


# --------------------------------------------------------------------------- filtros

def filter_and_dedup(records, threshold=NEAR_DUP_THRESHOLD):
    """Aplica filtro de largo, ids duplicados, duplicados exactos y casi duplicados."""
    kept = []
    discards = []
    seen_ids = set()
    seen_norm = {}
    kept_grams = []  # [(trigramas, tmpId)]
    for rec in records:
        base = {"file": rec["_file"], "line": rec["_line"], "tmpId": rec["tmpId"], "text": rec["text"]}
        words = word_count(rec["text"])
        if words < MIN_WORDS or words > MAX_WORDS:
            discards.append(dict(base, reason="largo fuera de rango (%d palabras)" % words))
            continue
        if not has_letter(rec["text"]):
            discards.append(dict(base, reason="sin letras"))
            continue
        if rec["tmpId"] in seen_ids:
            discards.append(dict(base, reason="tmpId repetido"))
            continue
        norm = normalize(rec["text"])
        if not norm:
            discards.append(dict(base, reason="sin letras"))
            continue
        if norm in seen_norm:
            discards.append(dict(base, reason="duplicado exacto de %s" % seen_norm[norm]))
            continue
        grams = trigrams(norm)
        near = None
        for other_grams, other_id in kept_grams:
            if jaccard(grams, other_grams) >= threshold:
                near = other_id
                break
        if near is not None:
            discards.append(dict(base, reason="casi duplicado de %s" % near))
            continue
        seen_ids.add(rec["tmpId"])
        seen_norm[norm] = rec["tmpId"]
        kept_grams.append((grams, rec["tmpId"]))
        kept.append(rec)
    return kept, discards


# --------------------------------------------------------------------------- seleccion

def proportional_quota(counts, total):
    """Reparte `total` en proporcion a `counts` (dict) por mayor resto; nunca excede counts[k]."""
    size = sum(counts.values())
    if total >= size:
        return dict(counts)
    quotas = {}
    remainders = []
    for key in sorted(counts):
        exact = total * counts[key] / size
        quotas[key] = int(exact)
        remainders.append((-(exact - int(exact)), key))
    missing = total - sum(quotas.values())
    for _, key in sorted(remainders)[:missing]:
        quotas[key] += 1
    return quotas


def select_by_region(pool, target, rng):
    """Toma hasta `target` de `pool` manteniendo la mezcla de regiones del pool.

    Devuelve (elegidos, no_elegidos). Cada region se baraja con `rng`.
    """
    by_region = collections.defaultdict(list)
    for rec in pool:
        by_region[rec["region"]].append(rec)
    quotas = proportional_quota({region: len(items) for region, items in by_region.items()}, target)
    chosen = []
    rest = []
    for region in sorted(by_region):
        items = list(by_region[region])
        rng.shuffle(items)
        chosen.extend(items[:quotas[region]])
        rest.extend(items[quotas[region]:])
    return chosen, rest


def split_counts(selected_counts, dev_share):
    """Cuantos casos de DEV por fenomeno; el total global = round(N * dev_share) (resto mayor)."""
    total = sum(selected_counts.values())
    dev_total = int(total * dev_share + 0.5)
    base = {}
    remainders = []
    for code in sorted(selected_counts):
        exact = selected_counts[code] * dev_share
        base[code] = int(exact)
        remainders.append((-(exact - int(exact)), code))
    missing = dev_total - sum(base.values())
    for _, code in sorted(remainders)[:max(missing, 0)]:
        base[code] += 1
    return base


def curate(raw_dir, spec, seed=DEFAULT_SEED):
    """Pipeline completo en memoria. Devuelve dict con cases, reserve y report."""
    records, discards = load_raw(raw_dir)
    records, more = filter_and_dedup(records)
    discards.extend(more)

    phenomena_spec = spec["phenomena"]
    split_spec = spec.get("split", {"dev": 400, "test": 600})
    dev_share = split_spec["dev"] / float(split_spec["dev"] + split_spec["test"])

    rng = random.Random(seed)
    pools = collections.defaultdict(list)
    for rec in records:
        pools[rec["phenomena"][0]].append(rec)

    selected = {}
    reserve = []
    for code in sorted(phenomena_spec):
        target = int(phenomena_spec[code]["target"])
        chosen, rest = select_by_region(pools.get(code, []), target, rng)
        selected[code] = chosen
        reserve.extend(rest)
    for code in sorted(pools):
        if code not in phenomena_spec:  # fenomeno sin cuota en la matriz: todo a reserva
            reserve.extend(pools[code])

    dev_by_code = split_counts({code: len(items) for code, items in selected.items()}, dev_share)
    split_of = {}
    for code in sorted(selected):
        items = selected[code]
        dev_items, test_items = select_by_region(items, dev_by_code[code], rng)
        for rec in dev_items:
            split_of[rec["tmpId"]] = "dev"
        for rec in test_items:
            split_of[rec["tmpId"]] = "test"

    cases = []
    counter = 0
    for code in sorted(selected):
        for rec in sorted(selected[code], key=lambda r: natural_key(r["tmpId"])):
            counter += 1
            cases.append({
                "id": "E%04d" % counter,
                "split": split_of[rec["tmpId"]],
                "text": rec["text"],
                "mealType": rec["mealType"],
                "region": rec["region"],
                "phenomena": list(rec["phenomena"]),
                "style": rec["style"],
                "origin": rec["tmpId"],
            })

    reserve_rows = []
    for rec in sorted(reserve, key=lambda r: (r["phenomena"][0], natural_key(r["tmpId"]))):
        row = {key: value for key, value in rec.items() if not key.startswith("_")}
        reserve_rows.append(row)

    report = build_report(cases, reserve_rows, discards, phenomena_spec, seed, len(records))
    return {"cases": cases, "reserve": reserve_rows, "report": report}


def reason_key(reason):
    """Motivo sin el detalle variable (ids, valores, conteos) para agrupar en el informe."""
    key = re.sub(r":.*$", "", reason)
    key = re.sub(r"\s*\(.*\)$", "", key)
    key = re.sub(r" de [A-Za-z0-9]+-\d+$", "", key)
    return key


def build_report(cases, reserve_rows, discards, phenomena_spec, seed, kept_after_filters):
    def count(key):
        return dict(sorted(collections.Counter(c[key] for c in cases).items()))

    principal = collections.Counter(c["phenomena"][0] for c in cases)
    covered = collections.Counter(code for c in cases for code in c["phenomena"])
    by_phenomenon = {}
    for code in sorted(phenomena_spec):
        target = int(phenomena_spec[code]["target"])
        got = principal.get(code, 0)
        by_phenomenon[code] = {
            "target": target,
            "selected": got,
            "shortfall": max(target - got, 0),
            "dev": sum(1 for c in cases if c["phenomena"][0] == code and c["split"] == "dev"),
            "test": sum(1 for c in cases if c["phenomena"][0] == code and c["split"] == "test"),
            "appearsIn": covered.get(code, 0),
        }
    reasons = collections.Counter(reason_key(d["reason"]) for d in discards)
    return {
        "seed": seed,
        "total": len(cases),
        "keptAfterFilters": kept_after_filters,
        "reserve": len(reserve_rows),
        "discarded": len(discards),
        "split": {"dev": sum(1 for c in cases if c["split"] == "dev"),
                  "test": sum(1 for c in cases if c["split"] == "test")},
        "byPhenomenon": by_phenomenon,
        "byRegion": count("region"),
        "byStyle": count("style"),
        "byMealType": count("mealType"),
        "discardReasons": dict(sorted(reasons.items())),
        "discards": discards,
    }


def write_outputs(result, out_dir):
    cases = result["cases"]
    write_jsonl(os.path.join(out_dir, "cases", "all.jsonl"), cases)
    write_jsonl(os.path.join(out_dir, "cases", "dev.jsonl"), [c for c in cases if c["split"] == "dev"])
    write_jsonl(os.path.join(out_dir, "cases", "test.jsonl"), [c for c in cases if c["split"] == "test"])
    write_jsonl(os.path.join(out_dir, "work", "reserve.jsonl"), result["reserve"])
    report_path = os.path.join(out_dir, "work", "curation-report.json")
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, "w", encoding="utf-8", newline="\n") as stream:
        json.dump(result["report"], stream, ensure_ascii=False, indent=2)
        stream.write("\n")


# --------------------------------------------------------------------------- CLI

def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Curacion de descripciones del examen de nutricion")
    parser.add_argument("--raw", required=True, help="carpeta con raw/*.jsonl")
    parser.add_argument("--spec", required=True, help="spec/matriz.json")
    parser.add_argument("--out", required=True, help="carpeta de salida (cases/ y work/)")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    args = parser.parse_args(argv)
    with open(args.spec, "r", encoding="utf-8-sig") as stream:
        spec = json.load(stream)
    result = curate(args.raw, spec, seed=args.seed)
    write_outputs(result, args.out)
    report = result["report"]
    print("casos: %d (dev %d / test %d); reserva: %d; descartes: %d" % (
        report["total"], report["split"]["dev"], report["split"]["test"],
        report["reserve"], report["discarded"]), file=sys.stderr)
    short = {c: v["shortfall"] for c, v in report["byPhenomenon"].items() if v["shortfall"]}
    if short:
        print("faltan casos por fenomeno: %s" % json.dumps(short), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
