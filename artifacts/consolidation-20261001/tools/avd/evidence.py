"""Evidence directories, hashing, and the PASS / FAIL / NOT_RUN step recorder.

Semantics used by every driver of this toolset
-----------------------------------------------
* ``PASS``     the step ran and its assertion held.
* ``FAIL``     the step ran and its assertion did not hold (or crashed).
* ``NOT_RUN``  the step did not execute (blocked by an earlier FAIL, missing
               input, or unavailable evidence).  NEVER counted as success.

A driver's overall result is ``FAIL`` if any required step failed, ``PASS`` only
if every required step passed, otherwise ``NOT_RUN``.  Steps registered with
``optional=True`` (corroborating evidence) can be NOT_RUN without preventing an
overall PASS, but a FAIL in an optional step still fails the run.

Exit codes: 0 PASS, 1 FAIL, 3 NOT_RUN, 4 MISSING_INPUT / precondition refused
(argparse usage errors keep Python's code 2).
"""

from __future__ import annotations

import contextlib
import hashlib
import json
import sys
import time
import traceback
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

from qa_paths import EVIDENCE_ROOT

PASS, FAIL, NOT_RUN = "PASS", "FAIL", "NOT_RUN"
EXIT_PASS, EXIT_FAIL, EXIT_NOT_RUN, EXIT_MISSING_INPUT = 0, 1, 3, 4
REFUSAL_REASONS = ("MISSING_INPUT", "REFUSED_DEVICE")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def utc_stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path | str) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def tools_fingerprint() -> dict[str, Any]:
    """SHA-256 of every tool script (not tests/fixtures): provenance of *which* tools produced a result."""
    folder = Path(__file__).resolve().parent
    per_file = {p.name: sha256_file(p) for p in sorted(folder.glob("*.py")) if p.is_file()}
    combined = hashlib.sha256("".join(f"{n}:{h}\n" for n, h in per_file.items()).encode()).hexdigest()
    return {"files": len(per_file), "sha256": combined, "perFile": per_file}


def write_json(path: Path, value: Any, *, exclusive: bool = False) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    mode = "x" if exclusive else "w"
    with path.open(mode, encoding="utf-8", newline="\n") as output:
        json.dump(value, output, ensure_ascii=False, indent=2, default=str)
        output.write("\n")
    return path


def read_json(path: Path | str) -> Any:
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def new_run_dir(kind: str, flavor: str | None = None, label: str | None = None, root: Path | None = None) -> Path:
    """Create ``<root>/<kind>/<flavor>/<UTC stamp>-<id>[-label]`` (never reuses a directory)."""
    safe = lambda text: "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in text)[:40]
    name = f"{utc_stamp()}-{uuid.uuid4().hex[:6]}" + (f"-{safe(label)}" if label else "")
    path = (root or EVIDENCE_ROOT) / safe(kind) / safe(flavor or "-") / name
    path.mkdir(parents=True, exist_ok=False)
    return path


class FileLedger:
    """Remembers every evidence file written during a run, with size and hash."""

    def __init__(self, base: Path) -> None:
        self.base = base
        self.entries: dict[str, dict[str, Any]] = {}

    def add(self, path: Path, *, kind: str = "file") -> dict[str, Any]:
        path = Path(path)
        try:
            relative = path.resolve().relative_to(self.base.resolve()).as_posix()
        except ValueError:
            relative = str(path)
        entry = {"path": relative, "kind": kind, "bytes": path.stat().st_size, "sha256": sha256_file(path)}
        self.entries[relative] = entry
        return entry

    def write(self, name: str = "files.json") -> Path:
        return write_json(self.base / name, sorted(self.entries.values(), key=lambda e: e["path"]))


class MissingInput(RuntimeError):
    """A required input (APK, fixture, baseline, tool) is absent or inconsistent.

    Raised during preflight, before any device state is touched.
    """

    def __init__(self, what: str, hint: str = "") -> None:
        super().__init__(what + (f" -- {hint}" if hint else ""))
        self.what = what
        self.hint = hint


class StepFailure(AssertionError):
    """Raised by ``Step.check`` / ``Step.fail`` to FAIL the step with a message."""


class StepNotRun(Exception):
    """Raised by ``Step.not_run`` to leave a step NOT_RUN and continue."""


class RunAborted(Exception):
    """A required step failed; remaining steps are marked NOT_RUN."""

    def __init__(self, step: str, message: str) -> None:
        super().__init__(f"{step}: {message}")
        self.step = step
        self.message = message


class Step:
    def __init__(self, recorder: "Recorder", name: str, optional: bool) -> None:
        self.recorder = recorder
        self.name = name
        self.optional = optional
        self.detail_text = ""
        self.evidence_data: dict[str, Any] = {}

    def detail(self, text: str) -> None:
        self.detail_text = text

    def evidence(self, **values: Any) -> None:
        self.evidence_data.update(values)

    def check(self, condition: Any, message: str) -> None:
        if not condition:
            raise StepFailure(message)

    def fail(self, message: str) -> None:
        raise StepFailure(message)

    def not_run(self, reason: str) -> None:
        raise StepNotRun(reason)


class Recorder:
    """Collects step outcomes and renders the final JSON result."""

    def __init__(self, driver: str, flavor: str | None, out_dir: Path, *, extra: dict[str, Any] | None = None,
                 stream: Any = None) -> None:
        self.driver = driver
        self.flavor = flavor
        self.out_dir = out_dir
        self.extra = dict(extra or {})
        self.stream = stream if stream is not None else sys.stderr
        self.started = utc_now()
        self.steps: dict[str, dict[str, Any]] = {}
        self.order: list[str] = []
        self.notes: list[str] = []
        self.aborted: RunAborted | None = None
        self.inputs: dict[str, Any] = {}
        self.ledger = FileLedger(out_dir)

    # -- declaration -------------------------------------------------------
    def declare(self, *names: str, optional: tuple[str, ...] = ()) -> None:
        for name in names:
            if name in self.steps:
                raise ValueError(f"step declared twice: {name}")
            self.steps[name] = {"name": name, "status": NOT_RUN, "detail": "not reached",
                                "optional": name in optional, "evidence": {}}
            self.order.append(name)

    def note(self, text: str) -> None:
        self.notes.append(text)
        self._emit(f"[note] {text}")

    def _emit(self, line: str) -> None:
        try:
            print(line, file=self.stream, flush=True)
        except Exception:  # pragma: no cover - closed stderr
            pass

    # -- execution ---------------------------------------------------------
    @contextlib.contextmanager
    def step(self, name: str, *, optional: bool = False) -> Iterator[Step]:
        if self.aborted is not None:
            raise RunAborted(self.aborted.step, self.aborted.message)
        if name not in self.steps:
            self.declare(name, optional=(name,) if optional else ())
        record = self.steps[name]
        record["optional"] = optional or record.get("optional", False)
        step = Step(self, name, record["optional"])
        started = time.monotonic()
        status: str
        detail: str
        try:
            yield step
            status, detail = PASS, step.detail_text
        except StepNotRun as reason:
            status, detail = NOT_RUN, str(reason) or step.detail_text
        except StepFailure as failure:
            status, detail = FAIL, str(failure)
        except RunAborted:
            raise
        except MissingInput as missing:
            reason = getattr(missing, "reason", "MISSING_INPUT")
            status, detail = NOT_RUN, f"{reason}: {missing}"
            self.extra["reason"] = reason
        except Exception as error:  # noqa: BLE001 - every crash is a FAIL with traceback
            status = FAIL
            detail = f"{type(error).__name__}: {error}"
            record["traceback"] = traceback.format_exc()[-4000:]
        record.update(status=status, detail=detail, evidence=step.evidence_data,
                      seconds=round(time.monotonic() - started, 3), atUtc=utc_now())
        self._emit(f"[{status}] {name}" + (f" - {detail}" if detail else ""))
        if status == FAIL or self.extra.get("reason") in REFUSAL_REASONS:
            self.aborted = RunAborted(name, detail)
            raise self.aborted

    def crash(self, error: BaseException) -> None:
        """Record an unexpected driver exception that happened outside any step."""
        name = "driver_crash"
        if name not in self.steps:
            self.declare(name)
        self.steps[name].update(
            status=FAIL, detail=f"{type(error).__name__}: {error}", atUtc=utc_now(),
            traceback="".join(traceback.format_exception(type(error), error, error.__traceback__))[-4000:],
        )
        self._emit(f"[FAIL] {name} - {type(error).__name__}: {error}")

    def mark_remaining_blocked(self) -> None:
        if self.aborted is None:
            return
        for name in self.order:
            record = self.steps[name]
            if record["status"] == NOT_RUN and record["detail"] == "not reached":
                record["detail"] = f"blocked by failed step {self.aborted.step}"

    # -- result ------------------------------------------------------------
    def overall(self) -> str:
        statuses = [(rec["status"], rec["optional"]) for rec in self.steps.values()]
        if any(status == FAIL for status, _ in statuses):
            return FAIL
        if any(status == NOT_RUN and not optional for status, optional in statuses):
            return NOT_RUN
        return PASS if statuses else NOT_RUN

    def result(self) -> dict[str, Any]:
        self.mark_remaining_blocked()
        overall = self.overall()
        counts = {PASS: 0, FAIL: 0, NOT_RUN: 0}
        for rec in self.steps.values():
            counts[rec["status"]] += 1
        return {
            "schema": "kpkn-avd-driver-result/v1",
            "driver": self.driver,
            "flavor": self.flavor,
            "overall": overall,
            "counts": counts,
            "startedAtUtc": self.started,
            "finishedAtUtc": utc_now(),
            "evidenceDir": str(self.out_dir),
            "steps": [self.steps[name] for name in self.order],
            "notes": self.notes,
            "inputs": self.inputs,
            **self.extra,
        }

    def finish(self, *, print_stdout: bool = True) -> tuple[dict[str, Any], int]:
        result = self.result()
        write_json(self.out_dir / "result.json", result)
        try:
            self.ledger.add(self.out_dir / "result.json", kind="result")
            self.ledger.write()
        except OSError:
            pass
        if print_stdout:
            print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
        missing = result.get("reason") in REFUSAL_REASONS and result["overall"] == NOT_RUN
        return result, (EXIT_MISSING_INPUT if missing else exit_code(result["overall"]))


def exit_code(overall: str) -> int:
    return {PASS: EXIT_PASS, FAIL: EXIT_FAIL, NOT_RUN: EXIT_NOT_RUN}.get(overall, EXIT_NOT_RUN)


def missing_input_result(driver: str, flavor: str | None, problems: list[str], out_dir: Path | None = None) -> dict[str, Any]:
    result = {
        "schema": "kpkn-avd-driver-result/v1",
        "driver": driver,
        "flavor": flavor,
        "overall": NOT_RUN,
        "reason": "MISSING_INPUT",
        "problems": problems,
        "finishedAtUtc": utc_now(),
        "deviceTouched": False,
        "steps": [],
    }
    if out_dir is not None:
        write_json(out_dir / "result.json", result)
    return result
