#!/usr/bin/env python3
"""Generate deterministic daily diagnostic reports and optionally enrich them with AI.

The script consumes an exported KPKN tree (``logs/<area>/<yyyyMMdd>``), keeps
physical line provenance, and writes ``reports/daily/YYYY-MM-DD/*.md``.  AI is
optional: without the provider environment variables the deterministic report
is still useful and explicitly says that enrichment was not configured.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


AREAS = ("voice", "workout", "nutrition", "performance", "auge", "reports")
EVENTS_NEVER_AGGREGATE = {
    "user_comment",
    "report_created",
    "report_context",
    "report_ai_enrichment",
    "report_ai_failed",
}
ERROR_MARKERS = ("error", "failed", "failure", "crash", "exception", "blocked", "rejected")
REF_RE = re.compile(r"(?P<file>[^\s\[\]]+\.jsonl)#L(?P<start>\d+)(?:-L(?P<end>\d+))?")


@dataclass(frozen=True)
class EventLine:
    area: str
    file: str
    line: int
    data: dict[str, Any]

    @property
    def event(self) -> str:
        return str(self.data.get("event", "unknown"))

    @property
    def event_id(self) -> str:
        return str(self.data.get("eventId", ""))

    def ref(self, end: int | None = None) -> dict[str, Any]:
        return {
            "file": self.file,
            "lineStart": self.line,
            "lineEnd": end or self.line,
            "eventId": self.event_id,
        }


def parse_date(value: str) -> tuple[str, str]:
    for fmt in ("%Y-%m-%d", "%Y%m%d"):
        try:
            parsed = dt.datetime.strptime(value, fmt).date()
            return parsed.isoformat(), parsed.strftime("%Y%m%d")
        except ValueError:
            pass
    raise SystemExit(f"invalid date {value!r}; use YYYY-MM-DD or YYYYMMDD")


def find_log_root(root: Path) -> Path:
    candidates = (root / "logs", root / "KPKN" / "logs", root / "kpkn_logs", root)
    for candidate in candidates:
        if any((candidate / area).is_dir() for area in AREAS):
            return candidate
    raise SystemExit(f"could not find logs/<area> under {root}")


def export_relative(file: Path, export_root: Path, log_root: Path) -> str:
    try:
        relative = file.relative_to(export_root).as_posix()
    except ValueError:
        relative = file.relative_to(log_root).as_posix()
        relative = f"logs/{relative}"
    if relative.startswith("KPKN/"):
        relative = relative.removeprefix("KPKN/")
    if relative.startswith("kpkn_logs/"):
        relative = f"logs/{relative.removeprefix('kpkn_logs/')}"
    return relative


def load_events(
    log_root: Path,
    export_root: Path,
    area: str,
    day_dir: str,
) -> tuple[list[EventLine], list[str]]:
    events: list[EventLine] = []
    errors: list[str] = []
    area_root = log_root / area
    candidates = sorted(area_root.glob(f"{day_dir}/*.jsonl"))
    for file in candidates:
        reference_file = export_relative(file, export_root, log_root)
        try:
            lines = file.read_text(encoding="utf-8").splitlines()
        except OSError as exc:
            errors.append(f"{reference_file}: read failed: {exc}")
            continue
        for line_number, raw in enumerate(lines, 1):
            if not raw.strip():
                continue
            try:
                value = json.loads(raw)
            except json.JSONDecodeError as exc:
                errors.append(f"{reference_file}#L{line_number}: invalid JSON: {exc.msg}")
                continue
            if not isinstance(value, dict):
                errors.append(f"{reference_file}#L{line_number}: event is not an object")
                continue
            contract_errors = validate_event(value)
            if contract_errors:
                errors.append(f"{reference_file}#L{line_number}: {'; '.join(contract_errors)}")
                continue
            events.append(EventLine(area, reference_file, line_number, value))
    events.sort(key=lambda item: (str(item.data.get("timestamp", "")), item.file, item.line))
    return events, errors


def validate_event(value: dict[str, Any]) -> list[str]:
    required = {
        "schemaVersion", "eventId", "timestamp", "elapsedMs", "area", "event",
        "screen", "sessionId", "traceId", "process",
    }
    errors: list[str] = []
    missing = sorted(required - value.keys())
    if missing:
        errors.append("missing " + ", ".join(missing))
    if value.get("schemaVersion") != 2:
        errors.append("schemaVersion must be 2")
    if value.get("area") not in AREAS:
        errors.append(f"invalid area {value.get('area')!r}")
    for key in ("eventId", "timestamp", "event", "screen", "sessionId", "traceId", "process"):
        if key in value and not isinstance(value[key], str):
            errors.append(f"{key} must be a string")
    if "elapsedMs" in value and (not isinstance(value["elapsedMs"], int) or isinstance(value["elapsedMs"], bool)):
        errors.append("elapsedMs must be an integer")
    process = value.get("process")
    if process != "main" and not str(process).startswith(":"):
        errors.append("process must be main or a colon-prefixed process")

    def finite(item: Any, path: str = "$") -> None:
        if isinstance(item, float) and not math.isfinite(item):
            errors.append(f"non-finite number at {path}")
        elif isinstance(item, dict):
            for key, child in item.items():
                finite(child, f"{path}.{key}")
        elif isinstance(item, list):
            for index, child in enumerate(item):
                finite(child, f"{path}[{index}]")

    finite(value)
    return errors


def ref_string(ref: dict[str, Any]) -> str:
    file = str(ref.get("file", ""))
    start = int(ref.get("lineStart", 0))
    end = int(ref.get("lineEnd", start))
    if not file or start < 1:
        return ""
    return f"{file}#L{start}" + (f"-L{end}" if end != start else "")


def render_ref(ref: dict[str, Any]) -> str:
    value = ref_string(ref)
    if not value:
        return ""
    event_id = str(ref.get("eventId", "")).strip()
    suffix = f" <!-- eventId: {event_id} -->" if event_id else ""
    return f"[{value}]{suffix}"


def is_rare_or_sensitive(event: EventLine) -> bool:
    name = event.event.lower()
    return name in EVENTS_NEVER_AGGREGATE or any(marker in name for marker in ERROR_MARKERS)


def preaggregate(events: list[EventLine]) -> tuple[list[dict[str, Any]], int]:
    """Aggregate only adjacent high-volume events and keep all risky events intact."""
    output: list[dict[str, Any]] = []
    omitted = 0
    index = 0
    while index < len(events):
        current = events[index]
        if is_rare_or_sensitive(current) or current.event not in {"frame_jank", "trace_metric"}:
            output.append({"kind": "event", "event": current, "ref": current.ref()})
            index += 1
            continue
        end = index
        while end + 1 < len(events):
            candidate = events[end + 1]
            if candidate.event != current.event or is_rare_or_sensitive(candidate):
                break
            end += 1
        run = events[index:end + 1]
        if len(run) < 3:
            output.extend({"kind": "event", "event": item, "ref": item.ref()} for item in run)
        else:
            numeric = [
                float(item.data.get("p95FrameMs", item.data.get("durationMs", 0)))
                for item in run
                if isinstance(item.data.get("p95FrameMs", item.data.get("durationMs", 0)), (int, float))
            ]
            numeric.sort()
            p95 = numeric[min(len(numeric) - 1, max(0, math.ceil(len(numeric) * 0.95) - 1))] if numeric else None
            output.append({
                "kind": "aggregate",
                "event": current,
                "count": len(run),
                "p95": p95,
                "ref": current.ref(run[-1].line),
            })
            omitted += len(run) - 1
        index = end + 1
    return output, omitted


def bundle_lines(records: list[dict[str, Any]]) -> list[str]:
    lines: list[str] = []
    for record in records:
        ref = ref_string(record["ref"])
        if record["kind"] == "event":
            data = dict(record["event"].data)
            data["_physicalRef"] = ref
            lines.append(f"[{ref}] {json.dumps(data, ensure_ascii=False, separators=(',', ':'))}")
        else:
            lines.append(json.dumps({
                "aggregated": record["event"].event,
                "count": record["count"],
                "p95": record["p95"],
                "refRange": record["ref"],
                "_physicalRef": ref,
            }, ensure_ascii=False, separators=(',', ':')))
    return lines


def event_ref_for(record: dict[str, Any]) -> dict[str, Any]:
    return dict(record["ref"])


def deterministic_payload(area: str, date: str, events: list[EventLine], records: list[dict[str, Any]], errors: list[str], omitted: int) -> dict[str, Any]:
    event_refs = [event_ref_for(record) for record in records]
    first_ref = event_refs[0] if event_refs else None
    last_ref = event_refs[-1] if event_refs else None
    error_events = [event for event in events if is_rare_or_sensitive(event)]
    health = max(0, 100 - min(70, len(error_events) * 5) - min(20, len(errors) * 2))
    facts: list[dict[str, Any]] = []
    if first_ref:
        facts.append({
            "text": f"Se procesaron {len(events)} eventos válidos del área {area}.",
            "evidenceRefs": [first_ref, last_ref] if last_ref and last_ref != first_ref else [first_ref],
        })
    if omitted and records:
        facts.append({
            "text": f"Se preagregaron {omitted} líneas de alta frecuencia; el rango físico queda conservado.",
            "evidenceRefs": [event_ref_for(records[0])],
        })
    if error_events:
        facts.append({
            "text": f"Se detectaron {len(error_events)} eventos de error, fallo o cierre sensible que no fueron agregados.",
            "evidenceRefs": [error_events[0].ref()],
        })
    user_claims = []
    for event in events:
        if event.event == "user_comment" or "userComment" in event.data:
            text = event.data.get("text", event.data.get("userComment", ""))
            user_claims.append({
                "reportId": event.data.get("reportId"),
                "text": str(text),
                "screen": event.data.get("screen"),
                "linkedEventRefs": [event.ref()],
            })
    missing = list(errors[:8])
    if omitted:
        missing.append(f"{omitted} líneas de alta frecuencia fueron preagregadas; revisar el rango citado si se necesita detalle.")
    return {
        "area": area,
        "date": date,
        "summary": f"Reporte determinista de {area}: {len(events)} eventos válidos, healthScore {health}.",
        "healthScore": health,
        "facts": facts,
        "userClaims": user_claims,
        "hypotheses": [],
        "inconsistencies": [],
        "missingEvidence": missing,
        "suggestedChecks": ["Revisar los eventos sensibles y cualquier rango preagregado antes de concluir causalidad."],
        "tags": [area, "deterministic", "refs_validated" if not errors else "schema_errors"],
    }


def provider_payload(system_prompt: str, user_prompt: str, model: str, endpoint: str, api_key: str) -> dict[str, Any]:
    request_body = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": 0,
        "response_format": {"type": "json_object"},
    }).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=request_body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        body = json.loads(response.read().decode("utf-8"))
    content = body["choices"][0]["message"]["content"]
    if isinstance(content, list):
        content = "".join(str(part.get("text", "")) for part in content if isinstance(part, dict))
    start, end = str(content).find("{"), str(content).rfind("}")
    if start < 0 or end <= start:
        raise ValueError("provider response did not contain a JSON object")
    result = json.loads(str(content)[start:end + 1])
    if not isinstance(result, dict):
        raise ValueError("provider response was not an object")
    return result


SYSTEM_PROMPT = """Eres el analista de calidad de KPKN Fit. Contrato exacto: daily-report-v1.
Recibirás líneas JSONL de un único apartado, cada una prefijada con su referencia física,
más comentarios manuales y de voz intercalados por tiempo.
Devuelve exclusivamente JSON válido con este esquema:
{"area":"...","date":"YYYY-MM-DD","summary":"...","healthScore":0,"facts":[{"text":"","evidenceRefs":[{"file":"","lineStart":0,"lineEnd":0,"eventId":""}]}],"userClaims":[{"reportId":"","text":"","screen":"","linkedEventRefs":[]}],"hypotheses":[{"text":"","basedOn":[],"confidence":0.0}],"inconsistencies":[{"text":"","evidenceRefs":[],"severity":"low|medium|high"}],"missingEvidence":[],"suggestedChecks":[],"tags":[]}
Reglas duras: nunca presentes hipótesis como hechos; toda afirmación lleva evidenceRefs
que existan en el bundle; no inventes eventos ni líneas; si el bundle fue pre-agregado,
cita el agregado y marca lo omitido en missingEvidence; no propongas acciones destructivas."""


def refs_in_payload(payload: Any) -> Iterable[dict[str, Any]]:
    if isinstance(payload, dict):
        for key, value in payload.items():
            if key in {"evidenceRefs", "linkedEventRefs", "basedOn"} and isinstance(value, list):
                for ref in value:
                    if isinstance(ref, dict):
                        yield ref
                    elif isinstance(ref, str):
                        match = REF_RE.search(ref)
                        if match:
                            yield {
                                "file": match.group("file"),
                                "lineStart": int(match.group("start")),
                                "lineEnd": int(match.group("end") or match.group("start")),
                                "eventId": "",
                            }
            yield from refs_in_payload(value)
    elif isinstance(payload, list):
        for value in payload:
            yield from refs_in_payload(value)


def validate_payload(payload: dict[str, Any], area: str, date: str) -> list[str]:
    """Validate the daily-report-v1 shape before accepting provider output."""
    required = {
        "area", "date", "summary", "healthScore", "facts", "userClaims",
        "hypotheses", "inconsistencies", "missingEvidence", "suggestedChecks", "tags",
    }
    errors: list[str] = []
    missing = sorted(required - payload.keys())
    if missing:
        errors.append("missing " + ", ".join(missing))
    if payload.get("area") != area:
        errors.append("area does not match the requested bundle")
    if payload.get("date") != date:
        errors.append("date does not match the requested bundle")
    if not isinstance(payload.get("summary"), str):
        errors.append("summary must be a string")
    score = payload.get("healthScore")
    if isinstance(score, bool) or not isinstance(score, (int, float)) or not 0 <= score <= 100:
        errors.append("healthScore must be between 0 and 100")
    for key in ("facts", "userClaims", "hypotheses", "inconsistencies", "missingEvidence", "suggestedChecks", "tags"):
        if not isinstance(payload.get(key), list):
            errors.append(f"{key} must be a list")

    def require_ref_list(item: Any, key: str, path: str) -> None:
        refs = item.get(key) if isinstance(item, dict) else None
        if not isinstance(refs, list) or not refs:
            errors.append(f"{path}.{key} must contain evidence references")
            return
        for index, ref in enumerate(refs):
            if not isinstance(ref, dict):
                errors.append(f"{path}.{key}[{index}] must be an object")
                continue
            for field in ("file", "eventId"):
                if not isinstance(ref.get(field), str) or not str(ref.get(field)).strip():
                    errors.append(f"{path}.{key}[{index}].{field} is required")
            for field in ("lineStart", "lineEnd"):
                value = ref.get(field)
                if isinstance(value, bool) or not isinstance(value, int) or value < 1:
                    errors.append(f"{path}.{key}[{index}].{field} must be a positive integer")

    for index, item in enumerate(payload.get("facts", []) if isinstance(payload.get("facts"), list) else []):
        if not isinstance(item, dict) or not isinstance(item.get("text"), str):
            errors.append(f"facts[{index}] must contain text")
        else:
            require_ref_list(item, "evidenceRefs", f"facts[{index}]")
    for index, item in enumerate(payload.get("userClaims", []) if isinstance(payload.get("userClaims"), list) else []):
        if not isinstance(item, dict) or not isinstance(item.get("text"), str):
            errors.append(f"userClaims[{index}] must contain text")
        else:
            require_ref_list(item, "linkedEventRefs", f"userClaims[{index}]")
    for index, item in enumerate(payload.get("hypotheses", []) if isinstance(payload.get("hypotheses"), list) else []):
        if not isinstance(item, dict) or not isinstance(item.get("text"), str):
            errors.append(f"hypotheses[{index}] must contain text")
        else:
            require_ref_list(item, "basedOn", f"hypotheses[{index}]")
        confidence = item.get("confidence") if isinstance(item, dict) else None
        if isinstance(confidence, bool) or not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1:
            errors.append(f"hypotheses[{index}].confidence must be between 0 and 1")
    for index, item in enumerate(payload.get("inconsistencies", []) if isinstance(payload.get("inconsistencies"), list) else []):
        if not isinstance(item, dict) or not isinstance(item.get("text"), str):
            errors.append(f"inconsistencies[{index}] must contain text")
        else:
            require_ref_list(item, "evidenceRefs", f"inconsistencies[{index}]")
        if isinstance(item, dict) and item.get("severity") not in {"low", "medium", "high"}:
            errors.append(f"inconsistencies[{index}].severity is invalid")
    return errors


def resolve_export_file(export_root: Path, reference: str) -> Path:
    """Resolve both a flat export root and a root containing ``KPKN/``."""
    candidates = (export_root / reference, export_root / "KPKN" / reference)
    return next((candidate for candidate in candidates if candidate.is_file()), candidates[0])


def refs_are_valid(payload: dict[str, Any], export_root: Path, records: list[dict[str, Any]]) -> bool:
    allowed = [
        (
            str(record["ref"].get("file", "")),
            int(record["ref"].get("lineStart", 0)),
            int(record["ref"].get("lineEnd", record["ref"].get("lineStart", 0))),
            str(record["ref"].get("eventId", "")),
        )
        for record in records
    ]
    for ref in refs_in_payload(payload):
        file = Path(str(ref.get("file", "")))
        start = int(ref.get("lineStart", 0) or 0)
        end = int(ref.get("lineEnd", start) or start)
        target = resolve_export_file(export_root, file)
        normalized_file = file.as_posix()
        expected = str(ref.get("eventId", "")).strip()
        if not target.is_file() or start < 1 or end < start or not expected:
            return False
        if not any(
            normalized_file == allowed_file and
            start >= allowed_start and end <= allowed_end and expected == allowed_event_id
            for allowed_file, allowed_start, allowed_end, allowed_event_id in allowed
        ):
            return False
        try:
            lines = target.read_text(encoding="utf-8").splitlines()
        except OSError:
            return False
        if end > len(lines):
            return False
        if not any(expected in lines[index - 1] for index in range(start, end + 1)):
            return False
    return True


def md_ref_list(value: Any) -> str:
    if not isinstance(value, list):
        return ""
    rendered: list[str] = []
    for ref in value:
        if isinstance(ref, dict):
            item = render_ref(ref)
        elif isinstance(ref, str):
            item = f"[{ref}]" if "#L" in ref else ""
        else:
            item = ""
        if item:
            rendered.append(item)
    return " ".join(rendered)


def write_markdown(path: Path, payload: dict[str, Any], model: str, bundle_count: int, cited_count: int, ai_status: str) -> None:
    area = str(payload.get("area", "unknown")).upper()
    date = str(payload.get("date", "unknown"))
    lines = [
        f"# Reporte diario — {area} — {date}",
        f"> healthScore: {payload.get('healthScore', '—')} · generado por {model} · bundle: {bundle_count} líneas ({cited_count} citadas) · IA: {ai_status}",
        "",
        "## Hechos",
    ]
    for item in payload.get("facts", []) if isinstance(payload.get("facts"), list) else []:
        if isinstance(item, dict):
            text = str(item.get("text", "")).strip()
            refs = md_ref_list(item.get("evidenceRefs"))
            if text:
                lines.append(f"- {text}{(' ' + refs) if refs else ''}")
    lines.extend(["", "## Lo que reportaste"])
    for item in payload.get("userClaims", []) if isinstance(payload.get("userClaims"), list) else []:
        if isinstance(item, dict):
            text = str(item.get("text", "")).strip()
            report_id = str(item.get("reportId", "")).strip()
            refs = md_ref_list(item.get("linkedEventRefs"))
            if text:
                prefix = f"[{report_id}] " if report_id else ""
                lines.append(f"- {prefix}{text}{(' ' + refs) if refs else ''}")
    lines.extend(["", "## Hipótesis (etiquetadas, con confianza)"])
    for item in payload.get("hypotheses", []) if isinstance(payload.get("hypotheses"), list) else []:
        if isinstance(item, dict):
            text = str(item.get("text", "")).strip()
            confidence = item.get("confidence", "—")
            refs = md_ref_list(item.get("basedOn"))
            if text:
                lines.append(f"- {text} (confianza: {confidence}){(' ' + refs) if refs else ''}")
    lines.extend(["", "## Inconsistencias detectadas"])
    for item in payload.get("inconsistencies", []) if isinstance(payload.get("inconsistencies"), list) else []:
        if isinstance(item, dict):
            text = str(item.get("text", "")).strip()
            severity = str(item.get("severity", "unknown"))
            refs = md_ref_list(item.get("evidenceRefs"))
            if text:
                lines.append(f"- [{severity}] {text}{(' ' + refs) if refs else ''}")
    lines.extend(["", "## Evidencia faltante"])
    for item in payload.get("missingEvidence", []) if isinstance(payload.get("missingEvidence"), list) else []:
        lines.append(f"- {item}")
    lines.extend(["", "## Chequeos sugeridos"])
    for item in payload.get("suggestedChecks", []) if isinstance(payload.get("suggestedChecks"), list) else []:
        lines.append(f"- {item}")
    lines.extend(["", "## Tags"])
    tags = payload.get("tags", [])
    lines.append(" ".join(f"`{tag}`" for tag in tags) if isinstance(tags, list) else "")
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def generate_area(area: str, date: str, day_dir: str, log_root: Path, export_root: Path, output_root: Path, ai_enabled: bool) -> Path:
    events, errors = load_events(log_root, export_root, area, day_dir)
    records, omitted = preaggregate(events)
    deterministic = deterministic_payload(area, date, events, records, errors, omitted)
    bundle = "\n".join(bundle_lines(records))
    output_dir = output_root / "reports" / "daily" / date
    output_dir.mkdir(parents=True, exist_ok=True)
    model = os.environ.get("KPKN_AI_MODEL", "deepseek-v4-flash")
    ai_status = "not_configured"
    payload = deterministic
    if ai_enabled:
        api_key = os.environ.get("KPKN_AI_API_KEY", "").strip()
        endpoint = os.environ.get("KPKN_AI_ENDPOINT", "https://api.deepseek.com/chat/completions").strip()
        if api_key:
            try:
                candidate = provider_payload(
                    SYSTEM_PROMPT,
                    f"Área: {area}\nFecha: {date}\nBundle JSONL:\n{bundle}",
                    model,
                    endpoint,
                    api_key,
                )
                candidate.setdefault("area", area)
                candidate.setdefault("date", date)
                payload_errors = validate_payload(candidate, area, date)
                if not payload_errors and refs_are_valid(candidate, export_root, records):
                    payload = candidate
                    ai_status = "enriched"
                else:
                    if payload_errors:
                        payload["missingEvidence"].append("ai_schema_invalid: " + "; ".join(payload_errors[:4]))
                    else:
                        payload["missingEvidence"].append("refs_invalid: la respuesta IA citó archivos, líneas o eventId inexistentes.")
                    payload["tags"].append("refs_invalid")
                    ai_status = "refs_invalid"
            except (OSError, ValueError, KeyError, json.JSONDecodeError, urllib.error.URLError) as exc:
                payload["missingEvidence"].append(f"ai_failed: {type(exc).__name__}")
                payload["tags"].append("ai_failed")
                ai_status = "failed"
        else:
            payload["missingEvidence"].append("IA no configurada: falta KPKN_AI_API_KEY.")
    report_path = output_dir / f"{area}.md"
    write_markdown(report_path, payload, model, len(bundle.splitlines()) if bundle else 0, sum(1 for _ in refs_in_payload(payload)), ai_status)
    return report_path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-root", type=Path, default=Path("."), help="export root containing logs/ or KPKN/logs/")
    parser.add_argument("--output-root", type=Path, default=None, help="where reports/ is written; defaults to input root")
    parser.add_argument("--date", default=dt.date.today().isoformat(), help="YYYY-MM-DD or YYYYMMDD")
    parser.add_argument("--area", choices=AREAS, action="append", help="repeat to generate only selected areas")
    parser.add_argument("--no-ai", action="store_true", help="never call the configured provider")
    args = parser.parse_args()
    export_root = args.input_root.resolve()
    output_root = (args.output_root or args.input_root).resolve()
    date, day_dir = parse_date(args.date)
    log_root = find_log_root(export_root)
    areas = tuple(args.area or AREAS)
    for area in areas:
        report = generate_area(area, date, day_dir, log_root, export_root, output_root, not args.no_ai)
        print(f"READY report={report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
