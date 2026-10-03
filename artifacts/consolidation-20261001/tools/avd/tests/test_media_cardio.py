"""Media assertions, probing of encoder output, CameraX origin classification, cardio profiles."""

from __future__ import annotations

import hashlib
import io
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import camerax_capture as cx  # noqa: E402
import cardio_profiles as cp  # noqa: E402
import fixtures  # noqa: E402
import gps_tools  # noqa: E402
import media_checks as mc  # noqa: E402
import media_flow as mf  # noqa: E402
import media_probe as mp  # noqa: E402

UUID_A, UUID_B, UUID_C = ("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "cccccccc-cccc-4ccc-8ccc-cccccccccccc")
KEY = "P::S::1790720640166"
BASE = "/data/user/0/com.example.kpkn/files/workout_media/2026-10/"


def row(uid, kind="PHOTO", ext="jpg", **extra):
    base = {"id": uid, "kind": kind, "filePath": f"{BASE}{uid}.{ext}", "createdAtMs": 1000, "sessionKey": KEY, "workoutLogId": None,
            "programId": "P", "sessionId": "S", "exerciseId": fixtures.SQUAT_EXERCISE_ID, "exerciseName": fixtures.SQUAT_DISPLAY_NAME,
            "setIndex": 0, "side": None, "weightKg": None, "reps": None}
    base.update(extra)
    return base


class MediaChecksTests(unittest.TestCase):
    def summary(self, rows, associations=(), version=28):
        return {"databaseVersion": version, "mediaRows": rows, "associations": list(associations), "workoutLogIds": ["L1"],
                "ongoing": {"sessionKey": KEY}}

    def test_session_media_ok_and_each_failure(self):
        ok = self.summary([row(UUID_A), row(UUID_B, "VIDEO", "mp4")])
        self.assertIsNone(mc.session_media_problem(ok, KEY, ["PHOTO", "VIDEO"]))
        self.assertIn("kinds", mc.session_media_problem(ok, KEY, ["PHOTO", "PHOTO"]))
        self.assertIn("v28", mc.session_media_problem(self.summary([row(UUID_A)], version=27), KEY, ["PHOTO"]))
        self.assertIn("UUID", mc.session_media_problem(self.summary([row("not-a-uuid")]), KEY, ["PHOTO"]))
        self.assertIn("attached", mc.session_media_problem(self.summary([row(UUID_A, workoutLogId="L1")]), KEY, ["PHOTO"]))
        self.assertIn("association", mc.session_media_problem(
            self.summary([row(UUID_A)], [{"sessionKey": KEY, "workoutLogId": "L1"}]), KEY, ["PHOTO"]))
        self.assertIn("outside", mc.session_media_problem(self.summary([row(UUID_A, filePath="/sdcard/x.jpg")]), KEY, ["PHOTO"]))
        self.assertIn("non-video", mc.session_media_problem(self.summary([row(UUID_A, "VIDEO", "jpg")]), KEY, ["VIDEO"]))

    def test_finish_binding(self):
        before = self.summary([row(UUID_A), row(UUID_B, "VIDEO", "mp4")])
        after = self.summary([row(UUID_A, workoutLogId="L1"), row(UUID_B, "VIDEO", "mp4", workoutLogId="L1")],
                             [{"sessionKey": KEY, "workoutLogId": "L1"}])
        after["ongoing"] = None
        problem, info = mc.finish_binding_problem(before, after, KEY)
        self.assertIsNone(problem)
        self.assertEqual(info["workoutLogId"], "L1")
        for mutate, text in ((lambda a: a["associations"].clear(), "exactly one"),
                             (lambda a: a["mediaRows"][0].update(workoutLogId=None), "not attached"),
                             (lambda a: a["mediaRows"][0].update(exerciseName="Other"), "frozen"),
                             (lambda a: a["workoutLogIds"].clear(), "missing workout log"),
                             (lambda a: a.update(ongoing={"sessionKey": KEY}), "ongoing_workout"),
                             (lambda a: a["mediaRows"].pop(), "ids changed")):
            broken = {**after, "mediaRows": [dict(r) for r in after["mediaRows"]], "associations": list(after["associations"]),
                      "workoutLogIds": list(after["workoutLogIds"])}
            mutate(broken)
            self.assertIn(text, mc.finish_binding_problem(before, broken, KEY)[0], text)

    def test_s1_and_finalized_set(self):
        s1 = {"id": UUID_C, "exerciseId": fixtures.SQUAT_EXERCISE_ID, "setIndex": 0, "weight": 20.0, "reps": 6}
        summary = {"ongoing": {"sessionKey": KEY, "completedSets": [s1]}}
        problem, row_ = mc.s1_active_problem(summary, KEY, 20.0, 6)
        self.assertIsNone(problem)
        self.assertIn("20", mc.s1_active_problem(summary, KEY, 25.0, 6)[0])
        log = {"recentTargetExerciseLogs": [{"id": "L1", "targetExerciseSets": [dict(s1)]}]}
        self.assertIsNone(mc.finalized_set_problem(log, "L1", row_))
        self.assertIn("UUID", mc.finalized_set_problem({"recentTargetExerciseLogs": [{"id": "L1", "targetExerciseSets": [dict(s1, id=UUID_A)]}]}, "L1", row_))


class OriginTests(unittest.TestCase):
    FIXTURE_HASHES = {"f" * 64, "e" * 64}

    def classify(self, **overrides):
        base = dict(kind="PHOTO", file_format="JPEG", sha256="a" * 64, import_fixture_hashes=self.FIXTURE_HASHES,
                    interaction="shutter", created_in_window=True, via_picker=False)
        base.update(overrides)
        return mc.classify_origin(**base)

    def test_camerax_capture_vs_import(self):
        self.assertEqual(self.classify()["origin"], "CAMERAX_CAPTURE")
        self.assertEqual(self.classify(kind="VIDEO", file_format="MP4")["origin"], "CAMERAX_CAPTURE")
        self.assertEqual(self.classify(sha256="f" * 64, file_format="PNG")["origin"], "IMPORT")           # fixture bytes
        self.assertEqual(self.classify(interaction="picker", file_format="PNG")["origin"], "IMPORT")       # Galería
        self.assertEqual(self.classify(via_picker=True)["origin"], "IMPORT")                               # picker in foreground
        self.assertEqual(self.classify(file_format="PNG")["origin"], "UNKNOWN")                            # shutter but PNG bytes
        self.assertEqual(self.classify(created_in_window=False)["origin"], "UNKNOWN")
        self.assertEqual(self.classify(kind="VIDEO", file_format="JPEG")["origin"], "UNKNOWN")

    def test_sniffing(self):
        self.assertEqual(mc.sniff_media_format(b"\x89PNG\r\n\x1a\n" + b"0" * 8), "PNG")
        self.assertEqual(mc.sniff_media_format(b"\xff\xd8\xff\xe0" + b"0" * 12), "JPEG")
        self.assertEqual(mc.sniff_media_format(b"\x00\x00\x00\x18ftypisom" + b"0" * 4), "MP4")
        self.assertEqual(mc.sniff_media_format(b"junkjunkjunkjunk"), "UNKNOWN")

    def test_real_fixtures_classify_as_import_and_never_as_capture(self):
        photo = fixtures.fixture_path("qa-photo").read_bytes()
        video = fixtures.fixture_path("qa-video").read_bytes()
        hashes = {hashlib.sha256(photo).hexdigest(), hashlib.sha256(video).hexdigest()}
        for kind, data in (("PHOTO", photo), ("VIDEO", video)):
            verdict = mc.classify_origin(kind=kind, file_format=mc.sniff_media_format(data[:16]), sha256=hashlib.sha256(data).hexdigest(),
                                         import_fixture_hashes=hashes, interaction="shutter", created_in_window=True, via_picker=False)
            self.assertEqual(verdict["origin"], "IMPORT", kind)


class ProbeTests(unittest.TestCase):
    def test_png_generator_is_a_valid_deterministic_image(self):
        first, second = mf.make_png(96, 72, 5), mf.make_png(96, 72, 5)
        self.assertEqual(first, second)
        self.assertNotEqual(first, mf.make_png(96, 72, 6))
        self.assertEqual(mc.sniff_media_format(first[:16]), "PNG")
        try:
            from PIL import Image
        except ImportError:  # pragma: no cover
            return
        image = Image.open(io.BytesIO(first))
        image.load()
        self.assertEqual(image.size, (96, 72))

    def test_jpeg_info(self):
        try:
            from PIL import Image
        except ImportError:  # pragma: no cover
            self.skipTest("Pillow not installed")
        buffer = io.BytesIO()
        Image.new("RGB", (64, 48), (200, 30, 30)).save(buffer, "JPEG")
        info = mp.jpeg_info(buffer.getvalue())
        self.assertEqual((info["width"], info["height"]), (64, 48))
        self.assertTrue(info["eoi"])
        with self.assertRaises(ValueError):
            mp.jpeg_info(b"\x89PNG....")
        with self.assertRaises(ValueError):
            mp.jpeg_info(b"\xff\xd8\xff\xd9")

    def test_mp4_info_on_the_fixture_and_on_truncated_files(self):
        data = fixtures.fixture_path("qa-video").read_bytes()
        info = mp.mp4_info(data)
        self.assertEqual((info["brand"], info["durationMs"], info["hasVideoTrack"], info["hasAudioTrack"]), ("isom", 2000, True, False))
        self.assertEqual((info["tracks"][0]["width"], info["tracks"][0]["height"]), (320, 240))
        moov = data.find(b"moov")
        with self.assertRaises(ValueError):  # recording never finalized: ftyp+mdat but no moov
            mp.mp4_info(data[: moov - 4])
        with self.assertRaises(ValueError):
            mp.mp4_info(b"RIFF....")

    def test_camera_service_dump_helpers(self):
        dump = ("== Service global info: ==\nNumber of camera devices: 2\n== Camera service events log ==\n"
                "10-01 20:00:00 : CONNECT device 0 client for package com.example.kpkn (PID 4242) for Camera API 2\n")
        self.assertEqual(mp.camera_device_count(dump), 2)
        evidence = mp.camera_service_evidence(dump, "com.example.kpkn")
        self.assertTrue(evidence["found"])
        self.assertFalse(mp.camera_service_evidence("Number of camera devices: 1\n", "com.example.kpkn")["found"])
        self.assertEqual(len(mp.logcat_camera_lines("I CameraService: connect com.example.kpkn\nI other: com.example.kpkn\n", "com.example.kpkn")), 1)


class CameraxHelperTests(unittest.TestCase):
    @staticmethod
    def face(photo_thumbs=0, video_marks=0, technique=True):
        nodes = []
        if technique:
            nodes.append('<node content-desc="Foto de técnica de Sentadilla Trasera con Barra Baja" bounds="[10,300][1000,420]"/>')
        for index in range(photo_thumbs):
            left = 10 + index * 180
            nodes.append(f'<node content-desc="{fixtures.SQUAT_DISPLAY_NAME}" clickable="true" bounds="[{left},2000][{left + 168},2168]"/>')
        for index in range(video_marks):
            nodes.append(f'<node content-desc="Vídeo" bounds="[{60 + index * 180},2050][{150 + index * 180},2140]"/>')
        return "<hierarchy><node bounds=\"[0,0][1080,2400]\">" + "".join(nodes) + "</node></hierarchy>"

    def test_thumb_counts_ignore_the_technique_photo(self):
        self.assertEqual(cx.thumb_counts(self.face(0, 0), fixtures.SQUAT_DISPLAY_NAME), {"photoThumbs": 0, "videoMarks": 0, "squareThumbs": 0})
        counts = cx.thumb_counts(self.face(2, 1), fixtures.SQUAT_DISPLAY_NAME)
        self.assertEqual((counts["photoThumbs"], counts["videoMarks"], counts["squareThumbs"]), (2, 1, 2))
        self.assertTrue(cx.grew({"photoThumbs": 1, "squareThumbs": 1}, {"photoThumbs": 1, "squareThumbs": 2}, "photoThumbs", "squareThumbs"))
        self.assertFalse(cx.grew({"photoThumbs": 1}, {"photoThumbs": 1}, "photoThumbs"))

    def test_structural_detection_ignores_buttons_and_chips(self):
        xml = ("<hierarchy><node bounds=\"[0,0][1080,2400]\">"
               "<node clickable=\"true\" bounds=\"[10,100][106,196]\" content-desc=\"Álbum\"/>"       # 32 dp icon button
               "<node clickable=\"true\" bounds=\"[10,300][400,400]\"><node text=\"Foto\" bounds=\"[10,300][400,400]\"/></node>"  # chip
               "<node clickable=\"true\" bounds=\"[10,2000][178,2168]\"/>"                                   # 56 dp thumbnail without any description
               "</node></hierarchy>")
        self.assertEqual(cx.thumb_counts(xml, fixtures.SQUAT_DISPLAY_NAME, 480)["squareThumbs"], 1)

    def test_density_parsing(self):
        self.assertEqual(cx.density_dpi("Physical density: 480"), 480)
        self.assertEqual(cx.density_dpi("Physical density: 420\nOverride density: 560"), 560)
        self.assertEqual(cx.density_dpi(None), 480)

    def test_capture_row_problems(self):
        identity = {"programId": "P", "sessionId": "S"}
        good = row(UUID_A)
        self.assertIsNone(cx.capture_row_problem(good, kind="PHOTO", session_key=KEY, identity=identity))
        self.assertIn("no workout_media row", cx.capture_row_problem(None, kind="PHOTO", session_key=KEY, identity=identity))
        self.assertIn("UUID", cx.capture_row_problem(row("123"), kind="PHOTO", session_key=KEY, identity=identity))
        self.assertIn("kind", cx.capture_row_problem(row(UUID_A, "VIDEO", "mp4"), kind="PHOTO", session_key=KEY, identity=identity))
        self.assertIn("sessionKey", cx.capture_row_problem(row(UUID_A, sessionKey="other"), kind="PHOTO", session_key=KEY, identity=identity))
        self.assertIn("exerciseId", cx.capture_row_problem(row(UUID_A, exerciseId="x"), kind="PHOTO", session_key=KEY, identity=identity))
        self.assertIn("log", cx.capture_row_problem(row(UUID_A, workoutLogId="L"), kind="PHOTO", session_key=KEY, identity=identity))
        self.assertIn("<uuid>.jpg", cx.capture_row_problem(row(UUID_A, ext="png", filePath=f"{BASE}{UUID_A}.png"), kind="PHOTO",
                                                           session_key=KEY, identity=identity) or "")

    def test_window_and_ini(self):
        self.assertTrue(cx.window_contains(10_000, 9_000, 11_000))
        self.assertFalse(cx.window_contains(1_000, 9_000, 11_000))
        self.assertFalse(cx.window_contains(None, 1, 2))
        parsed = cx.parse_ini("hw.camera.back = emulated\n# c\nhw.camera.front=none\nhw.lcd.width=1344\n")
        self.assertEqual((parsed["hw.camera.back"], parsed["hw.camera.front"]), ("emulated", "none"))

    def test_error_banner_and_preview_state(self):
        class Shot:
            def __init__(self, xml):
                self.xml_text = xml
                self.tokens = __import__("uia").ui_tokens(xml)

            def has(self, label):
                return __import__("uia").has_exact_label(self.xml_text, label)

        off = Shot('<hierarchy><node text="Activa la cámara para grabar desde esta tarjeta" bounds="[0,0][1,1]"/></hierarchy>')
        self.assertFalse(cx.preview_ready(off))
        on = Shot('<hierarchy><node bounds="[0,0][1080,2400]"><node clickable="true" enabled="true" bounds="[0,2200][300,2300]"><node text="Foto" bounds="[0,2200][300,2300]"/></node>'
                  '<node clickable="true" enabled="true" bounds="[320,2200][620,2300]"><node text="Vídeo" bounds="[320,2200][620,2300]"/></node></node></hierarchy>')
        self.assertTrue(cx.preview_ready(on))
        self.assertIsNone(cx.capture_error(on))
        err = Shot('<hierarchy><node text="No se pudo guardar la foto. Podés reintentar." bounds="[0,0][1,1]"/></hierarchy>')
        self.assertIn("No se pudo", cx.capture_error(err))


class CardioProfileTests(unittest.TestCase):
    BASE_TOKENS = [cp.CARDIO_EXERCISE_NAME, "Serie 1", "Serie 2"]

    def test_profiles(self):
        ready = self.BASE_TOKENS + ["Iniciar GPS", "Listo · objetivo 30:00"]
        self.assertIsNone(cp.profile_problem(ready, "cardio-ready"))
        self.assertIn("unexpected confirmation", cp.profile_problem(ready + ["Confirmar cardio"], "cardio-ready"))
        running = self.BASE_TOKENS + ["Pausar GPS", "En curso · 00:10", "Señal GPS activa · 0,01 km"]
        self.assertIsNone(cp.profile_problem(running, "gps-running"))
        self.assertIsNotNone(cp.profile_problem(ready, "gps-running"))
        paused = self.BASE_TOKENS + ["Reanudar GPS", "Pausado · 00:12", "GPS pausado"]
        self.assertIsNone(cp.profile_problem(paused, "gps-paused"))
        self.assertIsNone(cp.profile_problem(self.BASE_TOKENS + ["Confirmar cardio", "Registrar", "Cancelar"], "confirm-cardio"))
        self.assertIsNotNone(cp.profile_problem(self.BASE_TOKENS + ["Confirmar cardio", "Registrar"], "confirm-cardio"))
        self.assertIsNone(cp.profile_problem(self.BASE_TOKENS + ["Pausado · 00:12", "Finalizar y registrar"], "after-cancel"))
        after_s1 = [cp.CARDIO_EXERCISE_NAME, "Serie 1 · Registrada", "Serie 2", "Iniciar GPS", "Listo · objetivo 30:00"]
        self.assertIsNone(cp.profile_problem(after_s1, "after-s1"))
        self.assertIsNotNone(cp.profile_problem(after_s1 + ["Cardio registrado · 5 km"], "after-s1"))
        after_s2 = [cp.CARDIO_EXERCISE_NAME, "Serie 1 · Registrada", "Serie 2 · Registrada"]
        self.assertIsNone(cp.profile_problem(after_s2, "after-s2"))
        self.assertIsNotNone(cp.profile_problem(["nothing"], "gps-paused"))
        self.assertIn("unknown", cp.profile_problem([], "bogus"))

    def test_room_checkpoints(self):
        key0, key1 = f"{fixtures.SQUAT_EXERCISE_ID}_0", f"{fixtures.SQUAT_EXERCISE_ID}_1"

        def state(completed, index, timer=None, version=28):
            return {"databaseVersion": version, "workoutLogCount": 0, "ongoing": {
                "programId": fixtures.PROGRAM_ID, "sessionId": fixtures.MONDAY_SESSION_ID, "startTimeMs": 1790720640166,
                "activeExerciseId": fixtures.SQUAT_EXERCISE_ID, "activeSetIndex": index, "cardioTimerState": timer,
                "cardioCompletedSets": {k: {} for k in completed}}}

        args = (fixtures.PROGRAM_ID, fixtures.MONDAY_SESSION_ID)
        paused = {"status": "PAUSED", "setId": "qa-cardio-series-0"}
        self.assertIsNone(cp.checkpoint_problem(state([], 0, paused), "paused-s1", *args))
        self.assertIn("timer identity", cp.checkpoint_problem(state([], 0, {"status": "RUNNING", "setId": "qa-cardio-series-0"}), "paused-s1", *args))
        self.assertIsNone(cp.checkpoint_problem(state([key0], 1), "after-s1", *args))
        self.assertIn("only the first", cp.checkpoint_problem(state([key0, key1], 1), "after-s1", *args))
        self.assertIn("cursor", cp.checkpoint_problem(state([key0], 0), "after-s1", *args))
        self.assertIsNone(cp.checkpoint_problem(state([key0, key1], 1), "after-s2", *args))
        self.assertIn("both", cp.checkpoint_problem(state([key0], 1), "after-s2", *args))
        self.assertIn("v28", cp.checkpoint_problem(state([], 0, paused, version=27), "paused-s1", *args))


class GpsToolsTests(unittest.TestCase):
    def test_synthetic_walk_is_deterministic_and_moves(self):
        points = [gps_tools.synthetic_point(i) for i in range(10)]
        self.assertEqual(points[0], (gps_tools.BASE_LON, gps_tools.BASE_LAT))
        self.assertEqual(len(set(points)), 10)
        self.assertGreater(points[7][0], points[0][0])
        self.assertGreater(points[8][1], points[0][1])


if __name__ == "__main__":
    unittest.main()
