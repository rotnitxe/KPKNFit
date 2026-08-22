#!/usr/bin/env python3
"""Analizador de logs de voz de KPKN Fit.

Lee los JSONL de `KPKN/logs/voice/` (espejo SAF del teléfono) o de
`filesDir/kpkn_logs/voice/` y resume métricas por sesión para detectar
regresiones entre builds y medir la calidad real del sistema de voz.

Uso:
    python scripts/analyze_voice_logs.py [dir1 dir2 ...]

Si no se pasa directorio, busca en ./KPKN/voice y en ./android-native/app/files.
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path


def iter_events(paths: list[Path]):
    for p in paths:
        if p.is_dir():
        files = sorted(p.rglob("*.jsonl"))
        elif p.is_file():
            files = [p]
        else:
            files = []
        for f in files:
            try:
                for line in f.read_text(encoding="utf-8").splitlines():
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        obj = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    obj["_file"] = f.name
                    yield obj
            except (OSError, UnicodeDecodeError):
                pass


TRACKED = (
    "diagnostic_started",
    "diagnostic_closed",
    "asr_final",
    "asr_partial_fallback",
    "command_parsed",
    "confirmation_input_received",
    "set_persistence_started",
    "set_persistence_succeeded",
    "set_persistence_rejected",
    "set_persistence_failed",
    "set_persistence_exception",
    "confirmation_timeout",
    "guided_clarification_resolved",
    "voice_recovery_started",
    "voice_recovery_success",
    "voice_recovery_failed_attempt",
    "voice_recovery_gave_up",
    "voice_stall_detected",
    "voice_ipc_died",
    "voice_fgs_start_failed",
    "voice_capture_health",
    "voice_low_signal_alert",
    "vosk_fragment",
    "vosk_noise_discarded",
)


def analyze(paths: list[Path]) -> dict:
    events = list(iter_events(paths))
    totals = Counter()
    by_session: dict[str, Counter] = defaultdict(Counter)

    for ev in events:
        name = ev.get("event", "")
        if name not in TRACKED:
            continue
        totals[name] += 1
        key = ev.get("traceId") or ev.get("_file")
        by_session[str(key)][name] += 1

    # Disponibilidad: rango temporal de la sesión más larga (por traceId).
    session_time = {}
    for ev in events:
        key = ev.get("traceId") or ev.get("_file")
        ts = ev.get("elapsedMs")
        if isinstance(ts, (int, float)):
            a, b = session_time.get(str(key), (ts, ts))
            session_time[str(key)] = (min(a, ts), max(b, ts))

    health_rms = []
    for ev in events:
        if ev.get("event") == "voice_capture_health" and ev.get("rmsAvgDb") is not None:
            v = ev.get("rmsAvgDb")
            if isinstance(v, (int, float)):
                health_rms.append(float(v))

    commands = totals.get("command_parsed", 0)
    persisted = totals.get("set_persistence_succeeded", 0)
    finals = totals.get("asr_final", 0)
    # Los finales de confirmación ("sí/no") y de reporte/feedback no deberían parsear
    # como comandos: excluirlos del denominador para no subestimar la tasa.
    confirmation_inputs = totals.get("confirmation_input_received", 0)
    parseable_finals = max(0, finals - confirmation_inputs)

    report = {
        "archivos": len({e["_file"] for e in events}),
        "eventos_totales": len(events),
        "finales_asr": finals,
        "finales_confirmacion_si_no": confirmation_inputs,
        "comandos_parseados": commands,
        "series_persistidas": persisted,
        "tasa_exito_parseo_porcentaje": round(100.0 * commands / parseable_finals, 1) if parseable_finals else None,
        "persistencia_vs_comandos_porcentaje": round(100.0 * persisted / commands, 1) if commands else None,
        "rearmados_confirmacion": totals.get("confirmation_timeout", 0),
        "aclaraciones_guiadas": totals.get("guided_clarification_resolved", 0),
        "recuperaciones_fenix_iniciadas": totals.get("voice_recovery_started", 0),
        "recuperaciones_fenix_exitosas": totals.get("voice_recovery_success", 0),
        "recuperaciones_fenix_rendidas": totals.get("voice_recovery_gave_up", 0),
        "cuelgues_detectados": totals.get("voice_stall_detected", 0),
        "muertes_proceso_voice": totals.get("voice_ipc_died", 0),
        "fallos_fgs_start": totals.get("voice_fgs_start_failed", 0),
        "aviso_senal_baja": totals.get("voice_low_signal_alert", 0),
        "fragmentos_vosk": totals.get("vosk_fragment", 0),
        "ruido_descartado": totals.get("vosk_noise_discarded", 0),
        "rms_promedio_db": round(sum(health_rms) / len(health_rms), 1) if health_rms else None,
        "sesiones_con_datos": len(by_session),
        "duracion_sesion_max_ms": max((b - a for a, b in session_time.values()), default=None),
        "recuperaciones_por_sesion": {
            k: v.get("voice_recovery_success", 0) for k, v in by_session.items()
        },
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dirs", nargs="*", help="Directorios/archivos con JSONL de voz")
    args = parser.parse_args()

    paths: list[Path] = []
    for d in args.dirs:
        paths.append(Path(d))
    if not paths:
        candidates = [
            Path("KPKN/logs/voice"),
            Path("android-native/app/files/kpkn_logs/voice"),
        ]
        paths = [p for p in candidates if p.exists()]

    if not any(p.exists() for p in paths):
        print("No se encontraron logs de voz. Pasa el directorio como argumento.", file=sys.stderr)
        return 1

    report = analyze(paths)
    for key, value in report.items():
        print(f"{key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
