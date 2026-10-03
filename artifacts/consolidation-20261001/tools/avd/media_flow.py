"""UI + device helpers for the workout media drivers (picker import, album, Finish).

The Android picker (DocumentsUI) is a system app: its tree is navigated with the same
fresh-XML single-tap rules.  Because its layout varies per system image, the file is located by
(1) direct visibility, (2) the picker's search field, (3) folder-by-folder navigation; every
attempt is archived so a failure shows exactly what the picker displayed.
"""

from __future__ import annotations

import struct
import time
import uuid
import zlib
from pathlib import Path
from typing import Any

import fixtures
import uia
from evidence import StepFailure, sha256_file, utc_stamp
from qa_common import QaSession, UiShot
from qa_paths import FIXTURES_DIR

MEDIA_FACE_TRIGGER = "text:Ver ejercicio/Fotos"
GALLERY_CHIP = "text:Galería"
PHOTO_CHIP = "text:Foto"
VIDEO_CHIP = "text:Vídeo"
STOP_CHIP = "text:Parar"
ALBUM_BUTTON = "desc:Álbum"
BACK_TO_SET = "desc:Volver a la serie"
ALBUM_TITLE = "Álbum de esta sesión"
EXIT_DIALOG_TITLE = "¿Qué deseas hacer?"
FINISH_PROMPT = "text:Terminar hasta acá"
FINISH_CONFIRM = "desc:Guardar y terminar entrenamiento"
SEARCH_SELECTORS = (
    "id:com.google.android.documentsui:id/option_menu_search",
    "id:com.android.documentsui:id/option_menu_search",
    "desc:Search", "desc:Buscar", "desc:Search files",
)
ROOTS_BUTTONS = ("desc:Show roots", "desc:Mostrar raíces", "desc:Mostrar carpetas raíz")


def make_png(width: int, height: int, seed: int) -> bytes:
    """Deterministic RGB PNG (no external libs): coloured stripes derived from ``seed``."""
    rows = bytearray()
    for y in range(height):
        rows.append(0)  # filter type 0
        for x in range(width):
            band = (x // 8 + y // 8 + seed) % 4
            r, g, b = [(230, 90, 70), (240, 200, 90), (80, 175, 120), (60, 90, 150)][band]
            rows += bytes(((r + seed * 7) % 256, (g + seed * 3) % 256, b))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b""))


def push_import_media(s: QaSession, *, late_photo: bool = False) -> dict[str, Any]:
    """Push the synthetic PNG/MP4 (unique names) into shared storage and verify their device hashes."""
    token = f"KPKNFit_QA_{utc_stamp()[:15]}_{uuid.uuid4().hex[:8]}"
    short = token[-8:]
    photo_src = FIXTURES_DIR / "media" / fixtures.PHOTO_NAME
    video_src = FIXTURES_DIR / "media" / fixtures.VIDEO_NAME
    photo_name, video_name = f"kpkn-qa-photo-{short}.png", f"kpkn-qa-video-{short}.mp4"
    items = [("photo", photo_src, f"/sdcard/Pictures/{token}", photo_name),
             ("video", video_src, f"/sdcard/Movies/{token}", video_name)]
    late_path: Path | None = None
    if late_photo:
        late_bytes = make_png(96, 72, seed=5)
        late_path = s.out / f"late-photo-{short}.png"
        late_path.write_bytes(late_bytes)
        items.append(("latePhoto", late_path, f"/sdcard/Pictures/{token}", f"kpkn-qa-late-{short}.png"))
    result: dict[str, Any] = {"token": token, "files": {}}
    for kind, source, directory, name in items:
        s.avd.shell("mkdir", "-p", directory)
        remote = f"{directory}/{name}"
        s.avd.push(source, remote)
        device_hash = s.avd.remote_sha256(remote)
        expected = sha256_file(source)
        if device_hash != expected:
            raise StepFailure(f"{remote}: device sha256 {device_hash} != host {expected}")
        s.avd.shell("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", f"file://{remote}",
                    check=False)
        result["files"][kind] = {"name": name, "remote": remote, "directory": directory, "sha256": device_hash,
                                 "bytes": source.stat().st_size, "folder": directory.rsplit("/", 1)[-1]}
    return result


# ---------------------------------------------------------------------------
# Workout UI navigation
# ---------------------------------------------------------------------------

def tap_target(s: QaSession, target: dict[str, Any], selector: str, label: str, shot: UiShot,
               candidates: list[dict[str, Any]], *, settle: float = 1.0) -> dict[str, Any]:
    """Tap an already resolved candidate exactly once and log it like QaSession.tap."""
    s.avd.tap(*target["center"])
    event = {"label": label, "selector": selector, "withinText": None, "target": target, "beforeCapture": shot.name,
             "tapCount": 1, "candidates": [c["bounds"] for c in candidates]}
    s.taps.append(event)
    s._log("tap", event)
    time.sleep(settle)
    return event


def tap_widest(s: QaSession, selector: str, label: str, *, settle: float = 1.0) -> dict[str, Any]:
    """Tap the widest on-screen control matching ``selector`` (neighbouring pager cards peek in at the edge)."""
    shot = s.capture_ui(label + "-before")
    candidates = uia.clickable_candidates(shot.xml_text, selector)
    if not candidates:
        raise StepFailure(f"tap {selector!r}: no enabled clickable on-screen node; {s.diagnostics(shot)}")
    target = max(candidates, key=lambda item: item["width"])
    if len(candidates) > 1 and sorted(c["width"] for c in candidates)[-2] * 2 > target["width"]:
        raise StepFailure(f"tap {selector!r}: {len(candidates)} similarly sized candidates "
                          f"{[c['bounds'] for c in candidates]}; refusing to guess")
    return tap_target(s, target, selector, label, shot, candidates, settle=settle)


def open_media_face(s: QaSession, label: str = "media-face") -> UiShot:
    tap_widest(s, MEDIA_FACE_TRIGGER, label + "-open")
    return s.wait_ui(lambda x: x.has("Técnica KPKN") or x.present(BACK_TO_SET), label, timeout=20, png=True,
                     what="exercise media back face (Técnica KPKN / Volver a la serie)")


def close_media_face(s: QaSession, label: str = "media-face-close") -> UiShot:
    s.tap(BACK_TO_SET, label)
    return s.wait_ui(lambda x: x.has("Registrar serie"), label + "-done", timeout=20, what="set card front (Registrar serie)")


def in_picker(shot: UiShot) -> bool:
    return "documentsui" in (shot.top_resumed or "").lower()


def pick_file(s: QaSession, filename: str, folder_chain: list[str], label: str, *, max_steps: int = 18) -> dict[str, Any]:
    """Locate ``filename`` in the system picker and tap it (single tap, unique name required)."""
    log: list[dict[str, Any]] = []
    searched = roots_opened = False
    visited: set[str] = set()
    for step in range(max_steps):
        shot = s.capture_ui(f"{label}-picker{step:02d}", png=(step < 2))
        if not in_picker(shot):
            if step < 4:
                time.sleep(1.5)
                continue
            raise StepFailure(f"the document picker is not in the foreground (top={shot.top_resumed!r}); {s.diagnostics(shot)}")
        selector = f"text:{filename}"
        # After a picker search the typed query (an EditText holding the same text) matches the selector too; only
        # file tiles/rows count (tool fix, base AVD validation 2026-10-02).
        tiles = [c for c in uia.clickable_candidates(shot.xml_text, selector)
                 if not c["class"].endswith(("EditText", "AutoCompleteTextView")) and "search" not in c["resourceId"].lower()]
        if len(tiles) > 1:
            raise StepFailure(f"{filename} appears more than once in the picker: {[c['bounds'] for c in tiles]}")
        if tiles:
            event = tap_target(s, tiles[0], selector, f"{label}-select-file", shot, tiles)
            log.append({"step": step, "action": "select", "event": event["target"]["bounds"]})
            return {"filename": filename, "steps": log}
        if not searched:
            for candidate in SEARCH_SELECTORS:
                try:
                    uia.resolve_clickable_target(shot.xml_text, candidate)
                except uia.TargetResolutionError:
                    continue
                s.tap(candidate, f"{label}-search", shot=shot, settle=1.0)
                s.avd.input_text(filename)
                s.avd.keyevent("KEYCODE_ENTER")
                time.sleep(1.5)
                searched = True
                log.append({"step": step, "action": "search", "selector": candidate})
                break
            if searched:
                continue
        progressed = False
        for folder in folder_chain:
            if folder not in visited and shot.present(f"text:{folder}"):
                try:
                    s.tap(f"text:{folder}", f"{label}-folder", shot=shot)
                except StepFailure:
                    continue
                visited.add(folder)
                log.append({"step": step, "action": "folder", "name": folder})
                progressed = True
                break
        if progressed:
            continue
        if not roots_opened:
            for candidate in ROOTS_BUTTONS:
                if shot.present(candidate):
                    s.tap(candidate, f"{label}-roots", shot=shot)
                    roots_opened = True
                    log.append({"step": step, "action": "roots", "selector": candidate})
                    break
            if roots_opened:
                continue
        if step > 0 and log and log[-1]["action"] == "roots":
            for root in ("Images", "Imágenes", "Videos", "Downloads", "Descargas"):
                if shot.present(f"text:{root}"):
                    s.tap(f"text:{root}", f"{label}-root", shot=shot)
                    log.append({"step": step, "action": "root", "name": root})
                    break
    raise StepFailure(f"could not locate {filename!r} in the picker after {max_steps} steps: {log}")


def import_via_gallery(s: QaSession, info: dict[str, Any], kind: str, label: str) -> dict[str, Any]:
    """Tap Galería on the media face and pick the pushed file; returns picker evidence + timing."""
    file = info["files"][kind]
    start_ms = s.avd.device_time_ms()
    s.tap(GALLERY_CHIP, label + "-gallery")
    picked = pick_file(s, file["name"], ["Pictures" if kind != "video" else "Movies", file["folder"]], label)
    s.avd.wait_main_top_resumed(timeout=40)
    return {"kind": kind, "file": file, "picker": picked, "startedAtDeviceMs": start_ms}


def open_album(s: QaSession, label: str = "album") -> UiShot:
    s.tap(ALBUM_BUTTON, label + "-open")
    return s.wait_ui(lambda x: x.has(ALBUM_TITLE), label, timeout=20, png=True, what="session album sheet")


def close_album(s: QaSession, label: str = "album-close") -> UiShot:
    s.key("KEYCODE_BACK", label + "-back", settle=0.8)
    shot = s.capture_ui(label + "-probe")
    if shot.has(ALBUM_TITLE):
        raise StepFailure("Back did not close the album sheet")
    return shot


# ---------------------------------------------------------------------------
# Finish
# ---------------------------------------------------------------------------

def finish_workout(s: QaSession) -> UiShot:
    """Back -> exit dialog -> Terminar hasta acá -> finish editor -> Guardar y terminar -> Home."""
    s.key("KEYCODE_BACK", "finish-back", settle=0.6)
    s.wait_ui(lambda x: x.has(EXIT_DIALOG_TITLE) and x.present(FINISH_PROMPT), "finish-exit-dialog", timeout=15, png=True,
              what="exit dialog with 'Terminar hasta acá'")
    s.tap(FINISH_PROMPT, "finish-up-to-here")
    s.wait_ui(lambda x: x.present(FINISH_CONFIRM), "finish-editor", timeout=30, png=True, what="finish editor (Guardar y terminar entrenamiento)")
    s.tap(FINISH_CONFIRM, "finish-confirm")
    home = s.wait_home("home-after-finish")
    if home.has("Registrar serie") or home.present(FINISH_CONFIRM):
        raise StepFailure("workout controls still visible after Finish")
    return home
