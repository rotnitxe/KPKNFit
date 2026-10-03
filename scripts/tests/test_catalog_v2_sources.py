#!/usr/bin/env python3
"""Tests for the evidence source tool (scripts/catalog_v2_sources.py). No test touches the network:
every fetch is a fake that answers from a dict, so the verification rules are exercised in isolation."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from typing import Any

SCRIPTS = Path(__file__).resolve().parents[1]
MODULE = SCRIPTS / "catalog_v2_sources.py"


def load_module():
    spec = importlib.util.spec_from_file_location("catalog_v2_sources", MODULE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {MODULE}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["catalog_v2_sources"] = module
    spec.loader.exec_module(module)
    return module


sources = load_module()

TITLE = "Surface electromyographic activation patterns and elbow joint motion during a pull-up, chin-up, or perfect-pullup rotational exercise"


def pubmed_reply(pmid: str, title: str) -> str:
    return json.dumps({"result": {"uids": [pmid], pmid: {"title": title + ".", "pubdate": "2010 Dec", "source": "J Strength Cond Res"}}})


def fake_fetch(responses: dict[str, str]):
    def fetch(url: str) -> str:
        for fragment, body in responses.items():
            if fragment in url:
                return body
        raise urllib.error.URLError(f"no fake answer for {url}")

    return fetch


def source(url: str, title: str = TITLE, source_id: str = "youdas2010") -> dict[str, Any]:
    return {"id": source_id, "url": url, "title": title, "claim": "Muestra que el bíceps trabaja con cualquier agarre."}


def ficha(definition_id: str, entries: list[dict[str, Any]], status: str = "CURATED") -> dict[str, Any]:
    return {"schemaVersion": 1, "familyId": "fam", "definitions": {definition_id: {"status": status, "sources": entries}}}


class WordMatchingTest(unittest.TestCase):
    def test_title_words_ignore_case_accents_stopwords_and_short_words(self) -> None:
        self.assertEqual({"effects", "grip", "width", "emg", "pull", "ups"}, sources.title_words("The Effects of GRIP width on EMG, in pull-ups"))
        self.assertEqual({"electromiografia"}, sources.title_words("Electromiografía"))

    def test_jaccard_is_symmetric_and_zero_for_unrelated_titles(self) -> None:
        self.assertAlmostEqual(1.0, sources.jaccard("Pull-up grip study", "The grip study: pull-up"))
        self.assertEqual(0.0, sources.jaccard("Pull-up grip study", "Rotator cuff repair outcomes"))

    def test_containment_is_the_share_of_the_claimed_title_found_in_the_page(self) -> None:
        self.assertEqual(1.0, sources.containment("Gluteal muscle activation", "<<Gluteal muscle activation during exercise>>"))
        self.assertAlmostEqual(2 / 3, sources.containment("Gluteal muscle activation", "Muscle activation only"))


class VerifySourceTest(unittest.TestCase):
    def test_pubmed_record_with_the_same_title_is_accepted(self) -> None:
        fetch = fake_fetch({"esummary": pubmed_reply("21068680", TITLE)})
        entry = sources.verify_source(source("https://pubmed.ncbi.nlm.nih.gov/21068680/"), fetch)
        self.assertEqual("pubmed-esummary", entry["method"])
        self.assertEqual(1.0, entry["similarity"])
        self.assertEqual(TITLE, entry["title"])

    def test_pubmed_record_with_another_title_is_rejected(self) -> None:
        fetch = fake_fetch({"esummary": pubmed_reply("21068680", "Rotator cuff repair outcomes after arthroscopic surgery")})
        with self.assertRaisesRegex(sources.SourceError, "title mismatch"):
            sources.verify_source(source("https://pubmed.ncbi.nlm.nih.gov/21068680/"), fetch)

    def test_pubmed_pmid_that_does_not_exist_is_rejected(self) -> None:
        fetch = fake_fetch({"esummary": json.dumps({"result": {"uids": [], "999": {"error": "cannot get document summary"}}})})
        with self.assertRaisesRegex(sources.SourceError, "no record 999"):
            sources.verify_source(source("https://pubmed.ncbi.nlm.nih.gov/999/"), fetch)

    def test_doi_is_matched_against_crossref(self) -> None:
        fetch = fake_fetch({"api.crossref.org": json.dumps({"message": {"title": [TITLE]}})})
        entry = sources.verify_source(source("https://doi.org/10.1519/jsc.0b013e3181f1598c"), fetch)
        self.assertEqual("crossref", entry["method"])

    def test_other_pages_must_answer_and_contain_the_title(self) -> None:
        page = f"<html><head><title>x</title><script>ignored()</script></head><body><h1>{TITLE}</h1></body></html>"
        entry = sources.verify_source(source("https://example.org/paper"), fake_fetch({"example.org": page}))
        self.assertEqual("http-page", entry["method"])
        with self.assertRaisesRegex(sources.SourceError, "title mismatch"):
            sources.verify_source(source("https://example.org/other"), fake_fetch({"example.org": "<body>Una página sin relación</body>"}))
        with self.assertRaisesRegex(sources.SourceError, "did not answer"):
            sources.verify_source(source("https://example.org/gone"), fake_fetch({}))


class ProofFileTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fichas = Path(self.temp.name) / "fichas"
        self.fichas.mkdir()
        self.proof_path = Path(self.temp.name) / "sources_verified.json"

    def write_ficha(self, name: str, payload: dict[str, Any]) -> None:
        (self.fichas / name).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

    def run_cli(self, argv: list[str], fetch=None) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = sources.main(argv, fichas_dir=self.fichas, proof_path=self.proof_path, fetch=fetch or fake_fetch({}), pause=0)
        return code, out.getvalue(), err.getvalue()

    def test_only_curated_fichas_need_proof(self) -> None:
        self.write_ficha("legacy.json", ficha("d_legacy", [source("https://pubmed.ncbi.nlm.nih.gov/1/")], status="LEGACY"))
        self.assertEqual(0, self.run_cli(["check"])[0])

    def test_check_names_the_missing_changed_and_weak_proof(self) -> None:
        self.write_ficha(
            "fam.json",
            ficha("pull_up", [source("https://pubmed.ncbi.nlm.nih.gov/1/", source_id="a"), source("https://pubmed.ncbi.nlm.nih.gov/2/", source_id="b"), source("https://pubmed.ncbi.nlm.nih.gov/3/", source_id="c")]),
        )
        sources.write_proof(
            self.proof_path,
            {
                "schemaVersion": 1,
                "sources": {
                    "https://pubmed.ncbi.nlm.nih.gov/2/": {"title": "Otro título distinto", "similarity": 1.0},
                    "https://pubmed.ncbi.nlm.nih.gov/3/": {"title": TITLE, "similarity": 0.4},
                },
            },
        )
        code, out, _ = self.run_cli(["check"])
        self.assertEqual(1, code)
        self.assertIn("source_unverified:pull_up:a", out)
        self.assertIn("source_title_changed:pull_up:b", out)
        self.assertIn("source_weak_match:pull_up:c", out)

    def test_verify_writes_proof_that_check_then_accepts_and_prunes_orphans(self) -> None:
        self.write_ficha("fam.json", ficha("pull_up", [source("https://pubmed.ncbi.nlm.nih.gov/21068680/")]))
        sources.write_proof(self.proof_path, {"schemaVersion": 1, "sources": {"https://pubmed.ncbi.nlm.nih.gov/777/": {"title": "Huérfana", "similarity": 1.0}}})
        code, out, _ = self.run_cli(["verify"], fake_fetch({"esummary": pubmed_reply("21068680", TITLE)}))
        self.assertEqual(0, code, out)
        proof = json.loads(self.proof_path.read_text(encoding="utf-8"))
        self.assertEqual(["https://pubmed.ncbi.nlm.nih.gov/21068680/"], sorted(proof["sources"]))
        self.assertEqual(0, self.run_cli(["check"])[0])

    def test_verify_fails_loudly_and_keeps_going_when_a_source_does_not_check_out(self) -> None:
        self.write_ficha(
            "fam.json",
            ficha("pull_up", [source("https://pubmed.ncbi.nlm.nih.gov/21068680/", source_id="ok"), source("https://pubmed.ncbi.nlm.nih.gov/5/", TITLE + " imaginario", source_id="bad")]),
        )
        fetch = fake_fetch({"id=21068680": pubmed_reply("21068680", TITLE), "id=5": pubmed_reply("5", "Rotator cuff repair outcomes after surgery")})
        code, _, err = self.run_cli(["verify"], fetch)
        self.assertEqual(1, code)
        self.assertIn("FAILED", err)
        self.assertIn("pubmed.ncbi.nlm.nih.gov/5/", err)
        proof = json.loads(self.proof_path.read_text(encoding="utf-8"))
        self.assertIn("https://pubmed.ncbi.nlm.nih.gov/21068680/", proof["sources"])
        self.assertNotIn("https://pubmed.ncbi.nlm.nih.gov/5/", proof["sources"])

    def test_scoped_verify_and_check_ignore_other_definitions_and_never_prune_their_proof(self) -> None:
        self.write_ficha("fam.json", ficha("pull_up", [source("https://pubmed.ncbi.nlm.nih.gov/21068680/")]))
        other = ficha("seal_row", [source("https://pubmed.ncbi.nlm.nih.gov/19620925/", source_id="fenwick")])
        self.write_ficha("fam2.json", other)
        sources.write_proof(self.proof_path, {"schemaVersion": 1, "sources": {"https://pubmed.ncbi.nlm.nih.gov/777/": {"title": "De otra definición", "similarity": 1.0}}})
        code, out, _ = self.run_cli(["verify", "--definitions", "pull_up"], fake_fetch({"esummary": pubmed_reply("21068680", TITLE)}))
        self.assertEqual(0, code, out)
        proof = json.loads(self.proof_path.read_text(encoding="utf-8"))
        self.assertEqual(
            ["https://pubmed.ncbi.nlm.nih.gov/21068680/", "https://pubmed.ncbi.nlm.nih.gov/777/"],
            sorted(proof["sources"]),
        )
        self.assertEqual(0, self.run_cli(["check", "--definitions", "pull_up"])[0])
        code, out, _ = self.run_cli(["check", "--definitions", "seal_row"])
        self.assertEqual(1, code)
        self.assertIn("source_unverified:seal_row:fenwick", out)
        self.assertIn("verify --definitions seal_row", out)

    def test_verify_merges_instead_of_overwriting_what_another_author_wrote_meanwhile(self) -> None:
        self.write_ficha("fam.json", ficha("pull_up", [source("https://pubmed.ncbi.nlm.nih.gov/21068680/")]))
        concurrent = {"https://pubmed.ncbi.nlm.nih.gov/555/": {"title": "Verificada por otro autor", "similarity": 1.0}}

        def fetch(url: str) -> str:
            sources.write_proof(self.proof_path, {"schemaVersion": 1, "sources": concurrent})  # lands during the network call
            return pubmed_reply("21068680", TITLE)

        code, out, _ = self.run_cli(["verify", "--definitions", "pull_up"], fetch)
        self.assertEqual(0, code, out)
        proof = json.loads(self.proof_path.read_text(encoding="utf-8"))
        self.assertIn("https://pubmed.ncbi.nlm.nih.gov/555/", proof["sources"])
        self.assertIn("https://pubmed.ncbi.nlm.nih.gov/21068680/", proof["sources"])

    def test_proof_lock_serializes_writers_and_breaks_a_stale_lock(self) -> None:
        lock_path = self.proof_path.with_name(self.proof_path.name + ".lock")
        with sources.ProofLock(self.proof_path):
            self.assertTrue(lock_path.exists())
            with self.assertRaisesRegex(sources.SourceError, "locked by another run"):
                with sources.ProofLock(self.proof_path, timeout=0.3):
                    pass  # pragma: no cover - must not be reached
        self.assertFalse(lock_path.exists())
        lock_path.write_text("stale", encoding="utf-8")
        old = lock_path.stat().st_mtime - sources.ProofLock.STALE_AFTER_SECONDS - 10
        os.utime(lock_path, (old, old))
        with sources.ProofLock(self.proof_path, timeout=1.0):
            self.assertTrue(lock_path.exists())
        self.assertFalse(lock_path.exists())

    def test_write_proof_is_atomic_and_leaves_no_temporary_file(self) -> None:
        sources.write_proof(self.proof_path, {"schemaVersion": 1, "sources": {}})
        self.assertTrue(self.proof_path.is_file())
        self.assertEqual([self.proof_path.name], sorted(path.name for path in self.proof_path.parent.iterdir() if path.is_file()))

    def test_abstract_prints_the_record_for_ids_and_urls(self) -> None:
        fetch = fake_fetch({"efetch": "1. J Strength Cond Res. 2010.\n\nAbstract text about pull-ups.\n"})
        code, out, err = self.run_cli(["abstract", "21068680", "https://pubmed.ncbi.nlm.nih.gov/28011412/"], fetch)
        self.assertEqual(0, code, err)
        self.assertIn("=== PMID 21068680 ===", out)
        self.assertIn("=== PMID 28011412 ===", out)
        self.assertIn("Abstract text about pull-ups.", out)
        code, _, err = self.run_cli(["abstract", "no-es-un-id"], fetch)
        self.assertEqual(1, code)
        self.assertIn("not a PubMed id or URL", err)

    def test_lookup_prints_ready_to_paste_urls(self) -> None:
        reply = {
            "esearch": json.dumps({"esearchresult": {"idlist": ["21068680"]}}),
            "esummary": pubmed_reply("21068680", TITLE),
        }
        code, out, _ = self.run_cli(["lookup", "Youdas pull-up"], fake_fetch(reply))
        self.assertEqual(0, code)
        self.assertIn("https://pubmed.ncbi.nlm.nih.gov/21068680/", out)
        self.assertIn("J Strength Cond Res", out)


if __name__ == "__main__":
    unittest.main()
