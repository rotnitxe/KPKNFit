#!/usr/bin/env python3
"""Session editor drafts: RESTORE / SAVE / DISCARD plus process and background lifecycle.

Port of OLDROOT ``editor_drafts_avd.py`` + ``editor-drafts-final-owned.py`` (their reviewed-driver
SHA pin is dropped; the offline Room verifier is the ported ``room_verify.py``).

Common start: fixture ``normal-v27`` + audit preferences, the target editor key removed from
``session_editor_drafts.xml``, the unchanged editor opened and left once so the app itself writes its
separate ``session_editor_preferences.xml``; a "clean-preparation" Room snapshot is the baseline.

  RESTORE  type a unique marker in the Monday description -> autosave -> force-stop -> reopen: marker
           restored from the draft; Room still unchanged (DRAFTRESTORE).
  SAVE     marker -> Back -> "Guardar y salir" -> reopen shows the marker from Room, draft key gone
           (DRAFTCOMMIT).
  DISCARD  marker -> autosave -> force-stop -> reopen (draft restored) -> Back -> "Cerrar sin guardar"
           -> reopen: marker gone, draft key removed, Room unchanged (DRAFTDISCARD).

Lifecycle checks in every scenario: HOME + return keeps the editor state; force-stop cycles are the
process-death proof.

    python -X utf8 ui_editor_drafts.py --flavor base --scenario RESTORE
"""

from __future__ import annotations

import argparse
import time
import uuid
from pathlib import Path
from typing import Any

import editor_flow as ef
import fixtures
import room_verify
from evidence import StepFailure
from qa_common import PREFLIGHT_STEPS, QaSession, RoomShot, UiShot, add_common_args, execute
from qa_paths import PACKAGE

AUTOSAVE_WAIT = 2.6

COMMON = (
    *PREFLIGHT_STEPS, "seed_normal_fixture_clean_draft", "unchanged_editor_initializes_preferences",
    "baseline_snapshot_clean", "open_editor_and_enter_marker", "autosave_settled", "lifecycle_home_and_return",
)
TAIL = {
    "RESTORE": ("force_stop_reopen_restores_marker", "restored_state_settled", "final_snapshot", "verify_draftrestore"),
    "SAVE": ("exit_dialog_save_and_exit", "saved_returns_to_program", "force_stop_reopen_shows_saved_marker",
             "saved_state_settled", "final_snapshot", "verify_draftcommit"),
    "DISCARD": ("force_stop_before_discard", "dirty_snapshot_has_draft", "reopen_restores_marker_before_discard",
                "exit_dialog_discard", "discard_returns_to_program", "force_stop_reopen_marker_gone",
                "discarded_state_settled", "final_snapshot", "verify_draftdiscard"),
}


def make_marker() -> str:
    return f"KPKNQA-DRAFT-{uuid.uuid4().hex[:12].upper()}"


def marker_visible(shot: UiShot, marker: str) -> bool:
    return marker in shot.xml_text


def enter_description(s: QaSession, marker: str) -> None:
    shot = s.capture_ui("description-before-edit")
    if not shot.present(ef.DESCRIPTION_SELECTOR):
        raise StepFailure("target session description is not empty ('Añadir descripción' absent); refusing to overwrite it")
    s.tap(ef.DESCRIPTION_SELECTOR, "description-field", shot=shot, settle=0.5)
    s.avd.input_text(marker)
    s.wait_ui(lambda x: marker_visible(x, marker), "marker-visible", timeout=10, png=True, what=f"marker {marker} in the editor")


def settle_autosave(s: QaSession, label: str, marker: str, *, expect_marker: bool = True) -> UiShot:
    time.sleep(AUTOSAVE_WAIT)
    shot = s.capture_ui(label, png=True)
    if marker_visible(shot, marker) != expect_marker:
        raise StepFailure(f"marker {'disappeared from' if expect_marker else 'returned to'} the editor at {label}")
    return shot


def close_editor_dialog(s: QaSession, marker: str) -> UiShot:
    for attempt in (1, 2):
        s.key("KEYCODE_BACK", f"back-to-exit-dialog-{attempt}", settle=0.5)
        shot = s.capture_ui(f"exit-dialog-probe-{attempt}", png=True)
        if shot.present(ef.EXIT_DIALOG):
            return shot
        if not shot.present(ef.EDITOR_MARK) or not marker_visible(shot, marker):
            raise StepFailure("Back left the editor before showing the dirty-session dialog")
    raise StepFailure("two Back presses did not open the dirty-session dialog")


def wait_after_save(s: QaSession) -> UiShot:
    deadline = time.monotonic() + 30
    shot = None
    while time.monotonic() < deadline:
        shot = s.capture_ui("save-outcome")
        if shot.present("text:Reintentar") and shot.present(ef.EXIT_DIALOG):
            s.tap("text:Reintentar", "save-retry", shot=shot)
            continue
        if ef.program_page_present(shot) and not shot.present(ef.EXIT_DIALOG):
            return shot
        time.sleep(0.7)
    raise StepFailure("save did not return to the program page within 30s; editor data left in place")


def verify_offline(step: Any, scenario: str, before: RoomShot, after: RoomShot, marker: str | None,
                   reopened_xml: Path | None, out_dir: Path) -> dict[str, Any]:
    drafts_before = ef.prefs_path(before.directory, "session_editor_drafts.xml")
    drafts_after = ef.prefs_path(after.directory, "session_editor_drafts.xml")
    settings_before = ef.prefs_path(before.directory, "session_editor_preferences.xml")
    settings_after = ef.prefs_path(after.directory, "session_editor_preferences.xml")
    report = room_verify.verify(
        before.db_path, after.db_path, scenario,
        program_id=fixtures.PROGRAM_ID, week_id=fixtures.WEEK_ID, draft_session_id=fixtures.MONDAY_SESSION_ID,
        macro_index=0, meso_index=0, draft_prefs_before=drafts_before, draft_prefs_after=drafts_after,
        settings_before=settings_before, settings_after=settings_after, draft_marker=marker,
        reopened_ui_xml=reopened_xml,
    )
    payload = report.as_json()
    from evidence import write_json

    write_json(out_dir / f"verify-{scenario.lower()}.json", payload)
    bad = [c for c in payload["checks"] if c["status"] != "PASS"]
    step.evidence(result=payload["result"], checks=len(payload["checks"]), nonPass=bad)
    if payload["result"] == "FAIL":
        step.fail(f"{scenario}: " + "; ".join(f"{c['name']}={c['status']}" for c in bad))
    if payload["result"] != "PASS":
        step.not_run(f"{scenario} incomplete: " + "; ".join(f"{c['name']}={c['status']}" for c in bad))
    step.detail(f"{scenario}: {len(payload['checks'])} offline Room/preference checks PASS")
    return payload


def run(s: QaSession, scenario: str) -> None:
    r = s.rec
    r.declare(*COMMON, *TAIL[scenario])
    s.rec.extra["scenario"] = scenario
    marker = make_marker()
    s.rec.extra["marker"] = marker
    key = ef.draft_key()
    s.standard_preflight(("normal-v27",))

    with r.step("seed_normal_fixture_clean_draft") as step:
        seeded = s.seed("normal-v27")
        source = (fixtures.FIXTURES_DIR / fixtures.PREFS_DIR / "session_editor_drafts.xml").read_bytes()
        cleaned, removed = ef.clean_drafts_xml(source, key)
        clean_path = s.out / "clean-session_editor_drafts.xml"
        clean_path.write_bytes(cleaned)
        got = s.avd.run_as_push(clean_path, "shared_prefs/session_editor_drafts.xml", staging_name="clean-drafts")
        from evidence import sha256_bytes

        step.check(got == sha256_bytes(cleaned), "clean draft preferences did not copy exactly")
        step.detail(f"fixture pushed; {removed} stale draft entr{'y' if removed == 1 else 'ies'} for the target key removed")
        step.evidence(removedEntries=removed, cleanDraftsSha256=got, seed=seeded["fixture"]["sha256"])

    with r.step("unchanged_editor_initializes_preferences") as step:
        s.launch_main()
        s.wait_home()
        ef.open_editor(s)
        time.sleep(AUTOSAVE_WAIT)
        shot = s.capture_ui("unchanged-editor", png=True)
        step.check(not shot.present("text:Cambios sin guardar"), "unchanged editor unexpectedly became dirty")
        s.key("KEYCODE_BACK", "leave-unchanged-editor", settle=2.0)
        step.detail("editor opened and left without edits (app initializes its separate preferences file)")

    with r.step("baseline_snapshot_clean") as step:
        baseline = s.room("clean-preparation")
        prefs = ef.prefs_path(baseline.directory, "session_editor_drafts.xml")
        drafts = ef.parse_preferences_xml(prefs.read_bytes()) if prefs else {}
        step.check(key not in drafts, f"target draft key already present in the clean baseline: {key}")
        step.check(ef.prefs_path(baseline.directory, "session_editor_preferences.xml") is not None,
                   "separate session_editor_preferences.xml was not created by the app")
        session = ef.session_from_db(baseline.db_path)
        step.check(session.get("dayOfWeek") == 1 and not str(session.get("description") or "").strip(),
                   f"target session is not the empty-description Monday fixture: day={session.get('dayOfWeek')!r}")
        step.check(baseline.summary["databaseVersion"] == 28, f"Room version {baseline.summary['databaseVersion']} != 28")
        step.detail("baseline is clean: no target draft, separate prefs present, Monday description empty, Room v28")

    with r.step("open_editor_and_enter_marker") as step:
        ef.open_editor(s)
        enter_description(s, marker)
        step.detail(f"marker {marker} entered")

    with r.step("autosave_settled") as step:
        shot = settle_autosave(s, "after-autosave-edit", marker)
        step.detail(f"autosave window {AUTOSAVE_WAIT}s elapsed with the marker still visible ({shot.name})")

    with r.step("lifecycle_home_and_return") as step:
        s.key("KEYCODE_HOME", "home-key", settle=3.0)
        step.check(not s.avd.is_main_top_resumed(), "HOME did not background MainActivity")
        s.avd.shell("am", "start", "-W", "-f", "0x30000000", "-n", f"{PACKAGE}/.MainActivity", timeout=60)
        s.avd.wait_main_top_resumed()
        shot = s.wait_ui(lambda x: marker_visible(x, marker) and x.present(ef.EDITOR_MARK), "after-home-return", timeout=20,
                         png=True, what="editor with marker after HOME + return")
        step.detail(f"editor state survived background/foreground ({shot.name})")

    final: RoomShot
    if scenario == "RESTORE":
        with r.step("force_stop_reopen_restores_marker") as step:
            s.avd.force_stop(PACKAGE)
            shot = ef.open_editor(s)
            shot = s.wait_ui(lambda x: marker_visible(x, marker), "restored-editor", timeout=20, png=True,
                             what="draft marker restored after process death")
            ctx_reopened = shot.xml_path
            step.detail("marker restored from the draft after force-stop")
        with r.step("restored_state_settled") as step:
            shot = settle_autosave(s, "after-restored-editor-delay", marker)
            reopened_xml = shot.xml_path
        with r.step("final_snapshot") as step:
            final = s.room("draft-after")
            step.detail(f"snapshot {final.directory.name}")
        with r.step("verify_draftrestore") as step:
            verify_offline(step, "DRAFTRESTORE", baseline, final, marker, reopened_xml, s.out)

    elif scenario == "SAVE":
        with r.step("exit_dialog_save_and_exit") as step:
            close_editor_dialog(s, marker)
            s.tap("text:Guardar y salir", "save-and-exit")
            step.detail("tapped Guardar y salir")
        with r.step("saved_returns_to_program") as step:
            wait_after_save(s)
            time.sleep(AUTOSAVE_WAIT)
            step.detail("editor closed and program page visible")
        with r.step("force_stop_reopen_shows_saved_marker") as step:
            s.avd.force_stop(PACKAGE)
            ef.open_editor(s)
            s.wait_ui(lambda x: marker_visible(x, marker), "saved-editor", timeout=20, png=True, what="saved marker from Room")
            step.detail("marker present after process death (now from Room, not from a draft)")
        with r.step("saved_state_settled") as step:
            settle_autosave(s, "after-saved-editor-delay", marker)
        with r.step("final_snapshot") as step:
            final = s.room("draft-after")
            step.detail(f"snapshot {final.directory.name}")
        with r.step("verify_draftcommit") as step:
            verify_offline(step, "DRAFTCOMMIT", baseline, final, marker, None, s.out)

    else:  # DISCARD
        with r.step("force_stop_before_discard") as step:
            s.avd.force_stop(PACKAGE)
            step.detail("process killed with a dirty autosaved editor")
        with r.step("dirty_snapshot_has_draft") as step:
            dirty = s.room("draft-discard-before")
            prefs = ef.prefs_path(dirty.directory, "session_editor_drafts.xml")
            drafts = ef.parse_preferences_xml(prefs.read_bytes()) if prefs else {}
            step.check(key in drafts, "autosave did not persist the target draft before discard; not a discard proof")
            import room_verify as rv

            step.check(rv.canonical(ef.session_from_db(dirty.db_path)) == rv.canonical(ef.session_from_db(baseline.db_path)),
                       "Room changed before discard; refusing to classify the run as a discard proof")
            step.detail("draft key present, Room still equal to the baseline")
        with r.step("reopen_restores_marker_before_discard") as step:
            ef.open_editor(s)
            s.wait_ui(lambda x: marker_visible(x, marker), "restored-before-discard", timeout=20, png=True,
                      what="draft marker restored")
        with r.step("exit_dialog_discard") as step:
            close_editor_dialog(s, marker)
            s.tap("text:Cerrar sin guardar", "discard-without-saving")
        with r.step("discard_returns_to_program") as step:
            s.wait_ui(lambda x: ef.program_page_present(x) and not x.present(ef.EXIT_DIALOG), "returned-after-discard",
                      timeout=30, png=True, what="program page after discarding")
            time.sleep(AUTOSAVE_WAIT)
        with r.step("force_stop_reopen_marker_gone") as step:
            s.avd.force_stop(PACKAGE)
            ef.open_editor(s)
            shot = s.capture_ui("reopened-after-discard", png=True)
            step.check(not marker_visible(shot, marker), "discarded marker returned in the editor UI")
        with r.step("discarded_state_settled") as step:
            settle_autosave(s, "after-discarded-editor-delay", marker, expect_marker=False)
        with r.step("final_snapshot") as step:
            final = s.room("draft-discard-after")
            step.detail(f"snapshot {final.directory.name}")
        with r.step("verify_draftdiscard") as step:
            verify_offline(step, "DRAFTDISCARD", dirty, final, None, None, s.out)


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    parser.add_argument("--scenario", choices=tuple(TAIL), required=True)
    args = parser.parse_args(argv)
    return execute(f"editor-drafts-{args.scenario.lower()}", args, lambda session: run(session, args.scenario))


if __name__ == "__main__":
    raise SystemExit(main())
