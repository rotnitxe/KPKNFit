"""Reach the live cardio card of the synthetic cardio fixtures through the real roadmap strip.

Why this exists (base AVD validation, 2026-10-02)
-------------------------------------------------
In the integrated build ``Session.allExercises()`` orders cardio parts AFTER the strength parts
(``cardioFirst = false``), and a process restart resumes the first incomplete step.  The synthetic
``cardio-two-series-ui-v27`` session therefore opens on the Floor Press approach card, and the cardio
exercise sits at the end of the roadmap strip under a "CARDIO" divider (card subtitle ``Estático · 60 min``).
A real user has to scroll the strip and tap that card; the driver does the same instead of assuming
the cardio card is the first page (the assumption inherited from OLDROOT).

Pure helpers are unit-tested offline; ``ensure_cardio_surface`` only uses ``QaSession`` primitives
(fresh XML before each action, single resolved tap, bounded swipes).
"""

from __future__ import annotations

import xml.etree.ElementTree as ET

import cardio_profiles as cp
import uia
from evidence import StepFailure

ROADMAP_CARDIO_CARD = "contains:Estático"
STRIP_ANCHOR_TEXT = "Accesorios"   # part label printed on every strength roadmap card
MAX_SWIPES = 6


def on_cardio_surface(tokens: list[str]) -> bool:
    return cp.cardio_series_chips_present(tokens)


def strip_swipe(xml_text: str) -> tuple[int, int, int, int]:
    """Horizontal swipe (content moves left) over the roadmap card strip."""
    root = ET.fromstring(xml_text)
    display = uia.display_bounds(root)
    if display is None:
        raise uia.TargetResolutionError("no display bounds in hierarchy")
    left, top, right, bottom = display
    width, height = right - left, bottom - top
    y = top + int(height * 0.93)
    best_top = -1
    for node in root.iter():
        if node.attrib.get("text") != STRIP_ANCHOR_TEXT:
            continue
        bounds = uia.parse_bounds(node.attrib.get("bounds"))
        if bounds and bounds[1] > best_top and bounds[1] > top + int(height * 0.6):
            best_top = bounds[1]
            y = (bounds[1] + bounds[3]) // 2
    return left + int(width * 0.82), y, left + int(width * 0.15), y


def ensure_cardio_surface(s, label: str, *, max_swipes: int = MAX_SWIPES):
    """Return a fresh shot showing the live cardio card, navigating via the roadmap strip when necessary."""
    shot = s.capture_ui(f"{label}-cardio-probe")
    if on_cardio_surface(shot.tokens):
        return shot
    for attempt in range(max_swipes + 1):
        try:
            uia.resolve_clickable_target(shot.xml_text, ROADMAP_CARDIO_CARD)
        except uia.TargetResolutionError as error:
            if uia.is_ambiguous_target_error(error):
                raise StepFailure(f"{ROADMAP_CARDIO_CARD!r} is ambiguous in the roadmap strip: {error}") from error
        else:
            s.tap(ROADMAP_CARDIO_CARD, f"{label}-cardio-card", shot=shot, settle=2.0)
            return s.wait_ui(lambda x: on_cardio_surface(x.tokens), f"{label}-cardio-surface", timeout=30, png=True,
                             what="live cardio card (Serie 1 / Serie 2 chips) after tapping the roadmap card")
        if attempt == max_swipes:
            break
        x1, y1, x2, y2 = strip_swipe(shot.xml_text)
        s.avd.swipe(x1, y1, x2, y2)
        s._log("swipe", {"label": f"{label}-strip-swipe{attempt + 1}", "coords": [x1, y1, x2, y2]})
        shot = s.capture_ui(f"{label}-strip-swipe{attempt + 1}")
    raise StepFailure(f"the cardio roadmap card ({ROADMAP_CARDIO_CARD}) was not found after {max_swipes} strip swipes; "
                      f"{s.diagnostics(shot)}")


def card_swipe_up(xml_text: str) -> tuple[int, int, int, int]:
    """Vertical swipe over the upper part of the screen (the cardio card; the bottom sheet is left alone)."""
    root = ET.fromstring(xml_text)
    display = uia.display_bounds(root)
    if display is None:
        raise uia.TargetResolutionError("no display bounds in hierarchy")
    left, top, right, bottom = display
    height = bottom - top
    x = left + (right - left) // 2
    return x, top + int(height * 0.45), x, top + int(height * 0.15)


def reveal(s, selector: str, label: str, *, max_swipes: int = 4):
    """Scroll the live cardio card until ``selector`` resolves to one clickable control; returns the fresh shot.

    Base AVD validation (2026-10-02): on the 1344x2992 AVD the bottom roadmap panel reserves ~47% of the screen
    while a cardio card is open, so "Finalizar y registrar" (below the metric tiles) is not in the UiAutomator
    tree until the card is scrolled (a real user has to scroll too).
    """
    shot = s.capture_ui(f"{label}-reveal-probe")
    for attempt in range(max_swipes + 1):
        try:
            uia.resolve_clickable_target(shot.xml_text, selector)
        except uia.TargetResolutionError as error:
            if uia.is_ambiguous_target_error(error):
                raise StepFailure(f"{selector!r} is ambiguous on the cardio card: {error}") from error
        else:
            return shot
        if attempt == max_swipes:
            break
        x1, y1, x2, y2 = card_swipe_up(shot.xml_text)
        s.avd.swipe(x1, y1, x2, y2)
        s._log("swipe", {"label": f"{label}-card-scroll{attempt + 1}", "coords": [x1, y1, x2, y2]})
        shot = s.capture_ui(f"{label}-card-scroll{attempt + 1}")
    raise StepFailure(f"{selector!r} not reachable after {max_swipes} card scrolls; {s.diagnostics(shot)}")


FINISH_SHEET_TITLE = "RESUMEN DE ENTRENAMIENTO"
FINISH_SHEET_BACK = "desc:Volver al entrenamiento"


def dismiss_finish_sheet(s, label: str, *, timeout: float = 20.0) -> bool:
    """Close the "Resumen de entrenamiento" sheet WITHOUT finishing the workout; True when it was open.

    Registering the last item of the roadmap order (the cardio card is last in this build) opens that sheet
    (``showFinishSheet`` is persisted, so it also reappears after a process restart).  The sheet's own
    "Volver al entrenamiento" button returns to the session; the finish (check) button is never touched.
    """
    import time
    shot = s.capture_ui(f"{label}-finish-sheet-probe")
    if not shot.present(f"text:{FINISH_SHEET_TITLE}") and not shot.has(FINISH_SHEET_TITLE):
        return False
    try:
        s.tap(FINISH_SHEET_BACK, f"{label}-finish-sheet-back", shot=shot, settle=1.5)
    except StepFailure:
        # fallback: drag the sheet down ("Arrastra hacia abajo para cerrar")
        root = ET.fromstring(shot.xml_text)
        display = uia.display_bounds(root)
        left, top, right, bottom = display
        x = left + (right - left) // 2
        s.avd.swipe(x, top + int((bottom - top) * 0.20), x, top + int((bottom - top) * 0.85))
        time.sleep(1.5)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        probe = s.capture_ui(f"{label}-finish-sheet-closed")
        if not probe.has(FINISH_SHEET_TITLE):
            return True
        time.sleep(1.0)
    raise StepFailure("the finish summary sheet did not close; " + s.diagnostics(probe))
