#!/usr/bin/env python3
"""Strength flow: S1 -> S2 with ONE click per set, 1/2 then 2/2, and two sets after reopen.

Port of OLDROOT ``repeat-strength-final-owned.py`` onto the consolidation framework
(no hash pins; fixture and source hashes are recorded in the evidence instead).

Flow (emulator only, synthetic data, fixture ``strength-empty-v27``):
  seed -> Home -> deep link to the ongoing squat workout -> select S1 -> type 20 ->
  ONE tap on "Registrar serie" -> skip rest -> badge 1/2 -> type 22.5 -> ONE tap ->
  feedback/rest overlays -> badge 2/2 -> force-stop + relaunch + deep link -> badge 2/2 ->
  Room: exactly (set 0, 20 kg x 6) and (set 1, 22.5 kg x 6).

    python -X utf8 ui_strength_s1_s2.py --flavor base
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import time

import fixtures
import room_inspect
import uia
from qa_common import PREFLIGHT_STEPS, QaSession, UiShot, add_common_args, execute

REGISTER = "desc:Registrar serie"
SKIP_REST = "text:Saltar descanso"
FEEDBACK = "text:Registrar feedback"
S1_CHIP = "text:S1"
# Old drivers tapped the squat card label to bring the exercise badge back into view.
SQUAT_CARD_LABEL = "Sentadilla Trasera con Barra Baja · Barra"
# Rail card of the squat when it is clipped at the screen edge: the label is truncated (no " · Barra").
SQUAT_RAIL_CARD = "contains:text:Sentadilla Trasera con Barra Baja"

STEPS = (
    *PREFLIGHT_STEPS,
    "seed_strength_fixture",
    "home_ready",
    "open_workout",
    "s1_selected_and_weight_typed",
    "s1_register_single_click",
    "s1_progress_1_of_2",
    "s2_weight_typed_register_single_click",
    "s2_progress_2_of_2",
    "reopen_progress_2_of_2",
    "room_two_sets_persisted",
    "rom_null_for_squat_in_room",
    "one_click_per_set_proof",
)


def badge_state(shot: UiShot) -> list[str]:
    return uia.progress_tokens(shot.xml_text)


def settle_after_register(s: QaSession, label: str, *, rounds: int = 6) -> list[str]:
    """Dismiss the post-set overlays (feedback prompt, rest overlay) in whatever order they appear.

    Stops after two consecutive probes show neither overlay (overlays animate in, so one empty
    probe right after the tap is not conclusive).
    """
    handled: list[str] = []
    empty = 0
    for index in range(rounds):
        shot = s.capture_ui(f"{label}-settle{index + 1}")
        if shot.present(FEEDBACK):
            s.tap(FEEDBACK, f"{label}-feedback", shot=shot)
            handled.append("feedback")
            empty = 0
        elif shot.present(SKIP_REST):
            s.tap(SKIP_REST, f"{label}-skip-rest", shot=shot)
            handled.append("skip-rest")
            empty = 0
        else:
            empty += 1
            if empty >= 2:
                break
            time.sleep(1.5)
    return handled


def register_once(s: QaSession, label: str) -> dict:
    """One tap on Registrar serie; the IME is dismissed first whenever dumpsys proves it is shown.

    Tool fix (base AVD validation 2026-10-02, attempt 2): on the integrated build the UiAutomator tree keeps the
    "Registrar serie" FAB (bounds [1122,2089][1296,2263]) while the numeric keypad covers it, so "resolvable in the
    XML" no longer proves "reachable".  The attempt-1 tap landed on the keypad's "-" key (field became "20-") and
    nothing was registered.  The IME state is therefore always read from ``dumpsys input_method`` before the tap.
    """
    ime_record = s.dismiss_ime_if_shown(label)
    shot = s.capture_ui(label + "-pre-register")
    return {"tap": s.tap(REGISTER, label + "-register", shot=shot), "imeDismiss": ime_record}


def run(s: QaSession) -> None:
    r = s.rec
    r.declare(*STEPS)
    ctx: dict = {}
    s.standard_preflight(("strength-empty-v27",))
    identity = s.rec.inputs["fixtures"]["strength-empty-v27"]["identity"]["ongoing"]

    with r.step("seed_strength_fixture") as step:
        seeded = s.seed("strength-empty-v27")
        step.detail(f"fixture {seeded['fixture']['sha256'][:12]} pushed; grants={seeded['grants']}")
        step.evidence(deviceDbSha256=seeded["deviceDbSha256"])

    with r.step("home_ready") as step:
        s.launch_main()
        shot = s.wait_home()
        step.detail(f"Home ring visible in {shot.name}")

    with r.step("open_workout") as step:
        s.deeplink(fixtures.WORKOUT_URI)
        shot = s.wait_ui(s.workout_surface_present, "workout-open", timeout=60, png=True, what="workout surface")
        ctx["badgesBefore"] = badge_state(shot)
        step.detail(f"workout surface visible; badges before: {ctx['badgesBefore']}")
        step.evidence(badgesBefore=ctx["badgesBefore"])

    with r.step("s1_selected_and_weight_typed") as step:
        s.tap(S1_CHIP, "select-s1")
        typed = s.type_primary("20", "weight-s1")
        step.detail(f"typed 20; field now {typed['editTextsAfter']}")
        step.evidence(typed=typed)

    with r.step("s1_register_single_click") as step:
        outcome = register_once(s, "s1")
        handled = settle_after_register(s, "s1")
        ctx["s1Handled"] = handled
        step.detail(f"1 tap on Registrar serie; overlays handled: {handled}")
        step.evidence(**outcome, overlays=handled)

    with r.step("s1_progress_1_of_2") as step:
        shot = s.wait_ui(lambda x: "1/2" in badge_state(x), "s1-progress", timeout=20, png=True, what="progress badge 1/2")
        s.require_main_top(shot)
        ctx["afterS1Badges"] = badge_state(shot)
        step.check("2/2" not in ctx["afterS1Badges"], "one click advanced two sets (2/2 visible after S1)")
        step.detail(f"badges after S1: {ctx['afterS1Badges']}")

    with r.step("s2_weight_typed_register_single_click") as step:
        typed = s.type_primary("22.5", "weight-s2")
        outcome = register_once(s, "s2")
        handled = settle_after_register(s, "s2")
        step.detail(f"typed 22.5, 1 tap on Registrar serie; overlays handled: {handled}")
        step.evidence(typed=typed, **outcome, overlays=handled)

    def progress_done(shot: UiShot) -> bool:
        tokens = badge_state(shot)
        return "2/2" in tokens or "✓" in tokens

    with r.step("s2_progress_2_of_2") as step:
        shot = s.capture_ui("s2-progress-probe")
        if not progress_done(shot) and shot.has(SQUAT_CARD_LABEL):
            s.tap("text:" + SQUAT_CARD_LABEL, "s2-squat-card", shot=shot)
        shot = s.wait_ui(progress_done, "s2-progress", timeout=20, png=True, what="progress badge 2/2")
        s.require_main_top(shot)
        tokens = badge_state(shot)
        ctx["afterS2Badges"] = tokens
        step.detail(f"badges after S2: {tokens}" + ("" if "2/2" in tokens else " (all-done mark instead of 2/2; Room proves the count)"))

    with r.step("reopen_progress_2_of_2") as step:
        shot = s.cold_restart(fixtures.WORKOUT_URI, "reopen")
        navigated = None
        if not progress_done(shot):
            # Tool fix (final base pass, 2026-10-02): once the squat is complete the integrated build reopens the
            # workout on the NEXT incomplete exercise (Floor Press), leaving the squat only as a clipped rail card whose
            # label is truncated (no " · Barra" suffix).  Bring the squat back with a unique ``contains:`` match on
            # the rail card; the 2/2 / check assertion below stays strict and unchanged.
            if shot.has(SQUAT_CARD_LABEL):
                navigated = s.tap("text:" + SQUAT_CARD_LABEL, "reopen-squat-card", shot=shot)
            elif shot.present(SQUAT_RAIL_CARD):
                navigated = s.tap(SQUAT_RAIL_CARD, "reopen-squat-rail-card", shot=shot)
        shot = s.wait_ui(progress_done, "reopen-progress", timeout=30, png=True, what="progress badge 2/2 after reopen")
        s.require_main_top(shot)
        step.detail(f"after process restart badges: {badge_state(shot)}"
                    + ("" if navigated is None else f"; reopened on another exercise, squat rail card tapped once ({navigated['target']['matchedValue']!r})"))
        step.evidence(squatRailNavigation=navigated)

    with r.step("room_two_sets_persisted") as step:
        capture = s.room("final-s1-s2")
        step.check(capture.summary["databaseVersion"] == 28, f"Room version {capture.summary['databaseVersion']} != 28")
        sets = room_inspect.assert_two_squat_sets(capture.summary)
        step.detail(f"Room v28 holds exactly {sets}")
        step.evidence(sets=sets, capture=capture.directory.name, counts=capture.summary["counts"])

    with r.step("rom_null_for_squat_in_room") as step:
        # Round-2 check (2026-10-02): SetExecutionCard registers `rom` only when the exercise tracks it (trackRom).
        # The squat does not, so the persisted CompletedSet must carry rom = null (round 1 persisted rom = 100).
        db_file = room_inspect.db_path_of(capture.directory)
        connection = sqlite3.connect(f"file:{db_file.as_posix()}?mode=ro", uri=True)
        try:
            rows = [json.loads(raw) for (raw,) in connection.execute('SELECT "data" FROM "ongoing_workout"')]
        finally:
            connection.close()
        squat = []
        for state in rows:
            for key, value in (state.get("completedSets") or {}).items():
                if isinstance(value, dict) and not value.get("isWarmup") and fixtures.SQUAT_EXERCISE_ID in key:
                    squat.append({"key": key, "romKeyPresent": "rom" in value, "rom": value.get("rom"),
                                  "weight": value.get("weight"), "reps": value.get("reps")})
        step.check(len(squat) == 2, f"expected 2 persisted squat working sets in ongoing_workout JSON, found {len(squat)}")
        step.check(all(item["rom"] is None for item in squat), f"squat sets persisted a non-null rom: {squat}")
        step.detail("squat sets persisted rom = " + ", ".join("null" if item["romKeyPresent"] else "absent" for item in squat)
                    + " (round 1 persisted 100)")
        step.evidence(squatSets=squat, round1RomValue=100)

    with r.step("one_click_per_set_proof") as step:
        register_taps = [t for t in s.taps if t["selector"] == REGISTER]
        step.check(len(register_taps) == 2, f"expected exactly 2 Registrar serie taps (one per set), saw {len(register_taps)}")
        step.check("1/2" in ctx["afterS1Badges"] and "2/2" not in ctx["afterS1Badges"],
                   "badge after the first tap was not exactly 1/2")
        step.detail("2 sets registered with exactly 2 taps (1 per set); 1/2 then 2/2")
        step.evidence(registerTaps=[t["beforeCapture"] for t in register_taps])


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    args = parser.parse_args(argv)
    return execute("strength-s1-s2", args, run)


if __name__ == "__main__":
    raise SystemExit(main())
