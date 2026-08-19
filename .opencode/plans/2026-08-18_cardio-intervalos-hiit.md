---
flags: [auge]
---

# Plan: Circuitos de cardio programables (intervalos) + HIIT con plantillas

> Orquestador · 2026-08-18 · Solicitud del usuario: programar variaciones de velocidad/tiempo en cardio
> (trotadora, bici estática/exterior, correr afuera, remo, etc.), visualizarlas en vivo como bloques
> verticales, y habilitar HIIT manual o por plantillas con asesoría oficial de diseño.

## Contexto investigado (evidencia)

- El cardio Beta 10 **ya existe**: `CardioDetails` embebido como JSON en la sesión (`data/models/Session.kt:328-346`),
  8 modalidades en `data/models/CardioCatalog.kt:13-23`, editor inline `CardioEditorCard.kt`, vivo en
  `CardioLiveCard.kt` con timer único (`CardioTimerEngine` en `domain/cardio/CardioExecutionRules.kt:63-98` +
  `CardioTimerState` en `data/models/WorkoutLog.kt:249-260`).
- **No existe nada de intervalos/HIIT/circuitos** (grep `HIIT|tabata|interval|circuit` en producto: cero).
- Persistencia: blobs JSON con `dbJson { ignoreUnknownKeys = true; encodeDefaults = true }` (`data/db/Entities.kt:13`)
  → campos nuevos con default = **cero migración Room** (precedente documentado en `Session.kt:324-327` y en las
  migraciones no-op v16/v17, `KpknDatabase.kt:412-434`). Room actual: **v23**.
- El cardio es **un solo step atómico** en vivo (`WorkoutStepRules.kt:238-252`): los bloques deben vivir como
  sub-estado derivado del timer cardio, **sin tocar** `WorkoutStepNavigator`.
- AUGE no procesa cardio hoy (cero menciones en `domain/auge/`), pero hay **riesgo latente**: si un set cardio con
  `timeSeconds` llega a `isSetEffective` (`AugeFatigueEngine.kt:232-239`) drena baterías como "reps sintéticas"
  (20 min = ~240 "reps"). Hoy no llega porque `exercise.sets` está vacío; hay que blindarlo.
- Hay **WIP sin commitear** en cardio (`Session.kt`, `CardioEditorCard.kt`, `CardioLiveCard.kt`,
  `SessionEditorViewModelCardio.kt`, `CardioExecutionRules.kt`, `CardioCalorieEngine.kt`): el plan se construye
  **sobre** ese WIP, no en paralelo.
- iOS no tiene cardio en absoluto (ni `CardioDetails`); SessionEditor iOS es placeholder. Paridad diferible.

## Diseño asesorado (el usuario pidió asesoría HIIT oficial)

### Modelo de bloque

Un **bloque de intervalo** = tramo con tipo + duración + parámetro de intensidad. Todos los campos con default
(retrocompatibilidad JSON garantizada; app vieja ignora la clave `intervalBlocks` completa gracias a
`ignoreUnknownKeys`).

```kotlin
// data/models/Session.kt (junto a CardioDetails) — enums NUEVOS, nunca valores nuevos en enums viejos
@Serializable
enum class CardioBlockType { WARMUP, WORK, RECOVER, COOLDOWN }

@Serializable
data class CardioIntervalBlock(
    val id: String = "",                    // UUID generado en editor
    val type: CardioBlockType = CardioBlockType.WORK,
    val durationSeconds: Int = 60,
    val speedKmh: Double? = null,           // trotadora / correr / bici exterior / caminata
    val inclinePercent: Double? = null,     // trotadora
    val rpm: Int? = null,                   // bici estática / elíptica (SPM en remo)
    val watts: Int? = null,                 // bici estática / remo
    val intensityLevel: Int? = null,        // 1-10 genérico cuando no aplica lo anterior
)

// Dentro de CardioDetails (campos nuevos con default → sin migración):
val intervalBlocks: List<CardioIntervalBlock> = emptyList(),
val intervalRounds: Int = 1,                // repeticiones del circuito completo
```

**Regla de dominio:** si `intervalBlocks.isNotEmpty()` el ejercicio corre en "modo intervalos"; la duración
efectiva = `Σ(durationSeconds) × intervalRounds`. El editor mantiene `targetDurationSeconds` como espejo
denormalizado (mismo patrón que `targetDurationMinutes` hoy, `ExerciseEditorCard.kt:392-406`) para consumidores
legacy. **Prohibido** añadir valores a `CardioType`/`CardioIntensity` (dbJson sin `coerceInputValues`: un valor
nuevo de enum rompe el decode silenciosamente en downgrade/import de backup — evidencia en `Entities.kt:37,48-50`).

### Parámetros por modalidad (guía de edición)

| Modalidad | Parámetro primario | Secundario |
|---|---|---|
| Trotadora | velocidad km/h | inclinación % |
| Correr exterior / caminata | velocidad km/h (informativa, GPS mide) | intensidad 1-10 |
| Bicicleta estática | RPM | vatios (o nivel → intensityLevel) |
| Bicicleta exterior | velocidad km/h (informativa) | intensidad 1-10 |
| Remo | vatios | SPM (campo rpm) |
| Elíptica / escaladora | intensidad 1-10 | RPM |

Se recomienda extender `CardioCatalogItem` con capacidades (`supportsSpeed/Incline/Rpm/Watts`, con defaults)
para que el editor muestre solo los campos aplicables. Cambio de data class con defaults: sin migración.

### Plantillas HIIT iniciales (catálogo estático Kotlin, cero Room)

Catálogo compilado en `data/models/CardioHiitTemplates.kt` (patrón `SESSION_TEMPLATES_SYSTEM`, no assets, no
Room). Cada plantilla: bloques WARMUP/COOLDOWN incluidos, valores por defecto conservadores, todo editable tras
aplicar, y metadatos de modalidades aplicables + nivel (principiante/intermedio/avanzado).

1. **Tabata 20/10** — 20s trabajo máximo + 10s recuperación ×8 rondas (4 min útiles) + calentamiento 5' y vuelta a la calma 3'.
2. **30/30 clásico** — 30s fuerte + 30s suave, 10 rondas.
3. **Pirámide 1-2-3-2-1** — trabajo 1/2/3/2/1 min con recuperación activa igual al trabajo precedente.
4. **Fartlek trotadora** (el caso del usuario) — calentamiento 5' @6 km/h → trote 5' @8 → caminata 2' @5 → correr 3' @11 → trote 3' @8 → correr 2' @12 → caminata 4' @5.
5. **Sprint 8** — 30s sprint + 90s suave ×8 (protocolo popularizado para trotadora/bici).
6. **Z2 con picos** — base aeróbica larga (intensidad 4-5) con picos cortos de 30-60s cada 3-5 min.

Seguridad: las plantillas muestran aviso de calentamiento obligatorio y las intensidades usan la escala 1-10 ya
existente (`intensityLevel`, `CardioIntensity.fromLevel`).

### Motor de ejecución (stateless)

`domain/cardio/CardioIntervalEngine.kt` (nuevo, puro): dado `elapsedSeconds` del `CardioTimerState` existente,
**deriva** bloque actual, progreso intra-bloque, bloque siguiente y lista aplanada por rondas. Sin estado nuevo
que hidratar: `CardioTimerEngine`/`CardioTimerState` actuales no cambian; el total del timer = duración del
circuito. Esto da process-death recovery gratis (la hidratación ya existe, `WorkoutSessionHydrator.kt:154,282`).

## Fases

**F1 — Modelo + dominio (sin UI)**
1. `Session.kt`: `CardioBlockType`, `CardioIntervalBlock`, campos `intervalBlocks`/`intervalRounds` en `CardioDetails`.
2. `data/models/CardioCatalog.kt`: capacidades por modalidad (defaults).
3. `domain/cardio/CardioIntervalEngine.kt` (nuevo): expansión por rondas, `blockAt()`, `nextBlock()`, MET por bloque
   (velocidad→MET por tipo vía compendio estándar; fallback `intensityLevel`→`CardioIntensity.fromLevel`→`defaultMet`).
4. `domain/calculations/Calculations.kt:571-574`: si hay intervalos, `executionSec += Σ bloques × rondas`.
5. `domain/calculations/CardioCalorieEngine.kt`: agregador por bloques (extensión; la fórmula MET no cambia).
6. `data/models/CardioHiitTemplates.kt` (nuevo): las 6 plantillas.

**F2 — Editor (SessionEditor)**
7. `screens/sessioneditor/components/CardioIntervalsEditor.kt` (nuevo), integrado en `CardioEditorCard.kt` como
   modo "Intervalos/HIIT" junto a los chips de objetivo actuales: lista de bloques (chip tipo, duración vía
   `KpknNativeTimePickerDialog`, campos por capacidad de modalidad), añadir/duplicar/eliminar, reordenar con
   botones ↑↓ (**sin drag&drop**: regresión documentada en MEMORY §23), stepper de rondas, mini-preview con el
   mismo chart del vivo.
8. Sheet picker de plantillas HIIT (`HiitTemplatePickerSheet`, patrón `CardioCatalogSheet`) registrado en
   `SessionEditorSheets.kt`; aplicar plantilla = rellenar `intervalBlocks` (IDs nuevos) y quedar editable.
9. Resumen colapsado (`cardioCollapsedSummary`, `ExerciseEditorCard.kt:331`) muestra "HIIT · N bloques · MM:SS".

**F3 — Vivo + hardening AUGE**
10. `screens/workout/CardioIntervalChart.kt` (nuevo): `Canvas` de columnas — ancho ∝ duración, altura ∝ valor
    primario (speedKm/h, si no intensityLevel×10, si no watts normalizados), color semántico por `CardioBlockType`
    en familia verde cardio (`#10B981`, `DEFAULT_CARDIO_PART_COLOR`) con WORK en acento de sesión; bloques pasados
    atenuados, actual con borde + etiqueta "8.0 km/h · 3:24 restantes", fill de progreso intra-bloque.
    Patrón de referencia: `CalorieTrendChart` (`NutritionScreen.kt:1089-1195`).
11. `CardioLiveCard.kt`: si hay intervalos, el chart + tarjeta "bloque actual/siguiente" reemplazan la tabla de
    zonas estática; el anillo de progreso total se mantiene.
12. `WorkoutViewModel.kt`: en el tick existente (`launchCardioTimerJob`, :3292-3310) derivar bloque actual y
    exponerlo en `WorkoutUiState`; anuncio TTS **opcional** en cada transición vía
    `voiceController.speakAnnouncement(...)` (API existente, gateada por `isEnabled()`; **sin tocar
    `services/workout/`**). Cadencia baja: solo en cambio de bloque.
13. Logging: **sin cambios estructurales** — `recordCardioSet` sigue grabando UN `CompletedSet` agregado
    (duración total = circuito). No persistir bloques como sets individuales (riesgo AUGE).
14. **Hardening AUGE** (bandera `auge`): `AugeFatigueEngine.kt:232-239` — `isSetEffective` excluye sets puramente
    cardio (`weight == 0.0 && reps == 0 && timeSeconds > 0`) para que ningún set cardio, presente o futuro, drene
    baterías como reps sintéticas. Una guarda + test de regresión.

**F4 — Documentación y paridad diferida**
15. Actualizar `docs/IOS_DEVELOPMENT_PLAN.md` con la divergencia cardio-intervalos (iOS ni siquiera tiene
    `CardioDetails`); el modelo JSON con defaults no rompe iOS en lectura.
16. Anotar en `docs/CARDIO_GPS_SPIKE.md` o doc de cardio la arquitectura de intervalos (si aplica).

## Rutas

**Modificar:**
- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt` (CardioDetails + nuevas clases)
- `android-native/app/src/main/java/com/example/kpkn/data/models/CardioCatalog.kt` (capacidades)
- `android-native/app/src/main/java/com/example/kpkn/domain/calculations/Calculations.kt` (breakdown con intervalos, :571-574)
- `android-native/app/src/main/java/com/example/kpkn/domain/calculations/CardioCalorieEngine.kt` (agregador por bloques)
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeFatigueEngine.kt` (guarda isSetEffective, :232-239)
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/CardioEditorCard.kt` (modo intervalos)
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/ExerciseEditorCard.kt` (resumen colapsado)
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/SessionEditorSheets.kt` (sheet plantillas)
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/CardioLiveCard.kt` (chart + bloque actual)
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt` (derivación bloque + TTS opcional)
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutUiModels.kt` (estado de progreso de intervalos, si aplica)
- `docs/IOS_DEVELOPMENT_PLAN.md` (nota de divergencia)

**Crear:**
- `android-native/app/src/main/java/com/example/kpkn/domain/cardio/CardioIntervalEngine.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/models/CardioHiitTemplates.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/CardioIntervalsEditor.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/HiitTemplatePickerSheet.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/CardioIntervalChart.kt`
- Tests: `domain/cardio/CardioIntervalEngineTest.kt`, `data/models/CardioIntervalsSerializationTest.kt`,
  `data/models/CardioHiitTemplatesTest.kt`, extensión de `screens/sessioneditor/SessionEditorCardioSpaceTest.kt`,
  caso en tests de `AugeFatigueEngine` (exclusión cardio) y de `Calculations` (breakdown con intervalos).

**NO se toca:** `data/db/`, `app/schemas/` (sin migración; campos con default en JSON lenient),
`services/workout/` (voz usa solo APIs públicas existentes), `ios-native/`, `backend/`.

## Impacto

- **Android:** modelo + dominio puro + SessionEditor + sesión en vivo. Compatible con sesiones existentes
  (defaults; cardio viejo = `intervalBlocks` vacío = comportamiento actual intacto).
- **Room:** sin migración ni bump (v23 se mantiene; precedente del WIP actual y de v16/v17). No requiere bandera
  `room` porque no se edita `data/db/` ni `app/schemas/`.
- **AUGE:** solo la guarda de `isSetEffective` (bandera `auge`). Suite AUGE como verificación de no-regresión.
- **Voz:** anuncios opcionales de transición de bloque desde `WorkoutViewModel` vía `speakAnnouncement` existente;
  ningún archivo de `services/workout/` se edita (sin bandera `voice`).
- **iOS:** sin cambios (paridad diferida y documentada). Riesgo conocido: si iOS re-guarda un `Program` con
  intervalos importado por backup, el round-trip JSON pierde los campos nuevos → mitigación futura: structs
  `Codable` pasivos en iOS (fuera de este plan).
- **Backend:** los campos nuevos viajan en blobs JSON con defaults; parsers lenient los ignoran. Sin edición de
  `backend/` en este plan (verificación diferida en fase de paridad).

## Pruebas

1. **Nuevos unitarios (JVM, sin emulador):**
   - `CardioIntervalEngineTest`: expansión por rondas, `blockAt` en bordes exactos de bloque, elapsed > total,
     derivación stateless (mismo input → mismo output), MET por velocidad vs fallback intensidad.
   - `CardioIntervalsSerializationTest`: JSON viejo sin `intervalBlocks` decodifica con defaults; JSON nuevo es
     ignorado por decoder "viejo" (`ignoreUnknownKeys`); round-trip completo.
   - `CardioHiitTemplatesTest`: toda plantilla tiene WARMUP y COOLDOWN, duraciones > 0, modalidades aplicables
     existen en `CardioCatalog`.
   - Caso `Calculations`: breakdown con intervalos = Σ bloques × rondas.
   - Caso `AugeFatigueEngine`: set cardio (`weight=0, reps=0, timeSeconds>0`) NO es efectivo (regresión).
2. **Comandos (desde raíz del repo):**
   - `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "compileBaseDebugKotlin"`
   - `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.domain.cardio.*'"`
   - `... -Tasks "testBaseDebugUnitTest --tests '*CardioHiitTemplates*' --tests '*CardioIntervalsSerialization*'"`
   - `... -Tasks "testBaseDebugUnitTest --tests '*Auge*'"` (guarda de no-regresión AUGE)
   - `... -Tasks "testBaseDebugUnitTest --tests '*SessionEditorCardio*' --tests '*Calculations*'"`
   - Final: `assembleBaseDebug` antes de `submit_audit`.
3. **Manual (Constructor):** crear espacio de cardio → trotadora → aplicar plantilla Fartlek → editar bloques →
   guardar → abrir sesión en vivo → verificar chart, progreso por bloque, transiciones y registro del set agregado;
   verificar sesión cardio antigua sin intervalos sigue idéntica.

## Riesgos

1. **WIP sin commitear en cardio** (7+ archivos, `git status` al 2026-08-18): el plan se apoya en ese WIP.
   Precondición para el Constructor: integrar/commitear el WIP actual primero y rebasar; no trabajar en paralelo.
2. **Fatiga AUGE fantasma** si bloques llegaran al log como sets individuales (20 min = ~240 "reps" sintéticas).
   Mitigado por diseño (un solo `CompletedSet` agregado, como hoy) + guarda explícita en `isSetEffective` + test.
3. **Enums y downgrade/backup:** añadir valores a `CardioType`/`CardioIntensity` rompería decodes viejos
   (sin `coerceInputValues`). El plan usa enum `CardioBlockType` nuevo dentro de clave nueva — inmune.
4. **Eco TTS / churn de audio** (B6/B15, `docs/AUDITORIA_SISTEMA_VOZ_2026-08.md`): los anuncios de bloque son
   opcionales, solo en transición, vía `speakAnnouncement` (respeta anti-eco y ducking). Si surge eco, se
   desactivan sin tocar el resto.
5. **Timer con pantalla apagada:** el tick vive en `viewModelScope` (igual que el cardio actual); intervalos largos
   en background quedan cubiertos por hidratación de `CardioTimerState`, pero no hay FGS/alarma nueva. Diferido
   conscientemente (patrón de referencia futuro: `RestTimerController`).
6. **Drag&drop de bloques:** prohibido en MVP por la regresión documentada (MEMORY §23); reorden con ↑↓.
7. **iOS round-trip:** pérdida silenciosa de `intervalBlocks` si iOS re-guarda un Program importado. Documentado;
   mitigación (structs pasivos) fuera de alcance.
8. **Estimación calórica por velocidad** depende de tablas MET por km/h (compendio): son aproximaciones públicas
   estándar; el fallback por intensidad 1-10 ya existe si falta velocidad.
