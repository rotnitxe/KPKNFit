import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import refindex  # noqa: E402
from refindex import Ref, RefIndex, eaten, tokens  # noqa: E402


def make_ref(rid, source, names, kcal, protein=1.0, fat=1.0, carbs=1.0, portions=None):
    return Ref(id=rid, source=source, code=rid.split("_", 1)[-1], name=names[0], names=list(names),
               category="", preparation="", form="",
               per100g={"kcal": kcal, "protein": protein, "fat": fat, "carbs": carbs,
                        "fiber": None, "sugar": None, "sodium_mg": None},
               portions=portions or [])


CORPUS = [
    make_ref("usda_1", "USDA_SR_LEGACY", ["Rice, white, long-grain, regular, cooked"], 130, 2.7, 0.3, 28.0,
             [("1 cup", 158.0)]),
    make_ref("usda_2", "USDA_FNDDS", ["Rice, white, cooked, NFS"], 129, 2.7, 0.3, 28.2),
    make_ref("usda_3", "USDA_SR_LEGACY", ["Rice, brown, raw"], 370, 7.9, 2.9, 77.0),
    make_ref("usda_4", "USDA_SR_LEGACY", ["Milk, dry, whole"], 496, 26.3, 26.7, 38.4),
    make_ref("usda_5", "USDA_SR_LEGACY", ["Milk, whole, 3.25% milkfat"], 61, 3.2, 3.3, 4.8),
    make_ref("ciqual_6", "CIQUAL", ["Riz blanc cuit", "White rice, cooked"], 131, 2.5, 0.3, 28.5),
    make_ref("usda_7", "USDA_FOUNDATION", ["Rice, white, cooked"], 128, 2.6, 0.3, 28.0),
]


class TokensTest(unittest.TestCase):
    def test_lowercase_accents_and_split(self):
        self.assertEqual(tokens("Pan Marraqueta, WITH Palta!"), ["pan", "marraqueta", "palta"])
        self.assertEqual(tokens("Crème brûlée"), ["creme", "brulee"])

    def test_plural_and_stopwords(self):
        self.assertEqual(tokens("Beans of the garden"), ["bean", "garden"])
        self.assertEqual(tokens("gas bus"), ["gas", "bus"])  # len <= 3 no se toca
        self.assertEqual(tokens("lait de la ferme"), ["lait", "ferme"])

    def test_empty(self):
        self.assertEqual(tokens(""), [])
        self.assertEqual(tokens(None), [])
        self.assertEqual(tokens("!!! ..."), [])


class SearchTest(unittest.TestCase):
    def setUp(self):
        self.index = RefIndex(CORPUS)

    def test_expected_first(self):
        hits = self.index.search("milk dry whole", k=3)
        self.assertEqual(hits[0][1].id, "usda_4")
        hits = self.index.search("rice brown raw", k=3)
        self.assertEqual(hits[0][1].id, "usda_3")

    def test_source_tiebreak_and_id_stability(self):
        hits = self.index.search("rice white cooked", k=5)
        ids = [r.id for _, r in hits]
        self.assertEqual(ids[0], "usda_2")  # FNDDS gana empates frente a SR y Foundation
        self.assertEqual(hits, self.index.search("rice white cooked", k=5))
        # Foundation (usda_7) antes que CIQUAL (ciqual_6) cuando empatan, y siempre en un orden fijo
        self.assertLess(ids.index("usda_7"), ids.index("ciqual_6"))

    def test_source_filter(self):
        hits = self.index.search("rice white cooked", k=5, sources=["CIQUAL"])
        self.assertEqual([r.id for _, r in hits], ["ciqual_6"])
        self.assertEqual(self.index.search("rice", sources=["NO_EXISTE"]), [])

    def test_french_names_and_no_match(self):
        self.assertEqual(self.index.search("riz blanc")[0][1].id, "ciqual_6")
        self.assertEqual(self.index.search("zzzzz"), [])
        self.assertEqual(self.index.search(""), [])

    def test_get(self):
        self.assertEqual(self.index.get("usda_4").name, "Milk, dry, whole")
        self.assertIsNone(self.index.get("usda_999"))


class EatenTest(unittest.TestCase):
    def test_scaling_and_none(self):
        ref = make_ref("usda_4", "USDA_SR_LEGACY", ["Milk, dry, whole"], 496, 26.3, 26.7, 38.4)
        self.assertEqual(eaten(ref, 26.0), {"kcal": 129.0, "protein": 6.8, "fat": 6.9, "carbs": 10.0})
        ref.per100g["fat"] = None
        self.assertIsNone(eaten(ref, 50)["fat"])
        self.assertEqual(eaten(ref, 0)["kcal"], 0.0)


class BatchTest(unittest.TestCase):
    def test_roundtrip(self):
        index = RefIndex(CORPUS)
        with tempfile.TemporaryDirectory() as tmp:
            q = Path(tmp) / "q.jsonl"
            out = Path(tmp) / "c.jsonl"
            q.write_text(
                json.dumps({"qid": "a", "query": "milk dry whole"}) + "\n\n"
                + json.dumps({"qid": "b", "query": "rice white cooked", "sources": "CIQUAL,USDA_FOUNDATION"}) + "\n",
                encoding="utf-8")
            self.assertEqual(refindex.run_batch(index, q, out, 2), 2)
            rows = [json.loads(line) for line in out.read_text(encoding="utf-8").splitlines()]
        self.assertEqual([r["qid"] for r in rows], ["a", "b"])
        self.assertEqual(rows[0]["query"], "milk dry whole")
        first = rows[0]["candidates"][0]
        self.assertEqual(first["id"], "usda_4")
        self.assertEqual(set(first), {"id", "source", "code", "name", "per100g", "preparation", "form", "portions"})
        self.assertEqual(first["per100g"]["kcal"], 496)
        self.assertEqual(len(rows[0]["candidates"]), 2)
        self.assertEqual({c["source"] for c in rows[1]["candidates"]}, {"CIQUAL", "USDA_FOUNDATION"})
        rice = rows[1]["candidates"]
        self.assertEqual(rice[0]["portions"], [])

    def test_portions_capped_at_six(self):
        ref = make_ref("usda_9", "USDA_FNDDS", ["Bread"], 250, portions=[("p%d" % i, 10.0 + i) for i in range(9)])
        cand = refindex.candidate_dict(ref)
        self.assertEqual(len(cand["portions"]), 6)
        self.assertEqual(cand["portions"][0], {"label": "p0", "grams": 10.0})


class CachePayloadTest(unittest.TestCase):
    def test_payload_roundtrip(self):
        index = RefIndex(CORPUS)
        clone = RefIndex.from_payload(index.to_payload())
        self.assertEqual(clone.refs, index.refs)
        self.assertEqual(clone.search("milk dry whole")[0][1].id, "usda_4")


INPUTS = refindex.DEFAULT_INPUTS_DIR


@unittest.skipUnless(os.path.isdir(INPUTS), "fuentes reales no disponibles")
class RealSourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = refindex.load_index(INPUTS, refindex.DEFAULT_LOCKS_DIR, refindex.DEFAULT_CACHE_DIR)

    def test_milk_dry_whole_and_rice_white_cooked(self):
        self.assertGreater(len(self.index.refs), 15000)
        milk = [r.per100g["kcal"] for _, r in self.index.search("milk dry whole", k=5)]
        self.assertTrue(any(450 <= k <= 520 for k in milk), milk)
        rice = [r.per100g["kcal"] for _, r in self.index.search("rice white cooked", k=5)]
        self.assertTrue(any(100 <= k <= 160 for k in rice), rice)


if __name__ == "__main__":
    unittest.main()
