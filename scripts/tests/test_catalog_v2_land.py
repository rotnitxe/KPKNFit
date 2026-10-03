#!/usr/bin/env python3
"""The landing chain runs the documented scripts in a fixed order and stops at the first failure."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
MODULE = SCRIPTS / "catalog_v2_land.py"


def load_module():
    spec = importlib.util.spec_from_file_location("catalog_v2_land", MODULE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {MODULE}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["catalog_v2_land"] = module
    spec.loader.exec_module(module)
    return module


land = load_module()


def run(argv: list[str], failing: str | None = None) -> tuple[int, list[list[str]], str]:
    executed: list[list[str]] = []

    def runner(command) -> int:
        executed.append(list(command))
        return 3 if failing and command[0] == failing else 0

    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = land.main(argv, runner=runner)
    return code, executed, out.getvalue() + err.getvalue()


class LandTest(unittest.TestCase):
    def test_runs_every_documented_script_in_order_with_the_selected_definitions(self) -> None:
        code, executed, out = run(["--definitions", "pull_up, rack_chin"])
        self.assertEqual(0, code)
        self.assertEqual(
            [
                ["catalog_v2_sources.py", "check", "--definitions", "pull_up,rack_chin"],
                ["catalog_v2_apply_fichas.py", "--only-definitions=pull_up,rack_chin"],
                ["merge_catalog_v2_families.py"],
                ["catalog_v2_gate.py", "--strict"],
                ["catalog_v2_quality_audit.py", "--strict", "--definitions=pull_up,rack_chin"],
                ["compile_exercise_catalog_v2.py", "--write"],
                ["compile_exercise_catalog_v2.py", "--check"],
                ["catalog_v2_pin_sha.py"],
            ],
            executed,
        )
        self.assertIn("LAND OK: pull_up,rack_chin", out)

    def test_no_pin_leaves_the_pins_alone(self) -> None:
        _, executed, _ = run(["--definitions", "pull_up", "--no-pin"])
        self.assertNotIn(["catalog_v2_pin_sha.py"], executed)

    def test_stops_at_the_first_failure_and_names_the_step(self) -> None:
        code, executed, out = run(["--definitions", "pull_up"], failing="catalog_v2_gate.py")
        self.assertEqual(3, code)
        self.assertEqual("catalog_v2_gate.py", executed[-1][0])
        self.assertNotIn("compile_exercise_catalog_v2.py", [command[0] for command in executed])
        self.assertIn("LAND FAILED at step 'gate'", out)

    def test_assets_are_only_written_after_gate_and_audit_pass(self) -> None:
        names = [name for name, _ in land.steps("a", pin=True)]
        self.assertLess(names.index("gate"), names.index("compile-write"))
        self.assertLess(names.index("audit"), names.index("compile-write"))
        self.assertLess(names.index("compile-write"), names.index("compile-check"))
        self.assertLess(names.index("compile-check"), names.index("pin-sha"))

    def test_requires_definitions(self) -> None:
        with self.assertRaises(SystemExit):
            with contextlib.redirect_stderr(io.StringIO()):
                land.main(["--definitions", " , "], runner=lambda command: 0)


if __name__ == "__main__":
    unittest.main()
