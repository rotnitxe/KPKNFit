#!/usr/bin/env python3
"""Finish with ZERO registered sets: the finish sheet must say why saving is blocked (round-2 QA, 2026-10-02).

Product contract under test (WorkoutFinishController.FINISH_EMPTY_SESSION_GUIDANCE + WorkoutFinishHost): when the
session has no recorded sets the finish sheet shows

    Registra al menos una serie para terminar o abandona sin guardar.

(a polite live-region banner) and the save is blocked - no workout_logs row is created.  Round 1 had a silent
no-op (only a transient toast); this driver proves the banner is visible in the UiAutomator XML and the screenshot.

Flow (emulator only, synthetic fixture ``strength-empty-v27``):
  seed -> Home -> deep link to the ongoing squat workout (0 sets) -> Back -> exit dialog -> "Terminar hasta acá" ->
  finish sheet (confirm FAB visible) -> banner text present -> tap "Guardar y terminar entrenamiento" once ->
  sheet still open, nothing saved -> Room: workout_logs == 0, ongoing_workout holds no working set.

    python -X utf8 ui_finish_empty_session.py --flavor base
"""

from __future__ import annotations

import argparse

import fixtures
import uia
from qa_common import PREFLIGHT_STEPS, QaSession, UiShot, add_common_args, execute

EXIT_DIALOG_TITLE = "¿Qué deseas hacer?"
FINISH_PROMPT = "text:Terminar hasta acá"
FINISH_CONFIRM = "desc:Guardar y terminar entrenamiento"
BANNER_TEXT = "Registra al menos una serie para terminar o abandona sin guardar."
BANNER = "text:" + BANNER_TEXT
FIXTURE = "strength-empty-v27"

STEPS = (
    *PREFLIGHT_STEPS,
    "seed_strength_fixture",
    "home_ready",
    "open_workout_with_zero_sets",
    "open_exit_dialog",
    "finish_sheet_opens",
    "empty_session_banner_visible",
    "banner_in_screenshot_png",
    "confirm_does_not_save_and_sheet_stays",
    "room_no_log_no_working_sets",
    "main_activity_top_resumed",
)


def run(s: QaSession) -> None:
    r = s.rec
    r.declare(*STEPS)
    ctx: dict = {}
    s.standard_preflight((FIXTURE,))

    with r.step("seed_strength_fixture") as step:
        seeded = s.seed(FIXTURE)
        step.detail(f"fixture {seeded['fixture']['sha256'][:12]} pushed")

    with r.step("home_ready") as step:
        s.launch_main()
        shot = s.wait_home()
        step.detail(f"Home ring visible in {shot.name}")

    with r.step("open_workout_with_zero_sets") as step:
        s.deeplink(fixtures.WORKOUT_URI)
        shot = s.wait_ui(s.workout_surface_present, "workout-open", timeout=60, png=True, what="workout surface")
        tokens = uia.progress_tokens(shot.xml_text)
        step.check("1/2" not in tokens and "2/2" not in tokens and "✓" not in tokens,
                   f"the fixture workout already shows progress {tokens}; expected 0 registered sets")
        step.detail(f"workout surface visible with no registered set (badges {tokens}); no set was registered by this driver")

    with r.step("open_exit_dialog") as step:
        step.check(s.avd.ime_visible() is not True, "keyboard is visible; Back would only dismiss it")
        s.key("KEYCODE_BACK", "back-to-exit", settle=0.6)
        shot = s.wait_ui(lambda x: x.has(EXIT_DIALOG_TITLE) and x.present(FINISH_PROMPT), "exit-dialog", timeout=15,
                         png=True, what="exit dialog with 'Terminar hasta acá'")
        step.detail(f"exit dialog open ({shot.name})")

    with r.step("finish_sheet_opens") as step:
        s.tap(FINISH_PROMPT, "finish-up-to-here")
        shot = s.wait_ui(lambda x: x.present(FINISH_CONFIRM), "finish-sheet", timeout=30, png=True,
                         what="finish sheet (Guardar y terminar entrenamiento)")
        ctx["sheet"] = shot
        step.detail(f"finish sheet visible ({shot.name})")

    with r.step("empty_session_banner_visible") as step:
        shot = ctx["sheet"]
        if not shot.present(BANNER):
            # The banner sits near the top of the sheet; allow one more fresh dump in case the sheet was still animating.
            shot = s.wait_ui(lambda x: x.present(BANNER), "finish-sheet-banner", timeout=10, png=True,
                             what=f"banner text {BANNER_TEXT!r}")
        ctx["bannerShot"] = shot
        step.check(shot.present(BANNER), "banner text not found in the UiAutomator XML")
        step.check(shot.has(BANNER_TEXT), "banner text is not an exact text/content-desc label")
        step.detail(f"banner text present exactly in {shot.name} (xml {shot.xml_path.name})")
        step.evidence(xml=shot.xml_path.name, text=BANNER_TEXT, visibleSummary=shot.summary(14))

    with r.step("banner_in_screenshot_png") as step:
        shot = ctx["bannerShot"]
        png = shot.png_path or s.screenshot("finish-sheet-banner-png")
        step.check(png is not None and png.exists() and png.stat().st_size > 0, "no screenshot of the finish sheet was archived")
        step.detail(f"screenshot archived: {png.name} (open it to confirm the banner visually)")
        step.evidence(png=png.name, bytes=png.stat().st_size)

    with r.step("confirm_does_not_save_and_sheet_stays") as step:
        s.tap(FINISH_CONFIRM, "finish-confirm-while-empty", settle=2.0)
        after = s.capture_ui("after-confirm-empty", png=True)
        s.require_main_top(after)
        step.check(after.present(FINISH_CONFIRM) and after.present(BANNER),
                   "the finish sheet (or its banner) is gone after confirming an empty session; "
                   f"{s.diagnostics(after)}")
        step.detail(f"sheet and banner still visible after confirm ({after.name}): the save was refused, not silent")

    with r.step("room_no_log_no_working_sets") as step:
        capture = s.room("after-empty-confirm")
        summary = capture.summary
        step.check(summary["databaseVersion"] == 28, f"Room version {summary['databaseVersion']} != 28")
        step.check(summary["counts"].get("workout_logs") == 0, f"workout_logs != 0: {summary['counts'].get('workout_logs')}")
        working = ((summary.get("ongoing") or {}).get("completedSets")) or []
        step.check(not working, f"ongoing_workout holds working sets although none was registered: {working}")
        step.check(not summary.get("recentTargetExerciseLogs"), "a log for the target exercise exists")
        step.detail("workout_logs=0 and no working set in ongoing_workout")
        step.evidence(counts=summary["counts"], capture=capture.directory.name)

    with r.step("main_activity_top_resumed") as step:
        # Room capture force-stops the app: relaunch to prove MainActivity comes back and is top-resumed.
        s.launch_main()
        shot = s.capture_ui("final-top", png=True)
        s.require_main_top(shot)
        step.detail(f"MainActivity top-resumed: {shot.top_resumed}")


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    args = parser.parse_args(argv)
    return execute("finish-empty-session", args, run)


if __name__ == "__main__":
    raise SystemExit(main())
