#!/usr/bin/env python3
"""The SHA pin sites must stay findable: each pins exactly one hash, and re-pinning rewrites only that hash."""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
MODULE = SCRIPTS / "catalog_v2_pin_sha.py"


def load_module():
    spec = importlib.util.spec_from_file_location("catalog_v2_pin_sha", MODULE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {MODULE}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["catalog_v2_pin_sha"] = module
    spec.loader.exec_module(module)
    return module


pin = load_module()
FRESH = "0" * 64


class PinSitesTest(unittest.TestCase):
    def test_every_pin_site_exists_and_pins_exactly_one_hash(self) -> None:
        self.assertEqual(3, len(pin.PINS))
        for path, pattern in pin.PINS:
            with self.subTest(path=path.name):
                self.assertTrue(path.is_file(), path)
                self.assertEqual(1, len(pattern.findall(pin.read(path))))

    def test_rewriting_changes_only_the_hash_and_keeps_line_endings(self) -> None:
        for path, pattern in pin.PINS:
            with self.subTest(path=path.name):
                text = pin.read(path)
                rewritten = pattern.sub(lambda found: f"{found.group(1)}{FRESH}{found.group(3)}", text, count=1)
                self.assertEqual(len(text), len(rewritten))
                self.assertEqual(text.count("\r\n"), rewritten.count("\r\n"))
                changed = [index for index, (a, b) in enumerate(zip(text, rewritten)) if a != b]
                self.assertTrue(changed and changed[-1] - changed[0] < 64, "only the 64-character hash may change")


if __name__ == "__main__":
    unittest.main()
