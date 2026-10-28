#!/usr/bin/env python3
"""Puntuador del examen de nutricion v2 (contrato: docs/nutrition-v2/EXAMEN.md).

Uso:
  python score.py --cases all.jsonl --gold gold.jsonl --pred A.jsonl [--pred-b B.jsonl]
                  --split dev|test --out-dir <dir> [--seed 20261027] [--boot 2000]

Empareja las predicciones de un sistema con el oro, calcula M1-M8, extras, errores y
latencia (global y por fenomeno), compara dos sistemas con bootstrap pareado y aplica la
regla de aceptacion. Escribe un informe Markdown y un JSON. Con --split test el informe
contiene SOLO agregados (nunca ids, textos ni casos sueltos).

Solo biblioteca estandar.
"""
from __future__ import annotations

import argparse
import collections
import datetime
import json
import math
import os
import random
import re
import sys
import unicodedata

# ---------------------------------------------------------------------------
# Constantes del contrato
# ---------------------------------------------------------------------------

THRESHOLD = 0.34
DEFAULT_SEED = 20261027
DEFAULT_BOOT = 2000

STOPWORDS = frozenset(
    "de del la el los las un una unos unas con y e al a en por para mi me medio media poco".split()
)

# Consonantes tras las cuales "es" es plural de un sustantivo que termina en consonante
# (panes, limones, pasteles, colores). Con otras consonantes ("tomates", "dulces",
# "panqueques") la "e" es parte de la raiz y solo se quita la "s".
ES_PLURAL_CONSONANTS = frozenset("lnrdzjsx")

TOLERANCES = {
    "EXPLICIT": {"grams": 0.02, "nut": 0.10, "kcal": 15.0, "macro": 2.0},
    "CONTAINER": {"grams": 0.10, "nut": 0.15, "kcal": 20.0, "macro": 2.0},
    "COUNT": {"grams": 0.20, "nut": 0.25, "kcal": 25.0, "macro": 3.0},
    "HOUSEHOLD": {"grams": 0.20, "nut": 0.25, "kcal": 25.0, "macro": 3.0},
    "SUBJECTIVE": {"grams": 0.35, "nut": 0.40, "kcal": 40.0, "macro": 4.0},
}
CLASS_RANK = {"EXPLICIT": 0, "CONTAINER": 1, "COUNT": 2, "HOUSEHOLD": 3, "SUBJECTIVE": 4}

NUT_KEYS = ("kcal", "protein", "fat", "carbs")

M1_KCAL = (0.12, 15.0)
M1_MACRO = (0.20, 2.0)
M4_KCAL = (0.50, 40.0)
M7_KCAL = (0.20, 40.0)

QUESTION_MAP = {
    "IDENTITY": "IDENTITY",
    "STATE": "STATE",
    "FORM": "FORM",
    "AMOUNT": "AMOUNT",
    "OIL": "STATE",
    "CUT": "IDENTITY",
}
M8_CORE = frozenset(["fiber", "sugar", "sodium", "potassium"])

PHENOMENA_NAMES = {
    "F01": "Alimento suelto sin cantidad",
    "F02": "Masa o volumen explicito",
    "F03": "Conteo y tamano",
    "F04": "Medida casera",
    "F05": "Envase y fraccion",
    "F06": "Coccion / estado",
    "F07": "Presentacion / forma",
    "F08": "Plato regional",
    "F09": "Composicion X con Y",
    "F10": "Exclusion frente a atributo",
    "F11": "Marca o producto envasado",
    "F12": "Correccion o referencia",
    "F13": "Faltas de tipeo o dictado",
    "F14": "Regionalismo",
    "F15": "Bebida o alcohol",
    "F16": "Suplemento",
}

# Aceptacion (seccion 7)
ACCEPT_M4_UPPER_PP = 0.5
ACCEPT_PHENOMENON_MIN_MENTIONS = 30
ACCEPT_PHENOMENON_MAX_DROP_PP = 3.0

COUNTERS = (
    "cases", "errors", "missing",
    "m3_num", "m3_den",
    "m5_num", "m5_den",
    "m1_num", "m1_den",
    "m2_num", "m2_den",
    "m4_num", "m4_den",
    "m6_tp", "m6_asked", "m6_just",
    "m7_num", "m7_den",
    "m8_num", "m8_den",
    "extras", "questions", "omissions", "excluded_hits",
)
BOOT_COUNTERS = tuple(c for c in COUNTERS if c not in ("missing", "omissions", "excluded_hits"))

# (clave, etiqueta, tipo, (numerador, denominador), mejor_menor)
METRICS = (
    ("M1", "M1 Identidad", "pct", ("m1_num", "m1_den"), False),
    ("M2", "M2 Gramos (dada M1 principal)", "pct", ("m2_num", "m2_den"), False),
    ("M3", "M3 Mencion correcta (principal)", "pct", ("m3_num", "m3_den"), False),
    ("M4", "M4 Error silencioso", "pct", ("m4_num", "m4_den"), True),
    ("M5", "M5 Cobertura", "pct", ("m5_num", "m5_den"), False),
    ("M6P", "M6 Preguntas: precision", "pct", ("m6_tp", "m6_asked"), False),
    ("M6R", "M6 Preguntas: recobrado", "pct", ("m6_tp", "m6_just"), False),
    ("M7", "M7 Comida dentro de +-20 % kcal", "pct", ("m7_num", "m7_den"), False),
    ("M8", "M8 Micronutrientes (nucleo completo)", "pct", ("m8_num", "m8_den"), False),
    ("EXTRAS", "Extras por comida", "num", ("extras", "cases"), True),
    ("PREG", "Preguntas por comida", "num", ("questions", "cases"), True),
    ("ERR", "Casos con error del sistema", "pct", ("errors", "cases"), True),
)
METRIC_KEYS = tuple(m[0] for m in METRICS)
PHEN_METRICS = ("M3", "M5")


# ---------------------------------------------------------------------------
# Entrada / salida
# ---------------------------------------------------------------------------

def read_jsonl(path):
    rows = []
    with open(path, "r", encoding="utf-8-sig") as fh:
        for n, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except ValueError:
                raise SystemExit("%s: la linea %d no es JSON valido" % (os.path.basename(path), n))
    return rows


def sanitize_filename(name):
    out = re.sub(r"[^A-Za-z0-9._@+-]", "_", str(name))
    return out.strip("._") or "sistema"


# ---------------------------------------------------------------------------
# Normalizacion y similitud
# ---------------------------------------------------------------------------

def _singular(tok):
    if len(tok) > 4 and tok.endswith("es") and tok[-3] in ES_PLURAL_CONSONANTS:
        return tok[:-2]
    if len(tok) > 3 and tok.endswith("s"):
        return tok[:-1]
    return tok


def norm_tokens(text):
    if not isinstance(text, str) or not text:
        return frozenset()
    s = unicodedata.normalize("NFKD", text.lower())
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    out = set()
    for tok in re.findall(r"[a-z0-9]+", s):
        if tok in STOPWORDS:
            continue
        out.add(_singular(tok))
    return frozenset(out)


def token_sets(strings):
    """Lista de conjuntos de tokens no vacios."""
    res = []
    for s in strings:
        t = norm_tokens(s)
        if t:
            res.append(t)
    return res


def similarity(a_sets, b_sets):
    """Maximo coeficiente de solapamiento |A&B| / min(|A|,|B|) entre dos listas de conjuntos."""
    best = 0.0
    for a in a_sets:
        for b in b_sets:
            s = len(a & b) / min(len(a), len(b))
            if s > best:
                best = s
    return best


def _gold_strings(g):
    out = [g.get("span"), g.get("canonicalEs")]
    out.extend(g.get("aliasesEs") or [])
    return out


def _pred_strings(p):
    return [p.get("text"), p.get("foodName")]


def _excluded_strings(e):
    if isinstance(e, str):
        return [e]
    if isinstance(e, dict):
        out = [e.get("span"), e.get("text"), e.get("canonicalEs"), e.get("name")]
        out.extend(e.get("aliasesEs") or [])
        return out
    return []


# ---------------------------------------------------------------------------
# Tolerancias
# ---------------------------------------------------------------------------

def _num(x):
    if isinstance(x, bool):
        return None
    return x if isinstance(x, (int, float)) else None


def within(pred, gold, rel, floor):
    """|pred - gold| <= max(rel*|gold|, floor). Oro nulo: no se evalua (True)."""
    g = _num(gold)
    if g is None:
        return True
    p = _num(pred)
    if p is None:
        return False
    return abs(p - g) <= max(rel * abs(g), floor) + 1e-9


def mention_class(g, group=False):
    comps = g.get("components") or []
    comp_classes = [
        (c.get("grams") or {}).get("tolerance") for c in comps if isinstance(c, dict)
    ]
    comp_classes = [c for c in comp_classes if c in TOLERANCES]
    own = (g.get("grams") or {}).get("tolerance")
    if group and comp_classes:
        return max(comp_classes, key=lambda c: CLASS_RANK[c])
    if own in TOLERANCES:
        return own
    if comp_classes:
        return max(comp_classes, key=lambda c: CLASS_RANK[c])
    return "SUBJECTIVE"


def nutrients_failed(pred_eaten, gold_eaten, cls):
    t = TOLERANCES.get(cls, TOLERANCES["SUBJECTIVE"])
    failed = []
    pe = pred_eaten or {}
    ge = gold_eaten or {}
    for k in NUT_KEYS:
        floor = t["kcal"] if k == "kcal" else t["macro"]
        if not within(pe.get(k), ge.get(k), t["nut"], floor):
            failed.append(k)
    return failed


def grams_ok(pred_grams, gold_grams_obj, cls):
    """None si no es evaluable; True/False en otro caso."""
    target = _num((gold_grams_obj or {}).get("target"))
    if target is None:
        return None
    t = TOLERANCES.get(cls, TOLERANCES["SUBJECTIVE"])
    return within(pred_grams, target, t["grams"], 0.0)


def _per100_has_values(d):
    return isinstance(d, dict) and any(_num(d.get(k)) is not None for k in NUT_KEYS)


def per100_close(pred_p, gold_p):
    pp = pred_p or {}
    for k in NUT_KEYS:
        if _num(gold_p.get(k)) is None:
            continue
        rel, floor = M1_KCAL if k == "kcal" else M1_MACRO
        if not within(pp.get(k), gold_p.get(k), rel, floor):
            return False
    return True


def sum_eaten(pm, pis):
    out = {}
    for k in NUT_KEYS:
        vals = [_num((pm[i].get("eaten") or {}).get(k)) for i in pis]
        vals = [v for v in vals if v is not None]
        out[k] = sum(vals) if vals else None
    return out


# ---------------------------------------------------------------------------
# Evaluacion de un caso
# ---------------------------------------------------------------------------

def _assess(g, pm, pis, group):
    cls = mention_class(g, group=group)
    eaten = sum_eaten(pm, pis)
    failed = nutrients_failed(eaten, g.get("eaten"), cls)
    heur = any(bool(pm[i].get("heuristic")) for i in pis)
    return {"cls": cls, "eaten": eaten, "failed": failed, "heur": heur,
            "ok": (not failed) and (not heur)}


def evaluate_case(case, gold, pred, thr=THRESHOLD):
    c = dict.fromkeys(COUNTERS, 0)
    c["cases"] = 1
    failures = []
    gms = [g for g in (gold.get("mentions") or []) if isinstance(g, dict)]

    missing = pred is None
    err = (not missing) and pred.get("error") is not None
    if missing:
        c["errors"] = 1
        c["missing"] = 1
        base_reason = "sin linea de prediccion"
    elif err:
        c["errors"] = 1
        base_reason = "error del sistema: %s" % str(pred.get("error"))[:60]
    else:
        base_reason = None

    pm = [] if (missing or err) else [p for p in (pred.get("mentions") or []) if isinstance(p, dict)]
    latency = None
    if not missing:
        latency = _num(pred.get("latencyMs"))

    active = [i for i, p in enumerate(pm) if not p.get("excluded")]
    ptoks = {i: token_sets(_pred_strings(pm[i])) for i in range(len(pm))}
    gtoks = [token_sets(_gold_strings(g)) for g in gms]

    # Predicciones que describen lo excluido por el usuario
    forbidden = set()
    ex_toks = [token_sets(_excluded_strings(e)) for e in (gold.get("excluded") or [])]
    ex_toks = [t for t in ex_toks if t]
    if ex_toks:
        for i in active:
            ex_sim = max(similarity(ptoks[i], t) for t in ex_toks)
            if ex_sim >= thr - 1e-12:
                gold_best = max([similarity(ptoks[i], gt) for gt in gtoks] or [0.0])
                if ex_sim > gold_best:
                    forbidden.add(i)
                    c["excluded_hits"] += 1

    # Fase 1: asignacion codiciosa de un-a-uno
    match = {}   # gi -> {"preds": [pi...], "kind": "whole"|"group"}
    owner = {}   # pi -> gi
    pairs = []
    for gi in range(len(gms)):
        for pi in active:
            if pi in forbidden:
                continue
            s = similarity(ptoks[pi], gtoks[gi])
            if s >= thr - 1e-12:
                pairs.append((-s, gi, pi))
    pairs.sort()
    for _, gi, pi in pairs:
        if gi in match or pi in owner:
            continue
        match[gi] = {"preds": [pi], "kind": "whole"}
        owner[pi] = gi

    # Fase 2: platos con allowSplit
    for gi, g in enumerate(gms):
        comps = g.get("components") or []
        if not (g.get("granularity") == "DISH" and g.get("allowSplit") and comps):
            continue
        comp_toks = [token_sets([cc.get("canonicalEs")] + list(cc.get("aliasesEs") or []))
                     for cc in comps if isinstance(cc, dict)]
        comp_toks = [t for t in comp_toks if t]
        if not comp_toks:
            continue
        free = [pi for pi in active if pi not in forbidden and pi not in owner]

        def group_from(cands):
            return sorted(pi for pi in cands
                          if any(similarity(ptoks[pi], ct) >= thr - 1e-12 for ct in comp_toks))

        if gi in match:
            whole = match[gi]["preds"]
            if _assess(g, pm, whole, False)["ok"]:
                continue
            grp = group_from(free + whole)
            if grp and set(grp) != set(whole) and _assess(g, pm, grp, True)["ok"]:
                for pi in whole:
                    owner.pop(pi, None)
                match[gi] = {"preds": grp, "kind": "group"}
                for pi in grp:
                    owner[pi] = gi
        else:
            grp = group_from(free)
            if grp:
                match[gi] = {"preds": grp, "kind": "group"}
                for pi in grp:
                    owner[pi] = gi

    # Puntuacion de menciones de oro
    for gi, g in enumerate(gms):
        optional = bool(g.get("optional"))
        m = match.get(gi)
        if m is None and optional:
            continue
        refs = []
        if _per100_has_values(g.get("per100g")):
            refs.append(g["per100g"])
        eq_refs = [e["per100g"] for e in (g.get("equivalents") or [])
                   if isinstance(e, dict) and _per100_has_values(e.get("per100g"))]
        just = bool(g.get("ambiguity"))
        span = g.get("span") or g.get("canonicalEs") or ""
        gold_kcal = _num((g.get("eaten") or {}).get("kcal"))

        c["m3_den"] += 1
        if not optional:
            c["m5_den"] += 1
        if just:
            c["m6_just"] += 1

        if m is None:
            c["omissions"] += 1
            if refs or eq_refs:
                c["m1_den"] += 1
            failures.append({"id": case["id"], "text": case.get("text", ""), "span": span,
                             "goldKcal": gold_kcal, "predKcal": None,
                             "reason": base_reason or "omision (sin pareja)"})
            continue

        pis = m["preds"]
        group = m["kind"] == "group"
        a = _assess(g, pm, pis, group)
        if a["ok"]:
            c["m3_num"] += 1
        else:
            if a["heur"]:
                reason = "ficha heuristica"
                if a["failed"]:
                    reason += " y fuera de tolerancia (%s)" % ",".join(a["failed"])
            else:
                reason = "fuera de tolerancia %s (%s)" % (a["cls"], ",".join(a["failed"]))
            failures.append({"id": case["id"], "text": case.get("text", ""), "span": span,
                             "goldKcal": gold_kcal, "predKcal": a["eaten"].get("kcal"),
                             "reason": reason})
        if not optional and not a["heur"]:
            c["m5_num"] += 1

        # M1 / M2 (solo emparejes enteros con referencia)
        if not group and (refs or eq_refs):
            c["m1_den"] += 1
            pp = pm[pis[0]].get("per100g") or {}
            main_ok = bool(refs) and per100_close(pp, refs[0])
            any_ok = main_ok or any(per100_close(pp, r) for r in eq_refs)
            if any_ok:
                c["m1_num"] += 1
            if main_ok:
                gk = grams_ok(pm[pis[0]].get("grams"), g.get("grams"), a["cls"])
                if gk is not None:
                    c["m2_den"] += 1
                    if gk:
                        c["m2_num"] += 1

        # M6
        asked = set()
        for pi in pis:
            for q in (pm[pi].get("questions") or []):
                if q in QUESTION_MAP:
                    asked.add(QUESTION_MAP[q])
        if asked:
            c["m6_asked"] += 1
            if just:
                c["m6_tp"] += 1

    # M4: error silencioso sobre predicciones autoaceptadas
    for pi in active:
        p = pm[pi]
        if p.get("autoAccepted") is not True:
            continue
        c["m4_den"] += 1
        gi = owner.get(pi)
        bad = False
        if gi is None or bool(p.get("heuristic")):
            bad = True
        else:
            m = match[gi]
            gk = _num((gms[gi].get("eaten") or {}).get("kcal"))
            if gk is not None:
                pk = sum_eaten(pm, m["preds"]).get("kcal")
                if pk is None or abs(pk - gk) > max(M4_KCAL[0] * abs(gk), M4_KCAL[1]) + 1e-9:
                    bad = True
        if bad:
            c["m4_num"] += 1

    # Extras, preguntas, M8
    nutr_counts = []
    for pi in active:
        p = pm[pi]
        if pi not in owner:
            c["extras"] += 1
        c["questions"] += len(p.get("questions") or [])
        present = p.get("nutrientsPresent") or []
        c["m8_den"] += 1
        if M8_CORE.issubset(set(present)):
            c["m8_num"] += 1
        nutr_counts.append(len(present))

    # M7: comida completa
    meal_kcal = _num((gold.get("mealEaten") or {}).get("kcal"))
    if meal_kcal is not None:
        c["m7_den"] += 1
        total = sum(_num((pm[i].get("eaten") or {}).get("kcal")) or 0.0 for i in active)
        if (not (missing or err)) and abs(total - meal_kcal) <= max(M7_KCAL[0] * abs(meal_kcal), M7_KCAL[1]) + 1e-9:
            c["m7_num"] += 1

    return {"id": case["id"], "text": case.get("text", ""), "counters": c,
            "latency": latency, "nutr_counts": nutr_counts, "failures": failures,
            "phenomena": list(case.get("phenomena") or [])}


# ---------------------------------------------------------------------------
# Agregacion y metricas
# ---------------------------------------------------------------------------

def percentile(values, q):
    v = sorted(values)
    if not v:
        return None
    k = (len(v) - 1) * q / 100.0
    lo = int(math.floor(k))
    hi = int(math.ceil(k))
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


def median(values):
    return percentile(values, 50)


def totals(results):
    t = dict.fromkeys(COUNTERS, 0)
    for r in results:
        for k, v in r["counters"].items():
            t[k] += v
    return t


def metrics_from_totals(t):
    out = {}
    for key, _label, kind, (num, den), _lower in METRICS:
        d = t[den]
        if d == 0:
            out[key] = None
        else:
            out[key] = (100.0 * t[num] / d) if kind == "pct" else (t[num] / d)
    return out


def system_summary(results):
    t = totals(results)
    lat = [r["latency"] for r in results if r["latency"] is not None]
    nutr = [n for r in results for n in r["nutr_counts"]]
    return {"counts": t, "metrics": metrics_from_totals(t),
            "latencyMs": {"p50": percentile(lat, 50), "p95": percentile(lat, 95), "n": len(lat)},
            "m8MedianNutrients": median(nutr)}


def phenomenon_summary(results, code):
    sub = [r for r in results if code in r["phenomena"]]
    t = totals(sub)
    m = metrics_from_totals(t)
    lat = [r["latency"] for r in sub if r["latency"] is not None]
    return {"cases": len(sub), "mentions": t["m5_den"],
            "metrics": {k: m[k] for k in PHEN_METRICS},
            "latencyMs": {"p50": percentile(lat, 50), "p95": percentile(lat, 95)}}


# ---------------------------------------------------------------------------
# Bootstrap pareado
# ---------------------------------------------------------------------------

def ci95(values):
    vals = [v for v in values if v is not None]
    if not vals:
        return None
    vals.sort()
    return [percentile(vals, 2.5), percentile(vals, 97.5)]


def bootstrap(per_system, phen_members, seed, reps):
    """per_system: lista (1 o 2) de listas de resultados de caso en el MISMO orden.

    Devuelve dict con listas de replicas: glob[s][metrica], phen[s][codigo][metrica],
    y si hay 2 sistemas dglob[metrica], dphen[codigo][metrica] (B - A).
    """
    n = len(per_system[0])
    ns = len(per_system)
    cols = [{k: [r["counters"][k] for r in res] for k in BOOT_COUNTERS} for res in per_system]
    glob = [{k: [] for k in METRIC_KEYS} for _ in range(ns)]
    phen = [{code: {k: [] for k in PHEN_METRICS} for code in phen_members} for _ in range(ns)]
    dglob = {k: [] for k in METRIC_KEYS}
    dphen = {code: {k: [] for k in PHEN_METRICS} for code in phen_members}
    if n == 0:
        return {"glob": glob, "phen": phen, "dglob": dglob, "dphen": dphen}
    rng = random.Random(seed)
    population = list(range(n))
    for _ in range(reps):
        idx = rng.choices(population, k=n)
        weights = collections.Counter(idx)
        gm = []
        for s in range(ns):
            t = {k: sum(map(cols[s][k].__getitem__, idx)) for k in BOOT_COUNTERS}
            m = metrics_from_totals(dict(t, missing=0, omissions=0, excluded_hits=0))
            gm.append(m)
            for k in METRIC_KEYS:
                glob[s][k].append(m[k])
        if ns == 2:
            for k in METRIC_KEYS:
                a, b = gm[0][k], gm[1][k]
                dglob[k].append(None if a is None or b is None else b - a)
        for code, members in phen_members.items():
            pm_ = []
            for s in range(ns):
                t = {}
                for k in ("m3_num", "m3_den", "m5_num", "m5_den"):
                    col = cols[s][k]
                    t[k] = sum(weights.get(i, 0) * col[i] for i in members)
                vals = {
                    "M3": (100.0 * t["m3_num"] / t["m3_den"]) if t["m3_den"] else None,
                    "M5": (100.0 * t["m5_num"] / t["m5_den"]) if t["m5_den"] else None,
                }
                pm_.append(vals)
                for k in PHEN_METRICS:
                    phen[s][code][k].append(vals[k])
            if ns == 2:
                for k in PHEN_METRICS:
                    a, b = pm_[0][k], pm_[1][k]
                    dphen[code][k].append(None if a is None or b is None else b - a)
    return {"glob": glob, "phen": phen, "dglob": dglob, "dphen": dphen}


# ---------------------------------------------------------------------------
# Regla de aceptacion
# ---------------------------------------------------------------------------

def decide_acceptance(delta_m3_ci, delta_m4_ci, phenomena_checks):
    """phenomena_checks: lista de dicts {code, mentions, deltaM3}. Devuelve dict con veredicto."""
    reasons = []
    notes = []
    c1 = bool(delta_m3_ci) and delta_m3_ci[0] is not None and delta_m3_ci[0] > 0
    if not c1:
        low = None if not delta_m3_ci else delta_m3_ci[0]
        reasons.append("M3 no mejora con certeza: IC inferior de Delta M3 = %s pp (se exige > 0)" % _f(low))
    if delta_m4_ci is None:
        c2 = True
        notes.append("M4 sin denominador (ningun sistema autoacepto menciones): criterio de M4 no evaluable, se da por cumplido")
    else:
        c2 = delta_m4_ci[1] <= ACCEPT_M4_UPPER_PP + 1e-12
        if not c2:
            reasons.append("M4 empeora: IC superior de Delta M4 = %s pp (se exige <= +%.1f pp)"
                           % (_f(delta_m4_ci[1]), ACCEPT_M4_UPPER_PP))
    violators = []
    for chk in phenomena_checks:
        if chk["mentions"] >= ACCEPT_PHENOMENON_MIN_MENTIONS and chk["deltaM3"] is not None \
                and chk["deltaM3"] < -ACCEPT_PHENOMENON_MAX_DROP_PP - 1e-12:
            violators.append(chk)
    c3 = not violators
    for v in violators:
        reasons.append("Fenomeno %s (%d menciones) cae %s pp en M3 (maximo permitido %.1f pp)"
                       % (v["code"], v["mentions"], _f(-v["deltaM3"]), ACCEPT_PHENOMENON_MAX_DROP_PP))
    accepted = c1 and c2 and c3
    return {
        "verdict": "ACEPTA" if accepted else "NO ACEPTA",
        "criteria": {
            "m3_ci_lower_gt_0": c1,
            "m4_ci_upper_le_0_5pp": c2,
            "no_phenomenon_drops_gt_3pp": c3,
        },
        "reasons": reasons,
        "notes": notes,
        "violatingPhenomena": [{"code": v["code"], "mentions": v["mentions"], "deltaM3": v["deltaM3"]}
                               for v in violators],
    }


# ---------------------------------------------------------------------------
# Formato
# ---------------------------------------------------------------------------

def _f(v, nd=1):
    if v is None:
        return "n/d"
    return ("%." + str(nd) + "f") % v


def _fmt_metric(key, v):
    kind = next(m[2] for m in METRICS if m[0] == key)
    if v is None:
        return "n/d"
    return ("%.1f %%" % v) if kind == "pct" else ("%.2f" % v)


def _fmt_delta(key, v):
    kind = next(m[2] for m in METRICS if m[0] == key)
    if v is None:
        return "n/d"
    return ("%+.1f pp" % v) if kind == "pct" else ("%+.2f" % v)


def _fmt_ci(key, ci, delta=False):
    if not ci:
        return "n/d"
    kind = next(m[2] for m in METRICS if m[0] == key)
    if kind == "pct":
        return "[%+.1f, %+.1f]" % tuple(ci) if delta else "[%.1f, %.1f]" % tuple(ci)
    return "[%+.2f, %+.2f]" % tuple(ci) if delta else "[%.2f, %.2f]" % tuple(ci)


def _esc(s):
    return str(s).replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def _fmt_ms(v):
    return "n/d" if v is None else "%.1f" % v


def _fmt_kcal(v):
    return "n/d" if v is None else "%.0f" % v


# ---------------------------------------------------------------------------
# Informe
# ---------------------------------------------------------------------------

def dominant_system_name(rows, fallback):
    names = collections.Counter(str(r.get("system")) for r in rows if r.get("system"))
    return names.most_common(1)[0][0] if names else fallback


def load_predictions(path):
    rows = read_jsonl(path)
    by_id = {}
    dup = 0
    for r in rows:
        rid = r.get("id")
        if rid in by_id:
            dup += 1
            continue
        by_id[rid] = r
    name = dominant_system_name(rows, os.path.splitext(os.path.basename(path))[0])
    return name, by_id, dup


def run(args):
    split = args.split
    cases_all = read_jsonl(args.cases)
    gold_rows = read_jsonl(args.gold)
    gold = {}
    for g in gold_rows:
        gold.setdefault(g.get("id"), g)
    cases = [c for c in cases_all if c.get("split") == split and c.get("id") in gold]
    n_split = sum(1 for c in cases_all if c.get("split") == split)
    cov_no_gold = n_split - len(cases)

    name_a, preds_a, dup_a = load_predictions(args.pred)
    ab = args.pred_b is not None
    if ab:
        name_b, preds_b, dup_b = load_predictions(args.pred_b)
        if name_b == name_a:
            name_b = name_b + "#B"
    else:
        name_b, preds_b, dup_b = None, {}, 0

    systems = [("A", name_a, preds_a)] + ([("B", name_b, preds_b)] if ab else [])
    results = []
    for _label, _name, preds in systems:
        results.append([evaluate_case(c, gold[c["id"]], preds.get(c["id"])) for c in cases])

    phen_members = collections.OrderedDict()
    for code in sorted({p for c in cases for p in (c.get("phenomena") or [])}):
        phen_members[code] = [i for i, c in enumerate(cases) if code in (c.get("phenomena") or [])]

    boot = bootstrap(results, phen_members, args.seed, args.boot) if cases else None

    report = {
        "schema": 1,
        "split": split,
        "seed": args.seed,
        "bootstrapReplicates": args.boot,
        "systems": {"A": name_a, "B": name_b},
        "inputs": {"cases": os.path.basename(args.cases), "predA": os.path.basename(args.pred),
                   "predB": os.path.basename(args.pred_b) if ab else None},
        "generatedAt": datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        "coverage": {
            "casesInSplit": n_split,
            "casesScored": len(cases),
            "casesWithoutGold": cov_no_gold,
            "duplicatePredictionLinesA": dup_a,
            "duplicatePredictionLinesB": dup_b if ab else None,
        },
        "global": {},
        "phenomena": {},
        "acceptance": None,
        "notes": [],
    }

    def with_ci(point, reps, deltas=None):
        return {k: {"value": point[k], "ci95": ci95(reps[k]) if reps is not None else None}
                for k in METRIC_KEYS}

    for s, (label, _name, _p) in enumerate(systems):
        summ = system_summary(results[s])
        entry = {"counts": summ["counts"], "latencyMs": summ["latencyMs"],
                 "m8MedianNutrients": summ["m8MedianNutrients"],
                 "metrics": with_ci(summ["metrics"], boot["glob"][s] if boot else None)}
        report["global"][label] = entry
    if ab:
        ma = report["global"]["A"]["metrics"]
        mb = report["global"]["B"]["metrics"]
        report["global"]["delta"] = {
            k: {"value": (None if ma[k]["value"] is None or mb[k]["value"] is None
                          else mb[k]["value"] - ma[k]["value"]),
                "ci95": ci95(boot["dglob"][k]) if boot else None}
            for k in METRIC_KEYS}

    phen_checks = []
    for code, members in phen_members.items():
        entry = {"name": PHENOMENA_NAMES.get(code, code)}
        for s, (label, _name, _p) in enumerate(systems):
            ps = phenomenon_summary(results[s], code)
            entry["mentions"] = ps["mentions"]
            entry["cases"] = ps["cases"]
            entry[label] = {
                "metrics": {k: {"value": ps["metrics"][k],
                                "ci95": ci95(boot["phen"][s][code][k]) if boot else None}
                            for k in PHEN_METRICS},
                "latencyMs": ps["latencyMs"],
            }
        if ab:
            entry["delta"] = {}
            for k in PHEN_METRICS:
                a = entry["A"]["metrics"][k]["value"]
                b = entry["B"]["metrics"][k]["value"]
                entry["delta"][k] = {"value": None if a is None or b is None else b - a,
                                     "ci95": ci95(boot["dphen"][code][k]) if boot else None}
            phen_checks.append({"code": code, "mentions": entry["mentions"],
                                "deltaM3": entry["delta"]["M3"]["value"]})
        report["phenomena"][code] = entry

    if ab:
        report["acceptance"] = decide_acceptance(
            report["global"]["delta"]["M3"]["ci95"], report["global"]["delta"]["M4"]["ci95"], phen_checks)
        report["acceptance"]["ruleText"] = (
            "ACEPTA si IC95 inferior de Delta M3 > 0, IC95 superior de Delta M4 <= +0,5 pp y ningun "
            "fenomeno con >= 30 menciones cae mas de 3 pp en M3 (diferencia puntual).")

    report["notes"] = [
        "Solo se puntuan los casos del split con oro; un caso con oro y sin linea de prediccion cuenta como error.",
        "Singular simple: se quita 'es' solo tras l, n, r, d, z, j, s, x (panes, limones); "
        "con otras consonantes solo la 's' (tomates -> tomate).",
        "Plato con allowSplit: se empareja primero como un todo; si ese todo queda fuera de tolerancia "
        "y el grupo de componentes si cabe, se usa el grupo.",
        "M2 se calcula sobre las menciones con M1 cumplida contra la referencia principal y gramos evaluables.",
        "M1/M2 no se evaluan en menciones de oro sin per100g ni en platos emparejados por componentes.",
        "Las menciones con 'optional' sin pareja no cuentan; emparejadas puntuan en M3 pero no en M5.",
    ]
    if split == "dev":
        fails = [f for r in results[0] for f in r["failures"]]
        report["failures"] = {"total": len(fails), "shown": fails[:50]}
        if ab:
            fails_b = [f for r in results[1] for f in r["failures"]]
            report["failuresB"] = {"total": len(fails_b), "shown": fails_b[:50]}
    return report


def render_markdown(rep):
    ab = rep["systems"]["B"] is not None
    a_name = rep["systems"]["A"]
    b_name = rep["systems"]["B"]
    L = []
    title = "Examen de nutricion v2 - %s - %s" % (rep["split"].upper(), a_name)
    if ab:
        title += " vs %s" % b_name
    L.append("# " + title)
    L.append("")
    L.append("- Generado: %s | semilla: %d | remuestreos: %d" % (rep["generatedAt"], rep["seed"], rep["bootstrapReplicates"]))
    cov = rep["coverage"]
    L.append("- Casos del split: %d | puntuados (con oro): %d | sin oro (omitidos): %d"
             % (cov["casesInSplit"], cov["casesScored"], cov["casesWithoutGold"]))
    ga = rep["global"]["A"]["counts"]
    L.append("- Sistema A (%s): menciones de oro puntuadas %d, errores del sistema %d (sin linea: %d)"
             % (a_name, ga["m3_den"], ga["errors"], ga["missing"]))
    if ab:
        gb = rep["global"]["B"]["counts"]
        L.append("- Sistema B (%s): menciones de oro puntuadas %d, errores del sistema %d (sin linea: %d)"
                 % (b_name, gb["m3_den"], gb["errors"], gb["missing"]))
    if rep["split"] == "test":
        L.append("- TEST sellado: este informe contiene solo agregados (ningun caso suelto).")
    L.append("")

    if ab:
        acc = rep["acceptance"]
        L.append("## Veredicto: %s" % acc["verdict"])
        L.append("")
        c = acc["criteria"]
        L.append("- IC95 inferior de Delta M3 > 0: %s" % ("si" if c["m3_ci_lower_gt_0"] else "NO"))
        L.append("- IC95 superior de Delta M4 <= +0,5 pp: %s" % ("si" if c["m4_ci_upper_le_0_5pp"] else "NO"))
        L.append("- Ningun fenomeno con >= 30 menciones cae mas de 3 pp en M3: %s"
                 % ("si" if c["no_phenomenon_drops_gt_3pp"] else "NO"))
        for r in acc["reasons"]:
            L.append("- Motivo: " + r)
        for nt in acc["notes"]:
            L.append("- Nota: " + nt)
        L.append("")

    L.append("## Metricas globales")
    L.append("")
    if ab:
        L.append("| Metrica | A | B | Delta (B-A) | IC95 Delta |")
        L.append("|---|---|---|---|---|")
        for key, label, _k, _d, _lower in METRICS:
            ma = rep["global"]["A"]["metrics"][key]
            mb = rep["global"]["B"]["metrics"][key]
            md = rep["global"]["delta"][key]
            L.append("| %s | %s | %s | %s | %s |" % (
                label, _fmt_metric(key, ma["value"]), _fmt_metric(key, mb["value"]),
                _fmt_delta(key, md["value"]), _fmt_ci(key, md["ci95"], delta=True)))
    else:
        L.append("| Metrica | Valor | IC95 |")
        L.append("|---|---|---|")
        for key, label, _k, _d, _lower in METRICS:
            m = rep["global"]["A"]["metrics"][key]
            L.append("| %s | %s | %s |" % (label, _fmt_metric(key, m["value"]), _fmt_ci(key, m["ci95"])))
    L.append("")
    la = rep["global"]["A"]["latencyMs"]
    line = "- Latencia A (ms): p50 %s, p95 %s; mediana de nutrientes presentes por mencion: %s" % (
        _fmt_ms(la["p50"]), _fmt_ms(la["p95"]), _f(rep["global"]["A"]["m8MedianNutrients"]))
    L.append(line)
    if ab:
        lb = rep["global"]["B"]["latencyMs"]
        L.append("- Latencia B (ms): p50 %s, p95 %s; mediana de nutrientes presentes por mencion: %s" % (
            _fmt_ms(lb["p50"]), _fmt_ms(lb["p95"]), _f(rep["global"]["B"]["m8MedianNutrients"])))
    L.append("")

    L.append("## Por fenomeno (M3 y M5 sobre las menciones de los casos con ese codigo)")
    L.append("")
    if ab:
        L.append("| Fenomeno | Menciones | M3 A | M3 B | Delta M3 [IC95] | M5 A | M5 B | Delta M5 [IC95] |")
        L.append("|---|---|---|---|---|---|---|---|")
        for code, e in rep["phenomena"].items():
            row = ["%s %s" % (code, e["name"]), str(e["mentions"])]
            for k in PHEN_METRICS:
                row.append(_fmt_metric(k, e["A"]["metrics"][k]["value"]))
                row.append(_fmt_metric(k, e["B"]["metrics"][k]["value"]))
                row.append("%s %s" % (_fmt_delta(k, e["delta"][k]["value"]),
                                      _fmt_ci(k, e["delta"][k]["ci95"], delta=True)))
            L.append("| " + " | ".join(row) + " |")
        # M3 y M5 se intercalan por fenomeno; ver orden en row
    else:
        L.append("| Fenomeno | Menciones | M3 [IC95] | M5 [IC95] | Latencia p50 / p95 (ms) |")
        L.append("|---|---|---|---|---|")
        for code, e in rep["phenomena"].items():
            m3 = e["A"]["metrics"]["M3"]
            m5 = e["A"]["metrics"]["M5"]
            L.append("| %s %s | %d | %s %s | %s %s | %s / %s |" % (
                code, e["name"], e["mentions"],
                _fmt_metric("M3", m3["value"]), _fmt_ci("M3", m3["ci95"]),
                _fmt_metric("M5", m5["value"]), _fmt_ci("M5", m5["ci95"]),
                _fmt_ms(e["A"]["latencyMs"]["p50"]), _fmt_ms(e["A"]["latencyMs"]["p95"])))
    L.append("")

    if rep["split"] == "dev" and "failures" in rep:
        f = rep["failures"]
        L.append("## Fallos de M3 en DEV - sistema A (%d en total, se muestran %d)" % (f["total"], len(f["shown"])))
        L.append("")
        L.append("| id | texto | mencion | kcal oro | kcal pred | motivo |")
        L.append("|---|---|---|---|---|---|")
        for x in f["shown"]:
            L.append("| %s | %s | %s | %s | %s | %s |" % (
                _esc(x["id"]), _esc(x["text"][:80]), _esc(x["span"][:50]),
                _fmt_kcal(x["goldKcal"]), _fmt_kcal(x["predKcal"]), _esc(x["reason"])))
        L.append("")
        if ab and "failuresB" in rep:
            fb = rep["failuresB"]
            L.append("## Fallos de M3 en DEV - sistema B (%d en total, se muestran %d)" % (fb["total"], len(fb["shown"])))
            L.append("")
            L.append("| id | texto | mencion | kcal oro | kcal pred | motivo |")
            L.append("|---|---|---|---|---|---|")
            for x in fb["shown"]:
                L.append("| %s | %s | %s | %s | %s | %s |" % (
                    _esc(x["id"]), _esc(x["text"][:80]), _esc(x["span"][:50]),
                    _fmt_kcal(x["goldKcal"]), _fmt_kcal(x["predKcal"]), _esc(x["reason"])))
            L.append("")

    L.append("## Notas de metodo")
    L.append("")
    for nt in rep["notes"]:
        L.append("- " + nt)
    L.append("")
    return "\n".join(L)


def output_basename(rep, now=None):
    now = now or datetime.datetime.now()
    stamp = now.strftime("%Y%m%d-%H%M")
    name = "%s-%s-%s" % (stamp, rep["split"], sanitize_filename(rep["systems"]["A"]))
    if rep["systems"]["B"] is not None:
        name += "-vs-" + sanitize_filename(rep["systems"]["B"])
    return name


def main(argv=None):
    ap = argparse.ArgumentParser(description="Puntuador del examen de nutricion v2")
    ap.add_argument("--cases", required=True)
    ap.add_argument("--gold", required=True)
    ap.add_argument("--pred", required=True)
    ap.add_argument("--pred-b", dest="pred_b", default=None)
    ap.add_argument("--split", required=True, choices=["dev", "test"])
    ap.add_argument("--out-dir", dest="out_dir", required=True)
    ap.add_argument("--seed", type=int, default=DEFAULT_SEED)
    ap.add_argument("--boot", type=int, default=DEFAULT_BOOT)
    args = ap.parse_args(argv)

    rep = run(args)
    os.makedirs(args.out_dir, exist_ok=True)
    base = os.path.join(args.out_dir, output_basename(rep))
    md = render_markdown(rep)
    with open(base + ".md", "w", encoding="utf-8", newline="\n") as fh:
        fh.write(md)
    with open(base + ".json", "w", encoding="utf-8", newline="\n") as fh:
        json.dump(rep, fh, ensure_ascii=False, indent=2, sort_keys=False)
        fh.write("\n")

    g = rep["global"]["A"]["metrics"]
    print("Informe: %s.md / .json" % base)
    print("Sistema A (%s): M3 = %s, M4 = %s, M5 = %s"
          % (rep["systems"]["A"], _fmt_metric("M3", g["M3"]["value"]),
             _fmt_metric("M4", g["M4"]["value"]), _fmt_metric("M5", g["M5"]["value"])))
    if rep["acceptance"]:
        print("Veredicto A/B: %s" % rep["acceptance"]["verdict"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
