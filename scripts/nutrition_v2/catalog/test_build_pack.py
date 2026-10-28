"""Pruebas del constructor del paquete de catalogo v0: fuentes sinteticas minimas + integracion real."""
import csv
import hashlib
import io
import json
import sqlite3
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import build_pack as B  # noqa: E402
import nutrients as N  # noqa: E402
import off_chile as O  # noqa: E402

REPO = HERE.parents[2]
REAL_INPUTS = Path("C:/Users/valen/Documents/KPKNFit/artifacts/nutrition-local-ai/inputs")
REAL_ASSETS = REPO / "android-native" / "app" / "src" / "main" / "assets" / "food_data"
REAL_OK = all(p.exists() for p in (
    REAL_INPUTS / "FoodData_Central_survey_food_json_2024-10-31.zip",
    REAL_INPUTS / "FoodData_Central_foundation_food_json_2026-04-30.zip",
    REAL_INPUTS / "FoodData_Central_sr_legacy_food_csv_2018-04.zip",
    REAL_INPUTS / "ciqual" / "compo_2025_11_03.xml",
    REAL_ASSETS / "off_chile.csv", REAL_ASSETS / "usda_es_aliases.csv"))

K = N.key_id


def nut(nid, name, unit, amount):
    return {"type": "FoodNutrient", "nutrient": {"id": nid, "name": name, "unitName": unit}, "amount": amount}


def milk_nutrients():
    return [nut(1008, "Energy", "kcal", 61), nut(1003, "Protein", "g", 3.15), nut(1004, "Total lipid (fat)", "g", 3.25),
            nut(1005, "Carbohydrate, by difference", "g", 4.8), nut(1087, "Calcium, Ca", "mg", 113),
            nut(1114, "Vitamin D (D2 + D3)", "\u00b5g", 1.3), nut(1278, "PUFA 20:5 n-3 (EPA)", "g", 0),
            nut(1272, "PUFA 22:6 n-3 (DHA)", "g", 0), nut(1018, "Alcohol, ethyl", "g", 0)]


def survey_document():
    return {"SurveyFoods": [
        {"fdcId": 1001, "description": "Milk, whole, fluid", "wweiaFoodCategory": {"wweiaFoodCategoryDescription": "Milk, whole"},
         "foodNutrients": milk_nutrients(),
         "foodPortions": [{"id": 1, "gramWeight": 244, "portionDescription": "1 cup", "measureUnit": {"name": "undetermined"},
                           "modifier": "10205", "sequenceNumber": 1},
                          {"id": 2, "gramWeight": 0, "portionDescription": "Quantity not specified", "sequenceNumber": 2,
                           "measureUnit": {"name": "undetermined"}}]},
        {"fdcId": 1002, "description": "Chicken breast, broiled",
         "wweiaFoodCategory": {"wweiaFoodCategoryDescription": "Chicken, whole pieces"},
         "foodNutrients": [nut(2047, "Energy (Atwater General Factors)", "kcal", 165), nut(1003, "Protein", "g", 31),
                           nut(1085, "Total fat (NLEA)", "g", 3.6), nut(1050, "Carbohydrate, by summation", "g", 0),
                           nut(1177, "Folate, total", "\u00b5g", 4), nut(1278, "PUFA 20:5 n-3 (EPA)", "g", 0.01)],
         "foodPortions": []},
        {"fdcId": 1003, "description": "Powder, no macros", "foodNutrients": [nut(1003, "Protein", "g", 10)], "foodPortions": []},
        {"fdcId": 1005, "description": "Orange juice", "wweiaFoodCategory": {"wweiaFoodCategoryDescription": "Other fruit juice"},
         "foodNutrients": [nut(1008, "Energy", "kcal", 45), nut(1003, "Protein", "g", 0.7), nut(1004, "Total lipid (fat)", "g", 0.2),
                           nut(1005, "Carbohydrate, by difference", "g", 10.4)],
         "foodPortions": [{"id": 4, "gramWeight": 248, "portionDescription": "1 cup", "measureUnit": {"name": "undetermined"},
                           "sequenceNumber": 1},
                          {"id": 5, "gramWeight": 0, "portionDescription": "Quantity not specified", "sequenceNumber": 2,
                           "measureUnit": {"name": "undetermined"}}]},
        {"fdcId": 1004, "description": "Sugar cube", "foodNutrients": [nut(1008, "Energy", "kcal", 387), nut(1003, "Protein", "g", 0),
                                                                       nut(1004, "Total lipid (fat)", "g", 0),
                                                                       nut(1005, "Carbohydrate, by difference", "g", 100)],
         "foodPortions": [{"id": 3, "gramWeight": 20, "portionDescription": "1 cup", "measureUnit": {"name": "undetermined"},
                           "sequenceNumber": 1}]},
    ]}


def foundation_document():
    return {"FoundationFoods": [
        None,
        {"fdcId": 2001, "description": "Milk, dry, whole", "foodCategory": {"description": "Dairy and Egg Products"},
         "foodNutrients": [nut(1008, "Energy", "kcal", 496), nut(1003, "Protein", "g", 26), nut(1004, "Total lipid (fat)", "g", 27),
                           nut(1005, "Carbohydrate, by difference", "g", 38)],
         "foodPortions": [{"id": 9, "value": 2.0, "amount": 2.0, "gramWeight": 33.9, "measureUnit": {"name": "tablespoon"},
                           "sequenceNumber": 1}]},
        {"fdcId": 1001, "description": "Milk, whole, fluid", "foodNutrients": milk_nutrients(), "foodPortions": []},
    ]}


def write_zip(path, name, document):
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(name, json.dumps(document, ensure_ascii=False).encode("utf-8"))


def csv_text(header, rows):
    out = io.StringIO()
    writer = csv.writer(out, lineterminator="\n")
    writer.writerow(header)
    writer.writerows(rows)
    return out.getvalue()


def write_sr_zip(path):
    prefix = "sr_legacy/"
    files = {
        "nutrient.csv": csv_text(["id", "name", "unit_name", "nutrient_nbr", "rank"], [
            [1008, "Energy", "KCAL", 208, 1], [1003, "Protein", "G", 203, 2], [1004, "Total lipid (fat)", "G", 204, 3],
            [1005, "Carbohydrate, by difference", "G", 205, 4], [1087, "Calcium, Ca", "MG", 301, 5],
            [1103, "Selenium, Se", "UG", 317, 6]]),
        "food_category.csv": csv_text(["id", "code", "description"], [[1, "0100", "Dairy and Egg Products"]]),
        "measure_unit.csv": csv_text(["id", "name"], [[9999, "undetermined"], [1000, "cup"]]),
        "food.csv": csv_text(["fdc_id", "data_type", "description", "food_category_id", "publication_date"], [
            [3001, "sr_legacy_food", "Cheese, cheddar", 1, "2019-04-01"],
            [3002, "branded_food", "Ignored branded row", 1, "2019-04-01"]]),
        "food_nutrient.csv": csv_text(["id", "fdc_id", "nutrient_id", "amount"], [
            [1, 3001, 1008, 403], [2, 3001, 1003, 24.9], [3, 3001, 1004, 33.1], [4, 3001, 1005, 1.28],
            [5, 3001, 1087, 710], [6, 3001, 1103, 28.5]]),
        "food_portion.csv": csv_text(["id", "fdc_id", "seq_num", "amount", "measure_unit_id", "portion_description",
                                      "modifier", "gram_weight"], [
            [1, 3001, 1, 1, 9999, "", "cup, diced", 132], [2, 3001, 2, 1, 1000, "", "", 113]]),
    }
    with zipfile.ZipFile(path, "w") as archive:
        for name, text in files.items():
            archive.writestr(prefix + name, text.encode("utf-8"))


def ciqual_xml(tag, rows):
    parts = ['<?xml version="1.0" encoding="utf-8" ?>\n<TABLE>\n']
    for row in rows:
        parts.append("  <%s>\n" % tag)
        for key, value in row.items():
            parts.append("    <%s> %s </%s>\n" % (key, escape(str(value)), key))
        parts.append("  </%s>\n" % tag)
    parts.append("</TABLE>\n")
    return "".join(parts)


def write_ciqual(directory):
    directory.mkdir(parents=True, exist_ok=True)
    const_rows = [{"const_code": code, "const_nom_fr": "x", "const_nom_eng": "Name (%s/100g)" % unit,
                   "code_INFOODS": N.CIQUAL_TAGS[code]} for code, unit in N.CIQUAL_UNITS.items()]
    compo = []

    def add(alim, code, text):
        compo.append({"alim_code": alim, "const_code": code, "teneur": text})

    for code, text in ((328, "274"), (25000, "13,5"), (40000, "20,1"), (31000, "0,5"), (10200, "traces"),
                       (52100, "< 0,5"), (51200, "100,0"), (51330, "1200"), (10110, "1,5"), (60000, "-")):
        add("5001", code, text)
    for code, text in ((328, "100"), (25000, "5"), (31000, "10")):   # sin grasa -> no calculable
        add("5002", code, text)
    (directory / "const.xml").write_text(ciqual_xml("CONST", const_rows), encoding="utf-8")
    (directory / "compo.xml").write_text(ciqual_xml("COMPO", compo), encoding="utf-8")
    (directory / "alim.xml").write_text(ciqual_xml("ALIM", [
        {"alim_code": "5001", "alim_nom_fr": "Lait entier en poudre", "alim_nom_eng": "Milk, whole, powder",
         "alim_grp_code": "05", "alim_ssgrp_code": "0501", "alim_ssssgrp_code": "000000"},
        {"alim_code": "5002", "alim_nom_fr": "Pain", "alim_nom_eng": "Bread", "alim_grp_code": "07",
         "alim_ssgrp_code": "0701", "alim_ssssgrp_code": "000000"}]), encoding="utf-8")
    (directory / "grp.xml").write_text(ciqual_xml("ALIM_GRP", [
        {"alim_grp_code": "05", "alim_grp_nom_eng": "milk and milk products", "alim_ssgrp_code": "0501",
         "alim_ssgrp_nom_eng": "milk", "alim_ssssgrp_code": "000000", "alim_ssssgrp_nom_eng": "-"}]), encoding="utf-8")


def off_row(**cols):
    row = [""] * 210
    for index, value in cols.items():
        row[int(index[1:])] = str(value)
    return "\t".join(row)


def write_off(path):
    base = dict(c10="Leche entera", c18="Soprole,Otra marca", c89=61, c92=3.3, c129=4.7, c130=4.7, c150=3.2, c156=0.05,
                c13="1 l", c61="Milk and dairy", c60="Milk and dairy")
    rows = [
        off_row(c0="7801", **base, c177=0.12, c160=1e-6, c128=0.014, c154=0.125, c188=0.0002),
        off_row(c0="7802", c10="Sin energia", c89="", c92=1, c129=1, c150=1, c156=0.1),             # app: sin energia
        off_row(c0="7803", c10="Incoherente", c89=900, c92=1, c129=1, c150=1, c156=0.1),            # app: energia incoherente
        off_row(c0="7804", c10="Sin proteina", c89=360, c92=0, c129=90, c150="", c156=0.01),        # app acepta, falta proteina
        off_row(c0="7805", c10="Galleta", c18="Marca G", c89=480, c92=20, c129=65, c150=6, c156=0.4, c13="200 g",
                c50="30 g", c51=30, c177=0.0002, c128=7),                                           # colesterol fuera de rango
        off_row(c0="7806", c10="", c89=100, c92=1, c129=20, c150=1, c156=0.1),                      # sin nombre
    ]
    Path(path).write_text("\n".join(rows) + "\n", encoding="utf-8")


def make_inputs(root, bad_name=False):
    root = Path(root)
    survey = survey_document()
    if bad_name:
        survey["SurveyFoods"][0]["foodNutrients"][4]["nutrient"]["name"] = "Magnesium, Mg"   # id 1087 con nombre ajeno
    write_zip(root / "fndds.zip", "surveyDownload.json", survey)
    write_zip(root / "foundation.zip", "foundation.json", foundation_document())
    write_sr_zip(root / "sr.zip")
    write_ciqual(root / "ciqual")
    write_off(root / "off.csv")
    (root / "aliases.csv").write_text(
        "fdc_id,en_description,es_name,aliases\n1001,\"Milk, whole, fluid\",Leche entera (FDC 1001),leche de vaca|l\u00e1cteo\n"
        "2001,\"Milk, dry, whole\",Leche en polvo entera,\n", encoding="utf-8")
    return B.Inputs(fndds=root / "fndds.zip", foundation=root / "foundation.zip", sr=root / "sr.zip",
                    ciqual_alim=root / "ciqual" / "alim.xml", ciqual_compo=root / "ciqual" / "compo.xml",
                    ciqual_const=root / "ciqual" / "const.xml", ciqual_grp=root / "ciqual" / "grp.xml",
                    off_csv=root / "off.csv", aliases_csv=root / "aliases.csv")


class SyntheticBuild(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.tmp.name)
        cls.inputs = make_inputs(cls.root)
        cls.out = cls.root / "out" / "pack.sqlite"
        cls.report = B.build(cls.inputs, cls.out, verify_locks=False, off_verify=False)
        cls.con = sqlite3.connect("file:%s?mode=ro" % cls.out.as_posix(), uri=True)

    @classmethod
    def tearDownClass(cls):
        cls.con.close()
        cls.tmp.cleanup()

    def values(self, record_id):
        return {nid: (amount, quality) for nid, amount, quality in self.con.execute(
            "SELECT nutrient_id, amount, quality FROM nutrient_value WHERE record_id=?", (record_id,))}

    def record(self, record_id):
        cur = self.con.execute("SELECT * FROM record WHERE id=?", (record_id,))
        names = [d[0] for d in cur.description]
        row = cur.fetchone()
        return dict(zip(names, row)) if row else None

    def test_schema_and_metadata(self):
        tables = {r[0] for r in self.con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertTrue({"metadata", "record", "nutrient_value", "portion", "product", "alias", "alias_fts"} <= tables)
        meta = dict(self.con.execute("SELECT key, value FROM metadata"))
        self.assertEqual(meta["formatVersion"], "1")
        self.assertEqual(len(meta["generation"]), 16)
        self.assertNotIn("builtAt", meta)
        self.assertIn("USDA_FNDDS", json.loads(meta["sources"]))
        self.assertTrue((self.out.parent / "pack.sqlite.sha256").exists())
        self.assertTrue((self.out.parent / "build-report.json").exists())

    def test_usda_mapping_units_and_zero_vs_absent(self):
        v = self.values("usda_1001")
        self.assertEqual(v[K("energy")], (61, 0))
        self.assertEqual(v[K("calcium")], (113, 0))
        self.assertEqual(v[K("vitamin_d")], (1.3, 0))
        self.assertEqual(v[K("alcohol")], (0.0, 0))             # cero declarado = fila con 0
        self.assertNotIn(K("caffeine"), v)                      # ausente = sin fila
        self.assertNotIn(K("iodine"), v)
        self.assertEqual(self.record("usda_1005")["source"], "USDA_FNDDS")

    def test_derived_epa_dha_and_priorities(self):
        self.assertEqual(self.values("usda_1001")[K("epa_dha")], (0.0, 2))
        v = self.values("usda_1002")
        self.assertNotIn(K("epa_dha"), v)                       # solo EPA: no se suma
        self.assertEqual(v[K("energy")], (165, 1))              # Atwater general, calidad 1
        self.assertEqual(v[K("fat")], (3.6, 0))                 # 1085 cuando falta 1004
        self.assertEqual(v[K("carbs")], (0, 0))                 # 1050
        self.assertEqual(v[K("folate_dfe")], (4, 1))            # folato total, calidad 1

    def test_not_calculable_and_precedence(self):
        self.assertIsNone(self.record("usda_1003"))
        counts = self.report["counts_by_source"]
        self.assertEqual(counts["USDA_FNDDS"]["not_calculable"], 1)
        self.assertEqual(counts["USDA_FNDDS"]["kept"], 3)   # 1002, 1004, 1005 (el 1001 pierde contra Foundation)
        self.assertEqual(counts["USDA_FOUNDATION"]["invalid_source_record"], 1)
        self.assertEqual(counts["USDA"]["fdcid_collisions_between_sources"], 1)
        self.assertEqual(self.record("usda_1001")["source"], "USDA_FOUNDATION")  # gana Foundation sobre FNDDS

    def test_sr_legacy_units_and_portions(self):
        v = self.values("usda_3001")
        self.assertEqual(v[K("selenium")], (28.5, 0))
        self.assertEqual(v[K("calcium")], (710, 0))
        self.assertIsNone(self.record("usda_3002"))
        portions = self.con.execute("SELECT seq, label, grams, ml FROM portion WHERE record_id='usda_3001' ORDER BY seq").fetchall()
        self.assertEqual(portions[0][:3], (1, "1 cup, diced", 132.0))
        self.assertAlmostEqual(portions[0][3], 236.588, places=3)
        self.assertAlmostEqual(self.record("usda_3001")["density_g_per_ml"], 132 / 236.588, places=3)
        self.assertEqual(self.record("usda_3001")["category"], "Dairy and Egg Products")

    def test_density_rules(self):
        self.assertIsNone(self.record("usda_1004")["density_g_per_ml"])        # 20 g/cup = 0,08: fuera de 0,2-2
        self.assertAlmostEqual(self.record("usda_1005")["density_g_per_ml"], 248 / 236.588, places=3)
        self.assertEqual(self.con.execute("SELECT count(*) FROM portion WHERE record_id='usda_1005'").fetchone()[0], 1)   # 0 g se descarta
        self.assertEqual(self.record("usda_1005")["form"], "LIQUID")
        self.assertIsNone(self.record("ciqual_5001")["density_g_per_ml"])
        self.assertIsNone(self.record("off_7801")["density_g_per_ml"])

    def test_fnndds_tablespoon_foundation_and_form(self):
        self.assertEqual(self.record("usda_2001")["form"], "POWDER")
        row = self.con.execute("SELECT label, grams, ml FROM portion WHERE record_id='usda_2001'").fetchone()
        self.assertEqual(row[0], "2 tablespoon")
        self.assertAlmostEqual(row[2], 29.574, places=3)
        self.assertEqual(self.record("usda_1002")["preparation"], "COOKED")     # "broiled"
        self.assertEqual(self.record("usda_1001")["form"], "LIQUID")

    def test_ciqual(self):
        v = self.values("ciqual_5001")
        self.assertEqual(v[K("protein")], (13.5, 0))             # coma decimal
        self.assertEqual(v[K("energy")], (274, 1))
        self.assertEqual(v[K("calcium")], (0.0, 3))              # "traces"
        self.assertEqual(v[K("vitamin_d")], (0.5, 3))            # "< 0,5"
        self.assertEqual(v[K("vitamin_a_rae")], (200.0, 2))      # retinol + beta-caroteno / 12
        self.assertEqual(v[K("sodium")], (1.5, 0))
        self.assertNotIn(K("alcohol"), v)                        # "-" = ausente
        self.assertIsNone(self.record("ciqual_5002"))            # sin grasa
        rec = self.record("ciqual_5001")
        self.assertEqual((rec["name"], rec["name_fr"], rec["name_en"], rec["form"]), ("Lait entier en poudre", "Lait entier en poudre", "Milk, whole, powder", "POWDER"))

    def test_off(self):
        v = self.values("off_7801")
        self.assertEqual(v[K("sodium")], (50.0, 0))              # g -> mg
        self.assertEqual(v[K("calcium")], (120.0, 0))
        self.assertEqual(v[K("vitamin_d")], (1.0, 0))            # g -> ug
        self.assertEqual(v[K("cholesterol")], (14.0, 0))
        self.assertEqual(v[K("iodine")], (200.0, 0))
        rec = self.record("off_7801")
        self.assertEqual((rec["basis_unit"], rec["form"], rec["brand"], rec["source"]), ("ml", "LIQUID", "Soprole", "OFF_CL"))
        self.assertEqual(self.con.execute("SELECT record_id FROM product WHERE gtin='7801'").fetchone()[0], "off_7801")
        self.assertIsNone(self.record("off_7802"))
        self.assertIsNone(self.record("off_7803"))
        self.assertIsNone(self.record("off_7804"))
        self.assertIsNone(self.record("off_7806"))
        v5 = self.values("off_7805")
        self.assertEqual(v5[K("calcium")], (0.2, 0))
        self.assertNotIn(K("cholesterol"), v5)                   # 7 g/100 g es dato corrupto: ausente
        self.assertEqual(self.con.execute("SELECT label, grams FROM portion WHERE record_id='off_7805'").fetchone(), ("30 g", 30.0))
        off = self.report["counts_by_source"]["OFF_CL"]
        self.assertEqual((off["app_accepted"], off["dropped_missing_required_macro"], off["kept"]), (3, 1, 2))

    def test_fts_and_alias_normalization(self):
        found = {r[0] for r in self.con.execute("SELECT record_id FROM alias_fts WHERE alias_norm MATCH 'leche'")}
        self.assertIn("usda_1001", found)
        self.assertIn("usda_2001", found)
        self.assertIn("off_7801", found)
        lacteo = self.con.execute("SELECT record_id FROM alias_fts WHERE alias_norm MATCH 'lacteo'").fetchall()
        self.assertEqual(lacteo, [("usda_1001",)])
        self.assertEqual(self.con.execute("SELECT name, name_es FROM record WHERE id='usda_1001'").fetchone(),
                         ("Leche entera", "Leche entera"))        # sufijo " (FDC 1001)" quitado
        bad = self.con.execute("SELECT count(*) FROM alias WHERE alias_norm GLOB '*[^a-z0-9 ]*' OR alias_norm LIKE '%  %'").fetchone()[0]
        self.assertEqual(bad, 0)
        langs = {r[0] for r in self.con.execute("SELECT DISTINCT lang FROM alias")}
        self.assertEqual(langs, {"en", "es", "fr"})

    def test_coverage_report(self):
        cov = self.report["coverage_pct_by_source"]
        self.assertEqual(cov["CIQUAL"]["energy"], 100.0)
        self.assertEqual(cov["USDA_FNDDS"]["calcium"], 0.0)        # 1002, 1004 y 1005 no traen calcio
        self.assertEqual(cov["USDA_FOUNDATION"]["calcium"], 50.0)  # 1001 si; 2001 no


class Determinism(unittest.TestCase):
    def test_two_builds_same_sha256(self):
        with tempfile.TemporaryDirectory() as tmp:
            inputs = make_inputs(tmp)
            a = B.build(inputs, Path(tmp) / "a" / "pack.sqlite", verify_locks=False, off_verify=False)
            b = B.build(inputs, Path(tmp) / "b" / "pack.sqlite", verify_locks=False, off_verify=False)
            self.assertEqual(a["sha256"], b["sha256"])
            self.assertEqual(a["generation"], b["generation"])
            self.assertEqual(hashlib.sha256((Path(tmp) / "a" / "pack.sqlite").read_bytes()).hexdigest(), a["sha256"])


class Failures(unittest.TestCase):
    def test_mismatched_nutrient_name_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            inputs = make_inputs(tmp, bad_name=True)
            with self.assertRaisesRegex(B.BuildError, "1087"):
                B.build(inputs, Path(tmp) / "x.sqlite", verify_locks=False, off_verify=False)

    def test_wrong_ciqual_unit_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            inputs = make_inputs(tmp)
            text = Path(inputs.ciqual_const).read_text(encoding="utf-8").replace("Name (mg/100g)", "Name (g/100g)", 1)
            Path(inputs.ciqual_const).write_text(text, encoding="utf-8")
            with self.assertRaises(B.BuildError):
                B.build(inputs, Path(tmp) / "x.sqlite", verify_locks=False, off_verify=False)


class PureHelpers(unittest.TestCase):
    def test_parse_ciqual_value(self):
        self.assertEqual(B.parse_ciqual_value("13,5"), (13.5, 0))
        self.assertEqual(B.parse_ciqual_value("traces"), (0.0, 3))
        self.assertEqual(B.parse_ciqual_value("< 0,05"), (0.05, 3))
        self.assertIsNone(B.parse_ciqual_value("-"))
        self.assertIsNone(B.parse_ciqual_value(""))

    def test_portion_ml_and_units(self):
        self.assertAlmostEqual(N.portion_ml("1 cup", None, "undetermined"), 236.588, places=3)
        self.assertAlmostEqual(N.portion_ml("1/2 fl oz", None, None), 14.787, places=3)
        self.assertAlmostEqual(N.portion_ml("x", 2, "tbsp"), 29.574, places=3)
        self.assertIsNone(N.portion_ml("1 slice", 1, "slice"))
        self.assertEqual(N.normalize_unit("UG"), "\u00b5g")
        self.assertEqual(N.normalize_unit("\ufffdg"), "\u00b5g")
        self.assertEqual(N.normalize_unit("KCAL"), "kcal")

    def test_alias_norm(self):
        self.assertEqual(N.alias_norm("  Br\u00f3coli  CRUDO (\u00f1o\u00f1o) 2% "), "brocoli crudo nono 2")

    def test_physical_form(self):
        self.assertEqual(N.physical_form("Milk, dry, nonfat"), "POWDER")
        self.assertEqual(N.physical_form("Nuts, almonds, dry roasted"), "NA")
        self.assertEqual(N.physical_form("Formula, prepared from powder", liquid_hint=True), "LIQUID")
        self.assertEqual(N.physical_form("Orange juice"), "LIQUID")

    def test_off_column_verification_accepts_and_rejects(self):
        rows = []
        for i in range(60):
            row = [""] * 210
            row[O.COL_NAME] = "producto %d" % i
            row[O.COL_FAT] = "10"
            row[93] = "4"          # saturadas <= grasa
            row[131] = "40"        # "azucares anadidos" > azucares: incoherente
            row[O.COL_SUGARS] = "5"
            rows.append(row)
        candidates = ((93, "sat_fat", (("le", O.COL_FAT, 0.5, 0.99),)), (131, "added_sugars", (("le", O.COL_SUGARS, 0.5, 0.99),)))
        used, report = O.verify_columns(rows, candidates)
        self.assertIn(93, used)
        self.assertNotIn(131, used)
        self.assertEqual({e["column"]: e["verdict"] for e in report}, {93: "verified", 131: "rejected"})


@unittest.skipUnless(REAL_OK, "fuentes reales no disponibles")
class RealBuild(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls.tmp.name) / "nutrition_catalog_v0.sqlite"
        cls.report = B.build(B.default_inputs(REAL_INPUTS, REAL_ASSETS), cls.out)
        cls.con = sqlite3.connect("file:%s?mode=ro" % cls.out.as_posix(), uri=True)

    @classmethod
    def tearDownClass(cls):
        cls.con.close()
        cls.tmp.cleanup()

    def test_counts(self):
        counts = self.report["counts_by_source"]
        self.assertGreaterEqual(counts["USDA_FNDDS"]["kept"], 5400)
        self.assertGreaterEqual(counts["USDA_SR_LEGACY"]["kept"], 7700)
        self.assertGreaterEqual(counts["CIQUAL"]["kept"], 3000)
        self.assertGreaterEqual(counts["OFF_CL"]["kept"], 5000)
        self.assertEqual(counts["USDA_FOUNDATION"]["kept"], 311)

    def test_whole_milk(self):
        values = {n: a for n, a, _q in self.con.execute("SELECT nutrient_id, amount, quality FROM nutrient_value WHERE record_id='usda_171265'")}
        self.assertTrue(100 <= values[K("calcium")] <= 130)
        self.assertIn(K("vitamin_d"), values)
        self.assertEqual(self.con.execute("SELECT form FROM record WHERE id='usda_171265'").fetchone()[0], "LIQUID")

    def test_sr_calcium_coverage(self):
        self.assertGreaterEqual(self.report["coverage_pct_by_source"]["USDA_SR_LEGACY"]["calcium"], 90.0)

    def test_fts_on_real_pack(self):
        found = {r[0] for r in self.con.execute("SELECT record_id FROM alias_fts WHERE alias_norm MATCH 'leche entera'")}
        self.assertTrue(found)
        gtin = self.con.execute("SELECT gtin, record_id FROM product LIMIT 1").fetchone()
        self.assertEqual("off_" + gtin[0], gtin[1])

    def test_off_columns_have_evidence(self):
        used = self.report["off"]["columns_used"]
        self.assertEqual(used["156"], "sodium")
        self.assertEqual(used["177"], "calcium")
        self.assertNotIn("131", used)   # azucares anadidos: incoherente con azucares en ~7 % de las filas


if __name__ == "__main__":
    unittest.main()
