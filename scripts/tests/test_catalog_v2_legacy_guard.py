#!/usr/bin/env python3
"""The retired template generators can not run by accident.

They wrote exercise copy and anatomy from pattern-level templates; running one
after the fichas exist would overwrite curated work with templates. These tests
keep three promises:

* none of the eight generators is left where it used to live (`scripts/`);
* every executable generator refuses to start unless the operator opts in, and it
  does so *before* touching anything;
* moving them one directory down did not break their repository-root resolution.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
LEGACY = SCRIPTS / "legacy"
if str(LEGACY) not in sys.path:
    sys.path.insert(0, str(LEGACY))

import _legacy_guard  # noqa: E402

GENERATORS = (
    "build_catalog_v2_complete",
    "build_catalog_v2_pilot",
    "curaduria_v3_transform",
    "curaduria_v4_descripciones",
    "curaduria_v4_mejoras",
    "curaduria_v5_catalogo_profundo",
    "curaduria_v6_catalogo_editorial",
    "seed_catalog_editorial_briefs",
)
# Pure data modules: no entry point, nothing to guard.
DATA_ONLY = {"curaduria_v4_descripciones"}
# One-time, idempotent migrations of the re-curation itself; they are safe to re-run.
MIGRATIONS = {"migrate_f1_retire_fields", "migrate_f1_fichas_skeleton"}
EXECUTABLE = tuple(name for name in GENERATORS if name not in DATA_ONLY)


def environment_without_ack() -> dict[str, str]:
    return {key: value for key, value in os.environ.items() if key != _legacy_guard.ENV_VAR}


class GeneratorsAreRetiredTest(unittest.TestCase):
    def test_no_generator_remains_in_the_scripts_root(self) -> None:
        for name in GENERATORS:
            with self.subTest(script=name):
                self.assertFalse((SCRIPTS / f"{name}.py").exists())
                self.assertTrue((LEGACY / f"{name}.py").is_file())

    def test_every_executable_generator_refuses_to_run_without_opt_in(self) -> None:
        for name in EXECUTABLE:
            with self.subTest(script=name):
                result = subprocess.run(
                    [sys.executable, str(LEGACY / f"{name}.py")],
                    cwd=REPOSITORY,
                    env=environment_without_ack(),
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                    timeout=120,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("retired template-based generator", result.stderr)
                self.assertIn("catalog_v2_apply_fichas.py", result.stderr)

    def test_a_wrong_acknowledgement_is_not_enough(self) -> None:
        with mock.patch.dict(os.environ, {_legacy_guard.ENV_VAR: "yes"}):
            with self.assertRaises(SystemExit):
                _legacy_guard.require_explicit_opt_in("anything.py")

    def test_the_exact_acknowledgement_lets_the_script_continue(self) -> None:
        with mock.patch.dict(os.environ, {_legacy_guard.ENV_VAR: _legacy_guard.ACK_VALUE}):
            self.assertIsNone(_legacy_guard.require_explicit_opt_in("anything.py"))

    def test_no_new_legacy_script_can_skip_the_guard(self) -> None:
        """Any executable script dropped into scripts/legacy must open its entry point with the guard
        (or be a known one-time migration)."""
        guarded_entry = re.compile(
            r'if __name__ == "__main__":\s*\n'
            r"\s+from _legacy_guard import require_explicit_opt_in\s*\n"
            r"\s*\n?"
            r"\s+require_explicit_opt_in\(__file__\)"
        )
        for path in sorted(LEGACY.glob("*.py")):
            if path.stem == "_legacy_guard" or path.stem in MIGRATIONS:
                continue
            text = path.read_text(encoding="utf-8")
            if '__name__ == "__main__"' not in text:
                continue
            with self.subTest(script=path.name):
                self.assertRegex(text, guarded_entry)


class RepositoryRootTest(unittest.TestCase):
    def test_moved_scripts_still_resolve_the_repository_root(self) -> None:
        """`parents[N]` of scripts/legacy/<x>.py must be the repository root: N == 2."""
        pattern = re.compile(r"^ROOT = Path\(__file__\)\.resolve\(\)\.parents\[(\d+)\]", re.MULTILINE)
        for path in sorted(LEGACY.glob("*.py")):
            match = pattern.search(path.read_text(encoding="utf-8"))
            if match is None:
                continue
            with self.subTest(script=path.name):
                self.assertEqual(path.resolve().parents[int(match.group(1))], REPOSITORY)


if __name__ == "__main__":  # pragma: no cover
    unittest.main()
