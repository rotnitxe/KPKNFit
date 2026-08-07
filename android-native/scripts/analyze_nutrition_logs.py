#!/usr/bin/env python3
"""
Analiza los JSONL de NutriTelemetry (telemetría exclusiva de nutrición).

Uso:
    python scripts/analyze_nutrition_logs.py <archivo.jsonl | carpeta> [--top-errors N]

Entrada esperada: los archivos exportados desde
Ajustes > Datos y app > Telemetría de nutrición > Exportar,
o directamente filesDir/nutrition_telemetry/*.jsonl del dispositivo.

Solo usa la librería estándar. Salida: informe en texto plano por stdout.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from statistics import mean


def load_events(path: Path) -> list[dict]:
    files: list[Path]
    if path.is_dir():
        files = sorted(path.rglob("nt-*.jsonl"))
    elif path.is_file():
        files = [path]
    else:
        sys.exit(f"[error] No existe: {path}")
    events: list[dict] = []
    for file in files:
        with file.open("r", encoding="utf-8-sig", errors="replace") as handle:
            for line_number, line in enumerate(handle, start=1):
                line = line.strip()
                if not line:
                    continue
                try:
                    events.append(json.loads(line))
                except json.JSONDecodeError:
                    print(f"[warn] línea inválida {file.name}:{line_number}", file=sys.stderr)
    return events


def percentile(sorted_values: list[float], pct: float) -> float:
    if not sorted_values:
        return 0.0
    index = min(len(sorted_values) - 1, int(round((pct / 100.0) * (len(sorted_values) - 1))))
    return sorted_values[index]


def ms_stats(values: list[float]) -> str:
    if not values:
        return "n=0"
    ordered = sorted(values)
    return (
        f"n={len(values)} avg={mean(ordered):.0f}ms "
        f"p50={percentile(ordered, 50):.0f}ms p95={percentile(ordered, 95):.0f}ms "
        f"max={ordered[-1]:.0f}ms"
    )


def fmt_counter(counter: Counter, limit: int = 10) -> list[str]:
    rows = []
    for key, count in counter.most_common(limit):
        rows.append(f"    {count:>5}  {key}")
    return rows or ["    (sin datos)"]


def main() -> None:
    parser = argparse.ArgumentParser(description="Analiza JSONL de NutriTelemetry")
    parser.add_argument("path", type=Path, help="Archivo .jsonl o carpeta con JSONL")
    parser.add_argument("--top-errors", type=int, default=6, help="Top N de tipos de error")
    args = parser.parse_args()

    events = load_events(args.path)
    if not events:
        sys.exit("[error] No se encontraron eventos JSONL")

    by_name = Counter(e.get("event", "?") for e in events)
    sessions = {e.get("sessionId") for e in events if e.get("sessionId")}
    traces_start = {e.get("traceId") for e in events if e.get("event") == "analysis_start" and e.get("traceId")}
    traces_end = {e.get("traceId") for e in events if e.get("event") == "analysis_end" and e.get("traceId")}
    orphan_traces = traces_start - traces_end

    print("=" * 72)
    print("NUTRITELEMETRY — INFORME")
    print("=" * 72)
    print(f"Eventos totales: {len(events)}")
    print(f"Sesiones: {len(sessions)}")
    orphan_pct = (len(orphan_traces) / len(traces_start) * 100.0) if traces_start else 0.0
    print(f"Trazas de análisis: {len(traces_start)} (sin cierre: {len(orphan_traces)}, {orphan_pct:.1f}%)")
    print()
    print("Eventos por tipo:")
    print("\n".join(fmt_counter(by_name, limit=30)))

    # --- Resultados de análisis (calidad) ------------------------------------
    ends = [e for e in events if e.get("event") == "analysis_end"]
    print()
    print("-" * 72)
    print("ANÁLISIS — RESULTADOS (calidad)")
    print("-" * 72)
    print("Outcomes:")
    print("\n".join(fmt_counter(Counter(str(e.get("outcome")) for e in ends))))
    engines = Counter(str(e.get("engine") or "<template/cache>") for e in ends)
    print("Motores reportados:")
    print("\n".join(fmt_counter(engines)))
    durations = [float(e.get("durationMs", 0)) for e in ends]
    print(f"Duración total por análisis: {ms_stats(durations)}")
    resolved_pairs = [
        (float(e.get("resolved") or 0), float(e.get("tags") or 0))
        for e in ends
        if e.get("tags")
    ]
    if resolved_pairs:
        ratio = mean([r / t for r, t in resolved_pairs if t > 0]) * 100.0
        print(f"% de alimentos resueltos (media de análisis con tags): {ratio:.1f}%")
    ai_inferred = [int(e.get("aiInferred") or 0) for e in ends]
    if ai_inferred:
        print(f"Alimentos inferidos por IA por análisis: avg={mean(ai_inferred):.2f} max={max(ai_inferred)}")
    slowest = sorted(ends, key=lambda e: float(e.get("durationMs", 0)), reverse=True)[:5]
    if slowest:
        print("Análisis más lentos (traceId, outcome, engine, duración):")
        for e in slowest:
            print(f"    {e.get('traceId')}  {e.get('outcome')}  {e.get('engine')}  {e.get('durationMs')}ms")

    # --- Etapas (cuellos de botella) -----------------------------------------
    stages = [e for e in events if e.get("event") == "analysis_stage"]
    print()
    print("-" * 72)
    print("ETAPAS — CUELLOS DE BOTELLA")
    print("-" * 72)
    stage_groups: dict[str, list[float]] = defaultdict(list)
    stage_failures: Counter = Counter()
    for e in stages:
        name = str(e.get("stage"))
        if e.get("ok"):
            stage_groups[name].append(float(e.get("durationMs", 0)))
        else:
            stage_failures[f"{name} ({e.get('errorType')})"] += 1
    for name in sorted(stage_groups, key=lambda n: max(stage_groups[n]), reverse=True):
        print(f"  {name:<18} {ms_stats(stage_groups[name])}")
    if stage_failures:
        print("Etapas fallidas:")
        print("\n".join(fmt_counter(stage_failures)))

    # --- API externa ---------------------------------------------------------
    api = [e for e in events if e.get("event") == "api_call"]
    if api:
        print()
        print("-" * 72)
        print("API EXTERNA (DeepSeek)")
        print("-" * 72)
        ok_count = sum(1 for e in api if e.get("ok"))
        print(f"Llamadas: {len(api)} — ok: {ok_count} ({ok_count / len(api) * 100.0:.1f}%)")
        print(f"Latencia: {ms_stats([float(e.get('durationMs', 0)) for e in api])}")
        errors = Counter(str(e.get("errorType")) for e in api if not e.get("ok"))
        if errors:
            print("Errores:")
            print("\n".join(fmt_counter(errors)))
        codes = Counter(str(e.get("httpCode")) for e in api if e.get("httpCode") is not None)
        if codes:
            print("HTTP:")
            print("\n".join(fmt_counter(codes)))

    # --- Fallos y degradación ------------------------------------------------
    pipeline_failed = [e for e in events if e.get("event") == "analysis_pipeline_failed"]
    salvage_failed = [e for e in events if e.get("event") == "analysis_salvage_failed"]
    rejected = [e for e in events if e.get("event") == "save_rejected"]
    saved = [e for e in events if e.get("event") == "save_log"]
    salvaged = sum(1 for e in ends if e.get("outcome") == "salvaged")
    failed = sum(1 for e in ends if e.get("outcome") == "failed")
    print()
    print("-" * 72)
    print("FALLOS Y DEGRADACIÓN")
    print("-" * 72)
    print(f"Análisis caídos al fallback: {len(pipeline_failed)} | salvados: {salvaged} | "
          f"fallidos: {failed} | salvavidas fallido: {len(salvage_failed)}")
    if pipeline_failed:
        print("Top errores del pipeline:")
        print("\n".join(fmt_counter(Counter(str(e.get("errorType")) for e in pipeline_failed), args.top_errors)))
    if rejected or saved:
        print(f"Guardados: {len(saved)} | rechazados: {len(rejected)}")
        if rejected:
            print("Razones de rechazo:")
            print("\n".join(fmt_counter(Counter(str(e.get("reason")) for e in rejected))))

    # --- Crashes y sesiones anteriores ---------------------------------------
    app_crash = [e for e in events if e.get("event") == "app_crash"]
    prev_crash = [e for e in events if e.get("event") == "previous_session_crash"]
    prev_exit = [e for e in events if e.get("event") == "previous_session_exit"]
    coroutine_crash = [e for e in events if e.get("event") == "analysis_coroutine_crash"]
    print()
    print("-" * 72)
    print("CRASHES")
    print("-" * 72)
    print(f"app_crash: {len(app_crash)} | previous_session_crash: {len(prev_crash)} | "
          f"previous_session_exit: {len(prev_exit)} | analysis_coroutine_crash: {len(coroutine_crash)}")
    if app_crash:
        print("Tipos de crash:")
        print("\n".join(fmt_counter(Counter(str(e.get("errorType")) for e in app_crash), args.top_errors)))
        last = app_crash[-1]
        print("Último crash:")
        print(f"    thread={last.get('thread')} error={last.get('errorType')}")
        print(f"    message={last.get('message')}")
        print(f"    inFlight={last.get('inFlight')}")
    if prev_exit:
        print("Última etapa viva al morir la sesión previa:")
        print("\n".join(fmt_counter(Counter(str(e.get("lastStage")) for e in prev_exit))))

    print()
    print("(Todos los datos proceden de archivos locales del dispositivo; sin texto de comidas.)")


if __name__ == "__main__":
    main()

