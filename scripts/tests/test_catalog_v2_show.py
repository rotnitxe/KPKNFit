#!/usr/bin/env python3
"""Contract of the read-only authoring view (scripts/catalog_v2_show.py) over the real catalog."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
MODULE = SCRIPTS / "catalog_v2_show.py"


def load_module():
    spec = importlib.util.spec_from_file_location("catalog_v2_show", MODULE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {MODULE}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["catalog_v2_show"] = module
    spec.loader.exec_module(module)
    return module


show = load_module()


def run(argv: list[str]) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            code = show.main(argv)
        except SystemExit as exit_request:  # argparse errors
            code = int(exit_request.code or 0)
    return code, out.getvalue(), err.getvalue()


class ShowTest(unittest.TestCase):
    def test_a_definition_lists_every_configuration_with_its_involvement(self) -> None:
        code, out, _ = run(["seal_row"])
        self.assertEqual(0, code)
        self.assertIn("definition=seal_row", out)
        self.assertIn("config=seal_row__barbell", out)
        self.assertIn("config=seal_row__dumbbells", out)
        self.assertIn("P=", out)
        self.assertIn("joints:", out)
        self.assertNotIn("DESC:", out)

    def test_public_adds_the_user_facing_copy(self) -> None:
        code, out, _ = run(["seal_row", "--public"])
        self.assertEqual(0, code)
        self.assertIn("DESCRIPTION:", out)
        self.assertIn("SETUP:", out)
        self.assertIn("EXEC:", out)

    def test_family_shows_all_its_definitions(self) -> None:
        code, out, _ = run(["--family", "back_seal_row"])
        self.assertEqual(0, code)
        self.assertIn("definition=seal_row", out)

    def test_list_covers_the_whole_catalog_and_filters_by_status(self) -> None:
        code, out, _ = run(["--list"])
        self.assertEqual(0, code)
        rows = [line.split("\t") for line in out.splitlines()]
        self.assertEqual(228, len(rows))
        self.assertEqual(len({row[1] for row in rows}), len(rows))
        curated = [row for row in run(["--list", "--status", "CURATED"])[1].splitlines()]
        self.assertTrue(any("\tseal_row\tCURATED\t" in line for line in curated))
        self.assertTrue(all("\tCURATED\t" in line for line in curated))

    def test_unknown_definition_fails(self) -> None:
        code, _, err = run(["no_such_definition"])
        self.assertEqual(2, code)
        self.assertIn("not found", err)

    def test_no_selector_is_a_usage_error(self) -> None:
        code, _, _ = run([])
        self.assertEqual(2, code)


if __name__ == "__main__":
    unittest.main()
