"""python -I scripts/nutrition_v2/exam/test_fallback.py"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fallback  # noqa: E402


def mention(food_id="usda_1", grams=100.0, kcal=50.0):
    return {"text": "x", "foodId": food_id, "grams": grams, "per100g": {"kcal": kcal}}


class FallbackTest(unittest.TestCase):
    def test_keeps_v2_when_any_mention_resolves(self):
        v2 = [{"id": "E1", "latencyMs": 2.0, "error": None, "mentions": [mention(food_id=None), mention()]}]
        v1 = [{"id": "E1", "latencyMs": 100.0, "error": None, "mentions": [mention("gen1")]}]
        rows, fell = fallback.combine(v2, v1, "v2app")
        self.assertEqual(0, fell)
        self.assertEqual("usda_1", rows[0]["mentions"][1]["foodId"])
        self.assertEqual("v2app", rows[0]["system"])

    def test_falls_back_when_nothing_resolves_or_error(self):
        v2 = [{"id": "E1", "latencyMs": 2.0, "error": None, "mentions": [mention(food_id=None)]},
              {"id": "E2", "latencyMs": 3.0, "error": "boom", "mentions": [mention()]},
              {"id": "E3", "latencyMs": 1.0, "error": None, "mentions": [mention(kcal=None)]}]
        v1 = [{"id": i, "latencyMs": 100.0, "error": None, "mentions": [mention("gen1")]} for i in ("E1", "E2", "E3")]
        rows, fell = fallback.combine(v2, v1, "v2app")
        self.assertEqual(3, fell)
        self.assertEqual(["gen1"] * 3, [r["mentions"][0]["foodId"] for r in rows])
        self.assertEqual(102.0, rows[0]["latencyMs"])

    def test_case_missing_in_fallback_keeps_v2(self):
        v2 = [{"id": "E9", "latencyMs": 2.0, "error": None, "mentions": []}]
        rows, fell = fallback.combine(v2, [], "v2app")
        self.assertEqual(0, fell)
        self.assertEqual("E9", rows[0]["id"])


if __name__ == "__main__":
    unittest.main()
