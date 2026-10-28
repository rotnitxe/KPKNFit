#!/usr/bin/env python3
"""Acuerdo entre dos etiquetadores del oro del examen (EXAMEN.md secciones 4, 5, 7 y 9).

    python agree.py --a gold_L1.jsonl --b gold_L2.jsonl --out-agreed gold.jsonl \\
                    --out-disagree desacuerdos.jsonl --report acuerdo.json

Criterio coherente con score.py:

1. Emparejamiento por caso: mencion con mencion (tokens sin tildes, plural simple, sin stopwords;
   coeficiente de solapamiento sobre span/canonicalEs/aliasesEs; umbral 0,34; codicioso) MAS
   plato = componentes: un DISH con allowSplit de un lado empareja con el grupo de menciones del otro
   lado cuyos nombres empatan con sus `components` (misma logica que la fase 2 de score.py: si el
   emparejamiento entero no concuerda y el grupo si, se usa el grupo). DISH contra DISH empareja normal.
2. Una pareja concuerda si kcal, proteina, grasa y carbohidratos consumidos (`eaten`, sumados si es
   grupo) caen dentro de la tolerancia de la clase mas holgada de las dos (tabla TOLERANCES, relativa
   con piso, igual a score.py) medida sobre el PROMEDIO de los dos valores.
3. Un caso concuerda si toda pareja no opcional-en-ambos concuerda, no queda mencion obligatoria sin
   pareja y los `excluded` coinciden (normalizados; la palabra «sin» se ignora). Las menciones
   opcionales sin pareja se ignoran para decidir (y pasan tal cual al oro).
4. Oro de un caso concordante: estructura del lado que tenga DISH en la pareja (si ambos o ninguno, A);
   `eaten` = promedio de A y B; `grams.target` = promedio si ambos son numericos y la pareja es
   mencion-mencion, si no el del lado elegido; `reference`/`per100g` del lado elegido; la ficha del otro
   lado se agrega a `equivalents` si su codigo es distinto; `ambiguity` = union; `mealEaten` = suma.
   El caso lleva `"agreement": {"labelers": ["L1", "L2"], "method": "mean"}`.
5. Caso discordante -> linea de desacuerdo para el adjudicador. Un caso presente en solo uno de los dos
   oros es desacuerdo.

Solo biblioteca estandar (no importa score.py: la normalizacion y las tolerancias se replican aqui).
"""
from __future__ import annotations

import argparse
import collections
import copy
import json
import os
import re
import sys
import unicodedata

THRESHOLD = 0.34
NUT_KEYS = ("kcal", "protein", "fat", "carbs")

STOPWORDS = frozenset(
    "de del la el los las un una unos unas con y e al a en por para mi me medio media poco".split()
)
ES_PLURAL_CONSONANTS = frozenset("lnrdzjsx")
EXCLUDED_IGNORED = frozenset(("sin",))

# Tolerancia de nutrientes por clase (identica a TOLERANCES de score.py, seccion 5):
# relativa, piso de kcal y piso de cada macro (g).
TOLERANCES = {
    "EXPLICIT": {"nut": 0.10, "kcal": 15.0, "macro": 2.0},
    "CONTAINER": {"nut": 0.15, "kcal": 20.0, "macro": 2.0},
    "COUNT": {"nut": 0.25, "kcal": 25.0, "macro": 3.0},
    "HOUSEHOLD": {"nut": 0.25, "kcal": 25.0, "macro": 3.0},
    "SUBJECTIVE": {"nut": 0.40, "kcal": 40.0, "macro": 4.0},
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


def token_sets(strings):
    sets = []
    for text in strings:
        tokens = norm_tokens(text)
        if tokens:
            sets.append(tokens)
    return sets


def mention_sets(mention):
    strings = [mention.get("span"), mention.get("canonicalEs")]
    strings.extend(mention.get("aliasesEs") or [])
    return token_sets(strings)


def similarity(a_sets, b_sets):
    best = 0.0
    for a in a_sets:
        for b in b_sets:
            value = len(a & b) / min(len(a), len(b))
            if value > best:
                best = value
    return best


def pair_mentions(a_mentions, b_mentions):
    """Emparejamiento codicioso uno a uno por similitud descendente (umbral 0,34). [(i, j, sim)]."""
    a_sets = [mention_sets(m) for m in a_mentions]
    b_sets = [mention_sets(m) for m in b_mentions]
    candidates = []
    for i, left in enumerate(a_sets):
        for j, right in enumerate(b_sets):
            sim = similarity(left, right)
            if sim >= THRESHOLD - 1e-12:
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


# --------------------------------------------------------------------------- clases y concordancia

def _num(value):
    if isinstance(value, bool):
        return None
    return value if isinstance(value, (int, float)) else None


def mention_class(mention, group=False):
    """Clase de tolerancia de una mencion (mismo criterio que score.mention_class)."""
    comp = [(c.get("grams") or {}).get("tolerance") for c in mention.get("components") or []
            if isinstance(c, dict)]
    comp = [c for c in comp if c in TOLERANCES]
    own = (mention.get("grams") or {}).get("tolerance")
    if group and comp:
        return max(comp, key=lambda c: CLASS_RANK[c])
    if own in TOLERANCES:
        return own
    if comp:
        return max(comp, key=lambda c: CLASS_RANK[c])
    return "SUBJECTIVE"


def looser_class(a, b):
    return a if CLASS_RANK[a] >= CLASS_RANK[b] else b


def is_split_dish(mention):
    return (mention.get("granularity") == "DISH" and bool(mention.get("allowSplit"))
            and any(isinstance(c, dict) for c in mention.get("components") or []))


def sum_eaten(mentions):
    out = {}
    for key in NUT_KEYS:
        values = [_num((m.get("eaten") or {}).get(key)) for m in mentions]
        values = [v for v in values if v is not None]
        out[key] = sum(values) if values else None
    return out


def side_class(mentions, as_group):
    """Clase mas holgada entre las menciones de un lado; un plato que empareja un grupo usa sus componentes."""
    classes = [mention_class(m, group=(as_group and is_split_dish(m))) for m in mentions]
    return max(classes, key=lambda c: CLASS_RANK[c]) if classes else "SUBJECTIVE"


def _fmt(value):
    return ("%g" % round(value, 2)) if isinstance(value, (int, float)) else str(value)


def compare_groups(a_mentions, b_mentions, grouped=False):
    """Diferencias entre dos lados emparejados (listas de menciones); vacia si concuerdan.

    Cada nutriente consumido (sumado en un grupo) debe quedar dentro de la tolerancia de la clase mas
    holgada de las dos, medida sobre el promedio de los dos valores.
    """
    diffs = []
    cls = looser_class(side_class(a_mentions, grouped), side_class(b_mentions, grouped))
    tol = TOLERANCES[cls]
    ea, eb = sum_eaten(a_mentions), sum_eaten(b_mentions)
    for key in NUT_KEYS:
        va, vb = ea[key], eb[key]
        if va is None and vb is None:
            continue
        if va is None or vb is None:
            diffs.append((key, "%s no comparable: %s vs %s" % (key, va, vb)))
            continue
        floor = tol["kcal"] if key == "kcal" else tol["macro"]
        mean = (abs(va) + abs(vb)) / 2.0
        if abs(va - vb) > max(tol["nut"] * mean, floor) + 1e-9:
            diffs.append((key, "%s %s vs %s (clase %s)" % (key, _fmt(va), _fmt(vb), cls)))
    return diffs


def compare_mentions(a, b):
    """Diferencias entre dos menciones emparejadas una a una; vacia si concuerdan."""
    return compare_groups([a], [b], grouped=False)


# --------------------------------------------------------------------------- emparejamiento con platos

class Pair:
    __slots__ = ("a", "b", "kind", "dish_side")

    def __init__(self, a, b, kind="whole", dish_side=None):
        self.a = list(a)
        self.b = list(b)
        self.kind = kind
        self.dish_side = dish_side


def _pair_diffs(pair, a_mentions, b_mentions):
    return compare_groups([a_mentions[i] for i in pair.a], [b_mentions[j] for j in pair.b],
                          grouped=pair.kind == "group")


def build_pairs(a_mentions, b_mentions):
    """Mencion-mencion (fase 1) y plato = componentes (fase 2, como score.py). Devuelve [Pair]."""
    pairs = [Pair([i], [j]) for i, j, _ in pair_mentions(a_mentions, b_mentions)]
    owner = {"A": {}, "B": {}}
    for pair in pairs:
        owner["A"][pair.a[0]] = pair
        owner["B"][pair.b[0]] = pair
    sides = {"A": a_mentions, "B": b_mentions}
    other_of = {"A": "B", "B": "A"}
    tokens = {side: [mention_sets(m) for m in ms] for side, ms in sides.items()}

    def make(side, idx, group):
        return Pair([idx] if side == "A" else group, group if side == "A" else [idx], "group", side)

    for side in ("A", "B"):
        other = other_of[side]
        for idx, dish in enumerate(sides[side]):
            if not is_split_dish(dish):
                continue
            comp_toks = [token_sets([c.get("canonicalEs")] + list(c.get("aliasesEs") or []))
                         for c in dish.get("components") or [] if isinstance(c, dict)]
            comp_toks = [t for t in comp_toks if t]
            if not comp_toks:
                continue

            def group_from(candidates, comp_toks=comp_toks, other=other):
                return sorted(j for j in candidates
                              if any(similarity(tokens[other][j], ct) >= THRESHOLD - 1e-12
                                     for ct in comp_toks))

            free = [j for j in range(len(sides[other])) if j not in owner[other]]
            current = owner[side].get(idx)
            if current is None:
                group = group_from(free)
                if group:
                    pair = make(side, idx, group)
                    pairs.append(pair)
                    owner[side][idx] = pair
                    for j in group:
                        owner[other][j] = pair
                continue
            if current.kind == "group":
                continue
            whole = current.b if side == "A" else current.a
            if any(sides[other][j].get("granularity") == "DISH" for j in whole):
                continue  # plato contra plato: empareja normal
            if not _pair_diffs(current, a_mentions, b_mentions):
                continue  # el emparejamiento entero ya concuerda
            group = group_from(free + whole)
            if group and set(group) != set(whole):
                candidate = make(side, idx, group)
                if not _pair_diffs(candidate, a_mentions, b_mentions):
                    for j in whole:
                        owner[other].pop(j, None)
                    pairs.remove(current)
                    pairs.append(candidate)
                    owner[side][idx] = candidate
                    for j in group:
                        owner[other][j] = candidate
    pairs.sort(key=lambda p: (min(p.a), min(p.b)))
    return pairs


def _pair_optional(pair, a_mentions, b_mentions):
    """Opcional en ambos lados: todas las menciones de cada lado son opcionales."""
    return (all(a_mentions[i].get("optional") for i in pair.a)
            and all(b_mentions[j].get("optional") for j in pair.b))


# --------------------------------------------------------------------------- excluidos

def _excluded_text(entry):
    if isinstance(entry, str):
        return entry
    if isinstance(entry, dict):
        return " ".join(str(entry.get(k)) for k in ("span", "text", "canonicalEs", "name") if entry.get(k))
    return ""


def excluded_set(entries):
    result = set()
    for entry in entries or []:
        tokens_ = norm_tokens(_excluded_text(entry), EXCLUDED_IGNORED)
        if tokens_:
            result.add(tokens_)
    return result


# --------------------------------------------------------------------------- comparacion de casos

def compare_cases(case_a, case_b):
    """Devuelve (concuerda, motivos, estadisticas, parejas). Motivo = (categoria, texto)."""
    a_mentions = case_a.get("mentions") or []
    b_mentions = case_b.get("mentions") or []
    pairs = build_pairs(a_mentions, b_mentions)
    reasons = []
    stats = {"pairs": 0, "agreedPairs": 0, "unmatchedRequired": 0, "kcalDiffs": [],
             "dishComponentPairs": 0, "agreedDishComponentPairs": 0}
    matched_a = {i for p in pairs for i in p.a}
    matched_b = {j for p in pairs for j in p.b}

    for pair in pairs:
        if _pair_optional(pair, a_mentions, b_mentions):
            continue  # opcional en ambos lados: no obliga a concordar ni cuenta
        left = [a_mentions[i] for i in pair.a]
        right = [b_mentions[j] for j in pair.b]
        stats["pairs"] += 1
        is_group = pair.kind == "group"
        if is_group:
            stats["dishComponentPairs"] += 1
        ka, kb = sum_eaten(left)["kcal"], sum_eaten(right)["kcal"]
        if ka is not None and kb is not None:
            stats["kcalDiffs"].append(abs(ka - kb))
        diffs = compare_groups(left, right, grouped=is_group)
        if diffs:
            label = left[0]
            for category, text in diffs:
                reasons.append((category, "%s '%s': %s" % (label.get("mid"), label.get("span"), text)))
        else:
            stats["agreedPairs"] += 1
            if is_group:
                stats["agreedDishComponentPairs"] += 1

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
    return (not reasons), reasons, stats, pairs


# --------------------------------------------------------------------------- construccion del oro

def _mean(x, y):
    if x is None:
        return y
    if y is None:
        return x
    return (x + y) / 2.0


def _round(value, digits=2):
    return None if value is None else round(value, digits)


def _equivalent_of(mention):
    """Ficha de una mencion como equivalente {source, code, grams, per100g}, o None si no la tiene."""
    ref = mention.get("reference") or {}
    per100 = mention.get("per100g")
    grams = _num((mention.get("grams") or {}).get("target"))
    if not ref.get("source") or not ref.get("code") or not isinstance(per100, dict) or grams is None:
        return None
    return {"source": ref["source"], "code": ref["code"], "grams": grams, "per100g": copy.deepcopy(per100)}


def merge_pair(left, right, pair):
    """Mencion de oro de una pareja concordante: promedio de A y B, estructura del lado con DISH."""
    if pair.dish_side == "B":
        use_b = True
    elif pair.dish_side == "A":
        use_b = False
    else:
        use_b = (right[0].get("granularity") == "DISH" and left[0].get("granularity") != "DISH")
    chosen_side, other_side = (right, left) if use_b else (left, right)
    chosen = copy.deepcopy(chosen_side[0])

    totals_chosen, totals_other = sum_eaten(chosen_side), sum_eaten(other_side)
    chosen["eaten"] = {k: _round(_mean(totals_chosen[k], totals_other[k])) for k in NUT_KEYS}

    if len(left) == 1 and len(right) == 1:  # mencion con mencion: misma forma
        g_chosen = _num((chosen.get("grams") or {}).get("target"))
        g_other = _num((other_side[0].get("grams") or {}).get("target"))
        if g_chosen is not None and g_other is not None:
            grams = dict(chosen.get("grams") or {})
            grams["target"] = _round(_mean(g_chosen, g_other))
            chosen["grams"] = grams
        # la ficha del otro lado queda como equivalente si su codigo es distinto
        extra = _equivalent_of(other_side[0])
        own_ref = chosen.get("reference") or {}
        equivalents = list(chosen.get("equivalents") or [])
        known = {(own_ref.get("source"), own_ref.get("code"))}
        known.update((e.get("source"), e.get("code")) for e in equivalents if isinstance(e, dict))
        if extra is not None and (extra["source"], extra["code"]) not in known:
            equivalents.append(extra)
        if equivalents or "equivalents" in chosen:
            chosen["equivalents"] = equivalents

    ambiguity = []
    for mention in left + right:
        for item in mention.get("ambiguity") or []:
            if item not in ambiguity:
                ambiguity.append(item)
    chosen["ambiguity"] = ambiguity
    chosen["optional"] = all(bool(m.get("optional")) for m in left + right)
    return chosen


def build_gold(case_a, case_b, pairs):
    """Caso de oro promedio de dos etiquetadores que concuerdan."""
    a_mentions = case_a.get("mentions") or []
    b_mentions = case_b.get("mentions") or []
    entries = []
    for pair in pairs:
        left = [a_mentions[i] for i in pair.a]
        right = [b_mentions[j] for j in pair.b]
        entries.append(((0, min(pair.a), 0), merge_pair(left, right, pair)))
    matched_a = {i for p in pairs for i in p.a}
    matched_b = {j for p in pairs for j in p.b}
    for i, mention in enumerate(a_mentions):
        if i not in matched_a:  # solo llegan aqui opcionales sin pareja
            entries.append(((0, i, 1), copy.deepcopy(mention)))
    for j, mention in enumerate(b_mentions):
        if j not in matched_b:
            entries.append(((1, j, 1), copy.deepcopy(mention)))
    entries.sort(key=lambda item: item[0])
    mentions = [m for _, m in entries]
    seen = set()
    for mention in mentions:
        mid = mention.get("mid")
        while mid in seen:
            mid = "%sb" % mid
        seen.add(mid)
        mention["mid"] = mid

    gold = copy.deepcopy(case_a)
    gold.pop("labeler", None)
    gold["mentions"] = mentions
    kcals = [_num((m.get("eaten") or {}).get("kcal")) for m in mentions]
    gold["mealEaten"] = {"kcal": round(sum(k for k in kcals if k is not None), 1)}
    gold["agreement"] = {"labelers": ["L1", "L2"], "method": "mean"}
    return gold


def agree(gold_a, gold_b, text_by_id=None):
    """gold_a, gold_b: listas de casos oro. Devuelve (concordantes, desacuerdos, informe)."""
    text_by_id = text_by_id or {}
    by_id_a = {c["id"]: c for c in gold_a}
    by_id_b = {c["id"]: c for c in gold_b}
    order = list(by_id_a) + [cid for cid in by_id_b if cid not in by_id_a]

    agreed, disagree = [], []
    reason_counts = collections.Counter()
    totals = {"pairs": 0, "agreedPairs": 0, "unmatchedRequired": 0,
              "dishComponentPairs": 0, "agreedDishComponentPairs": 0}
    kcal_diffs = []
    for cid in order:
        case_a, case_b = by_id_a.get(cid), by_id_b.get(cid)
        pairs = []
        if case_a is None or case_b is None:
            side = "A" if case_a is None else "B"
            reasons = [("casoAusente", "caso ausente en %s (descartado o con error)" % side)]
            ok = False
        else:
            ok, reasons, stats, pairs = compare_cases(case_a, case_b)
            for key in totals:
                totals[key] += stats[key]
            kcal_diffs.extend(stats["kcalDiffs"])
        if ok:
            agreed.append(build_gold(case_a, case_b, pairs))
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
        "dishComponentPairs": totals["dishComponentPairs"],
        "agreedDishComponentPairs": totals["agreedDishComponentPairs"],
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
