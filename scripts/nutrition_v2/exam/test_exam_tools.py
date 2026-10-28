import json
import os
import random
import string
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))
import agree  # noqa: E402
import curate  # noqa: E402
import gold_build  # noqa: E402

REAL_CACHE_DIR = Path(r"C:\kw\nutri-examen\cache")


def write_lines(path, rows):
    os.makedirs(os.path.dirname(str(path)), exist_ok=True)
    with open(str(path), "w", encoding="utf-8", newline="\n") as stream:
        for row in rows:
            stream.write((row if isinstance(row, str) else json.dumps(row, ensure_ascii=False)) + "\n")


def read_lines(path):
    with open(str(path), encoding="utf-8") as stream:
        return [json.loads(line) for line in stream if line.strip()]


# =========================================================================== curate

def random_word(rng, length=12):
    return "".join(rng.choice(string.ascii_lowercase) for _ in range(length))


def synthetic_raw(raw_dir, per_phenomenon, regions=(("CL", 0.7), ("AR", 0.3)), seed=7):
    """Un archivo por fenomeno con textos distintos entre si (palabras al azar)."""
    rng = random.Random(seed)
    styles = list(curate.STYLES)
    meals = list(curate.MEAL_TYPES)
    for code, count in per_phenomenon.items():
        rows = []
        n_primary = int(count * regions[0][1])
        for k in range(count):
            region = regions[0][0] if k < n_primary else regions[1][0]
            rows.append({
                "tmpId": "%s-%03d" % (code, k + 1),
                "text": "%s %s %s" % (random_word(rng), random_word(rng), random_word(rng)),
                "mealType": meals[k % 4], "region": region, "style": styles[k % 4],
                "persona": "x", "phenomena": [code],
            })
        write_lines(Path(raw_dir) / ("%s.jsonl" % code), rows)


def spec_for(targets, dev=400, test=600):
    return {"split": {"dev": dev, "test": test},
            "phenomena": {code: {"target": t} for code, t in targets.items()}}


class CurateTests(unittest.TestCase):
    def test_normalize_and_jaccard(self):
        self.assertEqual(curate.normalize("  Pan, CON   Palta!! Á é"), "pan con palta a e")
        a = curate.trigrams(curate.normalize("arroz con pollo y ensalada"))
        b = curate.trigrams(curate.normalize("arroz con pollo y ensaladas"))
        self.assertGreaterEqual(curate.jaccard(a, b), 0.8)
        c = curate.trigrams(curate.normalize("una manzana grande"))
        self.assertLess(curate.jaccard(a, c), 0.3)

    def _raw_with_noise(self, tmp):
        raw = Path(tmp) / "raw"
        base = {"mealType": "LUNCH", "region": "CL", "style": "telefono", "persona": "p"}

        def item(tmp_id, text, **over):
            row = dict(base, tmpId=tmp_id, text=text, phenomena=["F01"])
            row.update(over)
            return row

        long_text = " ".join(["palabra"] * 61)
        write_lines(raw / "A.jsonl", [
            item("A-001", "Pan con palta"),
            item("A-002", "pan  con PALTA!"),                       # exacto tras normalizar
            item("A-003", "arroz con pollo y ensalada"),
            item("A-004", "arroz con pollo y ensaladas"),            # casi duplicado
            item("A-005", "una manzana grande"),
            item("A-006", "12345 ???"),                              # sin letras
            item("A-007", long_text),                                # > 60 palabras
            item("A-008", "   "),                                    # vacio
            item("A-009", "yogur con granola", region="BR"),         # region invalida
            item("A-010", "te con leche", phenomena=["F99"]),        # fenomeno invalido
            item("A-011", "cafe con leche", mealType="BRUNCH"),      # mealType invalido
            item("A-012", "tostadas con queso", style="formal"),     # style invalido
            item("A-013", "huevo frito", phenomena=[]),              # sin fenomeno
            "esto no es json",
            item("A-001", "fideos con salsa bolonesa"),              # tmpId repetido
        ])
        return raw

    def test_filters_and_dedup(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = self._raw_with_noise(tmp)
            result = curate.curate(str(raw), spec_for({"F01": 100}))
            kept = [c["text"] for c in result["cases"]]
            self.assertEqual(sorted(kept), sorted(["Pan con palta", "arroz con pollo y ensalada",
                                                   "una manzana grande"]))
            reasons = {d["tmpId"]: d["reason"] for d in result["report"]["discards"] if d["tmpId"]}
            self.assertIn("duplicado exacto", reasons["A-002"])
            self.assertIn("casi duplicado", reasons["A-004"])
            self.assertIn("sin letras", reasons["A-006"])
            self.assertIn("largo", reasons["A-007"])
            self.assertIn("region", reasons["A-009"])
            self.assertIn("fenomeno", reasons["A-010"])
            self.assertIn("mealType", reasons["A-011"])
            self.assertIn("style", reasons["A-012"])
            self.assertIn("phenomena", reasons["A-013"])
            all_reasons = [d["reason"] for d in result["report"]["discards"]]
            self.assertIn("JSON invalido", all_reasons)
            self.assertIn("tmpId repetido", all_reasons)

    def test_ids_and_origin_are_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            synthetic_raw(tmp, {"F02": 30, "F01": 30})
            spec = spec_for({"F01": 10, "F02": 10})
            first = curate.curate(tmp, spec)
            second = curate.curate(tmp, spec)
            self.assertEqual(first["cases"], second["cases"])
            self.assertEqual(first["reserve"], second["reserve"])
            ids = [c["id"] for c in first["cases"]]
            self.assertEqual(ids, ["E%04d" % n for n in range(1, 21)])
            # orden: fenomeno principal (F01 primero) y luego tmpId
            self.assertTrue(all(c["phenomena"][0] == "F01" for c in first["cases"][:10]))
            origins = [c["origin"] for c in first["cases"][:10]]
            self.assertEqual(origins, sorted(origins))
            self.assertTrue(all(o.startswith("F01-") for o in origins))
            other = curate.curate(tmp, spec, seed=1)
            self.assertNotEqual([c["origin"] for c in first["cases"]], [c["origin"] for c in other["cases"]])
            self.assertEqual(len(first["reserve"]), 40)

    def test_stratified_split_per_phenomenon(self):
        with tempfile.TemporaryDirectory() as tmp:
            targets = {"F01": 50, "F02": 80, "F03": 70, "F04": 40}
            synthetic_raw(tmp, {code: n + 20 for code, n in targets.items()})
            result = curate.curate(tmp, spec_for(targets))
            report = result["report"]
            self.assertEqual(report["total"], 240)
            self.assertEqual(report["split"], {"dev": 96, "test": 144})
            for code, target in targets.items():
                self.assertEqual(report["byPhenomenon"][code]["dev"], int(target * 0.4))
                self.assertEqual(report["byPhenomenon"][code]["test"], target - int(target * 0.4))
            self.assertTrue(all(c["split"] in ("dev", "test") for c in result["cases"]))

    def test_split_rounding_keeps_global_total(self):
        counts = curate.split_counts({"F01": 7, "F02": 7, "F03": 6}, 0.4)
        self.assertEqual(sum(counts.values()), 8)
        for code, n in {"F01": 7, "F02": 7, "F03": 6}.items():
            self.assertLessEqual(abs(counts[code] - 0.4 * n), 1.0)

    def test_region_mix_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            synthetic_raw(tmp, {"F01": 100})
            result = curate.curate(tmp, spec_for({"F01": 20}))
            self.assertEqual(result["report"]["byRegion"], {"AR": 6, "CL": 14})
            dev = [c for c in result["cases"] if c["split"] == "dev"]
            self.assertEqual(len(dev), 8)
            self.assertTrue(any(c["region"] == "AR" for c in dev))

    def test_shortfall_keeps_proportions_and_reports(self):
        with tempfile.TemporaryDirectory() as tmp:
            synthetic_raw(tmp, {"F01": 12})
            result = curate.curate(tmp, spec_for({"F01": 50}))
            self.assertEqual(result["report"]["total"], 12)
            self.assertEqual(result["report"]["byPhenomenon"]["F01"]["shortfall"], 38)
            self.assertEqual(result["reserve"], [])

    def test_write_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp) / "raw"
            synthetic_raw(raw, {"F01": 30, "F02": 30})
            out = Path(tmp) / "out"
            spec_path = Path(tmp) / "matriz.json"
            spec_path.write_text(json.dumps(spec_for({"F01": 10, "F02": 10})), encoding="utf-8")
            self.assertEqual(curate.main(["--raw", str(raw), "--spec", str(spec_path), "--out", str(out),
                                          "--raw-glob", "*.jsonl"]), 0)
            all_cases = read_lines(out / "cases" / "all.jsonl")
            dev = read_lines(out / "cases" / "dev.jsonl")
            test = read_lines(out / "cases" / "test.jsonl")
            self.assertEqual(len(all_cases), 20)
            self.assertEqual(len(dev) + len(test), 20)
            self.assertEqual({c["id"] for c in dev} | {c["id"] for c in test}, {c["id"] for c in all_cases})
            self.assertEqual(list(all_cases[0].keys()),
                             ["id", "split", "text", "mealType", "region", "phenomena", "style", "origin",
                              "lang", "group"])
            self.assertEqual(len(read_lines(out / "work" / "reserve.jsonl")), 40)
            report = json.loads((out / "work" / "curation-report.json").read_text(encoding="utf-8"))
            self.assertEqual(report["split"], {"dev": 8, "test": 12})


# =========================================================================== gold_build

def ref(rid, source, name, kcal, protein, fat, carbs):
    return SimpleNamespace(
        id=rid, source=source, code=rid.split("_", 1)[1], name=name,
        per100g={"kcal": kcal, "protein": protein, "fat": fat, "carbs": carbs,
                 "fiber": None, "sugar": None, "sodium_mg": None},
        portions=[])


class FakeIndex:
    def __init__(self, refs):
        self._refs = {r.id: r for r in refs}

    def get(self, ref_id):
        return self._refs.get(ref_id)


INDEX = FakeIndex([
    ref("usda_rice", "USDA_FNDDS", "Rice, white, cooked", 130.0, 2.0, 0.4, 28.0),
    ref("usda_milkrec", "USDA_FNDDS", "Milk, dry, reconstituted, whole", 64.0, 3.3, 3.4, 4.9),
    ref("usda_milkdry", "USDA_SR_LEGACY", "Milk, dry, whole", 496.0, 26.3, 26.7, 38.4),
    ref("usda_bread", "USDA_FNDDS", "Bread, white", 270.0, 9.0, 3.0, 50.0),
    ref("usda_avocado", "USDA_SR_LEGACY", "Avocado, raw", 160.0, 2.0, 15.0, 9.0),
    ref("usda_dish", "USDA_FNDDS", "Bread with avocado", 200.0, 5.0, 8.0, 25.0),
    ref("usda_nofat", "USDA_FOUNDATION", "Mystery food", 100.0, 5.0, None, 10.0),
])


def food_draft(mid="m1", span="arroz", ref_id="usda_rice", grams=150, tolerance="HOUSEHOLD", **over):
    draft = {"mid": mid, "span": span, "canonicalEs": "arroz cocido", "aliasesEs": ["arroz"],
             "category": "PASTAS_ARROZ", "state": "COOKED", "form": "NA", "granularity": "FOOD",
             "refId": ref_id, "grams": grams, "tolerance": tolerance, "equivalents": [],
             "ambiguity": [], "optional": False, "rationale": "porcion"}
    draft.update(over)
    return draft


def draft_case(cid, mentions, **over):
    case = {"id": cid, "labeler": "L1", "mentions": mentions, "excluded": [], "notes": ""}
    case.update(over)
    return case


def case_row(cid, text):
    return {"id": cid, "split": "dev", "text": text, "mealType": "LUNCH", "region": "CL",
            "phenomena": ["F01"], "style": "telefono", "origin": "G1-001"}


class GoldBuildTests(unittest.TestCase):
    def build(self, text, mentions, **over):
        cases = [case_row("E0001", text)]
        rows, report = gold_build.build_gold(cases, [draft_case("E0001", mentions, **over)], INDEX)
        return rows, report

    def test_simple_food(self):
        rows, report = self.build("150 g de arroz", [food_draft(tolerance="EXPLICIT")])
        self.assertEqual(report["built"], 1)
        self.assertEqual(report["errorCount"], 0)
        gold = rows[0]
        mention = gold["mentions"][0]
        self.assertEqual(mention["reference"], {"source": "USDA_FNDDS", "code": "rice",
                                                "name": "Rice, white, cooked"})
        self.assertEqual(mention["per100g"], {"kcal": 130.0, "protein": 2.0, "fat": 0.4, "carbs": 28.0})
        self.assertEqual(mention["grams"], {"target": 150.0, "tolerance": "EXPLICIT"})
        self.assertEqual(mention["eaten"], {"kcal": 195.0, "protein": 3.0, "fat": 0.6, "carbs": 42.0})
        self.assertEqual(mention["components"], [])
        self.assertFalse(mention["optional"])
        self.assertEqual(gold["mealEaten"], {"kcal": 195.0})
        self.assertEqual(gold["labeler"], "L1")

    def test_equivalents_and_meal_total(self):
        milk = food_draft(
            mid="m1", span="un vaso de leche en polvo", ref_id="usda_milkrec", grams=220,
            equivalents=[{"refId": "usda_milkdry", "grams": 28.6, "note": "polvo seco"}],
            ambiguity=["FORM"])
        rice = food_draft(mid="m2", span="arroz")
        rows, report = self.build("un vaso de leche en polvo y arroz", [milk, rice])
        mentions = rows[0]["mentions"]
        eq = mentions[0]["equivalents"][0]
        self.assertEqual(eq["source"], "USDA_SR_LEGACY")
        self.assertEqual(eq["code"], "milkdry")
        self.assertEqual(eq["grams"], 28.6)
        self.assertEqual(eq["per100g"]["kcal"], 496.0)
        self.assertEqual(eq["note"], "polvo seco")
        self.assertEqual(mentions[0]["ambiguity"], ["FORM"])
        self.assertEqual(mentions[0]["eaten"]["kcal"], 140.8)
        self.assertAlmostEqual(rows[0]["mealEaten"]["kcal"], 140.8 + 195.0, places=6)

    def test_dish_without_ref_is_sum_of_components(self):
        dish = {"mid": "m1", "span": "pan con palta", "canonicalEs": "pan con palta", "aliasesEs": [],
                "category": "PLATOS", "state": "NA", "form": "NA", "granularity": "DISH",
                "allowSplit": True, "refId": None, "grams": 150, "tolerance": "SUBJECTIVE",
                "components": [
                    {"canonicalEs": "pan", "refId": "usda_bread", "grams": 100, "tolerance": "COUNT"},
                    {"canonicalEs": "palta", "refId": "usda_avocado", "grams": 50, "tolerance": "SUBJECTIVE"}],
                "equivalents": [], "ambiguity": [], "optional": False}
        rows, report = self.build("pan con palta", [dish])
        self.assertEqual(report["errorCount"], 0, report["errors"])
        mention = rows[0]["mentions"][0]
        self.assertTrue(mention["allowSplit"])
        self.assertIsNone(mention["reference"])
        self.assertEqual(mention["eaten"], {"kcal": 350.0, "protein": 10.0, "fat": 10.5, "carbs": 54.5})
        self.assertEqual(mention["per100g"]["kcal"], round(350.0 * 100 / 150, 1))
        self.assertEqual(mention["components"][0]["eaten"]["kcal"], 270.0)
        self.assertEqual(mention["components"][1]["eaten"]["kcal"], 80.0)
        self.assertEqual(mention["components"][0]["grams"], {"target": 100.0, "tolerance": "COUNT"})
        self.assertEqual(rows[0]["mealEaten"]["kcal"], 350.0)

    def test_dish_with_ref_uses_dish_card_and_components_keep_own_eaten(self):
        dish = {"mid": "m1", "span": "pan con palta", "canonicalEs": "pan con palta", "aliasesEs": [],
                "category": "PLATOS", "state": "NA", "form": "NA", "granularity": "DISH",
                "allowSplit": True, "refId": "usda_dish", "grams": 200, "tolerance": "SUBJECTIVE",
                "components": [
                    {"canonicalEs": "pan", "refId": "usda_bread", "grams": 100, "tolerance": "COUNT"},
                    {"canonicalEs": "palta", "refId": "usda_avocado", "grams": 50, "tolerance": "SUBJECTIVE"}],
                "equivalents": [], "ambiguity": [], "optional": False}
        rows, report = self.build("pan con palta", [dish])
        mention = rows[0]["mentions"][0]
        self.assertEqual(mention["reference"]["code"], "dish")
        self.assertEqual(mention["eaten"], {"kcal": 400.0, "protein": 10.0, "fat": 16.0, "carbs": 50.0})
        self.assertEqual(mention["components"][1]["eaten"]["fat"], 7.5)
        self.assertEqual(rows[0]["mealEaten"]["kcal"], 400.0)

    def test_missing_nutrient_stays_none(self):
        rows, report = self.build("mystery", [food_draft(span="mystery", ref_id="usda_nofat", grams=200)])
        eaten = rows[0]["mentions"][0]["eaten"]
        self.assertIsNone(eaten["fat"])
        self.assertEqual(eaten["protein"], 10.0)
        self.assertIsNone(rows[0]["mentions"][0]["per100g"]["fat"])
        dish = {"mid": "m1", "span": "mystery con pan", "canonicalEs": "x", "aliasesEs": [],
                "category": "PLATOS", "state": "NA", "form": "NA", "granularity": "DISH",
                "refId": None, "grams": None, "tolerance": None,
                "components": [
                    {"canonicalEs": "mystery", "refId": "usda_nofat", "grams": 100, "tolerance": "COUNT"},
                    {"canonicalEs": "pan", "refId": "usda_bread", "grams": 50, "tolerance": "SUBJECTIVE"}]}
        rows, report = self.build("mystery con pan", [dish])
        mention = rows[0]["mentions"][0]
        self.assertIsNone(mention["eaten"]["fat"])
        self.assertIsNone(mention["per100g"]["fat"])
        self.assertEqual(mention["eaten"]["kcal"], 235.0)
        self.assertEqual(mention["grams"], {"target": 150.0, "tolerance": "SUBJECTIVE"})

    def test_unknown_ref_is_error_and_case_dropped(self):
        cases = [case_row("E0001", "arroz"), case_row("E0002", "arroz")]
        drafts = [draft_case("E0001", [food_draft(ref_id="usda_nope")]),
                  draft_case("E0002", [food_draft()])]
        rows, report = gold_build.build_gold(cases, drafts, INDEX)
        self.assertEqual([r["id"] for r in rows], ["E0002"])
        self.assertEqual(report["errorCount"], 1)
        self.assertEqual(report["errors"][0]["id"], "E0001")
        self.assertIn("usda_nope", report["errors"][0]["errors"][0])

    def test_unknown_equivalent_ref_is_error(self):
        rows, report = self.build("arroz", [food_draft(equivalents=[{"refId": "usda_nope", "grams": 10}])])
        self.assertEqual(rows, [])
        self.assertEqual(report["errorCount"], 1)

    def test_validation_errors(self):
        bad_cases = {
            "gramos cero": food_draft(grams=0),
            "gramos negativos": food_draft(grams=-5),
            "gramos ausentes": food_draft(grams=None),
            "tolerance": food_draft(tolerance="LOOSE"),
            "state": food_draft(state="BURNT"),
            "form": food_draft(form="SOLID"),
            "category": food_draft(category="COSAS"),
            "granularity": food_draft(granularity="MEAL"),
            "ambiguity": food_draft(ambiguity=["TASTE"]),
            "span vacio": food_draft(span="  "),
        }
        for label, mention in bad_cases.items():
            with self.subTest(label):
                rows, report = self.build("arroz", [mention])
                self.assertEqual(rows, [])
                self.assertEqual(report["errorCount"], 1)

    def test_span_not_in_text_is_only_a_warning(self):
        rows, report = self.build("Pollo con ARROZ", [food_draft(span="fideos")])
        self.assertEqual(report["built"], 1)
        self.assertEqual(report["warningCount"], 1)
        self.assertIn("subcadena", report["warnings"][0]["warning"])
        rows, report = self.build("Pollo con ARRÓZ", [food_draft(span="arroz")])
        self.assertEqual(report["warningCount"], 0)

    def test_descartar(self):
        draft = draft_case("E0001", [], notes="DESCARTAR: no es comida")
        rows, report = gold_build.build_gold([case_row("E0001", "hola")], [draft], INDEX)
        self.assertEqual(rows, [])
        self.assertEqual(report["discardedCount"], 1)
        self.assertEqual(report["errorCount"], 0)
        self.assertIn("no es comida", report["discarded"][0]["reason"])

    def test_excluded_copied_and_missing_draft_reported(self):
        cases = [case_row("E0001", "hamburguesa sin pan"), case_row("E0002", "algo")]
        drafts = [draft_case("E0001", [food_draft(span="hamburguesa")], excluded=["pan"])]
        rows, report = gold_build.build_gold(cases, drafts, INDEX)
        self.assertEqual(rows[0]["excluded"], ["pan"])
        self.assertEqual(report["withoutDraft"], ["E0002"])

    def test_cli_with_injected_index_via_functions(self):
        with tempfile.TemporaryDirectory() as tmp:
            cases_path = Path(tmp) / "all.jsonl"
            write_lines(cases_path, [case_row("E0001", "150 g de arroz")])
            drafts = [draft_case("E0001", [food_draft(tolerance="EXPLICIT")])]
            rows, report = gold_build.build_gold(gold_build.read_jsonl(cases_path), drafts, INDEX)
            out = Path(tmp) / "gold" / "dev.jsonl"
            gold_build.write_jsonl(out, rows)
            self.assertEqual(read_lines(out)[0]["mentions"][0]["eaten"]["kcal"], 195.0)


# =========================================================================== agree

def gold_mention(mid, span, canonical, kcal, protein=5.0, fat=5.0, carbs=20.0, tol="HOUSEHOLD", **over):
    mention = {"mid": mid, "span": span, "canonicalEs": canonical, "aliasesEs": [],
               "granularity": "FOOD", "components": [], "optional": False,
               "grams": {"target": 100.0, "tolerance": tol},
               "eaten": {"kcal": kcal, "protein": protein, "fat": fat, "carbs": carbs}}
    mention.update(over)
    return mention


def gold_case(cid, mentions, excluded=()):
    return {"id": cid, "mentions": mentions, "excluded": list(excluded),
            "mealEaten": {"kcal": sum(m["eaten"]["kcal"] for m in mentions)}}


class AgreeTests(unittest.TestCase):
    def test_tokens_and_pairing(self):
        self.assertEqual(agree.norm_tokens("Los Panes con Palta"), frozenset({"pan", "palta"}))
        a = [gold_mention("m1", "arroz cocido", "arroz", 100.0), gold_mention("m2", "pollo", "pollo", 100.0)]
        b = [gold_mention("m1", "el pollo", "pollo asado", 100.0), gold_mention("m2", "arroz", "arroz", 100.0)]
        self.assertEqual([(i, j) for i, j, _ in agree.pair_mentions(a, b)], [(0, 1), (1, 0)])

    def test_agreement(self):
        a = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0),
                             gold_mention("m2", "pollo", "pollo", 150.0, protein=30.0)])
        b = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 215.0, protein=5.5),
                             gold_mention("m2", "pollo", "pollo", 140.0, protein=31.0)])
        agreed, disagree, report = agree.agree([a], [b], {"E1": "arroz con pollo"})
        self.assertEqual(agreed, [a])
        self.assertEqual(disagree, [])
        self.assertEqual(report["caseAgreementRate"], 1.0)
        self.assertEqual(report["mentionAgreementRate"], 1.0)
        self.assertAlmostEqual(report["meanAbsKcalDiff"], 12.5)

    def test_disagreement_by_kcal(self):
        a = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0)])
        b = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 260.0)])
        agreed, disagree, report = agree.agree([a], [b], {"E1": "arroz"})
        self.assertEqual(agreed, [])
        self.assertEqual(len(disagree), 1)
        line = disagree[0]
        self.assertEqual((line["id"], line["text"], line["a"], line["b"]), ("E1", "arroz", a, b))
        self.assertTrue(any("kcal" in reason for reason in line["reasons"]))
        self.assertEqual(report["caseAgreementRate"], 0.0)
        self.assertEqual(report["reasonCounts"]["kcal"], 1)

    def test_kcal_floor_and_relative_limit(self):
        near = agree.compare_mentions(gold_mention("m", "x", "x", 40.0), gold_mention("m", "x", "x", 59.0))
        self.assertEqual(near, [])  # 19 kcal <= piso 20
        far = agree.compare_mentions(gold_mention("m", "x", "x", 500.0), gold_mention("m", "x", "x", 400.0))
        self.assertEqual([k for k, _ in far], ["kcal"])  # 100 > 15 % de 500

    def test_macro_uses_looser_class(self):
        a = gold_mention("m", "x", "x", 100.0, protein=10.0, tol="EXPLICIT")
        b_strict = gold_mention("m", "x", "x", 100.0, protein=13.0, tol="EXPLICIT")
        b_loose = gold_mention("m", "x", "x", 100.0, protein=13.0, tol="SUBJECTIVE")
        self.assertEqual([k for k, _ in agree.compare_mentions(a, b_strict)], ["protein"])
        self.assertEqual(agree.compare_mentions(a, b_loose), [])

    def test_disagreement_by_missing_mention(self):
        a = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0),
                             gold_mention("m2", "pollo", "pollo", 150.0)])
        b = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0)])
        agreed, disagree, report = agree.agree([a], [b])
        self.assertEqual(agreed, [])
        self.assertIn("faltaEnB", report["reasonCounts"])
        self.assertIn("pollo", disagree[0]["reasons"][0])
        self.assertEqual(report["mentionAgreementRate"], 0.5)

    def test_optional_mentions_do_not_block(self):
        a = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0),
                             gold_mention("m2", "sal", "sal", 0.0, optional=True)])
        b = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0)])
        agreed, disagree, _ = agree.agree([a], [b])
        self.assertEqual(len(agreed), 1)
        self.assertEqual(disagree, [])

    def test_disagreement_by_excluded(self):
        a = gold_case("E1", [gold_mention("m1", "hamburguesa", "hamburguesa", 300.0)], excluded=["pan"])
        b = gold_case("E1", [gold_mention("m1", "hamburguesa", "hamburguesa", 300.0)], excluded=[])
        agreed, disagree, report = agree.agree([a], [b])
        self.assertEqual(agreed, [])
        self.assertEqual(report["reasonCounts"], {"excluded": 1})
        # mismos excluidos con distinta redaccion: concuerdan
        b2 = gold_case("E1", [gold_mention("m1", "hamburguesa", "hamburguesa", 300.0)], excluded=["sin Pan"])
        agreed, disagree, _ = agree.agree([a], [b2])
        self.assertEqual(len(agreed), 1)

    def test_case_present_on_one_side_only(self):
        a = gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0)])
        agreed, disagree, report = agree.agree([a], [])
        self.assertEqual(agreed, [])
        self.assertEqual(disagree[0]["b"], None)
        self.assertEqual(report["reasonCounts"], {"casoAusente": 1})

    def test_cli_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = [gold_case("E1", [gold_mention("m1", "arroz", "arroz", 200.0)]),
                 gold_case("E2", [gold_mention("m1", "pan", "pan", 100.0)])]
            b = [gold_case("E1", [gold_mention("m1", "arroz", "arroz", 205.0)]),
                 gold_case("E2", [gold_mention("m1", "pan", "pan", 300.0)])]
            write_lines(Path(tmp) / "a.jsonl", a)
            write_lines(Path(tmp) / "b.jsonl", b)
            write_lines(Path(tmp) / "cases.jsonl", [case_row("E1", "arroz"), case_row("E2", "pan")])
            code = agree.main(["--a", str(Path(tmp) / "a.jsonl"), "--b", str(Path(tmp) / "b.jsonl"),
                               "--out-agreed", str(Path(tmp) / "gold.jsonl"),
                               "--out-disagree", str(Path(tmp) / "dis.jsonl"),
                               "--report", str(Path(tmp) / "rep.json"),
                               "--cases", str(Path(tmp) / "cases.jsonl")])
            self.assertEqual(code, 0)
            self.assertEqual([c["id"] for c in read_lines(Path(tmp) / "gold.jsonl")], ["E1"])
            dis = read_lines(Path(tmp) / "dis.jsonl")
            self.assertEqual([(d["id"], d["text"]) for d in dis], [("E2", "pan")])
            report = json.loads((Path(tmp) / "rep.json").read_text(encoding="utf-8"))
            self.assertEqual(report["caseAgreementRate"], 0.5)


# =========================================================================== integracion

def _real_cache_available():
    return REAL_CACHE_DIR.is_dir() and any(REAL_CACHE_DIR.glob("refindex-*.pkl"))


@unittest.skipUnless(_real_cache_available(), "no hay cache de refindex en C:\\kw\\nutri-examen\\cache")
class RealIndexIntegrationTest(unittest.TestCase):
    def test_gold_for_minimal_draft_with_real_ids(self):
        import refindex
        index = refindex.load_index(cache_dir=REAL_CACHE_DIR)
        hits = index.search("rice white cooked", k=3, sources=["USDA_FNDDS"]) or index.search(
            "rice white cooked", k=3)
        self.assertTrue(hits)
        real = hits[0][1]
        draft = draft_case("E0001", [food_draft(span="arroz", ref_id=real.id, grams=150)])
        rows, report = gold_build.build_gold([case_row("E0001", "150 g de arroz")], [draft], index)
        self.assertEqual(report["errorCount"], 0, report["errors"])
        mention = rows[0]["mentions"][0]
        self.assertEqual(mention["reference"]["code"], real.code)
        self.assertEqual(mention["reference"]["source"], real.source)
        self.assertEqual(mention["eaten"], refindex.eaten(real, 150))
        self.assertEqual(rows[0]["mealEaten"]["kcal"], mention["eaten"]["kcal"])


# =========================================================================== curate: anexo modulo B

def spec_b(targets, enums=None, dev_share=0.4):
    spec = spec_for({"F01": 100})
    spec["moduloB"] = {"dev_share": dev_share,
                       "groups": {g: {"target": t} for g, t in targets.items()}}
    if enums is not None:
        spec["enums"] = enums
    return spec


def synthetic_b_raw(raw_dir, per_group, seed=11, lang_of=None, region_of=None, extra=None):
    """B<g>.jsonl con textos al azar; mitad de las lineas en la primera region."""
    rng = random.Random(seed)
    for group, count in per_group.items():
        rows = []
        for k in range(count):
            region = (region_of or {}).get(group, ("CL", "AR"))[k % 2]
            rows.append({
                "tmpId": "%s-%03d" % (group, k + 1),
                "text": "%s %s %s" % (random_word(rng), random_word(rng), random_word(rng)),
                "mealType": curate.MEAL_TYPES[k % 4], "region": region, "style": "telefono",
                "persona": "x", "phenomena": ["F01"],
                "lang": (lang_of or {}).get(group, "es"), "group": group,
            })
        if extra and group in extra:
            rows.extend(extra[group])
        write_lines(Path(raw_dir) / ("%s.jsonl" % group), rows)


def build_module_a(tmp, per_code=None, targets=None):
    """Genera un modulo A (cases/, work/) en <tmp>/out y devuelve (out, ids_textos)."""
    raw_a = Path(tmp) / "rawA"
    synthetic_raw(raw_a, per_code or {"F01": 30, "F02": 30}, seed=3)
    out = Path(tmp) / "out"
    result = curate.curate(str(raw_a), spec_for(targets or {"F01": 10, "F02": 10}))
    curate.write_outputs(result, str(out))
    return out, result


def read_bytes(path):
    return Path(path).read_bytes()


class AppendModuleBTests(unittest.TestCase):
    def test_normal_mode_ignores_b_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw_plain = Path(tmp) / "plain"
            raw_mixed = Path(tmp) / "mixed"
            for raw in (raw_plain, raw_mixed):
                synthetic_raw(raw, {"F01": 30, "F02": 30}, seed=3)
                for code in ("F01", "F02"):
                    os.rename(str(raw / ("%s.jsonl" % code)), str(raw / ("G-%s.jsonl" % code)))
            synthetic_b_raw(raw_mixed, {"B1": 15, "B2": 15})
            spec = spec_for({"F01": 10, "F02": 10})
            plain = curate.curate(str(raw_plain), spec, raw_glob="G*.jsonl")
            mixed = curate.curate(str(raw_mixed), spec, raw_glob="G*.jsonl")
            self.assertEqual(plain["cases"], mixed["cases"])
            self.assertEqual(plain["reserve"], mixed["reserve"])
            self.assertEqual(plain["report"]["discards"], mixed["report"]["discards"])
            self.assertEqual(len(plain["cases"]), 20)
            # el CLI usa G*.jsonl por defecto
            spec_path = Path(tmp) / "matriz.json"
            spec_path.write_text(json.dumps(spec), encoding="utf-8")
            out_a = Path(tmp) / "outA"
            out_b = Path(tmp) / "outB"
            self.assertEqual(curate.main(["--raw", str(raw_plain), "--spec", str(spec_path),
                                          "--out", str(out_a)]), 0)
            self.assertEqual(curate.main(["--raw", str(raw_mixed), "--spec", str(spec_path),
                                          "--out", str(out_b)]), 0)
            self.assertEqual(read_bytes(out_a / "cases" / "all.jsonl"), read_bytes(out_b / "cases" / "all.jsonl"))

    def test_append_keeps_previous_lines_and_adds_at_end(self):
        with tempfile.TemporaryDirectory() as tmp:
            out, _ = build_module_a(tmp)
            before = {name: read_bytes(out / "cases" / ("%s.jsonl" % name)) for name in ("all", "dev", "test")}
            raw_b = Path(tmp) / "rawB"
            synthetic_b_raw(raw_b, {"B1": 20, "B2": 15})
            spec_path = Path(tmp) / "matriz.json"
            spec_path.write_text(json.dumps(spec_b({"B1": 10, "B2": 5})), encoding="utf-8")
            self.assertEqual(curate.main(["--raw", str(raw_b), "--spec", str(spec_path), "--out", str(out),
                                          "--raw-glob", "B*.jsonl", "--append-module", "B"]), 0)
            for name, old in before.items():
                new = read_bytes(out / "cases" / ("%s.jsonl" % name))
                self.assertTrue(new.startswith(old), name)
                self.assertGreater(len(new), len(old), name)
            all_cases = read_lines(out / "cases" / "all.jsonl")
            added = all_cases[20:]
            self.assertEqual(len(all_cases), 35)
            self.assertEqual([c["group"] for c in added], ["B1"] * 10 + ["B2"] * 5)
            dev_added = read_lines(out / "cases" / "dev.jsonl")[8:]
            test_added = read_lines(out / "cases" / "test.jsonl")[12:]
            self.assertEqual({c["id"] for c in dev_added} | {c["id"] for c in test_added},
                             {c["id"] for c in added})
            self.assertEqual(len(read_lines(out / "work" / "reserve-B.jsonl")), 20)
            report = json.loads((out / "work" / "curation-report-B.json").read_text(encoding="utf-8"))
            self.assertEqual(report["total"], 15)
            self.assertEqual(report["byLang"], {"es": 15})
            self.assertEqual(report["byGroup"]["B1"]["selected"], 10)
            self.assertEqual(report["split"], {"dev": 6, "test": 9})
            # misma entrada y semilla: mismo resultado
            out2, _ = build_module_a(Path(tmp) / "again")
            self.assertEqual(curate.main(["--raw", str(raw_b), "--spec", str(spec_path), "--out", str(out2),
                                          "--raw-glob", "B*.jsonl", "--append-module", "B"]), 0)
            self.assertEqual(read_bytes(out / "cases" / "all.jsonl"), read_bytes(out2 / "cases" / "all.jsonl"))

    def test_append_requires_existing_all(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw_b = Path(tmp) / "rawB"
            synthetic_b_raw(raw_b, {"B1": 5})
            with self.assertRaises(SystemExit):
                curate.append_module(str(raw_b), spec_b({"B1": 3}), str(Path(tmp) / "vacio"))

    def test_ids_continue_from_maximum(self):
        with tempfile.TemporaryDirectory() as tmp:
            out, result = build_module_a(tmp)
            self.assertEqual(result["cases"][-1]["id"], "E0020")
            # un hueco en el medio no importa: se continua desde el mayor
            rows = read_lines(out / "cases" / "all.jsonl")
            rows[-1]["id"] = "E0099"
            write_lines(out / "cases" / "all.jsonl", rows)
            raw_b = Path(tmp) / "rawB"
            synthetic_b_raw(raw_b, {"B2": 8, "B1": 8})
            appended = curate.append_module(str(raw_b), spec_b({"B1": 4, "B2": 4}), str(out))
            ids = [c["id"] for c in appended["cases"]]
            self.assertEqual(ids, ["E%04d" % n for n in range(100, 108)])
            self.assertEqual([c["group"] for c in appended["cases"]], ["B1"] * 4 + ["B2"] * 4)
            origins = [c["origin"] for c in appended["cases"][:4]]
            self.assertEqual(origins, sorted(origins, key=curate.natural_key))

    def test_duplicate_of_module_a_is_discarded_with_reason(self):
        with tempfile.TemporaryDirectory() as tmp:
            out, result = build_module_a(tmp)
            a_case = result["cases"][3]
            raw_b = Path(tmp) / "rawB"
            row = {"tmpId": "B1-900", "text": a_case["text"].upper(), "mealType": "LUNCH", "region": "CL",
                   "style": "telefono", "persona": "x", "phenomena": ["F01"], "lang": "es", "group": "B1"}
            near = dict(row, tmpId="B1-901", text=a_case["text"] + "s")
            synthetic_b_raw(raw_b, {"B1": 6}, extra={"B1": [row, near]})
            appended = curate.append_module(str(raw_b), spec_b({"B1": 6}), str(out))
            reasons = {d["tmpId"]: d["reason"] for d in appended["report"]["discards"]}
            self.assertEqual(reasons["B1-900"], "duplicado exacto de %s" % a_case["id"])
            self.assertEqual(reasons["B1-901"], "casi duplicado de %s" % a_case["id"])
            self.assertNotIn(a_case["text"].upper(), [c["text"] for c in appended["cases"]])
            self.assertEqual(appended["report"]["discardReasons"], {"casi duplicado": 1, "duplicado exacto": 1})

    def test_split_40_60_per_group(self):
        with tempfile.TemporaryDirectory() as tmp:
            out, _ = build_module_a(tmp)
            raw_b = Path(tmp) / "rawB"
            synthetic_b_raw(raw_b, {"B1": 40, "B3": 60, "B5": 55})
            appended = curate.append_module(str(raw_b), spec_b({"B1": 30, "B3": 50, "B5": 20}), str(out))
            by_group = appended["report"]["byGroup"]
            self.assertEqual((by_group["B1"]["dev"], by_group["B1"]["test"]), (12, 18))
            self.assertEqual((by_group["B3"]["dev"], by_group["B3"]["test"]), (20, 30))
            self.assertEqual((by_group["B5"]["dev"], by_group["B5"]["test"]), (8, 12))
            self.assertEqual(appended["report"]["split"], {"dev": 40, "test": 60})
            dev_b1 = [c for c in appended["cases"] if c["group"] == "B1" and c["split"] == "dev"]
            self.assertTrue(any(c["region"] == "AR" for c in dev_b1))  # estratificado por region
            self.assertEqual(len(appended["reserve"]), 10 + 10 + 35)

    def test_matrix_enums_extend_validation(self):
        enums = {"regions": ["CL", "VE", "US"], "styles": ["telefono", "extenso"],
                 "langs": ["es", "en", "mix"], "phenomena": ["F01", "F17", "F18"]}
        base = {"tmpId": "B1-001", "text": "two eggs and toast", "mealType": "BREAKFAST", "region": "VE",
                "style": "extenso", "phenomena": ["F18"], "lang": "en", "group": "B1"}
        record, reason = curate.validate(base, enums)
        self.assertIsNone(reason)
        self.assertEqual((record["region"], record["style"], record["lang"], record["phenomena"]),
                         ("VE", "extenso", "en", ["F18"]))
        # sin enums de la matriz rige la lista actual
        self.assertIn("region", curate.validate(base)[1])
        for field, bad in (("region", "AR"), ("style", "formal"), ("lang", "fr"), ("phenomena", ["F02"])):
            with self.subTest(field):
                _, why = curate.validate(dict(base, **{field: bad}), enums)
                self.assertIsNotNone(why)
        _, why = curate.validate(dict(base, group=7), enums)
        self.assertIn("group", why)
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp) / "raw"
            write_lines(raw / "B1.jsonl", [base, dict(base, tmpId="B1-002", text="otro huevo", region="AR")])
            records, discards = curate.load_raw(str(raw), enums, "B*.jsonl")
            self.assertEqual([r["tmpId"] for r in records], ["B1-001"])
            self.assertIn("region", discards[0]["reason"])

    def test_lang_defaults_to_es_and_group_to_none(self):
        item = {"tmpId": "G1-001", "text": "pan con palta", "mealType": "LUNCH", "region": "CL",
                "style": "telefono", "phenomena": ["F01"]}
        record, reason = curate.validate(item)
        self.assertIsNone(reason)
        self.assertEqual(record["lang"], "es")
        self.assertIsNone(record["group"])
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp) / "raw"
            rows = [dict(item, tmpId="G1-%03d" % n, text="comida %s" % random_word(random.Random(n)))
                    for n in range(1, 6)]
            write_lines(raw / "G1.jsonl", rows)
            result = curate.curate(str(raw), spec_for({"F01": 5}), raw_glob="G*.jsonl")
            self.assertTrue(all(c["lang"] == "es" and c["group"] is None for c in result["cases"]))
            self.assertEqual(list(result["cases"][0].keys())[-2:], ["lang", "group"])


if __name__ == "__main__":
    unittest.main()
