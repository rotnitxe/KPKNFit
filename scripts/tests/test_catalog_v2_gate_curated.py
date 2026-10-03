#!/usr/bin/env python3
"""The gate must refuse a CURATED definition whose evidence is unverified or whose text regresses
under the editorial audit. Runs the real `source_gate()` over temporary copies of the real files,
using `seal_row` (the exemplar ficha) as the carrier."""

from __future__ import annotations

import copy
import importlib.util
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
GATE = SCRIPTS / "catalog_v2_gate.py"
TEMP_OVERRIDE = os.environ.get("KPKN_TEST_TEMP")

FAMILY = "back_seal_row"
DEFINITION = "seal_row"
SEAL_ROW_ID = "seal_row__barbell"


def temporary_directory() -> tempfile.TemporaryDirectory:
    root = Path(TEMP_OVERRIDE) if TEMP_OVERRIDE and Path(TEMP_OVERRIDE).is_dir() else None
    return tempfile.TemporaryDirectory(dir=str(root) if root else None)


def load_gate():
    spec = importlib.util.spec_from_file_location("catalog_v2_gate_curated_under_test", GATE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {GATE}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gate = load_gate()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def seal_row(source: dict[str, Any]) -> dict[str, Any]:
    for family in source["families"]:
        if family["id"] == FAMILY:
            for definition in family["definitions"]:
                if definition["id"] == DEFINITION:
                    return definition
    raise AssertionError(f"{DEFINITION} not found")


class CuratedGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self._saved = (gate.SOURCE, gate.FICHAS, gate.SOURCES_PROOF)
        self.addCleanup(self._restore)

    def _restore(self) -> None:
        gate.SOURCE, gate.FICHAS, gate.SOURCES_PROOF = self._saved

    def run_gate(
        self,
        mutate_source: Callable[[dict[str, Any]], None] | None = None,
        mutate_ficha: Callable[[dict[str, Any]], None] | None = None,
        mutate_proof: Callable[[dict[str, Any]], None] | None = None,
    ) -> list[str]:
        source = copy.deepcopy(read_json(self._saved[0]))
        if mutate_source is not None:
            mutate_source(source)
        with temporary_directory() as raw:
            workspace = Path(raw)
            source_file = workspace / "catalog_v2.json"
            fichas_dir = workspace / "fichas"
            proof_file = workspace / "sources_verified.json"
            source_file.write_text(json.dumps(source, ensure_ascii=False), encoding="utf-8")
            shutil.copytree(self._saved[1], fichas_dir)
            proof = read_json(self._saved[2])
            if mutate_ficha is not None:
                path = fichas_dir / f"{FAMILY}.json"
                ficha = read_json(path)
                mutate_ficha(ficha)
                path.write_text(json.dumps(ficha, ensure_ascii=False), encoding="utf-8")
            if mutate_proof is not None:
                mutate_proof(proof)
            proof_file.write_text(json.dumps(proof, ensure_ascii=False), encoding="utf-8")
            gate.SOURCE, gate.FICHAS, gate.SOURCES_PROOF = source_file, fichas_dir, proof_file
            return gate.source_gate()

    def test_the_exemplar_is_curated_and_clean(self) -> None:
        ficha = read_json(self._saved[1] / f"{FAMILY}.json")
        self.assertEqual("CURATED", ficha["definitions"][DEFINITION]["status"])
        offending = [f for f in self.run_gate() if f.startswith(("quality_audit:", "source_")) and DEFINITION in f]
        self.assertEqual([], offending)

    def test_a_source_without_offline_proof_blocks_the_gate(self) -> None:
        def cite_new_work(ficha: dict[str, Any]) -> None:
            ficha["definitions"][DEFINITION]["sources"].append(
                {
                    "id": "invented2099",
                    "url": "https://pubmed.ncbi.nlm.nih.gov/99999999/",
                    "title": "A study that nobody has verified against PubMed",
                    "claim": "Afirma algo sobre el remo que nadie comprobó en la fuente.",
                }
            )

        failures = self.run_gate(mutate_ficha=cite_new_work)
        self.assertTrue(any(f.startswith(f"source_unverified:{DEFINITION}:invented2099") for f in failures), failures)

    def test_a_retitled_source_invalidates_its_proof(self) -> None:
        def retitle(ficha: dict[str, Any]) -> None:
            ficha["definitions"][DEFINITION]["sources"][0]["title"] = "Un título distinto al que se verificó contra PubMed"

        failures = self.run_gate(mutate_ficha=retitle)
        self.assertTrue(any(f.startswith(f"source_title_changed:{DEFINITION}:") for f in failures), failures)

    def test_a_weak_proof_blocks_the_gate(self) -> None:
        def weaken(proof: dict[str, Any]) -> None:
            for entry in proof["sources"].values():
                entry["similarity"] = 0.1

        failures = self.run_gate(mutate_proof=weaken)
        self.assertTrue(any(f.startswith(f"source_weak_match:{DEFINITION}:") for f in failures), failures)

    def test_a_banned_phrase_in_curated_copy_blocks_the_gate(self) -> None:
        def add_cliche(source: dict[str, Any]) -> None:
            seal_row(source)["description"] += " Es una joya para la espalda."

        failures = self.run_gate(mutate_source=add_cliche)
        self.assertTrue(any(f.startswith(f"quality_audit:banned_phrase:{DEFINITION}:-:definition.description") for f in failures), failures)

    def test_a_sentence_recycled_between_definition_and_cue_blocks_the_gate(self) -> None:
        def recycle(source: dict[str, Any]) -> None:
            definition = seal_row(source)
            first = definition["description"].split(". ")[0].rstrip(".") + "."
            definition["configurations"][0]["profile"]["description"] = first + " " + definition["configurations"][0]["profile"]["description"]

        failures = self.run_gate(mutate_source=recycle)
        self.assertTrue(any(f.startswith("quality_audit:") and DEFINITION in f for f in failures), failures)

    def test_legacy_definitions_are_not_judged_by_the_quality_gate(self) -> None:
        offending = [f for f in self.run_gate() if f.startswith("quality_audit:") and DEFINITION not in f]
        self.assertEqual([], offending)


if __name__ == "__main__":  # pragma: no cover
    unittest.main()
