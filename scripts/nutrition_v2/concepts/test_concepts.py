"""Pruebas de las herramientas de conceptos F1b (sin indice real)."""
import copy
import json
import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import candidates  # noqa: E402
import check_stage1  # noqa: E402
import check_stage3  # noqa: E402
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


if __name__ == "__main__":
    unittest.main()
