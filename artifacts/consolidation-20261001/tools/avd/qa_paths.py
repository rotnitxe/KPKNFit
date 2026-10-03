"""Static paths and device contracts for the consolidation AVD toolset.

Importing this module has no side effects (no adb, no file creation).

Layout (derived from this file's location, so the tree can be moved):

    <repo>/artifacts/consolidation-20261001/tools/avd/qa_paths.py   <- this file
    <repo>/artifacts/consolidation-20261001/device-evidence/        <- every run archives here
    <repo>/android-native/app/build/outputs/apk/...                 <- integrated APKs
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
CONSOLIDATION_ROOT = TOOLS_DIR.parents[1]
REPO_ROOT = TOOLS_DIR.parents[3]
ANDROID_NATIVE = REPO_ROOT / "android-native"
APP_DIR = ANDROID_NATIVE / "app"
APP_SRC = APP_DIR / "src"
ANDROID_TEST_SRC = APP_SRC / "androidTest"
MAIN_JAVA = APP_SRC / "main" / "java" / "com" / "example" / "kpkn"
SCHEMAS_DIR = APP_DIR / "schemas" / "com.example.kpkn.data.db.KpknDatabase"
EVIDENCE_ROOT = Path(os.environ.get("KPKN_QA_EVIDENCE_ROOT") or (CONSOLIDATION_ROOT / "device-evidence"))
FIXTURES_DIR = TOOLS_DIR / "fixtures"

PACKAGE = "com.example.kpkn"
TEST_PACKAGE = "com.example.kpkn.test"
RUNNER = TEST_PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"
MAIN_ACTIVITY = ".MainActivity"
FLAVORS = ("base", "health")

SDK_ROOT = Path(os.environ.get("KPKN_QA_SDK") or r"C:\Users\valen\AppData\Local\Android\Sdk")
ADB = Path(os.environ.get("KPKN_QA_ADB") or (SDK_ROOT / "platform-tools" / "adb.exe"))
EMULATOR_EXE = SDK_ROOT / "emulator" / "emulator.exe"
PYTHON = Path(r"C:\Users\valen\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe")


@dataclass(frozen=True)
class DeviceContract:
    """One emulator this toolset is allowed to touch."""

    serial: str
    avd_name: str
    port: int
    role: str
    avd_home: str | None  # None = the default AVD home of the current user


def _wizard_port() -> int:
    """Console port of the wizard AVD: 5582 unless KPKN_QA_WIZARD_PORT (even, 5560-5680, never 5554/5556) says otherwise."""
    raw = os.environ.get("KPKN_QA_WIZARD_PORT", "5582")
    port = int(raw) if raw.isdigit() else -1
    if port % 2 or not 5560 <= port <= 5680:
        raise ValueError(f"KPKN_QA_WIZARD_PORT={raw!r}: expected an even port in 5560-5680")
    return port


WIZARD_PORT = _wizard_port()

# The ONLY devices the toolset will ever address.  Everything else is refused
# before a single adb command is sent.  emulator-5554 (shared Pixel) and
# emulator-5556 are listed in FORBIDDEN_SERIALS as a second, independent guard.
OWNED_DEVICES: dict[str, DeviceContract] = {
    "emulator-5580": DeviceContract(
        serial="emulator-5580",
        avd_name="KPKNFitSessionAudit20260929",
        port=5580,
        role="audit",
        avd_home=r"C:\Users\valen\AppData\Local\Temp\KPKNFitSessionAuditAVDs",
    ),
    # ASSUMPTION (not verifiable offline): the wizard AVD is started by start_avd.ps1 -Avd Wizard on
    # console port 5582 -> emulator-5582 (override with KPKN_QA_WIZARD_PORT).  Whatever the port, the
    # serial is only trusted after `adb emu avd name` answers exactly KPKNWizchatQA.
    f"emulator-{WIZARD_PORT}": DeviceContract(
        serial=f"emulator-{WIZARD_PORT}",
        avd_name="KPKNWizchatQA",
        port=WIZARD_PORT,
        role="wizard",
        avd_home=None,
    ),
}
FORBIDDEN_SERIALS = frozenset({"emulator-5554", "emulator-5556"})
DEFAULT_SERIAL = "emulator-5580"

assert not (set(OWNED_DEVICES) & FORBIDDEN_SERIALS), "allowlist must never contain a forbidden serial"


def app_apk(flavor: str) -> Path:
    _check_flavor(flavor)
    return APP_DIR / "build" / "outputs" / "apk" / flavor / "debug" / f"app-{flavor}-debug.apk"


def test_apk(flavor: str) -> Path:
    _check_flavor(flavor)
    return (
        APP_DIR / "build" / "outputs" / "apk" / "androidTest" / flavor / "debug"
        / f"app-{flavor}-debug-androidTest.apk"
    )


def _check_flavor(flavor: str) -> None:
    if flavor not in FLAVORS:
        raise ValueError(f"flavor must be one of {FLAVORS}, got {flavor!r}")
