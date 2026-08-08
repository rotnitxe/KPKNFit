# Auditoria Program Detail — Indice de planes

Esta carpeta consolida la auditoria profunda del sistema de programas (2026-08-08) y los planes de ejecucion derivados. Infra `/team` estuvo bloqueada por limite Clinepass 5h, asi que el coordinador ejecuto la auditoria en modo directo leyendo los motores y la UI en profundidad.

## Documentos

| Archivo | Contenido | Hallazgos que cubre |
|---|---|---|
| `00-auditoria-program-detail.md` | Informe central: resumen ejecutivo, tabla 15 hallazgos (P1/P2/MEJORA), detalle por hallazgo con evidencia `archivo:linea`, cobertura tests y gaps, preguntas abiertas | PD-01 a PD-15 |
| `01-plan-temporal-loops-calendario.md` | Plan M (3-5 dias): integridad temporal — loops, calendarizacion, fechas clave, modelo simple/avanzado | PD-01, PD-06, PD-07, PD-15 |
| `02-plan-splits-protocolos-plantillas.md` | Plan L (5-8 dias): splits, protocolos y plantillas — de esqueleto a plan completo | PD-04, PD-05, PD-08, PD-13 |
| `03-plan-ui-persistencia-competicion.md` | Plan M-L (4-7 dias, 2 fases): UI Program Detail, persistencia JSON y competiciones | PD-02, PD-03, PD-10, PD-11, PD-12, PD-14 |

## Orden recomendado de ejecucion

1. **Plan 01** — estabiliza el tiempo (loops + semana actual + `schedulePlan` SSoT). Sin esto, los otros planes construyen sobre base inconsistente.
2. **Plan 02** — potencia generacion de contenido (splits/protocolos/plantillas). Depende de 01 para que `prefillEmptyWeeks` y `applyProtocol` respeten calendario/loops.
3. **Plan 03 Fase A** — descompone `MacrocycleEditor` y cierra flujo competicion (PD-02/PD-03). Puede ir en paralelo a 02 si hay 2 agentes.
4. **Plan 03 Fase B** — blindaje persistencia + recordatorios boot. Al final, con programas grandes ya generados por 02.

## Rutas absolutas para el agente ejecutor

```
C:\Users\valen\Documents\KPKNFit\docs\audits\2026-08-program-detail\00-auditoria-program-detail.md
C:\Users\valen\Documents\KPKNFit\docs\audits\2026-08-program-detail\01-plan-temporal-loops-calendario.md
C:\Users\valen\Documents\KPKNFit\docs\audits\2026-08-program-detail\02-plan-splits-protocolos-plantillas.md
C:\Users\valen\Documents\KPKNFit\docs\audits\2026-08-program-detail\03-plan-ui-persistencia-competicion.md
```

## Validacion global (tras los 3 planes)

```powershell
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.LoopEngineTest' --tests '*.ProgramCalendarEngineTest' --tests '*.ProgramProgressEngineTest' --tests '*.ProgramDetailViewModelTest' --tests '*.ProtocolLibraryTest' --tests '*.SplitApplicationEngineTest' --tests '*.SessionTemplateCatalogTest' --tests '*.CompetitionSessionSyncTest'"
python scripts/catalog_v2_gate.py
```

## WIP relevante en working tree

`android-native/app/src/main/assets/exercise_catalog_v2.json` y `domain/exercises/catalogv2/*` estan modificados sin commit. La verdad-terreno para validar `exerciseDbId`/`configurationId` es ese asset, no `catalog/exercises/v2/source/` antiguo. Mencionado en Plan 02 T4.

## Hallazgos criticos (P1) — resumen

- **PD-01** Loops triplicado (`loops`+`loopState`+`loopOccurrences`) sin `syncOccurrences` en todos los caminos.
- **PD-02** Sesion competicion desde semana prometida en `strings.xml:431` sin CTA real.
- **PD-03** `ProtocolsView` fantasma (comentario en `ProgramProtocolEngine.kt:25-27` sin archivo).
- **PD-04** Protocolos 3 ej/sesion fijos, sin diferenciacion por dia del split.
- **PD-05** `defaultSplit` protocolos sin verificacion centralizada, fallback silencioso `ul_x4`.
- **PD-06** Loops no modelados en `CalendarBreak`/programa calendarizado.
