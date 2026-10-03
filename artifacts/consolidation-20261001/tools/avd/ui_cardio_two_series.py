#!/usr/bin/env python3
"""Two-series GPS cardio flow on the integrated build (synthetic coordinates only).

Port of OLDROOT ``run_cardio_two_series_ui.py`` (stepwise operator CLI) into one automated driver.
Fixture ``cardio-two-series-ui-v27`` ("Carrera GPS QA dos series", series ids qa-cardio-series-0/1).

  S1: start GPS, tap Serie 2 while running AND while paused (S1 must be retained) -> durable GPS
      snapshot keyed <program>::<session>::<start>::<exercise>::<series> -> force-stop -> Room
      checkpoint paused-s1 -> restart recovers the paused S1 with the same snapshot key ->
      resume/pause -> Finalizar y registrar -> Cancelar keeps S1 paused -> register S1 -> Room after-s1
      (only <exercise>_0 completed, cursor at S1 index 1).
  S2: relaunch (S1 "Registrada", S2 ready) -> GPS for S2 (distinct snapshot key) -> register S2 ->
      Room after-s2 (both completions) -> relaunch -> readback -> Room after-s2-reopened.

Evidence boundary: emulator geo fixes only (adb emu geo fix); no physical GPS accuracy, no acoustic
voice path.

    python -X utf8 ui_cardio_two_series.py --flavor base
"""

from __future__ import annotations

import argparse
import json
import re
import time
from typing import Any
from xml.etree import ElementTree

import cardio_profiles as cp
import fixtures
from cardio_nav import dismiss_finish_sheet, ensure_cardio_surface, on_cardio_surface, reveal
import room_inspect
from evidence import StepFailure, sha256_bytes
from gps_tools import GeoFeeder, gps_service_running, prepare_gps, synthetic_point
from qa_common import PREFLIGHT_STEPS, QaSession, UiShot, add_common_args, execute

FIXTURE = "cardio-two-series-ui-v27"
STEPS = (
    *PREFLIGHT_STEPS, "seed_cardio_fixture", "gps_prepared", "home_ready", "open_cardio_workout", "profile_cardio_ready",
    "start_gps_s1", "s2_tap_while_running_keeps_s1", "pause_gps_s1", "s2_tap_while_paused_keeps_s1",
    "gps_snapshot_s1_present", "room_checkpoint_paused_s1", "restart_recovers_paused_s1",
    "gps_snapshot_s1_same_key_after_restart", "resume_pause_open_confirm_cancel", "cancel_keeps_s1_paused",
    "room_checkpoint_paused_s1_after_cancel", "restart_register_s1", "profile_after_s1", "room_checkpoint_after_s1",
    "restart_s2_ready_start_gps", "gps_snapshot_s2_distinct_key", "register_s2", "profile_after_s2",
    "room_checkpoint_after_s2", "restart_readback_after_s2", "room_checkpoint_after_s2_reopened",
    "km_tile_formatted_not_raw_double", "geo_feed_health",
)


def wait_profile(s: QaSession, profile: str, label: str, *, timeout: float = 45.0) -> UiShot:
    """Poll fresh dumps until the UI satisfies ``profile`` (never repeats a tap)."""
    deadline = time.monotonic() + timeout
    attempt, problem, shot = 0, None, None
    while True:
        attempt += 1
        shot = s.capture_ui(f"{label}-{attempt:02d}", png=(attempt == 1 and profile != "visible") or None)
        problem = cp.profile_problem(shot.tokens, profile)
        if problem is None:
            return shot
        if "'Finalizar y registrar' missing" in problem and attempt <= 3:
            # the primary action sits below the fold on this AVD (see cardio_nav.reveal); scroll instead of tapping
            from cardio_nav import card_swipe_up
            s.avd.swipe(*card_swipe_up(shot.xml_text))
            time.sleep(1.0)
            continue
        if time.monotonic() >= deadline:
            raise StepFailure(f"UI profile {profile!r} not observed after {attempt} fresh captures: {problem}; {s.diagnostics(shot)}")
        time.sleep(1.0)


def read_gps_snapshot(s: QaSession, identity: dict[str, Any], series: int, *, timeout: float = 40.0) -> dict[str, Any]:
    key = room_inspect.expected_gps_session_key(identity, series)
    deadline = time.monotonic() + timeout
    while True:
        for pending in (False, True):
            relative = room_inspect.gps_snapshot_relative_path(key, pending)
            if s.avd.run_as_exists(relative, "-f"):
                payload = s.avd.run_as_cat(relative)
                if payload:
                    try:
                        snapshot = json.loads(payload.decode("utf-8"))
                    except (UnicodeDecodeError, json.JSONDecodeError) as error:
                        raise StepFailure(f"GPS snapshot {relative} unreadable: {error}") from error
                    if snapshot.get("sessionKey") != key:
                        raise StepFailure(f"GPS snapshot {relative} has sessionKey {snapshot.get('sessionKey')!r}, expected {key!r}")
                    (s.out / f"gps-snapshot-s{series + 1}-{int(time.time() * 1000)}.json").write_bytes(payload)
                    return {"series": series, "key": key, "relative": relative, "sha256": sha256_bytes(payload),
                            "bytes": len(payload), "pending": pending}
        if time.monotonic() >= deadline:
            raise StepFailure(f"GPS snapshot for series {series + 1} did not appear within {timeout:.0f}s (key {key})")
        time.sleep(2.0)


def room_checkpoint(s: QaSession, label: str, checkpoint: str, ctx: dict[str, Any]) -> dict[str, Any]:
    shot = s.room(label)
    state = room_inspect.cardio_state(shot.db_path)
    problem = cp.checkpoint_problem(state, checkpoint, fixtures.PROGRAM_ID, fixtures.MONDAY_SESSION_ID)
    if problem:
        raise StepFailure(f"Room checkpoint {checkpoint}: {problem} (capture {shot.directory.name})")
    ongoing = state["ongoing"]
    identity = {k: ongoing[k] for k in ("programId", "sessionId", "startTimeMs")}
    previous = ctx.get("identity")
    if previous and previous != identity:
        raise StepFailure(f"workout identity changed across captures: {previous} -> {identity}")
    ctx["identity"] = identity
    return {"capture": shot.directory.name, "identity": identity, "completed": sorted(ongoing["cardioCompletedSets"]),
            "timer": ongoing.get("cardioTimerState"), "cursor": ongoing.get("activeSetIndex"),
            "workoutLogCount": state["workoutLogCount"]}


def restart(s: QaSession, label: str) -> UiShot:
    """Cold restart + deep link, then reach the cardio card.

    The integrated build orders cardio parts after the strength parts and resumes the first incomplete step, so the
    workout reopens on the Floor Press approach card unless a cardio timer is protected; the cardio card is then
    opened through the roadmap strip exactly as a user would (``cardio_nav.ensure_cardio_surface``).  After both
    series are registered the GPS buttons are gone, so the surface is recognised by the Serie 1 / Serie 2 chips.
    """
    s.cold_restart(fixtures.WORKOUT_URI, label,
                   surface=lambda shot: on_cardio_surface(shot.tokens) or s.workout_surface_present(shot)
                   or shot.has("RESUMEN DE ENTRENAMIENTO"))
    dismiss_finish_sheet(s, label)
    return ensure_cardio_surface(s, label)


def run(s: QaSession) -> None:
    r = s.rec
    r.declare(*STEPS, optional=("geo_feed_health",))
    ctx: dict[str, Any] = {}
    feeder: GeoFeeder | None = None
    s.standard_preflight((FIXTURE,))
    ctx["identity"] = None
    fixture_identity = s.rec.inputs["fixtures"][FIXTURE]["identity"]["ongoing"]
    initial_identity = {k: fixture_identity[k] for k in ("programId", "sessionId", "startTimeMs")}
    try:
        with r.step("seed_cardio_fixture") as step:
            seeded = s.seed(FIXTURE)
            step.detail(f"fixture {seeded['fixture']['sha256'][:12]} pushed")
        with r.step("gps_prepared") as step:
            step.evidence(**prepare_gps(s.avd))
            lon, lat = synthetic_point(0)
            s.avd.geo_fix(lon, lat)
            feeder = GeoFeeder(s.avd)
            feeder.start()
            step.detail("location enabled, permissions granted, synthetic geo feed running")
        with r.step("home_ready") as step:
            s.launch_main()
            s.wait_home()
        with r.step("open_cardio_workout") as step:
            s.deeplink(fixtures.WORKOUT_URI)
            s.wait_ui(s.workout_surface_present, "cardio-surface", timeout=60, png=True, what="cardio workout surface")
        with r.step("profile_cardio_ready") as step:
            ensure_cardio_surface(s, "open")
            shot = wait_profile(s, "cardio-ready", "cardio-ready")
            step.detail(f"cardio-ready ({shot.name})")

        with r.step("start_gps_s1") as step:
            s.tap("text:Iniciar GPS", "start-gps-s1")
            shot = wait_profile(s, "gps-running", "gps-running-s1")
            step.detail(f"GPS running ({shot.name}); service={gps_service_running(s.avd)}")
        with r.step("s2_tap_while_running_keeps_s1") as step:
            s.tap("text:Serie 2", "tap-s2-running")
            shot = wait_profile(s, "gps-running", "still-running-s1", timeout=15)
            step.detail("S1 card retained and still running after the Serie 2 tap")
        with r.step("pause_gps_s1") as step:
            s.tap("text:Pausar GPS", "pause-gps-s1")
            wait_profile(s, "gps-paused", "gps-paused-s1")
        with r.step("s2_tap_while_paused_keeps_s1") as step:
            s.tap("text:Serie 2", "tap-s2-paused")
            wait_profile(s, "gps-paused", "still-paused-s1", timeout=15)
            step.detail("S1 card retained and still paused after the Serie 2 tap")
        with r.step("gps_snapshot_s1_present") as step:
            record = read_gps_snapshot(s, initial_identity, 0)
            ctx["gps0"] = record
            step.detail(f"snapshot {record['relative']} ({record['bytes']} bytes) carries the exact S1 execution key")
            step.evidence(**record)
        with r.step("room_checkpoint_paused_s1") as step:
            info = room_checkpoint(s, "paused-s1", "paused-s1", ctx)
            step.detail(f"Room v28: S1 paused timer {info['timer']}, no completions")
            step.evidence(**info)
        with r.step("restart_recovers_paused_s1") as step:
            restart(s, "recover-paused")
            shot = wait_profile(s, "gps-paused", "recovered-paused")
            step.detail(f"paused S1 recovered after process death ({shot.name})")
        with r.step("gps_snapshot_s1_same_key_after_restart") as step:
            record = read_gps_snapshot(s, ctx["identity"], 0)
            step.check(record["key"] == ctx["gps0"]["key"], "GPS execution identity changed across the restart")
            step.detail(f"same key after restart ({record['relative']})")

        with r.step("resume_pause_open_confirm_cancel") as step:
            s.tap("text:Reanudar GPS", "resume-gps")
            wait_profile(s, "gps-running", "resumed")
            s.tap("text:Pausar GPS", "pause-again")
            wait_profile(s, "gps-paused", "paused-again")
            s.tap("text:Finalizar y registrar", "open-confirm", shot=reveal(s, "text:Finalizar y registrar", "open-confirm"))
            wait_profile(s, "confirm-cardio", "confirm-open")
            s.tap("text:Cancelar", "cancel-confirm")
            wait_profile(s, "after-cancel", "after-cancel")
        with r.step("cancel_keeps_s1_paused") as step:
            s.tap("text:Serie 2", "cancel-s2")
            wait_profile(s, "gps-paused", "cancel-kept-s1", timeout=15)
            step.detail("Cancelar left S1 paused and the Serie 2 tap did not switch series")
        with r.step("room_checkpoint_paused_s1_after_cancel") as step:
            info = room_checkpoint(s, "cancel-snapshot", "paused-s1", ctx)
            step.evidence(**info)

        with r.step("restart_register_s1") as step:
            restart(s, "register-s1")
            wait_profile(s, "gps-paused", "before-s1-save")
            s.tap("text:Finalizar y registrar", "s1-confirm", shot=reveal(s, "text:Finalizar y registrar", "s1-confirm"))
            wait_profile(s, "confirm-cardio", "s1-confirm-open")
            s.tap("text:Registrar", "save-s1")
        with r.step("profile_after_s1") as step:
            shot = wait_profile(s, "after-s1", "after-s1", timeout=60)
            step.detail(f"S1 'Registrada', S2 ready ({shot.name})")
        with r.step("room_checkpoint_after_s1") as step:
            info = room_checkpoint(s, "after-s1", "after-s1", ctx)
            step.detail(f"only {info['completed']} persisted; cursor {info['cursor']}")
            step.evidence(**info)

        with r.step("restart_s2_ready_start_gps") as step:
            restart(s, "s2-ready")
            wait_profile(s, "after-s1", "s2-ready")
            s.tap("text:Iniciar GPS", "start-s2")
            wait_profile(s, "gps-running", "gps-running-s2")
        with r.step("gps_snapshot_s2_distinct_key") as step:
            record = read_gps_snapshot(s, ctx["identity"], 1)
            step.check(record["key"] != ctx["gps0"]["key"], "S2 reuses S1's GPS execution key")
            step.check(record["relative"] != ctx["gps0"]["relative"], "S2 reuses S1's snapshot file")
            step.detail(f"S2 snapshot {record['relative']} differs from S1")
            step.evidence(**record)
        with r.step("register_s2") as step:
            s.tap("text:Finalizar y registrar", "finish-s2", shot=reveal(s, "text:Finalizar y registrar", "finish-s2"))
            wait_profile(s, "confirm-cardio", "confirm-s2")
            s.tap("text:Registrar", "save-s2")
            # The last series closes the exercise: this build then shows the post-exercise feedback card
            # (technical-quality slider + "Registrar feedback"); accept it with the defaults exactly once.
            feedback_deadline = time.monotonic() + 25
            while time.monotonic() < feedback_deadline:
                probe = s.capture_ui("post-s2-feedback-probe")
                if probe.present("text:Registrar feedback"):
                    s.tap("text:Registrar feedback", "post-s2-feedback", shot=probe, settle=2.5)
                    step.detail("post-exercise feedback card accepted with its defaults")
                    feedback_deadline = max(feedback_deadline, time.monotonic() + 15)
                    continue
                if cp.profile_problem(probe.tokens, "after-s2") is None:
                    break
                if dismiss_finish_sheet(s, "post-s2"):
                    step.detail("finish summary sheet (opened because the cardio card is the last roadmap item) closed with Volver")
                    break
                time.sleep(1.0)
        with r.step("profile_after_s2") as step:
            shot = wait_profile(s, "after-s2", "both-saved", timeout=60)
            step.detail(f"both series 'Registrada' ({shot.name})")
        with r.step("room_checkpoint_after_s2") as step:
            info = room_checkpoint(s, "after-s2-before-reopen", "after-s2", ctx)
            step.evidence(**info)
        with r.step("restart_readback_after_s2") as step:
            restart(s, "s2-reopened")
            wait_profile(s, "after-s2", "s2-readback")
        with r.step("room_checkpoint_after_s2_reopened") as step:
            info = room_checkpoint(s, "after-s2-reopened", "after-s2", ctx)
            step.detail(f"both completions {info['completed']} survive a real reopen")
            step.evidence(**info)
    finally:
        if feeder:
            feeder.stop()
            r.extra["geoFixesSent"] = feeder.sent
            if feeder.error:
                r.extra["geoFeedError"] = feeder.error
    with r.step("km_tile_formatted_not_raw_double") as step:
        # Round-2 check (2026-10-02): the "Km" tile must show the formatted distance ("0.11 km"), never the raw
        # Double.toString() of a restored CompletedSet.distanceKm (round 1 showed e.g. "0.10811738104249106").
        formatted, raw = {}, {}
        for xml_path in sorted(s.ui_dir.glob("*.xml")):
            try:
                root = ElementTree.fromstring(xml_path.read_text(encoding="utf-8", errors="replace"))
            except ElementTree.ParseError:
                continue
            for node in root.iter("node"):
                text = (node.get("text") or "").strip()
                if re.fullmatch(r"\d+\.\d{2} km", text):
                    formatted.setdefault(text, xml_path.name)
                elif re.fullmatch(r"\d+\.\d{3,}( km)?", text):
                    raw.setdefault(text, xml_path.name)
        step.check(not raw, f"raw (unformatted) distance text visible in the UI: {raw}")
        step.check(bool(formatted), "no formatted distance ('N.NN km') text was ever visible; the Km tile was not observed")
        step.detail(f"formatted distance texts seen: {sorted(formatted)}; raw doubles seen: none")
        step.evidence(formatted=formatted, raw=raw)
    with r.step("geo_feed_health", optional=True) as step:
        if feeder is None:
            step.not_run("feeder never started")
        step.check(not feeder.error, f"geo feed error: {feeder.error}")
        step.detail(f"{feeder.sent} synthetic fixes sent")


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    args = parser.parse_args(argv)
    return execute("cardio-two-series", args, run)


if __name__ == "__main__":
    raise SystemExit(main())
