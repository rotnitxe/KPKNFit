#!/usr/bin/env python3
"""Convierte borradores de etiqueta (spec/ETIQUETADO.md) en oro del examen (EXAMEN.md seccion 4).

    python gold_build.py --cases cases/all.jsonl --drafts L1_lote1.jsonl L1_lote2.jsonl \\
                         --out gold/dev.jsonl --report work/gold-report-L1.json

Para cada mencion toma `per100g` y `reference` de la ficha `refId` (refindex.RefIndex.get) y calcula
`eaten` = gramos x per100g / 100 (None se conserva), `equivalents` con su ficha y `mealEaten`.
Un error de validacion saca el caso del oro y lo anota en el informe. Si dos borradores traen el
mismo id, gana el ultimo (se anota un aviso).

Solo biblioteca estandar. `build_gold(cases, drafts, index)` acepta cualquier objeto con `get(ref_id)`.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

NUTRIENTS = ("kcal", "protein", "fat", "carbs")
CATEGORIES = frozenset(
    "LACTEOS CARNES AVES PESCADOS_MARISCOS HUEVOS LEGUMBRES CEREALES_PANES PASTAS_ARROZ TUBERCULOS "
    "VERDURAS FRUTAS FRUTOS_SECOS ACEITES_GRASAS AZUCARES_DULCES SNACKS BEBIDAS ALCOHOL SUPLEMENTOS "
    "PLATOS SALSAS_CONDIMENTOS OTROS".split())
STATES = frozenset("NA RAW COOKED FRIED GRILLED BAKED STEAMED".split())
FORMS = frozenset(
    "NA POWDER_DRY RECONSTITUTED CONCENTRATE LIQUID DRIED CANNED_DRAINED CANNED_UNDRAINED LIGHT "
    "SUGAR_FREE".split())
GRANULARITIES = frozenset(("FOOD", "DISH"))
TOLERANCES = ("EXPLICIT", "CONTAINER", "COUNT", "HOUSEHOLD", "SUBJECTIVE")  # de estricta a holgada
AMBIGUITIES = frozenset(("IDENTITY", "STATE", "FORM", "AMOUNT"))


class DraftError(Exception):
    """Errores de validacion de un caso; `.errors` es la lista completa."""

    def __init__(self, errors):
        super().__init__("; ".join(errors))
        self.errors = list(errors)


# --------------------------------------------------------------------------- utilidades

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


def plain(text):
    decomposed = unicodedata.normalize("NFKD", str(text).lower())
    stripped = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    return re.sub(r"\s+", " ", stripped).strip()


def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def eaten_for(ref, grams):
    """kcal/protein/fat/carbs consumidos para `grams` de la ficha; None se conserva."""
    out = {}
    for key in NUTRIENTS:
        value = ref.per100g.get(key)
        out[key] = None if value is None else round(value * grams / 100.0, 1)
    return out


def per100g_of(ref):
    return {key: ref.per100g.get(key) for key in NUTRIENTS}


def sum_eaten(parts):
    """Suma por nutriente; si falta un valor en algun componente, el total es None."""
    total = {}
    for key in NUTRIENTS:
        values = [p.get(key) for p in parts]
        total[key] = None if (not values or any(v is None for v in values)) else round(sum(values), 1)
    return total


def grams_value(raw):
    """Acepta numero o {"target": n}; devuelve float o None."""
    if isinstance(raw, dict):
        raw = raw.get("target")
    return float(raw) if is_number(raw) else None


def loosest(tolerances):
    valid = [t for t in tolerances if t in TOLERANCES]
    return max(valid, key=TOLERANCES.index) if valid else None


# --------------------------------------------------------------------------- conversion

def _check_enum(errors, label, value, allowed):
    if value not in allowed:
        errors.append("%s invalido: %r" % (label, value))


def _lookup(errors, index, ref_id, label):
    if not isinstance(ref_id, str) or not ref_id:
        errors.append("%s: refId ausente" % label)
        return None
    ref = index.get(ref_id)
    if ref is None:
        errors.append("%s: refId inexistente %s" % (label, ref_id))
    return ref


def _convert_equivalents(errors, label, draft_list, index):
    result = []
    for number, eq in enumerate(draft_list or [], 1):
        eq_label = "%s equivalente %d" % (label, number)
        if not isinstance(eq, dict):
            errors.append("%s: no es objeto" % eq_label)
            continue
        ref = _lookup(errors, index, eq.get("refId"), eq_label)
        grams = grams_value(eq.get("grams"))
        if grams is None or grams <= 0:
            errors.append("%s: gramos invalidos %r" % (eq_label, eq.get("grams")))
        if ref is None or grams is None or grams <= 0:
            continue
        item = {"source": ref.source, "code": ref.code, "grams": grams, "per100g": per100g_of(ref)}
        if eq.get("note"):
            item["note"] = eq["note"]
        result.append(item)
    return result


def _convert_component(errors, label, comp, index):
    if not isinstance(comp, dict):
        errors.append("%s: no es objeto" % label)
        return None
    ref = _lookup(errors, index, comp.get("refId"), label)
    grams = grams_value(comp.get("grams"))
    if grams is None or grams <= 0:
        errors.append("%s: gramos invalidos %r" % (label, comp.get("grams")))
    tolerance = comp.get("tolerance")
    _check_enum(errors, "%s tolerance" % label, tolerance, TOLERANCES)
    if not str(comp.get("canonicalEs") or "").strip():
        errors.append("%s: canonicalEs vacio" % label)
    if ref is None or grams is None or grams <= 0:
        return None
    return {
        "canonicalEs": comp.get("canonicalEs"),
        "reference": {"source": ref.source, "code": ref.code, "name": ref.name},
        "per100g": per100g_of(ref),
        "grams": {"target": grams, "tolerance": tolerance},
        "eaten": eaten_for(ref, grams),
    }


def convert_mention(draft, index, position):
    """Devuelve (mencion_oro, avisos_span_no_aplica) o lanza DraftError."""
    errors = []
    mid = draft.get("mid") or "m%d" % position
    label = "mencion %s" % mid
    span = draft.get("span")
    if not isinstance(span, str) or not span.strip():
        errors.append("%s: span vacio" % label)
    _check_enum(errors, "%s category" % label, draft.get("category"), CATEGORIES)
    _check_enum(errors, "%s state" % label, draft.get("state"), STATES)
    _check_enum(errors, "%s form" % label, draft.get("form"), FORMS)
    granularity = draft.get("granularity")
    _check_enum(errors, "%s granularity" % label, granularity, GRANULARITIES)
    ambiguity = draft.get("ambiguity") or []
    if not isinstance(ambiguity, list):
        errors.append("%s: ambiguity no es lista" % label)
        ambiguity = []
    for item in ambiguity:
        _check_enum(errors, "%s ambiguity" % label, item, AMBIGUITIES)
    if not str(draft.get("canonicalEs") or "").strip():
        errors.append("%s: canonicalEs vacio" % label)

    raw_components = draft.get("components") or []
    if granularity == "FOOD" and raw_components:
        errors.append("%s: FOOD no lleva components" % label)
    if granularity == "DISH" and not raw_components and not draft.get("refId"):
        errors.append("%s: DISH sin refId ni components" % label)

    components = []
    if granularity == "DISH":
        for number, comp in enumerate(raw_components, 1):
            converted = _convert_component(errors, "%s componente %d" % (label, number), comp, index)
            components.append(converted)

    ref_id = draft.get("refId")
    ref = None
    if ref_id is not None or granularity == "FOOD":
        ref = _lookup(errors, index, ref_id, label)

    grams = grams_value(draft.get("grams"))
    tolerance = draft.get("tolerance")
    comp_grams = [c["grams"]["target"] for c in components if c]
    if granularity == "DISH" and ref_id is None:
        if grams is None and comp_grams and len(comp_grams) == len(components):
            grams = sum(comp_grams)
        if tolerance is None:
            tolerance = loosest([c["grams"]["tolerance"] for c in components if c])
    if grams is None or grams <= 0:
        errors.append("%s: gramos invalidos %r" % (label, draft.get("grams")))
    _check_enum(errors, "%s tolerance" % label, tolerance, TOLERANCES)

    equivalents = _convert_equivalents(errors, label, draft.get("equivalents"), index)

    if errors:
        raise DraftError(errors)

    mention = {
        "mid": mid,
        "span": span,
        "canonicalEs": draft["canonicalEs"],
        "aliasesEs": list(draft.get("aliasesEs") or []),
        "category": draft["category"],
        "state": draft["state"],
        "form": draft["form"],
        "granularity": granularity,
    }
    if granularity == "DISH":
        mention["allowSplit"] = bool(draft.get("allowSplit", True))
    if ref is not None:
        mention["reference"] = {"source": ref.source, "code": ref.code, "name": ref.name}
        mention["per100g"] = per100g_of(ref)
        mention["grams"] = {"target": grams, "tolerance": tolerance}
        mention["eaten"] = eaten_for(ref, grams)
    else:  # DISH calculado por componentes
        eaten = sum_eaten([c["eaten"] for c in components])
        total_grams = sum(comp_grams)
        mention["reference"] = None
        mention["grams"] = {"target": grams, "tolerance": tolerance}
        mention["eaten"] = eaten
        mention["per100g"] = {
            key: (None if eaten[key] is None or total_grams <= 0 else round(eaten[key] * 100.0 / total_grams, 1))
            for key in NUTRIENTS}
    mention["equivalents"] = equivalents
    mention["ambiguity"] = list(ambiguity)
    mention["components"] = components if granularity == "DISH" else []
    mention["optional"] = bool(draft.get("optional", False))
    rationale = draft.get("rationale")
    if isinstance(rationale, str) and rationale.strip():
        mention["rationale"] = rationale.strip()
    return mention


def convert_case(draft, text, index):
    """Convierte un borrador de caso. Devuelve (caso_oro, avisos). Lanza DraftError."""
    errors = []
    warnings = []
    raw_mentions = draft.get("mentions")
    if not isinstance(raw_mentions, list) or not raw_mentions:
        raise DraftError(["sin menciones"])
    mentions = []
    seen_mids = set()
    for position, raw in enumerate(raw_mentions, 1):
        if not isinstance(raw, dict):
            errors.append("mencion %d: no es objeto" % position)
            continue
        try:
            mention = convert_mention(raw, index, position)
        except DraftError as exc:
            errors.extend(exc.errors)
            continue
        if mention["mid"] in seen_mids:
            errors.append("mid repetido: %s" % mention["mid"])
            continue
        seen_mids.add(mention["mid"])
        if text is not None and plain(mention["span"]) not in plain(text):
            warnings.append("%s: el span %r no es subcadena del texto" % (mention["mid"], mention["span"]))
        mentions.append(mention)
    excluded = draft.get("excluded") or []
    if not isinstance(excluded, list):
        errors.append("excluded no es lista")
        excluded = []
    if errors:
        raise DraftError(errors)
    kcals = [m["eaten"]["kcal"] for m in mentions if m["eaten"]["kcal"] is not None]
    if len(kcals) != len(mentions):
        warnings.append("alguna mencion no tiene kcal en su ficha; mealEaten solo suma las que tienen")
    gold = {"id": draft["id"], "mentions": mentions, "excluded": list(excluded),
            "mealEaten": {"kcal": round(sum(kcals), 1)}}
    if draft.get("labeler"):
        gold["labeler"] = draft["labeler"]
    return gold, warnings


def is_discard(draft):
    return str(draft.get("notes") or "").strip().upper().startswith("DESCARTAR")


def build_gold(cases, drafts, index):
    """cases: lista de casos (cases/all.jsonl); drafts: lista de borradores; index: objeto con get().

    Devuelve (filas_oro, informe). El oro sale en el orden de `cases`.
    """
    case_by_id = {c["id"]: c for c in cases}
    latest = {}
    duplicates = []
    for draft in drafts:
        cid = draft.get("id")
        if cid in latest:
            duplicates.append(cid)
        latest[cid] = draft

    gold_rows = []
    errors = []
    warnings = []
    discarded = []
    unknown = sorted(str(cid) for cid in latest if cid not in case_by_id)
    for case in cases:
        draft = latest.get(case["id"])
        if draft is None:
            continue
        if is_discard(draft):
            discarded.append({"id": case["id"], "reason": str(draft["notes"]).strip()})
            continue
        try:
            gold, case_warnings = convert_case(draft, case.get("text"), index)
        except DraftError as exc:
            errors.append({"id": case["id"], "errors": exc.errors})
            continue
        gold_rows.append(gold)
        for message in case_warnings:
            warnings.append({"id": case["id"], "warning": message})
    for cid in duplicates:
        warnings.append({"id": cid, "warning": "borrador repetido; se usa el ultimo"})

    missing = [c["id"] for c in cases if c["id"] not in latest]
    report = {
        "cases": len(cases),
        "drafts": len(latest),
        "built": len(gold_rows),
        "errors": errors,
        "errorCount": len(errors),
        "discarded": discarded,
        "discardedCount": len(discarded),
        "warnings": warnings,
        "warningCount": len(warnings),
        "withoutDraft": missing,
        "draftsNotInCases": unknown,
    }
    return gold_rows, report


# --------------------------------------------------------------------------- CLI

def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Borradores de etiqueta -> oro del examen")
    parser.add_argument("--cases", required=True, help="cases/all.jsonl")
    parser.add_argument("--drafts", required=True, nargs="+", help="uno o mas borradores .jsonl")
    parser.add_argument("--out", required=True, help="oro de salida .jsonl")
    parser.add_argument("--report", required=True, help="informe .json")
    parser.add_argument("--inputs-dir", default=None)
    parser.add_argument("--locks-dir", default=None)
    parser.add_argument("--cache-dir", default=None)
    args = parser.parse_args(argv)

    import refindex  # noqa: E402  (perezoso: las pruebas no necesitan las fuentes)
    kwargs = {}
    if args.inputs_dir:
        kwargs["inputs_dir"] = args.inputs_dir
    if args.locks_dir:
        kwargs["locks_dir"] = args.locks_dir
    if args.cache_dir:
        kwargs["cache_dir"] = args.cache_dir
    index = refindex.load_index(**kwargs)

    cases = read_jsonl(args.cases)
    drafts = []
    for path in args.drafts:
        drafts.extend(read_jsonl(path))
    gold_rows, report = build_gold(cases, drafts, index)
    write_jsonl(args.out, gold_rows)
    os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
    with open(args.report, "w", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print("oro: %d de %d casos con borrador (errores %d, descartados %d, avisos %d)" % (
        report["built"], report["drafts"], report["errorCount"], report["discardedCount"],
        report["warningCount"]), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
