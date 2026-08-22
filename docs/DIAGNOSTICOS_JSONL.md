# Diagnósticos JSONL v2

La fuente única de eventos Android es `KpknDiagnosticLogger`. Los productores
emiten eventos por namespace, pero el bus los normaliza siempre a estas cuatro
áreas canónicas:

`workout` (sesión en vivo sin voz), `voice` (sesión en vivo con voz),
`nutrition` (parser/descripciones) y `app` (resto de la aplicación).

La copia local es autoritativa:

```text
filesDir/kpkn_logs/<area>/<yyyyMMdd>/*.jsonl
```

El espejo SAF se configura una sola vez en Ajustes > Diagnósticos y usa:

```text
KPKN/logs/workout/<yyyyMMdd>/…
KPKN/logs/voice/<yyyyMMdd>/…
KPKN/logs/nutrition/<yyyyMMdd>/…
KPKN/logs/app/<yyyyMMdd>/…
```

Cada línea lleva `schemaVersion=2`, `eventId`, `sequence`, `timestamp`,
`elapsedMs`, `area`, `subsystem`, `event`, `screen`, `sessionId`, `traceId` y
`process`. La cola se vacía en lotes cada 250 ms (o 32 eventos); crashes y
cierres solicitan `fsync`. La aplicación crea las cuatro carpetas al iniciar,
aunque todavía no haya eventos de una de ellas, por lo que la ausencia de
actividad no se confunde con un escritor roto. Al actualizar desde builds
antiguos, `performance`, `auge` y `reports` se copian una vez dentro de `app`
como evidencia histórica; no reciben eventos nuevos ni se crean en el espejo
SAF.
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

Los informes diarios externos son opcionales y no forman parte de la escritura
de la aplicación. La captura Android no llama a servicios de IA ni depende de
DeepSeek.

## Exportación manual

El selector de carpeta, la sincronización y el ZIP de JSONL viven en Ajustes >
Diagnósticos. El flujo de entrenamiento no abre selectores del sistema al
terminar, tenga o no tenga carpeta SAF configurada. Se eliminó el gesto de
long-press con dos dedos y el antiguo flujo manual de “informar”; solo queda la
exportación explícita desde Ajustes.
