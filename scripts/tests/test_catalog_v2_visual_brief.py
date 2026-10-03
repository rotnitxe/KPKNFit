#!/usr/bin/env python3
"""The image brief is assembled from the ficha and never invented (scripts/catalog_v2_visual_brief.py)."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import sys
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


def run(argv: list[str]) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            code = brief.main(argv)
        except SystemExit as exit_request:
            code = int(exit_request.code or 0)
    return code, out.getvalue(), err.getvalue()


class VisualBriefTest(unittest.TestCase):
    def test_a_curated_definition_yields_one_brief_per_implement(self) -> None:
        code, out, _ = run(["seal_row", "--json"])
        self.assertEqual(0, code)
        briefs = json.loads(out)
        self.assertEqual(["barbell", "dumbbells"], [item["equipment"] for item in briefs])
        for item in briefs:
            self.assertTrue(item["prompt"].isascii())
            self.assertTrue(item["prompt"].startswith(brief.STYLE_HEADER))
            self.assertIn("seal row", item["prompt"])
            self.assertGreaterEqual(len(item["rejectIf"]), 2)
            self.assertGreaterEqual(len(item["qa"]), 3)
            self.assertTrue(item["geometry"])
            self.assertEqual(len(brief.BASE_LABELS), len(item["base"]))

    def test_text_output_carries_every_section(self) -> None:
        code, out, _ = run(["seal_row", "--equipment", "barbell"])
        self.assertEqual(0, code)
        for section in ("== seal_row x barbell", "PROMPT (GenerateImage, ASCII):", "GEOMETRIA:", "CAMARA:", "RECHAZAR SI:", "QA (si/no, inspector limpio):"):
            self.assertIn(section, out)
        self.assertNotIn("dumbbells", out.split("QA (si/no")[0].split("EXERCISE:")[0])

    def test_a_legacy_definition_is_refused(self) -> None:
        code, _, err = run(["bench_press"])
        self.assertEqual(2, code)
        self.assertIn("no visual brief yet", err)

    def test_unknown_definition_and_unknown_implement_fail(self) -> None:
        self.assertEqual(2, run(["no_such_definition"])[0])
        code, _, err = run(["seal_row", "--equipment", "kettlebell"])
        self.assertEqual(2, code)
        self.assertIn("has no visual brief for equipment 'kettlebell'", err)

    def test_list_names_only_curated_pairs(self) -> None:
        code, out, _ = run(["--list"])
        self.assertEqual(0, code)
        rows = [line.split("\t") for line in out.splitlines()]
        self.assertIn(["seal_row", "barbell"], rows)
        self.assertNotIn("bench_press", {row[0] for row in rows})

    def test_the_prompt_must_be_ascii(self) -> None:
        with self.assertRaises(brief.BriefError):
            brief.build_prompt("Un atleta con acentos y ñ")


if __name__ == "__main__":
    unittest.main()
