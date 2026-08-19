---
flags: [voice, auge]
---

# Plan: Sistema de programación de cardio en 3 modos (Estático · HIIT/SIT · Intervalos)

> **Para el agente ejecutor**: fuente maestra de este plan. Banderas `voice` y `auge` obligatorias: se toca `services/workout/` (parser/handler de voz) y `domain/auge/` (drenaje de rings).
>
> **Principio rector**: el cardio ya tiene motor en vivo funcional (bloques, chart pirámide, TTS de transición). Este plan **reutiliza** ese motor; los tres modos son tres formas de *generar y editar* el mismo modelo, más dos capas nuevas: señales de ejecución (beeps/vibración/keep-awake) y drenaje AUGE científico.

## Índice

1. Objetivo y decisiones aprobadas
2. Estado actual del código (verificado)
3. Modelo de datos (sin migración Room)
4. Modo 1 — Estático (tiempo/distancia + RPE programado)
5. Modo 2 — HIIT/SIT (guía completa de 5 capas)
6. Modo 3 — Intervalos estilo trotadora
7. Sesión en vivo (señales, saltos, auto-cortes, keep-awake)
8. Modo de voz
9. AUGE / RINGS — modelo de drenaje por modo (ciencia)
10. Paridad iOS / backend
11. Rutas · 12. Impacto · 13. Pruebas · 14. Riesgos
15. Regresiones conocidas · 16. Fases de ejecución · 17. Anexos

---

## 1. Objetivo y decisiones aprobadas

El usuario programa cardio desde el editor de sesión (`CardioEditorCard`) y lo ejecuta en la sesión en vivo (`CardioLiveCard`). Hoy existe un único flujo "tiempo/distancia + slider de intensidad" con un editor de intervalos genérico acoplado. Se requieren **tres modos explícitos** seleccionables por el usuario:

| # | Modo | Qué es |
|---|------|--------|
| 1 | **Tiempo/Distancia (Estático)** | Lo actual: duración, distancia o ambos; intensidad = **RPE programado** (se eliminan los textos "Moderado (Quema grasa)" — el RPE no es una variable de oxidación de grasas). |
| 2 | **HIIT/SIT** | Configurador completo según la guía (5 capas): estructura temporal, métrica objetivo (tiempo/kcal/distancia), protocolo HIIT vs SIT, tipo de descanso, señales/alertas, presets. Soportado por la sesión en vivo. |
| 3 | **Intervalos** | Estilo pantalla de trotadora: plantillas de patrones (pirámide, escalera, 1:1…) **escaladas a una duración total elegida por el usuario**, con bloques modulables y vista pirámide de lo que viene. |

**Decisiones ya aprobadas por el usuario (no re-preguntar):**

1. ✅ **Añadir las modalidades de cardio necesarias** para soportar la guía (Air Bike, SkiErg, Cinta curva, Trineo) — "si hay que agregar cosas nuevas, hagámoslas".
2. ✅ **Objetivo por kcal**: se consulta el peso del usuario; si el dato no existe, se pide **con alerta previa explicando para qué se necesita**.
3. ✅ **Keep-awake por defecto ON** en HIIT/SIT.
4. ✅ El plan cubre **sesión en vivo + modo de voz** (usable, no solo programable) y **efecto en AUGE/RINGS** con base científica (HIIT/SIT drenan principalmente SNC).

## 2. Estado actual del código (verificado 2026-08-19)

### Modelo (`data/models/Session.kt`)
- `CardioDetails` (líneas 329–361): `type`, `intensity` (enum `CardioIntensity` BAJA/MEDIA/ALTA/MUY_ALTA), `targetDurationSeconds`, `targetDistanceKm`, `requiresGps`, `supportsDistance`, `metBase`, `intensityLevel` (1–10), `intervalBlocks`, `intervalRounds`. **Embebido en el JSON de sesión: no requiere migración Room** (DB v23 se mantiene).
- `CardioIntervalBlock` (367–379): `id`, `type` (WARMUP/WORK/RECOVER/COOLDOWN), `durationSeconds`, `speedKmh`, `inclinePercent`, `rpm`, `watts`, `intensityLevel`.
- `CardioType` (381–390): TREADMILL, ELLIPTICAL, ROW_MACHINE, BIKE_STATIONARY, RUN_OUTDOOR, BIKE_OUTDOOR, WALK, STAIR_CLIMBER.
- Compatibilidad ya testeada: `CardioIntervalsSerializationTest` (JSON viejo decodifica con defaults).

### Catálogo y plantillas (`data/models/`)
- `CardioCatalog.kt`: 8 modalidades con flags `supportsDistance/Speed/Incline/Rpm/Watts` y `requiresGps`.
- `CardioHiitTemplates.kt`: 6 plantillas (Tabata 20/10 ×8, 30/30 ×10, Fartlek, Sprint 8, Z2 con picos…) que **materializan bloques** vía `HiitTemplate.toDetails()`.

### Motores puros (`domain/`)
- `domain/cardio/CardioIntervalEngine.kt`: `expandedBlocks`, `totalSeconds`, `progressAt` (deriva bloque actual por elapsed), tablas MET por velocidad/watts/nivel, labels de bloque.
- `domain/cardio/CardioExecutionRules.kt`: `CardioGuideEngine` (**zonas "Calentamiento/Quema grasa/Aeróbico/Anaeróbico"** — a eliminar), `CardioCalorieTargetEngine`, `CardioTimerEngine` (start/pause/tick/requestConfirmation).
- `domain/calculations/CardioCalorieEngine.kt`: kcal por METs (también por intervalos), factor FC opcional.
- `domain/workout/CardioProgressionEngine.kt`: progresión 10% semanal.

### Editor (`screens/sessioneditor/`)
- `components/CardioEditorCard.kt`: chips Tiempo/Distancia/Ambos; **slider "Intensidad" 1–10 con `intensityZoneDescription()` → "Moderado (Quema grasa)" etc. (líneas 448–455)** — texto a eliminar; toggle GPS; embebe `CardioIntervalsEditor`.
- `components/CardioIntervalsEditor.kt`: switch on/off con seed de bloques, stepper de rondas, lista de `CardioBlockRow` (tipo/duración/velocidad/inclinación/rpm/watts/nivel según catálogo), botón "Plantillas HIIT" → `HiitTemplatePickerDialog`.
- `components/ExerciseEditorCard.kt`: `cardioCollapsedSummary()` (~línea 896) — resumen colapsado en la lista.
- **Usos de `CardioEditorCard`**: editor de sesión (`ExerciseEditorCard.kt:393`) y edición en vivo (`WorkoutStructureSheetsHost.kt:806`). Ambos heredan el cambio de modos gratis.

### Sesión en vivo (`screens/workout/`)
- `CardioLiveCard.kt`: círculo de progreso, `CardioIntervalChart` con elapsed, **tabla de zonas hardcodeada "Calentamiento/Quema grasa/…" (líneas 179–219)** — a eliminar; GPS; registro manual.
- `CardioIntervalChart.kt`: pirámide de bloques expandidos, pasado/actual/futuro, "Ahora/Siguiente".
- `WorkoutViewModel.kt`: `startCardioTimer` (3266) + `launchCardioTimerJob` (3293) con **TTS de transición vía `speakAnnouncement` (3308–3327)** — canal correcto, mantener; `recordCardioSet` (3474) guarda `CompletedSet(timeSeconds, distanceKm, avgHeartRate, calories, rpe = intensity.defaultRpe)` — RPE en bucket del enum, no el nivel exacto; `currentBodyWeight()` (1005) = `repository.settings.value.userVitals.weight`; `liveDrainSummary()` (1736) → (SNC%, muscular%, espinal%).

### Voz
- Parser (`services/workout/WorkoutVoiceCommandParser.kt:511–516`): `StartCardio`/`FinishCardio`. Handler (`WorkoutVoiceCommandHandler.kt:646–653`): inicia/finaliza con feedback hablado. Entrada de datos por voz ya soporta "20 minutos 5 kilometros fc 150".
- **No existen**: pausar/reanudar cardio, saltar/completar bloque, consulta de estado de intervalo, cues cortos HIIT.

### AUGE (`domain/auge/`)
- Tres RINGS con tanques personalizados (base SNC 250 / Muscular 300 / Espinal 4000 × multiplicador por tipo de atleta).
- `calculateSetBatteryDrain(...)`; `calculateRpeMultiplier(rpe) = 1 + (rpe/10)^4.2` (cap 2.3).
- **`isSetEffective` excluye el cardio** (`reps<=0 && weight<=0 && hasTime → false`, ~línea 239). → **Hoy el cardio drena 0% en todos los RINGS** (vivo, recovery desde logs, interferencia).
- `SessionEditorAugeComputation.kt`: **cero menciones a cardio** — la predicción de rings al programar lo ignora.
- `InterferenceEngine.kt`: interferencia por músculo con media-vida (24/48/72/96 h); cardio excluido.

### Infra reutilizable para señales
- `services/workout/WorkoutRestAlertManager.kt`: `playToneSequenceAsync` (AudioTrack PCM generado, sin assets) + `VibrationEffect`. Patrón a replicar en un player nuevo propio (§7) para **no tocar services/workout** más que parser/handler de voz.
- No hay keep-awake en la app hoy.

## 3. Modelo de datos (sin migración Room)

Todo campo nuevo lleva **default** → el JSON de sesiones antiguas decodifica sin cambios (patrón ya validado por `CardioIntervalsSerializationTest`). Room DB se mantiene en v23.

### 3.1 Modo derivado (no almacenado)

```kotlin
enum class CardioProgramMode { STEADY, HIIT_SIT, INTERVALS }
```

`CardioDetails.programMode()` **deriva** el modo, nunca se persiste:

- `hiit != null` → `HIIT_SIT`
- `intervalBlocks.isNotEmpty()` → `INTERVALS` (las sesiones con intervalos actuales caen aquí automáticamente)
- si no → `STEADY`

**Por qué derivado y no campo**: elimina toda posibilidad de dessincronización modo↔contenido y da migración gratis. Cambiar de modo en el editor es una operación explícita que transforma el estado (§4–§6).

### 3.2 Configuración HIIT/SIT

```kotlin
enum class HiitWorkTarget { TIME, KCAL, DISTANCE }
enum class HiitProtocol { HIIT, SIT }
enum class HiitRestNature { ACTIVE, PASSIVE }

@Serializable
data class CardioHiitConfig(
    // Capa 1 — estructura temporal
    val warmupSeconds: Int = 180,            // 0 = omitir
    val workSeconds: Int = 30,
    val restSeconds: Int = 60,
    val rounds: Int = 8,
    val sets: Int = 1,                       // bloques/series complejas (avanzado)
    val restBetweenSetsSeconds: Int = 120,
    val cooldownSeconds: Int = 120,          // 0 = omitir
    // Capa 2 — métrica objetivo del trabajo
    val workTargetType: HiitWorkTarget = HiitWorkTarget.TIME,
    val workTargetValue: Double? = null,     // kcal o metros según workTargetType
    // Capa 3 — intensidad y descanso
    val protocol: HiitProtocol = HiitProtocol.HIIT,
    val targetRpe: Double = 9.0,             // HIIT: 8.0–9.5; SIT fuerza 10 (all-out)
    val restNature: HiitRestNature = HiitRestNature.ACTIVE,
    // Capa 4 — señales y alertas
    val beepsEnabled: Boolean = true,
    val voiceCuesEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,        // aprobado: default ON
)
```

Campo nuevo en `CardioDetails`: `val hiit: CardioHiitConfig? = null`.

**Doble representación deliberada**: `hiit` es la fuente de verdad para *editar*; los `intervalBlocks` materializados son la fuente de verdad para *ejecutar* (motor, chart, TTS, kcal sin cambios de modelo). En modo HIIT_SIT el editor de bloques crudos se oculta → no hay drift. Cada cambio en la config regenera los bloques vía builder puro (§5.3).

### 3.3 Campos nuevos en `CardioIntervalBlock`

```kotlin
val targetKcal: Double? = null,           // corte del bloque al alcanzar X kcal
val targetDistanceMeters: Double? = null, // corte del bloque al alcanzar X metros
```

Con `workTargetType != TIME`, `durationSeconds` queda como **tope de seguridad** (cap de tiempo) y estimación para el chart.

### 3.4 Nuevas modalidades (`CardioType` + `CardioCatalog`)

Añadir a `CardioType`: `AIR_BIKE`, `SKI_ERG`, `CURVED_TREADMILL`, `SLED`. Entradas de catálogo:

| id | nombre | supportsSpeed | supportsDistance | supportsRpm | supportsWatts | notas |
|----|--------|---------------|------------------|-------------|---------------|-------|
| `cardio_air_bike` | Air Bike | false | false | true | true | Reina del HIIT; kcal visibles en consola |
| `cardio_ski_erg` | SkiErg | false | true | false | true | Concept2 mide metros |
| `cardio_curved_treadmill` | Cinta curva | true | true | false | false | Manual, ideal sprints |
| `cardio_sled` | Trineo (sled) | false | true | false | false | Distancia corta; en estático el campo km queda incómodo → aceptable (0.05 km) o solo tiempo |

**Toques en cadena obligatorios** (el compilador marca los `when` exhaustivos): `cardioTypeLabel()` en `CardioEditorCard.kt`, `CardioLiveCard` (`showsCadence`, texto header), tablas MET de `CardioIntervalEngine` (`speedToMet`/`wattsToMet`/`levelToMet`) y `CardioCalorieEngine.defaultMet`, `ExerciseEditorCard.cardioCollapsedSummary()`, aliases de voz si aplica. Valores MET de referencia en §17.3.

### 3.5 RPE exacto al registrar

En `WorkoutViewModel.recordCardioSet` (3474): `rpe = details.intensity.defaultRpe` pasa a `rpe = details.resolvedIntensityLevel().toDouble()` (o `hiit.targetRpe` si hay config) → drenaje y progresión usan el RPE programado exacto, no el bucket.

## 4. Modo 1 — Estático (tiempo/distancia + RPE programado)

Es el flujo actual con corrección semántica. En `CardioEditorCard` el contenido del modo STEADY es: chips Tiempo/Distancia/Ambos (sin cambios), campos de duración/distancia (sin cambios), y el slider pasa a ser **RPE programado**:

- Etiqueta: `"RPE programado"`; valor: `"RPE X/10 · <ancla>"` con anclas honestas tipo Borg CR10, sin claims metabólicos:

| Nivel | Ancla |
|-------|-------|
| 1–2 | Muy suave |
| 3–4 | Suave |
| 5–6 | Algo duro |
| 7–8 | Duro |
| 9 | Muy duro |
| 10 | Máximo |

- **Eliminar** `intensityZoneDescription()` (CardioEditorCard.kt:448–455) y todo texto "Quema grasa / zona".
- El slider sigue escribiendo `intensityLevel` + `intensity = CardioIntensity.fromLevel(level)` (el enum se mantiene internamente: lo consumen MET y progresión).
- `CardioGuideEngine` (CardioExecutionRules.kt:19–48) se reescribe como guía por RPE: `CardioGuide(rpeTarget: Int, hrPercentRef: String?, cadenceRef: String?)` — FC% y RPM solo como referencia opcional, **sin nombres de zona**. Actualizar `CardioGuideTest`.
- `CardioLiveCard`: eliminar la tabla de zonas (líneas 179–219); en su lugar una línea: `"Objetivo: RPE X/10 · <ancla>"` (+ HR% referencial si se decide mantener).

## 5. Modo 2 — HIIT/SIT

### 5.1 Editor (`screens/sessioneditor/components/CardioHiitEditor.kt`, nuevo)

Secciones en orden (todas reescriben la config y regeneran bloques en caliente):

1. **Presets** (fila de chips + botón "Más"): cargan un preset de `CardioHiitTemplates` en la config (editable después). Catálogo ampliado con los presets de la guía (§17.1): Micro-SIT Aláctico 10s/60s ×8 (SIT), Tabata 20/10 ×8 (HIIT), Wingate 30s/240s ×4 (SIT), HIIT 30/30 ×10 (HIIT), 1 min ON/OFF ×8 (HIIT). `HiitTemplate` gana `protocol: HiitProtocol` y pasa a construir **`CardioHiitConfig`** (no solo bloques).
2. **Protocolo**: segmented HIIT / SIT. SIT bloquea `targetRpe = 10` y muestra "All-out"; HIIT muestra slider RPE 8.0–9.5 (paso 0.5).
3. **Estructura temporal**: calentamiento (min:seg + toggle Omitir), trabajo (seg), descanso (seg), rondas (stepper 1–20), sección **Avanzado** colapsada: bloques/sets (1–5) + descanso inter-bloque, vuelta a la calma (min:seg + toggle Omitir).
4. **Objetivo del trabajo**: chips Tiempo / kcal / Distancia. kcal/distancia habilitan campo numérico y marcan cada bloque WORK con `targetKcal`/`targetDistanceMeters`. Si `KCAL` y no hay peso en perfil → **diálogo de alerta** (§5.2).
5. **Descanso**: chips Activo / Pasivo (informativo + etiqueta en cues TTS: "descanso activo, muévete suave" vs "alto total").
6. **Señales**: toggles Beeps (3-2-1 + cambio de fase), Voz (cues), Vibración, Pantalla encendida.
7. **Preview permanente**: `CardioIntervalChart` (ya existe) + línea `"X min totales · Y bloques · SNC +a% · Musc +b% · Esp +c%"` (drenaje predicho, §9.5).

### 5.2 Alerta de peso corporal (aprobada)

Al elegir objetivo kcal sin `settings.userVitals.weight`: `KpknAlertDialog` — título "Necesitamos tu peso", cuerpo: *"Para cortar los intervalos al alcanzar las kcal objetivo, la app estima las calorías en vivo con tu peso corporal y la intensidad de cada bloque (METs). Sin este dato el objetivo por kcal no puede medirse."* Campo de entrada kg + botones "Guardar en mi perfil" (persiste en settings) y "Solo esta sesión" (valor en memoria del editor). Si cancela → `workTargetType` vuelve a TIME.

### 5.3 Builder puro (`domain/cardio/CardioHiitProgramBuilder.kt`, nuevo)

`fun build(config: CardioHiitConfig, type: CardioType): List<CardioIntervalBlock>`:

- warmup (si >0) → `repeat(sets) { repeat(rounds) { WORK + RECOVER } + RECOVER_INTER_SET (si sets>1 y no es el último set) }` + cooldown (si >0).
- Bloque WORK: `intensityLevel = targetRpe.roundToInt()`, `targetKcal`/`targetDistanceMeters` si aplica, velocidad sugerida por modalidad solo como default editable (reusar heurística de `HiitTemplate`).
- Bloque RECOVER: `intensityLevel` 2–3 (activo) o sin métrica (pasivo); el inter-set es RECOVER con la duración `restBetweenSetsSeconds`.
- `intervalRounds = 1` siempre (la repetición ya viene materializada — regresión §15-#1).
- `targetDurationSeconds = suma` (sync obligatorio, patrón ya usado en `CardioIntervalsEditor`).

## 6. Modo 3 — Intervalos estilo trotadora

Réplica de las pantallas de intervalos de trotadoras/caminadoras: el usuario elige **duración total** + **patrón**, y la app genera los bloques escalados; después puede modular cada bloque.

### 6.1 Patrones (`data/models/CardioIntervalPrograms.kt`, nuevo)

```kotlin
enum class CardioIntervalPattern { PYRAMID, PYRAMID_INVERSE, LADDER, EVEN_1_1, RATIO_2_1, FARTLEK, CUSTOM }
```

Cada patrón define una **secuencia de unidades relativas** (peso de duración × nivel de intensidad 1–10) que el builder escala:

| Patrón | Unidades (duración rel., nivel) | Descripción corta |
|--------|-------------------------------|-------------------|
| Pirámide | sube 1→2→3→4→5→4→3→2→1 en nivel, duraciones iguales | La clásica de trotadora |
| Pirámide inversa | 5→4→3→2→1→2→3→4→5 | Pico al inicio |
| Escalera | sube 2→3→4→5→5 sostenido | Sin bajada |
| 1:1 constante | trabajo/recuperación iguales (60 s/60 s default) | Intervalos regulares |
| 2:1 | trabajo = 2 × recuperación | Más carga |
| Fartlek | secuencia fija pseudoaleatoria de duraciones/niveles | Libre estructurado |
| Personalizado | — | Bloques manuales (editor actual) |

### 6.2 Builder puro (`domain/cardio/CardioIntervalProgramBuilder.kt`, nuevo)

`fun build(pattern, totalSeconds: Int, type: CardioType, baseLevel: Int): List<CardioIntervalBlock>`:

- Reserva warmup 10% (cap 5 min, mín 2 min) y cooldown 10% (cap 5 min, mín 2 min); el resto se reparte entre las unidades del patrón en proporción a sus pesos, redondeando a 5 s y con **mínimo 15 s por bloque** (si no cabe, reduce niveles/unidades).
- Velocidad por nivel vía tabla `levelToSpeed(type, level)` (inversa aproximada de `speedToMet`) solo cuando `supportsSpeed`; si no, escribe `intensityLevel` (elíptica, escaladora) o `watts` (bici/remo/air bike/ski erg) según catálogo.
- Tras generar, el usuario edita bloque a bloque con `CardioBlockRow` (ya existe) y stepper de rondas; `targetDurationSeconds` se resincroniza en cada cambio (patrón existente).

### 6.3 Editor (`CardioIntervalsEditor.kt` → panel del modo INTERVALS)

- Cabecera: campo "Duración total" (reusa `KpknNativeTimePickerDialog`) + galería horizontal de patrones (chips con mini-descripción) + preview pirámide permanente.
- Debajo: el editor de bloques actual (lista, ↑↓, borrar, añadir, plantillas). **El switch on/off actual desaparece** — entrar al modo Intervalos ya implica intervalos; salir a Estático limpia bloques (confirmación si hay trabajo editado).
- Cambiar duración total con patrón activo → re-escala conservando el patrón (regenera); si el usuario ya editó bloques a mano, la duración total pasa a ser informativa (derivada de la suma) — comportamiento actual.

## 7. Sesión en vivo

### 7.1 Motor de señales (`domain/cardio/CardioCueRules.kt`, nuevo — puro y testeable)

```kotlin
data class CardioCuePlan(
    val countdownBeeps: List<Int>,   // segundos restantes que deben pitar (3,2,1)
    val phaseChangeTone: Boolean,    // tono al cambiar de fase
    val vibration: VibCue?,          // SHORT_TICK / DOUBLE_WORK / LONG_FINISH
    val speech: String?,             // cue TTS si aplica
)
fun transitionCue(prev: Progress?, curr: Progress, hiit: CardioHiitConfig?): CardioCuePlan
fun countdownCue(remainingInBlock: Int, blockType: CardioBlockType, hiit: CardioHiitConfig?): CardioCuePlan
```

- Countdown 3-2-1 en los últimos 3 s de **cada** bloque (beeps cortos); tono agudo al entrar a WORK, grave al entrar a RECOVER/COOLDOWN; fin de sesión: escalera de 3 tonos (reusar frecuencias D5→F5→A5 del rest timer).
- Cues TTS con HIIT: `"¡Sprint! 20 segundos"`, `"Descanso"` (+ "activo"/"alto total"), `"Última ronda"`, `"Bloque N de M"`. En modo INTERVALS sin config HIIT: mantener el anuncio actual "Bloque N: Trabajo a X km/h".
- Todo gateado por `hiit.beepsEnabled/voiceCuesEnabled/vibrationEnabled` + `settings.soundsEnabled` + `voiceController.isEnabled()` (voz). En modo INTERVALS los beeps quedan disponibles con config por defecto.

### 7.2 Player (`services/cardio/CardioCuePlayer.kt`, nuevo)

Replicar el patrón de `WorkoutRestAlertManager.playToneSequenceAsync` (AudioTrack PCM generado, executor mono-hilo) + `Vibrator`/`VibrationEffect`. **No tocar `WorkoutRestAlertManager`.** API: `fun play(plan: CardioCuePlan)`. `services/cardio/` no es zona con compuerta.

### 7.3 Wiring en `WorkoutViewModel.launchCardioTimerJob` (3293)

En cada tick, tras actualizar estado: `prev/curr = progressAt(details, before/after)` (ya existe para TTS) → `transitionCue` + `countdownCue` → `cuePlayer.play(...)`; la parte `speech` sigue yendo por `voiceController.speakAnnouncement` (nunca `speakFeedbackUpdated` — regresión §15-#4).

### 7.4 Salto de bloque y auto-cortes

- `CardioTimerEngine.skipToNextBlock(details, state, nowMs): CardioTimerState` (puro, en `CardioExecutionRules.kt`): fija `elapsedSeconds` al borde final del bloque actual vía `expandedBlocks` (clamp al total; si era el último → AWAITING_CONFIRMATION).
- **Botón "Completar bloque"** en `CardioLiveCard` con intervalos (visible en RUNNING): llama al skip. Fallback universal para targets kcal/distancia sin sensor.
- **Auto-corte kcal**: acumula por tick `kcal += metActual × 3.5 × peso / 200 / 3600`; si el bloque WORK tiene `targetKcal` y lo alcanza → skip automático + cue. Sin peso → no auto-corta (§5.2 ya lo previno) y manda el botón.
- **Auto-corte distancia**: solo con GPS activo (`CardioGpsTracker.state.distanceMeters`, delta dentro del bloque); sin GPS → manual.

### 7.5 Keep-awake

En `WorkoutScreen`: `DisposableEffect` que pone/quita `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` mientras `cardioTimerState.status == RUNNING` y (`details.hiit?.keepScreenOn == true` o intervalos con default ON). Sin permisos.

### 7.6 Live card

- Badge de modo: `"HIIT · RPE 9"` / `"SIT · All-out"` / `"Intervalos"` / nada en estático.
- Con bloque actual: línea de target ("Objetivo: 10 kcal · estimadas 6.2" / "150 m · llevas 90 m (GPS)") + botón "Completar bloque".
- Chart pirámide y "Ahora/Siguiente" ya funcionan — no tocar salvo labels de nuevas modalidades.

## 8. Modo de voz

Objetivo: que HIIT/Intervalos sean **usables sin mirar ni tocar** la pantalla. Se toca `services/workout/` → flag `voice`.

### 8.1 Comandos nuevos (`WorkoutVoiceCommandParser.kt` + `WorkoutVoiceSessionState.kt`)

| Frase (es) | Comando | Acción |
|---|---|---|
| "saltar bloque", "siguiente bloque", "completar bloque", "bloque hecho" | `SkipCardioBlock` | `skipToNextBlock` (§7.4) sobre el cardio activo |
| "pausar cardio" | `PauseCardio` | `pauseCardioTimer()` |
| "reanudar cardio", "continuar cardio" | `ResumeCardio` | reanuda `startCardioTimer` conservando elapsed |
| "cuánto queda de cardio", "qué bloque sigue" | `QueryCardioStatus` | TTS: bloque actual, restante, siguiente, rondas |

**Anti-colisión (obligatorio)**: `"siguiente"` ya navega ejercicios (`SkipExercise`). Los keywords nuevos de bloque solo se evalúan cuando hay contexto de cardio — añadir parámetro `isCardioTimerActive: Boolean` a `parseCommand` (ya recibe flags de contexto como `isRestTimerActive`) y evaluar `SKIP_CARDIO_BLOCK_KEYWORDS` **antes** que `SKIP_SET_KEYWORDS`/`SKIP_KEYWORDS`, únicamente si `isCardioTimerActive`. Tests de parser para ambas ramas.

### 8.2 Handler (`WorkoutVoiceCommandHandler.kt`)

Nuevos `ports`: `skipCardioBlock(): Boolean`, `pauseCardio()`/`resumeCardio(): Boolean`, `cardioStatusSpeech(): String?`. Feedback hablado de confirmación en cada uno (patrón existente: "Bloque saltado. Siguiente: Trabajo a 13 km/h."). `cardioStatusSpeech` se construye desde `CardioIntervalEngine.progressAt` + `CardioCueRules` (mismo formateador que los cues → una sola fuente de frases).

### 8.3 Flujo completo en voz (ya existe + nuevos)

"iniciar cardio" → arranca (existe); durante: beeps/vibración/cues corren solos aunque el micrófono esté en pausa de escucha (los cues TTS usan `speakAnnouncement`, canal de anuncios); "saltar bloque" / "pausar cardio" operativos; "finalizar cardio" registra (existe, usa GPS/timer/FC). Nada en este plan rompe el pipeline de clarificación/stale-grace documentado en MEMORY (ola 1–3 de voz).

## 9. AUGE / RINGS — modelo de drenaje por modo

**Problema actual**: `AugeFatigueEngine.isSetEffective` descarta los sets de cardio (solo tiempo) → el cardio drena **0%** en SNC/Muscular/Espinal, en vivo y en recovery. Con HIIT/SIT esto es fisiológicamente incorrecto: un Wingate ×4 es de las sesiones más fatigantes que existen a nivel sistémico/neural.

### 9.1 Base científica (resumen operativo)

- **SNC**: el trabajo *all-out* (SIT) y RPE ≥ 9 (HIIT) produce fatiga central y neural medible (decremento de activación voluntaria, fatiga de sprint repetido); la demanda crece de forma **no lineal** con la intensidad y con la **densidad** (descansos cortos impiden resíntesis de PCr y clearance de metabolitos). Cardio continuo Z1–Z2 (RPE ≤ 5): coste central mínimo.
- **Muscular**: fatiga periférica local dependiente de modalidad (mapa muscular) × volumen de trabajo (MET·min) × densidad. HIIT con ratio trabajo:descanso alto → más drenaje local por minuto.
- **Espinal/axial**: carga por impacto — carrera > caminata/escaladora > remo > ciclismo/elíptica/air bike/SkiErg ≈ mínimo. Escala con duración e intensidad.

### 9.2 Motor puro (`domain/auge/CardioRingDrainEngine.kt`, nuevo — flag `auge`)

```kotlin
data class CardioRingDrain(
    val cns: Double, val muscular: Double, val spinal: Double,
    val muscleDrains: Map<String, Double>,   // para baterías por músculo e interferencia
)
object CardioRingDrainEngine {
    fun drain(
        details: CardioDetails,
        durationSeconds: Int,
        rpeEffective: Double,     // resolvedIntensityLevel o hiit.targetRpe; lo real si el usuario lo editó
        settings: Settings,       // tanques personalizados
    ): CardioRingDrain
}
```

Constantes y fórmulas exactas en §17.2; propiedades obligatorias (verificadas por test de cotas, patrón `SessionDrainBoundsTest`):

1. **Monotonía en RPE**: mismo tiempo, RPE 9 > RPE 6 > RPE 3 en los tres rings.
2. **SIT > HIIT > continuo** a igual minutos de trabajo: protocolo multiplica SNC (SIT ×1.25) y la densidad trabajo:descanso multiplica SNC y muscular.
3. **Z2 suave casi gratis**: 30 min RPE 4 → SNC ≤ 5%, Muscular ≤ 8%, Espinal ≤ 3% (carrera).
4. **SIT corto pero serio**: Tabata all-out (4 min trabajo + wu/cd) → SNC 8–20%, Muscular 5–15% (piernas si cinta/bici).
5. **Carrera larga**: 45 min RPE 7 → SNC ≤ 12%, Muscular 10–25%, Espinal 3–8% (impacto).
6. **Modalidad**: a igualdad de todo, carrera > bici en Espinal; remo drena Dorsales/Cuádriceps, bici Cuádriceps, carrera Cuádriceps/Isquios/Glúteos/Pantorrillas (mapa §17.4).

### 9.3 Puntos de integración del drenaje

**Precondición de datos**: el drenaje necesita la modalidad y config en el punto de cálculo. Añadir a `CompletedExercise` (`data/models/WorkoutLog.kt`): `val cardioDetails: CardioDetails? = null` (default null → JSON viejo OK). Rellenarlo en `WorkoutViewModel.buildLiveCompletedExercises` (1753) desde `exercise.cardioDetails` y en el builder del log al finalizar sesión. **Checkpoint**: verificar que el `Exercise` persistido en `WorkoutLog` conserva `cardioDetails`; si no, el snapshot nuevo en `CompletedExercise` lo cubre.

1. **Drenaje en vivo** — `AugeFatigueEngine.calculateCompletedSessionDrain`: rama cardio: para cada `CompletedExercise` con `cardioDetails != null` y sets con `timeSeconds > 0`, sumar `CardioRingDrainEngine.drain(...)` (en puntos de tanque, no %). `isSetEffective` **no se toca** (sigue excluyendo cardio del path por serie).
2. **Recovery desde historial** — `AugeRecoveryEngine` (mismo patrón de rama cardio sobre logs). El decaimiento por media-vida ya existe para músculos; el componente SNC/sistémico de cardio decae con la ventana sistémica existente.
3. **Interferencia** — `InterferenceEngine.buildMuscleDrainsFromLog`: incluir `muscleDrains` de cardio (HIIT sprint → Isquios/Pantorrillas residual vs. sesión de piernas del día siguiente).
4. **Predicción en editor** — `SessionEditorAugeComputation`: sumar drenaje predicho de ejercicios cardio a los rings de la sesión en programación.

### 9.4 Visibilidad para el usuario

- Editor HIIT/Intervalos: chips `"SNC +a% · Musc +b% · Esp +c%"` recalculados en caliente con la config en borrador (§5.1-7). Tooltip: "Estimación de fatiga según intensidad, densidad y modalidad".
- En vivo: `liveDrainSummary()` ya agrega — al incluir cardio sube solo.
- Post-sesión: el resumen existente refleja el drenaje real del cardio registrado.

### 9.5 Calibración conservadora

Las constantes (§17.2) se eligen para que el cardio **no compita** con la fuerza en magnitud: una sesión de fuerza dura drena ~70–82% inmediato (recalibración 2026-08-17 documentada en `AugeFatigueEngine`); el cardio típico debe moverse en 3–25% según modo, y solo SIT frecuente debería acercarse a territorio de fuerza en SNC. Los tests de cotas (§13) congelan este contrato.

## 10. Paridad iOS / backend

- iOS **no tiene** modelo de cardio hoy (documentado en `docs/IOS_DEVELOPMENT_PLAN.md` §80–84): los campos nuevos son lenient en lectura (defaults), pero si iOS re-codifica un `Program` pierde claves. Acción: añadir a ese documento las claves nuevas (`hiit`, `targetKcal`, `targetDistanceMeters`, nuevos `CardioType`) y las fórmulas de §17.2 como referencia de paridad futura. **No se porta código Swift en este plan** (no hay cardio en iOS que drenar).
- Backend FastAPI: `backend/engines/` tiene motores espejo (recovery/fatigue). Acción: documentar en `docs/` las fórmulas de drenaje de cardio como contrato; port diferido hasta que el backend consuma cardio (hoy no lo hace).
- Regla del repo: "AUGE changes must preserve behavior across Android, iOS, and backend" → se cumple vía documento de contrato + ausencia de cambio de comportamiento en paths que sí existen en iOS/backend (el cardio no existe allí).

## 11. Rutas

**Modificar — modelo/dominio:**

- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt` — `CardioProgramMode`, `CardioHiitConfig` + enums, `CardioDetails.hiit` + `programMode()`, `CardioIntervalBlock.targetKcal/targetDistanceMeters`, `CardioType` +4 valores.
- `android-native/app/src/main/java/com/example/kpkn/data/models/CardioCatalog.kt` — 4 modalidades nuevas.
- `android-native/app/src/main/java/com/example/kpkn/data/models/CardioHiitTemplates.kt` — `protocol` + presets guía + construir `CardioHiitConfig`.
- `android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt` — `CompletedExercise.cardioDetails`.
- `android-native/app/src/main/java/com/example/kpkn/domain/cardio/CardioExecutionRules.kt` — `CardioGuideEngine` → RPE; `CardioTimerEngine.skipToNextBlock`.
- `android-native/app/src/main/java/com/example/kpkn/domain/cardio/CardioIntervalEngine.kt` — MET/labels nuevas modalidades.
- `android-native/app/src/main/java/com/example/kpkn/domain/calculations/CardioCalorieEngine.kt` — `defaultMet` nuevas modalidades.
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeFatigueEngine.kt` — rama cardio en `calculateCompletedSessionDrain` (flag auge).
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeRecoveryEngine.kt` — rama cardio (flag auge).
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/InterferenceEngine.kt` — muscleDrains de cardio (flag auge).

**Crear — dominio/servicios:**

- `domain/cardio/CardioHiitProgramBuilder.kt`, `domain/cardio/CardioIntervalProgramBuilder.kt`, `domain/cardio/CardioCueRules.kt`, `domain/auge/CardioRingDrainEngine.kt` (flag auge), `data/models/CardioIntervalPrograms.kt`, `services/cardio/CardioCuePlayer.kt`.

**Modificar — UI:**

- `screens/sessioneditor/components/CardioEditorCard.kt` — selector de 3 modos + panel estático con RPE.
- `screens/sessioneditor/components/CardioHiitEditor.kt` (**nuevo**) — §5.1.
- `screens/sessioneditor/components/CardioIntervalsEditor.kt` — panel INTERVALS con patrones (§6.3); sin switch.
- `screens/sessioneditor/components/ExerciseEditorCard.kt` — `cardioCollapsedSummary` mode-aware + labels.
- `screens/sessioneditor/SessionEditorAugeComputation.kt` — drenaje predicho de cardio (§9.3-4).
- `screens/workout/CardioLiveCard.kt` — badge modo, quitar tabla zonas, target de bloque, botón "Completar bloque".
- `screens/workout/WorkoutViewModel.kt` — cues (§7.3), skip, auto-cortes, RPE exacto (§3.5), cardioDetails en completed exercises.
- `screens/workout/WorkoutScreen.kt` — keep-awake (§7.5).

**Modificar — voz (flag voice):**

- `services/workout/WorkoutVoiceSessionState.kt` — 4 comandos nuevos.
- `services/workout/WorkoutVoiceCommandParser.kt` — keywords + `isCardioTimerActive`.
- `screens/workout/WorkoutVoiceCommandHandler.kt` — ports + feedback hablado.

**Docs**: `docs/IOS_DEVELOPMENT_PLAN.md` (paridad), `docs/ARCHITECTURE.md` o doc de cardio si se crea, MEMORY.md (entradas de regresión nuevas, las escribe el Auditor).

## 12. Impacto

- **Room**: sin migración (v23 intacta); cardio embebido en JSON con defaults.
- **JSON**: nuevas claves con default — sesiones viejas leen; `CardioIntervalsSerializationTest` ampliado lo congela.
- **AUGE**: el cardio empieza a drenar rings (antes 0%). Cambio de comportamiento intencional y calibrado por cotas; los paths de fuerza no se tocan.
- **Voz**: keywords nuevos scopeados por contexto; sin cambios en pipeline de clarificación.
- **Batería**: keep-awake solo con cardio RUNNING; beeps por AudioTrack efímero.

## 13. Pruebas

### Nuevos tests (JUnit puros salvo indicado)

- `domain/cardio/CardioHiitProgramBuilderTest.kt` — estructura (warmup omitido/presente, sets + descanso inter-bloque, cooldown omitido), totales exactos (Tabata = 12 min con wu/cd — regresión §15-#1), SIT fuerza `intensityLevel` 10 en WORK, targets kcal/distancia solo en WORK, `intervalRounds == 1`.
- `domain/cardio/CardioIntervalProgramBuilderTest.kt` — cada patrón rellena `totalSeconds` ±10 s, simetría de pirámide, mínimo 15 s/bloque, warmup+cooldown cap 5 min, modalidad sin velocidad usa `intensityLevel`/`watts`.
- `domain/cardio/CardioCueRulesTest.kt` — countdown solo en 3/2/1, tono de fase al cambiar índice, cues HIIT ("¡Sprint!"/"Última ronda"), gating por flags, sin cue en INTERVALS sin config más allá del anuncio estándar.
- `domain/cardio/CardioTimerSkipTest.kt` — `skipToNextBlock` salta al borde exacto, clamp al total, último bloque → AWAITING_CONFIRMATION.
- `domain/auge/CardioRingDrainBoundsTest.kt` — las 6 propiedades de §9.2 (monotonía, SIT>HIIT>continuo, cotas Z2/Tabata/carrera, mapa de modalidad).
- `data/models/CardioProgramModeTest.kt` — derivación de modo (hiit>bloques>estático) y serialización: JSON viejo sin `hiit` decodifica a STEADY; JSON con todos los campos nuevos hace round-trip.
- `services/workout/WorkoutVoiceCardioParserTest.kt` (ampliar) — los 4 comandos nuevos; "siguiente" sin cardio activo sigue siendo `SkipExercise` (anti-colisión); "siguiente bloque" con cardio activo → `SkipCardioBlock`.

### Tests a actualizar

- `CardioGuideTest` — guía por RPE (sin "Quema grasa").
- `CardioHiitTemplatesTest` — presets con `protocol`, duraciones exactas congeladas.
- `CardioIntervalsSerializationTest` — nuevos campos con defaults.
- `SessionEditorCardioSpaceTest` — defaults tras reemplazo de modalidad.
- `CardioCalorieEngineTest` / `CardioCalorieTargetTest` — nuevas modalidades.
- `SessionTemplateCatalogTest` y cualquier `when` exhaustivo sobre `CardioType` (el compilador los marca).

### Comandos de validación (desde `android-native/`)

```
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*Cardio*'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*Auge*' --tests '*Voice*'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "compileBaseDebugKotlin"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "assembleDebug"
```

Orden: tests filtrados tras cada fase → compile → suite completa al final → assemble (ambos flavors). **Nunca** `gradlew.bat` sin `--no-daemon --console=plain` (cuelgue Windows documentado).

## 14. Riesgos

| Riesgo | Mitigación |
|---|---|
| Romper JSON de sesiones existentes | Defaults en todo + test de serialización vieja/nueva |
| Colisión de voz "siguiente bloque" vs navegación | `isCardioTimerActive` + orden de evaluación + tests (§8.1) |
| Sobre-drenaje AUGE de cardio | Cotas calibradas (§9.2, §17.2) + tests de bounds; cardio nunca compite con fuerza |
| Estimación kcal sin sensor real (MET) | Marcada "estimado" en UI; botón "Completar bloque" siempre disponible; peso pedido con explicación |
| Auto-corte distancia sin GPS | Solo con GPS activo; si no, manual |
| Keep-awake olvidado ON | Solo mientras cardio RUNNING; `DisposableEffect` lo retira al salir |
| TTS verbosity | Cues por `speakAnnouncement` (canal gateado), nunca `speakFeedbackUpdated` (regresión §15-#4) |
| iOS pierde campos al re-codificar | Documentado en §10; lenient-read confirmado |

## 15. Regresiones conocidas a respetar (MEMORY.md)

1. **`CardioHiitTemplates.toDetails`**: warmup/cooldown FUERA del `repeat` y `intervalRounds = 1` (Tabata 12 min, no 68). El nuevo `CardioHiitProgramBuilder` hereda esta regla y su test.
2. **`CardioCatalog.supportsSpeed`**: false en bici/remo/elíptica (y nuevas air bike/ski erg) — no mostrar km/h donde no aplica.
3. **`CardioIntervalsEditor`**: borrar el último bloque restaura `targetDurationSeconds = 20*60` (nunca 0 → vivo 00:01). Mantener en el panel nuevo.
4. **TTS de intervalos** por `speakAnnouncement`, no `speakFeedbackUpdated` (bypass de VoiceVerbosity).
5. **SessionEditor**: commits solo al finalizar edición de campos (sin commits fantasma AUGE por onChange).
6. **Voz**: guards de doble-disparo de save y sesión vacía (handler:842, FinishController:132) intactos.

## 16. Fases de ejecución y criterios de aceptación

| Fase | Contenido | Gate de salida |
|------|-----------|----------------|
| 0 | (Flujo con compuertas) plan ya en `.opencode/plans/` con `flags: [voice, auge]` | `request_approval` aceptado |
| 1 | Modelo: §3 completo (enums, config, campos, modalidades, catálogo, MET, labels) + tests de serialización/modo | `*Cardio*Test` verde |
| 2 | Builders puros: `CardioHiitProgramBuilder`, `CardioIntervalProgramBuilder`, presets guía + tests | `CardioHiitProgramBuilderTest`, `CardioIntervalProgramBuilderTest` verdes |
| 3 | AUGE: `CardioRingDrainEngine` + integración (vivo, recovery, interferencia, predicción editor) + `CompletedExercise.cardioDetails` + RPE exacto (§3.5) + tests de cotas | `CardioRingDrainBoundsTest` + `*Auge*` verdes |
| 4 | Editor UI: selector de modos, panel RPE, `CardioHiitEditor` completo, panel Intervalos con patrones, resumen colapsado, preview de rings | `compileBaseDebugKotlin` + `SessionEditorCardioSpaceTest` verde |
| 5 | En vivo: `CardioCueRules`/`CardioCuePlayer`, wiring de ticks, skip + auto-cortes, keep-awake, live card | `*Cardio*Test` + compile verdes |
| 6 | Voz: comandos, handler, cues HIIT | `*Voice*` verde |
| 7 | Docs: paridad iOS/backend, MEMORY (Auditor), suite completa + `assembleDebug` | BUILD SUCCESSFUL |

**Criterios de aceptación funcionales (QA manual post-build):**

1. En el editor, al abrir un cardio se ven los 3 chips de modo y cada uno muestra su panel contextual.
2. Estático: slider muestra "RPE programado X/10 · ancla" y en ningún sitio aparece "quema grasa" (editor ni vivo).
3. HIIT: elegir preset Tabata → 12 min totales; cambiar rondas/trabajo/descanso regenera preview y duración; SIT bloquea RPE 10; activar kcal sin peso muestra la alerta explicativa.
4. Intervalos: elegir Pirámide 20 min → pirámide simétrica visible; editar un bloque actualiza el total.
5. En vivo (HIIT): beeps 3-2-1, tono al cambiar de fase, vibración doble al sprint, pantalla no se apaga, "Completar bloque" salta; voz: "saltar bloque", "pausar cardio", "cuánto queda de cardio" funcionan con cardio activo y no rompen la navegación sin cardio.
6. Tras registrar un HIIT, los rings SNC/Muscular suben (>0%) y el drain predicho del editor coincide en magnitud (±2 pp) con el real.
7. Una sesión creada antes del cambio (JSON viejo) abre y ejecuta igual que antes.

---

## 17. Anexos

### 17.1 Presets HIIT/SIT (de la guía del usuario)

| Preset | Modalidad sugerida | Trabajo | Descanso | Rondas | Protocolo |
|---|---|---|---|---|---|
| Micro-SIT Aláctico | Air Bike / Sled / Cuesta | 10 s | 60 s | 8 | SIT |
| Tabata Tradicional | Air Bike / Bici estática | 20 s | 10 s | 8 | HIIT (RPE 9.5) |
| Wingate Power Test | Bici estática / Air Bike | 30 s | 240 s | 4 | SIT |
| HIIT 30/30 Aeróbico | Remo / Bici / SkiErg | 30 s | 30 s | 10 | HIIT (RPE 9) |
| 1 min ON / 1 min OFF | Remo / Cinta curva | 60 s | 60 s | 8 | HIIT (RPE 8.5) |

Todos con warmup 5 min y cooldown 3 min por defecto (omitibles). Los 6 templates actuales se conservan y ganan `protocol` (Tabata→HIIT, Sprint 8→SIT, Z2 con picos→HIIT suave, etc.).

### 17.2 Fórmulas de drenaje (contrato §9.2)

Notación: `WM` = minutos de trabajo efectivo = Σ WORK×1.0 + RECOVER activo ×0.3 + WARMUP/COOLDOWN ×0.2. `rpeMult` = `AugeFatigueEngine.calculateRpeMultiplier(rpe)` (reuso). `densidad = trabajo / (trabajo + descanso)` de la config HIIT (1.0 si continuo). `protMult` = SIT 1.25 / HIIT 1.0 / continuo 0.6.

```
cnsPoints  = 6.0 × WM × (rpeMult − 1) × protMult × (0.7 + 0.6 × densidad)
musPoints  = 2.2 × METmin × (0.6 + 0.4 × (rpe/10)) × (0.8 + 0.4 × densidad)
spinPoints = 9.0 × impactFactor(type) × minutosTotales × (0.5 + 0.5 × rpe/10)
%ring      = points / tanque(settings) × 100   // tanques de calculatePersonalizedBatteryTanks
```

`METmin` = Σ (MET bloque × min) vía `CardioIntervalEngine.metForBlock` (reuso). `muscleDrains` reparte `musPoints` por el mapa de §17.4 con los pesos de rol (`FATIGUE_ROLE_MULTIPLIERS`) y se escala como `SessionEditorAugeComputation.scaleRawDrains`. Calibrar constantes para cumplir las cotas de §9.2; cualquier ajuste posterior debe mantener esos tests verdes.

### 17.3 METs de referencia para nuevas modalidades

- `AIR_BIKE`: levelToMet (5.0, 8.0, 11.0, 14.0); wattsToMet como bici +1.0 (brazos).
- `SKI_ERG`: levelToMet (5.5, 7.5, 9.5, 12.0); wattsToMet como remo.
- `CURVED_TREADMILL`: como TREADMILL +0.5 MET por tramo (mayor coste, autopropulsada).
- `SLED`: MET fijo por nivel (7.0, 9.0, 11.0, 13.0); sin velocidad.

### 17.4 Mapa muscular por modalidad (para `muscleDrains` e interferencia)

- TREADMILL/RUN_OUTDOOR/CURVED: Cuádriceps (P), Isquiosurales (S), Glúteos (S), Pantorrillas (S).
- WALK: Cuádriceps (P), Pantorrillas (S), Glúteos (S) ×0.6.
- BIKE_STATIONARY/BIKE_OUTDOOR: Cuádriceps (P), Glúteos (S), Pantorrillas (S).
- AIR_BIKE: Cuádriceps (P), Hombros (S), Pectorales (S), Dorsales (S).
- ROW_MACHINE: Dorsales (P), Cuádriceps (P), Bíceps (S), Core (S).
- SKI_ERG: Dorsales (P), Core (S), Tríceps (S).
- ELLIPTICAL: Cuádriceps (P), Glúteos (S) ×0.8.
- STAIR_CLIMBER: Glúteos (P), Cuádriceps (P), Pantorrillas (S).
- SLED: Cuádriceps (P), Glúteos (P), Pantorrillas (S), Core (S).

`impactFactor` (espinal): RUN/CURVED 1.0, WALK 0.5, STAIRS 0.8, SLED 0.6, ROW 0.35, bici/air/ski/elíptica 0.2.

---

*Plan generado 2026-08-19 a partir de la guía `PROGRAMAR SIT_HIIT.txt` y verificación directa del código (rama master, Room v23).*
