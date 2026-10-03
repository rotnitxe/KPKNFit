"""A command-level adb simulator + a small KPKN app model, for offline end-to-end runs of the drivers.

``FakeDevice`` is injected as the ``runner`` of ``avd.Avd`` so the REAL wrapper code (guard, quoting,
parsing, fresh dump, run-as helpers, tar extraction) executes against deterministic responses.
``FakeWorkoutApp`` renders UiAutomator hierarchies for the screens the strength / cancel / cardio /
media-import / CameraX / migration drivers touch and mutates a REAL SQLite database (the actual
synthetic fixtures), so Room inspection and the offline verifiers run for real too.

It is a test double, not an emulator: it proves the drivers' logic is internally consistent, not that
the product's UI looks like the simulated one.
"""

from __future__ import annotations

import hashlib
import io
import json
import re
import shlex
import sqlite3
import struct
import subprocess
import tarfile
import tempfile
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import fixtures
from qa_paths import SCHEMAS_DIR

PKG = "com.example.kpkn"
SQUAT = fixtures.SQUAT_EXERCISE_ID
SQUAT_LABEL = "Sentadilla Trasera con Barra Baja · Barra"
CARDIO_NAME = fixtures.CARDIO_EXERCISE_NAME
PICKER = "com.google.android.documentsui/.picker.PickActivity"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def png_bytes() -> bytes:
    from PIL import Image

    buffer = io.BytesIO()
    Image.new("RGB", (60, 120), (10, 20, 30)).save(buffer, "PNG")
    return buffer.getvalue()


def jpeg_bytes(seed: int) -> bytes:
    import random

    from PIL import Image

    rng = random.Random(seed)
    image = Image.new("RGB", (160, 120))
    image.putdata([(rng.randrange(256), rng.randrange(256), rng.randrange(256)) for _ in range(160 * 120)])
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=90)
    return buffer.getvalue()


def mp4_bytes(seconds_marker: int) -> bytes:
    """Real parseable MP4 (the synthetic fixture) + a trailing free box so the hash differs from every fixture."""
    data = fixtures.fixture_path("qa-video").read_bytes()
    payload = (b"camerax-sim-%d" % seconds_marker) * 8
    return data + struct.pack(">I4s", 8 + len(payload), b"free") + payload


class Node:
    def __init__(self, text="", desc="", cls="android.widget.TextView", clickable=False, enabled=True, key=None, size=(980, 100),
                 children=None):
        self.text, self.desc, self.cls, self.clickable, self.enabled = text, desc, cls, clickable, enabled
        self.key = key or text or desc
        self.size = size
        self.children: list[Node] = children or []
        self.bounds: tuple[int, int, int, int] | None = None


def place(nodes: list[Node], x: int, y: int, width: int) -> int:
    for node in nodes:
        if node.children:
            top = y
            inner = place(node.children, x + 10, y + 10, width - 20)
            node.bounds = (x, top, x + width, inner + 10)
            y = inner + 10 + 24
        else:
            node.bounds = (x, y, x + node.size[0], y + node.size[1])
            y += node.size[1] + 24
    return y


def flatten(nodes: list[Node]):
    for node in nodes:
        yield node
        yield from flatten(node.children)


def _emit(parent: ET.Element, node: Node, pkg: str) -> None:
    left, top, right, bottom = node.bounds
    element = ET.SubElement(parent, "node", {
        "class": "android.view.ViewGroup" if node.children else node.cls, "package": pkg, "text": node.text, "content-desc": node.desc,
        "clickable": str(node.clickable).lower(), "enabled": str(node.enabled).lower(), "focusable": str(node.clickable).lower(),
        "visible-to-user": "true", "bounds": f"[{left},{top}][{right},{bottom}]",
    })
    for child in node.children:
        _emit(element, child, pkg)


def to_xml(nodes: list[Node], pkg: str = PKG) -> str:
    root = ET.Element("hierarchy", rotation="0")
    frame = ET.SubElement(root, "node", {"class": "android.widget.FrameLayout", "package": pkg, "bounds": "[0,0][1080,2400]",
                                         "clickable": "false", "enabled": "true"})
    place(nodes, 50, 120, 980)
    for node in nodes:
        _emit(frame, node, pkg)
    return ET.tostring(root, encoding="unicode")


class FakeWorkoutApp:
    """Strength workout, cardio card, exit dialog + cancel, finish, media face, picker, album, Room migration."""

    def __init__(self, device: "FakeDevice") -> None:
        self.device = device
        self.screen = "home"
        self.typed = ""
        self.done = 0
        self.recording = False
        self.feedback_pending = False
        self.media: list[dict[str, Any]] = []
        self.nodes: list[Node] = []
        self.camera_ok = True
        self.migrations_run = 0
        # cardio
        self.cardio = False
        self.gps = "idle"            # idle | running | paused
        self.active_series = 0
        self.registered: set[int] = set()
        self.confirm = False
        # picker
        self.picker_path: list[str] = []
        # session editor
        self.later_pending = False
        self.later_seen = False
        self.page_selected = False
        self.desc = ""
        self.saved_desc = ""
        self.editing = False
        self.more_open = False
        self.pending_transfer: tuple[str, str] | None = None
        self.transfer_mode = "APPEND"
        self.transfer_choice: str | None = None

    # ---------------------------------------------------------------- database
    def _db(self) -> sqlite3.Connection:
        path = Path(tempfile.mkdtemp()) / "kpkn.db"
        path.write_bytes(self.device.fs_app["databases/kpkn.db"])
        self._tmp = path
        return sqlite3.connect(path)

    def _commit(self, connection: sqlite3.Connection) -> None:
        connection.commit()
        connection.close()
        self.device.fs_app["databases/kpkn.db"] = self._tmp.read_bytes()

    def _ongoing(self, connection: sqlite3.Connection) -> dict[str, Any] | None:
        row = connection.execute('SELECT data FROM ongoing_workout ORDER BY rowId LIMIT 1').fetchone()
        return json.loads(row[0]) if row else None

    def _save_ongoing(self, connection: sqlite3.Connection, ongoing: dict[str, Any]) -> None:
        connection.execute('UPDATE ongoing_workout SET data=?', (json.dumps(ongoing),))

    def _migrate(self, connection: sqlite3.Connection) -> None:
        """Room 27 -> 28 as the product describes it (association table, typed log normalization, identity hash)."""
        self.migrations_run += 1
        connection.execute('CREATE TABLE IF NOT EXISTS workout_media_session_associations('
                           'sessionKey TEXT NOT NULL PRIMARY KEY, workoutLogId TEXT NOT NULL)')
        per_key: dict[str, set[str]] = {}
        valid = {r[0] for r in connection.execute('SELECT id FROM workout_logs')}
        for key, log_id in connection.execute('SELECT sessionKey, workoutLogId FROM workout_media'):
            if key and log_id and log_id in valid:
                per_key.setdefault(key, set()).add(log_id)
        for key, logs in per_key.items():
            if len(logs) == 1:
                connection.execute('INSERT OR IGNORE INTO workout_media_session_associations VALUES (?,?)', (key, next(iter(logs))))
        for log_id, raw in connection.execute('SELECT id, data FROM workout_logs').fetchall():
            data = json.loads(raw)
            for exercise in data.get("completedExercises", []):
                exercise["canonicalExerciseId"] = exercise.get("exerciseDbId")
                for item in exercise.get("sets", []):
                    if isinstance(item.get("weight"), int):
                        item["weight"] = float(item["weight"])
            connection.execute('UPDATE workout_logs SET data=? WHERE id=?', (json.dumps(data), log_id))
        identity = json.loads(Path(SCHEMAS_DIR / "28.json").read_text(encoding="utf-8"))["database"]["identityHash"]
        connection.execute('UPDATE room_master_table SET identity_hash=?', (identity,))
        connection.execute("PRAGMA user_version=28")

    # ---------------------------------------------------------------- lifecycle
    def on_start(self, uri: str | None) -> None:
        connection = self._db()
        if connection.execute("PRAGMA user_version").fetchone()[0] == 27:
            self._migrate(connection)
        ongoing = self._ongoing(connection)
        completed = (ongoing or {}).get("completedSets") or {}
        self.cardio = bool(ongoing and (ongoing.get("activeStepKey") or "").endswith("_cardio"))
        self.done = len([k for k, v in completed.items() if k.startswith(SQUAT + "_") and "warmup" not in k and not v.get("isWarmup")])
        self.registered = {int(k.rsplit("_", 1)[1]) for k in completed if k.startswith(SQUAT + "_") and k.rsplit("_", 1)[1].isdigit()} \
            if self.cardio else set()
        self.active_series = int((ongoing or {}).get("activeSetIndex") or 0) if self.cardio else 0
        timer = (ongoing or {}).get("cardioTimerState") or {}
        self.gps = "paused" if timer.get("status") == "PAUSED" else "idle"
        self.media = [{"id": r[0], "kind": r[1]} for r in connection.execute(
            'SELECT id, kind FROM workout_media WHERE sessionKey = ?', (self._session_key(ongoing),))] if ongoing else []
        self._commit(connection)
        self.recording = False
        self.confirm = False
        self.typed = ""
        if uri and uri.startswith("kpkn://workout/") and ongoing:
            self.screen = "workout"
        elif uri and uri.startswith("kpkn://program/"):
            self.screen = "program"
            self.later_pending = not self.later_seen
            self.later_seen = True
            self.page_selected = False
        elif self.device.cold or self.screen == "":
            self.screen = "home"
        self.device.cold = False

    @staticmethod
    def _session_key(ongoing: dict[str, Any] | None) -> str | None:
        return f"{ongoing['programId']}::{ongoing['session']['id']}::{ongoing['startTime']}" if ongoing else None

    def on_stop(self) -> None:
        self.screen = "home"
        self.later_seen = False
        self.editing = False
        self.typed = ""
        self.confirm = False
        self.picker_path = []

    def top(self) -> str:
        return PICKER if self.screen == "picker" else f"{PKG}/.MainActivity"

    # ---------------------------------------------------------------- rendering
    def render(self) -> str:
        screen, nodes = self.screen, []
        if screen == "home":
            nodes = [Node(desc="Ring de Músculos. Toca para ver el detalle.", clickable=True, key="home-ring")]
        elif screen == "workout" and self.cardio:
            nodes = self._cardio_nodes()
        elif screen == "workout":
            badge = "✓" if self.done >= 3 else f"{self.done}/2"
            nodes = [Node(text=badge, key="badge"), Node(text="S1", clickable=True, key="S1"), Node(text="S2", clickable=True, key="S2"),
                     Node(text=SQUAT_LABEL, clickable=True, key="squat"),
                     Node(text=self.typed, cls="android.widget.EditText", clickable=True, key="weight", size=(900, 300)),
                     Node(text="6", cls="android.widget.EditText", clickable=True, key="reps", size=(140, 80)),
                     Node(desc="Registrar serie", clickable=True, key="register"),
                     Node(text="Ver ejercicio/Fotos", clickable=True, key="media")]
        elif screen == "rest":
            nodes = [Node(text="Saltar descanso", clickable=True, key="skip")]
            if self.feedback_pending:
                nodes.append(Node(text="Registrar feedback", clickable=True, key="feedback"))
        elif screen == "exit":
            nodes = [Node(text="¿Qué deseas hacer?"), Node(text="Continuar entrenando", clickable=True, key="continue"),
                     Node(text="Terminar hasta acá", clickable=True, key="finish"),
                     Node(text="Pausar y salir", clickable=True, key="pause"),
                     Node(text="Abandonar sin guardar", clickable=True, key="abandon")]
        elif screen == "finish":
            nodes = [Node(desc="Guardar y terminar entrenamiento", clickable=True, key="finish-confirm")]
        elif screen == "program":
            nodes = ([Node(text="Más tarde", clickable=True, key="later")] if self.later_pending else []) + [
                Node(text="Lunes", clickable=True, key="page-mon", size=(300, 100)), Node(text="Miércoles", clickable=True, key="page-wed", size=(300, 100)),
                Node(children=[Node(text="Día 1"), Node(desc="Edit", clickable=True, key="edit-1", size=(160, 80))]),
                Node(children=[Node(text="Día 2"), Node(desc="Edit", clickable=True, key="edit-2", size=(160, 80))])]
        elif screen == "editor":
            nodes = [Node(text="Ver semana", clickable=True, key="week"),
                     Node(text=self.desc or "Añadir descripción", clickable=True, key="desc"),
                     Node(text="Transferir", clickable=True, key="transfer") if self.more_open else Node(text="Más", clickable=True, key="more")]
        elif screen == "transfer":
            nodes = [Node(text="Transferir"), Node(text="Reemplazar", clickable=True, key="replace"),
                     Node(text="Martes · Libre", clickable=True, key="target-tue"),
                     Node(text="Miércoles · Día 2", clickable=True, key="target-wed"),
                     Node(text="Preparar transferencia", clickable=True, key="prepare")]
        elif screen == "exit_editor":
            nodes = [Node(text="Salir del editor"), Node(text="Guardar y salir", clickable=True, key="save-exit"),
                     Node(text="Cerrar sin guardar", clickable=True, key="discard")]
        elif screen == "picker":
            nodes = self._picker_nodes()
        elif screen in ("media", "album"):
            nodes = self._media_nodes(screen)
        self.nodes = nodes
        return to_xml(nodes, pkg="com.google.android.documentsui" if screen == "picker" else PKG)

    def _cardio_nodes(self) -> list[Node]:
        if self.confirm:
            return [Node(text=CARDIO_NAME), Node(text="Serie 1"), Node(text="Serie 2"), Node(text="Confirmar cardio"),
                    Node(text="Registrar", clickable=True, key="register-cardio"), Node(text="Cancelar", clickable=True, key="cancel-confirm")]
        chip = lambda i: f"Serie {i + 1} · Registrada" if i in self.registered else f"Serie {i + 1}"
        nodes = [Node(text=CARDIO_NAME), Node(text=chip(0), clickable=True, key="chip0"), Node(text=chip(1), clickable=True, key="chip1")]
        if len(self.registered) == 2:
            return nodes + [Node(text="Cardio registrado · 5 km"), Node(text="Actualizar cardio", clickable=True, key="update-cardio")]
        if self.gps == "idle":
            nodes += [Node(text="Listo · objetivo 30:00"), Node(text="Iniciar GPS", clickable=True, key="start-gps")]
        elif self.gps == "running":
            nodes += [Node(text="En curso · 00:10"), Node(text="Señal GPS activa · 0,02 km · 00:10"),
                      Node(text="Pausar GPS", clickable=True, key="pause-gps"), Node(text="Km"), Node(text="0.02 km")]
        else:
            nodes += [Node(text="Pausado · 00:12"), Node(text="GPS pausado"), Node(text="Reanudar GPS", clickable=True, key="resume-gps"),
                      Node(text="Km"), Node(text="0.02 km")]
        if self.gps != "idle":
            nodes.append(Node(text="Finalizar y registrar", clickable=True, key="finish-cardio"))
        return nodes

    def _picker_nodes(self) -> list[Node]:
        if not self.picker_path:
            return [Node(text="Pictures", clickable=True, key="dir:Pictures"), Node(text="Movies", clickable=True, key="dir:Movies")]
        if len(self.picker_path) == 1:
            top = self.picker_path[0]
            folders = sorted({p.split("/")[3] for p in self.device.fs_dev if p.startswith(f"/sdcard/{top}/")})
            return [Node(text=name, clickable=True, key=f"dir:{name}") for name in folders]
        top, folder = self.picker_path
        files = sorted(p for p in self.device.fs_dev if p.startswith(f"/sdcard/{top}/{folder}/"))
        return [Node(text=p.rsplit("/", 1)[1], clickable=True, key="file:" + p) for p in files]

    def _media_nodes(self, screen: str) -> list[Node]:
        nodes = [Node(text="Ver ejercicio/Fotos"), Node(text="Técnica KPKN"), Node(desc="Volver a la serie", clickable=True, key="back-set"),
                 Node(desc="Álbum", clickable=True, key="album-btn"), Node(desc=f"Foto de técnica de {fixtures.SQUAT_DISPLAY_NAME}")]
        if not self.camera_ok:
            nodes.append(Node(text="Activa la cámara para grabar desde esta tarjeta"))
        nodes += [Node(text="Foto", clickable=True, enabled=self.camera_ok, key="shutter", size=(300, 100)),
                  Node(text="Parar" if self.recording else "Vídeo", clickable=True, enabled=self.camera_ok, key="record", size=(300, 100)),
                  Node(text="Galería", clickable=True, key="gallery", size=(300, 100))]
        for item in self.media:
            nodes.append(Node(desc=fixtures.SQUAT_DISPLAY_NAME, clickable=True, key="thumb-" + item["id"], size=(168, 168)))
            if item["kind"] == "VIDEO":
                nodes.append(Node(desc="Vídeo", key="vmark-" + item["id"], size=(90, 90)))
        if screen == "album":
            return [Node(text="Álbum de esta sesión")] + [n for n in nodes if n.key.startswith(("thumb-", "vmark-"))]
        return nodes

    # ---------------------------------------------------------------- input
    def tap_xy(self, x: int, y: int) -> None:
        for node in flatten(self.nodes):
            left, top, right, bottom = node.bounds
            if node.clickable and node.enabled and left <= x < right and top <= y < bottom:
                self.on_tap(node.key)
                return

    def on_tap(self, key: str) -> None:
        if self.screen == "workout" and self.cardio:
            self._cardio_tap(key)
        elif self.screen == "workout":
            if key == "register":
                self._register_set()
            elif key == "media":
                self.screen = "media"
        elif self.screen == "rest":
            if key == "skip":
                self.screen = "workout"
                self.typed = ""
            elif key == "feedback":
                self.feedback_pending = False
        elif self.screen == "exit":
            if key == "abandon":
                self._abandon()
            elif key == "continue":
                self.screen = "workout"
            elif key == "finish":
                self.screen = "finish"
        elif self.screen == "finish" and key == "finish-confirm":
            self._finish()
        elif self.screen == "program":
            if key == "later":
                self.later_pending = False
            elif key == "page-mon":
                self.page_selected = True
            elif key == "edit-1" and not self.later_pending:
                self._open_editor()
        elif self.screen == "editor":
            if key == "desc":
                self.editing = True
            elif key == "more":
                self.more_open = True
            elif key == "transfer":
                self.screen, self.transfer_mode, self.transfer_choice = "transfer", "APPEND", None
        elif self.screen == "transfer":
            if key == "replace":
                self.transfer_mode = "REPLACE"
            elif key in ("target-tue", "target-wed"):
                self.transfer_choice = "TUE" if key == "target-tue" else "WED"
            elif key == "prepare" and self.transfer_choice:
                mode = "CREATE" if self.transfer_choice == "TUE" else self.transfer_mode
                self.pending_transfer = (mode, self.transfer_choice)
                self.screen = "editor"
        elif self.screen == "exit_editor":
            if key == "save-exit":
                self._save_editor()
            elif key == "discard":
                self._discard_editor()
        elif self.screen == "picker":
            self._picker_tap(key)
        elif self.screen in ("media", "album"):
            if key == "back-set":
                self.screen = "workout"
            elif key == "album-btn":
                self.screen = "album"
            elif key == "gallery":
                self.screen, self.picker_path = "picker", []
            elif key == "shutter":
                self._capture("PHOTO")
            elif key == "record":
                if self.recording:
                    self.recording = False
                    self._capture("VIDEO")
                else:
                    self.recording = True

    def on_key(self, code: str) -> None:
        if code == "KEYCODE_BACK":
            if self.screen == "workout":
                if self.confirm:
                    self.confirm = False
                else:
                    self.screen = "exit"
            elif self.screen == "exit":
                self.screen = "workout"
            elif self.screen in ("album", "picker"):
                self.screen = "media"
            elif self.screen == "editor":
                self.editing = False
                self.screen = "exit_editor" if (self.desc != self.saved_desc or self.pending_transfer) else "program"
            elif self.screen == "exit_editor":
                self.screen = "editor"
        elif code == "KEYCODE_HOME":
            self.device.foreground = False

    def on_text(self, value: str) -> None:
        if self.screen == "editor" and self.editing:
            self.desc = value.replace("%s", " ")
            self._write_draft()
            return
        self.typed = value.replace("%s", " ")

    # ---------------------------------------------------------------- effects: session editor
    PREFS = "shared_prefs/session_editor_drafts.xml"
    SETTINGS = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"
                "<map><boolean name=\"auto_save_enabled\" value=\"true\" /></map>")

    def _program(self, connection: sqlite3.Connection):
        row = connection.execute('SELECT id, data FROM programs WHERE id=?', (fixtures.PROGRAM_ID,)).fetchone()
        return json.loads(row[1])

    def _find_session(self, program: dict[str, Any]) -> dict[str, Any]:
        found: list[dict[str, Any]] = []

        def visit(value: Any) -> None:
            if isinstance(value, dict):
                if value.get("id") == fixtures.WEEK_ID and isinstance(value.get("sessions"), list):
                    found.extend(x for x in value["sessions"] if x.get("id") == fixtures.MONDAY_SESSION_ID)
                for child in value.values():
                    visit(child)
            elif isinstance(value, list):
                for child in value:
                    visit(child)

        visit(program)
        return found[0]

    def _draft_key(self) -> str:
        return f"program={fixtures.PROGRAM_ID}|week={fixtures.WEEK_ID}|macro=0|meso=0|editor={fixtures.MONDAY_SESSION_ID}"

    def _draft_tree(self) -> ET.Element:
        raw = self.device.fs_app.get(self.PREFS)
        return ET.fromstring(raw) if raw else ET.Element("map")

    def _draft_entry(self, tree: ET.Element):
        return next((e for e in tree if e.attrib.get("name") == self._draft_key()), None)

    def _open_editor(self) -> None:
        connection = self._db()
        session = self._find_session(self._program(connection))
        connection.close()
        self.saved_desc = session.get("description") or ""
        draft = self._draft_entry(self._draft_tree())
        self.desc = (json.loads(draft.text)["session"].get("description") or "") if draft is not None else self.saved_desc
        self.device.fs_app.setdefault("shared_prefs/session_editor_preferences.xml", self.SETTINGS.encode("utf-8"))
        self.screen, self.editing = "editor", False

    def _write_draft(self) -> None:
        tree = self._draft_tree()
        entry = self._draft_entry(tree)
        if self.desc == self.saved_desc:
            if entry is not None:
                tree.remove(entry)
        else:
            connection = self._db()
            session = self._find_session(self._program(connection))
            connection.close()
            session["description"] = self.desc
            payload = {"programId": fixtures.PROGRAM_ID, "sessionId": fixtures.MONDAY_SESSION_ID, "weekId": fixtures.WEEK_ID,
                       "macroIndex": 0, "mesoIndex": 0, "dayOfWeek": 1, "session": session, "savedAtMs": self.device.clock}
            if entry is None:
                entry = ET.SubElement(tree, "string", name=self._draft_key())
            entry.text = json.dumps(payload)
        self.device.fs_app[self.PREFS] = ET.tostring(tree, encoding="utf-8", xml_declaration=True)

    def _apply_transfer(self, program: dict[str, Any]) -> None:
        """APPEND / REPLACE onto Wednesday or CREATE on Tuesday, plus the destination SESSION override."""
        import copy

        import room_verify as rv

        mode, _choice = self.pending_transfer
        baseline = copy.deepcopy(program)
        week = rv.find_week(program, fixtures.WEEK_ID)
        source = rv.require_session(week, fixtures.MONDAY_SESSION_ID)

        def copies():
            result = copy.deepcopy(rv.all_exercises(source))
            for index, exercise in enumerate(result):
                exercise["id"] = f"re_sim_{mode.lower()}_{index}_{uuid.uuid4().hex[:8]}"
            return result

        def place(session: dict[str, Any], exercises: list[dict[str, Any]], replace: bool) -> None:
            parts = session.get("parts") or []
            if replace:
                for part in parts:
                    part["exercises"] = []
                session["exercises"] = [] if not parts else session.get("exercises", [])
            if parts:
                parts[-1].setdefault("exercises", []).extend(exercises)
            else:
                session.setdefault("exercises", []).extend(exercises)

        if mode == "CREATE":
            target = {"id": f"rs_sim_{uuid.uuid4().hex[:16]}", "dayOfWeek": 2, "name": "Día nuevo", "description": "",
                      "exercises": [], "parts": []}
            week["sessions"].append(target)
            place(target, copies(), replace=False)
            target_before = None
        else:
            target = rv.require_session(week, fixtures.WEDNESDAY_SESSION_ID)
            target_before = copy.deepcopy(target)
            place(target, copies(), replace=(mode == "REPLACE"))
        baseline_week = rv.find_week(baseline, fixtures.WEEK_ID)
        occurrence = rv.expected_week_occurrence(baseline, fixtures.MONDAY_SESSION_ID, target["id"], fixtures.WEEK_ID, baseline_week)
        recipe = rv.expected_target_recipe_id(baseline, target_before, target["id"], fixtures.WEEK_ID, target["dayOfWeek"])
        overrides = program.setdefault("manualSessionOverrides", [])
        overrides[:] = [o for o in overrides if not (o.get("sessionId") == target["id"] and o.get("weekId") == fixtures.WEEK_ID
                                                     and o.get("scope") == "SESSION")]
        overrides.append({"scope": "SESSION", "sessionId": target["id"], "weekId": fixtures.WEEK_ID, "weekOccurrence": occurrence,
                          "recipeDayId": recipe, "reason": "TRANSFER", "createdAtMs": self.device.clock})

    def _save_editor(self) -> None:
        connection = self._db()
        program = self._program(connection)
        self._find_session(program)["description"] = self.desc
        if self.pending_transfer:
            self._apply_transfer(program)
            self.pending_transfer = None
        connection.execute('UPDATE programs SET data=? WHERE id=?', (json.dumps(program), fixtures.PROGRAM_ID))
        self._commit(connection)
        self.saved_desc = self.desc
        self._write_draft()  # equal to Room now: the draft entry disappears
        self.screen, self.editing = "program", False

    def _discard_editor(self) -> None:
        self.desc = self.saved_desc
        self._write_draft()
        self.screen, self.editing = "program", False

    # ---------------------------------------------------------------- effects: strength
    def _register_set(self) -> None:
        if not self.typed:
            return
        connection = self._db()
        ongoing = self._ongoing(connection)
        ongoing.setdefault("completedSets", {})[f"{SQUAT}_{self.done}"] = {
            "id": str(uuid.uuid4()), "weight": float(self.typed), "reps": 6, "isWarmup": False}
        self._save_ongoing(connection, ongoing)
        self._commit(connection)
        self.done += 1
        self.screen = "rest"
        self.feedback_pending = self.done >= 2

    def _abandon(self) -> None:
        connection = self._db()
        connection.execute('DELETE FROM ongoing_workout')
        self._commit(connection)
        for name in [n for n in self.device.fs_app if n.startswith("files/cardio-gps/")]:
            self.device.fs_app.pop(name)
        self.gps = "idle"  # discard stops the GPS runtime
        self.screen = "home"

    def _finish(self) -> None:
        connection = self._db()
        ongoing = self._ongoing(connection)
        key = self._session_key(ongoing)
        log_id = str(uuid.uuid4())
        sets = [{"id": v["id"], "weight": v["weight"], "reps": v["reps"], "isWarmup": False}
                for k, v in sorted((ongoing.get("completedSets") or {}).items()) if k.startswith(SQUAT + "_")]
        data = {"id": log_id, "programId": ongoing["programId"], "sessionId": ongoing["session"]["id"], "sessionName": "Día 1",
                "date": "2026-10-01T00:00:00Z", "durationMinutes": 5,
                "completedExercises": [{"exerciseId": SQUAT, "exerciseName": fixtures.SQUAT_DISPLAY_NAME, "sets": sets}]}
        connection.execute('INSERT INTO workout_logs(id, programId, sessionId, date, data) VALUES (?,?,?,?,?)',
                           (log_id, ongoing["programId"], ongoing["session"]["id"], "2026-10-01T00:00:00Z", json.dumps(data)))
        connection.execute('UPDATE workout_media SET workoutLogId=? WHERE sessionKey=?', (log_id, key))
        connection.execute('INSERT OR REPLACE INTO workout_media_session_associations VALUES (?,?)', (key, log_id))
        connection.execute('DELETE FROM ongoing_workout')
        self._commit(connection)
        self.screen = "home"

    # ---------------------------------------------------------------- effects: cardio
    def _gps_file(self, ongoing: dict[str, Any], series: int) -> str:
        key = f"{self._session_key(ongoing)}::{SQUAT}::qa-cardio-series-{series}"
        return f"files/cardio-gps/{sha(key.encode())}.json", key

    def _cardio_tap(self, key: str) -> None:
        connection = self._db()
        ongoing = self._ongoing(connection)
        series_id = f"qa-cardio-series-{self.active_series}"
        if key == "start-gps":
            self.gps = "running"
            path, session_key = self._gps_file(ongoing, self.active_series)
            self.device.fs_app[path] = json.dumps({"sessionKey": session_key, "points": 3}).encode()
            ongoing["cardioTimerState"] = {"status": "RUNNING", "setId": series_id}
        elif key == "pause-gps" and self.gps == "running":
            self.gps = "paused"
            ongoing["cardioTimerState"] = {"status": "PAUSED", "setId": series_id}
        elif key == "resume-gps" and self.gps == "paused":
            self.gps = "running"
            ongoing["cardioTimerState"] = {"status": "RUNNING", "setId": series_id}
        elif key == "finish-cardio":
            self.confirm = True
        elif key == "cancel-confirm":
            self.confirm = False
        elif key == "register-cardio":
            ongoing.setdefault("completedSets", {})[f"{SQUAT}_{self.active_series}"] = {"id": str(uuid.uuid4()), "isWarmup": False}
            self.registered.add(self.active_series)
            self.confirm = False
            self.gps = "idle"
            ongoing["cardioTimerState"] = None
            if self.active_series == 0:
                self.active_series = 1
            ongoing["activeSetIndex"] = self.active_series
        # chip taps never change the active series while one is in progress (S1 retained)
        self._save_ongoing(connection, ongoing)
        self._commit(connection)

    # ---------------------------------------------------------------- effects: picker + media
    def _picker_tap(self, key: str) -> None:
        if key.startswith("dir:"):
            self.picker_path.append(key[4:])
        elif key.startswith("file:"):
            path = key[5:]
            self._ingest(path.rsplit("/", 1)[1], self.device.fs_dev[path])
            self.screen = "media"
            self.picker_path = []

    def _ingest(self, name: str, payload: bytes) -> None:
        extension = name.rsplit(".", 1)[1].lower()
        kind = "VIDEO" if extension in ("mp4", "webm") else "PHOTO"
        self._insert_media(kind, extension, payload, imported=True)

    def _capture(self, kind: str) -> None:
        if not self.camera_ok:
            return
        extension, payload = ("jpg", jpeg_bytes(len(self.media) + 1)) if kind == "PHOTO" else ("mp4", mp4_bytes(len(self.media) + 1))
        self._insert_media(kind, extension, payload, imported=False)

    def _insert_media(self, kind: str, extension: str, payload: bytes, *, imported: bool) -> None:
        connection = self._db()
        ongoing = self._ongoing(connection)
        media_id = str(uuid.uuid4())
        relative = f"files/workout_media/2026-10/{media_id}.{extension}"
        self.device.fs_app[relative] = payload
        thumb = None
        if kind == "VIDEO":
            thumb = f"/data/user/0/{PKG}/files/workout_media/thumbs/{media_id}.jpg"
            self.device.fs_app[f"files/workout_media/thumbs/{media_id}.jpg"] = jpeg_bytes(99)
        columns = [r[1] for r in connection.execute('PRAGMA table_info("workout_media")')]
        values = {c: None for c in columns}
        values.update(id=media_id, kind=kind, filePath=f"/data/user/0/{PKG}/{relative}", thumbPath=thumb,
                      createdAtMs=self.device.clock, sessionKey=self._session_key(ongoing), programId=ongoing["programId"],
                      sessionId=ongoing["session"]["id"], sessionName="Día 1", exerciseId=SQUAT, canonicalExerciseId=SQUAT,
                      exerciseName=fixtures.SQUAT_DISPLAY_NAME, setIndex=self.done, isPr=0)
        connection.execute(f'INSERT INTO workout_media ({",".join(chr(34) + c + chr(34) for c in columns)}) VALUES ({",".join("?" for _ in columns)})',
                           [values[c] for c in columns])
        self._commit(connection)
        self.media.append({"id": media_id, "kind": kind})


class FakeDevice:
    def __init__(self, *, apk_sha: str, serial: str = "emulator-5580", avd_name: str = "KPKNFitSessionAudit20260929",
                 version_code: int = 34) -> None:
        self.serial, self.avd_name, self.apk_sha, self.version_code = serial, avd_name, apk_sha, version_code
        self.fs_app: dict[str, bytes] = {}
        self.fs_dev: dict[str, bytes] = {}
        self.perms: set[str] = set()
        self.installed = True
        self.running = False
        self.cold = True
        self.foreground = True
        self.clock = 1_790_800_000_000
        self.app = FakeWorkoutApp(self)
        self.log: list[str] = []
        self.xml_at: dict[str, bytes] = {}
        self.installs: list[dict[str, Any]] = []
        self.settings: dict[str, str] = {}
        self.test_installed = True
        self.test_apk_sha = "7" * 64
        self.instrument_codes: dict[tuple[str, str], int] = {}
        self.instrument_omit: set[tuple[str, str]] = set()
        self.hang_instrument = False

    # --------------------------------------------------------------- adb entry
    def __call__(self, argv, *, timeout, input_bytes=None):
        self.log.append(" ".join(map(str, argv[1:])))
        try:
            out, err, code = self._dispatch(list(map(str, argv)))
        except subprocess.TimeoutExpired:
            raise
        except Exception as error:  # surface simulator bugs loudly
            return subprocess.CompletedProcess(argv, 99, b"", f"FAKE_DEVICE_ERROR {type(error).__name__}: {error}".encode())
        if isinstance(out, str):
            out = out.encode("utf-8")
        return subprocess.CompletedProcess(argv, code, out, err.encode() if isinstance(err, str) else err)

    def _dispatch(self, argv):
        if argv[1:3] == ["devices", "-l"]:
            return f"List of devices attached\nemulator-5554\tdevice\n{self.serial}\tdevice product:sdk\n", "", 0
        assert argv[1:3] == ["-s", self.serial], argv
        rest = argv[3:]
        head = rest[0]
        if head == "emu":
            if rest[1:3] == ["avd", "name"]:
                return f"{self.avd_name}\r\nOK\r\n", "", 0
            return "OK\r\n", "", 0
        if head == "push":
            self.fs_dev[rest[2]] = Path(rest[1]).read_bytes()
            return "1 file pushed", "", 0
        if head == "install":
            import apk_info

            path = Path(rest[-1])
            data = path.read_bytes()
            manifest = apk_info.read_manifest(path)
            if str(manifest.get("package", "")).endswith(".test"):
                self.test_apk_sha, self.test_installed = sha(data), True
                self.installs.append({"flags": rest[1:-1], "sha": self.test_apk_sha, "path": str(path), "test": True})
                return "Performing Streamed Install\nSuccess", "", 0
            self.apk_sha = sha(data)
            self.version_code = int(manifest.get("versionCode") or self.version_code)
            self.installed = True
            self.running = False
            self.installs.append({"flags": rest[1:-1], "sha": self.apk_sha, "path": str(path)})
            return "Performing Streamed Install\nSuccess", "", 0
        if head == "logcat":
            return ("" if "-c" in rest else "01-01 00:00:00.000 I/CameraService(1): connect com.example.kpkn\n"), "", 0
        if head == "exec-out":
            return self._exec_out(" ".join(rest[1:]))
        if head == "shell":
            return self._shell(shlex.split(" ".join(rest[1:])))
        raise AssertionError(f"unhandled adb command {rest}")

    # --------------------------------------------------------------- shell
    def _shell(self, t: list[str]):
        app = self.app
        if t[:1] == ["getprop"]:
            return {"ro.kernel.qemu": "1", "sys.boot_completed": "1", "ro.build.version.sdk": "36", "ro.build.version.release": "16",
                    "ro.product.cpu.abi": "x86_64", "ro.product.model": "sdk_gphone64_x86_64", "ro.build.fingerprint": "fake/fp",
                    "ro.boot.qemu.avd_name": self.avd_name}.get(t[1], "") + "\n", "", 0
        if t[:1] == ["wm"]:
            return "Physical size: 1344x2992\n", "", 0
        if t[:2] == ["pm", "path"]:
            if t[2] == PKG + ".test":
                return (f"package:/data/app/~~abc/{t[2]}-1/base.apk\n" if self.test_installed else ""), "", 0
            return (f"package:/data/app/~~abc/{t[2]}-1/base.apk\n" if self.installed and t[2] == PKG else ""), "", 0
        if t[:3] == ["pm", "list", "instrumentation"]:
            return f"instrumentation:{PKG}.test/androidx.test.runner.AndroidJUnitRunner (target={PKG})\n", "", 0
        if t[:3] == ["pm", "list", "features"]:
            return "feature:android.hardware.camera\nfeature:android.hardware.camera.any\n", "", 0
        if t[:2] == ["pm", "clear"]:
            self.fs_app.clear()
            self.perms.clear()
            self.running = False
            app.on_stop()
            app.camera_ok = False
            self.cold = True
            return "Success\n", "", 0
        if t[:2] in (["pm", "grant"], ["pm", "revoke"]):
            (self.perms.add if t[1] == "grant" else self.perms.discard)(t[3])
            app.camera_ok = "android.permission.CAMERA" in self.perms
            return "", "", 0
        if t[:2] == ["dumpsys", "package"]:
            lines = [f"    versionCode={self.version_code} minSdk=24 targetSdk=35", "    versionName=KPKN Beta 15",
                     "    flags=[ DEBUGGABLE HAS_CODE ]"]
            lines += [f"      {p}: granted=true" for p in sorted(self.perms)]
            lines += ["      android.permission.CAMERA: granted=false"] if "android.permission.CAMERA" not in self.perms else []
            return "\n".join(lines) + "\n", "", 0
        if t[:3] == ["dumpsys", "activity", "activities"]:
            top = app.top() if self.running and self.foreground else "com.google.android.apps.nexuslauncher/.NexusLauncherActivity"
            return f"  topResumedActivity=ActivityRecord{{1a2b u0 {top} t7}}\n", "", 0
        if t[:3] == ["dumpsys", "activity", "services"]:
            running = app.cardio and app.gps == "running"
            return ("  * ServiceRecord{1 u0 com.example.kpkn/.services.cardio.CardioGpsForegroundService}\n    app=ProcessRecord{2 4242:com.example.kpkn/u0a1}\n"
                    if running else "ACTIVITY MANAGER SERVICES (dumpsys activity services)\n  (nothing)\n"), "", 0
        if t[:2] == ["dumpsys", "input_method"]:
            return "  mInputShown=false\n  mIsInputViewShown=false\n", "", 0
        if t[:2] == ["dumpsys", "media.camera"]:
            return ("Number of camera devices: 2\n" + (f"10-01 20:00:00 : CONNECT device 0 client for package {PKG} (PID 4242) for Camera API 2\n"
                                                         if app.screen == "media" else "")), "", 0
        if t[:1] == ["sha256sum"]:
            path = t[1]
            if path.endswith("/base.apk"):
                return f"{self.test_apk_sha if '.test-' in path else self.apk_sha}  {path}\n", "", 0
            return (f"{sha(self.fs_dev[path])}  {path}\n", "", 0) if path in self.fs_dev else ("", f"sha256sum: {path}: No such file", 1)
        if t[:1] == ["pidof"]:
            return ("4242\n", "", 0) if self.running else ("", "", 1)
        if t[:2] == ["am", "force-stop"]:
            if t[2] == PKG:
                if app.cardio and app.gps == "running":
                    app.gps = "paused"  # a killed GPS session comes back paused (not exercised by the drivers)
                self.running = False
                app.on_stop()
            return "", "", 0
        if t[:2] == ["am", "start"]:
            uri = t[t.index("-d") + 1] if "-d" in t else None
            self.running, self.foreground = True, True
            app.on_start(uri)
            return "Starting: Intent\nStatus: ok\nActivity: com.example.kpkn/.MainActivity\n", "", 0
        if t[:1] == ["settings"]:
            if t[1] == "get":
                return self.settings.get(t[3], "null") + "\n", "", 0
            self.settings[t[3]] = t[4]
            return "", "", 0
        if t[:2] == ["am", "broadcast"] or t[:1] == ["cmd"]:
            return "", "", 0
        if t[:2] == ["am", "instrument"]:
            return self._instrument(t)
        if t[:2] == ["input", "tap"]:
            app.tap_xy(int(t[2]), int(t[3]))
            return "", "", 0
        if t[:2] == ["input", "keyevent"]:
            app.on_key(t[2])
            return "", "", 0
        if t[:2] == ["input", "text"]:
            app.on_text(t[2])
            return "", "", 0
        if t[:2] in (["input", "keycombination"], ["input", "swipe"]):
            return "", "", 0
        if t[:1] == ["date"]:
            self.clock += 400
            return f"{self.clock}\n", "", 0
        if t[:1] == ["mkdir"]:
            return "", "", 0
        if t[:2] == ["rm", "-f"]:
            for path in t[2:]:
                self.fs_dev.pop(path, None)
                self.xml_at.pop(path, None)
            return "", "", 0
        if t[:2] == ["uiautomator", "dump"]:
            self.xml_at[t[2]] = app.render().encode("utf-8")
            return f"UI hierarchy dumped to: {t[2]}\n", "", 0
        if t[:1] == ["run-as"]:
            return self._run_as(t[2:])
        raise AssertionError(f"unhandled shell command {t}")

    _INDEX = None

    def _instrument(self, t: list[str]):
        import kotlin_tests
        from qa_paths import ANDROID_TEST_SRC

        if self.hang_instrument:
            raise subprocess.TimeoutExpired(t, 1)
        if FakeDevice._INDEX is None:
            FakeDevice._INDEX = kotlin_tests.index_test_sources(ANDROID_TEST_SRC)
        cases = []
        for selection in t[t.index("class") + 1].split(","):
            cls, _, method = selection.partition("#")
            cases += [(cls, m) for m in ([method] if method else FakeDevice._INDEX[cls][0]["methods"])]
        lines = [f"INSTRUMENTATION_STATUS: numtests={len(cases)}"]
        passed = failed = 0
        for cls, method in cases:
            if (cls, method) in self.instrument_omit:
                continue
            code = self.instrument_codes.get((cls, method), 0)
            for status in (1, code):
                lines += [f"INSTRUMENTATION_STATUS: class={cls}", f"INSTRUMENTATION_STATUS: test={method}"]
                if status not in (1, 0):
                    lines += ["INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: simulated", "\tat sim.Test.run(Test.kt:1)"]
                lines.append(f"INSTRUMENTATION_STATUS_CODE: {status}")
            passed, failed = (passed + 1, failed) if code == 0 else (passed, failed + 1)
        lines += ["INSTRUMENTATION_RESULT: stream=", "", "Time: 1.5", ""]
        lines += [f"OK ({passed} tests)" if not failed else f"FAILURES!!!\nTests run: {passed + failed},  Failures: {failed}", "",
                  f"INSTRUMENTATION_CODE: {-1 if not failed else 0}"]
        return "\r\n".join(lines) + "\r\n", "", 0

    def _run_as(self, t: list[str]):
        if t[:1] == ["mkdir"]:
            return "", "", 0
        if t[:1] == ["cp"]:
            self.fs_app[t[2]] = self.fs_dev[t[1]]
            return "", "", 0
        if t[:1] == ["sha256sum"]:
            return (f"{sha(self.fs_app[t[1]])}  {t[1]}\n", "", 0) if t[1] in self.fs_app else ("", "No such file", 1)
        if t[:2] == ["sh", "-c"]:
            script = t[2]
            match = re.match(r"if \[ (-[efd]) (\S+) \]; then printf PRESENT; else printf ABSENT; fi", script)
            if match:
                flag, path = match.groups()
                present = any(k == path or k.startswith(path + "/") for k in self.fs_app) if flag == "-d" else path in self.fs_app
                return ("PRESENT" if present else "ABSENT"), "", 0
            match = re.match(r"find (\S+) -type f -exec stat", script)
            if match:
                prefix = match.group(1)
                return "".join(f"{k}|{len(v)}|1790800000\n" for k, v in sorted(self.fs_app.items()) if k.startswith(prefix + "/")), "", 0
        raise AssertionError(f"unhandled run-as {t}")

    def _exec_out(self, command: str):
        if command.startswith("cat "):
            path = command[4:]
            return self.xml_at.get(path) or self.fs_dev.get(path, b""), "", 0
        if command.startswith("screencap"):
            return png_bytes(), "", 0
        if command.startswith(f"run-as {PKG} cat "):
            path = command.split(" cat ", 1)[1]
            return (self.fs_app[path], "", 0) if path in self.fs_app else (b"", "No such file", 1)
        if command.startswith(f"run-as {PKG} tar cf - "):
            roots = command.split("tar cf - ", 1)[1].split()
            buffer = io.BytesIO()
            with tarfile.open(fileobj=buffer, mode="w") as archive:
                for name, data in sorted(self.fs_app.items()):
                    if name.split("/")[0] in roots:
                        info = tarfile.TarInfo(name)
                        info.size = len(data)
                        archive.addfile(info, io.BytesIO(data))
            return buffer.getvalue(), "", 0
        raise AssertionError(f"unhandled exec-out {command}")
