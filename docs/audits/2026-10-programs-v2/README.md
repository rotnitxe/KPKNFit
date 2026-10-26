# Programas y periodización — ola vigente

Aplicación del plan del 22-oct sobre la base integrada `1d399c1fc`, Room 35. Orden del usuario del 25-oct: ignorar DeepSeek y adaptar todo apartado a la retirada de AUGE/RINGS. Rama `codex/programs-periodization-current`, sin push ni integración en master.

La [auditoría original](AUDITORIA_ORIGINAL.md) conserva la Parte I, hallazgos A-xx y decisiones D1–D20 como documento histórico. Sus inventarios, números de línea, ramas, versiones y motores no describen el código vigente. La [adaptación autorizada](../../programas/PLAN_ACTUAL_20261025.md) y los [recibos de ejecución](EJECUCION_ACTUAL.md) describen los contratos y resultados actuales.

## Tarea, commit y prueba

La tabla incluye sólo commits existentes. Cada fila remite al recibo de su ola; un commit no acredita por sí solo compilación, instalación o QA. Las pruebas verdes por clase de corridas diferentes se identifican como tales en el recibo.

| Commit | Cambio y tarea | Evidencia |
|---|---|---|
| 33affef65 | feat(training): motor puro ProgramScope de alcance visible [T-01] | [Recibo](EJECUCION_ACTUAL.md) |
| 6e8ef0571 | feat(programs): retirar el vocabulario simple o avanzado de los textos [T-02] | [Recibo](EJECUCION_ACTUAL.md) |
| 888340da7 | feat(programs): etiqueta de alcance en fichas y cabecera [T-03] | [Recibo](EJECUCION_ACTUAL.md) |
| 0fa5cd612 | feat(programs): mostrar el alcance en Program Detail y la biblioteca [T-04] | [Recibo](EJECUCION_ACTUAL.md) |
| bde049549 | feat(protocols): Cube Method con rotación de métodos por semana [T-11] | [Recibo](EJECUCION_ACTUAL.md) |
| 6e38bcfff | feat(protocols): RP-style con rampa de series y Torso B de tirón [T-12] | [Recibo](EJECUCION_ACTUAL.md) |
| 3bcf5203f | feat(protocols): RTS-style con top sets por RPE en los tres levantamientos [T-13] | [Recibo](EJECUCION_ACTUAL.md) |
| 0f3c8e6b4 | feat(protocols): taper de Calgary, Smolov Jr legacy y accesorios de principiante [T-14] | [Recibo](EJECUCION_ACTUAL.md) |
| 28d9bdff0 | docs(programs): estado de la ola y relevo para continuar [T-62] | [Recibo](EJECUCION_ACTUAL.md) |
| 5b7117f9c | feat(programs): informe de progreso y decisiones del programa [T-50] | [Recibo](EJECUCION_ACTUAL.md) |
| 10e83f7a8 | fix(protocols): retirar exencion de comprobacion eliminada [T-14] | [Recibo](EJECUCION_ACTUAL.md) |
| 6a8ca74ec | fix(programs): limitar el informe a decisiones vigentes [T-50] | [Recibo](EJECUCION_ACTUAL.md) |
| c5a8af9e8 | feat(programs): exponer progreso de la ejecucion en el ViewModel [T-51] | [Recibo](EJECUCION_ACTUAL.md) |
| 99cfda6aa | test(programs): alinear la ficha con el catalogo vigente [T-04] | [Recibo](EJECUCION_ACTUAL.md) |
| 5759d6c98 | feat(programs): mostrar progreso y decisiones con texto adaptable [T-52] | [Recibo](EJECUCION_ACTUAL.md) |
| c74897ed9 | docs(programs): especificar metricas del detalle actual [T-53] | [Recibo](EJECUCION_ACTUAL.md) |
| d6336dce4 | feat(training): proponer TM desde top sets RPE registrados [T-15] | [Recibo](EJECUCION_ACTUAL.md) |
| 1287cac47 | docs(programs): inventariar fidelidad y consumidores actuales [T-16] | [Recibo](EJECUCION_ACTUAL.md) |
| 4d088a16c | feat(programs): add advisory weekly volume reference (T17) | [Recibo](EJECUCION_ACTUAL.md) |
| 9f6823266 | feat(programs): preserve exact protocol when fitting a meet (T18) | [Recibo](EJECUCION_ACTUAL.md) |
| 345621956 | feat(programs): prescribe the native RIR wave [T-20] | [Recibo](EJECUCION_ACTUAL.md) |
| 4b4e942ad | feat(programs): retain run-scoped weekly answers [T-21] | [Recibo](EJECUCION_ACTUAL.md) |
| 17ae0cac7 | feat(programs): propose bounded weekly accessory changes [T-22] | [Recibo](EJECUCION_ACTUAL.md) |
| 4cf700de3 | feat(programs): scope weekly review to the active occurrence [T-23] | [Recibo](EJECUCION_ACTUAL.md) |
| ae812c970 | feat(programs): describe recorded training evidence [T-90] | [Recibo](EJECUCION_ACTUAL.md) |
| ed4f12742 | feat(programs): propose explicit planning ranges from weekly feedback [T-91] | [Recibo](EJECUCION_ACTUAL.md) |
| 75e8cf76d | feat(programs): consume chosen bounds with exact feedback identity [T-92] | [Recibo](EJECUCION_ACTUAL.md) |
| 0d11890b9 | feat(programs): summarize answers from closed cycles [T-94] | [Recibo](EJECUCION_ACTUAL.md) |
| d365f0d06 | fix(parity): preserve the weekly review preference in iOS JSON [T-21] | [Recibo](EJECUCION_ACTUAL.md) |
| 69be29467 | [T-50] Limit progress metrics to the current run and cycle | [Recibo](EJECUCION_ACTUAL.md) |
| 446ea9f13 | feat(programs): Confirm weekly accessory proposals explicitly [T-24] | [Recibo](EJECUCION_ACTUAL.md) |
| 1aa325575 | feat(programs): Show optional sixth-cycle deload notice [T-25] | [Recibo](EJECUCION_ACTUAL.md) |
| 33698e7fa | feat(programs): Review volume evidence and confirm planning ranges [T-93] | [Recibo](EJECUCION_ACTUAL.md) |
| 1b18558b9 | feat(programs): Persist factual block reviews and continuation choices [T-30] | [Recibo](EJECUCION_ACTUAL.md) |
| 295a8d0a9 | feat(programs): Review block records without recovery diagnoses [T-31] | [Recibo](EJECUCION_ACTUAL.md) |
| ff80f70e3 | feat(programs): Apply confirmed block adjustments with session guards [T-32] | [Recibo](EJECUCION_ACTUAL.md) |
| ede7d54c3 | feat(programs): Record block closure and require adjustment confirmation [T-33] | [Recibo](EJECUCION_ACTUAL.md) |
| 57cd0eb9c | fix(programs): Offer continuation after the final 1RM gate [T-33] | [Recibo](EJECUCION_ACTUAL.md) |
| 49fe43c46 | feat(programs): Define main-lift variants by phase [T-70] | [Recibo](EJECUCION_ACTUAL.md) |
| c448d9d54 | feat(programs): Rotate feasible variants without transferring load references [T-71] | [Recibo](EJECUCION_ACTUAL.md) |
| 77da999b8 | feat(programs): Differentiate the long template blocks in recipe v3 [T-72] | [Recibo](EJECUCION_ACTUAL.md) |
| 7201e3044 | feat(programs): Describe prescribed muscle sets and explicit effort targets [T-82] | [Recibo](EJECUCION_ACTUAL.md) |
| b2b622062 | feat(programs): Apply explicit exercise swaps without sharing load references [T-73] | [Recibo](EJECUCION_ACTUAL.md) |
| 63f40a5d3 | feat(programs): Prepare an independent continuation before activation [T-34] | [Recibo](EJECUCION_ACTUAL.md) |
| 4e62585a3 | feat(programs): Review exercise swaps with explicit scope and equipment [T-73] | [Recibo](EJECUCION_ACTUAL.md) |
| 4783a2ae9 | feat(programs): Explain the next prescribed week in a reviewable sheet [T-83] | [Recibo](EJECUCION_ACTUAL.md) |
| d7b8e33d7 | feat(programs): Confirm durable block reviews and continuation before navigation [T-34] | [Recibo](EJECUCION_ACTUAL.md) |
| d7d2aa7b8 | docs(programs): Record confirmed block, swap and prescribed-load contracts [T-35] [T-74] [T-84] | [Recibo](EJECUCION_ACTUAL.md) |
| ba690ce0f | feat(programs): Resolve missing records through explicit run-scoped choices [T-100] [T-101] | [Recibo](EJECUCION_ACTUAL.md) |
| 435c33be2 | feat(programs): Describe gaps between valid training records [T-102] | [Recibo](EJECUCION_ACTUAL.md) |
| b2338e58f | feat(competitions): Preserve editable attempts and confirmed application metadata [T-110] | [Recibo](EJECUCION_ACTUAL.md) |
| 46e56e2c2 | feat(competitions): Plan editable attempts and review valid meet results [T-111] [T-112] [T-113] [T-114] | [Recibo](EJECUCION_ACTUAL.md) |
| dedc4103c | feat(programs): Explain season context without altering nutrition accounting [T-120] | [Recibo](EJECUCION_ACTUAL.md) |
| 6ae77bfd1 | feat(onboarding): Suggest maintenance only for an unchosen strength-program direction [T-122] | [Recibo](EJECUCION_ACTUAL.md) |
| c3a7e254f | feat(programs): Bound confirmed weekly proposals by declared nutrition context [T-123] | [Recibo](EJECUCION_ACTUAL.md) |
| b90f0002a | feat(home): Distinguish meet day from past-event registration [T-116] | [Recibo](EJECUCION_ACTUAL.md) |
| 3ce3dab5c | fix(parity): Preserve optional competition planning metadata in iOS JSON [T-110] | [Recibo](EJECUCION_ACTUAL.md) |
| ee08fa19e | [T-94] Preserve declared calibration notes in iOS settings transport | [Recibo](EJECUCION_ACTUAL.md) |
| 6753c7175 | [T-117] Document current competition planning and confirmed results | [Recibo](EJECUCION_ACTUAL.md) |
| 923c57c90 | [T-124] Document independent season context and guarded weekly proposals | [Recibo](EJECUCION_ACTUAL.md) |
| 919ee60d6 | [T-61] Map current program engines and additive JSON contracts | [Recibo](EJECUCION_ACTUAL.md) |
| 9eda33090 | [T-103][T-115][T-121] Integrate reviewed schedule competition and season flows | [Recibo](EJECUCION_ACTUAL.md) |
| 7cdd02d75 | [T-116] Show meet day separately from post-event registration on Home | [Recibo](EJECUCION_ACTUAL.md) |
| 2bd829db5 | [T-43] Keep exact protocols independent from the meet date in setup | [Recibo](EJECUCION_ACTUAL.md) |
| 60c5a55b7 | [T-40] Split tailored and catalog offers while preserving selected families | [Recibo](EJECUCION_ACTUAL.md) |
| cdcf441ae | [T-41] Show tailored programs first with expandable catalog and actual scope | [Recibo](EJECUCION_ACTUAL.md) |
| 3c3ea6d29 | [T-42] Open tailored training setup from empty and populated libraries | [Recibo](EJECUCION_ACTUAL.md) |
| 944a3b20d | [T-112] Reject invalid weight projections without inventing a zero weight | [Recibo](EJECUCION_ACTUAL.md) |
| 58ec7525d | [T-41] Hide empty tailored sections when only catalog plans fit | Cierre: 20/20 Compose; 3074 globales |
| 36ae90fc7 | [T-11][T-14][T-20][T-33] Align regression fixtures with current program contracts | Cierre: 286 clases/3074 tests verdes |

## Competición

P11 está implementada, con pruebas puras y Android D verdes por clase. [Contrato de competición](../../programas/COMPETICION_ACTUAL.md): intentos editables, pesaje aritmético, calentamientos sobre la apertura, enlace a la ficha existente y confirmación de marcas con transacción pareada/rollback. La protección numérica pasó CompetitionPlanningTest6/6 en E76/76. APK compatible instalado en Z Flip; el flujo de evento no se ejercitó físicamente. Halterofilia y strongman conservan sus asistentes y no reciben porcentajes de powerlifting.

## Temporada

P12 está implementada, con pruebas puras y Android D verdes por clase. [Contrato de temporada](../../programas/TEMPORADA_ACTUAL.md): datos nutricionales activos confirmados, tendencia de observaciones originales, refresco por invalidación Room y revalidación del contexto dentro de la escritura semanal. La tarjeta no altera gasto, contabilidad ni check-ins nutricionales. Tarjeta de temporada observada en Z Flip; no se cambió nutrición ni se confirmó una propuesta semanal.

## Cierre y límites

Las olas A–E y T60–62 están aplicadas. Cierre Android conjunto: **286 clases, 3074 tests, cero fallos, errores u omitidos**, 159 fuentes sin deriva. La corrida incluye las siete familias y las nueve clases Android exigidas. [Recibo portable](VALIDACION_FINAL.json).

KPKN Dev se actualizó con `-r` en el Z Flip inalámbrico y se verificaron biblioteca, entrada/salida del asistente, alcance, progreso y temporada. Para conservar la Room36 ya instalada se construyó una copia aislada con master1d5ba0ce7 y seis archivos de compatibilidad; 181 tests adicionales aprobaron. La rama entregable sigue Room35 y sus esquemas publicados no cambian. No instalar su APK Room35 sobre ese teléfono. La copia QA no está publicada ni fusionada en la rama entregable.

Registros originales, sesión en curso, programas y ajustes conservan exactamente sus huellas. Quedó un borrador sin activar creado por esta QA: Salir lo conserva; no se encontró acceso de descarte en la UI y no se modificó la DB para borrarlo. Los emuladores quedaron cerrados. El asistente completo y el evento de competición tienen pruebas automatizadas; la QA física no acredita esos recorridos completos. iOS conserva transporte opcional y pruebas preparadas, sin ejecución Swift en Windows.

T104 sigue aplazada según el original. T80/81 y los antiguos hooks AUGE/RINGS no aplican; la semana prevista se describe sin reactivar motores retirados. T10 consume CAL5/CAL5b de la base; T19 no define una tarea en el original. Los contratos congelados y el WIP del checkout principal se preservan. Excepciones de agrupación de commits compartidos documentadas en EJECUCION_ACTUAL.md.

Los recibos históricos reflejan el estado de cada momento; el estado final prevalece en ESTADO.md y VALIDACION_FINAL.json. XML, logs, hashes y borradores históricos se conservan localmente en `C:\Users\valen\.codex\visualizations\2026\10\25\01a173b5-bf3a-7aa1-8c4d-39bc80e76c78\programs-evidence`; las capturas y copias privadas de DB permanecen fuera de Git. Sin push ni integración de esta ola en master. La coordinación de cola con «Auditar sesión de entreno en vivo» contó con autorización humana específica.

## Parte I del plan original — copia histórica

# Auditoría histórica del 22-oct-2026

Copia de la Parte I del plan original. Sus cifras, ramas, versiones de Room y referencias AUGE/RINGS describen aquel estado, no la base vigente. La orden del 25-oct y PLAN_ACTUAL_20261025.md prevalecen. No atribuye trabajo nuevo a DeepSeek.

## 0. Contexto

El usuario pide: (a) auditar TODOS los programas ofrecidos (volumen, ejercicios, nada «ridículo», calidad, protocolos externos al pie de la letra con sus herramientas); (b) un solo concepto «Programa» (duración y bloques como característica secundaria); (c) seguimiento real de programas por bloques y decidir si los bloques van pre-hechos o «al vuelo»; (d) periodizar los programas de una semana y ajustar el esfuerzo semana a semana; (e) nivel de las apps más avanzadas; (f) en el wizard, programa personalizado primero y catálogo como opción; (g) métricas de Program Detail sin redundar con la ola MacroFactor; (h) plan de ejecución.

Este documento es la **auditoría** (§1–§3) y el **plan** (§4–§8). No repite la curaduría de octubre (`docs/audits/2026-10-programs/`, Fase 1 cerrada el 05-oct): parte de su estado, ejecuta lo que dejó para Fase 2 y cubre lo que nadie miró: la periodización como sistema, el seguimiento por bloques, el concepto de programa, el wizard y Program Detail.

## 1. Qué hay hoy (diagnóstico con evidencia)

### 1.1 Oferta de programas

`PersonalizedPlanCatalog.entries()` tiene **85 entradas, 47 listadas** (`listed = true`): 27 protocolos publicados (`PROTOCOL_LIBRARY`, `ProtocolLibrary.kt:397-405`), 4 PHUL/PHAT de autor (original y adaptado), 4 perfiles propios v2, 2 nativos históricos (máquinas, peso corporal) y 10 plantillas. No listadas: 16 protocolos legacy ocultos (`HIDDEN_UNVERIFIED`), 6 nativos históricos (D2), PHUL/PHAT «versión anterior» y los **30 planes «a medida»** (`generated:*`: 10 perfiles × simple/bloques/periodizado), que solo ofrece el asistente.

Por alcance (lo que el usuario llama «una semana / varias semanas / bloques»):

| Alcance | Total | Listados | Ejemplos |
|---|---|---|---|
| Una semana que se repite | 19 | 3 | máquinas, peso corporal, `simple-1`; a medida simples (asistente) |
| Varias semanas sin bloques (ciclo que se repite) | 19 | 17 | Texas 3d/4d, 5/3/1 BBB/FSL, Madcow, nSuns, GZCLP, Westside, Smolov Jr, Cube (10 sem, un solo `blockIndex`), PHUL/PHAT, `simple-ab`, `simple-4` |
| Con 2 o más bloques | 47 | 27 | J&T 2.0, Rippler, UHF-9, Juggernaut, Sheiko, Smolov, Candito, Korte, Lilliebridge, Calgary, TSA-9, SBD-4, PPL-12, RP-style, RTS-style, SBS RTF, 4 perfiles propios (5+1), 7 plantillas `power-*/body-*/powerbuild-*`, a medida bloques/periodizado |

Protocolos con receta y su progresión declarada (inventario completo en el informe del explorador; resumen):
- **Con regla de TM/AMRAP**: Texas (TopSetPr), 5/3/1 (CycleIncrement + AMRAP_TM), Madcow (CycleIncrement), nSuns (AmrapDrivenTm), GZCLP (CycleIncrement + AMRAP), J&T (RepMaxAutoregulated, nunca se dispara), Juggernaut (CycleIncrement BLOCK + AMRAP), SBS RTF (RepTargetDrivenTm). **Lo arregla CAL5 de screens-v2** (§1.5).
- **Fijos por porcentaje (`None`/`WeeklyKg`)**: Sheiko, Smolov, Smolov Jr, Candito, Coan, Korte, Cube, Lilliebridge, Westside, Calgary, TSA-9, SBD-4, Rippler, UHF-9.
- **Por RPE con gancho `RPE_CAP` SIN consumidor**: RTS-style, RP-style, Calgary (INV1 §6.12): el esfuerzo registrado no cambia nada.
- **Por RIR sin TM**: PPL-12, RP-style, PHUL/PHAT (PHUL con doble progresión nativa; PHAT sin progresión por decisión DEV-r2-04).

Contrato de recetas (`RecipeContractPolicy`): C1 series por slot 2–8, C2 configuración duplicada, C3 serie vacía, C4 días declarados, C5 descarga obligatoria desde 8 semanas, C6 progresión con consumidor, C8 enlace suplementario, C9 técnica redundante (HARD); C10 semanas idénticas (SOFT); C7 (%TM vs %1RM) vive en `SessionCompositionPolicy`. **No existe ninguna regla de volumen por músculo**: `VolumeLandmarks` (MEV/MAV/MRV de 17 grupos, cifras tipo RP-2017) solo lo consumen el generador propio y el fitter (`VolumeBudgets`, `SimpleCyclePersonalizer`, `TemplateVolumeScaler`, `VolumeSoftBand`, `SessionCompositionPolicy`).

### 1.2 El concepto «simple / avanzado» en el código

`ProgramStructure { SIMPLE, COMPLEX }` (`Program.kt:219`), 48 usos en 40 archivos. Significado real: SIMPLE = un bloque que se repite (`SimpleProgramKind.CYCLIC`, con loops y eventos) o una pasada (`LINEAR`) o calendarizado; COMPLEX = varios bloques, lineal. Lo exponen al usuario: `strings.xml:39-40` («Programa simple / avanzado»), `:344-360` («Convertir a programa avanzado», «los loops viven solo en programas simples»), `ProgramStructureDialogs.kt:51`, `SplitApplySheet.kt:251`, `ClonerSaveWarmupSheets.kt:466`, nombres y descripciones de `PROGRAM_TEMPLATES` («Programa simple de N semanas»), mensajes de `Program.validateTemporalStructure()`. Es una distinción **de ejecución** (cíclico vs lineal), no de nivel: se puede mantener dentro y retirar del vocabulario y la jerarquía visible sin migración.

### 1.3 Periodización y seguimiento por bloques (informe «Runtime de bloques»)

**Producción: 100 % pre-hecha.** `PlanMaterializer.materialize` (`:139`, `:172-221`) materializa TODAS las semanas de la receta al activar; `materializationPending` significa «reconstruir con el perfil nuevo», no «aún sin generar». Los propios «a medida» salen de `RoutinePeriodizer.build()` (`periodization/RoutinePeriodizer.kt:96`) con una tabla de fases fija (`ruleOf()` `:187`: acumulación ×1,15 series, +2 reps, RIR +1 hasta 3; intensificación ×1,0; realización/pico ×0,8, −2 reps, RIR 1–2; descarga/taper ×0,55; test = 1 serie) sobre la semana única del generador; las fases las fija `PeriodLayout` (bloques 4–8 sem; periodizado 10/12/16; con fecha, hacia atrás desde la competición). Con competición, los planes de autor «aptos» se estiran con `MeetRecipeFitter` (repite acumulación con +2,5 % por repetición, tope +10 %, descarga cada 6) y pasan a clase ADAPTED.

**Qué se adapta con resultados (parámetros, nunca estructura):**
- `ProgramAutoregulationEngine.evaluate/apply` (`:148`, `:178-257`): AMRAP → `ADJUST_TM` (`amrapTmChange` `:1085`; sube solo nSuns/SBS, baja −2,5 % en el resto), `TopSetPr` (`:1129`), `RepMaxAutoregulated` (`:1148`), `PROMOTE_TM` (e1RM > TM×1,05 dos semanas, `:964`), AUGE → `SCALE_WEEK_*`/`INSERT_DELOAD`/`DELAY_PEAK` (`:938-985`). OFF no hace nada; PROPOSE deja `CONFIRM_AUTOREGULATION`; AUTO aplica. Solo re-materializa `nextWeekId` (`applyMutations` `:874-883`).
- Método del autor (`AuthoredProgressionEngine.applyAtCycleClose/applyAtBlockClose` `:236-318`): `CycleIncrement`/`WeeklyKg` siempre, con congelación del lift con AMRAP corto.
- Progresión nativa (`NativeWorkoutProgressionRuntime.observeCompletedWorkout` `:114-403`): doble progresión por identidad de ejercicio tras N exposiciones en el tope del rango con RIR ≥ objetivo (`NativeProfileSpec.kt:782-793`); siempre propuesta; **no considera AUGE**; la semana DELOAD no cuenta; `carryForwardToNextCycle` (`:714`).
- Descargas: de receta (`WeekRecipe.kind`), o reactivas: `BlockTransitionEngine.insertDeloadBlockAfter` (`:229-269`: bloque nuevo «Descarga (auto)», 60 % series, %RM ×0,85 ≤ 65, RPE ≤ 6) → `CONFIRM_DELOAD`; y la propuesta semanal `INSERT_DELOAD` (solo con `augeAutoDeload`, Rings v4 1.12a).

**Cierre de bloque (`BlockTransitionEngine.evaluate` `:108-222`):** comprueba sesiones completas (HOLD_INCOMPLETE), fatiga AUGE / estrés EMA ≥ 75 (INSERT_DELOAD), REALIZATION/PEAK sin competición (PROPOSE_1RM_TEST) y si no ADVANCE_NEXT_BLOCK. **No lee e1RM, series cumplidas ni resultado del bloque para ajustar nada.** `BlockProgressionEngine.applyProgression` solo rellena un bloque vacío con la fórmula de `PeriodizationEngine` (no lee logs). `Block.progressionScheme` es metadato para recetas.

**Fin del programa:** COMPLEX → `COMPLETED` (tras test opcional); SIMPLE lineal → `COMPLETED`; nativo / cíclico → `completeCycle` N+1 (`ProgramProgressEngine.kt:1197-1305`). **No existe «siguiente bloque según resultados» ni continuación** (`:1034-1055`, `:593-610`).

### 1.4 Wizard (informe «Wizard y Program Detail»)

- GOAL: «¿Cuál es tu objetivo?» con «Generales» (Fuerza y masa muscular, Fuerza y cardio, Funcional y saludable) y «Disciplinas» (Powerbuilding, Calistenia, Culturismo, Halterofilia, Armwrestling, Strongman, Powerlifting) (`EntrenoContracts.kt:36-45`, `GoalProfileMapping.kt`).
- PLAN: perfil general → **solo** las 3 formas del plan a medida (simple 1 sem / bloques 6 / periodizado 12) como tarjetas, la recomendada por experiencia centrada con «Para ti» (`EntrenoPlanStep.kt:222-232`, `EntrenoPlanModels.kt:155-174`). Disciplina → carrusel con los a medida primero y hasta 6 fichas mezcladas con protocolos/plantillas + «Ver más programas (N)» (`EntrenoPlanModels.kt:145,180-194`; ranking `SetupTrainingPlanner.kt:86-113`).
- No hay pregunta de duración/forma ni de «personalizado vs catálogo». `PROGRAM_SOURCE` («¿Cómo quieres tu programa?»: recomendar / importar / más adelante) solo sale con experiencia avanzada (`SetupStepGraph.kt:435-440`).
- La biblioteca no muestra los planes a medida (`generated:* listed=false`): quien no pasa por el asistente no los ve.

### 1.5 Program Detail (mismo informe)

Orden: `ProgramHeader` → `ProgramAboutModule` → pestañas SEMANA / MACROCICLO / VOLUMEN → `ProgramPhasesModule` (bloques y semanas, actual/hecha) o `ProgramWeekStrip` → `ProgramTransitionModule` → propuestas → `ProgramWeekHeader` + `DayView`; VOLUMEN = `ProgramAnalyticsEngine.analyze` + `VolumeView` (balance y cobertura, directo/indirecto, patrones, unilateralidad…). `PlanDetailsSummary` muestra procedencia, autor, material, series/semana, línea TM (sin press militar). `programProgressLabel` «Semana 3 de 12 · Acumulación» (`ProgramHeaderModel.kt:101-112`).

**`ProgramAnalyticsEngine` ya calcula adherencia (`AdherenceAnalytics`, `:438-484`) y e1RM por ejercicio con tendencia (`ExerciseProgressionAnalytics`, `:486-507`, Brzycki) y ninguna de las dos se pinta** (`ProgramAnalyticsCards.kt:25-83` no las lee). No hay tonelaje ni carga semanal plan vs real ni % de bloque ni historial de decisiones. La cobertura suma todas las semanas aunque diga «semanales» (lo corrige T2 de MFP).

### 1.6 Lo que ya está en marcha en otras olas (no duplicar; este plan se encadena)

| Ola / rama | Qué cubre de programas | Relación con este plan |
|---|---|---|
| Curaduría oct-03 (master) | Fase 1 cerrada. **Fase 2 pendiente**: etapas GZCLP (L-09), rotación Cube (L-13), rampa RP (L-25), RTS por top sets (L-26), Sheiko con tirón (L-15), Westside resistencia acomodada (L-14), accesorios power-12-3 (L-28), bloques diferenciados (L-29), ocultar PHUL/PHAT heredados (L-22/23: ya `listed=false`), parches C-05..07, onda RIR propios (D4 → DEV-r2-07). | **Paquete P1 ejecuta la Fase 2** (lo no cubierto por CAL5). |
| screens-v2 (`C:\kw\u11`) | CAL1 contrato AMRAP, CAL2 calibrador, **CAL5 reglas fieles de 5/3/1, nSuns, Juggernaut, GZCLP, J&T, Cube, Lilliebridge, Rippler, banderas AMRAP de `power-*`, test de 1RM por levantamientos del programa + OHP, vista previa por programa**; **CAL5b TM por ejercicio con origen e historial, visible en el detalle, rematerialización automática**; CAL6 APRE. Después: rediseño de Program Detail. | Nada de AMRAP/TM/test de 1RM aquí. P1 se limita a lo no-AMRAP. Program Detail: entregamos **motor + especificación + tarjeta mínima**; la UI final la hace ese rediseño. |
| MFP (`C:\kw\mfp`) | T1 material, T2 ventana/contabilidad de volumen (toca `ProgramAnalyticsEngine`, `VolumeView`, `ProgramDetailViewModel.muscleCdbsStatus`), T3 `StrengthModel` (e1RM con RIR), T4 objetivo automático (`NativeWorkoutProgressionRuntime`), T5a panel por ventana (enlace desde Program Detail), T5b progreso por ejercicio + récords, P2-Tb descarga nativa. | **No tocar** `NativeWorkoutProgressionRuntime`, `Calculations.kt`, `LoadSuggestionEngine`, `VolumeCalculator`, `VolumeView`; en `ProgramAnalyticsEngine` solo lectura. e1RM: usar `StrengthModel` si está fusionado; si no, `ExerciseProgressionAnalytics` tal cual (misma cifra que hoy). |
| Rings v4 (`feat/rings-v4`) | 1.12a `INSERT_DELOAD` solo con `augeAutoDeload`; AUGE Pro pendiente de decisiones. | La fatiga de bloque se lee por la API actual de AUGE (`TransitionContext`); no se modifica AUGE. |

### 1.7 Apps de referencia (investigación B1 y informe MacroFactor; sin copiar constantes)

- **JuggernautAI**: bloques con readiness previo y RPE de top/back-off que ajustan la sesión; check-in semanal recorta accesorios; fin de bloque con test u ola nueva. **RP Hypertrophy**: plantilla fija de ejercicios y **series autorreguladas semana a semana** (matriz agujetas × rendimiento: +2–3 / +1 / mantener / recuperación), RIR 4→1, descarga al romper el MRV o cada 4–6 semanas. **RTS / Evolve**: top set por RPE y back-offs por % de fatiga; deload reactivo; consejos de fin de bloque. **MacroFactor Workouts**: creación con periodizaciones, objetivo automático con inventario, descarga ×0,8; programas fijos (S1 «no tocar»: KPKN ya iguala). **Boostcamp / Sheiko / Lift Vault**: fidelidad + herramientas (TM, AMRAP, test), sin adaptar estructura.
- Lectura: las mejores apps **no generan bloques desde cero al vuelo**; usan estructuras de bloque pre-hechas y **autorregulan los parámetros** (series, RIR/RPE, carga, descarga) con señales de la semana anterior, y al cerrar un bloque deciden cómo entra el siguiente. KPKN tiene la estructura y los motores de parámetros a medias; le falta el **ciclo de decisión** (semana → ajuste; cierre de bloque → siguiente bloque; fin → continuación).

## 2. Respuestas a las preguntas

**2.1 ¿Cumple KPKN lo que significa entrenar por bloques?** A medias. Tiene bloques con objetivo (acumulación → intensificación → especificidad → pico → taper → test), descargas reales, TM/1RM, AMRAP y test de 1RM, y propone descarga por fatiga. Le falta la parte viva: cerrar el bloque con una evaluación (series cumplidas por músculo, Δe1RM por levantamiento, adherencia, fatiga), entrar al siguiente con esa evaluación (RIR de partida, factor de series, base de carga) y decidir qué viene tras el pico. Hoy un bloque es una lista de semanas que se recorre.

**2.2 ¿Pre-hechos o al vuelo?** **Híbrido, como las apps de referencia (D1).** Protocolos de autor: 100 % pre-hechos y fieles, con la autorregulación que su autor define (eso ES «al pie de la letra»). Propios y plantillas KPKN: estructura de bloques pre-hecha y visible (el usuario avanzado quiere ver su macrociclo) y **parámetros del bloque siguiente producidos al cerrar el anterior** (re-materialización con los ajustes). Generar bloques enteros desde cero no lo hace ninguna app de referencia y rompe la previsibilidad.

**2.3 Programas de una semana: cómo periodizarlos.** Todo programa de una semana es un **ciclo**. Los propios ya son ciclos de 6 semanas (5 + descarga) pero el usuario no lo ve y solo actúa la doble progresión. Propuesta: ciclo explícito («Ciclo 2 · semana 3 de 6»), onda RIR por semana (D4: principiante 3,3,3,2,2; intermedio/avanzado 3,2,2,1,1; descarga RIR 4), carga por doble progresión (existe; T4 la mejora), descarga programada en la 6 y reactiva por AUGE, y un **check-in semanal ligero** (rendimiento frente a la semana previa + agujetas) que mueva ±1 serie por músculo dentro de MEV–MRV. Para `simple-1/ab/4`, importados y creados a mano: **capa «Ciclo KPKN»** opcional que no cambia sus ejercicios: objetivo de esfuerzo (RIR) por semana del ciclo visible en vivo, descarga propuesta cada 6 semanas (series a la mitad, RIR 4) y contador de ciclo; la doble progresión en manuales queda para después de T4.

**2.4 Cómo ajustar esfuerzo y dificultad por semana.** Tres capas con origen registrado: (1) la fase del bloque fija RIR/series base (receta, periodizador u onda del ciclo); (2) el check-in semanal (nuevo) mueve ±1 serie por músculo y −1 RIR si fue fácil, dentro de los topes del bloque; (3) en vivo, AUGE/readiness y el calibrador AMRAP (screens-v2) ajustan la sesión. Program Detail enseña las tres como «decisiones».

**2.5 Nivel de las mejores apps.** Ya iguala o supera: catálogo por configuraciones, generador con MEV–MRV y tiempo, calentamiento, AUGE, importador, descargas. Este plan cierra: ciclo de decisión semanal y por bloque, continuidad, fidelidad de los protocolos restantes, concepto único, wizard con plan a medida primero, Program Detail con lo que explica el progreso. Otras olas cierran: TM/AMRAP fieles (CAL5/5b), e1RM con RIR y objetivo automático (T3/T4), paneles de volumen y progreso (T5a/b).

**2.6 Wizard.** PLAN en dos niveles (D11): «Tu programa» (una ficha a medida con selector de alcance: una semana / 6 semanas en bloques / 12 periodizadas; con fecha, hasta la competición) y «Del catálogo (N)» plegado con los candidatos compatibles ordenados como hoy. La biblioteca gana la entrada «Crear mi programa a medida».

**2.7 Program Detail: qué datos importan.** Los que responden «¿voy bien y por qué?»: (1) dónde estoy (ciclo/bloque/semana, fase, próxima descarga o test); (2) cumplimiento (sesiones y series completadas vs plan, semana y bloque); (3) fuerza: e1RM de los levantamientos principales del programa con Δ desde el inicio del bloque; (4) carga de la semana: series duras por músculo plan vs real y frente a MEV–MRV (compacto; el detalle vive en T5a); (5) referencias TM/1RM con origen (CAL5b); (6) historial de decisiones del sistema (check-in, cierre de bloque, AMRAP→TM, descarga, método del autor) con fecha y motivo. **No se duplican**: cobertura/balance (pestaña VOLUMEN), mapas de fatiga (AUGE), progreso por ejercicio (T5b), volumen por ventana (T5a).

### 2.8 ¿Qué aportan RINGS/AUGE a los programas? (ampliación del 22-oct)

**Hoy (informe «AUGE en programas»).** AUGE llega al programa por tres puertas, todas reactivas y gruesas:
- Semanal: `WeeklyAutoregulationSignals` (`ProgramAutoregulationEngine.kt:69-89`) armado en `ProgramRepository.buildWeeklyAutoregulationSignals` (`:1107-1143`) con `readinessScore` (→ `INSERT_DELOAD` < 40 con fatiga > 75; `SCALE_WEEK_INTENSITY` −2,5/−5 % < 45/35; `DELAY_PEAK` < 50 antes de PEAK), `cumulativeFatigue` (suma de 14 días), `loadAdvisoryLevel` (UNLOAD → descarga o `SCALE_WEEK_VOLUME` ×0,8). **Defectos:** `computeAugeSnapshot` fija `overtrained = emptyList()` y `consecutiveHighE1rm = 0` (`:1221-1222`), así que la descarga por sobreentrenamiento y `PROMOTE_TM` **nunca se disparan desde el flujo semanal**; `stressLevel` llega y nadie lo lee.
- Cierre de bloque: `TransitionContext` (`BlockTransitionEngine.kt:37-46`) con fatiga, readiness, EMA de estrés y `OvertrainingDetector` (aquí sí llega) → descarga insertada (`:137-142`, `:181-195`).
- Sesión: `ExerciseReadinessEngine.calculatePerExerciseReadiness` + `calculateSetAdjustment` (`:36-224`, `:282-375`) bajan la carga por serie; **no escriben nada al programa**.
- AUGE calcula y el programa **no usa**: recuperación % y horas por músculo (`MuscleRecoveryStatus`, `AugeRecoveryEngine.kt:666-698`), baterías Muscular/Energía/Columna, ACWR sistémico y axial (solo llega el nivel agregado), sobreentrenamiento por músculo. **Nada mira hacia delante**: todo es ventana pasada (`SystemicLoadMonitor` 28 d, `cumulativeFatigue` 14 d); `OvertrainingDetector` usa `volumeRecommendations` como MRV pero nunca las semanas planificadas.
- Rings v4 (2.3 `AugeSnapshotComputer`, F-44) unificará el snapshot del programa con el de Inicio: la corrección de `overtrained`/`consecutiveHighE1rm` se coordina con esa ola (ver P8).

Lo que debe aportar AUGE a un programa, en orden de valor:
1. **Entrada del check-in semanal (P2)**: la recuperación por músculo al abrir la semana y el ACWR/Energía sustituyen o corroboran las agujetas declaradas (si AUGE dice «pectorales al 60 %» y el usuario dice «sin agujetas», se muestra la discrepancia y gana el usuario, pero queda registrada).
2. **Revisión de bloque (P3)**: fatiga acumulada del bloque (Energía media, semanas con ACWR alto, descargas reactivas insertadas) como un término más de `BlockReview`.
3. **Proyección hacia delante (nuevo, P8)**: con `AugeProjection` (Rings v4 2.4, «listo para…») el programa puede preguntar «¿qué fatiga producirá la semana 4 tal como está planificada?» y avisar antes («la semana 4 deja Columna al 55 %: baja un 10 % el volumen de bisagra o adelanta la descarga»). Hoy AUGE solo mira el pasado.
4. **Descarga reactiva honesta**: una sola regla (`augeAutoDeload` + multi-señal P7 de AUGE Pro) en vez de dos detectores; en programas de autor solo avisa, nunca inserta sin confirmar.
5. **Por ejercicio**: la readiness del músculo del ejercicio ya ajusta la carga en vivo; faltaba que esa decisión quedara en el historial del programa (P5, `ProgramDecision` con origen AUGE).

### 2.9 Calibrador de volumen: de encuesta a calibración con evidencia

**Hoy (informe «calibrador»).** `VolumeCalibrationEngine.calculate` (`:23-78`): cuatro respuestas 1–3 (técnica, consistencia, fuerza, movilidad) + estilo → `totalScore` 4–12, nivel ADVANCED si ≥ 8, `optimalSets` por estilo × nivel (BB 18/15, PB 16/14, PL 14/12) → `scale = optimalSets/15` que multiplica 12 músculos base con suelos y techos. Lo consumen el generador propio, `TemplateVolumeScaler`, la barra de volumen, el editor y `OvertrainingDetector` (como MRV). **Defectos:** es una foto única (recalibrar = reabrir la hoja); `CalibrationResponseState` solo se escribe como `DECLARED` (`SetupStepAnswers.kt:799`): nadie escribe `DERIVED/IMPORTED/SUGGESTED`; la hoja promete «Usamos tus datos y tu historial» (`ProgramHeroWidgets.kt:85`) y no es cierto; las notas se descartan (`:169`); el único ajuste por evidencia (`VolumeCalculator.calculateVolumeAdjustment` con DOMS/fuerza de los 3 últimos feedbacks, `ProgramDetailViewModel.kt:186-195`) vive en memoria y no se persiste; la hoja actúa de puerta antes de crear plantilla/protocolo (`HomeScreen.kt:562-605`, «Cancelar» = `skipCalibration`).

Evolución (P9): **calibración viva por evidencia, preguntas solo como arranque**.
- Las cuatro respuestas se **derivan** cuando hay datos: consistencia = adherencia real de 4+ semanas; fuerza = e1RM relativo al peso corporal por patrón (`StrengthModel`/`ProfileProgress`); técnica = puntuación del analizador de vídeo (ola VT) y precisión del RIR declarado frente al registrado; movilidad = molestias y ROM del vídeo cuando exista. El usuario ve el origen de cada una y puede sobrescribir (`DECLARED` gana sobre `DERIVED` hasta que la evidencia lo contradiga dos ciclos).
- **Landmarks personales por músculo**: a partir del check-in semanal (P2) y del rendimiento, cada músculo aprende su MEV/MRV efectivo (RP: el MRV es la semana en que el rendimiento deja de igualarse; MEV donde el estímulo es bajo). Empiezan en la tabla general y se mueven ±1–2 series por ciclo con evidencia; nunca por una sola semana.
- El calibrador deja de ser una hoja aparte: pasa a ser una tarjeta «Tu volumen» en Program Detail (de dónde sale cada tope, cuánto llevas, qué aprendió) y el mismo motor alimenta al generador, al ajuste semanal, a la revisión de bloque y a la barra de volumen. La hoja actual queda como «arranque sin datos».

### 2.10 Las sesiones de cada semana

**Hoy (informe «sesiones»).** La semana actual la fija el cursor persistido (`runState`), no la fecha (`ProgramActiveStateEngine.kt:27-104`). La sesión «de hoy» sale del calendario si el programa está fechado o del `dayOfWeek` (`HomeSessionResolver.kt:181-226`); entrenar otro día solo avisa («KPKN registrará la desviación», `DayView.kt:487-489`). Una semana se completa cuando TODAS las sesiones `REQUIRED` tienen log (`isWeekInstanceComplete` `:177-196`); **una sesión no hecha no se salta ni se arrastra**: el cursor se queda, y en la última semana de un bloque `HOLD_INCOMPLETE` bloquea el avance (`BlockTransitionEngine.kt:130-134`). Una semana entera sin entrenar sigue siendo «la actual» indefinidamente. **No existe «saltar sesión», «mover sesión» ni «repetir semana»** (grep sin resultados). La re-materialización conserva íntegras las sesiones ejecutadas y las marcadas `ManualSessionOverride` (`PlanMaterializer.kt:525-597`); el resto se sobrescribe entero (no hay «serie fijada»: la unidad es la sesión). El editor guarda cualquier semana futura con alcance `SESSION_ONLY` o `MESOCYCLE` (`SessionEditorContracts.kt:133-136`); no hay alcance «bloque» ni «programa». `WeekVariant` es de sesión (A/B/C/D), no de semana; `OptionalSessionConfirmation` solo la leen `DayView` y el adaptador de nutrición.

**Qué hacer (P10).** Reglas de producto explícitas, puras y con registro: (1) **sesión no hecha al cerrar la semana**: el usuario elige «saltar» (queda omitida, no cuenta), «hacerla ahora» (se mueve al siguiente día libre de la semana siguiente desplazando el resto) o «fusionar» (el motor propone meter los básicos de la omitida en la siguiente sesión del mismo patrón); por defecto, en propios y plantillas KPKN, saltar; en protocolos de autor, mover (fidelidad). (2) **Semana en blanco (≥ 7 días sin log)**: aviso en Inicio y en el detalle con «repetir semana» o «vuelta suave» (la misma semana con la mitad de series y RIR +1, marcada como DELOAD de retorno) antes de seguir; con ≥ 21 días, proponer reiniciar el bloque con TM −5 %. (3) `HOLD_INCOMPLETE` deja de bloquear sin salida: ofrece las mismas tres opciones. (4) El editor gana el alcance «resto del bloque» (junto a sesión y mesociclo). (5) Toda sesión futura regenerada por el motor conserva lo editado a mano (ya) y deja un `ProgramDecision` (P5).

### 2.11 Selección de ejercicios: ¿cambian por bloque?

**Hoy (informe «sesiones»).** Ningún motor rota ejercicios por bloque en propios/generados: `RoutinePeriodizer` copia la semana base y solo cambia series/reps/RIR/kilos (`:39-48`); `SessionCompositionPolicy` solo valida (H1 orden de roles, H2 ≤ 2 compuestos por familia de patrón, H3 ≤ 3 ejercicios del mismo dominante, H4 un ejercicio por `replacementGroup`, H5 ≤ 2 axiales y presupuesto espinal ≤ 12, H6 dosis 3–9 ejercicios / 10–30 series / ≤ 100 min; `:162-440`). El cambio de ejercicio por el usuario (`ExerciseSwapOptions`, hasta 6 alternativas por material y similitud) **solo existe en el alta** (`SetupWizardViewModel.kt:1842-1856`); en Program Detail no hay «cambiar ejercicio» pese al KDoc; el editor sustituye por catálogo sin heredar la progresión (la identidad nativa es por `configurationId`, `ProgramScheduleModels.kt:57-71`). Las plantillas `power-16-4/20-5` sí rotan variantes por bloque en su receta.

### 2.11 Selección de ejercicios: ¿cambian por bloque?

**Principio** (de RP, Juggernaut y la literatura de especificidad): **dentro de un bloque, los mismos ejercicios** (la progresión necesita un estímulo estable y la doble progresión es por identidad de ejercicio); **entre bloques, cambian las variantes según el objetivo del bloque**, no por capricho:
- Acumulación: variantes de más rango y más tolerancia (sentadilla alta, press con pausa/Spoto, peso muerto con déficit, más accesorios).
- Intensificación/especificidad: variantes más cercanas a la competición (cajón, inclinado, rumano → convencional), menos accesorios.
- Pico/taper: solo los levantamientos de competición o los principales del objetivo; accesorios mínimos.
- Hipertrofia (culturismo): los mismos ejercicios durante el mesociclo; se rotan entre mesociclos para repartir el estrés articular y evitar estancamiento; en descarga, los mismos con la mitad de series.
- Protocolos de autor: lo que diga el autor (Cube y Westside rotan por semana; Sheiko, 5/3/1, Juggernaut y Texas fijan; RP rota entre mesos).
- Excepciones a mano: cambio de ejercicio por el usuario con alcance (esta sesión / resto del bloque / todo el programa) conservando la progresión, y sustitución inteligente en vivo (T6).

**Qué hacer (P7).** Rotación de variantes por bloque en el periodizador y en `body-*`/`powerbuild-*` (L-29) usando `replacementGroup` / `preservesIntent` / `PatternFamily` del catálogo, con el presupuesto de volumen y tiempo ya existente y las reglas H1–H6 como validación; tabla de rotación por objetivo de bloque y disciplina (acumulación → variantes de rango; intensificación → específicas; pico → competición) con sus excepciones (calistenia y armwrestling: solo series). Y **cambiar ejercicio desde Program Detail** con alcance (esta sesión / resto del bloque / todo el programa) reutilizando `ExerciseSwapOptions`, heredando la progresión cuando la alternativa es EQUIVALENT (mismo `replacementGroup`) y arrancando de cero cuando es solo del mismo patrón.

### 2.12 Programa y competición

**Hoy (informe «competición»).** El modelo es completo para el registro *posterior*: `CompetitionRecord` con federación, categoría, pesaje, división, equipo, bloques técnicos con tres intentos (válido/nulo/omitido, RPE, notas), total, DOTS/IPF GL/Wilks (`CompetitionScoring`), podio, álbum y diario (`CompetitionModels.kt:62-183`); asistente de 4 pasos EVENT → LIFTS → PLACE → ALBUM, solo powerlifting y solo a posteriori (`CompetitionWizardViewModel.kt:36-41`, `:241-253`); archivo en Perfil con comparativa de dos meets. Enlace con el programa: la fecha clave COMPETITION crea/actualiza el record PLANNED (`CompetitionKeyDateSync.mergeFromKeyDate` `:86-124`), `fitBlocksToKeyDate` **solo informa** si faltan o sobran semanas (`ProgramKeyDateEngine.kt:92-129`), el asistente del alta periodiza hacia la fecha (`EntrenoCompetitionStep.kt:129-138`), `MeetRecipeFitter` estira protocolos aptos, `DELAY_PEAK` vive en la autorregulación semanal, y con competición por delante se suprime el test de 1RM (`BlockTransitionEngine.kt:149-179`, `hasCompetitionAhead` `:275-283`). Inicio muestra cuenta atrás y fase UPCOMING/REGISTER; avisos a −7 d, −48 h, inicio y +5 h «¿Cómo te fue?» (`CompetitionReminderManager.kt:48-59`).

**Lo que no existe** (confirmado): planificación de intentos (ni aperturas ni 2.º/3.º), objetivo de categoría de peso con cuenta atrás de pesaje, «sesión de competición» (el repositorio la excluye a propósito: «Competitions are historical/planned events, not workout sessions», `CompetitionRepository.kt:23`), calentamiento por tandas, cronómetro, **actualización del 1RM/TM con los intentos válidos**, ni propuesta de siguiente macrociclo tras el meet; el único efecto posterior es que vuelve a proponerse el test de 1RM.

**Qué añadir (P11), con lo que hacen las apps de referencia de powerlifting (OpenLifter, MeetPro, Boostcamp, JuggernautAI «meet mode»):** **antes** — objetivo de categoría y pesaje con la tendencia de peso (semanas para dar el peso, aviso si la tendencia no llega; se apoya en la tendencia de MFP); plan de intentos desde el e1RM/TM del final del pico (apertura ≈ 90–92 % del máximo esperado, 2.º ≈ 96–98 %, 3.º = objetivo; redondeo a 2,5 kg; se guarda en el record PLANNED como `plannedAttempts`); **el día** — «modo competición» (hoja, no sesión de programa, para respetar la decisión del repositorio): tandas con calentamientos hacia atrás desde la apertura, marcar válido/nulo, siguiente intento sugerido según el resultado; **después** — el resultado ya se registra; falta que los intentos válidos actualicen 1RM/TM (`TrainingMaxMerge`, con confirmación) y que el cierre del meet dispare la continuación (D7). Halterofilia y strongman: mismo esqueleto con sus eventos (el modelo ya tiene SNATCH/CLEAN_AND_JERK/CUSTOM).

### 2.13 Alimentación, cuerpo y plan: una sola temporada (pregunta del 22-oct)

**Hoy (informe «puentes»).** Entreno → nutrición: el programa entra solo como gasto previsto por fecha (`NutritionTrainingCalendarAdapter.adapt` `:210` con `TrainingEnergyEngine.estimatePlannedSession`) que alimenta el reparto semanal `T_i = B + α·(E_i − Ē)` sobre un presupuesto fijo (`NutritionWeeklyForecastPlanner.kt:159-214`); al cambiar el programa (bloque, descarga, semana sin sesión) solo se recalcula la previsión; **calorías base, macros y dirección no**. Nutrición → entreno: el único canal es el multiplicador de recuperación de AUGE (`NutritionRecoveryEngine`), **apagado por defecto** (`Settings.kt:300`, decisión D4 de MFP); nada del plan o del registro toca prescripción, volumen, RIR ni propuestas. Cuerpo: el peso entra en la energía de sesión, cardio, EER y ventana de peso objetivo (`NutritionGoalWeight`); % graso/músculo solo como `startValue`; la tendencia declarada en el alta no entra en la preparación del plan. **No existe ninguna regla que acople** `PlanDirection` (déficit/mantenimiento/superávit) con el modo o la fase del programa (grep vacío), ni concepto de temporada compartido. Lo que añade la ola MFP (estimador de gasto, tendencia robusta, check-in semanal de nutrición) se da por hecho y no se duplica.

**Qué hacer (P12).** Una **capa de temporada**, puro dominio, que lea fase del bloque, dirección nutricional y tendencia de peso y produzca avisos y propuestas con origen, sin que ninguna mande sobre las otras sin confirmación:
- **Fase de entreno → nutrición**: acumulación admite superávit o mantenimiento; pico/taper y semana de competición piden mantenimiento (nada de déficit agresivo); descarga baja el gasto planificado (ya lo hace la energía por sesión) y la propuesta del check-in de nutrición debe saberlo para no «corregir» una semana de descarga como si fuera un parón.
- **Nutrición → entreno**: en déficit ≥ 15 % del gasto, los topes de volumen bajan de MRV a MAV y el RIR de partida sube 1 (menos recuperación); en superávit, los propios pueden abrir el MAV. Esto va por el calibrador vivo (P9) y el ajuste semanal (P2), no por AUGE (el multiplicador nutricional de AUGE sigue apagado por decisión D4 de MFP).
- **Cuerpo → entreno**: peso corporal en las cargas de peso corporal/lastre (verificar), categoría de peso para la competición (P11), FFMI/relación fuerza–peso como evidencia de nivel para el calibrador (P9).
- **Cuerpo → nutrición**: ya cubierto por la tendencia y el check-in de MFP; la temporada solo añade el objetivo de pesaje con fecha.
- **Una sola pantalla de temporada** (más adelante, tras el rediseño de Program Detail): bloque actual, dirección nutricional, tendencia de peso y fecha objetivo, con las tres propuestas pendientes.

## 3. Hallazgos (A-xx). Gravedad: ALTA = recibe algo incorrecto o la función prometida no existe; MEDIA = calidad; BAJA = pulido

### 3.1 Catálogo y fidelidad

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-01 | Ninguna receta de autor ni plantilla COMPLEX se valida contra MEV/MAV/MRV por músculo; C1–C10 es estructural | `VolumeLandmarks` sin consumidores en `data/protocols`; sin test en `src/test/.../data/protocols/` | MEDIA | P1 |
| A-02 | Fase 2 sin ejecutar: GZCLP sin etapas/reset (L-09), Cube sin rotación y con un solo `blockIndex` en 10 semanas (L-13), RP sin rampa MEV→MRV (L-25), RTS sin top sets por RPE (L-26), Sheiko sin tirón (L-15), Westside sin bandas/cadenas (L-14), power-12-3 con accesorios avanzados (L-28), bloques poco diferenciados (L-29), parches C-05..07 | README curaduría §3.2; inventario | ALTA | P1 (menos lo de CAL5) |
| A-03 | `RPE_CAP` declarado por RTS-style, RP-style y Calgary sin consumidor: el RPE registrado no ajusta carga ni back-offs | INV1 §6.12; inventario | ALTA | P1 (motor puro) + coordinación CAL5 |
| A-04 | AMRAP de 5/3/1, GZCLP, Juggernaut, J&T no mueven el TM según su fuente; OHP de nSuns no progresa; test de 1RM siempre pide S/B/D | INV1 §6 | ALTA | **CAL5 (screens-v2)** |
| A-05 | `MeetRecipeFitter` altera protocolos aptos (+2,5 % por repetición, descargas insertadas) sin opción «sin ajustar» | `MeetRecipeFitter.kt:47-61` | MEDIA | P1 |
| A-06 | Tapers sin `kind`/`blockGoal` de descarga: Calgary w12–16 (PEAK), SBD-4 w11 (`TAPER` con `kind TRAINING`); el motor de transición y AUGE los tratan como entrenamiento | inventario | MEDIA | P1 |
| A-07 | Landmarks tipo 2017 (isquios MRV 20 frente a 8–14 de RP-2024; glúteos 16 frente a 24–30; pantorrillas MEV 8 frente a 4–6) | B1 §3.4 | MEDIA | decisión D9 |
| A-08 | Id `smolov-jr-hidden` para evitar la colisión con `smolov-jr`; `body-12-3` se llama «3» y tiene 4 días; `kpkn-ppl-6`/`rp-style` con `trainingMaxPercent` por defecto sin TM real | inventario | BAJA | P1 |

### 3.2 Periodización y seguimiento

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-10 | Cierre de bloque sin evaluación de resultados (e1RM, series cumplidas, adherencia); solo cumplimiento de sesiones, fatiga y test | `BlockTransitionEngine.kt:108-222` | ALTA | P3 |
| A-11 | Fin de programa sin continuación (nuevo ciclo con marcas nuevas, programa siguiente, mantenimiento) | `ProgramProgressEngine.kt:1034-1055`, `:593-610` | ALTA | P3 |
| A-12 | `Block.progressionScheme` y `materializationStatus` sin consumidor en recetas | informe runtime Q4; `Program.kt:347` | BAJA | P3 (se documenta) |
| A-13 | Propios de una semana: ciclo de 6 invisible; RIR fijo por nivel (sin onda D4); descarga con la misma carga (H-g) | `NativeProfileSpec.kt:741-760` | MEDIA | P2 |
| A-14 | No hay check-in semanal de entrenamiento que module series/RIR; la progresión nativa no mira AUGE | informe runtime Q7 | ALTA | P2 |
| A-15 | `simple-1/ab/4`, manuales e importados sin periodización, sin descarga ni objetivo de esfuerzo | `ProgramTemplates.kt:35-72` | MEDIA | P2 |
| A-16 | Solo se re-materializa `nextWeekId` al aplicar una propuesta; un cambio de TM en semana 2 de un bloque de 5 deja las semanas 4–5 con la carga vieja hasta su turno | `ProgramAutoregulationEngine.kt:874-883` | MEDIA | **CAL5b** (rematerialización automática) |

### 3.3 Concepto de programa

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-20 | UI y textos exponen «simple / avanzado» como dos tipos; plantillas «1 Semana / Semana A/B / 4 Semanas» descritas como «Programa simple de N semanas» | §1.2 | MEDIA | P0 |
| A-21 | La ficha, la biblioteca y la cabecera no dicen el alcance (una semana que se repite / N semanas / bloques) de forma uniforme | `PlanLabels`, `ProgramHeaderModel` | BAJA | P0 |

### 3.4 Wizard y Program Detail

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-30 | En disciplinas, PLAN mezcla a medida y catálogo en una lista de 6; en generales, las 3 formas son 3 tarjetas sin explicar la diferencia | `EntrenoPlanModels.kt:145-194` | MEDIA | P4 |
| A-31 | Los planes a medida no existen fuera del asistente (`generated:* listed=false`) | inventario | MEDIA | P4 |
| A-32 | Adherencia y e1RM se calculan y no se muestran; no hay carga semanal plan vs real, % de bloque ni historial de decisiones | `ProgramAnalyticsEngine.kt:438-507`; `ProgramAnalyticsCards.kt` | MEDIA | P5 |

### 3.5 AUGE en programas

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-40 | El snapshot semanal fija `overtrained = ∅` y `consecutiveHighE1rm = 0`: la descarga por sobreentrenamiento y `PROMOTE_TM` nunca se disparan desde el flujo semanal | `ProgramRepository.kt:1221-1222` | ALTA | P8 (con Rings v4 2.3) |
| A-41 | `stressLevel` llega y nadie lo lee; recuperación por músculo, baterías, ACWR y horas hasta recuperado no llegan al programa (solo el nivel agregado WATCH/ADJUST/UNLOAD) | `ProgramAutoregulationEngine.kt:69-89`, `AugeModels.kt:120-194` | MEDIA | P8 |
| A-42 | El ajuste de carga por readiness en vivo no deja rastro en el programa | `SetAdjustmentOverlay.kt:78-87` | MEDIA | P5/P8 |
| A-43 | AUGE solo mira el pasado: no hay proyección de la semana planificada | `SystemicLoadMonitor.kt:39-44`, `OvertrainingDetector.kt:26-55` | ALTA | P8 (proyector de carga; `AugeProjection` de Rings 2.4 cuando exista) |

### 3.6 Calibrador de volumen

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-50 | La hoja promete «Usamos tus datos y tu historial» y solo usa 4 respuestas declaradas; nadie escribe `DERIVED/IMPORTED/SUGGESTED` | `ProgramHeroWidgets.kt:85`, `SetupStepAnswers.kt:799` | ALTA (honestidad) | P9 |
| A-51 | Calibración de foto única; el único ajuste por evidencia (DOMS/fuerza de 3 feedbacks) vive en memoria y no se persiste; `calibratedAtMs`/`calculatorRevision` sin lector | `ProgramDetailViewModel.kt:186-195`, `Program.kt:295-296` | MEDIA | P9 |
| A-52 | La hoja es puerta previa a crear plantilla o protocolo («Cancelar» = `skipCalibration`) | `HomeScreen.kt:562-605`, `ProgramsScreen.kt:307/337` | MEDIA (fricción) | P9 |
| A-53 | Las notas de la hoja se descartan | `ProgramHeroWidgets.kt:169` | BAJA | P9 |

### 3.7 Sesiones de la semana

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-60 | Una sesión no hecha bloquea el cierre del bloque (`HOLD_INCOMPLETE`) sin salida; no hay «saltar», «mover» ni «repetir semana» | `BlockTransitionEngine.kt:130-134`, `ProgramProgressEngine.kt:951-966` | ALTA | P10 |
| A-61 | Una semana entera sin entrenar sigue siendo «la actual» sin aviso ni vuelta suave | `ProgramProgressEngine.kt:177-196` | MEDIA | P10 |
| A-62 | El editor solo ofrece alcance sesión / mesociclo; no «resto del bloque» | `SessionEditorContracts.kt:133-136` | MEDIA | P10 |
| A-63 | La re-materialización congela por sesión entera; no hay «serie fijada» | `PlanMaterializer.kt:525-597` | BAJA | se documenta |

### 3.8 Selección de ejercicios

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-70 | Ningún motor rota variantes por bloque en propios/generados; `body-*`/`powerbuild-*` cambian poco (L-29) | `RoutinePeriodizer.kt:39-48` | MEDIA | P7 |
| A-71 | «Cambiar ejercicio» solo existe en el alta; en Program Detail no, pese al KDoc; el editor sustituye sin heredar progresión | `ExerciseSwapOptions.kt:17`, `SetupWizardViewModel.kt:1842-1856` | MEDIA | P7 |

### 3.9 Competición

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-80 | El resultado del meet no actualiza 1RM/TM ni dispara continuación; solo vuelve a proponerse el test | `BlockTransitionEngine.kt:272-283`, `CompetitionWizardViewModel.kt:241-253` | ALTA | P11 |
| A-81 | Sin plan de intentos (aperturas, 2.º, 3.º) ni objetivo de categoría/pesaje con cuenta atrás | grep en `screens/competitions` | ALTA | P11 |
| A-82 | `fitBlocksToKeyDate` solo informa; sin «modo competición» del día (tandas, calentamiento, siguiente intento) | `ProgramKeyDateEngine.kt:92-129`, `CompetitionRepository.kt:23` | MEDIA | P11 |
| A-83 | Asistente solo de powerlifting; halterofilia/strongman usan el mismo record sin flujo | `CompetitionWizardScreen.kt:89` | BAJA | P11 (fase 2) |

### 3.10 Alimentación y cuerpo

| id | Hallazgo | Evidencia | Grav. | Paquete |
|---|---|---|---|---|
| A-90 | Ninguna regla acopla la dirección nutricional con el modo o la fase del programa; una descarga o una semana sin entrenar solo mueven el reparto, no el plan | `NutritionGoalResolver.kt:58-123`, `NutritionWeeklyForecastPlanner.kt:180-193` | ALTA | P12 |
| A-91 | Un déficit no baja los topes de volumen ni sube el RIR de partida | grep vacío | MEDIA | P12 → P9/P2 |
| A-92 | La tendencia de peso declarada en el alta no entra en la preparación del plan; % graso solo `startValue` | `NutritionPlanPreparation.kt:61-90`, `SetupStepAnswers.kt:463,568` | MEDIA | P12 |
| A-93 | No existe categoría de peso objetivo fuera del record posterior al meet | `CompetitionModels.kt:163-173` | MEDIA | P11 |

## 4. Decisiones (valor recomendado; cámbialas al aprobar)

| # | Decisión | Recomendación | Descartado |
|---|---|---|---|
| D1 | Bloques pre-hechos vs al vuelo | Híbrido (§2.2) | Generación completa al vuelo |
| D2 | Concepto único «Programa» | Retirar «simple/avanzado» de UI y textos; atributo visible **Alcance** («Una semana que se repite» / «N semanas» / «Por bloques: N bloques, M semanas»); `ProgramStructure` queda interno como modo de ejecución | Renombrar el enum (40 archivos y JSON persistido) |
| D3 | Añadir bloques a un programa cíclico | Se mantiene la mecánica (los ciclos y eventos se detienen) pero el texto deja de hablar de «convertir a avanzado»: «Este programa pasa a ser por bloques; los ciclos y eventos se detienen» | Reescribir loops para convivir con bloques (fuera de alcance) |
| D4 | Onda RIR en propios | Implementar DEV-r2-07 tal como se decidió el 03-oct | — |
| D5 | Check-in semanal de entrenamiento | Hoja opcional al abrir la primera sesión de la semana: por músculo trabajado, «rendimiento vs semana pasada» (1–4) y «agujetas» (1–4); ajuste ±1 serie dentro de MEV–MRV del plan y −1 RIR solo si fácil y sin agujetas; nunca en protocolos de autor; se puede desactivar en Ajustes. **DECIDIDO por el usuario (22-oct)** | Solo señales automáticas (rendimiento registrado + AUGE); una pregunta global |
| D6 | Cierre de bloque | `BlockReview` puro (adherencia, series reales/plan por músculo, Δe1RM, fatiga AUGE) → ajuste del bloque siguiente en propios/plantillas KPKN (RIR de partida, factor de series, base de carga) como propuesta `ADJUST_NEXT_BLOCK` que respeta OFF/PROPOSE/AUTO; en protocolos solo informa | Solo informativo |
| D7 | Fin de programa | `ProgramContinuation`: (a) nuevo ciclo con TM/1RM actualizados, (b) programa sugerido del catálogo (mismo objetivo, siguiente nivel/alcance), (c) semana de mantenimiento; test de 1RM opcional antes | Terminar sin más |
| D8 | Volumen en recetas de autor | Regla C11 SOFT: series directas por músculo y semana frente a MEV–MRV; a los protocolos de autor no se les cambia el volumen (fidelidad manda), se anota en la ficha («volumen alto en X») | Forzar los protocolos a los landmarks |
| D9 | Landmarks de volumen | Mantener cifras; registrar `volume-landmarks-2017` y dejar la revisión 2024 como decisión aparte (toca T006 y oráculos). **DECIDIDO por el usuario (22-oct)** | Cambiar ahora |
| D10 | `MeetRecipeFitter` | Opción «Sin ajustar a la fecha» (protocolo exacto, nota honesta) | — |
| D11 | Wizard PLAN | «Tu programa» con selector de alcance + «Del catálogo» plegado; sin paso nuevo | Paso «¿personalizado o catálogo?» |
| D12 | Program Detail | Motor puro `ProgramProgressReport` + tarjeta «Progreso» mínima + especificación para el rediseño de screens-v2 | Rediseñar ahora |
| D13 | Protocolos por RPE (A-03) | Motor puro `RpeTopSetEngine` (top set al RPE → back-offs al % del top; Helms: fuera de rango ±2 % por 0,5 RPE) consumido por `RPE_CAP`; se cablea en `ProgramAutoregulationEngine` de forma aditiva y se avisa a screens-v2 (CAL5) antes de fusionar | Esperar a CAL5 |
| D14 | AUGE en programas | Ampliar las señales semanales (recuperación por músculo, Energía, ACWR, sobreentrenamiento real) de forma aditiva y corregir el snapshot coordinando con Rings v4 2.3 (si `AugeSnapshotComputer` llega antes, se consume; si no, parche mínimo con aviso); proyección «hacia delante» como **proyector de carga planificada** (series × intensidad por músculo de la semana siguiente frente a recuperación actual), sustituible por `AugeProjection` (Rings 2.4) cuando exista; la descarga reactiva en protocolos de autor solo avisa | Simular AUGE desde el programa |
| D15 | Calibrador vivo | Respuestas `DERIVED` con ≥ 4 semanas de datos (adherencia, e1RM relativo, técnica del vídeo, molestias); `DECLARED` gana hasta 2 ciclos de contradicción; landmarks personales por músculo que se mueven ≤ 2 series por ciclo con evidencia del check-in y del rendimiento; la hoja actual queda como «arranque sin datos» con texto honesto y deja de ser puerta previa (crear plantilla usa `SUGGESTED` por nivel); tarjeta «Tu volumen» en Program Detail | Mantener la encuesta |
| D16 | Sesión no hecha y semana en blanco | Al cerrar la semana con sesiones `REQUIRED` sin hacer: saltar / hacerla ahora (mover) / fusionar; por defecto saltar en propios y plantillas KPKN, mover en protocolos de autor; `HOLD_INCOMPLETE` ofrece las mismas opciones; ≥ 7 días sin log → «repetir semana» o «vuelta suave» (mitad de series, RIR +1); ≥ 21 días → reiniciar bloque con TM −5 % (propuesta) | Avance por fecha automático |
| D17 | Rotación de ejercicios | Tabla por objetivo de bloque × disciplina (acumulación → variantes de rango; intensificación → específicas; pico → competición; culturismo: fijos dentro del meso, rotan entre mesos); nunca en protocolos de autor; cambiar ejercicio desde Program Detail con alcance sesión / resto del bloque / programa; herencia de progresión solo si EQUIVALENT | Rotar por semana |
| D18 | Competición | Plan de intentos desde e1RM/TM del fin del pico (apertura 90–92 %, 2.º 96–98 %, 3.º objetivo, redondeo 2,5 kg) en el record PLANNED; objetivo de categoría y pesaje con la tendencia (MFP) y cuenta atrás; «modo competición» del día como hoja (no sesión del programa, respetando `CompetitionRepository.kt:23`) con tandas, calentamiento hacia atrás y siguiente intento; intentos válidos → propuesta de 1RM/TM (`TrainingMaxMerge`, con confirmación) → continuación (D7); asistente «antes» además del «después»; halterofilia/strongman con el mismo esqueleto en fase 2 | Sesión de competición dentro del programa |
| D19 | Temporada | Motor puro `SeasonAdvisor` (fase del bloque + dirección nutricional + tendencia + competición → avisos y propuestas con origen): pico/taper/competición ⇒ mantenimiento (aviso si déficit); descarga ⇒ el check-in de nutrición no la lee como parón; déficit ≥ 15 % ⇒ topes MAV y RIR +1 en propios/KPKN; superávit ⇒ MAV ampliable; sugerir la dirección en el alta según el perfil (SUGGESTED); AUGE y el estimador de MFP no se tocan; UI = tarjeta en Program Detail y aviso en Inicio ahora, pantalla «Temporada» tras el rediseño | Que nutrición mande sobre el programa o al revés |
| D20 | Categoría de peso | Campos `targetWeightClass` / `targetBodyweightKg` en el record PLANNED (JSON, sin Room); solo avisos («a este ritmo das el peso 9 días antes» / «no llegas»); sin recortes de agua automáticos | — |

---
