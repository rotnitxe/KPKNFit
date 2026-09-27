#!/usr/bin/env python3
"""Regression tests for the `tate_press` axis roster and the brief/source sync.

Every test drives the production gate (`scripts/catalog_v2_gate.py`) through its
real `source_gate()` entry point, so the assertions observe actual validation
results and never a stubbed return value. Negative cases substitute temporary
`SOURCE` / `BRIEFS` paths, and the temporary fixtures are derived from the real
repository source and briefs (current truth) with one targeted mutation each,
instead of hard-coding production answers.

Scope notes that keep these tests durable:

* No test reads orchestration/execution artifacts under `.opencode`.
* No test asserts an absolute global failure count, so repairing unrelated
  note families later cannot break this file.
* The historical `tate_press__cable` configuration is NOT part of the accepted
  identity. Its brief entry is stale, and the generic validators must keep
  rejecting both a filler singleton axis and a reintroduced brief entry.
"""

from __future__ import annotations

import copy
import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any, Callable


SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
GATE = SCRIPTS / "catalog_v2_gate.py"
# Fixtures never touch the repository: temporary files can be redirected with
# KPKN_TEST_TEMP_DIR to keep runs inside an approved agent workspace on Windows.
TEMP_OVERRIDE = os.environ.get("KPKN_TEST_TEMP_DIR")

TATE_PRESS = "tate_press"
TATE_PRESS_DUMBBELLS = "tate_press__dumbbells"
TATE_PRESS_CABLE = "tate_press__cable"
PAUSED_BENCH_PRESS = "paused_bench_press"
PAUSED_BENCH_PRESS_BARBELL = "paused_bench_press__barbell"
REVERSE_CURL = "reverse_curl"
KATANA_EXTENSION = "katana_extension"

# The nine pre-existing brief/axis discrepancies closed by this patch, expressed
# as the identifiers the gate emits for them.  Later note-family repairs are
# unrelated and deliberately absent from this list.
NINE_KNOWN_DISCREPANCIES = (
    f"editorial_definition_mismatch:{PAUSED_BENCH_PRESS}",
    f"editorial_field_mismatch:{PAUSED_BENCH_PRESS_BARBELL}:techniqueSummary",
    f"editorial_field_mismatch:{PAUSED_BENCH_PRESS_BARBELL}:variantRationale",
    f"editorial_field_mismatch:{PAUSED_BENCH_PRESS_BARBELL}:setupCues",
    f"editorial_definition_mismatch:{REVERSE_CURL}",
    f"editorial_definition_mismatch:{KATANA_EXTENSION}",
    f"editorial_configuration_inventory_mismatch:{TATE_PRESS}",
    "editorial_configuration_inventory_global_mismatch",
    f"hierarchy_order:{TATE_PRESS}:[]",
)


def temporary_directory() -> tempfile.TemporaryDirectory:
    root = Path(TEMP_OVERRIDE) if TEMP_OVERRIDE and Path(TEMP_OVERRIDE).is_dir() else None
    return tempfile.TemporaryDirectory(dir=str(root) if root else None)


def load_gate():
    spec = importlib.util.spec_from_file_location("catalog_v2_gate_under_test", GATE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {GATE}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gate = load_gate()


def load_compiler_roster() -> dict[str, list[str]]:
    """The authoritative axis roster the compiler itself applies."""
    if str(SCRIPTS) not in sys.path:
        sys.path.insert(0, str(SCRIPTS))
    import build_catalog_v2_complete  # noqa: WPS433 - resolved from SCRIPTS

    return build_catalog_v2_complete.AXIS_ORDER_OVERRIDES


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def find_definition(source: dict[str, Any], family_id: str, definition_id: str) -> dict[str, Any]:
    for family in source["families"]:
        if family["id"] != family_id:
            continue
        for definition in family["definitions"]:
            if definition["id"] == definition_id:
                return definition
    raise AssertionError(f"{definition_id} not found in {family_id}")


class TatePressAxisGateTest(unittest.TestCase):
    """`tate_press` has no option axes and exactly one configuration."""

    def setUp(self) -> None:
        self._source_path = gate.SOURCE
        self._briefs_path = gate.BRIEFS
        self.addCleanup(self._restore_paths)

    def _restore_paths(self) -> None:
        gate.SOURCE = self._source_path
        gate.BRIEFS = self._briefs_path

    def _run_gate_against(
        self,
        mutate_source: Callable[[dict[str, Any]], None] | None = None,
        mutate_briefs: Callable[[dict[str, Any]], None] | None = None,
    ) -> list[str]:
        """Run the real `source_gate()` over temporary copies of the real files."""
        source = copy.deepcopy(read_json(self._source_path))
        briefs = copy.deepcopy(read_json(self._briefs_path))
        if mutate_source is not None:
            mutate_source(source)
        if mutate_briefs is not None:
            mutate_briefs(briefs)
        with temporary_directory() as raw:
            workspace = Path(raw)
            source_file = workspace / "catalog_v2.json"
            briefs_file = workspace / "editorial_briefs.json"
            source_file.write_text(
                json.dumps(source, ensure_ascii=False), encoding="utf-8"
            )
            briefs_file.write_text(
                json.dumps(briefs, ensure_ascii=False), encoding="utf-8"
            )
            gate.SOURCE = source_file
            gate.BRIEFS = briefs_file
            return gate.source_gate()

    # --- positive: the roster encodes the accepted identity -----------------

    def test_roster_value_matches_current_tate_press_axes(self) -> None:
        self.assertIn(TATE_PRESS, gate.EXPECTED_AXIS_ORDER)
        source_axes = find_definition(
            read_json(self._source_path), "triceps_tate_press", TATE_PRESS
        )["optionAxes"]
        self.assertEqual(gate.EXPECTED_AXIS_ORDER[TATE_PRESS], source_axes)
        self.assertEqual(gate.EXPECTED_AXIS_ORDER[TATE_PRESS], [])

    def test_roster_agrees_with_compiler_overrides(self) -> None:
        overrides = load_compiler_roster()
        self.assertIn(TATE_PRESS, overrides)
        self.assertEqual(
            gate.EXPECTED_AXIS_ORDER[TATE_PRESS], overrides[TATE_PRESS]
        )

    def test_current_tate_press_record_is_accepted(self) -> None:
        """Real files: `tate_press` must not fail any editorial or axis check."""
        failures = gate.source_gate()
        related = [
            failure
            for failure in failures
            if failure.startswith(("editorial_", "hierarchy_order", "singleton_axis"))
            and TATE_PRESS in failure
        ]
        self.assertEqual(related, [])

    def test_current_brief_inventory_matches_source_for_tate_press(self) -> None:
        source = read_json(self._source_path)
        briefs = read_json(self._briefs_path)
        source_ids = [
            configuration["id"]
            for configuration in find_definition(
                source, "triceps_tate_press", TATE_PRESS
            )["configurations"]
        ]
        brief_ids = sorted(
            briefs["definitions"][TATE_PRESS].get("configurations", {})
        )
        self.assertEqual(sorted(source_ids), brief_ids)
        self.assertNotIn(TATE_PRESS_CABLE, brief_ids)

    def test_six_synced_brief_fields_equal_current_source(self) -> None:
        """The brief fields this patch synchronised must track the canonical copy."""
        source = read_json(self._source_path)
        briefs = read_json(self._briefs_path)
        paused = find_definition(source, "chest_press", PAUSED_BENCH_PRESS)
        paused_profile = paused["configurations"][0]["profile"]
        reverse = find_definition(source, "elbow_flexion_biceps_curl", REVERSE_CURL)
        katana = find_definition(source, "triceps_katana_extension", KATANA_EXTENSION)
        brief_paused = briefs["definitions"][PAUSED_BENCH_PRESS]
        brief_paused_config = brief_paused["configurations"][PAUSED_BENCH_PRESS_BARBELL]
        self.assertEqual(brief_paused["description"], paused["description"])
        for field in ("techniqueSummary", "variantRationale", "setupCues"):
            with self.subTest(field=field):
                self.assertEqual(
                    brief_paused_config[field], paused_profile[field]
                )
        self.assertEqual(
            briefs["definitions"][REVERSE_CURL]["description"], reverse["description"]
        )
        self.assertEqual(
            briefs["definitions"][KATANA_EXTENSION]["description"],
            katana["description"],
        )

    # --- negative: the generic validators must stay active ------------------

    def test_gate_still_rejects_singleton_axis_on_tate_press(self) -> None:
        def introduce_singleton(source: dict[str, Any]) -> None:
            definition = find_definition(source, "triceps_tate_press", TATE_PRESS)
            definition["optionAxes"] = ["implement"]
            for configuration in definition["configurations"]:
                configuration["selectedOptions"] = {"implement": "dumbbells"}

        failures = self._run_gate_against(mutate_source=introduce_singleton)
        self.assertIn(f"singleton_axis:{TATE_PRESS}:implement", failures)

    def test_gate_still_rejects_hierarchy_drift_on_tate_press(self) -> None:
        def introduce_two_implements(source: dict[str, Any]) -> None:
            definition = find_definition(source, "triceps_tate_press", TATE_PRESS)
            definition["optionAxes"] = ["implement"]
            original = definition["configurations"][0]
            original["selectedOptions"] = {"implement": "dumbbells"}
            second = copy.deepcopy(original)
            second["id"] = TATE_PRESS_CABLE
            second["displaySummary"] = "cable"
            second["selectedOptions"] = {"implement": "cable"}
            definition["configurations"] = [original, second]

        failures = self._run_gate_against(mutate_source=introduce_two_implements)
        self.assertIn(f"hierarchy_order:{TATE_PRESS}:['implement']", failures)
        self.assertNotIn(f"singleton_axis:{TATE_PRESS}:implement", failures)

    def test_gate_still_rejects_a_reintroduced_cable_brief_entry(self) -> None:
        def reintroduce_cable_brief(briefs: dict[str, Any]) -> None:
            configurations = briefs["definitions"][TATE_PRESS]["configurations"]
            stale = copy.deepcopy(configurations[TATE_PRESS_DUMBBELLS])
            stale["description"] = (
                "Con polea, con las palmas al frente y los codos en abanico, la carga "
                "empuja desde el pecho mezclando press y extensión."
            )
            configurations[TATE_PRESS_CABLE] = stale

        failures = self._run_gate_against(mutate_briefs=reintroduce_cable_brief)
        self.assertIn(
            f"editorial_configuration_inventory_mismatch:{TATE_PRESS}", failures
        )
        self.assertIn("editorial_configuration_inventory_global_mismatch", failures)

    def test_gate_still_rejects_a_stale_brief_field(self) -> None:
        """A drifted brief field must keep failing byte-for-byte comparison."""

        def drift_field(briefs: dict[str, Any]) -> None:
            briefs["definitions"][PAUSED_BENCH_PRESS]["description"] += " "

        failures = self._run_gate_against(mutate_briefs=drift_field)
        self.assertIn(f"editorial_definition_mismatch:{PAUSED_BENCH_PRESS}", failures)

    # --- the nine closed discrepancies -------------------------------------

    def test_nine_known_discrepancies_absent_on_current_files(self) -> None:
        failures = set(gate.source_gate())
        remaining = sorted(set(NINE_KNOWN_DISCREPANCIES) & failures)
        self.assertEqual(remaining, [])

    def test_no_editorial_or_axis_failure_for_the_four_synced_definitions(self) -> None:
        """No brief/axis failure may reference the four definitions of this patch.

        Scoped by failure prefix and id on purpose: it must not depend on the
        unrelated global failure count, so later note-family repairs are safe.
        """
        watched = (PAUSED_BENCH_PRESS, PAUSED_BENCH_PRESS_BARBELL, REVERSE_CURL,
                   KATANA_EXTENSION, TATE_PRESS, TATE_PRESS_CABLE)
        offending = sorted(
            failure
            for failure in set(gate.source_gate())
            if failure.startswith(("editorial_", "hierarchy_order", "singleton_axis"))
            and any(token in failure for token in watched)
        )
        self.assertEqual(offending, [])


if __name__ == "__main__":  # pragma: no cover
    unittest.main()
