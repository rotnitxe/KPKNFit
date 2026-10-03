#!/usr/bin/env python3
"""The image brief is assembled from the ficha and never invented (scripts/catalog_v2_visual_brief.py)."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
MODULE = SCRIPTS / "catalog_v2_visual_brief.py"


def load_module():
    spec = importlib.util.spec_from_file_location("catalog_v2_visual_brief", MODULE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {MODULE}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["catalog_v2_visual_brief"] = module
    spec.loader.exec_module(module)
    return module


brief = load_module()

# A self-contained ficha: the behaviour under test must not depend on which definitions of the live
# catalogue happen to be CURATED today (they all will be, eventually).
FIXTURE_CORES = {
    "barbell": "Side view of an athlete frozen at the TOP of a fixture row. Both hands grip the barbell with an overhand grip.",
    "dumbbells": "Side view of an athlete frozen at the TOP of a fixture row. Each hand grips one dumbbell with palms facing each other.",
}
FIXTURE_FICHA = {
    "schemaVersion": 1,
    "familyId": "fixture_family",
    "definitions": {
        "fixture_row": {
            "status": "CURATED",
            "public": {"description": "Fixture.", "configurations": {}},
            "technique": {
                "identity": "Remo de prueba tumbado boca abajo sobre un banco elevado.",
                "keyPositions": ["Inicio: brazos extendidos.", "Punto alto: escapulas juntas."],
                "phases": [{"name": "Subida", "description": "Los codos suben hacia atras y arriba."}],
            },
            "visual": {
                "base": {
                    "camera": "Vista lateral pura.",
                    "phase": "Punto mas alto de la traccion.",
                    "orientation": "Boca abajo, cabeza a la izquierda.",
                    "contacts": "Pecho apoyado en el banco.",
                    "posture": "Columna neutra.",
                    "load": "Carga suspendida bajo el banco.",
                },
                "byImplement": {
                    "barbell": {"geometry": "Barra transversal bajo el banco."},
                    "dumbbells": {"geometry": "Una mancuerna a cada lado del banco."},
                },
                "forbidden": ["Atleta de pie.", "Carga apoyada en el suelo."],
                "qa": ["Esta boca abajo?", "Agarra la carga?", "Codos por detras del tronco?"],
                "promptCore": FIXTURE_CORES,
                "byVariant": {"fixture_row__dumbbells": {"difference": "Agarre neutro con las palmas enfrentadas."}},
            },
        },
        "fixture_legacy": {"status": "LEGACY", "public": {"description": "Legacy.", "configurations": {}}},
    },
}


def run(argv: list[str], fichas_dir: Path | None = None) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            code = brief.main(argv, fichas_dir=fichas_dir)
        except SystemExit as exit_request:
            code = int(exit_request.code or 0)
    return code, out.getvalue(), err.getvalue()


class VisualBriefTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls._directory = tempfile.TemporaryDirectory(prefix="kpkn-brief-test-")
        cls.fichas = Path(cls._directory.name)
        (cls.fichas / "fixture_family.json").write_text(json.dumps(FIXTURE_FICHA, ensure_ascii=False), encoding="utf-8")

    @classmethod
    def tearDownClass(cls) -> None:
        cls._directory.cleanup()

    def test_a_curated_definition_yields_one_brief_per_implement(self) -> None:
        code, out, _ = run(["fixture_row", "--json"], self.fichas)
        self.assertEqual(0, code)
        briefs = json.loads(out)
        self.assertEqual(["barbell", "dumbbells"], [item["equipment"] for item in briefs])
        for item in briefs:
            self.assertTrue(item["prompt"].isascii())
            self.assertEqual(brief.build_prompt(FIXTURE_CORES[item["equipment"]]), item["prompt"])
            self.assertGreaterEqual(len(item["rejectIf"]), 2)
            self.assertGreaterEqual(len(item["qa"]), 3)
            self.assertTrue(item["geometry"])
            self.assertEqual(len(brief.BASE_LABELS), len(item["base"]))
            self.assertEqual(FIXTURE_FICHA["definitions"]["fixture_row"]["technique"]["identity"], item["technique"]["identity"])
            self.assertEqual(2, len(item["technique"]["keyPositions"]))
            self.assertEqual([{"name": "Subida", "description": "Los codos suben hacia atras y arriba."}], item["technique"]["phases"])

    def test_text_output_carries_every_section_and_technique_stays_out_of_the_prompt(self) -> None:
        code, out, _ = run(["fixture_row", "--equipment", "barbell"], self.fichas)
        self.assertEqual(0, code)
        for section in ("== fixture_row x barbell", "PROMPT (GenerateImage, ASCII):", "GEOMETRIA:", "CAMARA:",
                        "CONTEXTO TECNICO", "RECHAZAR SI:", "QA (si/no, inspector limpio):", "VARIANTES"):
            self.assertIn(section, out)
        self.assertNotIn("dumbbells", out.split("QA (si/no")[0].split("EXERCISE:")[0])
        prompt_block, after_prompt = out.split("GEOMETRIA:", 1)
        self.assertNotIn("Remo de prueba", prompt_block)
        self.assertLess(after_prompt.index("QUE ES: Remo de prueba"), after_prompt.index("RECHAZAR SI:"))

    def test_a_legacy_definition_is_refused(self) -> None:
        code, _, err = run(["fixture_legacy"], self.fichas)
        self.assertEqual(2, code)
        self.assertIn("no visual brief yet", err)

    def test_unknown_definition_and_unknown_implement_fail(self) -> None:
        self.assertEqual(2, run(["no_such_definition"], self.fichas)[0])
        code, _, err = run(["fixture_row", "--equipment", "kettlebell"], self.fichas)
        self.assertEqual(2, code)
        self.assertIn("has no visual brief for equipment 'kettlebell'", err)

    def test_list_names_only_curated_pairs(self) -> None:
        code, out, _ = run(["--list"], self.fichas)
        self.assertEqual(0, code)
        rows = [line.split("\t") for line in out.splitlines()]
        self.assertEqual([["fixture_row", "barbell"], ["fixture_row", "dumbbells"]], rows)

    def test_fichas_dir_flag_reads_a_private_copy(self) -> None:
        code, out, _ = run(["--list", "--fichas-dir", str(self.fichas)])
        self.assertEqual(0, code)
        self.assertIn("fixture_row\tbarbell", out)

    def test_the_prompt_must_be_ascii(self) -> None:
        with self.assertRaises(brief.BriefError):
            brief.build_prompt("Un atleta con acentos y ñ")

    def test_live_reference_ficha_still_briefs(self) -> None:
        # Smoke test on the reference ficha of AUTHORING_FICHA.md, which is CURATED for good.
        code, out, _ = run(["seal_row", "--json"])
        self.assertEqual(0, code)
        self.assertEqual(["barbell", "dumbbells"], [item["equipment"] for item in json.loads(out)])


if __name__ == "__main__":
    unittest.main()
