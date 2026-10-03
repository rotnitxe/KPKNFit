#!/usr/bin/env python3
"""Session editor transfer APPEND / REPLACE / CREATE: real editor UI -> Room -> real rematerialization.

Port of OLDROOT ``editor-transfer-final-owned.py``.  Per scenario (fixture ``normal-v27``, clean draft):

  snapshot(before) -> program -> Monday "Día 1" editor -> Transferir (+ Reemplazar for REPLACE) ->
  pick the Wednesday (APPEND/REPLACE) or Tuesday (CREATE) target -> Preparar transferencia -> Back ->
  Guardar y salir -> snapshot(after) -> androidTest SessionEditorTransferMaterializationAvdTest
  (-e scenario X, real PlanMaterializer) -> snapshot(rematerialized) -> ported offline verifier
  (``room_verify``: payload counts/contents, ids, overrides, source/Friday untouched, stable after
  rematerialization).

Needs the androidTest APK installed (install_apks.py without --no-test-apk).

    python -X utf8 ui_editor_transfer.py --flavor base --scenario APPEND
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time

import editor_flow as ef
import fixtures
import room_verify
import uia
from evidence import MissingInput, StepFailure, sha256_bytes, write_json
from qa_common import PREFLIGHT_STEPS, QaSession, add_common_args, execute
from qa_paths import RUNNER, TOOLS_DIR
from ui_editor_drafts import wait_after_save

STEPS = (
    *PREFLIGHT_STEPS, "preflight_instrumentation_registered", "seed_normal_fixture_clean_draft", "home_ready",
    "snapshot_before", "open_editor", "open_transfer_sheet", "select_mode", "select_target",
    "prepare_transfer_pending", "save_and_exit", "snapshot_after_save", "rematerialization_instrumentation",
    "snapshot_rematerialized", "verify_room_transfer",
)


def target_prefix(scenario: str) -> str:
    return "Martes · " if scenario == "CREATE" else "Miércoles · "


TRANSFER_CLASS = "com.example.kpkn.screens.sessioneditor.SessionEditorTransferMaterializationAvdTest"
# Base AVD validation (2026-10-02): the transfer androidTest calls PlanMaterializer.rematerializeWeek with the default
# CompositionMetadataHolder.resolve(), but the only production code that fills the holder is MainActivity's
# initializeExerciseDatabase(); `am instrument` starts a fresh process (the app is killed) so MainActivity never runs and
# the test fails with "No hay ExerciseCompositionMetadataProvider".  The retry runs the GPS lifecycle class first in the
# same instrumentation process: it launches MainActivity (catalog bootstrap) on an isolated test database and leaves the
# static holder populated for the transfer test, which then reads/writes the real fixture program row.
CATALOG_PRECONDITION_CLASS = "com.example.kpkn.services.cardio.CardioGpsAvdLifecycleInstrumentedTest"
CATALOG_MISSING_MARKER = "No hay ExerciseCompositionMetadataProvider"


def run_rematerialization(s: QaSession, scenario: str, *, label: str | None = None, precondition: bool = False) -> dict:
    """Run the real-materializer androidTest through run_instrumentation.py (own evidence dir)."""
    label = label or f"transfer-{scenario.lower()}"
    classes = f"{CATALOG_PRECONDITION_CLASS},{TRANSFER_CLASS}" if precondition else TRANSFER_CLASS
    command = [sys.executable, "-X", "utf8", str(TOOLS_DIR / "run_instrumentation.py"), "--flavor", s.flavor,
               "--serial", s.args.serial, "--classes", classes,
               "--scenario", scenario, "--label", label, "--timeout", "1200",
               "--apk", str(s.apk_path)]
    if precondition:
        command += ["--prepare-gps", "--geo-feed"]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=1300)
    suffix = "-with-catalog-precondition" if precondition else ""
    (s.out / f"rematerialization-instrumentation{suffix}.stdout.json").write_text(result.stdout, encoding="utf-8")
    (s.out / f"rematerialization-instrumentation{suffix}.stderr.txt").write_text(result.stderr, encoding="utf-8")
    try:
        summary = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise StepFailure(f"run_instrumentation.py did not return JSON (exit {result.returncode}): {result.stdout[-400:]}") from error
    return {"exit": result.returncode, **summary}


def catalog_missing(summary: dict) -> bool:
    return any(CATALOG_MISSING_MARKER in str(case.get("stack", "")) for case in summary.get("notPassed", []))


def run(s: QaSession, scenario: str) -> None:
    r = s.rec
    r.declare(*STEPS)
    s.rec.extra["scenario"] = scenario
    key = ef.draft_key()
    s.standard_preflight(("normal-v27",))

    with r.step("preflight_instrumentation_registered") as step:
        listing = s.avd.shell("pm", "list", "instrumentation", check=False)
        if RUNNER not in listing:
            raise MissingInput(f"{RUNNER} is not installed", f"run: install_apks.py --flavor {s.flavor}")
        step.detail("androidTest runner registered for com.example.kpkn")

    with r.step("seed_normal_fixture_clean_draft") as step:
        seeded = s.seed("normal-v27")
        source = (fixtures.FIXTURES_DIR / fixtures.PREFS_DIR / "session_editor_drafts.xml").read_bytes()
        cleaned, removed = ef.clean_drafts_xml(source, key)
        path = s.out / "clean-session_editor_drafts.xml"
        path.write_bytes(cleaned)
        got = s.avd.run_as_push(path, "shared_prefs/session_editor_drafts.xml", staging_name="clean-drafts")
        step.check(got == sha256_bytes(cleaned), "clean draft preferences did not copy exactly")
        step.detail(f"fixture {seeded['fixture']['sha256'][:12]} pushed; {removed} stale Monday draft removed")

    with r.step("home_ready") as step:
        s.launch_main()
        s.wait_home()
        step.detail("Home visible")

    with r.step("snapshot_before") as step:
        before = s.room("before")
        step.check(before.summary["databaseVersion"] == 28, f"Room version {before.summary['databaseVersion']} != 28")
        step.detail(f"snapshot {before.directory.name} (Room v28 after first launch of the migrated fixture)")

    with r.step("open_editor") as step:
        shot = ef.open_editor(s)
        step.detail(f"editor open ({shot.name})")

    with r.step("open_transfer_sheet") as step:
        shot = s.capture_ui("editor-ready")
        try:
            uia.resolve_clickable_target(shot.xml_text, "text:Transferir")
        except uia.TargetResolutionError:  # Transferir sits behind the overflow chip
            s.tap("text:Más", "editor-overflow", shot=shot)
        s.tap("text:Transferir", "transfer")
        sheet = s.capture_ui("transfer-sheet", png=True)
        step.check(sheet.present("text:Preparar transferencia") or sheet.present("text:Reemplazar"),
                   f"transfer sheet not recognizable; {s.diagnostics(sheet)}")
        step.detail("transfer sheet open")

    with r.step("select_mode") as step:
        if scenario == "REPLACE":
            s.tap("text:Reemplazar", "replace-mode")
            step.detail("Reemplazar selected")
        else:
            step.detail(f"{scenario}: default (append) mode kept")

    with r.step("select_target") as step:
        shot = s.capture_ui("transfer-targets")
        prefix = target_prefix(scenario)
        labels = {t for t in shot.tokens if t.startswith(prefix)}
        step.check(len(labels) == 1, f"expected exactly one visible target starting with {prefix!r}, got {sorted(labels)}")
        label = labels.pop()
        s.tap("text:" + label, "target", shot=shot)
        selected = s.capture_ui("selected-transfer", png=True)
        step.detail(f"target {label!r} selected")
        step.evidence(target=label, visible=selected.summary(20))

    with r.step("prepare_transfer_pending") as step:
        s.tap("text:Preparar transferencia", "prepare-transfer")
        pending = s.capture_ui("pending-transfer", png=True)
        step.detail(f"transfer prepared ({pending.name})")

    with r.step("save_and_exit") as step:
        s.key("KEYCODE_BACK", "back-after-prepare", settle=1.0)
        shot = s.capture_ui("exit-dialog-probe")
        if not shot.present("text:Guardar y salir"):
            s.key("KEYCODE_BACK", "back-again", settle=1.0)
            shot = s.capture_ui("exit-dialog-probe-2")
        s.tap("text:Guardar y salir", "save-and-exit", shot=shot)
        wait_after_save(s)
        time.sleep(3.0)
        s.capture_ui("transfer-saved", png=True)
        step.detail("Guardar y salir completed; program page visible")

    with r.step("snapshot_after_save") as step:
        after = s.room("after")
        step.detail(f"snapshot {after.directory.name}")

    with r.step("rematerialization_instrumentation") as step:
        summary = run_rematerialization(s, scenario)
        first = {"status": summary.get("status"), "evidence": summary.get("evidence"), "reasons": summary.get("reasons")}
        step.evidence(firstAttempt=first)
        if summary.get("status") != "PASSED" and catalog_missing(summary):
            s.rec.note("rematerialization attempt 1 failed because CompositionMetadataHolder was never initialised in the "
                       "instrumentation process (test/harness defect); retrying with MainActivity-based catalog precondition")
            summary = run_rematerialization(s, scenario, label=f"transfer-{scenario.lower()}-catalog-precondition",
                                            precondition=True)
            step.evidence(retryWithCatalogPrecondition={"status": summary.get("status"), "evidence": summary.get("evidence"),
                                                         "reasons": summary.get("reasons")})
        step.evidence(instrumentationStatus=summary.get("status"), evidence=summary.get("evidence"), reasons=summary.get("reasons"))
        step.check(summary.get("status") == "PASSED", f"rematerialization test not PASSED: {summary.get('status')} {summary.get('reasons')}")
        step.detail(f"SessionEditorTransferMaterializationAvdTest[{scenario}] PASSED ({summary.get('evidence')})")

    with r.step("snapshot_rematerialized") as step:
        remat = s.room("rematerialized")
        step.detail(f"snapshot {remat.directory.name}")

    with r.step("verify_room_transfer") as step:
        report = room_verify.verify(before.db_path, after.db_path, scenario, rematerialized_path=remat.db_path,
                                     allow_derived_native_metadata=True)
        payload = report.as_json()
        write_json(s.out / f"verify-{scenario.lower()}.json", payload)
        bad = [c for c in payload["checks"] if c["status"] != "PASS"]
        step.evidence(result=payload["result"], checks=len(payload["checks"]), nonPass=bad,
                      createdSessionId=payload.get("createdSessionId"))
        if payload["result"] == "FAIL":
            step.fail("; ".join(f"{c['name']}: {c['detail']}" for c in bad)[:1500])
        if payload["result"] != "PASS":
            step.not_run("; ".join(f"{c['name']}={c['status']}" for c in bad))
        step.detail(f"{len(payload['checks'])} checks PASS (payload, ids, overrides, source/Friday unchanged, stable after rematerialization)")


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    parser.add_argument("--scenario", choices=("APPEND", "REPLACE", "CREATE"), required=True)
    args = parser.parse_args(argv)
    return execute(f"editor-transfer-{args.scenario.lower()}", args, lambda session: run(session, args.scenario))


if __name__ == "__main__":
    raise SystemExit(main())
