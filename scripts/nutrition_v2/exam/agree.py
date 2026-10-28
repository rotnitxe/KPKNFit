#!/usr/bin/env python3
"""Acuerdo entre dos etiquetadores del oro del examen (EXAMEN.md secciones 5, 7 y 9).

    python agree.py --a gold_L1.jsonl --b gold_L2.jsonl --out-agreed gold.jsonl \\
                    --out-disagree desacuerdos.jsonl --report acuerdo.json

Empareja las menciones de A y B por caso con el algoritmo de la seccion 7 (tokens sin tildes, plural
simple, sin stopwords; coeficiente de solapamiento sobre span/canonicalEs/aliasesEs; umbral 0,34;
codicioso). Una mencion concuerda si sus kcal difieren <= 15 % del mayor (piso 20 kcal) y cada macro
cae en la tolerancia de la clase mas holgada de las dos. Un caso concuerda si todas sus menciones no
opcionales emparejan y concuerdan y los conjuntos `excluded` coinciden (normalizados; la palabra
«sin» se ignora). Caso concordante -> se escribe la version A. Caso discordante -> linea de
desacuerdo para el adjudicador. Un caso presente en solo uno de los dos oros es desacuerdo.

Solo biblioteca estandar (no importa score.py: la normalizacion se replica aqui).
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import re
import sys
import unicodedata

THRESHOLD = 0.34
KCAL_REL = 0.15
KCAL_FLOOR = 20.0
NUT_KEYS = ("kcal", "protein", "fat", "carbs")
MACRO_KEYS = ("protein", "fat", "carbs")

STOPWORDS = frozenset(
    "de del la el los las un una unos unas con y e al a en por para mi me medio media poco".split()
)
ES_PLURAL_CONSONANTS = frozenset("lnrdzjsx")
EXCLUDED_IGNORED = frozenset(("sin",))

# Tolerancia de nutrientes por clase: (relativa, piso de macro en g). Seccion 5.
TOLERANCES = {
    "EXPLICIT": (0.10, 2.0),
    "CONTAINER": (0.15, 2.0),
    "COUNT": (0.25, 3.0),
    "HOUSEHOLD": (0.25, 3.0),
    "SUBJECTIVE": (0.40, 4.0),
}
CLASS_RANK = {"EXPLICIT": 0, "CONTAINER": 1, "COUNT": 2, "HOUSEHOLD": 3, "SUBJECTIVE": 4}


# --------------------------------------------------------------------------- E/S

def read_jsonl(path):
    rows = []
    with open(path, "r", encoding="utf-8-sig") as stream:
        for number, line in enumerate(stream, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except ValueError:
                raise SystemExit("%s: la linea %d no es JSON valido" % (os.path.basename(str(path)), number))
    return rows


def write_jsonl(path, rows):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")


# --------------------------------------------------------------------------- normalizacion

def _singular(tok):
    if len(tok) > 4 and tok.endswith("es") and tok[-3] in ES_PLURAL_CONSONANTS:
        return tok[:-2]
    if len(tok) > 3 and tok.endswith("s"):
        return tok[:-1]
    return tok


def norm_tokens(text, extra_stop=frozenset()):
    if not isinstance(text, str) or not text:
        return frozenset()
    decomposed = unicodedata.normalize("NFKD", text.lower())
    plain = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    out = set()
    for tok in re.findall(r"[a-z0-9]+", plain):
        if tok in STOPWORDS or tok in extra_stop:
            continue
        out.add(_singular(tok))
    return frozenset(out)


def mention_sets(mention):
    strings = [mention.get("span"), mention.get("canonicalEs")]
    strings.extend(mention.get("aliasesEs") or [])
    sets = []
    for text in strings:
        tokens = norm_tokens(text)
        if tokens:
            sets.append(tokens)
    return sets


def similarity(a_sets, b_sets):
    best = 0.0
    for a in a_sets:
        for b in b_sets:
            value = len(a & b) / min(len(a), len(b))
            if value > best:
                best = value
    return best


def pair_mentions(a_mentions, b_mentions):
    """Emparejamiento codicioso por similitud descendente (umbral 0,34). Devuelve [(i, j, sim)]."""
    a_sets = [mention_sets(m) for m in a_mentions]
    b_sets = [mention_sets(m) for m in b_mentions]
    candidates = []
    for i, left in enumerate(a_sets):
        for j, right in enumerate(b_sets):
            sim = similarity(left, right)
            if sim >= THRESHOLD:
                candidates.append((-sim, i, j))
    candidates.sort()
    used_a, used_b, pairs = set(), set(), []
    for neg, i, j in candidates:
        if i in used_a or j in used_b:
            continue
        used_a.add(i)
        used_b.add(j)
        pairs.append((i, j, -neg))
    pairs.sort()
    return pairs


# --------------------------------------------------------------------------- concordancia

def _num(value):
    if isinstance(value, bool):
        return None
    return value if isinstance(value, (int, float)) else None


def mention_class(mention):
    own = (mention.get("grams") or {}).get("tolerance")
    if own in TOLERANCES:
        return own
    comp = [(c.get("grams") or {}).get("tolerance") for c in mention.get("components") or []
            if isinstance(c, dict)]
    comp = [c for c in comp if c in TOLERANCES]
    return max(comp, key=lambda c: CLASS_RANK[c]) if comp else "SUBJECTIVE"


def looser_class(a, b):
    return a if CLASS_RANK[a] >= CLASS_RANK[b] else b


def compare_mentions(a, b):
    """Lista de diferencias entre dos menciones emparejadas; vacia si concuerdan."""
    diffs = []
    ea = a.get("eaten") or {}
    eb = b.get("eaten") or {}
    cls = looser_class(mention_class(a), mention_class(b))
    rel, floor = TOLERANCES[cls]

    ka, kb = _num(ea.get("kcal")), _num(eb.get("kcal"))
    if ka is None and kb is None:
        pass
    elif ka is None or kb is None:
        diffs.append(("kcal", "kcal no comparable: %s vs %s" % (ka, kb)))
    elif abs(ka - kb) > max(KCAL_REL * max(abs(ka), abs(kb)), KCAL_FLOOR) + 1e-9:
        diffs.append(("kcal", "kcal %s vs %s" % (ka, kb)))

    for key in MACRO_KEYS:
        va, vb = _num(ea.get(key)), _num(eb.get(key))
        if va is None and vb is None:
            continue
        if va is None or vb is None:
            diffs.append((key, "%s no comparable: %s vs %s" % (key, va, vb)))
        elif abs(va - vb) > max(rel * max(abs(va), abs(vb)), floor) + 1e-9:
            diffs.append((key, "%s %s vs %s (clase %s)" % (key, va, vb, cls)))
    return diffs


def _excluded_text(entry):
    if isinstance(entry, str):
        return entry
    if isinstance(entry, dict):
        return " ".join(str(entry.get(k)) for k in ("span", "text", "canonicalEs", "name") if entry.get(k))
    return ""


def excluded_set(entries):
    result = set()
    for entry in entries or []:
        tokens = norm_tokens(_excluded_text(entry), EXCLUDED_IGNORED)
        if tokens:
            result.add(tokens)
    return result


def compare_cases(case_a, case_b):
    """Devuelve (concuerda, motivos, estadisticas). Motivo = (categoria, texto)."""
    a_mentions = case_a.get("mentions") or []
    b_mentions = case_b.get("mentions") or []
    pairs = pair_mentions(a_mentions, b_mentions)
    reasons = []
    stats = {"pairs": len(pairs), "agreedPairs": 0, "unmatchedRequired": 0, "kcalDiffs": []}
    matched_a = {i for i, _, _ in pairs}
    matched_b = {j for _, j, _ in pairs}

    for i, j, _ in pairs:
        ma, mb = a_mentions[i], b_mentions[j]
        if ma.get("optional") and mb.get("optional"):
            continue  # opcional en ambas: no obliga a concordar
        ka = _num((ma.get("eaten") or {}).get("kcal"))
        kb = _num((mb.get("eaten") or {}).get("kcal"))
        if ka is not None and kb is not None:
            stats["kcalDiffs"].append(abs(ka - kb))
        diffs = compare_mentions(ma, mb)
        if diffs:
            for category, text in diffs:
                reasons.append((category, "%s '%s': %s" % (ma.get("mid"), ma.get("span"), text)))
        else:
            stats["agreedPairs"] += 1

    for i, mention in enumerate(a_mentions):
        if i not in matched_a and not mention.get("optional"):
            stats["unmatchedRequired"] += 1
            reasons.append(("faltaEnB", "mencion de A sin pareja en B: %s '%s'" % (
                mention.get("mid"), mention.get("span"))))
    for j, mention in enumerate(b_mentions):
        if j not in matched_b and not mention.get("optional"):
            stats["unmatchedRequired"] += 1
            reasons.append(("faltaEnA", "mencion de B sin pareja en A: %s '%s'" % (
                mention.get("mid"), mention.get("span"))))

    if excluded_set(case_a.get("excluded")) != excluded_set(case_b.get("excluded")):
        reasons.append(("excluded", "excluded distinto: A=%s B=%s" % (
            json.dumps(case_a.get("excluded") or [], ensure_ascii=False),
            json.dumps(case_b.get("excluded") or [], ensure_ascii=False))))
    return (not reasons), reasons, stats


def agree(gold_a, gold_b, text_by_id=None):
    """gold_a, gold_b: listas de casos oro. Devuelve (concordantes, desacuerdos, informe)."""
    text_by_id = text_by_id or {}
    by_id_a = {c["id"]: c for c in gold_a}
    by_id_b = {c["id"]: c for c in gold_b}
    order = list(by_id_a) + [cid for cid in by_id_b if cid not in by_id_a]

    agreed, disagree = [], []
    reason_counts = collections.Counter()
    totals = {"pairs": 0, "agreedPairs": 0, "unmatchedRequired": 0}
    kcal_diffs = []
    for cid in order:
        case_a, case_b = by_id_a.get(cid), by_id_b.get(cid)
        if case_a is None or case_b is None:
            side = "A" if case_a is None else "B"
            reasons = [("casoAusente", "caso ausente en %s (descartado o con error)" % side)]
            ok = False
        else:
            ok, reasons, stats = compare_cases(case_a, case_b)
            for key in totals:
                totals[key] += stats[key]
            kcal_diffs.extend(stats["kcalDiffs"])
        if ok:
            agreed.append(case_a)
            continue
        for category, _ in reasons:
            reason_counts[category] += 1
        disagree.append({
            "id": cid,
            "text": text_by_id.get(cid),
            "a": case_a,
            "b": case_b,
            "reasons": [text for _, text in reasons],
        })

    compared = len(order)
    mention_slots = totals["pairs"] + totals["unmatchedRequired"]
    report = {
        "cases": compared,
        "agreedCases": len(agreed),
        "disagreedCases": len(disagree),
        "caseAgreementRate": (len(agreed) / compared) if compared else None,
        "pairedMentions": totals["pairs"],
        "agreedMentions": totals["agreedPairs"],
        "unmatchedRequiredMentions": totals["unmatchedRequired"],
        "mentionAgreementRate": (totals["agreedPairs"] / mention_slots) if mention_slots else None,
        "meanAbsKcalDiff": (sum(kcal_diffs) / len(kcal_diffs)) if kcal_diffs else None,
        "reasonCounts": dict(reason_counts.most_common()),
        "topReasons": [{"reason": k, "count": v} for k, v in reason_counts.most_common(5)],
    }
    return agreed, disagree, report


# --------------------------------------------------------------------------- CLI

def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Acuerdo entre dos oros de etiquetadores")
    parser.add_argument("--a", required=True, help="oro del etiquetador L1 (.jsonl)")
    parser.add_argument("--b", required=True, help="oro del etiquetador L2 (.jsonl)")
    parser.add_argument("--out-agreed", required=True)
    parser.add_argument("--out-disagree", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--cases", default=None, help="cases/all.jsonl (opcional, para poner el texto)")
    args = parser.parse_args(argv)

    text_by_id = {}
    if args.cases:
        text_by_id = {c["id"]: c.get("text") for c in read_jsonl(args.cases)}
    agreed, disagree, report = agree(read_jsonl(args.a), read_jsonl(args.b), text_by_id)
    write_jsonl(args.out_agreed, agreed)
    write_jsonl(args.out_disagree, disagree)
    os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
    with open(args.report, "w", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    rate = report["caseAgreementRate"]
    print("acuerdo: %d de %d casos (%s)" % (
        report["agreedCases"], report["cases"], "%.1f %%" % (100 * rate) if rate is not None else "-"),
        file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
