# Diagnósticos JSONL v2

La fuente única de eventos Android es `KpknDiagnosticLogger`. Los productores
emiten eventos por namespace, pero el bus los normaliza a estas áreas:

`voice`, `workout`, `nutrition`, `performance`, `auge` y `reports`.

La copia local es autoritativa:

```text
filesDir/kpkn_logs/<area>/<yyyyMMdd>/*.jsonl
filesDir/kpkn_logs/reports/report-<reportId>.jsonl
filesDir/kpkn_logs/reports/report-<reportId>.md
```

El espejo SAF se configura únicamente en Ajustes > Diagnósticos y usa:

```text
KPKN/logs/<area>/<yyyyMMdd>/…
KPKN/reports/…
```

Cada línea lleva `schemaVersion=2`, `eventId`, `timestamp`, `elapsedMs`,
`area`, `subsystem`, `event`, `screen`, `sessionId`, `traceId` y `process`.
Los textos se sanean y se truncan; los números no finitos se convierten en
`null`. La retención es de 30 días/50 MB por área y la rotación de un archivo
se produce al superar 1 MB.

## Validación y reportes diarios

Desde la raíz del repositorio:

```powershell
py scripts/validate_jsonl_schema.py <export-root>/logs
py scripts/generate_daily_reports.py --input-root <export-root> --date 2026-08-08 --no-ai
py scripts/validate_report_refs.py <export-root>/reports/daily/2026-08-08/voice.md --root <export-root>
```

`generate_daily_reports.py` conserva las referencias físicas, preagrega solo
ráfagas de `frame_jank`/`trace_metric` y nunca agrupa errores, crashes ni
comentarios. La IA es opcional y se configura con `KPKN_AI_PROVIDER`,
`KPKN_AI_API_KEY`, `KPKN_AI_ENDPOINT` y `KPKN_AI_MODEL`; sin clave se entrega un
reporte determinista marcado como no enriquecido.

## Exportación manual

El selector de carpeta y el ZIP de voz viven en Ajustes > Diagnósticos. El flujo
de entrenamiento no abre selectores del sistema al terminar, tenga o no tenga
carpeta SAF configurada.
