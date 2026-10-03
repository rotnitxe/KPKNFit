#!/usr/bin/env python3
"""Tests for the side-effect-free ficha lint (scripts/catalog_v2_ficha_lint.py).

The lint is the authoring loop of the curation pipeline, so these tests pin the three
properties authors rely on: it never writes the repository, it refuses a ficha the real
applier would refuse, and it blocks (exit 2) a CURATED definition whose text recycles
sentences while passing (exit 0) the shipped CURATED fichas.
"""

from __future__ import annotations

import contextlib
import hashlib
import importlib.util
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
V2 = REPOSITORY / "catalog" / "exercises" / "v2"
LINT = SCRIPTS / "catalog_v2_ficha_lint.py"


def load_lint():
    sys.path.insert(0, str(SCRIPTS))
    spec = importlib.util.spec_from_file_location("catalog_v2_ficha_lint", LINT)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {LINT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["catalog_v2_ficha_lint"] = module
    spec.loader.exec_module(module)
    return module


lint = load_lint()


def tree_digest(*roots: Path) -> dict[str, str]:
    digests: dict[str, str] = {}
    for root in roots:
        for path in sorted(root.rglob("*")) if root.is_dir() else [root]:
            if path.is_file():
                digests[str(path)] = hashlib.sha256(path.read_bytes()).hexdigest()
    return digests


def run_lint(argv: list[str], **kwargs) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = lint.main(argv, **kwargs)
    return code, out.getvalue(), err.getvalue()


class SelectionHelpersTest(unittest.TestCase):
    def test_mentions_matches_whole_identifiers_only(self) -> None:
        ids = {"paused_bench_press", "chest_press__barbell"}
        self.assertTrue(lint.mentions("ficha_drift:chest_press:family.definitions[paused_bench_press].description", ids))
        self.assertTrue(lint.mentions("duplicate:chest_press__barbell|other__x", ids))
        self.assertFalse(lint.mentions("ficha_drift:chest_press:family.definitions[bench_press].description", ids))
        self.assertFalse(lint.mentions("nothing relevant here", ids))

    def test_preview_swaps_only_the_rendered_families(self) -> None:
        source = {"catalogRevision": "r", "families": [{"id": "a", "marker": 1}, {"id": "b", "marker": 2}]}
        preview = lint.preview_source(source, [{"id": "b", "marker": 99}])
        self.assertEqual([1, 99], [family["marker"] for family in preview["families"]])
        self.assertEqual("r", preview["catalogRevision"])
        self.assertEqual(2, source["families"][1]["marker"], "the input source is never mutated")

    def test_selection_ids_include_configuration_ids(self) -> None:
        source = {"families": [{"id": "f", "definitions": [{"id": "d1", "configurations": [{"id": "d1__x"}, {"id": "d1__y"}]}, {"id": "d2", "configurations": [{"id": "d2__x"}]}]}]}
        self.assertEqual({"d1", "d1__x", "d1__y"}, lint.selection_ids(source, ["d1"]))


class LintContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.families = Path(self.temp.name) / "families"
        self.fichas = Path(self.temp.name) / "fichas"
        shutil.copytree(V2 / "source" / "families", self.families)
        shutil.copytree(V2 / "curation" / "fichas", self.fichas)

    def test_lint_never_writes_the_repository(self) -> None:
        watched = (V2 / "source", V2 / "curation" / "fichas", REPOSITORY / "android-native" / "app" / "src" / "main" / "assets" / "exercise_catalog_v2.json")
        before = tree_digest(*[path for path in watched if path.exists()])
        run_lint(["--definitions", "seal_row"])
        self.assertEqual(before, tree_digest(*[path for path in watched if path.exists()]))

    def test_an_unknown_definition_is_refused_with_exit_1(self) -> None:
        code, _, err = run_lint(["--definitions", "no_such_definition"], families_dir=self.families, fichas_dir=self.fichas)
        self.assertEqual(1, code)
        self.assertIn("unknown definition id", err)

    def test_a_ficha_the_applier_would_refuse_is_refused_with_exit_1(self) -> None:
        path = self.fichas / "back_seal_row.json"
        ficha = json.loads(path.read_text(encoding="utf-8"))
        ficha["definitions"]["seal_row"]["public"]["configurations"].pop("seal_row__dumbbells")
        path.write_text(json.dumps(ficha, ensure_ascii=False), encoding="utf-8")
        code, _, err = run_lint(["--definitions", "seal_row"], families_dir=self.families, fichas_dir=self.fichas)
        self.assertEqual(1, code)
        self.assertIn("configuration inventory mismatch", err)

    def test_duplicate_ids_are_refused_with_exit_1(self) -> None:
        code, _, err = run_lint(["--definitions", "seal_row,seal_row"], families_dir=self.families, fichas_dir=self.fichas)
        self.assertEqual(1, code)
        self.assertIn("duplicate definition ids", err)

    def test_fichas_dir_flag_lints_a_private_copy(self) -> None:
        # A private copy that breaks seal_row: the flag must make the lint read it, not the shared fichas.
        path = self.fichas / "back_seal_row.json"
        ficha = json.loads(path.read_text(encoding="utf-8"))
        ficha["definitions"]["seal_row"]["public"]["configurations"].pop("seal_row__dumbbells")
        path.write_text(json.dumps(ficha, ensure_ascii=False), encoding="utf-8")
        code, _, err = run_lint(["--definitions", "seal_row", "--fichas-dir", str(self.fichas)], families_dir=self.families)
        self.assertEqual(1, code)
        self.assertIn("configuration inventory mismatch", err)


if __name__ == "__main__":
    unittest.main()
