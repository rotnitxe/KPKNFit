# Auditoría — Cardio circuitos / intervalos HIIT (2026-08-19)

- **Plan auditado:** `.opencode/plans/2026-08-18_cardio-intervalos-hiit.md` (flags: [auge])
- **Pipeline al auditar:** `.opencode/pipeline.json` apunta a `.opencode/plans/2026-08-17_auge-ring-drainage-audit.md` stage `construction` — **desalineado** (ver H0)
- **Diff auditado:** `git diff HEAD --stat` 14 archivos producto + 1 test nuevo (`CardioIntervalEngineTest.kt`) + fix `OnboardingStateDerivationTest.kt`; `compileBaseDebugKotlin` BUILD SUCCESSFUL 3m28s, `testBaseDebugUnitTest` BUILD SUCCESSFUL 57s/1m48s, `assembleBaseDebug` BUILD SUCCESSFUL 1m04s
- **Veredicto:** **request_corrections** — 1 CRÍTICO (H1) bloquea merge

---

## Resumen ejecutivo

La implementación cumple el contrato funcional base (vertical blocks en vivo, editor con rondas/bloques, plantillas, persistencia lenient sin migración) y **compila + tests en verde**, pero introduce **1 defecto crítico de datos** que infla la duración de las 3 plantillas principales 3-5× (Tabata 68 min vs 12 min) y contradice el diálogo que anuncia el valor correcto. Además hay 3 hallazgos MEDIO (catálogo, estado tras borrar último bloque, cobertura de tests del plan ausente) y 1 MAYOR de voz (bypass de verbosity). Ninguno rompe persistencia/Room/AUGE, pero H1 debe corregirse antes de merge.

---

## Hallazgos priorizados

| # | Sev | Área / archivo | Patrón | Evidencia | Test que lo detecta |
|---|---|---|---|---|---|
| H0 | MEDIO | `.opencode/pipeline.json:2` | Pipeline desalineado: apunta a plan viejo | `pipeline.json:2` `plan: auge-ring-drainage-audit` + `stage: construction` vs plan aprobado `cardio-intervalos-hiit` | — (proceso) |
| H1 | **CRÍTICO** | `data/models/CardioHiitTemplates.kt:23-49` `Session.kt:353` `domain/cardio/CardioIntervalEngine.kt:27` | `toDetails` incluye WARMUP/COOLDOWN dentro de la lista repetida por `intervalRounds` → Tabata 4080s (68 min) vs 720s (12 min) | `CardioHiitTemplates.kt:24-42` add WARMUP/COOLDOWN a `allBlocks`; `:47` `* rounds`; `Session.kt:353` `sum*rounds`; `CardioIntervalEngine.kt:27` `repeat(rounds)` sobre lista completa; diálogo `HiitTemplatePickerDialog.kt:65` calcula `warmup + sum*rounds + cooldown` (12 min) y contradice lo aplicado | `CardioHiitTemplatesTest` (ausente) — debió fallar |
| H2 | MEDIO | `data/models/CardioCatalog.kt:11-23` | `supportsSpeed` default `true` muestra “km/h” en bici/remo/elíptica | `CardioCatalog.kt:11` default true; `:16-19` bici/remo/elíptica con `supportsRpm/Watts` pero sin anular `speed` → `CardioIntervalsEditor.kt:187` `catalogSupportsSpeed ?: true` | manual |
| H3 | MEDIO | `screens/sessioneditor/components/CardioIntervalsEditor.kt:197-201, 108` | Borrar último bloque deja `intervalBlocks=[]` + `targetDurationSeconds=0` → vivo “00:01”, editor “1 min” | `:197` `filterIndexed != idx` → `total=0`; `:108` switch off no restaura duración previa | manual |
| H4 | MEDIO | `screens/workout/WorkoutViewModel.kt:3323` | TTS intervalo usa `speakFeedbackUpdated` (bypass verbosity) en vez de `speakAnnouncement` | `:3323` `speakFeedbackUpdated("Bloque …")` vs plan `:134` exige `speakAnnouncement`; `WorkoutVoiceController.kt:631` vs `:847` (gate `allows(COMPLETE)`) | manual |
| H5 | MEDIO | `src/test` | Faltan tests del plan: `CardioHiitTemplatesTest`, `CardioIntervalsSerializationTest` | `git grep HiitTemplates app/src/test` → 0; plan `:170` exige ambos; inline `CardioIntervalEngineTest.kt:87` no cubre plantillas → H1 se coló | — |
| H6 | BAJO | `screens/workout/CardioLiveCard.kt:69` | Cardio “Libre” (`targetDurationSeconds==null`) muestra 00:01 en anillo | `:69` `effectiveDurationSeconds().coerceAtLeast(1)` vs antes `?:20*60`; `:108` `targetGoalSummary` ya muestra “Libre” pero anillo no | manual |
| H7 | BAJO | `screens/sessioneditor/components/CardioIntervalsEditor.kt:96-102` | Seed de intervalos hardcodea `speedKmh` para toda máquina | `:96` `speedKmh=6.0/10.0` sin chequear `supportsSpeed` → bici/remo reciben km/h (H2) | manual |

Nits H8-H10 (forma dialog vs sheet, reset de campos en parse fallido, rama muerta) no bloquean y se listan al final.

---

## Detalle y recomendaciones concretas

### H0 — Pipeline desalineado (MEDIO, proceso)
- **Evidencia:** `pipeline.json:2` sigue `auge-ring-drainage-audit` en `construction`. El gate `kpkn-gate` permitió construir porque ese plan tenía `flags: [auge]`, pero `request_approval`/`submit_audit` del nuevo plan fallarán por `checkPlanSections` y el auditor audita el plan equivocado.
- **Fix:** desde una sesión con tool `pipeline`, `pipeline.start` + `pipeline.request_approval` + `approve` apuntando a `2026-08-18_cardio-intervalos-hiit.md` y `construction_start`. Una línea, sin tocar producto.

### H1 — Plantillas multiplican warmup/cooldown (CRÍTICO)
- **Evidencia ya citada.**
- **Cálculo:** Tabata: bloques `[W300,W20,R10,C180]`×8=4080s; esperado 300+(20+10)×8+180=720s. 30/30: (300+30+30+240)×10=6000s vs 300+600+240=1140s. Propaga a `WorkoutViewModel.kt:3271` `effectiveDurationSeconds()`, `Calculations.kt:570`, `CardioCalorieEngine.kt:40`.
- **Recomendación concreta (una de dos, sin tocar plan):**
  ```kotlin
  // CardioHiitTemplates.kt:toDetails — opción A (más fiel al plan)
  val core = blocks.map { it.copy(id=UUID.randomUUID().toString()) }
  val allBlocks = buildList {
      if (warmupSeconds>0) add(Block(WARMUP, warmupSeconds, warmupSpeedFor(type)))
      repeat(rounds.coerceIn(1,99)) { core.forEach { add(it.copy(id=UUID.randomUUID().toString())) } }
      if (cooldownSeconds>0) add(Block(COOLDOWN, cooldownSeconds, cooldownSpeedFor(type)))
  }
  return CardioDetails(..., intervalBlocks=allBlocks, intervalRounds=1, targetDurationSeconds=allBlocks.sumOf{durationSeconds})
  ```
  Añadir `CardioHiitTemplatesTest` con: `assertEquals(720, Tabata.toDetails(TREADMILL).effectiveDurationSeconds())` etc., y que `warmup/cooldown` aparezcan una sola vez en `expandedBlocks`.

### H2 — Catálogo muestra velocidad donde no aplica (MEDIO)
- **Fix:** `CardioCatalog.kt:16-19` → `supportsSpeed=false` para `ELLIPTICAL` (`supportsRpm=true` + intensidad por bloque), `ROW_MACHINE` y `BIKE_STATIONARY`; en `CardioIntervalsEditor.kt:272-394` mostrar campo intensidad 1-10 cuando `!supportsSpeed && supportsRpm` (elíptica) o añadir `supportsIntensity`.

### H3 — Borrar último bloque deja estado 0/1s (MEDIO)
- **Fix:** en `onDelete` si `newBlocks.isEmpty()` → `onChange(details.copy(intervalBlocks=emptyList(), intervalRounds=1, targetDurationSeconds=20*60))` (o restaurar `lastNonIntervalDuration` guardado en `remember`). Mismo en `Switch off` (`:108`): no dejar `total` del circuito como objetivo.

### H4 — TTS bypass verbosity (MEDIO)
- **Fix:** `WorkoutViewModel.kt:3323` → `voiceController.speakAnnouncement(...)` (mantiene gate `isEnabled()` ya en `:3309` y el `try/catch`). Es el contrato del plan `:134` y evita que `VoiceVerbosity.SILENT` hable bloques cada 20s.

### H5 — Tests del plan ausentes (MEDIO)
- **Fix:** crear `CardioHiitTemplatesTest.kt` (assert duraciones Tabata 12 min, 30/30 19 min, Sprint8 24 min, Fartlek/Z2 rounds=1, todos `duration>0`, `applicableTypes` ⊆ `CardioCatalog`) y `CardioIntervalsSerializationTest.kt` (round-trip `Session` con intervalos + decode blob v23 sin `intervalBlocks`). Esos tests habrían detectado H1.

### H6/H7 — Libre 00:01 y seed velocidad (BAJO)
- **Fix H6:** en `CardioLiveCard.kt:69` mantener guard 1s para timer pero en display READY usar fallback `20*60` cuando `targetDurationSeconds==null && !hasIntervals()`.
- **Fix H7:** generar seed según `CardioCatalog.findByType(details.type)` (si `supportsWatts` → `watts=120`, si `supportsRpm` y !`supportsSpeed` → `rpm=60/intensity`, etc.).

### Nits H8-H10
- H8: `HiitTemplatePickerDialog` vs `HiitTemplatePickerSheet` en `SessionEditorSheets.kt` — anotar desviación o registrar sheet como aprobó UI-revisor.
- H9: `remember(block.id, block.speedKmh)` pierde edición intermedia “10.” → usar `remember(block.id)` + sincronización `LaunchedEffect`.
- H10: `CardioIntervalsEditor.kt:105` `copy(intervalBlocks=intervalBlocks)` inalcanzable → eliminar.

---

## Verificaciones OK (con evidencia)

- **Room/migración:** `data/db/` y `app/schemas/` sin diff, `KpknDatabase.kt:61` v23, `Session.kt:340` defaults (`intervalBlocks=[]`, `intervalRounds=1`), `Entities.kt:13` `ignoreUnknownKeys+encodeDefaults` → viejos blobs decodifican, nuevos ignorados por app vieja, sin `coerceInputValues` y sin tocar enums viejos (`CardioType` 8, `CardioIntensity` 4 intactos) — nuevo `CardioBlockType` propio. ✓
- **AUGE hardening:** `AugeFatigueEngine.kt:239` `if (reps<=0 && weight<=0 && hasTime) return false` excluye cardio puro sin afectar TM de fuerza (test `Auge` en verde). ✓
- **Voz/coroutines:** `services/workout/` sin tocar, `launchCardioTimerJob` en `viewModelScope` + `persistOngoingState(immediate=false)`→`Dispatchers.IO`, tick puro, anuncio solo en transición con `try/catch`, sin eco/churn. ✓
- **Cálculo sesión:** `Calculations.kt:570` `effectiveDurationSeconds()` y calorías por bloque en `CardioCalorieEngine` coherentes. ✓
- **Pipeline flags:** `kpkn-gate` `ZONES: auge→domain/auge/` permitió `AugeFatigueEngine` porque pipeline viejo tenía `auge`; nuevo plan declara `flags: [auge]` — consistente. ✓

---

## Pruebas dirigidas ejecutadas (evidencia para gate)

- `compileBaseDebugKotlin` → `BUILD SUCCESSFUL in 3m28s`
- `testBaseDebugUnitTest` (full suite) → `BUILD SUCCESSFUL in 57s` (1ª) / `1m48s` (con `CardioIntervalEngineTest`, 1478 tests, 0 failures)
- `assembleBaseDebug` → `BUILD SUCCESSFUL in 1m04s`
- Intento `testBaseDebugUnitTest --tests '*.Cardio*'` falló por quoting del wrapper (no por código); revisores ejecutaron `CardioIntervalEngineTest` aislado → `BUILD SUCCESSFUL` (9/9) y `compileBaseDebugKotlin` UP-TO-DATE.

---

## Catálogo de regresiones — actualización propuesta

| Fecha | Área / archivo | Patrón | Test que lo detecta | Estado |
|---|---|---|---|---|
| 2026-08-19 | `data/models/CardioHiitTemplates.kt:23` | `toDetails` repite WARMUP/COOLDOWN por `intervalRounds` (Tabata 68 min) | `CardioHiitTemplatesTest` (a crear) | **abierto** → cerrar tras fix H1 |
| 2026-08-19 | `data/models/CardioCatalog.kt:11` | `supportsSpeed` default true muestra km/h en bici/remo/elíptica | manual + `CardioIntervalsEditor` | abierto → cerrar tras H2 |
| 2026-08-19 | `screens/sessioneditor/components/CardioIntervalsEditor.kt:197` | Borrar último bloque deja `0s`/`00:01` | manual | abierto → cerrar tras H3 |
| 2026-08-19 | `screens/workout/WorkoutViewModel.kt:3323` | `speakFeedbackUpdated` bypass `VoiceVerbosity` | manual | abierto → cerrar tras H4 |

---

## Recomendación al Constructor (orden)

1. **H1 (CRÍTICO)** + test de plantillas (H5 parcial) — sin esto el feature principal queda inutilizable.
2. H4 (voz) + H2 (catálogo) — 1 línea cada uno, sin riesgo.
3. H3 (borrado) + H6 (Libre) — pulido de borde.
4. Completar H5 tests restantes y nits.

Tras esos 3 primeros, el diff vuelve a `request_corrections`→`construction` y el próximo `submit_audit` debería dar `accept` si los tests de plantillas reflejan 12/19/24 min.

