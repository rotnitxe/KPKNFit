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

Modo anexo (`--append-module B --raw-glob "B*.jsonl"`): suma el modulo B a cases/ sin reescribir
nada (ids desde el maximo + 1, particion 40/60 por grupo); ver append_module().

Solo biblioteca estandar. Con la misma semilla el resultado es identico.
"""
from __future__ import annotations

import argparse
import collections
import fnmatch
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
LANGS = ("es", "en", "mix")
DEFAULT_LANG = "es"
APPEND_MAX_WORDS = 160
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


def enum_values(enums):
    """Enums efectivos: los de la matriz (seccion `enums`) o las constantes del script."""
    enums = enums or {}
    return {
        "regions": tuple(enums.get("regions") or REGIONS),
        "styles": tuple(enums.get("styles") or STYLES),
        "langs": tuple(enums.get("langs") or LANGS),
        "phenomena": tuple(enums.get("phenomena") or PHENOMENA),
    }


def validate(item, enums=None):
    """Devuelve (registro_limpio, None) o (None, motivo)."""
    allowed = enum_values(enums)
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
    if item["region"] not in allowed["regions"]:
        return None, "region invalida: %r" % (item["region"],)
    if item["style"] not in allowed["styles"]:
        return None, "style invalido: %r" % (item["style"],)
    lang = item.get("lang", DEFAULT_LANG)
    if lang not in allowed["langs"]:
        return None, "lang invalido: %r" % (lang,)
    group = item.get("group")
    if group is not None and (not isinstance(group, str) or not group.strip()):
        return None, "group invalido: %r" % (group,)
    phenomena = item["phenomena"]
    if not isinstance(phenomena, list) or not phenomena:
        return None, "phenomena vacio o no es lista"
    cleaned = []
    for code in phenomena:
        if code not in allowed["phenomena"]:
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
        "lang": lang,
        "group": group.strip() if group is not None else None,
    }
    if isinstance(item.get("persona"), str):
        record["persona"] = item["persona"]
    return record, None


def load_raw(raw_dir, enums=None, raw_glob="*.jsonl"):
    """Lista ordenada (archivo, tmpId natural) de registros validos + descartes por validacion.

    Solo lee los archivos .jsonl que casan con `raw_glob` (sin distinguir mayusculas).
    """
    valid = []
    discards = []
    pattern = raw_glob.lower()
    files = sorted(name for name in os.listdir(raw_dir)
                   if name.lower().endswith(".jsonl") and fnmatch.fnmatchcase(name.lower(), pattern))
    for name in files:
        for number, item in read_jsonl(os.path.join(raw_dir, name)):
            if item is None:
                discards.append({"file": name, "line": number, "tmpId": None, "text": None,
                                 "reason": "JSON invalido"})
                continue
            record, reason = validate(item, enums)
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

def filter_and_dedup(records, threshold=NEAR_DUP_THRESHOLD, max_words=MAX_WORDS, prior=()):
    """Aplica filtro de largo, ids duplicados, duplicados exactos y casi duplicados.

    `prior`: textos ya curados [(id, texto)] contra los que tambien se compara (modo anexo).
    """
    kept = []
    discards = []
    seen_ids = set()
    seen_norm = {}
    kept_grams = []  # [(trigramas, tmpId)]
    for prior_id, prior_text in prior:
        prior_norm = normalize(prior_text)
        if not prior_norm:
            continue
        seen_norm.setdefault(prior_norm, prior_id)
        kept_grams.append((trigrams(prior_norm), prior_id))
    for rec in records:
        base = {"file": rec["_file"], "line": rec["_line"], "tmpId": rec["tmpId"], "text": rec["text"]}
        words = word_count(rec["text"])
        if words < MIN_WORDS or words > max_words:
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


def curate(raw_dir, spec, seed=DEFAULT_SEED, raw_glob="*.jsonl"):
    """Pipeline completo en memoria. Devuelve dict con cases, reserve y report."""
    records, discards = load_raw(raw_dir, spec.get("enums"), raw_glob)
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
                "lang": rec["lang"],
                "group": rec["group"],
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
    key = re.sub(r" de (?:[A-Za-z0-9]+-\d+|E\d+)$", "", key)
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


# --------------------------------------------------------------------------- anexo (modulo B)

def read_existing_cases(path):
    cases = []
    with open(path, "r", encoding="utf-8-sig") as stream:
        for line in stream:
            line = line.strip()
            if line:
                cases.append(json.loads(line))
    return cases


def max_case_number(cases):
    top = 0
    for case in cases:
        match = re.fullmatch(r"E(\d+)", str(case.get("id", "")))
        if match:
            top = max(top, int(match.group(1)))
    return top


def append_module(raw_dir, spec, out_dir, seed=DEFAULT_SEED, raw_glob="B*.jsonl", module="B"):
    """Calcula el anexo de un modulo sin tocar los casos ya curados (solo agrega al final)."""
    all_path = os.path.join(out_dir, "cases", "all.jsonl")
    if not os.path.isfile(all_path):
        raise SystemExit("--append-module exige que exista %s" % all_path)
    module_spec = spec["modulo" + module]
    groups_spec = module_spec["groups"]
    dev_share = float(module_spec.get("dev_share", 0.4))
    existing = read_existing_cases(all_path)

    records, discards = load_raw(raw_dir, spec.get("enums"), raw_glob)
    known = []
    for rec in records:
        if rec["group"] not in groups_spec:
            discards.append({"file": rec["_file"], "line": rec["_line"], "tmpId": rec["tmpId"],
                             "text": rec["text"], "reason": "grupo desconocido: %r" % (rec["group"],)})
        else:
            known.append(rec)
    prior = [(case["id"], case["text"]) for case in existing]
    records, more = filter_and_dedup(known, max_words=APPEND_MAX_WORDS, prior=prior)
    discards.extend(more)

    rng = random.Random(seed)
    pools = collections.defaultdict(list)
    for rec in records:
        pools[rec["group"]].append(rec)

    selected = {}
    reserve = []
    for group in sorted(groups_spec):
        target = int(groups_spec[group]["target"])
        chosen, rest = select_by_region(pools.get(group, []), target, rng)
        selected[group] = chosen
        reserve.extend(rest)

    dev_by_group = split_counts({g: len(items) for g, items in selected.items()}, dev_share)
    split_of = {}
    for group in sorted(selected):
        dev_items, test_items = select_by_region(selected[group], dev_by_group[group], rng)
        for rec in dev_items:
            split_of[rec["tmpId"]] = "dev"
        for rec in test_items:
            split_of[rec["tmpId"]] = "test"

    counter = max_case_number(existing)
    cases = []
    for group in sorted(selected):
        for rec in sorted(selected[group], key=lambda r: natural_key(r["tmpId"])):
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
                "lang": rec["lang"],
                "group": rec["group"],
            })

    reserve_rows = []
    for rec in sorted(reserve, key=lambda r: (r["group"], natural_key(r["tmpId"]))):
        reserve_rows.append({key: value for key, value in rec.items() if not key.startswith("_")})

    report = build_append_report(cases, reserve_rows, discards, groups_spec, seed, len(records), module)
    return {"cases": cases, "reserve": reserve_rows, "report": report}


def build_append_report(cases, reserve_rows, discards, groups_spec, seed, kept_after_filters, module):
    def count(key):
        return dict(sorted(collections.Counter(c[key] for c in cases).items()))

    by_group = {}
    for group in sorted(groups_spec):
        in_group = [c for c in cases if c["group"] == group]
        target = int(groups_spec[group]["target"])
        by_group[group] = {
            "target": target,
            "selected": len(in_group),
            "shortfall": max(target - len(in_group), 0),
            "dev": sum(1 for c in in_group if c["split"] == "dev"),
            "test": sum(1 for c in in_group if c["split"] == "test"),
        }
    reasons = collections.Counter(reason_key(d["reason"]) for d in discards)
    return {
        "module": module,
        "seed": seed,
        "total": len(cases),
        "keptAfterFilters": kept_after_filters,
        "reserve": len(reserve_rows),
        "discarded": len(discards),
        "split": {"dev": sum(1 for c in cases if c["split"] == "dev"),
                  "test": sum(1 for c in cases if c["split"] == "test")},
        "byGroup": by_group,
        "byRegion": count("region"),
        "byLang": count("lang"),
        "byStyle": count("style"),
        "byMealType": count("mealType"),
        "discardReasons": dict(sorted(reasons.items())),
        "discards": discards,
    }


def append_jsonl(path, rows):
    """Agrega filas al final sin reescribir lo que ya hay."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    needs_newline = False
    if os.path.isfile(path) and os.path.getsize(path) > 0:
        with open(path, "rb") as stream:
            stream.seek(-1, os.SEEK_END)
            needs_newline = stream.read(1) != b"\n"
    with open(path, "ab") as stream:
        if needs_newline:
            stream.write(b"\n")
        for row in rows:
            stream.write((json.dumps(row, ensure_ascii=False) + "\n").encode("utf-8"))


def write_append_outputs(result, out_dir, module="B"):
    cases = result["cases"]
    append_jsonl(os.path.join(out_dir, "cases", "all.jsonl"), cases)
    append_jsonl(os.path.join(out_dir, "cases", "dev.jsonl"), [c for c in cases if c["split"] == "dev"])
    append_jsonl(os.path.join(out_dir, "cases", "test.jsonl"), [c for c in cases if c["split"] == "test"])
    write_jsonl(os.path.join(out_dir, "work", "reserve-%s.jsonl" % module), result["reserve"])
    report_path = os.path.join(out_dir, "work", "curation-report-%s.json" % module)
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
    parser.add_argument("--raw-glob", default="G*.jsonl",
                        help="archivos de raw a leer (por defecto G*.jsonl = modulo A)")
    parser.add_argument("--append-module", choices=["B"],
                        help="anexa el modulo sin tocar los casos ya curados (usar --raw-glob B*.jsonl)")
    args = parser.parse_args(argv)
    with open(args.spec, "r", encoding="utf-8-sig") as stream:
        spec = json.load(stream)
    if args.append_module:
        module = args.append_module
        result = append_module(args.raw, spec, args.out, seed=args.seed,
                               raw_glob=args.raw_glob, module=module)
        write_append_outputs(result, args.out, module)
        report = result["report"]
        print("anexo %s: %d casos (dev %d / test %d); reserva: %d; descartes: %d" % (
            module, report["total"], report["split"]["dev"], report["split"]["test"],
            report["reserve"], report["discarded"]), file=sys.stderr)
        short = {g: v["shortfall"] for g, v in report["byGroup"].items() if v["shortfall"]}
        if short:
            print("faltan casos por grupo: %s" % json.dumps(short), file=sys.stderr)
        return 0
    result = curate(args.raw, spec, seed=args.seed, raw_glob=args.raw_glob)
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
