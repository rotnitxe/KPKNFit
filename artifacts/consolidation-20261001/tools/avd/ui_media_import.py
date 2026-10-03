#!/usr/bin/env python3
"""Workout media IMPORT flow: photo + video through the real Android picker, a late photo, Finish.

Port of the OLDROOT V4 media controller (``run_workout_media_avd.py`` + ``verify_workout_media_finish.py``)
as one automated driver.  Everything here is an IMPORT of synthetic files; it never claims a CameraX
capture - that is ``camerax_capture.py`` (and both drivers classify what they created with
``media_checks.classify_origin``).

Flow (fixture ``finish-v2-normal-v27``; CAMERA pre-granted so the media face shows no system dialog):

  push PNG/MP4 to shared storage (unique names, device hash verified) -> Home -> workout ->
  "Ver ejercicio/Fotos" -> Galería -> picker -> PNG  ;  Galería -> picker -> MP4 -> album shows both ->
  Room(A): two rows PHOTO+VIDEO, UUID ids, no log link, no association, private copies byte-identical ->
  reopen -> type 20 -> ONE tap Registrar serie -> LATE photo (imported after the set, before Finish) ->
  Room(B): S1 UUID + three rows -> reopen -> Back -> Terminar hasta acá -> Guardar y terminar ->
  Home -> Room(C): one durable session->log association, every row bound to the log, frozen metadata
  unchanged, finalized set == ongoing S1.

"Late" here means: added after the set and immediately before Finish.  Ingests that outlive the
workout observer after Finish are covered only by the androidTest classes
WorkoutMediaLateRetryInstrumentedTest / WorkoutMediaUriDurabilityInstrumentedTest (run them with
run_instrumentation.py); this driver reports that explicitly as an optional NOT_RUN step.

    python -X utf8 ui_media_import.py --flavor base
"""

from __future__ import annotations

import argparse
import time
from typing import Any

import fixtures
import media_checks as mc
import media_flow as mf
from evidence import StepFailure
from qa_common import PREFLIGHT_STEPS, QaSession, add_common_args, execute
from ui_strength_s1_s2 import register_once, settle_after_register

FIXTURE = "finish-v2-normal-v27"
STEPS = (
    *PREFLIGHT_STEPS, "push_import_media_verified", "seed_fixture", "home_ready", "open_workout", "open_media_face",
    "import_photo_via_picker", "import_video_via_picker", "album_shows_photo_and_video", "room_pre_s1_binding",
    "private_files_equal_fixtures", "reopen_register_s1_single_click", "late_photo_via_picker",
    "room_s1_and_late_rows", "reopen_and_finish", "home_after_finish", "room_post_finish_binding",
    "finalized_set_equals_ongoing_s1", "private_files_after_finish", "late_ingest_after_finish_instrumented_only",
)


def relative_private(path: str) -> str:
    for prefix in ("/data/user/0/com.example.kpkn/", "/data/data/com.example.kpkn/"):
        if path.startswith(prefix):
            return path[len(prefix):]
    raise StepFailure(f"media path is not under the app's private dir: {path}")


def hash_private_files(s: QaSession, rows: list[dict[str, Any]]) -> dict[str, str]:
    hashes: dict[str, str] = {}
    for row in rows:
        problem = mc.private_path_problem(row)
        if problem:
            raise StepFailure(problem)
        hashes[row["id"]] = s.avd.run_as_sha256(relative_private(row["filePath"]))
    return hashes


def expected_hash_problem(rows: list[dict[str, Any]], hashes: dict[str, str], expectations: list[tuple[str, str, str]]) -> str | None:
    """Each (label, kind, sha) must match exactly one row of that kind."""
    for label, kind, sha in expectations:
        matches = [r["id"] for r in rows if r["kind"] == kind and hashes.get(r["id"]) == sha]
        if len(matches) != 1:
            return f"{label}: expected exactly one {kind} row with sha256 {sha[:12]}..., found {matches}"
    return None


def run(s: QaSession) -> None:
    r = s.rec
    r.declare(*STEPS, optional=("late_ingest_after_finish_instrumented_only",))
    ctx: dict[str, Any] = {}
    s.standard_preflight((FIXTURE,))
    identity = s.rec.inputs["fixtures"][FIXTURE]["identity"]["ongoing"]
    session_key = identity["sessionKey"]

    with r.step("push_import_media_verified") as step:
        ctx["media"] = mf.push_import_media(s, late_photo=True)
        step.detail("; ".join(f"{k}={v['name']} {v['sha256'][:10]}" for k, v in ctx["media"]["files"].items()))
        step.evidence(**ctx["media"])
    media = ctx["media"]["files"]

    with r.step("seed_fixture") as step:
        seeded = s.seed(FIXTURE, grants=("android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_FINE_LOCATION",
                                         "android.permission.ACCESS_COARSE_LOCATION", "android.permission.CAMERA"))
        step.detail(f"fixture {seeded['fixture']['sha256'][:12]} pushed; CAMERA granted={seeded['grants']['android.permission.CAMERA']}")
    with r.step("home_ready"):
        s.launch_main()
        s.wait_home()
    with r.step("open_workout") as step:
        s.deeplink(fixtures.WORKOUT_URI)
        shot = s.wait_ui(lambda x: x.has("Registrar serie"), "workout-open", timeout=60, png=True, what="set card with Registrar serie")
        step.detail(f"S1 card visible ({shot.name})")
    with r.step("open_media_face") as step:
        shot = mf.open_media_face(s)
        step.detail(f"media back face open ({shot.name})")

    with r.step("import_photo_via_picker") as step:
        ctx["photo"] = mf.import_via_gallery(s, ctx["media"], "photo", "photo")
        step.detail(f"picked {media['photo']['name']} through the document picker")
        step.evidence(picker=ctx["photo"]["picker"])
    with r.step("import_video_via_picker") as step:
        s.wait_ui(lambda x: x.has("Parar") or x.present(mf.GALLERY_CHIP), "after-photo", timeout=30, what="media face after the photo import")
        ctx["video"] = mf.import_via_gallery(s, ctx["media"], "video", "video")
        step.detail(f"picked {media['video']['name']} through the document picker")
        step.evidence(picker=ctx["video"]["picker"])

    with r.step("album_shows_photo_and_video") as step:
        mf.open_album(s)
        shot = s.wait_ui(lambda x: mf.ALBUM_TITLE in x.xml_text and "Vídeo" in x.xml_text, "album-both", timeout=60, png=True,
                         what="album with the video overlay")
        step.detail(f"album lists the imported media ({shot.name})")
        mf.close_album(s)
        mf.close_media_face(s)

    with r.step("room_pre_s1_binding") as step:
        a = s.room("media-before-finish-a")
        ctx["roomA"] = a
        problem = mc.session_media_problem(a.summary, session_key, ["PHOTO", "VIDEO"])
        step.check(problem is None, problem or "")
        step.detail(f"Room v28: PHOTO+VIDEO rows (UUID ids, unlinked) for {session_key}")
        step.evidence(rows=mc.media_for_session(a.summary, session_key))
    with r.step("private_files_equal_fixtures") as step:
        rows = mc.media_for_session(ctx["roomA"].summary, session_key)
        hashes = hash_private_files(s, rows)
        problem = expected_hash_problem(rows, hashes, [("photo", "PHOTO", media["photo"]["sha256"]),
                                                       ("video", "VIDEO", media["video"]["sha256"])])
        step.check(problem is None, problem or "")
        step.detail("private copies are byte-identical to the pushed PNG/MP4")
        step.evidence(hashes=hashes)

    with r.step("reopen_register_s1_single_click") as step:
        s.cold_restart(fixtures.WORKOUT_URI, "reopen-a")
        s.type_primary("20", "weight-s1")
        register_once(s, "s1")
        handled = settle_after_register(s, "s1")
        shot = s.wait_ui(lambda x: "1/2" in x.tokens or "Registrar serie" in x.tokens, "s1-registered", timeout=20, png=True,
                         what="surface after S1")
        step.detail(f"S1 registered with one tap; overlays {handled}")

    with r.step("late_photo_via_picker") as step:
        mf.open_media_face(s, "late-face")
        ctx["late"] = mf.import_via_gallery(s, ctx["media"], "latePhoto", "late")
        time.sleep(2.0)
        step.detail(f"late photo {media['latePhoto']['name']} imported after the set")

    with r.step("room_s1_and_late_rows") as step:
        b = s.room("media-after-s1-and-late")
        ctx["roomB"] = b
        problem = mc.session_media_problem(b.summary, session_key, ["PHOTO", "PHOTO", "VIDEO"])
        step.check(problem is None, problem or "")
        problem, s1 = mc.s1_active_problem(b.summary, session_key, 20.0, 6)
        step.check(problem is None, problem or "")
        ctx["s1"] = s1
        step.detail(f"S1 {s1['id']} (20 x 6) persisted with three unlinked media rows")
        step.evidence(s1=s1, rows=mc.media_for_session(b.summary, session_key))

    with r.step("reopen_and_finish") as step:
        s.cold_restart(fixtures.WORKOUT_URI, "reopen-b")
        step.detail("workout restored before Finish")
    with r.step("home_after_finish") as step:
        home = mf.finish_workout(s)
        s.require_main_top(home)
        step.detail(f"Home after Finish ({home.name})")

    with r.step("room_post_finish_binding") as step:
        c = s.room("media-after-finish")
        ctx["roomC"] = c
        problem, info = mc.finish_binding_problem(ctx["roomB"].summary, c.summary, session_key)
        step.check(problem is None, problem or "")
        ctx["binding"] = info
        step.detail(f"one association {session_key} -> {info['workoutLogId']}; {len(info['mediaIds'])} rows bound to the log")
        step.evidence(**info)
    with r.step("finalized_set_equals_ongoing_s1") as step:
        problem = mc.finalized_set_problem(ctx["roomC"].summary, ctx["binding"]["workoutLogId"], ctx["s1"])
        step.check(problem is None, problem or "")
        step.detail("finalized log holds the same S1 UUID, weight and reps that were in ongoing_workout")
    with r.step("private_files_after_finish") as step:
        rows = mc.media_for_session(ctx["roomC"].summary, session_key)
        hashes = hash_private_files(s, rows)
        problem = expected_hash_problem(rows, hashes, [
            ("photo", "PHOTO", media["photo"]["sha256"]), ("late photo", "PHOTO", media["latePhoto"]["sha256"]),
            ("video", "VIDEO", media["video"]["sha256"])])
        step.check(problem is None, problem or "")
        step.detail("all three private files unchanged by Finish")
        step.evidence(hashes=hashes)

    with r.step("late_ingest_after_finish_instrumented_only", optional=True) as step:
        step.not_run("an ingest outliving the workout observer after Finish is not drivable from the UI; run "
                     "WorkoutMediaLateRetryInstrumentedTest and WorkoutMediaUriDurabilityInstrumentedTest with "
                     "run_instrumentation.py")


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    args = parser.parse_args(argv)
    return execute("media-import", args, run)


if __name__ == "__main__":
    raise SystemExit(main())
