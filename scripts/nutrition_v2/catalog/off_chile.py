"""OFF Chile (TSV sin cabecera, 210 columnas) para el paquete de catalogo v0.

- Acepta exactamente los productos que acepta `FoodImporter.parseOffLine` (mismos filtros).
- Ademas exige las 4 macros presentes y legibles (el paquete solo guarda fichas calculables).
- Las columnas de micronutrientes salen del orden estandar del CSV de Open Food Facts anclado en los
  indices que ya usa la app, y SOLO entran si pasan pruebas con los datos (coherencia entre columnas y
  magnitudes en alimentos conocidos). El informe guarda la evidencia de cada columna.
"""
import math
import re
import statistics

import nutrients as N

# Indices que ya usa FoodImporter.kt (0-indexados, separados por tabulador).
COL_CODE, COL_NAME, COL_QUANTITY, COL_BRAND = 0, 10, 13, 18
COL_SERVING_SIZE, COL_SERVING_QTY = 50, 51
COL_PNNS1, COL_PNNS2 = 60, 61
COL_KCAL, COL_FAT, COL_CARBS, COL_SUGARS, COL_FIBER, COL_PROTEIN, COL_SODIUM = 89, 92, 129, 130, 146, 150, 156
COL_SALT = 154          # salt_100g (solo evidencia: sal ~ 2,5 x sodio)
MIN_COLUMNS = COL_SODIUM + 1
MAX_ENERGY_DEVIATION = 0.5   # igual que OFF_MAX_ENERGY_DEVIATION de la app

_NUMBER = re.compile(r"^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$")
_VOLUME_QTY = re.compile(r"(\d+(?:[.,]\d+)?)\s*(ml|cl|dl|l|fl\.?\s?oz)\b", re.IGNORECASE)
_MASS_QTY = re.compile(r"(\d+(?:[.,]\d+)?)\s*(g|gr|kg|mg|oz|lb)\b", re.IGNORECASE)


def parse_float(text):
    """Como toDoubleOrNull + isFinite de la app, sin las rarezas de float() de Python."""
    text = (text or "").strip()
    if not _NUMBER.match(text):
        return None
    value = float(text)
    return value if math.isfinite(value) else None


# Candidatos: (columna, clave de nutriente, pruebas). Las anclas (indices de la app) no se excluyen,
# pero su evidencia tambien se guarda. Pruebas, todas sobre g/100 g crudos del CSV:
#  ("le", ref_col, tolerancia_g, cuota)            valor <= referencia + tol en >= cuota de las filas con ambos
#  ("lt", limite_g, cuota)                         valor < limite en >= cuota de los numericos
#  ("kwq", regex, cuantil, lo, hi, min_filas)      cuantil de los valores de productos cuyo nombre encaja
#  ("kwgt", regex, otra_col, min_filas)            mediana(col - otra) > 0 en productos que encajan
#  ("top", regex, n)                               entre los n mayores valores plausibles, alguno encaja
#  ("salt",)                                       sal ~ 2,5 x sodio (columna 154) en >= 95 % de las filas
DAIRY = r"yogur|yogurt|yoghurt|leche|milk|queso|cheese|lacteo|lácteo"
CEREAL = r"cereal|avena|oat|lenteja|lentil|corn flakes|trigo|nestum|chocapic|granola"
NUTS = r"almendra|almond|nuez|nueces|nut|walnuss|mani\b|maní|cashew|castaña|pistacho|pistachio|avellana|hazelnut"
OILS = r"aceite|oil|almendra|almond|nuez|nueces|walnuss|nut|cereal|margarina|margarine"
MEAT = r"carne|vacuno|beef|cerdo|pork|cereal|avena|oat|pollo|chicken"
FISH = r"atun|atún|tuna|pescado|fish|salm|merluza|jurel|sardina|huevo|egg|pulpo|moules|mejill"
DRINK_ALC = r"vino|wine|pisco|cerveza|beer|whisk|vodka|licor|liqueur|ron\b|gin\b|tequila|fernet|sour|merlot|cabernet|pinot|reserva"

CANDIDATES = (
    # anclas de la app
    (COL_KCAL, "energy", (("lt", 1000.0, 0.99),)),
    (COL_FAT, "fat", (("lt", 100.0, 0.99),)),
    (COL_CARBS, "carbs", (("lt", 100.0, 0.99),)),
    (COL_SUGARS, "sugars", (("le", COL_CARBS, 0.5, 0.99),)),
    (COL_FIBER, "fiber", (("le", COL_CARBS, 0.5, 0.99),)),
    (COL_PROTEIN, "protein", (("lt", 100.0, 0.99),)),
    (COL_SODIUM, "sodium", (("lt", 5.0, 0.99), ("salt",))),
    # verificables por coherencia entre columnas
    (93, "sat_fat", (("le", COL_FAT, 0.5, 0.99),)),
    (109, "mono_fat", (("le", COL_FAT, 0.5, 0.99), ("kwgt", r"oliva|olive", 111, 3))),
    (111, "poly_fat", (("le", COL_FAT, 0.5, 0.99), ("top", r"omega|fish oil|aceite de pescado|walnuss|walnut|noci|girasol|sunflower|mayo", 10))),
    (127, "trans_fat", (("le", COL_FAT, 0.5, 0.99),)),
    (128, "cholesterol", (("lt", 1.0, 0.99), ("kwq", r"huevo|egg", 0.9, 0.15, 0.6, 5))),
    (131, "added_sugars", (("le", COL_SUGARS, 0.5, 0.99),)),
    # verificables por magnitud en alimentos conocidos (g/100 g)
    (157, "alcohol", (("lt", 100.0, 0.99), ("kwq", DRINK_ALC, 0.9, 10.0, 45.0, 5))),
    (158, "vitamin_a_rae", (("kwq", DAIRY + "|" + r"zanahoria|carrot|huevo|egg|margarin|" + CEREAL, 0.75, 1e-6, 1e-3, 5),)),
    (160, "vitamin_d", (("kwq", DAIRY + "|" + r"salm|margarin", 0.9, 1e-6, 5e-5, 5),)),
    (161, "vitamin_e", (("kwq", OILS, 0.5, 5e-4, 3e-2, 5),)),
    (162, "vitamin_k", (("top", r"f[oó]rmula|nutri|sustagen|equilibrad|espinaca|spinach|kale|br[oó]coli|broccoli|lechuga|lettuce|aceite|oil|multi", 5),)),
    (163, "vitamin_c", (("kwq", r"jugo|juice|zumo|naranja|orange|lim[oó]n|lemon", 0.5, 5e-4, 0.1, 5),)),
    (164, "thiamin", (("kwq", CEREAL + "|" + r"pan\b|bread", 0.5, 3e-5, 3e-3, 5),)),
    (165, "riboflavin", (("kwq", CEREAL + "|" + DAIRY, 0.5, 3e-5, 3e-3, 5),)),
    (166, "niacin", (("kwq", CEREAL + "|" + r"pollo|chicken|atun|tuna|mani|peanut", 0.5, 1e-3, 5e-2, 5),)),
    (167, "vitamin_b6", (("kwq", CEREAL + "|" + r"pollo|chicken|banana|papa|potato", 0.5, 3e-5, 3e-3, 5),)),
    (168, "folate_dfe", (("kwq", CEREAL + "|" + r"pan\b|bread|espinaca|spinach", 0.5, 3e-6, 1e-3, 5),)),
    (169, "folate_dfe", (("kwq", CEREAL + "|" + r"pan\b|bread|espinaca|spinach", 0.5, 3e-6, 1e-3, 5),)),
    (170, "vitamin_b12", (("kwq", DAIRY + "|" + r"huevo|egg|pescado|fish|carne|beef|cereal", 0.5, 1e-7, 5e-5, 5),)),
    (175, "potassium", (("kwq", r"banana|platano|plátano|papa|potato|patata", 0.5, 0.15, 0.8, 5),)),
    (177, "calcium", (("kwq", DAIRY, 0.5, 0.05, 0.3, 20),)),
    (178, "phosphorus", (("kwq", r"yogur|yogurt|yoghurt|queso|cheese", 0.9, 0.05, 1.0, 5), ("top", r"levadura|yeast|f[oó]rmula|nutri|alimento equilibrado|cacao|nido|leche en polvo", 5))),
    (179, "iron", (("kwq", CEREAL, 0.5, 5e-4, 2e-2, 5),)),
    (180, "magnesium", (("kwq", NUTS, 0.5, 0.05, 0.4, 5),)),
    (181, "zinc", (("kwq", MEAT, 0.5, 3e-4, 1e-2, 5),)),
    (185, "selenium", (("kwq", FISH, 0.5, 5e-6, 2e-4, 5),)),
    (188, "iodine", (("kwq", r"\bsal\b|^sal |salt", 0.9, 1e-5, 1e-2, 3),)),
    (189, "caffeine", (("top", r"caf[eé]|coffee|punch|cold brew|energy|energ|cola|power|\bte\b|tea|nescaf", 5),)),
    (113, "ala", (("lt", 100.0, 0.99),)),
)
ANCHORS = {COL_KCAL, COL_FAT, COL_CARBS, COL_SUGARS, COL_FIBER, COL_PROTEIN, COL_SODIUM}
MIN_NUMERIC_VALUES = 30      # menos datos que esto no permite verificar una columna
MIN_IN_BOUNDS = 0.97     # el 1-3 % de basura (p. ej. mg capturados como g) se descarta valor a valor

# g -> unidad del nutriente
_UNIT_FACTOR = {"kcal": 1.0, "g": 1.0, "mg": 1e3, N.UG: 1e6}


def factor_for(key):
    return _UNIT_FACTOR[N.BY_KEY[key].unit]


# --------------------------------------------------------------------------------------------
# Filtros de la app + extraccion
# --------------------------------------------------------------------------------------------
def split_lines(text):
    """Igual que BufferedReader.lineSequence(): separa por \\n, \\r o \\r\\n."""
    lines = re.split(r"\r\n|\r|\n", text)
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def app_accepts(parts):
    """Replica FoodImporter.parseOffLine. Devuelve (True, None) o (False, motivo)."""
    if len(parts) <= COL_SODIUM:
        return False, "few_columns"
    if not parts[COL_CODE].strip():
        return False, "no_code"
    if not parts[COL_NAME].strip():
        return False, "no_name"

    def bounded(index, maximum):
        value = parse_float(parts[index])
        return value if value is not None and 0.0 <= value <= maximum else 0.0

    kcal = bounded(COL_KCAL, 1000.0)
    protein, fat, carbs = bounded(COL_PROTEIN, 100.0), bounded(COL_FAT, 100.0), bounded(COL_CARBS, 100.0)
    sodium = parse_float(parts[COL_SODIUM])
    sodium = sodium if sodium is not None and 0.0 <= sodium <= 5.0 else None
    if parts[COL_SODIUM].strip() and sodium is None:
        return False, "sodium_unreadable"
    macro = protein * 4.0 + fat * 9.0 + carbs * 4.0
    if not (kcal > 0.0 and macro > 0.0):
        return False, "no_energy_or_macros"
    if abs(kcal - macro) / macro > MAX_ENERGY_DEVIATION:
        return False, "energy_inconsistent"
    return True, None


def basis_for(parts):
    """('ml'|'g', form): OFF declara por 100 ml cuando el envase se expresa en volumen."""
    quantity = parts[COL_QUANTITY] if len(parts) > COL_QUANTITY else ""
    if _VOLUME_QTY.search(quantity) and not _MASS_QTY.search(quantity):
        return "ml", "LIQUID"
    return "g", None


def category_for(parts):
    for index in (COL_PNNS2, COL_PNNS1):
        if len(parts) > index:
            value = parts[index].strip()
            if value and value.lower() != "unknown":
                return value
    return None


def serving_portion(parts, basis_unit):
    if basis_unit == "ml" or len(parts) <= COL_SERVING_QTY:
        return None
    label = " ".join(parts[COL_SERVING_SIZE].split())
    quantity = parse_float(parts[COL_SERVING_QTY])
    if not label or quantity is None or not (0 < quantity <= 5000) or _VOLUME_QTY.search(label):
        return None
    return (label, round(quantity, 3), None)


def extract_record(parts, columns):
    """Ficha de un producto aceptado. `columns` = {indice: (id de nutriente, factor)}.
    Devuelve (registro | None, motivo). Valores fuera de rango quedan ausentes (se cuentan)."""
    values, rejected = {}, []
    for index, (nutrient_id, factor) in columns.items():
        if index >= len(parts):
            continue
        raw = parse_float(parts[index])
        if raw is None:
            continue
        amount = N.clean_value(round(raw * factor, 6), nutrient_id)
        if amount is None:
            rejected.append(nutrient_id)
            continue
        # Primer candidato con valor gana (p. ej. folatos 168 antes que 169).
        values.setdefault(nutrient_id, (amount, N.Q_DECLARED))
    if any(i not in values for i in N.REQUIRED_IDS):
        return None, "missing_required_macro", rejected
    basis_unit, form = basis_for(parts)
    brand = parts[COL_BRAND].split(",")[0].strip() or None
    name = " ".join(parts[COL_NAME].split())
    code = parts[COL_CODE].strip()
    portion = serving_portion(parts, basis_unit)
    return {
        "id": "off_" + code, "source": "OFF_CL", "source_record_id": code, "gtin": code,
        "name": name, "name_es": name, "name_en": None, "name_fr": None, "brand": brand,
        "category": category_for(parts), "preparation": "AS_SOLD", "form": form or "NA",
        "basis_unit": basis_unit, "density": None, "nutrients": values,
        "portions": [portion] if portion else [],
        "aliases": [],
    }, None, rejected


# --------------------------------------------------------------------------------------------
# Verificacion de columnas
# --------------------------------------------------------------------------------------------
def _numeric_column(rows, index):
    return [parse_float(r[index]) if len(r) > index else None for r in rows]


def _quantile(sorted_values, q):
    return sorted_values[min(len(sorted_values) - 1, int(q * (len(sorted_values) - 1)))]


def run_test(test, rows, names, column, cache):
    kind = test[0]
    values = cache(column)
    if kind == "lt":
        _, limit, share = test
        numeric = [v for v in values if v is not None]
        if not numeric:
            return {"test": "lt", "passed": False, "detail": "sin datos"}
        ok = sum(1 for v in numeric if v < limit) / len(numeric)
        return {"test": "lt %g g" % limit, "passed": ok >= share, "detail": "%.4f de %d" % (ok, len(numeric))}
    if kind == "le":
        _, ref, tol, share = test
        ref_values = cache(ref)
        pairs = [(v, r) for v, r in zip(values, ref_values) if v is not None and r is not None]
        if len(pairs) < MIN_NUMERIC_VALUES:
            return {"test": "le col %d" % ref, "passed": False, "detail": "solo %d pares" % len(pairs)}
        ok = sum(1 for v, r in pairs if v <= r + tol) / len(pairs)
        return {"test": "<= col %d (+%g g)" % (ref, tol), "passed": ok >= share, "detail": "%.4f de %d filas" % (ok, len(pairs))}
    if kind == "kwq":
        _, pattern, q, lo, hi, min_rows = test
        rx = re.compile(pattern)
        matched = sorted(v for v, n in zip(values, names) if v is not None and rx.search(n))
        if len(matched) < min_rows:
            return {"test": "kwq", "passed": False, "detail": "solo %d productos de referencia" % len(matched)}
        value = _quantile(matched, q)
        return {"test": "cuantil %.2f de %d productos de referencia en [%g, %g]" % (q, len(matched), lo, hi),
                "passed": lo <= value <= hi, "detail": "%.4g g/100 g" % value}
    if kind == "kwgt":
        _, pattern, other, min_rows = test
        rx = re.compile(pattern)
        other_values = cache(other)
        diffs = [v - o for v, o, n in zip(values, other_values, names)
                 if v is not None and o is not None and rx.search(n)]
        if len(diffs) < min_rows:
            return {"test": "kwgt", "passed": False, "detail": "solo %d productos de referencia" % len(diffs)}
        median = statistics.median(diffs)
        return {"test": "mediana(col - col %d) > 0 en %d productos de referencia" % (other, len(diffs)),
                "passed": median > 0, "detail": "mediana %.3g" % median}
    if kind == "top":
        _, pattern, top_n = test
        rx = re.compile(pattern)
        plausible = sorted(((v, n) for v, n in zip(values, names) if v is not None and v > 0), reverse=True)[:top_n]
        hits = [n[:30] for v, n in plausible if rx.search(n)]
        return {"test": "entre los %d mayores valores, alguno es de un producto esperado" % top_n,
                "passed": bool(hits), "detail": "; ".join(hits) or "ninguno"}
    if kind == "salt":
        salt = cache(COL_SALT)
        pairs = [(s, v) for s, v in zip(salt, values) if s is not None and v is not None and v > 0]
        if len(pairs) < MIN_NUMERIC_VALUES:
            return {"test": "sal ~ 2,5 x sodio", "passed": False, "detail": "solo %d pares" % len(pairs)}
        ok = sum(1 for s, v in pairs if abs(s - 2.5 * v) <= 0.05 * 2.5 * v + 1e-4) / len(pairs)
        return {"test": "sal (col %d) ~ 2,5 x sodio (+-5 %%)" % COL_SALT, "passed": ok >= 0.95,
                "detail": "%.4f de %d filas" % (ok, len(pairs))}
    raise ValueError("prueba desconocida: " + kind)


def verify_columns(rows, candidates=CANDIDATES):
    """Evidencia por columna. Devuelve (columnas aceptadas {indice: (id, factor)}, informe)."""
    names = [(r[COL_NAME].lower() if len(r) > COL_NAME else "") for r in rows]
    cache_store = {}

    def cache(index):
        if index not in cache_store:
            cache_store[index] = _numeric_column(rows, index)
        return cache_store[index]

    accepted, report = {}, []
    for index, key, tests in candidates:
        nutrient = N.BY_KEY[key]
        factor = factor_for(key)
        values = [v for v in cache(index) if v is not None]
        ceiling_g = nutrient.max_value / factor
        in_bounds = (sum(1 for v in values if 0 <= v <= ceiling_g) / len(values)) if values else 0.0
        results = [run_test(t, rows, names, index, cache) for t in tests]
        enough = len(values) >= MIN_NUMERIC_VALUES
        passed = enough and in_bounds >= MIN_IN_BOUNDS and all(r["passed"] for r in results)
        anchor = index in ANCHORS
        verdict = "anchor" if anchor else ("verified" if passed else "rejected")
        if anchor or passed:
            accepted.setdefault(index, (nutrient.id, factor))
        report.append({"column": index, "nutrient": key, "unit_factor_from_g": factor, "numeric_values": len(values),
                       "share_in_physical_bounds": round(in_bounds, 4), "tests": results, "verdict": verdict,
                       "reason_if_rejected": None if (anchor or passed) else (
                           "menos de %d valores" % MIN_NUMERIC_VALUES if not enough else
                           "fuera de rango fisico" if in_bounds < MIN_IN_BOUNDS else "prueba(s) fallida(s)")})
    # Una sola columna por nutriente en el orden de prioridad de CANDIDATES (168 antes que 169).
    taken, final = set(), {}
    for index, key, _ in candidates:
        if index in accepted and N.BY_KEY[key].id not in taken:
            final[index] = accepted[index]
            taken.add(N.BY_KEY[key].id)
    for entry in report:
        if entry["verdict"] == "verified" and entry["column"] not in final:
            entry["verdict"] = "verified_not_used"
    return final, report


def anchor_columns():
    return {i: (N.BY_KEY[k].id, factor_for(k)) for i, k, _ in CANDIDATES if i in ANCHORS}


def load(path, verify=True, columns=None):
    """Lee off_chile.csv. Devuelve (registros, informe). `verify=False` acepta todas las columnas
    candidatas sin pruebas (solo para pruebas sinteticas); `columns` fuerza un mapa de columnas."""
    with open(path, encoding="utf-8", errors="replace", newline="") as stream:
        text = stream.read()
    rows = [line.split("\t") for line in split_lines(text)]
    counts = {"lines": len(rows)}
    reasons = {}
    accepted_rows = []
    for parts in rows:
        ok, reason = app_accepts(parts)
        if ok:
            accepted_rows.append(parts)
        else:
            reasons[reason] = reasons.get(reason, 0) + 1
    counts["app_accepted"] = len(accepted_rows)
    counts["app_rejected_by_reason"] = reasons

    evidence = []
    if columns is not None:
        used = columns
    elif verify:
        wide = [r for r in rows if len(r) >= 190]
        used, evidence = verify_columns(wide)
    else:
        used, taken = {}, set()
        for index, key, _ in CANDIDATES:
            nid = N.BY_KEY[key].id
            if nid not in taken:
                used[index] = (nid, factor_for(key))
                taken.add(nid)

    records, seen, dropped_required, dup_codes, rejected = [], set(), 0, 0, {}
    for parts in accepted_rows:
        record, reason, rej = extract_record(parts, used)
        for nid in rej:
            rejected[nid] = rejected.get(nid, 0) + 1
        if record is None:
            dropped_required += 1
            continue
        if record["gtin"] in seen:
            dup_codes += 1
            continue
        seen.add(record["gtin"])
        records.append(record)
    counts.update(dropped_missing_required_macro=dropped_required, duplicate_codes=dup_codes,
                  records=len(records), values_rejected_out_of_range={N.BY_ID[k].key: v for k, v in sorted(rejected.items())})
    return records, {"counts": counts, "columns_used": {str(i): N.BY_ID[v[0]].key for i, v in sorted(used.items())},
                     "column_evidence": evidence}
