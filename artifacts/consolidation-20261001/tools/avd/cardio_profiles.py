"""Pure UI-profile and Room-checkpoint assertions for the two-series cardio flow.

Ported from OLDROOT ``run_cardio_two_series_ui.py`` (``validate_ui_profile`` and
``verify_room_checkpoint``) without its source/hash pins.  The visible strings come from
``CardioLiveCard.kt``; if the product changes them, the profile names the missing token.
"""

from __future__ import annotations

from typing import Any

from room_inspect import CARDIO_EXERCISE_ID, CARDIO_SERIES_IDS, expected_gps_session_key

CARDIO_EXERCISE_NAME = "Carrera GPS QA dos series"
# Base AVD validation (2026-10-02): the synthetic cardio exercise carries the squat's catalogDefinitionId/
# catalogConfigurationId, so the integrated build shows the catalog-resolved name instead of the fixture name.
CARDIO_EXERCISE_LABELS = (
    CARDIO_EXERCISE_NAME,
    "Sentadilla Trasera con Barra Baja · Barra",
    "Sentadilla Trasera con Barra Baja",
)
PROFILES =("visible", "cardio-ready", "gps-running", "gps-paused", "confirm-cardio", "after-cancel", "after-s1", "after-s2")
# "confirm-cardio" is deliberately NOT a chip profile: in the integrated build the confirmation is a separate dialog
# window and the UiAutomator dump contains only that window ("Confirmar cardio", the question, Cancelar, Registrar),
# not the Serie 1 / Serie 2 chips behind it (base AVD validation, 2026-10-02).
CHIP_PROFILES = {"cardio-ready", "gps-running", "gps-paused", "after-cancel", "after-s1", "after-s2"}


def _contains(tokens: list[str], fragment: str) -> bool:
    return any(fragment.casefold() in token.casefold() for token in tokens)


def exercise_label_present(tokens: list[str]) -> bool:
    """The cardio exercise title (fixture name or the catalog-resolved name of this build)."""
    return any(label in tokens for label in CARDIO_EXERCISE_LABELS)


def cardio_series_chips_present(tokens: list[str]) -> bool:
    """Both ``Serie n`` chips of the live cardio card (the strength rail uses S1/S2, never these tokens)."""
    return (any(token in ("Serie 1", "Serie 1 · Registrada") for token in tokens)
            and any(token in ("Serie 2", "Serie 2 · Registrada") for token in tokens))


def profile_problem(tokens: list[str], profile: str) -> str | None:
    """Return ``None`` when the visible tokens satisfy ``profile``, else a human-readable reason."""
    if profile not in PROFILES:
        return f"unknown UI profile {profile!r}"
    if profile in CHIP_PROFILES:
        if not exercise_label_present(tokens):
            return f"{profile}: exercise label {CARDIO_EXERCISE_NAME!r} not visible"
        if not any(token in ("Serie 1", "Serie 1 · Registrada") for token in tokens):
            return f"{profile}: Serie 1 chip missing"
        if not any(token in ("Serie 2", "Serie 2 · Registrada") for token in tokens):
            return f"{profile}: Serie 2 chip missing"
    if profile == "cardio-ready":
        if "Iniciar GPS" not in tokens:
            return "cardio-ready: 'Iniciar GPS' missing"
        if not _contains(tokens, "Listo · objetivo"):
            return "cardio-ready: 'Listo · objetivo' missing"
        if "Confirmar cardio" in tokens:
            return "cardio-ready: unexpected confirmation dialog"
    elif profile == "gps-running":
        if "Pausar GPS" not in tokens:
            return "gps-running: 'Pausar GPS' missing"
        if not _contains(tokens, "En curso ·"):
            return "gps-running: 'En curso ·' missing"
        if not _contains(tokens, "Señal GPS activa"):
            return "gps-running: 'Señal GPS activa' missing"
    elif profile == "gps-paused":
        if "Reanudar GPS" not in tokens:
            return "gps-paused: 'Reanudar GPS' missing"
        if not _contains(tokens, "Pausado ·"):
            return "gps-paused: 'Pausado ·' missing"
        if not _contains(tokens, "GPS pausado"):
            return "gps-paused: 'GPS pausado' missing"
    elif profile == "confirm-cardio":
        if "Confirmar cardio" not in tokens:
            return "confirm-cardio: dialog title missing"
        if not ({"Registrar", "Reintentar"} & set(tokens)):
            return "confirm-cardio: neither Registrar nor Reintentar"
        if "Cancelar" not in tokens:
            return "confirm-cardio: 'Cancelar' missing"
    elif profile == "after-cancel":
        if "Confirmar cardio" in tokens:
            return "after-cancel: confirmation dialog still open"
        if not _contains(tokens, "Pausado ·"):
            return "after-cancel: 'Pausado ·' missing (S1 timer not kept paused)"
        if "Finalizar y registrar" not in tokens:
            return "after-cancel: 'Finalizar y registrar' missing"
    elif profile == "after-s1":
        if "Serie 1 · Registrada" not in tokens:
            return "after-s1: 'Serie 1 · Registrada' missing"
        if "Iniciar GPS" not in tokens:
            return "after-s1: 'Iniciar GPS' missing (S2 should be the ready live card)"
        if not _contains(tokens, "Listo · objetivo"):
            return "after-s1: 'Listo · objetivo' missing"
        if any("Cardio registrado ·" in token for token in tokens):
            return "after-s1: series card still shows S1 as recorded; S2 should be the live card"
    elif profile == "after-s2":
        for chip in ("Serie 1 · Registrada", "Serie 2 · Registrada"):
            if chip not in tokens:
                return f"after-s2: {chip!r} missing"
    return None


def checkpoint_problem(state: dict[str, Any], checkpoint: str, program_id: str, session_id: str) -> str | None:
    """Assert a Room ``cardio_state`` against ``paused-s1`` / ``after-s1`` / ``after-s2``."""
    if state.get("databaseVersion") != 28:
        return f"expected Room v28 after upgrade, got {state.get('databaseVersion')!r}"
    ongoing = state.get("ongoing")
    if not ongoing:
        return "no ongoing_workout row captured"
    if ongoing.get("programId") != program_id or ongoing.get("sessionId") != session_id:
        return "captured Room state no longer belongs to the cardio fixture session"
    start = ongoing.get("startTimeMs")
    if not isinstance(start, (int, float)) or int(start) <= 0:
        return "startTimeMs absent or invalid"
    completed = ongoing.get("cardioCompletedSets") or {}
    key0, key1 = f"{CARDIO_EXERCISE_ID}_0", f"{CARDIO_EXERCISE_ID}_1"
    if checkpoint == "paused-s1":
        if key0 in completed or key1 in completed:
            return "paused-s1 must not have completed cardio series"
        if ongoing.get("activeExerciseId") != CARDIO_EXERCISE_ID or int(ongoing.get("activeSetIndex", -1)) != 0:
            return "paused-s1 lost the active S1 cursor"
        timer = ongoing.get("cardioTimerState") or {}
        if timer.get("status") != "PAUSED" or timer.get("setId") != CARDIO_SERIES_IDS[0]:
            return f"paused-s1 lacks the paused S1 timer identity: {timer}"
    elif checkpoint == "after-s1":
        if key0 not in completed or key1 in completed:
            return "S1 checkpoint must persist only the first cardio completion"
        if ongoing.get("activeExerciseId") != CARDIO_EXERCISE_ID or int(ongoing.get("activeSetIndex", -1)) != 1:
            return "after S1 the active cursor did not advance cleanly to S2"
    elif checkpoint == "after-s2":
        if key0 not in completed or key1 not in completed:
            return "final checkpoint must contain both completed cardio series"
        if not isinstance(completed[key0], dict) or not isinstance(completed[key1], dict):
            return "completed cardio series payloads are malformed"
    else:
        return f"unknown Room checkpoint {checkpoint!r}"
    if expected_gps_session_key(ongoing, 0) == expected_gps_session_key(ongoing, 1):
        return "S1 and S2 GPS execution keys unexpectedly collide"
    return None
