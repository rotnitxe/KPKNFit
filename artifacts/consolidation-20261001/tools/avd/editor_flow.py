"""Navigation shared by the session-editor drivers (drafts, transfer).

Ported from OLDROOT ``editor_drafts_avd.py`` / ``batch_editor_benchmark.py`` helpers:
program deep link -> optional "Más tarde" -> Monday page -> scoped ``Edit`` button of
the "Día 1" card -> editor ("Ver semana" visible).  Every tap is resolved from a fresh
dump; the cold-process route is entered through a hydrated Home first.
"""

from __future__ import annotations

import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import fixtures
import uia
from evidence import StepFailure
from qa_common import QaSession, UiShot

PAGE_SELECTOR = "text:Lunes"
EDITOR_BUTTON = "desc:Edit"
DAY_LABEL = "Día 1"
DESCRIPTION_SELECTOR = "text:Añadir descripción"
EDITOR_MARK = "text:Ver semana"
EXIT_DIALOG = "text:Salir del editor"
DEFAULT_WEEK_ID = fixtures.WEEK_ID


def draft_key(program_id: str = fixtures.PROGRAM_ID, week_id: str = DEFAULT_WEEK_ID,
              session_id: str = fixtures.MONDAY_SESSION_ID, macro_index: int = 0, meso_index: int = 0) -> str:
    return f"program={program_id}|week={week_id or '__unspecified_week__'}|macro={macro_index}|meso={meso_index}|editor={session_id}"


def parse_preferences_xml(payload: bytes | str) -> dict[str, str]:
    raw = payload.decode("utf-8") if isinstance(payload, bytes) else payload
    root = ET.fromstring(raw)
    if root.tag != "map":
        raise ValueError(f"SharedPreferences root must be <map>, got <{root.tag}>")
    values: dict[str, str] = {}
    for item in root:
        name = item.attrib.get("name")
        if not name:
            continue
        if item.tag == "string":
            values[name] = item.text or ""
        elif item.tag in {"boolean", "int", "long", "float"}:
            values[name] = item.attrib.get("value", "")
    return values


def prefs_path(room_dir: Path, name: str) -> Path | None:
    path = Path(room_dir) / "app-data" / "shared_prefs" / name
    return path if path.is_file() else None


def clean_drafts_xml(source: bytes, key: str) -> tuple[bytes, int]:
    """Remove one draft entry from a session_editor_drafts.xml payload."""
    tree = ET.fromstring(source)
    removed = 0
    for element in list(tree):
        if element.attrib.get("name") == key:
            tree.remove(element)
            removed += 1
    return ET.tostring(tree, encoding="utf-8", xml_declaration=True), removed


def program_page_present(shot: UiShot) -> bool:
    return shot.present(PAGE_SELECTOR) and not shot.present(EDITOR_MARK)


def launch_program(s: QaSession) -> UiShot:
    """Cold-safe program route: hydrate Home when the process is gone, then deep link."""
    if not s.avd.pidof():
        s.launch_main()
        s.wait_home("editor-hydrate-home")
    s.avd.start_deeplink(fixtures.PROGRAM_URI)
    time.sleep(2.0)
    shot = s.capture_ui("after-program-deeplink")
    if shot.present("text:Más tarde"):
        s.tap("text:Más tarde", "dismiss-later", shot=shot)
        return s.wait_ui(lambda x: x.present(PAGE_SELECTOR), "after-later", timeout=30, what="program page after dismissing 'Más tarde'")
    if not shot.present(PAGE_SELECTOR):
        return s.wait_ui(lambda x: x.present(PAGE_SELECTOR), "program-ready", timeout=45, what="program page (Lunes)")
    return shot


def open_editor(s: QaSession, *, day_label: str = DAY_LABEL, max_scrolls: int = 10) -> UiShot:
    launch_program(s)
    s.tap(PAGE_SELECTOR, "select-monday")
    for attempt in range(max_scrolls + 1):
        shot = s.capture_ui(f"find-editor-{attempt:02d}")
        if shot.present(EDITOR_MARK):
            return shot
        if shot.present(f"text:{day_label}"):
            try:
                uia.resolve_scoped_clickable_target(shot.xml_text, EDITOR_BUTTON, day_label)
            except uia.TargetResolutionError as error:
                s._log("editor_target_unresolved", {"attempt": attempt, "reason": str(error)})
            else:
                s.tap(EDITOR_BUTTON, "open-editor", within_text=day_label, shot=shot, settle=1.5)
                return s.wait_ui(lambda x: x.present(EDITOR_MARK), "editor-ready", timeout=30, png=True, what="editor ('Ver semana')")
        if attempt < max_scrolls:
            coords = uia.screen_swipe_up(shot.xml_text)
            s.avd.swipe(*coords)
            s._log("swipe", {"label": "find-editor", "coords": coords})
            time.sleep(0.5)
    raise StepFailure(f"could not resolve {EDITOR_BUTTON} within {day_label!r} after {max_scrolls} swipes")


def session_from_db(db_path: Path, session_id: str = fixtures.MONDAY_SESSION_ID,
                    program_id: str = fixtures.PROGRAM_ID, week_id: str = DEFAULT_WEEK_ID) -> dict[str, Any]:
    import room_verify

    connection = room_verify.open_ro(Path(db_path))
    try:
        program = room_verify.program_json(connection, program_id)
        return dict(room_verify.require_session(room_verify.find_week(program, week_id), session_id))
    finally:
        connection.close()
