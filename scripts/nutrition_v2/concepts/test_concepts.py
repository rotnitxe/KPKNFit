"""Pruebas de las herramientas de conceptos F1b (sin indice real)."""
import copy
import json
import os
import gzip
import shutil
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import candidates  # noqa: E402
import check_stage1  # noqa: E402
import check_stage3  # noqa: E402
import export  # noqa: E402
import merge  # noqa: E402
import schema  # noqa: E402


def rice():
    return {
        "id": "arroz_blanco", "name": "Arroz blanco", "kind": "FOOD", "category": "PASTAS_ARROZ",
        "aliasesEs": ["arroz", "Arroz blanco"], "aliasesEn": ["white rice"], "typos": ["aroz"],
        "defaultState": "COOKED", "defaultForm": "NA",
        "variants": [{"state": "COOKED", "form": "NA", "query": "rice white cooked"},
                     {"state": "RAW", "form": "NA", "query": "rice white raw"}],
        "countable": False, "unit": None,
        "portions": [{"label": "taza", "grams": 160, "state": "COOKED"}],
        "defaultGrams": 180, "densityGPerMl": None, "reconstitution": None, "components": None, "notes": "",
    }


def milk():
    c = rice()
    c.update(id="leche_polvo", name="Leche en polvo", kind="DRINK", category="LACTEOS",
             aliasesEs=["leche en polvo"], aliasesEn=[], typos=[], defaultState="NA", defaultForm="POWDER_DRY",
             variants=[{"state": "NA", "form": "POWDER_DRY", "query": "milk dry whole"}],
             portions=[{"label": "cucharada", "grams": 8, "state": "NA"}],
             reconstitution={"gramsPer100ml": 13, "medium": "AGUA"})
    return c


def dish():
    return {
        "id": "pastel_choclo", "name": "Pastel de choclo", "kind": "DISH", "category": "PLATOS",
        "aliasesEs": ["pastel de choclo"], "aliasesEn": [], "typos": [],
        "defaultState": "BAKED", "defaultForm": "NA", "variants": [],
        "countable": False, "unit": None, "portions": [], "defaultGrams": 350,
        "components": [{"query": "corn sweet cooked", "grams": 150, "note": "pino"}],
    }


def stage3(c):
    c = copy.deepcopy(c)
    for i, v in enumerate(c.get("variants") or []):
        v["ref"] = "r%d" % i
    for i, v in enumerate(c.get("components") or []):
        v["ref"] = "c%d" % i
    return c


class FakeRef:
    def __init__(self, rid, name, kcal, portions=()):
        self.id, self.name = rid, name
        self.per100g = {"kcal": kcal, "protein": 1, "fat": 1, "carbs": 1}
        self.portions = list(portions)
        self.source = "FAKE"


class FakeIndex:
    def __init__(self, refs):
        self.refs = {r.id: r for r in refs}

    def get(self, rid):
        return self.refs.get(rid)

    def search(self, query, k=8, sources=None):
        return [(1.0, r) for r in list(self.refs.values())[:k]]


def write(path, rows):
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write((r if isinstance(r, str) else json.dumps(r, ensure_ascii=False)) + "\n")


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, True)

    def p(self, name):
        return os.path.join(self.tmp, name)


class NormalizeTest(unittest.TestCase):
    def test_normalize(self):
        self.assertEqual(schema.normalize("  Ñoquis  de Papá!! "), "noquis de papa")
        self.assertEqual(schema.normalize("Coca-Cola 1,5L"), "coca cola 1 5l")
        self.assertEqual(schema.normalize("¿¿"), "")


class ValidateTest(unittest.TestCase):
    def errs(self, c, stage=1):
        return schema.validate_concept(c, stage)

    def assertErr(self, c, text, stage=1):
        self.assertTrue(any(text in e for e in self.errs(c, stage)), self.errs(c, stage))

    def test_valid(self):
        for c in (rice(), milk(), dish()):
            self.assertEqual(self.errs(c), [])
            self.assertEqual(self.errs(stage3(c), 3), [])

    def test_id(self):
        c = rice(); c["id"] = "Arroz Blanco"; self.assertErr(c, "snake_case")
        c["id"] = "arroz__x"; self.assertErr(c, "snake_case")

    def test_basic_fields(self):
        c = rice(); c["name"] = " "; self.assertErr(c, "name")
        c = rice(); c["kind"] = "X"; self.assertErr(c, "kind")
        c = rice(); c["category"] = "X"; self.assertErr(c, "category")
        c = rice(); c["aliasesEs"] = []; self.assertErr(c, "aliasesEs")
        c = rice(); c["typos"] = ["a", ""]; self.assertErr(c, "typos")
        c = rice(); c["defaultState"] = "BOILED"; self.assertErr(c, "defaultState")

    def test_default_covered(self):
        c = rice(); c["defaultState"] = "FRIED"; self.assertErr(c, "ninguna variante cubre")

    def test_variants_required(self):
        c = rice(); c["variants"] = []; self.assertErr(c, "variants vacio")
        c = dish(); c["components"] = []; self.assertErr(c, "components no vacio")

    def test_countable(self):
        c = rice(); c["countable"] = True; self.assertErr(c, "countable")
        c["unit"] = {"names": ["unidad"], "grams": {"S": 60, "M": 50, "L": 70}}; self.assertErr(c, "S <= M")
        c["unit"]["grams"] = {"S": 44, "M": 50, "L": 56}; self.assertEqual(self.errs(c), [])

    def test_portions(self):
        c = rice(); c["portions"][0]["grams"] = 0; self.assertErr(c, "portions[0].grams")
        c = rice(); c["portions"][0]["state"] = "X"; self.assertErr(c, "portions[0].state")

    def test_default_grams(self):
        c = rice(); c["defaultGrams"] = 0; self.assertErr(c, "defaultGrams")

    def test_reconstitution(self):
        c = rice(); c["reconstitution"] = {"gramsPer100ml": 13, "medium": "AGUA"}
        self.assertErr(c, "reconstitution solo")

    def test_component_grams(self):
        c = dish(); c["components"][0]["grams"] = -1; self.assertErr(c, "components[0].grams")

    def test_stage3(self):
        c = rice(); self.assertErr(c, "sin ref ni noMatch", 3)
        c["variants"][0]["ref"] = "x"; c["variants"][1]["noMatch"] = True
        self.assertEqual(self.errs(c, 3), [])
        d = dish(); self.assertErr(d, "sin ref", 3)


class Stage1Test(Base):
    def test_collisions_and_dups(self):
        a, b = rice(), rice()
        b["id"] = "arroz_integral"; b["aliasesEs"] = ["Arroz", "arroz integral"]; b["aliasesEn"] = []
        a["aliasesEs"] = ["arroz", "arroz"]
        write(self.p("a.jsonl"), [a, b])
        r = check_stage1.check_files([self.p("a.jsonl")])
        self.assertEqual(r["errors"], [])
        self.assertIn("arroz", r["collisions"])
        self.assertTrue(any("mismo concepto" in w for w in r["warnings"]))
        self.assertEqual(r["stats"][self.p("a.jsonl")]["concepts"], 2)

    def test_dup_ids_across_files_and_exit(self):
        write(self.p("a.jsonl"), [rice()])
        write(self.p("b.jsonl"), [rice(), "{malo"])
        r = check_stage1.check_files([self.p("a.jsonl"), self.p("b.jsonl")])
        self.assertTrue(any("id repetido" in e for e in r["errors"]))
        self.assertTrue(any("JSON invalido" in e for e in r["errors"]))
        write(self.p("ok.jsonl"), [rice()])
        self.assertEqual(check_stage1.main([self.p("ok.jsonl")]), 0)
        self.assertEqual(check_stage1.main([self.p("b.jsonl")]), 1)


class CandidatesTest(Base):
    def test_process(self):
        idx = FakeIndex([FakeRef("a", "Rice", 130, [("1 cup", 158.0)]), FakeRef("b", "Rice raw", 360)])
        write(self.p("s1.jsonl"), [dish(), rice()])
        n, q = candidates.process(idx, self.p("s1.jsonl"), self.p("s2.jsonl"), 1)
        self.assertEqual((n, q), (2, 3))
        with open(self.p("s2.jsonl"), encoding="utf-8") as f:
            rows = [json.loads(l) for l in f]
        cand = rows[1]["variants"][0]["candidates"]
        self.assertEqual(len(cand), 1)
        self.assertEqual(cand[0]["ref"], "a")
        self.assertEqual(cand[0]["portions"], ["1 cup = 158"])
        self.assertIn("candidates", rows[0]["components"][0])


class Stage3Test(Base):
    def run3(self, concept, refs):
        write(self.p("s3.jsonl"), [concept])
        return check_stage3.check_files([self.p("s3.jsonl")], FakeIndex(refs))

    def test_clean(self):
        r = self.run3(stage3(rice()), [FakeRef("r0", "Rice, white, cooked", 130, [("1 cup", 158)]),
                                       FakeRef("r1", "Rice, white, raw", 360)])
        self.assertEqual((r["errors"], r["warnings"]), ([], []))

    def test_missing_ref(self):
        r = self.run3(stage3(rice()), [FakeRef("r0", "Rice cooked", 130)])
        self.assertTrue(any("inexistente" in e for e in r["errors"]))

    def test_kcal_range_and_exempt(self):
        c = stage3(rice()); c["category"] = "ACEITES_GRASAS"
        r = self.run3(c, [FakeRef("r0", "Rice cooked", 130), FakeRef("r1", "Rice raw", 360)])
        self.assertTrue(any("fuera de" in w for w in r["warnings"]))
        m = stage3(milk()); m["category"] = "BEBIDAS"
        r = self.run3(m, [FakeRef("r0", "Milk dry", 500)])
        self.assertEqual(r["warnings"], [])
        m["variants"][0]["form"] = "NA"; m["defaultForm"] = "NA"
        r = self.run3(m, [FakeRef("r0", "Milk dry", 500)])
        self.assertTrue(any("fuera de" in w for w in r["warnings"]))

    def test_portion_mismatch(self):
        c = stage3(rice()); c["portions"][0]["grams"] = 300
        r = self.run3(c, [FakeRef("r0", "Rice cooked", 130, [("1 cup", 158)]), FakeRef("r1", "Rice raw", 360)])
        self.assertTrue(any("difiere" in w for w in r["warnings"]))
        c["portions"][0]["grams"] = 150
        r = self.run3(c, [FakeRef("r0", "Rice cooked", 130, [("2 cup", 316)]), FakeRef("r1", "Rice raw", 360)])
        self.assertEqual(r["warnings"], [])

    def test_raw_cooked_mismatch(self):
        r = self.run3(stage3(rice()), [FakeRef("r0", "Rice, white, raw", 130), FakeRef("r1", "Rice, cooked", 360)])
        self.assertEqual(len([w for w in r["warnings"] if "enlaza ficha" in w]), 2)


class MergeTest(Base):
    def test_merge(self):
        d = self.p("in"); os.mkdir(d)
        bad = stage3(rice()); bad["id"] = "malo"; bad["defaultGrams"] = 0
        other = stage3(rice()); other["id"] = "arroz_2"; other["aliasesEs"] = ["ARROZ"]
        other["variants"][0]["candidates"] = [{"ref": "z"}]
        write(os.path.join(d, "a.jsonl"), [stage3(milk()), stage3(rice()), bad])
        write(os.path.join(d, "b.jsonl"), [other, stage3(dish())])
        concepts, amb, rep = merge.merge(sorted(os.path.join(d, f) for f in os.listdir(d)))
        self.assertEqual([c["id"] for c in concepts], ["arroz_2", "arroz_blanco", "leche_polvo", "pastel_choclo"])
        self.assertEqual(len(rep["rejected"]), 1)
        self.assertEqual(rep["rejected"][0]["id"], "malo")
        self.assertIn({"alias": "arroz", "concepts": ["arroz_2", "arroz_blanco"]}, amb)
        self.assertEqual(rep["ambiguousAliases"], len(amb))
        self.assertNotIn("candidates", concepts[0]["variants"][0])
        self.assertEqual(rep["byCategory"]["PASTAS_ARROZ"], 2)
        self.assertEqual(rep["aliasesByType"]["typos"], 2)
        out = [self.p(n) for n in ("c.jsonl", "a.jsonl", "r.json")]
        self.assertEqual(merge.main(["--in-dir", d, "--out", out[0], "--ambiguous", out[1], "--report", out[2]]), 0)
        with open(out[0], encoding="utf-8") as f:
            self.assertEqual(len(f.read().splitlines()), 4)

    def test_repeated_id_folds_aliases(self):
        d = self.p("in"); os.mkdir(d)
        dup = stage3(rice()); dup["aliasesEs"] = ["arroz", "arroz graneado"]; dup["defaultGrams"] = 999
        write(os.path.join(d, "a.jsonl"), [stage3(rice())])
        write(os.path.join(d, "b.jsonl"), [dup])
        paths = sorted(os.path.join(d, f) for f in os.listdir(d))
        concepts, _, rep = merge.merge(paths)
        self.assertEqual(len(concepts), 1)
        self.assertEqual(rep["rejected"], [])
        self.assertEqual(rep["folded"], [{"where": "b.jsonl:1", "id": "arroz_blanco", "into": "a.jsonl:1",
                                          "aliasesMoved": 1}])
        self.assertIn("arroz graneado", concepts[0]["aliasesEs"])
        self.assertNotEqual(concepts[0]["defaultGrams"], 999)
        concepts, _, rep = merge.merge(paths, {"keepFrom": {"arroz_blanco": "b.jsonl"}})
        self.assertEqual(concepts[0]["defaultGrams"], 999)
        self.assertIn("Arroz blanco", concepts[0]["aliasesEs"])
        self.assertEqual(rep["folded"][0]["into"], "b.jsonl:1")


class CurationTest(Base):
    def raw(self):
        a = stage3(rice())
        b = stage3(rice()); b["id"] = "arroz_con_leche"
        b["aliasesEs"] = ["arroz con leche", "Arroz"]; b["aliasesEn"] = ["rice pudding"]; b["typos"] = ["aroz con leche"]
        m = stage3(milk()); m["variants"].append({"state": "NA", "form": "LIQUID", "query": "milk", "ref": "x"})
        m["aliasesEs"] = ["leche en polvo", "leche con azucar"]
        return {c["id"]: ("t:1", c) for c in (a, b, m)}

    def test_drop_moves_aliases(self):
        raw = self.raw()
        rep = merge.apply_curation(raw, {"drop": {"arroz_con_leche": "arroz_blanco", "nada": None}})
        self.assertNotIn("arroz_con_leche", raw)
        dest = raw["arroz_blanco"][1]
        self.assertEqual(dest["aliasesEs"], ["arroz", "Arroz blanco", "arroz con leche"])
        self.assertEqual(dest["aliasesEn"], ["white rice", "rice pudding"])
        self.assertEqual(dest["typos"], ["aroz", "aroz con leche"])
        self.assertEqual((rep["dropped"], rep["aliasesMoved"]), (1, 3))
        self.assertEqual(len(rep["warnings"]), 1)

    def test_drop_variants_and_aliases(self):
        raw = self.raw()
        rep = merge.apply_curation(raw, {
            "dropVariants": [{"id": "leche_polvo", "state": None, "form": "LIQUID"}, {"id": "fantasma", "form": "NA"}],
            "dropAliases": {"leche_polvo": ["Leche con Azúcar"], "otro": ["x"]}})
        m = raw["leche_polvo"][1]
        self.assertEqual(len(m["variants"]), 1)
        self.assertEqual(m["aliasesEs"], ["leche en polvo"])
        self.assertEqual((rep["variantsDropped"], rep["aliasesDropped"], len(rep["warnings"])), (1, 1, 2))

    def test_merge_with_curation_in_report(self):
        d = self.p("in"); os.mkdir(d)
        b = stage3(rice()); b["id"] = "arroz_2"; b["aliasesEs"] = ["arroz dos"]
        write(os.path.join(d, "a.jsonl"), [stage3(rice()), b])
        cur = self.p("cur.json")
        with open(cur, "w", encoding="utf-8") as f:
            json.dump({"drop": {"arroz_2": "arroz_blanco"}}, f)
        out = [self.p(n) for n in ("c.jsonl", "a.jsonl", "r.json")]
        self.assertEqual(merge.main(["--in-dir", d, "--out", out[0], "--ambiguous", out[1], "--report", out[2],
                                     "--curation", cur]), 0)
        with open(out[2], encoding="utf-8") as f:
            rep = json.load(f)
        self.assertEqual(rep["accepted"], 1)
        self.assertEqual(rep["curation"]["dropped"], 1)


class ExportTest(Base):
    def make_pack(self):
        path = self.p("pack.sqlite")
        db = sqlite3.connect(path)
        db.executescript("""
            CREATE TABLE metadata(key TEXT, value TEXT);
            CREATE TABLE record(id TEXT, name TEXT, name_es TEXT, density_g_per_ml REAL);
            CREATE TABLE nutrient_value(record_id TEXT, nutrient_id INTEGER, amount REAL, quality INTEGER);
            CREATE TABLE portion(record_id TEXT, seq INTEGER, label TEXT, label_es TEXT, grams REAL, ml REAL);
            INSERT INTO metadata VALUES('generation','gen1');
            INSERT INTO record VALUES('r0','Rice cooked','Arroz',NULL),('r1','Rice raw','Arroz crudo',NULL),('c1','Corn','Choclo',1.1);
            INSERT INTO nutrient_value VALUES
              ('r0',1,130,0),('r0',2,2.7,0),('r0',15,10,0),
              ('r1',1,360,0),('r1',2,7,0),
              ('c1',1,100,0),('c1',2,3,0),('c1',15,20,0);
            INSERT INTO portion VALUES('r0',1,'1 cup','1 taza',158,NULL),('r0',2,'1 oz',NULL,28,NULL);
        """)
        db.commit(); db.close()
        return path

    def run_export(self, concepts, *flags):
        write(self.p("c.jsonl"), concepts)
        write(self.p("a.jsonl"), [{"alias": "x", "concepts": ["a", "b"]}])
        out = self.p("out.json.gz" if "--gzip" in flags else "out.json")
        code = export.main(["--concepts", self.p("c.jsonl"), "--ambiguous", self.p("a.jsonl"),
                            "--pack", self.make_pack(), "--out", out] + list(flags))
        return code, out

    def load(self, out):
        opener = gzip.open if out.endswith(".gz") else open
        with opener(out, "rt", encoding="utf-8") as f:
            return json.load(f)

    def concepts(self):
        r = stage3(rice()); r["variants"][0]["ref"] = "r0"; r["variants"][1]["ref"] = "r1"
        d = stage3(dish())
        d["components"] = [{"query": "corn", "grams": 100, "ref": "c1"},
                           {"query": "rice", "grams": 100, "ref": "r0"},
                           {"query": "mystery", "grams": 100, "noMatch": True}]
        return [r, d]

    def test_full_export(self):
        code, out = self.run_export(self.concepts())
        self.assertEqual(code, 0)
        data = self.load(out)
        self.assertEqual(data["generation"], "gen1")
        self.assertEqual(len(data["conceptsSha256"]), 64)
        self.assertEqual(set(data["records"]), {"r0", "r1", "c1", "kpkn_pastel_choclo"})
        r0 = data["records"]["r0"]
        self.assertEqual(r0["per100g"], {"energy": 130, "protein": 2.7, "calcium": 10})
        self.assertEqual(r0["portions"], [{"label": "1 taza", "grams": 158}, {"label": "1 oz", "grams": 28}])
        self.assertEqual(data["records"]["c1"]["densityGPerMl"], 1.1)
        der = data["records"]["kpkn_pastel_choclo"]["per100g"]
        self.assertAlmostEqual(der["energy"], (100 * 100 / 100 + 100 * 130 / 100) / 300 * 100, 3)
        self.assertAlmostEqual(der["calcium"], (20 + 10) / 300 * 100, 3)
        pastel = [c for c in data["concepts"] if c["id"] == "pastel_choclo"][0]
        self.assertEqual(pastel["variants"], [{"state": None, "form": "NA", "query": "", "ref": "kpkn_pastel_choclo"}])
        self.assertEqual(data["ambiguous"][0]["alias"], "x")

    def test_missing_nutrient_in_one_component(self):
        cs = self.concepts()
        cs[1]["components"][0]["ref"] = "r1"  # r1 no trae calcio
        code, out = self.run_export(cs)
        self.assertEqual(code, 0)
        der = self.load(out)["records"]["kpkn_pastel_choclo"]["per100g"]
        self.assertNotIn("calcium", der)
        self.assertIn("energy", der)

    def test_macros_only_and_gzip(self):
        code, out = self.run_export(self.concepts(), "--macros-only", "--gzip")
        self.assertEqual(code, 0)
        data = self.load(out)
        self.assertEqual(data["records"]["r0"]["per100g"], {"energy": 130, "protein": 2.7})
        self.assertNotIn("calcium", data["records"]["kpkn_pastel_choclo"]["per100g"])

    def test_missing_ref_is_error(self):
        cs = self.concepts(); cs[0]["variants"][0]["ref"] = "no_existe"
        code, _ = self.run_export(cs)
        self.assertEqual(code, 1)

    def test_concept_without_record_is_warning(self):
        lone = stage3(rice()); lone["id"] = "solo"
        for v in lone["variants"]:
            v.pop("ref"); v["noMatch"] = True
        code, _ = self.run_export(self.concepts() + [lone])
        self.assertEqual(code, 0)

    def test_nutrient_keys_match_kotlin(self):
        if os.path.exists(export.NUTRIENT_ID_KT):
            self.assertTrue(export.check_nutrient_keys())


if __name__ == "__main__":
    unittest.main()
