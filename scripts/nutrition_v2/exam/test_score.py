"""Pruebas del puntuador del examen de nutricion v2 (fixtures sinteticos en carpeta temporal)."""
import contextlib
import glob
import io
import json
import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import score  # noqa: E402


# ---------------------------------------------------------------------------
# Constructores de fixtures
# ---------------------------------------------------------------------------

def eaten(kcal, protein=0.0, fat=0.0, carbs=0.0):
    return {"kcal": kcal, "protein": protein, "fat": fat, "carbs": carbs}


def gold_food(span, canonical, kcal, protein=0.0, fat=0.0, carbs=0.0, tol="EXPLICIT",
              grams=100.0, per100g=None, equivalents=None, ambiguity=None, optional=False,
              aliases=None):
    m = {"mid": "m", "span": span, "canonicalEs": canonical, "aliasesEs": aliases or [],
         "granularity": "FOOD", "components": [],
         "grams": {"target": grams, "tolerance": tol},
         "eaten": eaten(kcal, protein, fat, carbs),
         "equivalents": equivalents or [], "ambiguity": ambiguity or []}
    if per100g is not None:
        m["per100g"] = per100g
    if optional:
        m["optional"] = True
    return m


def pred_m(text, kcal, protein=0.0, fat=0.0, carbs=0.0, grams=100.0, heuristic=False,
           auto=False, questions=None, per100g=None, excluded=False, food_name=None,
           nutrients=None):
    return {"text": text, "foodName": food_name or text, "heuristic": heuristic,
            "excluded": excluded, "grams": grams, "eaten": eaten(kcal, protein, fat, carbs),
            "per100g": per100g or {}, "autoAccepted": auto, "questions": questions or [],
            "nutrientsPresent": nutrients or []}


def case(cid, text="x", split="dev", phenomena=None):
    return {"id": cid, "split": split, "text": text, "phenomena": phenomena or []}


def gold_rec(cid, mentions, excluded=None, meal_kcal=None):
    g = {"id": cid, "mentions": mentions, "excluded": excluded or []}
    if meal_kcal is not None:
        g["mealEaten"] = {"kcal": meal_kcal}
    return g


def pred_rec(cid, mentions, system="sysA", latency=10.0, error=None):
    return {"id": cid, "system": system, "latencyMs": latency, "error": error, "mentions": mentions}


def ev(mentions_gold, mentions_pred, excluded=None, meal_kcal=None):
    """Evalua un caso y devuelve el resultado (diccionario de score.evaluate_case)."""
    return score.evaluate_case(case("E0001"),
                               gold_rec("E0001", mentions_gold, excluded, meal_kcal),
                               pred_rec("E0001", mentions_pred))


def write_jsonl(path, rows):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        for r in rows:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")


# ---------------------------------------------------------------------------
# Normalizacion y emparejamiento
# ---------------------------------------------------------------------------

class NormalizationTests(unittest.TestCase):
    def test_tildes_stopwords_singular(self):
        self.assertEqual(score.norm_tokens("Los Plátanos de Ñuñoa"), frozenset(["platano", "nunoa"]))
        self.assertEqual(score.norm_tokens("panes con palta"), frozenset(["pan", "palta"]))

    def test_singular_es_solo_tras_consonante_plural(self):
        self.assertEqual(score.norm_tokens("tomates"), score.norm_tokens("tomate"))
        self.assertEqual(score.norm_tokens("limones"), frozenset(["limon"]))
        self.assertEqual(score.norm_tokens("frijoles"), frozenset(["frijol"]))

    def test_similarity_overlap(self):
        a = score.token_sets(["leche en polvo"])
        b = score.token_sets(["leche descremada"])
        self.assertAlmostEqual(score.similarity(a, b), 0.5)
        self.assertEqual(score.similarity(a, score.token_sets(["zapallo"])), 0.0)


class MatchingTests(unittest.TestCase):
    def test_simple_match_is_correct(self):
        r = ev([gold_food("un huevo cocido", "huevo cocido", 155, 13, 11, 1.1)],
               [pred_m("huevo", 155, 13, 11, 1.1)])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m3_den"], c["m5_num"], c["m5_den"]), (1, 1, 1, 1))
        self.assertEqual(c["extras"], 0)
        self.assertEqual(r["failures"], [])

    def test_unmatched_gold_is_omission_and_pred_is_extra(self):
        r = ev([gold_food("arroz", "arroz", 130, 2.7, 0.3, 28)],
               [pred_m("zanahoria", 41, 0.9, 0.2, 10)])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m3_den"], c["omissions"], c["extras"]), (0, 1, 1, 1))
        self.assertEqual(len(r["failures"]), 1)

    def test_greedy_one_to_one(self):
        r = ev([gold_food("pan", "pan", 265, 9, 3, 49), gold_food("palta", "palta", 160, 2, 15, 9)],
               [pred_m("palta", 160, 2, 15, 9), pred_m("pan", 265, 9, 3, 49)])
        self.assertEqual((r["counters"]["m3_num"], r["counters"]["m3_den"]), (2, 2))

    def test_dish_allow_split_matched_by_components(self):
        dish = {"mid": "m2", "span": "2 marraquetas con palta", "canonicalEs": "sandwich nordico",
                "granularity": "DISH", "allowSplit": True,
                "components": [
                    {"canonicalEs": "marraqueta", "grams": {"target": 200, "tolerance": "COUNT"},
                     "eaten": eaten(552, 17, 2.4, 114)},
                    {"canonicalEs": "palta", "grams": {"target": 80, "tolerance": "SUBJECTIVE"},
                     "eaten": eaten(128, 1.6, 11.7, 6.8)}],
                "eaten": eaten(680, 18.6, 14.1, 120.8), "ambiguity": []}
        r = ev([dish], [pred_m("marraqueta", 540, 17, 2.5, 112), pred_m("palta", 130, 1.6, 12, 7)])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m3_den"], c["extras"]), (1, 1, 0))

    def test_dish_whole_match_when_single_prediction_covers_dish(self):
        dish = {"mid": "m2", "span": "pan con palta", "canonicalEs": "pan con palta",
                "granularity": "DISH", "allowSplit": True,
                "components": [
                    {"canonicalEs": "marraqueta", "grams": {"target": 100, "tolerance": "COUNT"}},
                    {"canonicalEs": "palta", "grams": {"target": 80, "tolerance": "SUBJECTIVE"}}],
                "eaten": eaten(400, 10, 14, 60), "ambiguity": []}
        r = ev([dish], [pred_m("pan con palta", 410, 10, 14, 61)])
        self.assertEqual((r["counters"]["m3_num"], r["counters"]["extras"]), (1, 0))

    def test_excluded_prediction_ignored_and_excluded_item_is_extra(self):
        r = ev([gold_food("palta", "palta", 160, 2, 15, 9)],
               [pred_m("palta", 160, 2, 15, 9), pred_m("pan", 265, 9, 3, 49),
                pred_m("tomate", 20, 1, 0, 4, excluded=True)],
               excluded=["pan"])
        c = r["counters"]
        self.assertEqual(c["extras"], 1)          # pan (excluido por el usuario), no el tomate excluded=true
        self.assertEqual(c["excluded_hits"], 1)
        self.assertEqual(c["m3_num"], 1)

    def test_optional_unmatched_is_not_omission_and_matched_is_scored(self):
        sal = gold_food("sal", "sal", 0, optional=True)
        r1 = ev([gold_food("arroz", "arroz", 130, 2.7, 0.3, 28), sal],
                [pred_m("arroz", 130, 2.7, 0.3, 28)])
        self.assertEqual((r1["counters"]["m3_den"], r1["counters"]["m5_den"], r1["counters"]["omissions"]), (1, 1, 0))
        r2 = ev([gold_food("arroz", "arroz", 130, 2.7, 0.3, 28), sal],
                [pred_m("arroz", 130, 2.7, 0.3, 28), pred_m("sal", 0)])
        self.assertEqual((r2["counters"]["m3_den"], r2["counters"]["m3_num"], r2["counters"]["m5_den"]), (2, 2, 1))

    def test_heuristic_not_m3_nor_m5(self):
        r = ev([gold_food("arroz", "arroz", 130, 2.7, 0.3, 28)],
               [pred_m("arroz", 130, 2.7, 0.3, 28, heuristic=True)])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m5_num"], c["m3_den"]), (0, 0, 1))
        self.assertIn("heuristica", r["failures"][0]["reason"])

    def test_null_gold_nutrient_not_evaluated(self):
        g = gold_food("arroz", "arroz", 130, 2.7, 0.3, 28)
        g["eaten"]["fat"] = None
        r = ev([g], [pred_m("arroz", 130, 2.7, 99, 28)])
        self.assertEqual(r["counters"]["m3_num"], 1)


# ---------------------------------------------------------------------------
# Tolerancias
# ---------------------------------------------------------------------------

class ToleranceTests(unittest.TestCase):
    def check(self, cls, gold_kcal, pred_kcal_in, pred_kcal_out):
        ge = eaten(gold_kcal, 20, 10, 30)
        self.assertEqual(score.nutrients_failed(eaten(pred_kcal_in, 20, 10, 30), ge, cls), [])
        self.assertEqual(score.nutrients_failed(eaten(pred_kcal_out, 20, 10, 30), ge, cls), ["kcal"])

    def test_explicit(self):
        self.check("EXPLICIT", 200, 219.9, 221)    # 10 % de 200 = 20 (> piso 15)

    def test_explicit_floor(self):
        ge = eaten(100, 10, 5, 10)                 # 10 % = 10 < piso 15
        self.assertEqual(score.nutrients_failed(eaten(114, 10, 5, 10), ge, "EXPLICIT"), [])
        self.assertEqual(score.nutrients_failed(eaten(116, 10, 5, 10), ge, "EXPLICIT"), ["kcal"])

    def test_container(self):
        self.check("CONTAINER", 400, 459, 461)     # 15 % de 400 = 60

    def test_count(self):
        self.check("COUNT", 400, 499, 501)         # 25 % = 100

    def test_household_same_as_count(self):
        self.check("HOUSEHOLD", 400, 499, 501)

    def test_subjective(self):
        self.check("SUBJECTIVE", 400, 559, 561)    # 40 % = 160

    def test_macro_floor_and_relative(self):
        ge = eaten(500, 1.0, 50, 30)
        # proteina: 25 % de 1 g = 0,25 pero piso 3 g (COUNT)
        self.assertEqual(score.nutrients_failed(eaten(500, 3.9, 50, 30), ge, "COUNT"), [])
        self.assertEqual(score.nutrients_failed(eaten(500, 4.1, 50, 30), ge, "COUNT"), ["protein"])
        # grasa: 25 % de 50 g = 12,5
        self.assertEqual(score.nutrients_failed(eaten(500, 1, 62, 30), ge, "COUNT"), [])
        self.assertEqual(score.nutrients_failed(eaten(500, 1, 63, 30), ge, "COUNT"), ["fat"])

    def test_grams_tolerance_by_class(self):
        for cls, pct in (("EXPLICIT", 0.02), ("CONTAINER", 0.10), ("COUNT", 0.20),
                         ("HOUSEHOLD", 0.20), ("SUBJECTIVE", 0.35)):
            g = {"target": 100.0, "tolerance": cls}
            self.assertTrue(score.grams_ok(100 * (1 + pct) - 0.01, g, cls), cls)
            self.assertFalse(score.grams_ok(100 * (1 + pct) + 0.5, g, cls), cls)
        self.assertIsNone(score.grams_ok(100, {"tolerance": "COUNT"}, "COUNT"))


# ---------------------------------------------------------------------------
# M1, M2, M4, M6, M7, M8
# ---------------------------------------------------------------------------

class MetricTests(unittest.TestCase):
    PER = {"kcal": 64.0, "protein": 3.3, "fat": 3.4, "carbs": 4.9}
    DRY = {"kcal": 496.0, "protein": 26.3, "fat": 26.7, "carbs": 38.4}

    def milk(self, **kw):
        return gold_food("un vaso de leche en polvo", "leche en polvo entera", 128, 6.6, 6.8, 9.8,
                         tol="HOUSEHOLD", grams=200.0, per100g=self.PER,
                         equivalents=[{"source": "SR", "code": "1", "grams": 26.0, "per100g": self.DRY}],
                         **kw)

    def test_m1_main_reference_and_m2(self):
        r = ev([self.milk()], [pred_m("leche en polvo", 128, 6.6, 6.8, 9.8, grams=200.0, per100g=dict(self.PER))])
        c = r["counters"]
        self.assertEqual((c["m1_num"], c["m1_den"], c["m2_num"], c["m2_den"]), (1, 1, 1, 1))

    def test_m1_by_equivalence_measures_m2_against_equivalent_grams(self):
        r = ev([self.milk()], [pred_m("leche en polvo", 129, 6.8, 6.9, 9.9, grams=26.0, per100g=dict(self.DRY))])
        c = r["counters"]
        self.assertEqual((c["m1_num"], c["m1_den"]), (1, 1))
        self.assertEqual((c["m2_num"], c["m2_den"]), (1, 1))   # 26 g frente a los 26 g del equivalente
        self.assertEqual(c["m3_num"], 1)                       # los nutrientes consumidos coinciden
        self.assertEqual(c["m3_via_equiv"], 0)                 # aceptada por la principal, no por el equivalente

    def test_m1_fails_when_per100g_far(self):
        r = ev([self.milk()], [pred_m("leche en polvo", 128, 6.6, 6.8, 9.8, per100g={"kcal": 150.0, "protein": 3, "fat": 8, "carbs": 5})])
        self.assertEqual((r["counters"]["m1_num"], r["counters"]["m1_den"]), (0, 1))

    def test_m2_gramos_fuera(self):
        r = ev([self.milk()], [pred_m("leche en polvo", 128, 6.6, 6.8, 9.8, grams=300.0, per100g=dict(self.PER))])
        self.assertEqual((r["counters"]["m2_num"], r["counters"]["m2_den"]), (0, 1))

    def test_m4_silent_error(self):
        golds = [gold_food("arroz", "arroz", 130, 2.7, 0.3, 28),
                 gold_food("palta", "palta", 160, 2, 15, 9),
                 gold_food("pan", "pan", 265, 9, 3, 49)]
        preds = [pred_m("arroz", 135, 2.7, 0.3, 28, auto=True),       # bien
                 pred_m("palta", 400, 2, 15, 9, auto=True),           # +150 % -> silencioso
                 pred_m("pan", 265, 9, 3, 49, auto=True, heuristic=True),  # heuristica -> silencioso
                 pred_m("kiwi", 50, auto=True),                        # sin pareja -> silencioso
                 pred_m("queso", 50, auto=False)]                      # no autoaceptado: fuera del denominador
        c = ev(golds, preds)["counters"]
        self.assertEqual((c["m4_num"], c["m4_den"]), (3, 4))

    def test_m4_kcal_floor_40(self):
        g = gold_food("te", "te", 20)       # 50 % = 10 < piso 40
        c = ev([g], [pred_m("te", 55, auto=True)])["counters"]    # diff 35 <= 40
        self.assertEqual(c["m4_num"], 0)
        c = ev([g], [pred_m("te", 65, auto=True)])["counters"]    # diff 45 > 40
        self.assertEqual(c["m4_num"], 1)

    def test_m6_oil_counts_as_state(self):
        g = gold_food("pollo", "pollo", 200, 20, 10, 0, ambiguity=["STATE"])
        c = ev([g], [pred_m("pollo", 200, 20, 10, 0, questions=["OIL"])])["counters"]
        self.assertEqual((c["m6_asked"], c["m6_tp"], c["m6_just"], c["questions"]), (1, 1, 1, 1))

    def test_m6_cut_counts_as_identity_and_unjustified_question(self):
        g = gold_food("carne", "carne", 200, 20, 10, 0)           # sin ambiguedad
        c = ev([g], [pred_m("carne", 200, 20, 10, 0, questions=["CUT", "AMOUNT"])])["counters"]
        self.assertEqual((c["m6_asked"], c["m6_tp"], c["m6_just"], c["questions"]), (1, 0, 0, 2))

    def test_m6_recall_counts_unasked(self):
        g = gold_food("pollo", "pollo", 200, 20, 10, 0, ambiguity=["FORM"])
        c = ev([g], [pred_m("pollo", 200, 20, 10, 0)])["counters"]
        self.assertEqual((c["m6_asked"], c["m6_tp"], c["m6_just"]), (0, 0, 1))

    def test_m7_meal_within_20_percent(self):
        g = [gold_food("arroz", "arroz", 500, 10, 1, 100)]
        ok = ev(g, [pred_m("arroz", 590, 10, 1, 100)], meal_kcal=500)["counters"]
        bad = ev(g, [pred_m("arroz", 610, 10, 1, 100)], meal_kcal=500)["counters"]
        self.assertEqual((ok["m7_num"], ok["m7_den"]), (1, 1))
        self.assertEqual((bad["m7_num"], bad["m7_den"]), (0, 1))

    def test_m7_ignores_excluded_predictions_and_floor(self):
        g = [gold_food("te", "te", 30)]
        c = ev(g, [pred_m("te", 60), pred_m("pan", 300, excluded=True)], meal_kcal=30)["counters"]
        self.assertEqual(c["m7_num"], 1)       # |60-30| = 30 <= piso 40

    def test_m8_core_and_median(self):
        r = ev([gold_food("arroz", "arroz", 130, 2.7, 0.3, 28)],
               [pred_m("arroz", 130, 2.7, 0.3, 28, nutrients=["fiber", "sugar", "sodium", "potassium", "iron"])])
        self.assertEqual((r["counters"]["m8_num"], r["counters"]["m8_den"]), (1, 1))
        self.assertEqual(r["nutr_counts"], [5])
        r2 = ev([gold_food("arroz", "arroz", 130, 2.7, 0.3, 28)],
                [pred_m("arroz", 130, 2.7, 0.3, 28, nutrients=["fiber", "sugar"])])
        self.assertEqual(r2["counters"]["m8_num"], 0)

    def test_error_and_missing_cases(self):
        c = case("E0001")
        g = gold_rec("E0001", [gold_food("arroz", "arroz", 130, 2.7, 0.3, 28)], meal_kcal=130)
        r1 = score.evaluate_case(c, g, pred_rec("E0001", [], error="timeout"))
        self.assertEqual((r1["counters"]["errors"], r1["counters"]["missing"], r1["counters"]["m3_den"], r1["counters"]["m3_num"]), (1, 0, 1, 0))
        r2 = score.evaluate_case(c, g, None)
        self.assertEqual((r2["counters"]["errors"], r2["counters"]["missing"]), (1, 1))
        self.assertEqual((r2["counters"]["m7_den"], r2["counters"]["m7_num"]), (1, 0))


class EquivalentReadingTests(unittest.TestCase):
    FRIED = {"kcal": 312.0, "protein": 3.4, "fat": 15.0, "carbs": 41.0}
    BOILED = {"kcal": 87.0, "protein": 1.9, "fat": 0.1, "carbs": 20.1}

    def papas(self, equivalents=None):
        """Principal: 150 g de papas fritas (468 kcal); equivalente: 200 g de papas hervidas (174 kcal)."""
        if equivalents is None:
            equivalents = [{"source": "SR", "code": "2", "grams": 200.0, "per100g": self.BOILED,
                            "note": "el texto no dice como estaban cocidas"}]
        return gold_food("papas", "papas", 468, 5.1, 22.5, 61.5, tol="HOUSEHOLD", grams=150.0,
                         per100g=self.FRIED, equivalents=equivalents)

    def boiled_pred(self, grams=200.0, kcal=174.0, auto=True, per100g=None):
        return pred_m("papas", kcal, 3.8, 0.2, 40.2, grams=grams, auto=auto,
                      per100g=dict(per100g or self.BOILED))

    def test_equivalent_eaten_helper(self):
        alts = score.equivalent_eaten(self.papas())
        self.assertEqual(len(alts), 1)
        self.assertAlmostEqual(alts[0]["eaten"]["kcal"], 174.0)
        self.assertAlmostEqual(alts[0]["eaten"]["protein"], 3.8)
        self.assertAlmostEqual(alts[0]["eaten"]["fat"], 0.2)
        self.assertAlmostEqual(alts[0]["eaten"]["carbs"], 40.2)
        self.assertEqual(alts[0]["grams"], 200.0)

    def test_prediction_matching_equivalent_is_correct_and_not_silent_error(self):
        r = ev([self.papas()], [self.boiled_pred()])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m3_den"], c["m3_via_equiv"]), (1, 1, 1))
        self.assertEqual((c["m4_num"], c["m4_den"]), (0, 1))
        self.assertEqual((c["m1_num"], c["m1_den"]), (1, 1))
        self.assertEqual((c["m2_num"], c["m2_den"]), (1, 1))     # 200 g frente a los 200 g del equivalente
        self.assertEqual(r["failures"], [])

    def test_m2_against_equivalent_grams_can_fail(self):
        r = ev([self.papas()], [self.boiled_pred(grams=100.0)])   # 100 g no cumplen los 200 g del equivalente
        c = r["counters"]
        self.assertEqual((c["m2_num"], c["m2_den"]), (0, 1))

    def test_prediction_matching_main_is_not_counted_as_via_equiv(self):
        r = ev([self.papas()], [pred_m("papas", 468, 5.1, 22.5, 61.5, grams=150.0, auto=True,
                                       per100g=dict(self.FRIED))])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m3_via_equiv"], c["m4_num"]), (1, 0, 0))
        self.assertEqual((c["m2_num"], c["m2_den"]), (1, 1))

    def test_prediction_far_from_main_and_equivalent_is_wrong_and_silent_error(self):
        r = ev([self.papas()], [pred_m("papas", 900, 8, 40, 90, grams=300.0, auto=True,
                                       per100g={"kcal": 233.0, "protein": 2.7, "fat": 13.0, "carbs": 30.0})])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m3_den"], c["m3_via_equiv"]), (0, 1, 0))
        self.assertEqual((c["m4_num"], c["m4_den"]), (1, 1))
        self.assertEqual(len(r["failures"]), 1)

    def test_heuristic_prediction_matching_equivalent_is_not_accepted(self):
        p = self.boiled_pred()
        p["heuristic"] = True
        c = ev([self.papas()], [p])["counters"]
        self.assertEqual((c["m3_num"], c["m3_via_equiv"], c["m4_num"]), (0, 0, 1))

    def test_incomplete_equivalents_are_ignored(self):
        bad = [{"source": "SR", "code": "a", "per100g": self.BOILED},                       # sin grams
               {"source": "SR", "code": "b", "grams": 0, "per100g": self.BOILED},          # grams = 0
               {"source": "SR", "code": "c", "grams": 200.0},                               # sin per100g
               {"source": "SR", "code": "d", "grams": 200.0, "per100g": {}},                # per100g vacio
               {"source": "SR", "code": "e", "grams": "200", "per100g": self.BOILED},      # grams no numerico
               {"source": "SR", "code": "f", "grams": 200.0, "per100g": {"kcal": 87.0}},    # faltan macros
               "basura"]
        self.assertEqual(score.equivalent_eaten(self.papas(bad)), [])
        r = ev([self.papas(bad)], [self.boiled_pred()])
        c = r["counters"]
        self.assertEqual((c["m3_num"], c["m3_den"], c["m3_via_equiv"]), (0, 1, 0))
        self.assertEqual((c["m4_num"], c["m4_den"]), (1, 1))
        # y la lectura principal sigue funcionando junto a equivalentes rotos
        ok = ev([self.papas(bad)], [pred_m("papas", 468, 5.1, 22.5, 61.5, grams=150.0, auto=True,
                                           per100g=dict(self.FRIED))])["counters"]
        self.assertEqual((ok["m3_num"], ok["m4_num"]), (1, 0))

    def test_equivalent_without_per100g_values_does_not_break_m1(self):
        eq = [{"source": "SR", "code": "x", "grams": 200.0, "per100g": {}}]
        c = ev([self.papas(eq)], [pred_m("papas", 468, 5.1, 22.5, 61.5, per100g=dict(self.FRIED))])["counters"]
        self.assertEqual((c["m1_num"], c["m1_den"]), (1, 1))

    def test_m3_via_equiv_is_in_counters(self):
        self.assertIn("m3_via_equiv", score.COUNTERS)
        self.assertIn("m3_via_equiv", score.BOOT_COUNTERS)
        t = score.totals([ev([self.papas()], [self.boiled_pred()])])
        self.assertEqual(t["m3_via_equiv"], 1)


# ---------------------------------------------------------------------------
# CLI de extremo a extremo
# ---------------------------------------------------------------------------

def good_pred(cid, system, kcal=155.0):
    return pred_rec(cid, [pred_m("huevo cocido", kcal, 13, 11, 1.1, auto=True,
                                 nutrients=["fiber", "sugar", "sodium", "potassium"])], system=system)


class CliTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="score_test_")
        self.addCleanup(shutil.rmtree, self.tmp, True)

    def fixtures(self, n=40, split="dev", ids=None, text="huevo cocido"):
        ids = ids or ["E%04d" % (i + 1) for i in range(n)]
        cases = [case(i, text, split, ["F01"] if k % 2 == 0 else ["F01", "F02"]) for k, i in enumerate(ids)]
        golds = [gold_rec(i, [gold_food("huevo cocido", "huevo cocido", 155, 13, 11, 1.1)], meal_kcal=155)
                 for i in ids]
        paths = {"cases": os.path.join(self.tmp, "all.jsonl"), "gold": os.path.join(self.tmp, "gold.jsonl")}
        write_jsonl(paths["cases"], cases)
        write_jsonl(paths["gold"], golds)
        return ids, paths

    def pred_file(self, name, ids, system, kcal):
        p = os.path.join(self.tmp, name)
        write_jsonl(p, [good_pred(i, system, kcal) for i in ids])
        return p

    def run_cli(self, paths, pred, pred_b=None, split="dev", boot=200, seed=20261027, out="out"):
        out_dir = os.path.join(self.tmp, out)
        argv = ["--cases", paths["cases"], "--gold", paths["gold"], "--pred", pred,
                "--split", split, "--out-dir", out_dir, "--seed", str(seed), "--boot", str(boot)]
        if pred_b:
            argv += ["--pred-b", pred_b]
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(score.main(argv), 0)
        md = glob.glob(os.path.join(out_dir, "*.md"))
        js = glob.glob(os.path.join(out_dir, "*.json"))
        self.assertEqual((len(md), len(js)), (1, 1))
        with open(md[0], encoding="utf-8") as fh:
            md_text = fh.read()
        with open(js[0], encoding="utf-8") as fh:
            data = json.load(fh)
        return md[0], md_text, data

    def test_ab_better_system_accepted(self):
        ids, paths = self.fixtures()
        a = self.pred_file("a.jsonl", ids, "sysA", 400.0)   # mal y autoaceptado
        b = self.pred_file("b.jsonl", ids, "sysB", 155.0)
        path, md, data = self.run_cli(paths, a, b)
        self.assertEqual(data["acceptance"]["verdict"], "ACEPTA")
        self.assertIn("-vs-sysB", os.path.basename(path))
        self.assertIn("ACEPTA", md)
        self.assertAlmostEqual(data["global"]["delta"]["M3"]["value"], 100.0)
        self.assertGreater(data["global"]["delta"]["M3"]["ci95"][0], 0)

    def test_ab_worse_system_rejected(self):
        ids, paths = self.fixtures()
        a = self.pred_file("a.jsonl", ids, "sysA", 155.0)
        b = self.pred_file("b.jsonl", ids, "sysB", 400.0)
        _path, md, data = self.run_cli(paths, a, b)
        acc = data["acceptance"]
        self.assertEqual(acc["verdict"], "NO ACEPTA")
        self.assertFalse(acc["criteria"]["m3_ci_lower_gt_0"])
        self.assertFalse(acc["criteria"]["m4_ci_upper_le_0_5pp"])
        self.assertFalse(acc["criteria"]["no_phenomenon_drops_gt_3pp"])   # F01 y F02 tienen >= 30 menciones
        self.assertTrue(acc["reasons"])
        self.assertIn("NO ACEPTA", md)

    def test_phenomenon_with_few_mentions_does_not_veto(self):
        ids, paths = self.fixtures(n=40)
        # B igual de bueno en todo salvo 10 casos F02 (solo 20 menciones F02 < 30)
        a = self.pred_file("a.jsonl", ids, "sysA", 400.0)
        pb = os.path.join(self.tmp, "b.jsonl")
        write_jsonl(pb, [good_pred(i, "sysB", 155.0) for i in ids])
        _path, _md, data = self.run_cli(paths, a, pb)
        self.assertEqual(data["phenomena"]["F02"]["mentions"], 20)
        self.assertEqual(data["acceptance"]["verdict"], "ACEPTA")

    def test_bootstrap_deterministic_same_seed(self):
        ids, paths = self.fixtures(n=30)
        # mezcla de aciertos y fallos para que el IC no sea degenerado
        rows_a = [good_pred(i, "sysA", 155.0 if k % 3 else 400.0) for k, i in enumerate(ids)]
        rows_b = [good_pred(i, "sysB", 155.0 if k % 2 else 400.0) for k, i in enumerate(ids)]
        pa, pb = os.path.join(self.tmp, "a.jsonl"), os.path.join(self.tmp, "b.jsonl")
        write_jsonl(pa, rows_a)
        write_jsonl(pb, rows_b)
        _p1, _m1, d1 = self.run_cli(paths, pa, pb, seed=7, out="o1")
        _p2, _m2, d2 = self.run_cli(paths, pa, pb, seed=7, out="o2")
        _p3, _m3, d3 = self.run_cli(paths, pa, pb, seed=8, out="o3")
        for d in (d1, d2, d3):
            d.pop("generatedAt")
        self.assertEqual(d1, d2)
        self.assertNotEqual(d1["global"]["delta"]["M3"]["ci95"], d3["global"]["delta"]["M3"]["ci95"])
        ci = d1["global"]["A"]["metrics"]["M3"]["ci95"]
        self.assertLess(ci[0], ci[1])

    def test_test_split_report_is_sealed(self):
        ids = ["E9101", "E9102", "E9103", "E9104"]
        ids_dev = ["E0001", "E0002"]
        cases = [case(i, "zanahoria rallada unica %s" % i, "test", ["F01"]) for i in ids] \
            + [case(i, "texto de dev", "dev", ["F01"]) for i in ids_dev]
        golds = [gold_rec(i, [gold_food("zanahoria rallada", "zanahoria", 41, 0.9, 0.2, 10)]) for i in ids + ids_dev]
        cp, gp = os.path.join(self.tmp, "all.jsonl"), os.path.join(self.tmp, "gold.jsonl")
        write_jsonl(cp, cases)
        write_jsonl(gp, golds)
        pa = os.path.join(self.tmp, "a.jsonl")
        write_jsonl(pa, [pred_rec(i, [pred_m("zanahoria rallada", 400, 0.9, 0.2, 10, auto=True)], system="sysA")
                         for i in ids + ids_dev])
        pb = os.path.join(self.tmp, "b.jsonl")
        write_jsonl(pb, [pred_rec(i, [pred_m("zanahoria rallada", 41, 0.9, 0.2, 10, auto=True)], system="sysB")
                         for i in ids + ids_dev])
        paths = {"cases": cp, "gold": gp}
        _p, md, data = self.run_cli(paths, pa, pb, split="test", out="t")
        blob = md + json.dumps(data, ensure_ascii=False)
        for forbidden in ids + ids_dev + ["zanahoria", "rallada", "texto de dev"]:
            self.assertNotIn(forbidden, blob)
        self.assertNotIn("failures", data)
        self.assertEqual(data["coverage"]["casesScored"], 4)
        # el mismo caso en DEV si muestra ids y textos
        _p, md_dev, data_dev = self.run_cli(paths, pa, pb, split="dev", out="d")
        self.assertIn("E0001", md_dev)
        self.assertIn("failures", data_dev)

    def test_dev_failure_table_limited_to_50(self):
        ids, paths = self.fixtures(n=60)
        a = self.pred_file("a.jsonl", ids, "sysA", 400.0)
        _p, md, data = self.run_cli(paths, a)
        self.assertEqual(data["failures"]["total"], 60)
        self.assertEqual(len(data["failures"]["shown"]), 50)
        self.assertEqual(md.count("| E0"), 50)

    def test_missing_prediction_line_counts_as_error_and_gold_less_cases_skipped(self):
        ids, paths = self.fixtures(n=10)
        extra = case("E0999", "sin oro", "dev", ["F01"])
        write_jsonl(paths["cases"], [case(i, "huevo cocido", "dev", ["F01"]) for i in ids] + [extra])
        a = os.path.join(self.tmp, "a.jsonl")
        write_jsonl(a, [good_pred(i, "sysA") for i in ids[:8]])
        _p, _md, data = self.run_cli(paths, a)
        self.assertEqual(data["coverage"]["casesScored"], 10)
        self.assertEqual(data["coverage"]["casesWithoutGold"], 1)
        self.assertEqual(data["global"]["A"]["counts"]["errors"], 2)
        self.assertEqual(data["global"]["A"]["counts"]["missing"], 2)
        self.assertAlmostEqual(data["global"]["A"]["metrics"]["M3"]["value"], 80.0)

    def test_f17_f18_names_in_report(self):
        self.assertEqual(score.PHENOMENA_NAMES["F17"], "Texto extenso o con ruido")
        self.assertEqual(score.PHENOMENA_NAMES["F18"], "Inglés o mezcla de idiomas")
        ids = ["E1001", "E1002"]
        cases = [dict(case(ids[0], "huevo cocido", "dev", ["F17"]), group="B6", lang="es"),
                 dict(case(ids[1], "boiled egg", "dev", ["F18"]), group="B1", lang="en")]
        golds = [gold_rec(i, [gold_food("huevo cocido", "huevo cocido", 155, 13, 11, 1.1)]) for i in ids]
        paths = {"cases": os.path.join(self.tmp, "all.jsonl"), "gold": os.path.join(self.tmp, "gold.jsonl")}
        write_jsonl(paths["cases"], cases)
        write_jsonl(paths["gold"], golds)
        a = self.pred_file("a.jsonl", ids, "sysA", 155.0)
        _p, md, data = self.run_cli(paths, a)
        self.assertEqual(data["phenomena"]["F17"]["name"], "Texto extenso o con ruido")
        self.assertEqual(data["phenomena"]["F18"]["name"], "Inglés o mezcla de idiomas")
        self.assertIn("F17 Texto extenso o con ruido", md)
        self.assertIn("F18 Inglés o mezcla de idiomas", md)

    def breakdown_fixtures(self):
        ids = ["E0001", "E0002", "E1001", "E1002"]
        cases = [case("E0001", "huevo cocido", "dev", ["F01"]),                                  # modulo A: sin group ni lang
                 dict(case("E0002", "huevo cocido", "dev", ["F01"]), group=None),                # group null explicito
                 dict(case("E1001", "huevo cocido", "dev", ["F18"]), group="B1", lang="en"),
                 dict(case("E1002", "huevo cocido", "dev", ["F17"]), group="B3", lang="mix")]
        golds = [gold_rec(i, [gold_food("huevo cocido", "huevo cocido", 155, 13, 11, 1.1)]) for i in ids]
        paths = {"cases": os.path.join(self.tmp, "all.jsonl"), "gold": os.path.join(self.tmp, "gold.jsonl")}
        write_jsonl(paths["cases"], cases)
        write_jsonl(paths["gold"], golds)
        return ids, paths

    def test_breakdown_by_group_and_language_single_system(self):
        ids, paths = self.breakdown_fixtures()
        a = self.pred_file("a.jsonl", ids, "sysA", 155.0)
        _p, md, data = self.run_cli(paths, a)
        self.assertEqual(list(data["groups"]), ["A", "B1", "B3"])
        self.assertEqual(list(data["langs"]), ["en", "es", "mix"])
        self.assertEqual(data["groups"]["A"]["cases"], 2)          # group null y ausente caen en "A"
        self.assertEqual(data["groups"]["A"]["mentions"], 2)
        self.assertEqual(data["langs"]["es"]["cases"], 2)          # lang ausente cae en "es"
        self.assertEqual(data["groups"]["B1"]["A"]["metrics"]["M3"]["value"], 100.0)
        self.assertIn("ci95", data["groups"]["B3"]["A"]["metrics"]["M3"])
        self.assertIn("## Por grupo", md)
        self.assertIn("## Por idioma", md)
        self.assertIn("| A Modulo A", md)
        self.assertIn("| B1 ", md)
        self.assertIn("| mix ", md)

    def test_breakdown_by_group_and_language_with_paired_delta(self):
        ids, paths = self.breakdown_fixtures()
        a = self.pred_file("a.jsonl", ids, "sysA", 400.0)
        b = self.pred_file("b.jsonl", ids, "sysB", 155.0)
        _p, md, data = self.run_cli(paths, a, b)
        for fam in ("groups", "langs"):
            for key, e in data[fam].items():
                self.assertIn("delta", e, (fam, key))
                self.assertAlmostEqual(e["delta"]["M3"]["value"], 100.0)
                for k in ("M3", "M5"):
                    self.assertEqual(len(e["delta"][k]["ci95"]), 2)
        self.assertIn("| Grupo | Menciones | M3 A | M3 B |", md)
        self.assertIn("| Idioma | Menciones | M3 A | M3 B |", md)
        # el desglose no entra en la regla de aceptacion
        self.assertEqual(set(data["acceptance"]["criteria"]),
                         {"m3_ci_lower_gt_0", "m4_ci_upper_le_0_5pp", "no_phenomenon_drops_gt_3pp"})

    def test_m3_via_equiv_in_report_counts(self):
        ids, paths = self.fixtures(n=2)
        eq = [{"source": "SR", "code": "2", "grams": 200.0,
               "per100g": {"kcal": 87.0, "protein": 1.9, "fat": 0.1, "carbs": 20.1}}]
        golds = [gold_rec(i, [gold_food("papas", "papas", 468, 5.1, 22.5, 61.5, tol="HOUSEHOLD", equivalents=eq)])
                 for i in ids]
        write_jsonl(paths["gold"], golds)
        a = os.path.join(self.tmp, "a.jsonl")
        write_jsonl(a, [pred_rec(i, [pred_m("papas", 174, 3.8, 0.2, 40.2, auto=True)]) for i in ids])
        _p, _md, data = self.run_cli(paths, a)
        self.assertEqual(data["global"]["A"]["counts"]["m3_via_equiv"], 2)
        self.assertEqual(data["global"]["A"]["counts"]["m4_num"], 0)
        self.assertAlmostEqual(data["global"]["A"]["metrics"]["M3"]["value"], 100.0)

    def test_sanitize_filename(self):
        self.assertEqual(score.sanitize_filename("master@c4491bc83"), "master@c4491bc83")
        self.assertEqual(score.sanitize_filename("a/b:c*d"), "a_b_c_d")


if __name__ == "__main__":
    unittest.main()
