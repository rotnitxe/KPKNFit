#!/usr/bin/env python3
"""Constructor del paquete de catalogo de nutricion v0 (SQLite de solo lectura).

Contrato: docs/nutrition-v2/F1A_PAQUETE.md. Fuentes: USDA FNDDS / Foundation / SR Legacy, CIQUAL 2025 y
OFF Chile, con el vector completo de nutrientes (tabla NUTRIENTS en nutrients.py). Solo biblioteca estandar.
Dos construcciones seguidas con las mismas entradas dan el mismo sha256 (no hay fecha dentro del archivo).

    python -I scripts/nutrition_v2/catalog/build_pack.py [--inputs DIR] [--assets DIR] [--out ARCHIVO]
"""
import argparse
import csv
import hashlib
import io
import json
import math
import os
import re
import sqlite3
import sys
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1].parent
for _path in (str(HERE), str(HERE.parent / "sources")):
    if _path not in sys.path:
        sys.path.insert(0, _path)

import nutrients as N  # noqa: E402
import off_chile  # noqa: E402
import nutrition_catalog_usda as usda_adapter  # noqa: E402
from build_nutrition_catalog_pack import digest as file_sha256  # noqa: E402

FORMAT_VERSION = "1"
SOURCES = ("USDA_FNDDS", "USDA_SR_LEGACY", "USDA_FOUNDATION", "CIQUAL", "OFF_CL")
PRECEDENCE = ("USDA_FOUNDATION", "USDA_FNDDS", "USDA_SR_LEGACY")   # si un fdcId se repite, gana el primero
PREPARATIONS = {"RAW", "COOKED", "PREPARED", "AS_SOLD", "UNKNOWN"}
FORMS = {"NA", "POWDER", "LIQUID"}
REPORT_NUTRIENTS = ("sat_fat", "fiber", "sugars", "sodium", "calcium", "iron", "vitamin_a_rae", "vitamin_c",
                    "vitamin_d", "vitamin_b12")

SCHEMA = """
CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE record (
  id TEXT PRIMARY KEY, source TEXT NOT NULL, source_record_id TEXT NOT NULL, name TEXT NOT NULL,
  name_es TEXT, name_en TEXT, name_fr TEXT, brand TEXT, category TEXT,
  preparation TEXT NOT NULL, form TEXT NOT NULL, basis_amount REAL NOT NULL, basis_unit TEXT NOT NULL,
  density_g_per_ml REAL
) WITHOUT ROWID;
CREATE TABLE nutrient_value (
  record_id TEXT NOT NULL, nutrient_id INTEGER NOT NULL, amount REAL NOT NULL, quality INTEGER NOT NULL,
  PRIMARY KEY (record_id, nutrient_id)
) WITHOUT ROWID;
CREATE TABLE portion (
  record_id TEXT NOT NULL, seq INTEGER NOT NULL, label TEXT NOT NULL, label_es TEXT,
  grams REAL NOT NULL, ml REAL, PRIMARY KEY (record_id, seq)
) WITHOUT ROWID;
CREATE TABLE product (gtin TEXT PRIMARY KEY, record_id TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE alias (record_id TEXT NOT NULL, alias TEXT NOT NULL, alias_norm TEXT NOT NULL,
                    lang TEXT NOT NULL, kind TEXT NOT NULL);
CREATE VIRTUAL TABLE alias_fts USING fts4(alias_norm, record_id);
"""


class BuildError(Exception):
    pass


@dataclass
class Inputs:
    fndds: Path
    foundation: Path
    sr: Path
    ciqual_alim: Path
    ciqual_compo: Path
    ciqual_const: Path
    ciqual_grp: Path
    off_csv: Path
    aliases_csv: Path
    usda_lock: Path = None
    ciqual_lock: Path = None


def default_inputs(inputs_dir, assets_dir):
    inputs_dir, assets_dir = Path(inputs_dir), Path(assets_dir)
    locks = HERE.parent / "sources" / "locks"
    ciq = inputs_dir / "ciqual"
    return Inputs(
        fndds=inputs_dir / "FoodData_Central_survey_food_json_2024-10-31.zip",
        foundation=inputs_dir / "FoodData_Central_foundation_food_json_2026-04-30.zip",
        sr=inputs_dir / "FoodData_Central_sr_legacy_food_csv_2018-04.zip",
        ciqual_alim=ciq / "alim_2025_11_03.xml", ciqual_compo=ciq / "compo_2025_11_03.xml",
        ciqual_const=ciq / "const_2025_11_03.xml", ciqual_grp=ciq / "alim_grp_2025_11_03.xml",
        off_csv=assets_dir / "off_chile.csv", aliases_csv=assets_dir / "usda_es_aliases.csv",
        usda_lock=locks / "usda-sources.lock.json", ciqual_lock=locks / "ciqual-sources.lock.json")


class Stats:
    """Contadores por fuente para el informe."""

    def __init__(self):
        self.data = defaultdict(lambda: defaultdict(int))
        self.rejected = defaultdict(lambda: defaultdict(int))

    def add(self, source, key, n=1):
        self.data[source][key] += n

    def reject(self, source, nutrient_id):
        self.rejected[source][N.BY_ID[nutrient_id].key] += 1


# --------------------------------------------------------------------------------------------
# Alias en espanol
# --------------------------------------------------------------------------------------------
_FDC_SUFFIX = re.compile(r"\s*\(FDC\s+\d+\)\s*$")


def read_aliases(path):
    """fdc_id -> (es_name sin sufijo ' (FDC NNN)', [alias ...])."""
    result = {}
    if path is None:
        return result
    with open(path, encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            fdc = (row.get("fdc_id") or "").strip()
            if not fdc:
                continue
            name = _FDC_SUFFIX.sub("", (row.get("es_name") or "").strip()).strip()
            extra = [a.strip() for a in (row.get("aliases") or "").split("|") if a.strip()]
            result[fdc] = (name, extra)
    return result


# --------------------------------------------------------------------------------------------
# USDA
# --------------------------------------------------------------------------------------------
_checked_usda = set()


def check_usda_nutrient(nutrient_id, name, unit, where):
    key = (nutrient_id, name, unit)
    if key in _checked_usda:
        return
    expected_names, expected_unit = N.USDA_EXPECTED[nutrient_id]
    if str(name or "").strip().lower() not in expected_names or N.normalize_unit(unit) != expected_unit:
        raise BuildError("%s: el nutriente %d trae nombre/unidad %r/%r y se esperaba %s/%s"
                         % (where, nutrient_id, name, unit, sorted(expected_names), expected_unit))
    _checked_usda.add(key)


def to_float(value):
    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def usda_nutrients(entries, source, stats, where):
    """entries: (id USDA, nombre, unidad, cantidad). Aplica prioridades por id y comprueba nombre/unidad."""
    raw = {}
    for nutrient_id, name, unit, amount in entries:
        if nutrient_id not in N.USDA_TARGET_IDS:
            continue
        check_usda_nutrient(nutrient_id, name, unit, where)
        value = to_float(amount)
        if value is not None and nutrient_id not in raw:
            raw[nutrient_id] = value
    out = {}
    for nid, rules in N.USDA_RULES.items():
        for uid, quality in rules:
            if uid not in raw:
                continue
            amount = N.clean_value(raw[uid], nid)
            if amount is None:
                stats.reject(source, nid)
                continue
            out[nid] = (round(amount, 6), quality)
            break
    for nid, ids in N.USDA_SUMS.items():
        if all(i in raw for i in ids):
            amount = N.clean_value(round(sum(raw[i] for i in ids), 6), nid)
            if amount is None:
                stats.reject(source, nid)
            else:
                out[nid] = (amount, N.Q_DERIVED)
    return out


def make_portion(order, grams, description, amount, unit_name, modifier):
    grams = to_float(grams)
    if grams is None or not (0 < grams <= 100000):
        return None
    amount = to_float(amount)
    desc = " ".join(str(description or "").split())
    unit = str(unit_name or "").strip()
    unit = "" if unit.lower() == "undetermined" else unit
    mod = str(modifier or "").strip()
    mod = "" if mod.isdigit() else mod
    if desc and desc.lower() != "quantity not specified":
        label = desc
    else:
        label = " ".join(([("%g" % amount)] if amount else []) + ([unit] if unit else []))
        if mod:
            # sin unidad estructurada la unidad viene en el modificador ("1" + "cup, chopped")
            label = (label + (", " if unit else " ") + mod) if label else mod
        if not label:
            label = "%g g" % grams
    ml = N.portion_ml(desc or label, amount, unit)
    return (order, label, round(grams, 4), ml)


def finish_portions(items):
    """[(orden, label, grams, ml)] -> [(label, grams, ml)] ordenadas y numeradas por el llamador."""
    items = sorted(items, key=lambda x: x[0])
    return [(label, grams, ml) for _o, label, grams, ml in items]


def usda_record(source, fdc_id, description, category, entries, portions, aliases, stats):
    fdc_id = str(fdc_id)
    nutrients = usda_nutrients(entries, source, stats, source + " " + fdc_id)
    # preparacion y forma: canonical() del adaptador (+ verbos de cocinado extra)
    canon = usda_adapter.canonical(
        {"fdcId": fdc_id, "description": description, "foodNutrients": [], "foodPortions": [],
         "foodCategory": {"description": category} if category else {}}, source, "", {})
    preparation = N.extra_cooked(description, canon["denominator"]["preparation"])
    if source == "USDA_FNDDS":
        liquid_hint = category in N.FNDDS_LIQUID_CATEGORIES
    elif source == "USDA_SR_LEGACY":
        liquid_hint = category == "Beverages"
    else:
        liquid_hint = False
    form = N.physical_form(description, category, liquid_hint)
    es_name, extra = aliases.get(fdc_id, ("", []))
    alias_rows = [(description, "en", "NAME")]
    if es_name:
        alias_rows.append((es_name, "es", "NAME"))
    alias_rows += [(a, "es", "SOURCE_ALIAS") for a in extra]
    return {
        "id": "usda_" + fdc_id, "source": source, "source_record_id": fdc_id,
        "name": es_name or description, "name_es": es_name or None, "name_en": description, "name_fr": None,
        "brand": None, "category": category or None, "preparation": preparation, "form": form,
        "basis_unit": "g", "density": N.density_from_portions(portions), "nutrients": nutrients,
        "portions": portions, "aliases": alias_rows, "gtin": None}


def read_usda_json(path, source, aliases, stats):
    with zipfile.ZipFile(path) as archive:
        names = [n for n in archive.namelist() if n.endswith(".json")]
        if len(names) != 1:
            raise BuildError("%s: se esperaba exactamente un JSON en %s" % (source, path))
        with archive.open(names[0]) as stream:
            document = json.load(stream)
    foods = next(iter(document.values()))
    for food in foods:
        stats.add(source, "read")
        if not isinstance(food, dict) or not food.get("description") or food.get("fdcId") is None:
            stats.add(source, "invalid_source_record")
            continue
        entries = []
        for item in food.get("foodNutrients") or []:
            nut = item.get("nutrient") if isinstance(item, dict) else None
            if isinstance(nut, dict) and nut.get("id") is not None:
                entries.append((int(nut["id"]), nut.get("name"), nut.get("unitName"), item.get("amount")))
        portions = []
        for index, row in enumerate(food.get("foodPortions") or []):
            unit = (row.get("measureUnit") or {}).get("name")
            amount = row.get("amount", row.get("value"))
            order = row.get("sequenceNumber")
            item = make_portion((order if isinstance(order, int) else 10 ** 6, index), row.get("gramWeight"),
                                row.get("portionDescription"), amount, unit, row.get("modifier"))
            if item:
                portions.append(item)
        cat = food.get("foodCategory") or {}
        wweia = food.get("wweiaFoodCategory") or {}
        category = cat.get("description") or wweia.get("wweiaFoodCategoryDescription")
        yield usda_record(source, food["fdcId"], str(food["description"]).strip(), category, entries,
                          finish_portions(portions), aliases, stats)


def read_usda_sr(path, aliases, stats):
    source = "USDA_SR_LEGACY"
    with zipfile.ZipFile(path) as archive:
        members = {Path(n).name: n for n in archive.namelist() if not n.endswith("/")}

        def rows(filename):
            if filename not in members:
                raise BuildError("SR Legacy: falta %s en %s" % (filename, path))
            with archive.open(members[filename]) as stream, io.TextIOWrapper(stream, encoding="utf-8-sig", newline="") as text:
                yield from csv.DictReader(text)

        nutrient_meta = {int(r["id"]): (r["name"], r["unit_name"]) for r in rows("nutrient.csv")}
        for uid in N.USDA_TARGET_IDS:
            if uid in nutrient_meta:
                check_usda_nutrient(uid, nutrient_meta[uid][0], nutrient_meta[uid][1], "SR nutrient.csv")
        units = {r["id"]: r["name"] for r in rows("measure_unit.csv")}
        categories = {r["id"]: r["description"] for r in rows("food_category.csv")} if "food_category.csv" in members else {}
        by_food = defaultdict(list)
        for r in rows("food_nutrient.csv"):
            nid = int(r["nutrient_id"])
            if nid in N.USDA_TARGET_IDS:
                name, unit = nutrient_meta[nid]
                by_food[r["fdc_id"]].append((nid, name, unit, r["amount"]))
        portions_by_food = defaultdict(list)
        for index, r in enumerate(rows("food_portion.csv")):
            seq = r.get("seq_num", "")
            order = int(seq) if seq.strip().isdigit() else 10 ** 6
            item = make_portion((order, index), r["gram_weight"], r.get("portion_description"), r.get("amount"),
                                units.get(r.get("measure_unit_id")), r.get("modifier"))
            if item:
                portions_by_food[r["fdc_id"]].append(item)
        for r in rows("food.csv"):
            if r["data_type"] != "sr_legacy_food":
                continue
            stats.add(source, "read")
            yield usda_record(source, r["fdc_id"], r["description"].strip(), categories.get(r.get("food_category_id")),
                              by_food.get(r["fdc_id"], []), finish_portions(portions_by_food.get(r["fdc_id"], [])),
                              aliases, stats)


# --------------------------------------------------------------------------------------------
# CIQUAL
# --------------------------------------------------------------------------------------------
def xml_rows(path, tag):
    for _event, element in ET.iterparse(str(path), events=("end",)):
        if element.tag == tag:
            yield {child.tag: (child.text or "").strip() for child in element}
            element.clear()


_CIQUAL_NUMBER = re.compile(r"^\d+(?:[.,]\d+)?$")
_CIQUAL_LIMIT = re.compile(r"^<\s*(\d+(?:[.,]\d+)?)$")


def parse_ciqual_value(text):
    """(cantidad, calidad) o None. 'traces' -> (0, 3); '< x' -> (x, 3); numero con coma -> (n, 0)."""
    text = (text or "").strip()
    if not text or text == "-":
        return None
    if text.lower() in ("traces", "trace", "traza", "trazas"):
        return 0.0, N.Q_TRACE
    match = _CIQUAL_LIMIT.match(text)
    if match:
        return float(match.group(1).replace(",", ".")), N.Q_TRACE
    if _CIQUAL_NUMBER.match(text):
        return float(text.replace(",", ".")), N.Q_DECLARED
    return None


def check_ciqual_constants(const_path):
    found = {}
    for row in xml_rows(const_path, "CONST"):
        try:
            code = int(row["const_code"])
        except (KeyError, ValueError):
            continue
        if code in N.CIQUAL_UNITS:
            found[code] = row
    for code, unit in N.CIQUAL_UNITS.items():
        row = found.get(code)
        if row is None:
            raise BuildError("CIQUAL: el const_code %d no existe en %s" % (code, const_path))
        units = re.findall(r"\(([^()/]*)/\s*100\s*g\)", row.get("const_nom_eng", ""))
        if not units or N.normalize_unit(units[-1]) != unit:
            raise BuildError("CIQUAL: const_code %d trae unidad %r en %r y se esperaba %s"
                             % (code, units[-1:] , row.get("const_nom_eng"), unit))
        if row.get("code_INFOODS", "") != N.CIQUAL_TAGS[code]:
            raise BuildError("CIQUAL: const_code %d trae INFOODS %r y se esperaba %s"
                             % (code, row.get("code_INFOODS"), N.CIQUAL_TAGS[code]))


def ciqual_vector(values, source, stats):
    """values: const_code -> (cantidad, calidad). Devuelve {id nutriente: (cantidad, calidad)}."""
    out = {}
    for nid, rules in N.CIQUAL_RULES.items():
        for code, quality in rules:
            if code not in values:
                continue
            amount = N.clean_value(values[code][0], nid)
            if amount is None:
                stats.reject(source, nid)
                continue
            out[nid] = (round(amount, 6), max(quality, values[code][1]) if values[code][1] == N.Q_TRACE else quality)
            break
    for nid, (required, optional) in N.CIQUAL_SUMS.items():
        if not all(code in values for code, _d in required):
            continue
        used = list(required) + [(code, d) for code, d in optional if code in values]
        total = round(sum(values[code][0] / divisor for code, divisor in used), 6)
        amount = N.clean_value(total, nid)
        if amount is None:
            stats.reject(source, nid)
            continue
        if len(used) == 1:
            quality = values[used[0][0]][1]
        else:
            quality = N.Q_TRACE if any(values[code][1] == N.Q_TRACE for code, _d in used) else N.Q_DERIVED
        out[nid] = (amount, quality)
    return out


def read_ciqual(inputs, stats):
    source = "CIQUAL"
    check_ciqual_constants(inputs.ciqual_const)
    values = defaultdict(dict)
    for row in xml_rows(inputs.ciqual_compo, "COMPO"):
        try:
            code = int(row["const_code"])
        except (KeyError, ValueError):
            continue
        if code not in N.CIQUAL_TARGET_CODES:
            continue
        parsed = parse_ciqual_value(row.get("teneur"))
        if parsed is not None and code not in values[row["alim_code"]]:
            values[row["alim_code"]][code] = parsed
    groups = {}
    if inputs.ciqual_grp is not None:
        for row in xml_rows(inputs.ciqual_grp, "ALIM_GRP"):
            for code_key, name_key in (("alim_grp_code", "alim_grp_nom_eng"), ("alim_ssgrp_code", "alim_ssgrp_nom_eng"),
                                       ("alim_ssssgrp_code", "alim_ssssgrp_nom_eng")):
                if row.get(code_key) and row.get(name_key) not in (None, "", "-"):
                    groups[row[code_key]] = row[name_key]
    for row in xml_rows(inputs.ciqual_alim, "ALIM"):
        stats.add(source, "read")
        code = row["alim_code"]
        french, english = row.get("alim_nom_fr", ""), row.get("alim_nom_eng", "")
        if not code or not (french or english):
            stats.add(source, "invalid_source_record")
            continue
        toks = set(N.words(english))
        preparation = ("PREPARED" if toks & {"prepared", "reconstituted"}
                       else "COOKED" if toks & {"cooked", "boiled", "fried", "baked", "roasted"}
                       else "RAW" if "raw" in toks else "AS_SOLD")
        preparation = N.extra_cooked(english, preparation)
        grp = groups.get(row.get("alim_grp_code"), "")
        ssgrp = groups.get(row.get("alim_ssgrp_code"), "")
        sssgrp = groups.get(row.get("alim_ssssgrp_code"), "")
        category = sssgrp or ssgrp or grp or None
        liquid_hint = "beverage" in grp.lower() or ssgrp.lower() == "milk"
        form = N.physical_form((english + " " + french).strip(), category, liquid_hint)
        alias_rows = []
        if french:
            alias_rows.append((french, "fr", "NAME"))
        if english:
            alias_rows.append((english, "en", "NAME"))
        yield {
            "id": "ciqual_" + code, "source": source, "source_record_id": code,
            "name": french or english, "name_es": None, "name_en": english or None, "name_fr": french or None,
            "brand": None, "category": category, "preparation": preparation, "form": form, "basis_unit": "g",
            "density": None, "nutrients": ciqual_vector(values.get(code, {}), source, stats), "portions": [],
            "aliases": alias_rows, "gtin": None}


# --------------------------------------------------------------------------------------------
# OFF Chile
# --------------------------------------------------------------------------------------------
def read_off(inputs, stats, verify=True, columns=None):
    records, report = off_chile.load(inputs.off_csv, verify=verify, columns=columns)
    counts = report["counts"]
    stats.add("OFF_CL", "read", counts["lines"])
    for key in ("app_accepted", "dropped_missing_required_macro", "duplicate_codes"):
        stats.add("OFF_CL", key, counts[key])
    for key, n in counts["values_rejected_out_of_range"].items():
        stats.rejected["OFF_CL"][key] += n
    for record in records:
        alias_rows = [(record["name"], "es", "NAME")]
        if record["brand"]:
            alias_rows.append((record["brand"], "es", "SOURCE_ALIAS"))
            alias_rows.append((record["name"] + " " + record["brand"], "es", "SOURCE_ALIAS"))
        record["aliases"] = alias_rows
    return records, report


# --------------------------------------------------------------------------------------------
# Ensamblado, validacion, SQLite
# --------------------------------------------------------------------------------------------
def is_calculable(record):
    return all(i in record["nutrients"] for i in N.REQUIRED_IDS)


def validate(records):
    """Errores duros (BuildError) y avisos (contados por fuente)."""
    warnings = defaultdict(lambda: defaultdict(int))
    examples = defaultdict(list)
    seen = set()
    for r in records:
        if r["id"] in seen:
            raise BuildError("id duplicado: " + r["id"])
        seen.add(r["id"])
        if r["source"] not in SOURCES or r["preparation"] not in PREPARATIONS or r["form"] not in FORMS \
                or r["basis_unit"] not in ("g", "ml") or not r["name"]:
            raise BuildError("ficha invalida: " + r["id"])
        for nid, (amount, quality) in r["nutrients"].items():
            nut = N.BY_ID.get(nid)
            if nut is None or not math.isfinite(amount) or not (0 <= amount <= nut.max_value) or quality not in (0, 1, 2, 3):
                raise BuildError("valor fuera de rango fisico en %s: nutriente %s = %r" % (r["id"], nid, amount))
        v = {nid: a for nid, (a, _q) in r["nutrients"].items()}
        src = r["source"]

        def warn(kind, detail):
            warnings[kind][src] += 1
            if len(examples[kind]) < 12:
                examples[kind].append("%s %s: %s" % (src, r["id"], detail))

        kcal, p, c, f = (v[N.key_id(k)] for k in ("energy", "protein", "carbs", "fat"))
        alc = v.get(N.key_id("alcohol"), 0.0)
        atwater = 4 * p + 4 * c + 9 * f + 7 * alc
        if abs(kcal - atwater) > 0.2 * max(kcal, 1.0) and abs(kcal - atwater) > 30:
            warn("atwater_deviation_over_20pct_and_30kcal", "kcal %.0f vs 4P+4C+9G+7A %.0f" % (kcal, atwater))
        if p + c + f > 100.5:
            warn("protein_carbs_fat_over_100g", "P+C+G = %.1f" % (p + c + f))
        sat = v.get(N.key_id("sat_fat"))
        if sat is not None and sat > f + 0.5:
            warn("sat_fat_over_fat", "sat %.1f > fat %.1f" % (sat, f))
        sug = v.get(N.key_id("sugars"))
        if sug is not None and sug > c + 0.5:
            warn("sugars_over_carbs", "sugars %.1f > carbs %.1f" % (sug, c))
    return ({k: dict(v) for k, v in warnings.items()}, dict(examples))


def coverage(records):
    by_source = defaultdict(list)
    for r in records:
        by_source[r["source"]].append(r)
    out = {}
    for src, recs in sorted(by_source.items()):
        out[src] = {n.key: round(100.0 * sum(1 for r in recs if n.id in r["nutrients"]) / len(recs), 2)
                    for n in N.NUTRIENTS}
    return out


def tables(records):
    """Filas de cada tabla, en orden determinista."""
    records = sorted(records, key=lambda r: r["id"])
    record_rows, value_rows, portion_rows, product_rows, alias_rows = [], [], [], [], []
    for r in records:
        record_rows.append((r["id"], r["source"], r["source_record_id"], r["name"], r["name_es"], r["name_en"],
                            r["name_fr"], r["brand"], r["category"], r["preparation"], r["form"], 100.0,
                            r["basis_unit"], r["density"]))
        for nid in sorted(r["nutrients"]):
            amount, quality = r["nutrients"][nid]
            value_rows.append((r["id"], nid, amount, quality))
        for seq, (label, grams, ml) in enumerate(r["portions"], start=1):
            portion_rows.append((r["id"], seq, label, None, grams, ml))
        if r["gtin"]:
            product_rows.append((r["gtin"], r["id"]))
        seen = set()
        for alias, lang, kind in r["aliases"]:
            norm = N.alias_norm(alias)
            if norm and (norm, lang) not in seen:
                seen.add((norm, lang))
                alias_rows.append((r["id"], " ".join(str(alias).split()), norm, lang, kind))
    product_rows.sort()
    alias_rows.sort(key=lambda a: (a[0], a[2], a[3], a[4]))
    return {"record": record_rows, "nutrient_value": value_rows, "portion": portion_rows,
            "product": product_rows, "alias": alias_rows}


def generation_of(table_rows):
    digest = hashlib.sha256()
    digest.update(("formatVersion=" + FORMAT_VERSION + "\n").encode())
    for table in ("record", "nutrient_value", "portion", "product", "alias"):
        for row in table_rows[table]:
            digest.update(json.dumps([table] + list(row), ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            digest.update(b"\n")
    return digest.hexdigest()[:16]


def write_sqlite(path, table_rows, metadata):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    for stale in (tmp, Path(str(tmp) + "-journal")):
        if stale.exists():
            stale.unlink()
    con = sqlite3.connect(str(tmp))
    try:
        con.execute("PRAGMA page_size=4096")
        con.execute("PRAGMA journal_mode=OFF")
        con.execute("PRAGMA synchronous=OFF")
        con.executescript(SCHEMA)
        con.executemany("INSERT INTO metadata VALUES (?,?)", sorted(metadata.items()))
        con.executemany("INSERT INTO record VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", table_rows["record"])
        con.executemany("INSERT INTO nutrient_value VALUES (?,?,?,?)", table_rows["nutrient_value"])
        con.executemany("INSERT INTO portion VALUES (?,?,?,?,?,?)", table_rows["portion"])
        con.executemany("INSERT INTO product VALUES (?,?)", table_rows["product"])
        con.executemany("INSERT INTO alias VALUES (?,?,?,?,?)", table_rows["alias"])
        con.execute("INSERT INTO alias_fts(docid, alias_norm, record_id) SELECT rowid, alias_norm, record_id FROM alias ORDER BY rowid")
        con.execute("INSERT INTO alias_fts(alias_fts) VALUES('optimize')")
        con.commit()
        check = con.execute("PRAGMA integrity_check").fetchone()[0]
        if check != "ok":
            raise BuildError("integrity_check: " + check)
    finally:
        con.close()
    os.replace(str(tmp), str(path))


def input_hashes(inputs, verify_locks):
    files = {
        "USDA_FNDDS": inputs.fndds, "USDA_FOUNDATION": inputs.foundation, "USDA_SR_LEGACY": inputs.sr,
        "CIQUAL_alim": inputs.ciqual_alim, "CIQUAL_compo": inputs.ciqual_compo, "CIQUAL_const": inputs.ciqual_const,
        "CIQUAL_alim_grp": inputs.ciqual_grp, "OFF_CL": inputs.off_csv, "USDA_ES_ALIASES": inputs.aliases_csv}
    out = {key: {"file": Path(p).name, "sha256": file_sha256(p)} for key, p in files.items() if p is not None}
    if verify_locks:
        expected = {}
        if inputs.usda_lock:
            for entry in json.loads(Path(inputs.usda_lock).read_text(encoding="utf-8")).values():
                expected[entry["file"]] = entry["sha256"]
        if inputs.ciqual_lock:
            for name, entry in json.loads(Path(inputs.ciqual_lock).read_text(encoding="utf-8"))["files"].items():
                expected[name] = entry["sha256"]
        for key, info in out.items():
            if info["file"] in expected and expected[info["file"]] != info["sha256"]:
                raise BuildError("%s: el sha256 de %s no coincide con la cerradura" % (key, info["file"]))
        checked = [i["file"] for i in out.values() if i["file"] in expected]
        if len(checked) < 7:
            raise BuildError("cerraduras: solo se pudieron comprobar %d archivos" % len(checked))
    return out


def build(inputs, out_path, verify_locks=True, off_verify=True, off_columns=None):
    stats = Stats()
    hashes = input_hashes(inputs, verify_locks)
    aliases = read_aliases(inputs.aliases_csv)
    by_source = {
        "USDA_FOUNDATION": list(read_usda_json(inputs.foundation, "USDA_FOUNDATION", aliases, stats)),
        "USDA_FNDDS": list(read_usda_json(inputs.fndds, "USDA_FNDDS", aliases, stats)),
        "USDA_SR_LEGACY": list(read_usda_sr(inputs.sr, aliases, stats)),
    }
    ciqual = list(read_ciqual(inputs, stats))
    off, off_report = read_off(inputs, stats, verify=off_verify, columns=off_columns)

    chosen, collisions = {}, 0
    for source in PRECEDENCE:
        for record in by_source[source]:
            if record["id"] in chosen:
                collisions += 1
                continue
            chosen[record["id"]] = record
    for record in ciqual + off:
        if record["id"] in chosen:
            raise BuildError("id repetido entre fuentes: " + record["id"])
        chosen[record["id"]] = record
    stats.add("USDA", "fdcid_collisions_between_sources", collisions)

    records = []
    for record in chosen.values():
        if is_calculable(record):
            records.append(record)
        else:
            stats.add(record["source"], "not_calculable")
    for record in records:
        stats.add(record["source"], "kept")

    warnings, warning_examples = validate(records)
    table_rows = tables(records)
    generation = generation_of(table_rows)
    metadata = {"formatVersion": FORMAT_VERSION, "generation": generation,
                "sources": json.dumps(hashes, sort_keys=True, separators=(",", ":"))}
    out_path = Path(out_path)
    write_sqlite(out_path, table_rows, metadata)
    file_hash = file_sha256(out_path)
    out_path.with_name(out_path.name + ".sha256").write_text("%s  %s\n" % (file_hash, out_path.name), encoding="utf-8")

    report = {
        "file": str(out_path), "bytes": out_path.stat().st_size, "megabytes": round(out_path.stat().st_size / 1048576, 2),
        "sha256": file_hash, "generation": generation, "formatVersion": FORMAT_VERSION,
        "counts_by_source": {s: dict(stats.data[s]) for s in SOURCES + ("USDA",) if s in stats.data},
        "rows": {t: len(r) for t, r in table_rows.items()},
        "values_rejected_out_of_range_by_source": {s: dict(v) for s, v in stats.rejected.items()},
        "coverage_pct_by_source": coverage(records),
        "validation_warnings_by_check_and_source": warnings, "validation_warning_examples": warning_examples,
        "off": {"counts": off_report["counts"], "columns_used": off_report["columns_used"],
                "column_evidence": off_report["column_evidence"]},
        "inputs": hashes,
    }
    out_path.with_name("build-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=False), encoding="utf-8")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description="Construye nutrition_catalog_v0.sqlite")
    parser.add_argument("--inputs", default=str(Path("C:/Users/valen/Documents/KPKNFit/artifacts/nutrition-local-ai/inputs")),
                        help="carpeta con los zip USDA y la subcarpeta ciqual/")
    parser.add_argument("--assets", default=str(REPO / "android-native" / "app" / "src" / "main" / "assets" / "food_data"),
                        help="carpeta con off_chile.csv y usda_es_aliases.csv")
    parser.add_argument("--out", default="C:/kw/nutri-build/nutrition_catalog_v0.sqlite")
    args = parser.parse_args(argv)
    report = build(default_inputs(args.inputs, args.assets), args.out)
    summary = {k: report[k] for k in ("file", "megabytes", "sha256", "generation", "counts_by_source")}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
