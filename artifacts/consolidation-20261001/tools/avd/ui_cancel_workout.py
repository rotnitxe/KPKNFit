#!/usr/bin/env python3
"""Workout cancellation ("Abandonar sin guardar"): Room delete acknowledged before leaving,
no partial strength log, no partial cardio (log, GPS snapshot files, running service).

Two modes (one run each):

  --mode strength   fixture finish-v2-normal-v27: register S1 (20 kg), then Back -> Abandonar sin guardar.
  --mode cardio     fixture cardio-two-series-ui-v27: start GPS (synthetic fixes), then Back -> Abandonar sin guardar.

Product contract under test (WorkoutViewModel.discardWorkout): the ongoing_workout row is deleted and
flushed to Room BEFORE the terminal state / navigation, GPS runtime is stopped and its session
snapshot cleared, and no workout_logs row is created.

Ordering evidence is bounded by UiAutomator latency: the Room snapshot is taken as soon as the UI is
observed to have left the workout (first dump after the tap).  A deletion that lagged navigation by
more than that window would show up as a surviving ongoing row.  The strict-ordering proof stays in the
unit/instrumented tests and is reported NOT_RUN here (optional step).

    python -X utf8 ui_cancel_workout.py --flavor base --mode strength
    python -X utf8 ui_cancel_workout.py --flavor base --mode cardio
"""

from __future__ import annotations

import argparse
import time

import cardio_profiles as cp
import fixtures
import uia
from cardio_nav import ensure_cardio_surface
from gps_tools import GeoFeeder, app_workout_services, cardio_gps_files, gps_service_running, prepare_gps, synthetic_point
from qa_common import PREFLIGHT_STEPS, QaSession, UiShot, add_common_args, execute
from ui_strength_s1_s2 import register_once, settle_after_register

EXIT_DIALOG_TITLE = "¿Qué deseas hacer?"
ABANDON = "text:Abandonar sin guardar"
COMMON_STEPS = (
    *PREFLIGHT_STEPS, "seed_fixture", "home_ready", "open_workout",
)
MODE_STEPS = {
    "strength": ("register_s1_before_cancel", "room_before_cancel_has_progress", "reopen_workout"),
    "cardio": ("gps_prepared", "gps_started_running", "gps_snapshot_files_present_before_cancel"),
}
TAIL_STEPS = (
    "open_exit_dialog", "abandon_without_saving", "left_workout_surface",
    "room_after_cancel_no_ongoing_no_log", "no_partial_log_or_media", "ordering_delete_before_exit_strict",
)


def exit_dialog_open(shot: UiShot) -> bool:
    return shot.has(EXIT_DIALOG_TITLE) and shot.present(ABANDON)


def left_workout(shot: UiShot) -> bool:
    return not exit_dialog_open(shot) and not shot.has(EXIT_DIALOG_TITLE) and not s_workout_surface(shot)


def s_workout_surface(shot: UiShot) -> bool:
    return any(shot.has(label) for label in ("Registrar serie", "Iniciar GPS", "Pausar GPS", "Reanudar GPS",
                                              "Finalizar y registrar", "Ver ejercicio/Fotos"))


def run_strength(s: QaSession, ctx: dict) -> None:
    r = s.rec
    with r.step("register_s1_before_cancel") as step:
        s.type_primary("20", "weight-s1")
        register_once(s, "s1")
        settle_after_register(s, "s1")
        shot = s.wait_ui(lambda x: "1/2" in uia.progress_tokens(x.xml_text), "s1-progress", timeout=20, png=True,
                         what="badge 1/2")
        step.detail(f"S1 registered; badges {uia.progress_tokens(shot.xml_text)}")
    with r.step("room_before_cancel_has_progress") as step:
        before = s.room("before-cancel")
        ctx["before"] = before.summary
        sets = [x for x in (before.summary["ongoing"] or {}).get("completedSets", [])
                if x["exerciseId"] == fixtures.SQUAT_EXERCISE_ID]
        step.check(len(sets) == 1, f"expected 1 persisted working set before cancel, found {sets}")
        step.detail(f"ongoing_workout holds {len(sets)} working set; logs={before.summary['counts'].get('workout_logs')}")
        step.evidence(sets=sets, counts=before.summary["counts"])
    with r.step("reopen_workout") as step:
        shot = s.cold_restart(fixtures.WORKOUT_URI, "reopen")
        step.detail(f"workout restored from Room after process restart ({shot.name})")


def run_cardio(s: QaSession, ctx: dict) -> None:
    r = s.rec
    feeder: GeoFeeder | None = None
    with r.step("gps_prepared") as step:
        step.evidence(**prepare_gps(s.avd))
        lon, lat = synthetic_point(0)
        s.avd.geo_fix(lon, lat)
        step.detail("location enabled; first synthetic fix sent (emulator only)")
    with r.step("gps_started_running") as step:
        feeder = GeoFeeder(s.avd)
        feeder.start()
        ctx["feeder"] = feeder
        # The integrated build orders cardio parts after strength parts: open the cardio card from the roadmap strip.
        ensure_cardio_surface(s, "cardio")
        shot = s.wait_ui(lambda x: x.has("Iniciar GPS"), "cardio-ready", timeout=30, png=True, what="cardio card with Iniciar GPS")
        step.check(cp.exercise_label_present(shot.tokens), "cardio exercise name not visible")
        s.tap("text:Iniciar GPS", "start-gps")
        running = s.wait_ui(lambda x: x.has("Pausar GPS") and x.present("contains:Señal GPS activa"), "gps-running",
                            timeout=45, png=True, what="GPS running (Pausar GPS + Señal GPS activa)")
        step.detail(f"GPS running ({running.name}); service running={gps_service_running(s.avd)}")
    with r.step("gps_snapshot_files_present_before_cancel") as step:
        deadline = time.monotonic() + 30
        files: list = []
        while time.monotonic() < deadline:
            files = cardio_gps_files(s.avd)
            if files:
                break
            time.sleep(2)
        ctx["gpsFilesBefore"] = files
        step.check(bool(files), "no files/cardio-gps snapshot appeared within 30s of starting GPS; cannot prove cleanup")
        step.check(gps_service_running(s.avd), "CardioGpsForegroundService is not running although GPS shows recording")
        step.detail(f"{len(files)} GPS snapshot file(s) before cancel")
        step.evidence(files=files)


def run(s: QaSession, mode: str) -> None:
    r = s.rec
    fixture = "finish-v2-normal-v27" if mode == "strength" else "cardio-two-series-ui-v27"
    r.declare(*COMMON_STEPS, *MODE_STEPS[mode], *TAIL_STEPS)
    ctx: dict = {"mode": mode}
    s.rec.extra["mode"] = mode
    s.standard_preflight((fixture,))

    with r.step("seed_fixture") as step:
        seeded = s.seed(fixture)
        step.detail(f"{fixture} {seeded['fixture']['sha256'][:12]} pushed")
    with r.step("home_ready") as step:
        s.launch_main()
        s.wait_home()
        step.detail("Home visible")
    with r.step("open_workout") as step:
        s.deeplink(fixtures.WORKOUT_URI)
        shot = s.wait_ui(s.workout_surface_present, "workout-open", timeout=60, png=True, what="workout surface")
        step.detail(f"workout surface visible ({shot.name})")

    try:
        (run_strength if mode == "strength" else run_cardio)(s, ctx)

        with r.step("open_exit_dialog") as step:
            step.check(s.avd.ime_visible() is not True, "keyboard is visible; Back would only dismiss it")
            s.key("KEYCODE_BACK", "back-to-exit")
            shot = s.wait_ui(exit_dialog_open, "exit-dialog", timeout=15, png=True, what="exit dialog with Abandonar sin guardar")
            step.detail("exit dialog open with the four choices" if shot.has("Pausar y salir") else "exit dialog open")

        with r.step("abandon_without_saving") as step:
            tap_time = s.avd.device_time_ms()
            ctx["tapTimeMs"] = tap_time
            s.tap(ABANDON, "abandon", settle=0.5)
            step.detail(f"tapped Abandonar sin guardar at device time {tap_time}")

        with r.step("left_workout_surface") as step:
            shot = s.wait_ui(left_workout, "after-abandon", timeout=45, png=True, what="workout surface gone after cancel")
            ctx["leftAtMs"] = s.avd.device_time_ms()
            step.detail(f"workout surface gone {ctx['leftAtMs'] - ctx['tapTimeMs']} ms after the tap; top={shot.top_resumed}")
            step.evidence(msAfterTap=ctx["leftAtMs"] - ctx["tapTimeMs"], visible=shot.summary(12))
            if mode == "cardio":
                deadline = time.monotonic() + 15
                while gps_service_running(s.avd) and time.monotonic() < deadline:
                    time.sleep(1)
                step.check(not gps_service_running(s.avd), "GPS foreground service still running after cancel")
                step.evidence(gpsServiceRunningAfter=False)
            # Added in the base AVD validation: voice / rest-timer / cardio GPS services must all be gone after cancel
            # (observed BEFORE the Room capture, which force-stops the app).
            deadline = time.monotonic() + 15
            services = app_workout_services(s.avd)
            while services and time.monotonic() < deadline:
                time.sleep(1)
                services = app_workout_services(s.avd)
            step.check(not services, f"workout services still alive 15s after cancel: {services}")
            step.evidence(workoutServicesAfterCancel=services)
        feeder = ctx.get("feeder")
        if feeder:
            feeder.stop()
            r.note(f"synthetic geo fixes sent: {feeder.sent}")

        with r.step("room_after_cancel_no_ongoing_no_log") as step:
            if mode == "cardio":
                time.sleep(3)  # GPS snapshot deletion is queued on the persistence lane
            after = s.room("after-cancel", extra_files=())
            summary = after.summary
            step.check(summary["databaseVersion"] == 28, f"Room version {summary['databaseVersion']} != 28")
            step.check(summary["counts"].get("ongoing_workout") == 0 and summary["ongoing"] is None,
                       f"ongoing_workout survived the cancel: count={summary['counts'].get('ongoing_workout')}")
            step.detail(f"ongoing_workout=0 in capture {after.directory.name}")
            ctx["after"] = after

        with r.step("no_partial_log_or_media") as step:
            after = ctx["after"]
            baseline = ctx.get("before") or {"counts": {"workout_logs": 0, "workout_media": 0}}
            logs, media = after.summary["counts"].get("workout_logs"), after.summary["counts"].get("workout_media")
            step.check(logs == baseline["counts"].get("workout_logs", 0) == 0, f"workout_logs changed or non-zero: {logs}")
            step.check(media == baseline["counts"].get("workout_media", 0), f"workout_media changed: {media}")
            step.check(not after.summary["recentTargetExerciseLogs"], "a partial log for the target exercise exists")
            if mode == "cardio":
                leftovers = [f for f in after.manifest.get("filesTree", []) if f["path"].startswith("files/cardio-gps/")]
                step.check(not leftovers, f"cardio GPS snapshot file(s) survived cancel: {leftovers}")
                step.check(not gps_service_running(s.avd), "GPS foreground service running after cancel")
                step.detail("no workout_logs row, no completed cardio set, no files/cardio-gps snapshot, service stopped")
            else:
                step.detail("no workout_logs row, no media, no partial log")

        with r.step("ordering_delete_before_exit_strict", optional=True) as step:
            step.not_run("strict delete-before-navigation ordering cannot be sampled from the host faster than UiAutomator "
                         "latency; covered by WorkoutLifecycleDurabilityInstrumentedTest and unit tests (see README)")
    finally:
        feeder = ctx.get("feeder")
        if feeder:
            feeder.stop()


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    parser.add_argument("--mode", choices=("strength", "cardio"), required=True)
    args = parser.parse_args(argv)
    return execute(f"cancel-{args.mode}", args, lambda session: run(session, args.mode))


if __name__ == "__main__":
    raise SystemExit(main())
