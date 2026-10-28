"""Valida la etapa 3: python check_stage3.py <archivos.jsonl...> (carga el indice real)."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "exam"))
import schema  # noqa: E402

KCAL_RANGES = {
    "ACEITES_GRASAS": (300, 900), "ALCOHOL": (0, 900), "AZUCARES_DULCES": (0, 600),
    "BEBIDAS": (0, 120), "CARNES": (50, 600), "CEREALES_PANES": (50, 520),
    "FRUTAS": (10, 400), "FRUTOS_SECOS": (100, 750), "HUEVOS": (30, 700),
    "LACTEOS": (10, 750), "LEGUMBRES": (20, 450), "OTROS": (0, 900),
    "PASTAS_ARROZ": (50, 400), "PESCADOS_MARISCOS": (30, 500), "PLATOS": (30, 600),
    "SALSAS_CONDIMENTOS": (0, 900), "SNACKS": (50, 650), "SUPLEMENTOS": (0, 600),
    "TUBERCULOS": (30, 600), "VERDURAS": (5, 350),
}
BEVERAGE_EXEMPT_FORMS = ("POWDER_DRY", "CONCENTRATE")
# medida casera -> palabras de la etiqueta de la ficha
MEASURES = {
    "taza": ("cup", "cups"), "cucharada": ("tbsp", "tablespoon", "tablespoons"),
    "cucharadita": ("tsp", "teaspoon", "teaspoons"), "vaso": ("glass",),
    "rebanada": ("slice", "slices"),
}


def _lead_number(label):
    m = re.match(r"^\s*(\d+(?:\.\d+)?)\s*/\s*(\d+)|^\s*(\d+(?:\.\d+)?)", str(label))
    if not m:
        return 1.0
    if m.group(1):
        return float(m.group(1)) / float(m.group(2))
    return float(m.group(3))


def _ref_portion_per_unit(ref, words):
    out = []
    for label, grams in ref.portions:
        toks = re.findall(r"[a-z]+", str(label).lower())
        if any(w in toks for w in words):
            n = _lead_number(label)
            if n > 0:
                out.append(grams / n)
    return out


def check_concept(obj, index, warnings, errors, loc):
    cid = obj.get("id")
    cat = obj.get("category")
    refs_by_state = {}
    for v in obj.get("variants") or []:
        ref_id = v.get("ref")
        if not ref_id:
            continue
        ref = index.get(ref_id)
        if ref is None:
            errors.append("%s [%s]: ref inexistente %s" % (loc, cid, ref_id))
            continue
        refs_by_state.setdefault(v.get("state"), ref)
        kcal = ref.per100g.get("kcal")
        rng = KCAL_RANGES.get(cat)
        if cat == "BEBIDAS" and v.get("form") in BEVERAGE_EXEMPT_FORMS:
            rng = None
        if rng and kcal is not None and not (rng[0] <= kcal <= rng[1]):
            warnings.append("%s [%s]: %s kcal/100g=%g fuera de %s-%s para %s" % (
                loc, cid, ref_id, kcal, rng[0], rng[1], cat))
        name = ref.name.lower()
        has_raw = re.search(r"\braw\b|\bcru[oa]?s?\b", name) is not None
        has_cooked = re.search(r"\bcooked\b|\bboiled\b|\bcuit[es]?\b", name) is not None
        if v.get("state") == "COOKED" and has_raw and not has_cooked:
            warnings.append("%s [%s]: variante COOKED enlaza ficha cruda: %s (%s)" % (loc, cid, ref_id, ref.name))
        if v.get("state") == "RAW" and has_cooked and not has_raw:
            warnings.append("%s [%s]: variante RAW enlaza ficha cocida: %s (%s)" % (loc, cid, ref_id, ref.name))
    for c in obj.get("components") or []:
        ref_id = c.get("ref")
        if ref_id and index.get(ref_id) is None:
            errors.append("%s [%s]: ref inexistente en componente %s" % (loc, cid, ref_id))
    for p in obj.get("portions") or []:
        words = MEASURES.get(schema.normalize(p.get("label", "")))
        ref = refs_by_state.get(p.get("state"))
        if not words or ref is None or not isinstance(p.get("grams"), (int, float)):
            continue
        cands = _ref_portion_per_unit(ref, words)
        if not cands:
            continue
        nearest = min(cands, key=lambda g: abs(g - p["grams"]) / g)
        if abs(nearest - p["grams"]) / nearest > 0.25:
            warnings.append("%s [%s]: porcion %s=%g g difiere >25%% de la ficha %s (%g g)" % (
                loc, cid, p["label"], p["grams"], ref.id, nearest))


def check_files(paths, index):
    errors, warnings = [], []
    ids = {}
    n = 0
    for path in paths:
        for ln, obj, err in schema.read_jsonl(path):
            loc = "%s:%d" % (os.path.basename(path), ln)
            if err:
                errors.append("%s: %s" % (loc, err))
                continue
            n += 1
            for e in schema.validate_concept(obj, 3):
                errors.append("%s [%s]: %s" % (loc, obj.get("id") if isinstance(obj, dict) else "?", e))
            if not isinstance(obj, dict):
                continue
            if obj.get("id") in ids:
                errors.append("%s: id repetido %r (ya en %s)" % (loc, obj["id"], ids[obj["id"]]))
            ids[obj.get("id")] = loc
            check_concept(obj, index, warnings, errors, loc)
    return {"errors": errors, "warnings": warnings, "concepts": n}


def main(argv=None):
    paths = list(sys.argv[1:] if argv is None else argv)
    if not paths:
        print("uso: check_stage3.py <archivos.jsonl...>", file=sys.stderr)
        return 2
    import refindex
    r = check_files(paths, refindex.load_index())
    for w in r["warnings"]:
        print("AVISO", w)
    for e in r["errors"]:
        print("ERROR", e)
    print("%d conceptos, %d errores, %d avisos" % (r["concepts"], len(r["errors"]), len(r["warnings"])))
    return 1 if r["errors"] else 0


if __name__ == "__main__":
    for s in (sys.stdout, sys.stderr):
        if hasattr(s, "reconfigure"):
            s.reconfigure(encoding="utf-8")
    sys.exit(main())
