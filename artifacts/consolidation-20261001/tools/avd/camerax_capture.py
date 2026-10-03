#!/usr/bin/env python3
"""REAL CameraX photo / video capture from the live-workout UI on the emulator camera.

This is NOT an import test.  The synthetic PNG/MP4 fixtures are only used as *negative controls*
(their hashes can never be a capture); the media created here come out of CameraX's ImageCapture /
Recorder bound to the emulator camera by ``SetCardExerciseMediaBack`` (in-card preview, chips
"Foto" / "Vídeo" -> "Parar").  Navigation uses the real labels of that screen:

    Ver ejercicio/Fotos  ->  [Foto] / [Vídeo -> Parar] / Galería (import, never used here) / Álbum / Volver a la serie

Permissions (contract in SetCardExerciseMediaBack.kt):
  * CAMERA is requested when the media face opens (LaunchedEffect) - ``--permissions pm`` pre-grants
    it with ``pm grant``; ``--permissions dialog`` leaves it ungranted and taps the system dialog.
  * RECORD_AUDIO is requested when Vídeo is pressed; granted by pm (``--audio grant``, default) or
    decided in the system dialog.  ``--audio deny`` records without audio (the app handles denial).

What is verified per captured item (all must hold for ``captureClaim == REAL_CAMERAX_CAPTURE``):
  1. a new thumbnail appears on the media face (UI), after pressing the shutter control only;
  2. a ``workout_media`` row exists: canonical UUID id, kind, sessionKey/programId/sessionId of the
     ongoing workout, the squat exerciseId, no log link, createdAtMs inside the capture window;
  3. the file exists under filesDir/workout_media/yyyy-MM/<uuid>.jpg|mp4 and is a real encoder output
     (JPEG with SOF / MP4 with ftyp+moov+video track and ~recorded duration), byte-different from the
     import fixtures;
  4. ``media_checks.classify_origin`` -> CAMERAX_CAPTURE (an imported file would classify IMPORT);
  5. corroboration (optional steps): the camera service lists the app as a client / logcat lines.
With ``--finish`` the workout is finished and the session->log association of the captured rows is
verified as well.

    python -X utf8 camerax_capture.py --flavor base                 # photo + video, pm grants
    python -X utf8 camerax_capture.py --flavor base --mode photo --permissions dialog
    python -X utf8 camerax_capture.py --flavor base --audio deny --video-seconds 5 --finish
"""

from __future__ import annotations

import argparse
import json
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import fixtures
import media_checks as mc
import media_flow as mf
import media_probe as mp
import room_inspect
import uia
from evidence import StepFailure, sha256_bytes, sha256_file, write_json
from qa_common import PREFLIGHT_STEPS, QaSession, UiShot, add_common_args, execute
from qa_paths import EVIDENCE_ROOT, OWNED_DEVICES, PACKAGE
from ui_strength_s1_s2 import register_once, settle_after_register

FIXTURE = "finish-v2-normal-v27"
CAMERA_OFF_TEXT = "Activa la cámara para grabar desde esta tarjeta"
CAPTURE_ERROR_PREFIX = "No se pudo"
PERMISSION_APP_MARKERS = ("permissioncontroller",)
ALLOW_SELECTORS = (
    "id:com.google.android.permissioncontroller:id/permission_allow_foreground_only_button",
    "id:com.android.permissioncontroller:id/permission_allow_foreground_only_button",
    "id:com.google.android.permissioncontroller:id/permission_allow_button",
    "id:com.android.permissioncontroller:id/permission_allow_button",
    "id:com.google.android.permissioncontroller:id/permission_allow_one_time_button",
    "text:While using the app", "text:Mientras la app está en uso", "text:Allow", "text:Permitir",
)
DENY_SELECTORS = (
    "id:com.google.android.permissioncontroller:id/permission_deny_button",
    "id:com.android.permissioncontroller:id/permission_deny_button",
    "id:com.google.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
    "text:Don't allow", "text:No permitir", "text:Deny", "text:Denegar",
)
PERMISSION_CAMERA, PERMISSION_AUDIO = "android.permission.CAMERA", "android.permission.RECORD_AUDIO"


# ---------------------------------------------------------------------------
# Pure helpers
# ---------------------------------------------------------------------------

def parse_ini(text: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in text.splitlines():
        if "=" in line and not line.lstrip().startswith(("#", ";")):
            key, _, value = line.partition("=")
            out[key.strip()] = value.strip()
    return out


def find_avd_config(serial: str) -> Path | None:
    contract = OWNED_DEVICES[serial]
    if contract.avd_home:
        candidate = Path(contract.avd_home) / f"{contract.avd_name}.avd" / "config.ini"
        return candidate if candidate.is_file() else None
    ini = Path.home() / ".android" / "avd" / f"{contract.avd_name}.ini"
    if not ini.is_file():
        return None
    path = parse_ini(ini.read_text(encoding="utf-8", errors="replace")).get("path")
    candidate = Path(path) / "config.ini" if path else None
    return candidate if candidate and candidate.is_file() else None


def camera_config_facts(serial: str) -> dict[str, Any]:
    config = find_avd_config(serial)
    facts: dict[str, Any] = {"configIni": str(config) if config else None, "back": None, "front": None,
                             "launchOverride": None}
    if config:
        values = parse_ini(config.read_text(encoding="utf-8", errors="replace"))
        facts.update(back=values.get("hw.camera.back"), front=values.get("hw.camera.front"))
    launches = sorted((EVIDENCE_ROOT / "avd-logs").glob(f"avd-{OWNED_DEVICES[serial].role}-*.launch.json")) \
        if (EVIDENCE_ROOT / "avd-logs").is_dir() else []
    if launches:
        try:
            record = json.loads(launches[-1].read_text(encoding="utf-8-sig"))
            facts["launchOverride"] = record.get("cameraOverride")
            facts["launchRecord"] = str(launches[-1])
        except (OSError, ValueError):
            pass
    return facts


def thumb_counts(xml_text: str, exercise_name: str, dpi: int = 480) -> dict[str, int]:
    """Count media thumbnails on the media face with two independent heuristics.

    * ``photoThumbs`` / ``videoMarks``: nodes whose content-description carries the exercise name (the
      AsyncImage of ``WorkoutMediaThumb``) or the "Vídeo" play overlay;
    * ``squareThumbs``: clickable, roughly square nodes of 40-80 dp (the 56 dp ``WorkoutMediaThumb`` boxes;
      32 dp icon buttons and wide chips are excluded) - independent of any description text.
    """
    root = ET.fromstring(xml_text)
    low, high = 40 * dpi / 160, 80 * dpi / 160
    photos = videos = squares = 0
    for node in root.iter():
        bounds = uia.parse_bounds(node.attrib.get("bounds"))
        if bounds is None:
            continue
        width, height = bounds[2] - bounds[0], bounds[3] - bounds[1]
        description = node.attrib.get("content-desc", "")
        if node.attrib.get("clickable") == "true" and low <= width <= high and abs(width - height) <= 0.1 * width:
            squares += 1
        if not description:
            continue
        if "Vídeo" in description and width <= 400:
            videos += 1
        elif exercise_name in description and "técnica" not in description.casefold() and 60 <= width <= 400 and abs(width - height) <= 16:
            photos += 1
    return {"photoThumbs": photos, "videoMarks": videos, "squareThumbs": squares}


def grew(before: dict[str, int], after: dict[str, int], *keys: str) -> bool:
    """True when any of the listed counters increased (heuristics may disagree; one signal suffices)."""
    return any(after.get(key, 0) > before.get(key, 0) for key in keys)


def density_dpi(text: str | None, default: int = 480) -> int:
    """``wm density`` prints 'Physical density: 480' (and 'Override density: N' when changed)."""
    values = [int(v) for v in re.findall(r"density:\s*(\d+)", text or "")]
    return values[-1] if values else default


def window_contains(created_ms: int | None, start_ms: int, end_ms: int, slack: int = 3000) -> bool:
    return created_ms is not None and start_ms - slack <= int(created_ms) <= end_ms + slack


def capture_row_problem(row: dict[str, Any] | None, *, kind: str, session_key: str, identity: dict[str, Any]) -> str | None:
    if row is None:
        return f"no workout_media row of kind {kind} for the capture"
    if not room_inspect.is_uuid(row.get("id")):
        return f"row id is not a canonical UUID: {row.get('id')!r}"
    if row.get("kind") != kind:
        return f"row kind {row.get('kind')!r} != {kind}"
    expected = {"sessionKey": session_key, "programId": identity["programId"], "sessionId": identity["sessionId"]}
    for field, value in expected.items():
        if row.get(field) != value:
            return f"row {field}={row.get(field)!r} != {value!r}"
    if row.get("exerciseId") != fixtures.SQUAT_EXERCISE_ID:
        return f"row exerciseId {row.get('exerciseId')!r} is not the active squat"
    if row.get("workoutLogId") not in (None, ""):
        return "captured row is already attached to a log before Finish"
    problem = mc.private_path_problem(row)
    if problem:
        return problem
    expected_ext = ".jpg" if kind == "PHOTO" else ".mp4"
    path = str(row.get("filePath"))
    if not path.endswith(f"/{row['id']}{expected_ext}"):
        return f"file name does not match <uuid>{expected_ext}: {path}"
    return None


# ---------------------------------------------------------------------------
# Device interaction helpers
# ---------------------------------------------------------------------------

def permission_dialog_visible(shot: UiShot) -> bool:
    top = (shot.top_resumed or "").lower()
    return any(marker in top for marker in PERMISSION_APP_MARKERS) or any(
        marker in shot.xml_text for marker in PERMISSION_APP_MARKERS)


def handle_permission_dialog(s: QaSession, allow: bool, label: str, *, timeout: float = 20.0) -> dict[str, Any]:
    shot = s.wait_ui(permission_dialog_visible, f"{label}-dialog", timeout=timeout, png=True,
                     what="a runtime-permission dialog")
    candidates = ALLOW_SELECTORS if allow else DENY_SELECTORS
    for selector in candidates:
        try:
            uia.resolve_clickable_target(shot.xml_text, selector)
        except uia.TargetResolutionError:
            continue
        event = s.tap(selector, f"{label}-{'allow' if allow else 'deny'}", shot=shot)
        return {"dialogTexts": [t for t in shot.tokens][:12], "tapped": selector, "allow": allow, "target": event["target"]}
    raise StepFailure(f"no {'allow' if allow else 'deny'} control found in the permission dialog; {s.diagnostics(shot)}")


def preview_ready(shot: UiShot) -> bool:
    if shot.has(CAMERA_OFF_TEXT):
        return False
    try:
        uia.resolve_clickable_target(shot.xml_text, mf.PHOTO_CHIP)
        uia.resolve_clickable_target(shot.xml_text, mf.VIDEO_CHIP)
    except uia.TargetResolutionError:
        return False
    return True


def capture_error(shot: UiShot) -> str | None:
    for token in shot.tokens:
        if token.startswith(CAPTURE_ERROR_PREFIX):
            return token
    return None


def fetch_media_file(s: QaSession, row: dict[str, Any], label: str) -> dict[str, Any]:
    relative = "files/" + str(row["filePath"]).split("/files/", 1)[1]
    payload = s.avd.run_as_cat(relative, timeout=180)
    extension = relative.rsplit(".", 1)[-1]
    target = s.out / "captured" / f"{label}-{row['id']}.{extension}"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(payload)
    info: dict[str, Any] = {"relative": relative, "bytes": len(payload), "sha256": sha256_bytes(payload),
                            "format": mc.sniff_media_format(payload[:16]), "evidenceFile": str(target)}
    if info["format"] == "JPEG":
        info["jpeg"] = mp.jpeg_info(payload)
    elif info["format"] == "MP4":
        info["mp4"] = mp.mp4_info(payload)
    thumb = row.get("thumbPath")
    if thumb:
        thumb_relative = "files/" + str(thumb).split("/files/", 1)[1]
        exists = s.avd.run_as_exists(thumb_relative, "-f")
        info["thumb"] = {"path": thumb_relative, "exists": exists,
                         "bytes": len(s.avd.run_as_cat(thumb_relative)) if exists else 0}
    return info


# ---------------------------------------------------------------------------
# The run
# ---------------------------------------------------------------------------

def run(s: QaSession, args: argparse.Namespace) -> None:
    r = s.rec
    photo, video = args.mode in ("photo", "both"), args.mode in ("video", "both")
    dialog_mode = args.permissions == "dialog"
    steps = [*PREFLIGHT_STEPS, "camera_hardware_present", "origin_classifier_self_check", "seed_fixture", "home_ready",
             "open_workout", "open_media_face"]
    if dialog_mode:
        steps.append("camera_permission_dialog")
    steps += ["camera_preview_ready", "camera_service_corroboration_preview"]
    if photo:
        steps += ["photo_shutter_single_tap", "photo_thumbnail_visible"]
    if video:
        steps += ["video_start_recording", "video_stop_recording", "video_thumbnail_visible"]
    steps += ["album_lists_captured_media", "room_rows_and_private_files", "origin_is_camerax_not_import"]
    if args.finish:
        steps += ["finish_binds_captured_media"]
    r.declare(*steps, optional=("camera_service_corroboration_preview",))
    s.rec.extra["captureClaim"] = "NOT_PROVEN"
    s.rec.extra["mode"] = args.mode
    s.rec.extra["permissions"] = args.permissions
    s.rec.extra["claimScope"] = ("CameraX still/recording from the live-workout media face on an EMULATOR camera "
                                 "(emulated or virtual scene); no physical-camera claim")
    s.standard_preflight((FIXTURE,))
    identity = s.rec.inputs["fixtures"][FIXTURE]["identity"]["ongoing"]
    session_key = identity["sessionKey"]
    exercise_name = fixtures.SQUAT_DISPLAY_NAME
    ctx: dict[str, Any] = {"captures": [], "dpi": density_dpi(s.rec.inputs.get("device", {}).get("density"))}

    with r.step("camera_hardware_present") as step:
        features = s.avd.shell("pm", "list", "features", check=False)
        camera_dump = s.avd.shell("dumpsys", "media.camera", timeout=60, check=False)
        (s.out / "dumpsys-media-camera-before.txt").write_text(camera_dump, encoding="utf-8")
        count = mp.camera_device_count(camera_dump)
        facts = camera_config_facts(s.args.serial)
        facts.update(featureCamera="android.hardware.camera" in features, featureCameraAny="android.hardware.camera.any" in features,
                     deviceCount=count)
        s.rec.inputs["camera"] = facts
        step.check(facts["featureCamera"] or facts["featureCameraAny"], "device reports no android.hardware.camera feature")
        step.check(count is None or count >= 1, f"camera service reports {count} camera devices")
        step.check(facts["back"] not in ("none",), "AVD config.ini has hw.camera.back=none (override with start_avd.ps1 -CameraBack)")
        step.detail(f"camera devices={count}; config hw.camera.back={facts['back']}, front={facts['front']}; "
                    f"launch override={facts['launchOverride']}")
        step.evidence(**facts)

    with r.step("origin_classifier_self_check") as step:
        fixture_hashes = {sha256_file(fixtures.fixture_path("qa-photo")), sha256_file(fixtures.fixture_path("qa-video"))}
        ctx["fixtureHashes"] = fixture_hashes
        imported = mc.classify_origin(kind="PHOTO", file_format="PNG", sha256=sorted(fixture_hashes)[0],
                                      import_fixture_hashes=fixture_hashes, interaction="picker",
                                      created_in_window=True, via_picker=True)
        captured = mc.classify_origin(kind="PHOTO", file_format="JPEG", sha256="0" * 64, import_fixture_hashes=fixture_hashes,
                                      interaction="shutter", created_in_window=True, via_picker=False)
        step.check(imported["origin"] == "IMPORT" and captured["origin"] == "CAMERAX_CAPTURE",
                   f"classifier does not separate import from capture: {imported} / {captured}")
        step.detail("classifier separates an import (IMPORT) from a shutter capture (CAMERAX_CAPTURE)")

    grants = ["android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_FINE_LOCATION",
              "android.permission.ACCESS_COARSE_LOCATION"]
    if not dialog_mode:
        grants.append(PERMISSION_CAMERA)
    if video and args.audio == "grant" and not dialog_mode:
        grants.append(PERMISSION_AUDIO)
    with r.step("seed_fixture") as step:
        seeded = s.seed(FIXTURE, grants=tuple(grants))
        camera_state = s.avd.permission_granted(PERMISSION_CAMERA)
        step.check((camera_state is True) != dialog_mode,
                   f"CAMERA grant state {camera_state} does not match --permissions {args.permissions}")
        step.detail(f"fixture pushed; CAMERA granted={camera_state}; RECORD_AUDIO granted={s.avd.permission_granted(PERMISSION_AUDIO)}")
    with r.step("home_ready"):
        s.launch_main()
        s.wait_home()
    with r.step("open_workout") as step:
        s.deeplink(fixtures.WORKOUT_URI)
        shot = s.wait_ui(lambda x: x.has("Registrar serie"), "workout-open", timeout=60, png=True, what="set card (Registrar serie)")
        ctx["before"] = thumb_counts(shot.xml_text, exercise_name, ctx["dpi"])
        step.detail(f"front card visible; thumbnails before: {ctx['before']}")
    with r.step("open_media_face") as step:
        mf.tap_widest(s, mf.MEDIA_FACE_TRIGGER, "open-media-face")
        if dialog_mode:
            step.detail("media face requested; the CAMERA dialog is handled next")
        else:
            shot = s.wait_ui(lambda x: x.has("Técnica KPKN") or x.present(mf.BACK_TO_SET), "media-face", timeout=20, png=True,
                             what="media back face")
            step.detail(f"media face open ({shot.name})")

    if dialog_mode:
        with r.step("camera_permission_dialog") as step:
            record = handle_permission_dialog(s, allow=True, label="camera")
            step.evidence(**record)
            step.detail(f"CAMERA dialog answered with {record['tapped']}")
            step.check(s.avd.permission_granted(PERMISSION_CAMERA) is True, "CAMERA is still not granted after the dialog")

    with r.step("camera_preview_ready") as step:
        shot = s.wait_ui(preview_ready, "preview", timeout=45, png=True, what="CameraX preview bound (Foto/Vídeo chips enabled)")
        time.sleep(args.bind_wait)
        shot = s.capture_ui("preview-settled", png=True)
        step.check(capture_error(shot) is None, f"capture error already visible: {capture_error(shot)}")
        s.require_main_top(shot)
        ctx["baseline"] = thumb_counts(shot.xml_text, exercise_name, ctx["dpi"])
        step.detail(f"preview ready, chips enabled; baseline thumbnails {ctx['baseline']}")

    with r.step("camera_service_corroboration_preview", optional=True) as step:
        dump = s.avd.shell("dumpsys", "media.camera", timeout=60, check=False)
        (s.out / "dumpsys-media-camera-preview.txt").write_text(dump, encoding="utf-8")
        evidence = mp.camera_service_evidence(dump, PACKAGE)
        step.evidence(**evidence)
        if not evidence["found"]:
            step.not_run("dumpsys media.camera does not list the app (format differs or the preview is not bound); "
                         "see dumpsys-media-camera-preview.txt")
        step.detail(f"camera service lists {PACKAGE}: {evidence['lines'][:2]}")

    # ------------------------------------------------------------------ photo
    if photo:
        with r.step("photo_shutter_single_tap") as step:
            ctx["photoStart"] = s.avd.device_time_ms()
            before_taps = len(s.taps)
            event = s.tap(mf.PHOTO_CHIP, "photo-shutter", settle=0.5)
            ctx["photoTapped"] = event
            step.check(len(s.taps) == before_taps + 1, "more than one tap was issued for the shutter")
            step.detail(f"one tap on 'Foto' at device time {ctx['photoStart']}")
        with r.step("photo_thumbnail_visible") as step:
            def photo_thumb(x: UiShot) -> bool:
                if capture_error(x):
                    raise StepFailure(f"app reported a capture error: {capture_error(x)}")
                return grew(ctx["baseline"], thumb_counts(x.xml_text, exercise_name, ctx["dpi"]), "photoThumbs", "squareThumbs")

            shot = s.wait_ui(photo_thumb, "photo-thumb", timeout=args.capture_timeout, png=True, what="a new photo thumbnail")
            ctx["photoEnd"] = s.avd.device_time_ms()
            ctx["afterPhoto"] = thumb_counts(shot.xml_text, exercise_name, ctx["dpi"])
            step.check(not mf.in_picker(shot), "the document picker is in the foreground (an import, not a capture)")
            step.detail(f"thumbnails {ctx['baseline']} -> {ctx['afterPhoto']} within {ctx['photoEnd'] - ctx['photoStart']} ms")

    # ------------------------------------------------------------------ video
    if video:
        audio_pm = s.avd.permission_granted(PERMISSION_AUDIO) is True
        with r.step("video_start_recording") as step:
            ctx["videoStart"] = s.avd.device_time_ms()
            s.tap(mf.VIDEO_CHIP, "video-start", settle=0.8)
            if not audio_pm:
                record = handle_permission_dialog(s, allow=(args.audio == "grant"), label="audio")
                step.evidence(audioDialog=record)
            shot = s.wait_ui(lambda x: x.present(mf.STOP_CHIP), "recording", timeout=30, png=True,
                             what="recording state (chip 'Parar')")
            err = capture_error(shot)
            step.check(err is None, f"app reported a capture error while starting: {err}")
            ctx["videoRecordingSince"] = time.monotonic()
            step.detail(f"recording; audio permission pm-granted={audio_pm}, policy={args.audio}")
        with r.step("video_stop_recording") as step:
            remaining = args.video_seconds - (time.monotonic() - ctx["videoRecordingSince"])
            if remaining > 0:
                time.sleep(remaining)
            ctx["recordedSeconds"] = round(time.monotonic() - ctx["videoRecordingSince"], 2)
            # Health-flavor validation (2026-10-02): ``videoRecordingSince`` is taken only AFTER the UiAutomator wait that
            # confirms the 'Parar' chip (one dump + PNG, ~10 s on this AVD), so "stopped after ~4 s" under-reported the real
            # recording (MP4 durationMs = 14.3 s base-run 15.7 s). Record the device-clock window from the 'Vídeo' tap to the
            # 'Parar' tap and bound the MP4 duration from above with it.
            ctx["videoStopDevice"] = s.avd.device_time_ms()
            s.tap(mf.STOP_CHIP, "video-stop", settle=0.8)
            ctx["recordedWindowSeconds"] = round((ctx["videoStopDevice"] - ctx["videoStart"]) / 1000.0, 2)
            step.detail(f"stopped after ~{ctx['recordedSeconds']} s of UI-confirmed recording; "
                        f"{ctx['recordedWindowSeconds']} s on the device clock between the 'Vídeo' tap and the 'Parar' tap")
        with r.step("video_thumbnail_visible") as step:
            reference = ctx.get("afterPhoto", ctx["baseline"])

            def video_thumb(x: UiShot) -> bool:
                if capture_error(x):
                    raise StepFailure(f"app reported a capture error: {capture_error(x)}")
                counts = thumb_counts(x.xml_text, exercise_name, ctx["dpi"])
                return grew(reference, counts, "videoMarks", "squareThumbs") and not x.present(mf.STOP_CHIP)

            shot = s.wait_ui(video_thumb, "video-thumb", timeout=args.capture_timeout + 60, png=True, what="a new video thumbnail")
            ctx["videoEnd"] = s.avd.device_time_ms()
            ctx["afterVideo"] = thumb_counts(shot.xml_text, exercise_name, ctx["dpi"])
            step.detail(f"thumbnails {reference} -> {ctx['afterVideo']} ({shot.name})")

    # ------------------------------------------------------------------ album + Room + files
    with r.step("album_lists_captured_media") as step:
        mf.open_album(s, "album-captured")
        wanted_video = video
        shot = s.wait_ui(lambda x: mf.ALBUM_TITLE in x.xml_text and (not wanted_video or "Vídeo" in x.xml_text),
                         "album-captured-ready", timeout=40, png=True, what="album listing the captured media")
        step.detail(f"album shows the captured media ({shot.name})")
        mf.close_album(s, "album-captured-close")

    with r.step("room_rows_and_private_files") as step:
        room_after = s.room("after-capture")
        rows = mc.media_for_session(room_after.summary, session_key)
        step.check(room_after.summary["databaseVersion"] == 28, f"Room version {room_after.summary['databaseVersion']} != 28")
        expected_kinds = (["PHOTO"] if photo else []) + (["VIDEO"] if video else [])
        step.check(sorted(r_["kind"] for r_ in rows) == sorted(expected_kinds),
                   f"expected rows {expected_kinds}, Room has {[r_['kind'] for r_ in rows]}")
        records = []
        for row in rows:
            kind = row["kind"]
            start = ctx["photoStart"] if kind == "PHOTO" else ctx["videoStart"]
            end = ctx["photoEnd"] if kind == "PHOTO" else ctx["videoEnd"]
            problem = capture_row_problem(row, kind=kind, session_key=session_key, identity=identity)
            step.check(problem is None, problem or "")
            file_info = fetch_media_file(s, row, kind.lower())
            step.check(file_info["bytes"] > 2048, f"{kind} file is implausibly small ({file_info['bytes']} bytes)")
            if kind == "PHOTO":
                step.check(file_info["format"] == "JPEG" and file_info.get("jpeg", {}).get("width"), f"photo is not a valid JPEG: {file_info['format']}")
            else:
                mp4 = file_info.get("mp4") or {}
                step.check(file_info["format"] == "MP4" and mp4.get("hasVideoTrack"), f"video is not a valid MP4 with a video track: {file_info['format']}")
                step.check((mp4.get("durationMs") or 0) >= max(1000, int(args.video_seconds * 1000 * 0.4)),
                           f"MP4 duration {mp4.get('durationMs')} ms is far below the {args.video_seconds}s recording")
                window_s = ctx.get("recordedWindowSeconds")
                if window_s is not None and mp4.get("durationMs"):
                    # upper bound: the encoder cannot hold more footage than elapsed between the two taps (+3 s slack for
                    # tap dispatch / muxer finalisation / clock read latency)
                    step.check(mp4["durationMs"] <= int(window_s * 1000) + 3000,
                               f"MP4 duration {mp4['durationMs']} ms exceeds the {window_s}s 'Vídeo'->'Parar' window")
                    step.evidence(mp4DurationMs=mp4["durationMs"], tapWindowSeconds=window_s)
                if args.audio == "deny":
                    step.check(not mp4.get("hasAudioTrack"), "audio was denied but the MP4 has an audio track")
                thumb = file_info.get("thumb")
                step.check(bool(thumb and thumb["exists"] and thumb["bytes"] > 0), f"video thumbnail file missing: {thumb}")
            records.append({"row": row, "file": file_info, "window": [start, end]})
        ctx["records"] = records
        write_json(s.out / "captured-media.json", records)
        step.detail(f"{len(rows)} Room row(s) with UUID ids and real encoder files in files/workout_media")
        step.evidence(rows=[x["row"]["id"] for x in records])

    with r.step("origin_is_camerax_not_import") as step:
        via_picker = any(t["selector"] == mf.GALLERY_CHIP for t in s.taps)
        origins = []
        for record in ctx["records"]:
            row, info = record["row"], record["file"]
            verdict = mc.classify_origin(
                kind=row["kind"], file_format=info["format"], sha256=info["sha256"], import_fixture_hashes=ctx["fixtureHashes"],
                interaction="shutter", created_in_window=window_contains(row.get("createdAtMs"), *record["window"]),
                via_picker=via_picker)
            origins.append({"id": row["id"], "kind": row["kind"], **verdict})
            step.check(verdict["origin"] == "CAMERAX_CAPTURE", f"{row['kind']} {row['id']} classified {verdict}")
        s.rec.extra["captureClaim"] = "REAL_CAMERAX_CAPTURE"
        s.rec.extra["origins"] = origins
        step.detail("; ".join(f"{o['kind']} {o['id'][:8]} -> {o['origin']}" for o in origins))
        step.evidence(origins=origins, galleryTapped=via_picker)

    if args.finish:
        with r.step("finish_binds_captured_media") as step:
            # The product refuses to finish a session with zero registered sets (diagnostic event
            # finish_blocked_empty_session; the confirm button is a silent no-op), so register S1 first - exactly
            # as ui_media_import does - and use the post-S1 Room snapshot as the "before" of the binding check.
            s.cold_restart(fixtures.WORKOUT_URI, "reopen-register")
            s.type_primary("20", "weight-s1")
            register_once(s, "s1")
            settle_after_register(s, "s1")
            s.wait_ui(lambda x: "1/2" in x.tokens or "Registrar serie" in x.tokens, "s1-registered", timeout=20, png=True,
                      what="surface after S1")
            before_finish = s.room("before-finish")
            problem_s1, s1_row = mc.s1_active_problem(before_finish.summary, session_key, 20.0, 6)
            step.check(problem_s1 is None, problem_s1 or "")
            before = before_finish.summary
            s.cold_restart(fixtures.WORKOUT_URI, "reopen-finish")
            home = mf.finish_workout(s)
            after = s.room("after-finish")
            problem, info = mc.finish_binding_problem(before, after.summary, session_key)
            step.check(problem is None, problem or "")
            step.detail(f"captured rows bound to log {info['workoutLogId']} via the durable association")
            step.evidence(**info)


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    parser.add_argument("--mode", choices=("photo", "video", "both"), default="both")
    parser.add_argument("--permissions", choices=("pm", "dialog"), default="pm",
                        help="pm: pre-grant CAMERA/RECORD_AUDIO with 'pm grant'; dialog: answer the system dialogs")
    parser.add_argument("--audio", choices=("grant", "deny"), default="grant", help="RECORD_AUDIO policy for the video")
    parser.add_argument("--video-seconds", type=float, default=4.0)
    parser.add_argument("--bind-wait", type=float, default=4.0, help="seconds to let the CameraX preview deliver frames")
    parser.add_argument("--capture-timeout", type=float, default=45.0, help="seconds to wait for a thumbnail after a capture")
    parser.add_argument("--finish", action="store_true", help="finish the workout and verify the session->log association")
    args = parser.parse_args(argv)
    args.label = args.label or f"{args.mode}-{args.permissions}"
    return execute("camerax-capture", args, lambda session: run(session, args))


if __name__ == "__main__":
    raise SystemExit(main())
