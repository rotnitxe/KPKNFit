"""AwakeKeeper (run_instrumentation): periodic KEYCODE_WAKEUP + user-activity poke, errors counted but never fatal, stops promptly."""

from __future__ import annotations

import sys
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from run_instrumentation import AwakeKeeper  # noqa: E402


class _StubAvd:
    def __init__(self, fail_first: int = 0) -> None:
        self.codes: list[str] = []
        self.pokes = 0
        self.fail_first = fail_first
        self.calls = 0
        self.seen = threading.Event()

    def keyevent(self, code: str) -> None:
        self.calls += 1
        if self.calls <= self.fail_first:
            raise RuntimeError("adb hiccup")
        self.codes.append(code)
        self.seen.set()

    def poke_user_activity(self) -> None:
        self.pokes += 1


class AwakeKeeperTests(unittest.TestCase):
    def test_sends_wakeup_and_stops(self) -> None:
        avd = _StubAvd()
        keeper = AwakeKeeper(avd, interval=0.01)  # type: ignore[arg-type]
        keeper.start()
        self.assertTrue(avd.seen.wait(2.0))
        keeper.stop()
        self.assertFalse(keeper.is_alive())
        self.assertGreaterEqual(keeper.sent, 1)
        self.assertTrue(all(code == "KEYCODE_WAKEUP" for code in avd.codes))
        self.assertIsNone(keeper.error)
        # KEYCODE_WAKEUP alone does not reset the screen-off timer of an awake display: every cycle must also poke it.
        self.assertGreaterEqual(avd.pokes, 1)
        self.assertEqual(keeper.pokes, avd.pokes)

    def test_transient_failure_is_recorded_but_does_not_stop_the_thread(self) -> None:
        avd = _StubAvd(fail_first=1)
        keeper = AwakeKeeper(avd, interval=0.01)  # type: ignore[arg-type]
        keeper.start()
        self.assertTrue(avd.seen.wait(2.0))
        keeper.stop()
        self.assertIn("adb hiccup", keeper.error or "")
        self.assertGreaterEqual(keeper.sent, 1)


if __name__ == "__main__":
    unittest.main()
