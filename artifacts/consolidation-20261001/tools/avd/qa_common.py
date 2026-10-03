"""Shared machinery for the functional UI drivers.

``QaSession`` bundles the allowlisted device, the evidence directory and the
PASS/FAIL/NOT_RUN recorder, and offers the few primitives every driver needs:
fresh-XML capture, single resolved taps, bounded waits, process lifecycle,
fixture seeding and Room capture.

Design rules inherited from the OLDROOT drivers
-----------------------------------------------
* every tap target is resolved from a *fresh* UiAutomator dump taken right before
  the tap; there are no cached coordinates and no blind retries of a tap;
* an ambiguous / missing selector is a hard stop with the visible nodes saved;
* the app is force-stopped before every Room capture (db + WAL consistency);
* the fixture, the preferences and the installed APK are hashed into the evidence.
"""

from __future__ import annotations

import argparse
import json
import platform
import re
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

import apk_info
import fixtures
import room_inspect
import uia
from avd import Avd, OwnedDeviceError
from evidence import (
    EXIT_MISSING_INPUT, MissingInput, Recorder, RunAborted, StepFailure, sha256_file, tools_fingerprint, utc_now, write_json,
    new_run_dir,
)
from qa_paths import (
    APP_DIR, APP_SRC, DEFAULT_SERIAL, FLAVORS, MAIN_ACTIVITY, PACKAGE, app_apk,
)

HOME_SELECTOR = "desc:Ring de Músculos. Toca para ver el detalle."
STARTUP_GRANTS = (
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
)
WORKOUT_SURFACE_LABELS = ("Registrar serie", "Ver ejercicio/Fotos", "Iniciar GPS", "Pausar GPS", "Reanudar GPS")
PREFLIGHT_STEPS = ("preflight_host_inputs", "preflight_device_allowlist", "preflight_installed_apk_matches_build")


def slug(text: str, limit: int = 48) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", text).strip("_")[:limit] or "x"


@dataclass
class UiShot:
    label: str
    name: str
    xml_text: str
    xml_path: Path
    top_resumed: str | None
    png_path: Path | None = None
    meta: dict[str, Any] = field(default_factory=dict)

    @property
    def tokens(self) -> list[str]:
        return uia.ui_tokens(self.xml_text)

    def has(self, label: str) -> bool:
        return uia.has_exact_label(self.xml_text, label)

    def present(self, selector: str) -> bool:
        return uia.selector_present(self.xml_text, selector)

    def summary(self, limit: int = 40) -> list[dict[str, str]]:
        return uia.visible_summary(self.xml_text, limit)


@dataclass
class RoomShot:
    directory: Path
    manifest: dict[str, Any]
    summary: dict[str, Any]

    @property
    def db_path(self) -> Path:
        return room_inspect.db_path_of(self.directory)


def add_common_args(parser: argparse.ArgumentParser) -> argparse.ArgumentParser:
    parser.add_argument("--flavor", choices=FLAVORS, required=True, help="base or health (selects the integrated APK)")
    parser.add_argument("--serial", default=DEFAULT_SERIAL, help="allowlisted emulator serial")
    parser.add_argument("--apk", type=Path, help="override the expected installed app APK (default: integrated build output)")
    parser.add_argument("--label", help="short label appended to the evidence directory")
    parser.add_argument("--home-timeout", type=float, default=180.0, help="seconds to wait for Home after a launch")
    parser.add_argument("--allow-stale", action="store_true", help="accept an APK older than the integrated sources (recorded)")
    parser.add_argument("--png-all", action="store_true", help="screenshot every UI capture (default: key checkpoints only)")
    return parser


class QaSession:
    def __init__(self, driver: str, args: argparse.Namespace, *, extra: dict[str, Any] | None = None,
                 avd: Avd | None = None) -> None:
        self.driver = driver
        self.args = args
        self.flavor: str = args.flavor
        self.avd = avd or Avd(args.serial)
        self.out = new_run_dir(driver, self.flavor, getattr(args, "label", None))
        self.ui_dir = self.out / "ui"
        self.room_dir = self.out / "room"
        self.ui_dir.mkdir()
        self.room_dir.mkdir()
        self.rec = Recorder(driver, self.flavor, self.out, extra={"serial": args.serial, **(extra or {})})
        self.seq = 0
        self.room_seq = 0
        self.taps: list[dict[str, Any]] = []
        self.png_all = bool(getattr(args, "png_all", False))
        self.home_timeout = float(getattr(args, "home_timeout", 180.0))
        self.allow_stale = bool(getattr(args, "allow_stale", False))
        self.apk_path = Path(args.apk) if getattr(args, "apk", None) else app_apk(self.flavor)
        self.device_used = False
        if getattr(args, "validate_only", False):
            self.avd.offline = True  # host-only validation must not even attempt adb

    # ------------------------------------------------------------------ evidence
    def _log(self, kind: str, record: dict[str, Any]) -> None:
        with (self.out / "events.jsonl").open("a", encoding="utf-8", newline="\n") as stream:
            stream.write(json.dumps({"kind": kind, "atUtc": utc_now(), **record}, ensure_ascii=False, default=str) + "\n")

    # ------------------------------------------------------------------ preflight
    def host_preflight(self, *, fixtures_needed: tuple[str, ...] = (), prefs: bool = True) -> dict[str, Any]:
        """Host-only checks; raises MissingInput before any device state changes."""
        info: dict[str, Any] = {"python": platform.python_version(), "tools": tools_fingerprint(), "fixtures": {}}
        if not self.apk_path.is_file():
            raise MissingInput(f"app APK not found: {self.apk_path}", "wait for / run the build, or pass --apk")
        apk = apk_info.inspect_apk(self.apk_path)
        manifest = apk["manifest"]
        if manifest["package"] != PACKAGE or not manifest["debuggable"]:
            raise MissingInput(f"{self.apk_path.name}: package={manifest['package']!r} debuggable={manifest['debuggable']}")
        meta = apk.get("outputMetadata") or {}
        if meta.get("variantName") and meta["variantName"] != f"{self.flavor}Debug":
            raise MissingInput(f"APK variant {meta['variantName']!r} does not match --flavor {self.flavor}")
        apk["staleness"] = apk_info.staleness(self.apk_path, [APP_SRC / "main", APP_DIR / "build.gradle.kts"])
        if apk["staleness"]["apkOlderThanSources"] and not self.allow_stale:
            raise MissingInput(
                f"{self.apk_path.name} is older than {apk['staleness']['newestSourceFile']}",
                "rebuild the integrated tree or pass --allow-stale",
            )
        info["apk"] = apk
        for name in fixtures_needed:
            record = fixtures.verify_fixture(name)
            if fixtures.FIXTURES[name]["kind"] == "room-db":
                record["identity"] = fixtures.read_db_identity(Path(record["path"]))
            info["fixtures"][name] = record
        if prefs:
            info["auditSharedPrefs"] = fixtures.verify_prefs()
        info["sourceRegistry"] = fixtures.source_registry()
        self.rec.inputs.update(info)
        return info

    def device_preflight(self) -> dict[str, Any]:
        self.device_used = True
        described = self.avd.describe()
        self.rec.inputs["device"] = described
        self.avd.logcat_clear()
        return described

    def check_installed_apk(self) -> dict[str, Any]:
        installed = self.avd.installed_apk(PACKAGE)
        expected = self.rec.inputs.get("apk", {}).get("sha256") or sha256_file(self.apk_path)
        facts = apk_info.parse_dumpsys_package(self.avd.dumpsys_package(PACKAGE))
        record = {"installed": installed, "expectedSha256": expected, "matched": installed["sha256"] == expected,
                  "device": facts, "apkPath": str(self.apk_path)}
        self.rec.inputs["installedApk"] = record
        if not record["matched"]:
            raise MissingInput(
                "installed app APK differs from the integrated build output "
                f"({installed['sha256'][:12]} != {expected[:12]})",
                f"run: python -X utf8 install_apks.py --flavor {self.flavor}",
            )
        return record

    def standard_preflight(self, fixtures_needed: tuple[str, ...] = (), *, prefs: bool = True) -> None:
        """The three common steps (names in PREFLIGHT_STEPS); every driver declares them first."""
        with self.rec.step("preflight_host_inputs") as step:
            info = self.host_preflight(fixtures_needed=fixtures_needed, prefs=prefs)
            step.detail(f"APK {info['apk']['sha256'][:12]} v{info['apk']['manifest']['versionCode']}; "
                        f"fixtures={list(info['fixtures'])}")
            step.evidence(apkSha256=info["apk"]["sha256"], staleness=info["apk"]["staleness"])
        with self.rec.step("preflight_device_allowlist") as step:
            device = self.device_preflight()
            step.detail(f"{device['serial']} {device['avdName']} API {device['sdk']}")
            step.evidence(device=device)
        with self.rec.step("preflight_installed_apk_matches_build") as step:
            record = self.check_installed_apk()
            step.detail(f"installed {record['installed']['sha256'][:12]} == build; versionCode {record['device']['versionCode']}")
            step.evidence(installed=record["installed"], deviceFacts=record["device"])

    # ------------------------------------------------------------------ UI capture
    def capture_ui(self, label: str, *, png: bool | None = None) -> UiShot:
        self.seq += 1
        name = f"{self.seq:03d}-{slug(label)}"
        xml, meta = self.avd.capture_ui_xml()
        xml_path = self.ui_dir / (name + ".xml")
        xml_path.write_bytes(xml)
        top = self.avd.top_resumed()
        png_path = None
        if png or (png is None and self.png_all):
            png_path = self.ui_dir / (name + ".png")
            meta["pngSha256"] = self.avd.screenshot(png_path)
        shot = UiShot(label, name, xml.decode("utf-8", errors="replace"), xml_path, top, png_path, meta)
        self._log("ui", {"name": name, "label": label, "top": top, "xmlSha256": meta.get("sha256"),
                         "attempts": (meta.get("retry") or {}).get("attemptCount")})
        return shot

    def screenshot(self, label: str) -> Path:
        self.seq += 1
        path = self.ui_dir / f"{self.seq:03d}-{slug(label)}.png"
        digest = self.avd.screenshot(path)
        self._log("screenshot", {"label": label, "path": path.name, "sha256": digest})
        return path

    def diagnostics(self, shot: UiShot) -> str:
        return json.dumps({"xml": shot.name, "top": shot.top_resumed, "visible": shot.summary(25)}, ensure_ascii=False)[:1800]

    def require_main_top(self, shot: UiShot) -> None:
        if not uia.component_is(shot.top_resumed, PACKAGE, MAIN_ACTIVITY):
            raise StepFailure(f"MainActivity is not top-resumed: {shot.top_resumed!r}")

    # ------------------------------------------------------------------ actions
    def tap(self, selector: str, label: str, *, within_text: str | None = None, settle: float = 1.0,
            png: bool | None = None, shot: UiShot | None = None) -> dict[str, Any]:
        """Resolve ``selector`` in a fresh dump and tap its center exactly once."""
        shot = shot or self.capture_ui(label + "-before", png=png)
        try:
            target = (uia.resolve_scoped_clickable_target(shot.xml_text, selector, within_text)
                      if within_text else uia.resolve_clickable_target(shot.xml_text, selector))
        except uia.TargetResolutionError as error:
            raise StepFailure(f"tap {selector!r}: {error}; {self.diagnostics(shot)}") from error
        self.avd.tap(*target["center"])
        event = {"label": label, "selector": selector, "withinText": within_text, "target": target,
                 "beforeCapture": shot.name, "tapCount": 1}
        self.taps.append(event)
        self._log("tap", event)
        time.sleep(settle)
        return event

    def tap_if_present(self, selector: str, label: str, *, wait: float = 0.0, settle: float = 1.0) -> dict[str, Any] | None:
        deadline = time.monotonic() + wait
        attempt = 0
        while True:
            attempt += 1
            shot = self.capture_ui(f"{label}-probe{attempt:02d}")
            try:
                uia.resolve_clickable_target(shot.xml_text, selector)
            except uia.TargetResolutionError as error:
                if uia.is_ambiguous_target_error(error):
                    raise StepFailure(f"{selector!r} is ambiguous: {error}") from error
                if time.monotonic() >= deadline:
                    return None
                time.sleep(1.0)
                continue
            return self.tap(selector, label, settle=settle, shot=shot)

    def key(self, code: str, label: str, *, settle: float = 1.0) -> None:
        self.avd.keyevent(code)
        self._log("key", {"label": label, "key": code})
        time.sleep(settle)

    def swipe_up(self, label: str, shot: UiShot | None = None) -> tuple[int, int, int, int]:
        shot = shot or self.capture_ui(label + "-before")
        try:
            coords = uia.scroll_container_swipe(shot.xml_text)
        except uia.TargetResolutionError:
            coords = uia.screen_swipe_up(shot.xml_text)
        self.avd.swipe(*coords)
        self._log("swipe", {"label": label, "coords": coords})
        time.sleep(0.6)
        return coords

    def wait_ui(self, predicate: Callable[[UiShot], bool], label: str, *, timeout: float = 30.0,
                interval: float = 1.0, png: bool | None = None, what: str = "expected UI state") -> UiShot:
        deadline = time.monotonic() + timeout
        attempt = 0
        shot: UiShot | None = None
        while True:
            attempt += 1
            shot = self.capture_ui(f"{label}-{attempt:02d}", png=png)
            try:
                if predicate(shot):
                    return shot
            except uia.TargetResolutionError:
                pass
            if time.monotonic() >= deadline:
                raise StepFailure(f"timed out after {timeout:.0f}s waiting for {what}; {self.diagnostics(shot)}")
            time.sleep(interval)

    def dismiss_ime_if_shown(self, label: str) -> dict[str, Any]:
        observation = self.avd.ime_observation()
        record = {"label": label, "imeVisibility": observation.visibility,
                  "evidence": [list(item) for item in observation.evidence], "backSent": False}
        if observation.visibility == "unknown":
            raise StepFailure("dumpsys input_method did not prove the IME state; refusing to send Back")
        if observation.visibility == "shown":
            self.key("KEYCODE_BACK", label + "-ime-back", settle=0.8)
            record["backSent"] = True
            # Pass-3 tool fix: on a loaded host `dumpsys input_method` can say "shown" for an IME that is already
            # hiding, so the Back reaches the app and opens its exit-confirmation dialog.  Verify with a fresh dump and
            # close that dialog through its own "Continuar entrenando" action (never "Terminar"/"Abandonar").
            after = self.capture_ui(label + "-ime-back-after")
            if after.present("text:Continuar entrenando") and after.present("text:Abandonar sin guardar"):
                record["exitDialogOpenedByBack"] = True
                self.tap("text:Continuar entrenando", label + "-exit-dialog-continue", shot=after)
                record["exitDialogRecovered"] = True
        self._log("ime", record)
        return record

    def type_primary(self, value: str, label: str) -> dict[str, Any]:
        """Type into the dominant EditText (area >= 3x the next one), as the old drivers did."""
        if not re.fullmatch(r"[0-9.]{1,6}", value):
            raise ValueError("only bounded numeric QA input is typed")
        shot = self.capture_ui(label + "-before")
        try:
            target = uia.resolve_primary_input(shot.xml_text)
        except uia.TargetResolutionError as error:
            raise StepFailure(f"primary input: {error}; {self.diagnostics(shot)}") from error
        self.avd.tap(*target["center"])
        self.avd.key_combo("KEYCODE_CTRL_LEFT", "KEYCODE_A")
        self.avd.input_text(value)
        time.sleep(0.6)
        after = self.capture_ui(label + "-after")
        texts = [c["text"] for c in uia.edit_candidates(after.xml_text)]
        record = {"label": label, "typed": value, "target": target, "editTextsAfter": texts,
                  "valueVisible": any(value in text for text in texts)}
        self._log("type", record)
        return record

    # ------------------------------------------------------------------ lifecycle
    def launch_main(self) -> str:
        output = self.avd.start_main()
        top = self.avd.wait_main_top_resumed()
        self._log("launch", {"amStart": output, "top": top})
        return top

    def deeplink(self, uri: str, *, settle: float = 4.0) -> str:
        output = self.avd.start_deeplink(uri)
        time.sleep(settle)
        top = self.avd.wait_main_top_resumed()
        self._log("deeplink", {"uri": uri, "amStart": output, "top": top})
        return top

    def wait_home(self, label: str = "home") -> UiShot:
        def ready(shot: UiShot) -> bool:
            uia.resolve_clickable_target(shot.xml_text, HOME_SELECTOR)
            return True

        return self.wait_ui(ready, label, timeout=self.home_timeout, interval=2.0, png=True,
                            what="Home (muscle ring); is onboarding gating the fixture?")

    def workout_surface_present(self, shot: UiShot) -> bool:
        return any(shot.has(item) for item in WORKOUT_SURFACE_LABELS)

    def cold_restart(self, deeplink: str | None = None, label: str = "restart",
                     surface: Callable[[UiShot], bool] | None = None) -> UiShot:
        """Force-stop, relaunch, wait for Home, optionally re-enter a deep link and wait for ``surface``."""
        self.avd.force_stop(PACKAGE)
        self.launch_main()
        home = self.wait_home(label + "-home")
        if deeplink:
            self.deeplink(deeplink)
            return self.wait_ui(surface or self.workout_surface_present, label + "-surface", timeout=60,
                                what="workout surface")
        return home

    # ------------------------------------------------------------------ data
    def seed(self, fixture_name: str, *, grants: tuple[str, ...] = STARTUP_GRANTS, prefs: bool = True,
             preseed_backup: bool = True) -> dict[str, Any]:
        """pm clear -> push the verified fixture DB (+ audit prefs) -> pre-grant startup permissions."""
        record = fixtures.verify_fixture(fixture_name)
        prefs_record = fixtures.verify_prefs() if prefs else None
        result: dict[str, Any] = {"fixture": record, "prefs": prefs_record, "grants": {}}
        self.avd.force_stop(PACKAGE)
        if preseed_backup and self.avd.is_installed(PACKAGE):
            try:
                self.room_seq += 1
                shot = self.avd.room_pull(self.room_dir / f"{self.room_seq:02d}-preseed", stop_app=False)
                result["preSeedBackup"] = {"dir": f"room/{self.room_seq:02d}-preseed", "db": shot["database"]["db"]}
            except Exception as error:  # noqa: BLE001 - first run: nothing to preserve
                result["preSeedBackup"] = {"skipped": f"{type(error).__name__}: {error}"[:300]}
        result["pmClear"] = self.avd.pm_clear(PACKAGE)
        for directory in ("databases", "shared_prefs", "files"):
            self.avd.shell(f"run-as {PACKAGE} mkdir -p {directory}")
        db_hash = self.avd.run_as_push(Path(record["path"]), "databases/kpkn.db", staging_name=f"seed-{fixture_name}")
        if db_hash != record["sha256"]:
            raise StepFailure(f"fixture DB copied to the device has hash {db_hash}, expected {record['sha256']}")
        result["deviceDbSha256"] = db_hash
        if prefs_record:
            device_hashes = {}
            for path in sorted((fixtures.FIXTURES_DIR / fixtures.PREFS_DIR).glob("*.xml")):
                got = self.avd.run_as_push(path, f"shared_prefs/{path.name}", staging_name=f"pref-{slug(path.stem)}")
                if got != sha256_file(path):
                    raise StepFailure(f"preference file {path.name} copied with a different hash")
                device_hashes[path.name] = got
            result["devicePrefsSha256"] = device_hashes
        for permission in grants:
            self.avd.grant(permission)
            result["grants"][permission] = self.avd.permission_granted(permission)
        self._log("seed", result)
        write_json(self.out / "seed.json", result)
        return result

    def room(self, label: str, *, extra_files: tuple[str, ...] = ()) -> RoomShot:
        """Force-stop the app, copy db + WAL + prefs with hashes, summarize read-only."""
        self.room_seq += 1
        destination = self.room_dir / f"{self.room_seq:02d}-{slug(label)}"
        manifest = self.avd.room_pull(destination, stop_app=True, extra_private_files=extra_files)
        summary = room_inspect.inspect_database(room_inspect.db_path_of(destination))
        write_json(destination / "summary.json", summary)
        self._log("room", {"label": label, "dir": destination.name, "version": summary["databaseVersion"],
                           "counts": summary["counts"], "consistent": manifest["consistent"]})
        return RoomShot(destination, manifest, summary)

    # ------------------------------------------------------------------ finish
    def collect_logcat(self) -> None:
        try:
            self.avd.logcat_dump(self.out / "logcat.txt")
        except Exception as error:  # noqa: BLE001
            self.rec.note(f"logcat dump failed: {type(error).__name__}: {error}")

    def finalize(self) -> tuple[dict[str, Any], int]:
        if self.device_used:
            self.collect_logcat()
        self.rec.extra["tapCount"] = len(self.taps)
        # hash everything that was archived
        for path in sorted(self.out.rglob("*")):
            if path.is_file() and path.name not in {"files.json", "result.json"}:
                try:
                    self.rec.ledger.add(path)
                except OSError:
                    pass
        return self.rec.finish()


def execute(driver: str, args: argparse.Namespace, body: Callable[[QaSession], None],
            *, extra: dict[str, Any] | None = None, avd: Avd | None = None) -> int:
    """Create the session, run ``body`` and always print/save the JSON result."""
    try:
        session = QaSession(driver, args, extra=extra, avd=avd)
    except OwnedDeviceError as error:
        print(json.dumps({"driver": driver, "overall": "NOT_RUN", "reason": "REFUSED_DEVICE", "error": str(error)}, indent=2))
        return EXIT_MISSING_INPUT
    try:
        body(session)
    except RunAborted:
        pass
    except MissingInput as error:  # includes OwnedDeviceError (reason REFUSED_DEVICE)
        session.rec.extra["reason"] = getattr(error, "reason", "MISSING_INPUT")
        session.rec.note(f"{session.rec.extra['reason'].lower()}: {error}")
    except Exception as error:  # noqa: BLE001 - unexpected driver crash must still produce a result
        session.rec.crash(error)
    _result, code = session.finalize()
    return code
