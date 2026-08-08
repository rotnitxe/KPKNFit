# Plan de ejecución — Fix "Aplicar" de Reglas de Sesión
> **Para:** agente implementador. Documento autocontenido: diagnóstico consolidado + cambios exactos + tests + validación.
> **Diagnósticos base:** `reglas-apply-diagnostico-y-plan.md` (Investigación A) y `reglas-y-tiempo.md`, ambas en esta carpeta. Consolidado con una segunda investigación independiente que además **ejecutó los tests del engine sobre el working tree actual** (`testBaseDebugUnitTest --tests *SessionEditorRulesEngine*` → BUILD SUCCESSFUL; `compileBaseDebugKotlin` UP-TO-DATE). El motor funciona; el fallo es de flujo/UX.

## 0. Síntoma del usuario

> "Pongo los valores, presiono 'aplicar' pero NADA se aplica. Las tarjetas de sesión simplemente lo ignoran."

El usuario probó de varias formas (scope global y por grupo; no usa variantes de forma consciente).

## 1. Diagnóstico consolidado (causas, con evidencia)

| # | Defecto | Evidencia (working tree) |
|---|---|---|
| H1 | **No-op silencioso + snackbar de éxito falso.** La sheet se siembra con las medianas de la propia sesión (`SessionEditorViewModel.kt:407-430`), así que lo que el usuario "pone" suele coincidir con lo que las tarjetas ya tienen. Si `applyDefaults` devuelve sesión idéntica, `updateSession` descarta el cambio (`SessionEditorViewModel.kt:556`, `if (transformed == current) return`) pero `SessionEditorScreen.kt:769-779` muestra SIEMPRE "Defaults aplicados a la sesión" (SUCCESS). | CONFIRMADO |
| H2 | **Scope por grupo que no existe en la sesión transformada → no-op garantizado.** Los chips de grupo leen `uiState.session?.parts` (= variante A, `RulesSheet.kt:344`) pero `updateSession` transforma la variante activa (`SessionEditorViewModel.kt:550-587`). Idem en TODO el tab TIEMPO (ver lista en Fase 3). | CONFIRMADO estático |
| H3 | **Regresión sin commit:** el working tree revirtió el fix F1 y ahora `restBetweenSidesSeconds = safeSideRest` (borra a 0 el descanso entre lados cuando la regla es 0). | `git diff` `SessionEditorRulesEngine.kt` |
| H4 | **Restos de debug sin commit:** 3 `Log.d("RulesDebug")` + fallback que aplica GLOBAL sin consentimiento cuando el scope no produce cambios (cambia comportamiento, no solo ruido). | `SessionEditorViewModelStructure.kt:594-621` |
| H5/H6 | Por diseño (documentar, no tocar): en superseries el descanso del grupo manda en vivo; `SOLO_RPE` borra `targetReps` y `RM` fuerza `LOAD` en `normalizeSet` (`SessionEditorRulesEngine.kt:252-282`). | — |

Descartado por ambas investigaciones: fallo de render (las tarjetas SÍ se re-sincronizan cuando el modelo cambia: `ExerciseEditorCard.kt:183` para descanso, `SessionEditorFormFields.kt:69-73` para campos de serie) y fallo de engine (tests pasan).

Nota de persistencia (caso borde, fuera de este fix): `persistRecoverableSession` (`SessionEditorViewModel.kt:251-264`) salta el upsert a Room si `weekId` está en blanco.

## 2. Cambios a realizar

**Base:** trabajar sobre el working tree actual. El árbol contiene cambios SIN COMMIT no relacionados (roadmap/swipe en `SessionHero.kt`, `SessionContextNavigator.kt` y su wiring en `SessionEditorScreen.kt:459-515`; `gradlew` con cambio de EOL). **NO tocarlos ni revertirlos.** Solo modificar los archivos indicados abajo.

Rutas base: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn\` (main) y `C:\Users\valen\Documents\KPKNFit\android-native\app\src\test\java\com\example\kpkn\` (test).

### Fase 1+2 — Engine puro: restaurar F1 + evaluación con resultado honesto

**Archivo:** `screens\sessioneditor\SessionEditorRulesEngine.kt`

1. **Restaurar el fix F1** (regresión H3). Estado actual del working tree (~línea 89-92):
```kotlin
return copy(
    sets = nextSets,
    restTime = effectiveRest,
    restBetweenSidesSeconds = safeSideRest,
)
```
Reemplazar por (estado en HEAD, commit `47ab319f`):
```kotlin
return copy(
    sets = nextSets,
    restTime = effectiveRest,
    // F1: no borrar restBetweenSides si la regla está en 0; preservar valor existente
    restBetweenSidesSeconds = if (safeSideRest > 0) safeSideRest else this.restBetweenSidesSeconds,
)
```

2. **Añadir tipo de resultado + función pura de evaluación** en el mismo archivo (el tipo top-level; la función dentro de `object SessionEditorRulesEngine`). Este archivo es Kotlin puro: **NO añadir imports `android.*`**.

```kotlin
/** Resultado honesto de aplicar los defaults de reglas sobre una sesión. */
sealed interface ApplyRulesOutcome {
    data class Applied(val exercisesChanged: Int) : ApplyRulesOutcome
    data object NoChanges : ApplyRulesOutcome
    data class ScopeNotFound(val partId: String) : ApplyRulesOutcome
}
```
(Si `data object` diera problemas con el toolchain, usar `object NoChanges : ApplyRulesOutcome`.)

Dentro de `object SessionEditorRulesEngine`:
```kotlin
/**
 * Evalúa qué produciría [applyDefaults] sin mutar estado.
 * - Scope a grupo inexistente/vacío -> [ApplyRulesOutcome.ScopeNotFound].
 * - Sesión resultante idéntica (o sin ejercicios/grupos realmente cambiados) -> [ApplyRulesOutcome.NoChanges].
 * - En otro caso -> [ApplyRulesOutcome.Applied] con el conteo de ejercicios modificados.
 */
fun evaluateApply(
    session: Session,
    defaults: SessionEditorRuleDefaults,
    partId: String?,
    exerciseIndex: Map<String, ExerciseMuscleInfo> = emptyMap(),
): ApplyRulesOutcome {
    if (partId != null) {
        val part = session.parts.firstOrNull { it.id == partId }
        if (part == null || part.exercises.isEmpty()) return ApplyRulesOutcome.ScopeNotFound(partId)
    }
    val transformed = applyDefaults(session, defaults, partId, exerciseIndex)
    if (transformed == session) return ApplyRulesOutcome.NoChanges
    val exercisesBeforeById = session.allExercises().associateBy { it.id }
    val exercisesChanged = transformed.allExercises().count { ex -> exercisesBeforeById[ex.id] != ex }
    val groupsBeforeById = session.allSupersetGroups().associateBy { it.id }
    val groupsChanged = transformed.allSupersetGroups().count { g -> groupsBeforeById[g.id] != g }
    return if (exercisesChanged == 0 && groupsChanged == 0) {
        ApplyRulesOutcome.NoChanges
    } else {
        ApplyRulesOutcome.Applied(exercisesChanged)
    }
}
```
Nota: `applyDefaults` materializa grupos superset legacy (`session.copy(supersetGroups = updatedGroups)`); comparar vía `allSupersetGroups()` en ambos lados normaliza ese efecto y evita falsos `Applied(0)`.

### Fase 2 — ViewModel: reescribir `applyRuleDefaultsToSession`

**Archivo:** `screens\sessioneditor\SessionEditorViewModelStructure.kt` (líneas 594-621 actuales)

Reemplazar la función completa (esto elimina de paso los logs `RulesDebug` y el fallback global de H4):

```kotlin
fun SessionEditorViewModel.applyRuleDefaultsToSession(partId: String? = null): ApplyRulesOutcome {
    val state = currentUiState
    val target = state.activeVariantSession ?: return ApplyRulesOutcome.NoChanges
    val defaults = getRuleDefaultsForPart(partId)
    val outcome = SessionEditorRulesEngine.evaluateApply(
        session = target,
        defaults = defaults,
        partId = partId,
        exerciseIndex = exerciseIndex,
    )
    if (outcome is ApplyRulesOutcome.Applied) {
        updateSession { session ->
            SessionEditorRulesEngine.applyDefaults(
                session = session,
                defaults = defaults,
                partId = partId,
                exerciseIndex = exerciseIndex,
            )
        }
        closeSheet()
    }
    // En NoChanges / ScopeNotFound la sheet queda abierta para que el usuario ajuste valores.
    return outcome
}
```

Notas:
- El cierre de sheet ocurre SOLO cuando algo se aplicó (antes cerraba siempre). Cambio de comportamiento intencional, parte del fix de feedback.
- `updateSession` ya es variant-aware (`SessionEditorViewModel.kt:550-587`) y re-ejecuta `applyDefaults` sobre la sesión viva (mismo tick, función pura); su guard `transformed == current` sigue protegiendo. No duplicar lógica de variantes.
- Verificar con grep que no haya otros llamadores de `applyRuleDefaultsToSession` (solo `SessionEditorScreen.kt`).

### Fase 2 — Pantalla: snackbar honesto

**Archivo:** `screens\sessioneditor\SessionEditorScreen.kt` (líneas 769-779 actuales)

Reemplazar el bloque `onApplyRules` por:

```kotlin
onApplyRules = { partId ->
    val outcome = viewModel.applyRuleDefaultsToSession(partId)
    scope.launch {
        when (outcome) {
            is ApplyRulesOutcome.Applied -> {
                val target = if (partId == null) "la sesión" else "el grupo"
                val noun = if (outcome.exercisesChanged == 1) "ejercicio" else "ejercicios"
                snackbarHostState.showKpknSnackbar(
                    "Defaults aplicados a $target (${outcome.exercisesChanged} $noun)",
                    SnackbarType.SUCCESS,
                )
            }
            ApplyRulesOutcome.NoChanges -> snackbarHostState.showKpknSnackbar(
                "Sin cambios: las tarjetas ya tienen estos valores",
                SnackbarType.SUGGESTION,
            )
            is ApplyRulesOutcome.ScopeNotFound -> snackbarHostState.showKpknSnackbar(
                "Ese grupo no existe en esta sesión",
                SnackbarType.DANGER,
            )
        }
    }
},
```

- `SnackbarType` tiene exactamente: `SUCCESS, DANGER, ACHIEVEMENT, SUGGESTION` (`ui\components\KpknSnackbar.kt:23`). No inventar `INFO`/`WARNING`.
- Importar `ApplyRulesOutcome` (mismo paquete `screens.sessioneditor`, probablemente no haga falta import explícito).

### Fase 3 — Scope consciente de variante en la sheet REGLAS/TIEMPO

**Archivo:** `screens\sessioneditor\components\sheets\RulesSheet.kt`

La sheet lee la sesión base (variante A) en muchos puntos. Hay un local `session` derivado de `uiState.session` (usado en duración objetivo ~286/292, tab TIEMPO ~743-745, chips de presupuesto ~909, ~990, ~1022, ~1057, ~1084, ~1111, ~1122) y lecturas directas `uiState.session?.parts` (~344, chips de scope).

1. Cambiar el local a la variante activa (mantener la semántica de nulidad existente):
```kotlin
val session = uiState.activeVariantSession ?: uiState.session
```
2. Reemplazar `uiState.session?.parts` (~línea 344, chips "Grupo") por `session?.parts`.
3. Recorrer el archivo y asegurar que TODA lectura estructural (partes, ejercicios, `targetDurationMinutes`, `supersetGroups`) use ese local y no `uiState.session`. Puntos conocidos: ~286, ~292, ~344, ~743, ~909, ~990, ~1022, ~1057, ~1084, ~1111, ~1122. Al terminar, grep `uiState\.session` en el archivo: no deben quedar lecturas estructurales.
4. El `enabled` del botón Aplicar (`enabled = uiState.session != null && !uiState.isApplyingTemplate`) debe usar el mismo local (`session != null`).

No hace falta tocar `SessionEditorViewModelVariants.kt`: `setTargetDuration`, `setPartTargetDuration`, `setExerciseTargetDuration` y `distributeTargetDurationAcrossParts` ya escriben sobre la variante activa vía `updateActiveVariantSession` (líneas 6-58).

### Fase 4 — Tests

**Archivo:** `android-native\app\src\test\java\com\example\kpkn\screens\sessioneditor\SessionEditorRulesEngineTest.kt` (extender el existente; seguir sus convenciones: fixtures `Session`/`Exercise`/`ExerciseSet` + JUnit4).

Añadir 4 tests:

1. `applyDefaults_preserves_rest_between_sides_when_rule_is_zero` — anti-regresión F1: ejercicio con `restBetweenSidesSeconds = 45`, defaults con `betweenSidesRestSeconds = 0` → el resultado conserva 45. Variante del mismo test con `betweenSidesRestSeconds = 30` → escribe 30.
2. `evaluateApply_returns_NoChanges_when_defaults_match_session` — sesión de 1 ejercicio (p.ej. 3 series `targetReps=10`, `targetRPE=8.0`, `intensityMode=RPE`, `restTime=90`, sin `reference1RM` para que `calculateSuggestedLoad` no inyecte peso) + defaults idénticos (`setCount=3, reps=10, rpe=8.0, normalRestSeconds=90, betweenSidesRestSeconds=0`) → `NoChanges`. Si fallara por peso sugerido, ajustar el fixture para que el set ya tenga ese peso.
3. `evaluateApply_returns_ScopeNotFound_for_missing_or_empty_part` — `partId` inexistente y `partId` de parte con 0 ejercicios → `ScopeNotFound`; además `applyDefaults` con ese scope devuelve sesión idéntica.
4. `evaluateApply_returns_Applied_with_changed_exercise_count` — sesión con 2 ejercicios y defaults que cambian reps/rest → `Applied(2)`; verificar que el resultado de `applyDefaults` refleja los nuevos valores (mismo patrón que el test existente `applyDefaults_rewrites_sets_with_target_defaults`).

Opcional (si es de bajo costo): sesión con supersets legacy (`supersetId` en ejercicios, `supersetGroups` vacío) + defaults idénticos → `NoChanges` (cubre la materialización de grupos sin cambio real).

### Fase 5 — Validación (obligatoria, en este orden)

Correr **desde la raíz del repo** (`C:\Users\valen\Documents\KPKNFit`), nunca `gradlew.bat` a secas (el wrapper fuerza `--no-daemon --console=plain`; ver `AGENTS.md`):

```
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*SessionEditorRulesEngine*'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "compileBaseDebugKotlin"
```

Si todo pasa, opcional para dejarlo listo en dispositivo:

```
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "installDebug" -TimeoutSec 900
```

## 3. Criterios de aceptación

- [ ] Aplicar con valores idénticos a los de las tarjetas → snackbar "Sin cambios: las tarjetas ya tienen estos valores" (SUGGESTION), sheet sigue abierta, **ningún** mensaje de éxito falso.
- [ ] Aplicar con valores distintos (global) → tarjetas actualizan series/reps/descanso + snackbar con conteo real; sheet se cierra.
- [ ] Aplicar con scope a grupo válido → solo cambia ese grupo.
- [ ] Aplicar con scope a grupo inexistente/vacío → snackbar de error, nada mutado.
- [ ] Regla "entre lados" en 0 no borra `restBetweenSidesSeconds` existente (test F1 verde).
- [ ] Sin logs `RulesDebug` ni `android.util.Log` nuevos en los archivos tocados (grep = 0 resultados).
- [ ] Tests y compilación verdes (Fase 5).

## 4. Fuera de alcance deliberado (no cambiar sin OK del usuario)

- Aplicar es destructivo por diseño (reescribe series y puede pisar pesos/reps personalizados vía `normalizeSet`/`calculateSuggestedLoad`). Convertirlo en merge no destructivo es decisión de producto.
- H5/H6 (comportamientos por diseño de supersets y modos RM/SOLO_RPE).
- Subsistemas zombie: `SessionEditorRuleLimits` (límites avanzados sin consumidor), `Exercise.timeStrategy`, `ExerciseSet.restBetweenSides` (nivel serie) — ver `reglas-y-tiempo.md`.
- Los cambios sin commit de roadmap/swipe (Hero/Navigator) y el upsert condicional por `weekId` en blanco.
- Paridad iOS/backend: este cambio es UX/editor de Android; no toca lógica AUGE/nutrición/recuperación compartida.
- `SessionEditorScreen.kt` tiene imports duplicados (`sessionBackgroundPresets`/`sessionGradients` x2, solo warning): deduplicar es opcional.

## 5. Convenciones del repo (recordatorio)

- Clean Architecture/MVVM; `domain/` sin `android.*` (el engine de este fix es puro, mantenerlo así).
- ViewModels exponen `StateFlow` read-only; manual DI; feature packages.
- Room v20 es autoridad; este cambio NO toca esquema ni entidades; no regenerar datasets.
- No leer/imprimir `.env`, keystores ni tokens.
- No commitear salvo pedido explícito del usuario (dejar cambios en working tree). Mensaje sugerido si se pide: `fix(reglas): apply honesto sin exito falso, scope variant-aware y restaurar fix F1`.

## 6. Mapa de archivos a tocar

| Archivo | Cambio |
|---|---|
| `screens\sessioneditor\SessionEditorRulesEngine.kt` | Restaurar F1 + `ApplyRulesOutcome` + `evaluateApply` |
| `screens\sessioneditor\SessionEditorViewModelStructure.kt` | Reescribir `applyRuleDefaultsToSession` (quita debug/fallback) |
| `screens\sessioneditor\SessionEditorScreen.kt` | Snackbar honesto en `onApplyRules` |
| `screens\sessioneditor\components\sheets\RulesSheet.kt` | Lecturas desde `activeVariantSession` |
| `app\src\test\...\sessioneditor\SessionEditorRulesEngineTest.kt` | 4 tests nuevos |

## 7. Referencias

- `docs\audits\2026-08-editor-sesiones\reglas-apply-diagnostico-y-plan.md` (diagnóstico Investigación A; sus preguntas abiertas quedaron resueltas: el usuario probó de varias formas; la variante A global con valores distintos funciona — el fallo percibido lo explican H1+H2).
- `docs\audits\2026-08-editor-sesiones\reglas-y-tiempo.md` (auditoría madre del subsistema de reglas).
- Commits clave: `47ab319f` (fix reglas-tiempo original), `69b38297` (variantes; hizo `updateSession` variant-aware).
