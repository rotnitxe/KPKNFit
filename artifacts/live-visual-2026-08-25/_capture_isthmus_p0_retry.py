#!/usr/bin/env python3
"""Robust clean isthmus P0 capture: warm-up, ANR Wait, readiness dismiss, chevron nav."""
from __future__ import annotations

import json
import re
import shutil
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
OUT = Path(__file__).resolve().parent
SRC = OUT / "kpkn-pull.db"
PKG = "com.example.kpkn"
PROG = "live-visual-prog"
SESS = "live-visual-sess"


def detect_serial() -> str:
    r = subprocess.run([ADB, "devices"], capture_output=True, text=True, check=True)
    for line in r.stdout.splitlines():
        if line.startswith("emulator-") and "\tdevice" in line:
            return line.split("\t")[0]
    raise SystemExit(f"No emulator device:\n{r.stdout}")


SERIAL = detect_serial()
print(f"SERIAL={SERIAL}", flush=True)


def adb(*a, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *a], check=check, capture_output=True, text=True)


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def sets(n, p):
    return [{"id": f"{p}-s{i}", "targetReps": 8, "targetRPE": 7.0} for i in range(n)]


def build_long() -> Path:
    exs = [{"id": "ex", "name": "Curl largo", "sets": sets(12, "ex")}]
    sess = {
        "id": SESS,
        "name": "DoD long isthmus",
        "dayOfWeek": 2,
        "assignedDays": [1, 2, 3, 4, 5, 6, 7],
        "exercises": exs,
    }
    prog = {
        "id": PROG,
        "name": "Live Visual DoD",
        "structure": "SIMPLE",
        "simpleProgramKind": "CYCLIC",
        "macrocycles": [
            {
                "id": "live-macro",
                "name": "M",
                "blocks": [
                    {
                        "id": "live-block",
                        "name": "B",
                        "mesocycles": [
                            {
                                "id": "live-meso",
                                "name": "Me",
                                "goal": "ACCUMULATION",
                                "weeks": [
                                    {
                                        "id": "live-visual-week",
                                        "name": "S1",
                                        "sessions": [sess],
                                        "executionKind": "TRAINING",
                                    }
                                ],
                            }
                        ],
                    }
                ],
            }
        ],
        "runState": {
            "runId": "live-run-1",
            "weekInstanceId": "live-visual-week",
            "status": "ACTIVE",
            "cycleNumber": 1,
        },
    }
    active = {
        "programId": PROG,
        "status": "ACTIVE",
        "currentMacrocycleIndex": 0,
        "currentBlockIndex": 0,
        "currentMesocycleIndex": 0,
        "currentWeekId": "live-visual-week",
        "currentMacrocycleId": "live-macro",
        "currentBlockId": "live-block",
        "currentMesocycleId": "live-meso",
        "currentWeekInstanceId": "live-visual-week",
        "currentCycleNumber": 1,
        "programRunId": "live-run-1",
    }
    settings = {
        "username": "Valen",
        "onboardingCompleted": True,
        "onboardingNameDone": True,
        "onboardingProgramDone": True,
        "onboardingNutritionDone": True,
        "hasSeenWelcome": True,
        "hasSeenHomeTour": True,
    }
    db = OUT / "kpkn-seed-long-isthmus.db"
    shutil.copyfile(SRC, db)
    c = sqlite3.connect(str(db))
    for t in ("programs", "active_program", "settings", "ongoing_workout"):
        c.execute(f"DELETE FROM {t}")
    c.execute(
        "INSERT INTO programs(id,name,data) VALUES (?,?,?)",
        (PROG, prog["name"], json.dumps(prog)),
    )
    c.execute("INSERT INTO active_program(rowId,data) VALUES (1,?)", (json.dumps(active),))
    c.execute("INSERT INTO settings(rowId,data) VALUES (1,?)", (json.dumps(settings),))
    c.commit()
    c.close()
    return db


def pull_shot(name: str) -> Path:
    sh(f"screencap -p /sdcard/{name}")
    dest = OUT / name
    adb("pull", f"/sdcard/{name}", str(dest))
    print(f"  shot {dest.name} size={dest.stat().st_size}", flush=True)
    return dest


def dump_ui() -> ET.Element:
    sh("uiautomator dump /sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", str(OUT / "_ui_tmp.xml"), check=False)
    return ET.parse(OUT / "_ui_tmp.xml").getroot()


def all_texts(root: ET.Element) -> list[str]:
    out = []
    for n in root.iter("node"):
        t = n.attrib.get("text") or ""
        if t.strip():
            out.append(t.strip())
    return out


def find_bounds(root: ET.Element, pred) -> tuple[int, int, int, int] | None:
    for n in root.iter("node"):
        if pred(n):
            b = n.attrib.get("bounds", "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
            if m:
                return tuple(map(int, m.groups()))  # type: ignore
    return None


def center(b):
    return ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)


def has_anr(root: ET.Element | None = None, img: Path | None = None) -> bool:
    if root is not None:
        texts = " | ".join(all_texts(root))
        if "isn't responding" in texts or "no responde" in texts.lower() or "Close app" in texts:
            return True
    if img and img.exists():
        # white dialog mid-screen heuristic
        im = Image.open(img).convert("RGB")
        w, h = im.size
        white = 0
        n = 0
        for y in range(int(h * 0.35), int(h * 0.55), 6):
            for x in range(int(w * 0.2), int(w * 0.8), 6):
                r, g, b = im.getpixel((x, y))
                n += 1
                if r > 230 and g > 230 and b > 230:
                    white += 1
        if white / max(n, 1) > 0.35:
            return True
    return False


def dismiss_anr() -> bool:
    root = dump_ui()
    # Prefer Wait
    for label in ("Wait", "Esperar", "wait"):
        b = find_bounds(root, lambda n, lab=label: (n.attrib.get("text") or "") == lab)
        if b:
            x, y = center(b)
            print(f"  ANR tap Wait {x},{y}", flush=True)
            sh(f"input tap {x} {y}")
            time.sleep(3)
            return True
    # fallback: right button of dialog ~ (700, 1280) on 1080x2400
    print("  ANR fallback tap Wait region", flush=True)
    sh("input tap 700 1280")
    time.sleep(3)
    return True


def looks_like_readiness(path: Path) -> bool:
    im = Image.open(path).convert("RGB")
    w, h = im.size
    neonish = bright_gray = samples = 0
    for y in range(int(h * 0.28), int(h * 0.55), 8):
        for x in range(int(w * 0.25), int(w * 0.75), 8):
            r, g, b = im.getpixel((x, y))
            samples += 1
            if r > 180 and g > 180 and b > 180 and abs(r - g) < 25 and abs(g - b) < 25:
                bright_gray += 1
            if (g > 150 and b > 150 and r < 120) or (b > 160 and r > 100 and g < 120) or (
                r > 180 and g > 80 and b < 80
            ):
                neonish += 1
    gray_ratio = bright_gray / max(samples, 1)
    neon_ratio = neonish / max(samples, 1)
    print(f"  pixel gray={gray_ratio:.3f} neon={neon_ratio:.3f}", flush=True)
    if neon_ratio > 0.035 and gray_ratio < 0.12:
        return True
    if gray_ratio < 0.03 and neon_ratio > 0.02:
        return True
    # text backup
    try:
        texts = " | ".join(all_texts(dump_ui()))
        if "preparado" in texts.lower() or "Reporta tu estado" in texts:
            return True
    except Exception:
        pass
    return False


def dismiss_readiness_loop(max_tries: int = 12) -> bool:
    for i in range(max_tries):
        shot = pull_shot("_probe.png")
        root = dump_ui()
        if has_anr(root, shot):
            dismiss_anr()
            continue
        if not looks_like_readiness(shot):
            # hide IME if any
            sh("input keyevent 111", check=False)  # ESCAPE
            time.sleep(0.3)
            print(f"  live after {i} tries", flush=True)
            return True
        # tap check button: white circle bottom
        b = find_bounds(
            root,
            lambda n: n.attrib.get("clickable") == "true"
            and "Check" in (n.attrib.get("content-desc") or "")
            or (n.attrib.get("text") or "") in ("✓",),
        )
        if b:
            x, y = center(b)
        else:
            # known check region
            x, y = 540, 2190
            # try white-blob centroid
            im = Image.open(shot).convert("RGB")
            w, h = im.size
            cands = []
            for y2 in range(int(h * 0.78), int(h * 0.96)):
                for x2 in range(int(w * 0.35), int(w * 0.65)):
                    r, g, bcol = im.getpixel((x2, y2))
                    if r > 220 and g > 220 and bcol > 220:
                        cands.append((x2, y2))
            if cands:
                x = sum(c[0] for c in cands) // len(cands)
                y = sum(c[1] for c in cands) // len(cands)
        print(f"  readiness tap {x},{y}", flush=True)
        sh(f"input tap {x} {y}")
        time.sleep(2.2)
    return not looks_like_readiness(pull_shot("_probe.png"))


def dock_progress() -> str | None:
    try:
        texts = all_texts(dump_ui())
    except Exception:
        return None
    for t in texts:
        m = re.search(r"(\d+)\s*/\s*(\d+)", t)
        if not m:
            continue
        cur, total = int(m.group(1)), int(m.group(2))
        if "Curl" in t or total >= 10:
            return f"{cur}/{total}"
    return None


def ensure_clean_frame(name: str) -> Path:
    for attempt in range(6):
        # dismiss IME
        sh("input keyevent 4", check=False)
        time.sleep(0.35)
        root = dump_ui()
        shot = pull_shot("_probe.png")
        if has_anr(root, shot):
            dismiss_anr()
            continue
        if looks_like_readiness(shot):
            dismiss_readiness_loop(6)
            continue
        # copy probe to final
        dest = OUT / name
        shutil.copyfile(shot, dest)
        print(f"  CLEAN {name} attempt={attempt} dock={dock_progress()}", flush=True)
        return dest
    dest = pull_shot(name)
    return dest


def tap_card_next():
    """Tap right chevron on live card header row."""
    root = dump_ui()
    # content-desc or near text "RPE"
    b = find_bounds(
        root,
        lambda n: "Next" in (n.attrib.get("content-desc") or "")
        or "Siguiente" in (n.attrib.get("content-desc") or "")
        or "forward" in (n.attrib.get("content-desc") or "").lower(),
    )
    if b:
        x, y = center(b)
        print(f"  chevron next {x},{y}", flush=True)
        sh(f"input tap {x} {y}")
    else:
        # empirical: right arrow on card ~ (980, 780) for 1080x2400 Medium phone
        # try a few candidates
        for x, y in ((1000, 760), (980, 800), (1020, 720), (960, 840)):
            print(f"  chevron fallback {x},{y}", flush=True)
            sh(f"input tap {x} {y}")
            time.sleep(0.25)
    time.sleep(0.85)


def swipe_next():
    sh("input swipe 920 1050 200 1050 260")
    time.sleep(0.85)


def seed_and_open(wait_s: float = 18.0):
    db = build_long()
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.7)
    adb("push", str(db), "/data/local/tmp/kpkn-seed.db")
    sh(
        "run-as com.example.kpkn sh -c '"
        "cd databases && rm -f kpkn.db-wal kpkn.db-shm && "
        "cat /data/local/tmp/kpkn-seed.db > kpkn.db'"
    )
    sh(
        "am start -W -a android.intent.action.VIEW "
        f"-d kpkn://workout/{PROG}/{SESS} "
        f"-n {PKG}/.MainActivity"
    )
    time.sleep(wait_s)


def fatals_anrs() -> tuple[int, int]:
    r = adb("logcat", "-d", "-v", "brief", check=False)
    text = r.stdout or ""
    (OUT / "logcat-isthmus-p0-retry.txt").write_text(text[-250000:], encoding="utf-8", errors="replace")
    fatals = len(re.findall(r"FATAL EXCEPTION", text))
    # Prefer Am_anr / "ANR in <pkg>"
    anrs = len(re.findall(r"ANR in com\.example\.kpkn", text))
    anrs += len(re.findall(r"Am_anr.*com\.example\.kpkn", text))
    return fatals, anrs


def card_center_y(path: Path) -> float | None:
    """Rough vertical center of large light-gray card via brightness scan."""
    im = Image.open(path).convert("L")
    w, h = im.size
    best_y = None
    best_run = 0
    for y in range(int(h * 0.2), int(h * 0.75), 4):
        row = [im.getpixel((x, y)) for x in range(int(w * 0.25), int(w * 0.9), 8)]
        bright = sum(1 for v in row if v > 170)
        if bright > best_run:
            best_run = bright
            best_y = y
    return float(best_y) if best_y else None


def main():
    summary = [f"serial={SERIAL}"]

    # Warm-up pass: open once to JIT compile, dismiss overlays, leave warm
    print("WARMUP", flush=True)
    seed_and_open(22)
    dismiss_readiness_loop(10)
    time.sleep(4)
    # second open without reinstall — warm process
    print("CAPTURE PASS", flush=True)
    adb("logcat", "-c", check=False)
    seed_and_open(12)
    if not dismiss_readiness_loop(12):
        ensure_clean_frame("40-isthmus-s1-FAIL.png")
        summary.append("FAIL readiness")
        (OUT / "isthmus-p0-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
        raise SystemExit(1)

    time.sleep(2)
    # hide keyboard
    sh("input keyevent 4", check=False)
    time.sleep(0.5)

    s1 = ensure_clean_frame("40-isthmus-s1-clean.png")
    y1 = card_center_y(s1)
    summary.append(f"S1 dock={dock_progress()} cardY={y1}")

    # Navigate to set index 2 (S3) — prefer swipe + verify dock
    for i in range(8):
        prog = dock_progress()
        print(f"  nav progress={prog}", flush=True)
        if prog and prog.startswith("2/"):
            break
        swipe_next()
        tap_card_next()
        sh("input keyevent 4", check=False)
        time.sleep(0.4)
    s3 = ensure_clean_frame("41-isthmus-s3-clean.png")
    y3 = card_center_y(s3)
    summary.append(f"S3 dock={dock_progress()} cardY={y3}")

    # Navigate to last (11/12)
    for i in range(20):
        prog = dock_progress()
        print(f"  nav-last progress={prog}", flush=True)
        if prog and prog.startswith("11/"):
            break
        swipe_next()
        if i % 3 == 2:
            tap_card_next()
        sh("input keyevent 4", check=False)
        time.sleep(0.35)
    last = ensure_clean_frame("42-isthmus-last-clean.png")
    yL = card_center_y(last)
    summary.append(f"LAST dock={dock_progress()} cardY={yL}")
    shutil.copyfile(last, OUT / "43-long-last-set-clean.png")

    # P1 adjacent
    sh("input swipe 200 1050 920 1050 260")
    time.sleep(0.8)
    ensure_clean_frame("44-adjacent-snap-clean.png")
    summary.append(f"adjacent dock={dock_progress()}")

    # P1 + continuity
    sh("input tap 80 1680")
    time.sleep(1.5)
    dismiss_readiness_loop(6)
    ensure_clean_frame("45-continuity-after-plus-clean.png")
    summary.append(f"plus dock={dock_progress()}")

    # blur attempt
    sh("input swipe 540 900 540 350 160")
    time.sleep(0.04)
    pull_shot("46-transition-blur-attempt.png")
    time.sleep(0.7)
    ensure_clean_frame("47-after-vertical-swipe.png")

    fatals, anrs = fatals_anrs()
    summary.append(f"fatals={fatals} anrs_pkg={anrs}")
    if y1 and y3 and yL:
        summary.append(f"cardY_delta_s1_s3={abs(y1-y3):.1f} s1_last={abs(y1-yL):.1f}")
    (OUT / "isthmus-p0-summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("DONE\n" + "\n".join(summary), flush=True)


if __name__ == "__main__":
    main()
