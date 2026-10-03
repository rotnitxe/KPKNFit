"""Synthetic GPS helpers for the owned AVDs (never a physical-GPS claim).

* ``prepare_gps``           grants location / notification permissions and enables location.
* ``GeoFeeder``             thread that sends ``adb emu geo fix`` points every few seconds.
* ``gps_service_running``   observes the cardio foreground service through dumpsys.
* ``cardio_gps_files``      lists the app-private GPS snapshot files.

Coordinates are generated test points around the emulator's default location; any
evidence produced with them proves app behavior on an emulator only.
"""

from __future__ import annotations

import re
import threading
from typing import Any

from avd import Avd
from qa_paths import PACKAGE

SERVICE_NAME = "CardioGpsForegroundService"
BASE_LAT, BASE_LON = 37.421998, -122.084000


def synthetic_point(index: int) -> tuple[float, float]:
    """Deterministic walk: 8 steps east, then one step north."""
    return BASE_LON + (index % 8) * 0.00018, BASE_LAT + (index // 8) * 0.00012


def prepare_gps(avd: Avd) -> dict[str, Any]:
    actions: dict[str, Any] = {}
    for permission in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                       "android.permission.POST_NOTIFICATIONS"):
        avd.grant(permission)
        actions[permission] = avd.permission_granted(permission)
    avd.shell("cmd", "location", "set-location-enabled", "true", check=False)
    mode = avd.shell("settings", "get", "secure", "location_mode", check=False)
    if mode in ("0", "null", ""):
        avd.shell("settings", "put", "secure", "location_mode", "3")
        mode = avd.shell("settings", "get", "secure", "location_mode", check=False)
    actions["locationMode"] = mode
    if mode in ("0", "null", ""):
        raise RuntimeError(f"location could not be enabled (location_mode={mode!r})")
    return actions


class GeoFeeder(threading.Thread):
    """Send synthetic ``emu geo fix`` points every ``interval`` seconds until stopped."""

    def __init__(self, avd: Avd, interval: float = 3.0) -> None:
        super().__init__(daemon=True)
        self.avd, self.interval = avd, interval
        self.stop_event = threading.Event()
        self.sent = 0
        self.error: str | None = None

    def run(self) -> None:
        index = 0
        while not self.stop_event.is_set():
            lon, lat = synthetic_point(index)
            try:
                self.avd.geo_fix(lon, lat, 10.0)
                self.sent += 1
            except Exception as error:  # noqa: BLE001
                self.error = f"{type(error).__name__}: {error}"
                return
            index += 1
            self.stop_event.wait(self.interval)

    def stop(self) -> None:
        self.stop_event.set()
        if self.is_alive():
            self.join(timeout=10)


def gps_service_running(avd: Avd) -> bool:
    out = avd.shell("dumpsys", "activity", "services", PACKAGE, timeout=60, check=False)
    return SERVICE_NAME in out and "app=ProcessRecord" in out


_SERVICE_RECORD_RE = re.compile(r"ServiceRecord\{[^}]*?\s(?P<component>com\.example\.kpkn[\w.:]*/[\w.$]+)(?=[\s}])")
WORKOUT_SERVICE_MARKERS = ("WorkoutVoiceForegroundService", "WorkoutRestForegroundService", "CardioGpsForegroundService",
                           "WorkoutForegroundService")


def app_workout_services(avd: Avd) -> list[str]:
    """Components of the app's live workout-related services (voice, rest timer, cardio GPS, ...) from dumpsys.

    Added during the base AVD validation (2026-10-02): "services / voice stopped after cancel" needs more than the
    cardio GPS service check.  Every ServiceRecord line of the package is parsed; only workout/cardio/voice ones are
    returned (other app services, e.g. WorkManager/telemetry, are irrelevant to the cancel contract).
    """
    out = avd.shell("dumpsys", "activity", "services", PACKAGE, timeout=60, check=False)
    found: list[str] = []
    for match in _SERVICE_RECORD_RE.finditer(out):
        component = match.group("component")
        if any(marker in component for marker in WORKOUT_SERVICE_MARKERS) and component not in found:
            found.append(component)
    return found


def cardio_gps_files(avd: Avd) -> list[dict[str, Any]]:
    return [row for row in avd.run_as_files_tree("files") if row["path"].startswith("files/cardio-gps/")]
