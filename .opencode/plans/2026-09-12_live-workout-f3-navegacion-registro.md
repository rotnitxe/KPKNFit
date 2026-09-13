---
flags: []
---

# F3 — Navegación de pasos y registro de series

Depende de F0 (claves) y D1. Cierra: **NAV-03, NAV-04, NAV-05, NAV-06, NAV-07, NAV-08, NAV-09, NAV-10, NAV-11, NAV-12, NAV-13, NAV-14, NAV-15** (NAV-16 P3 si cabe).

## Objetivo

El pager no se queda sordo. La última serie abre finish (no wrap a movilidad). Omitir/skip coherentes. Input numérico no crashea. Drop/RP no marcan done a medias.

## Rutas

| Ítem | Archivos |
|------|----------|
| Pager sticky | `screens/workout/WorkoutV2Body.kt:948-993`, `WorkoutPagerSync.kt` |
| Wrap / finish | `WorkoutStepNavigator.kt:120-170`, `WorkoutViewModel.omitSet` ~3232, `advanceAfterPreparation` |
| Resume | `WorkoutStepNavigator.resolveResumePosition` ~90, `WorkoutSessionHydrator`, `setActiveMode` |
| Unilateral done | `WorkoutViewModel.isSetDone` ~1552, `WorkoutEditingRules`, `WorkoutContinuityComponents.kt:217`, `WorkoutStructureSheetsHost.kt:751`, `WorkoutStepNavigator.isWorkoutStepDone` |
| Técnica | `SetExecutionCard.kt` guided drop/RP, `WorkoutSetRecorder.kt:415-536` |
| Rest kind | `WorkoutSetRecorder.kt:534-678` |
| Validación | `SetExecutionCard.kt:1387-1391,1999-2002,2641`, `data/models/Session.kt` `RepRange` |
| Empty slot | `WorkoutV2Body` páginas vs `WorkoutStepRules.isEmptySlot` |
| Tests | `WorkoutPagerSyncTest`, `WorkoutStepNavigatorResumeTest`, `WorkoutStepNavigatorNavigationTest`, `WorkoutStepRulesTest` |

## Impacto

### NAV-09 — Target programático pegado

`LaunchedEffect(activeSwipePageIndex, ...)`:

```kotlin
pagerSyncCoordinator.beginProgrammaticScroll(activeSwipePageIndex)
try {
    if (activeSwipePageIndex != pagerState.currentPage) {
        pagerState.animateScrollToPage(...)
    }
} finally {
    pagerSyncCoordinator.clearProgrammaticScroll(activeSwipePageIndex)
}
```

Hoy el `finally` solo limpia si `!isActive`. Siempre limpiar. Si no hay animación (`currentPage == target`), igual `clear`. Timeout: si a los 600 ms no settled, `clearProgrammaticScroll()`.

Test nuevo: `beginProgrammaticScroll(2)` sin settle en 2 + gesto usuario en 3 → origin USER y `shouldSyncSettledPagerPage` true. Cubrir el hueco que `WorkoutPagerSyncTest` no cubre.

### NAV-05 — `nextSet` no wrappea a prep

`nextIncompleteStepAfter(includeCurrent=false)` no debe concatenar `steps.take(start)` hacia movilidad/warmup **anteriores**. Wrap solo si `voiceExerciseQueue` no vacío.

Si no hay incompletos **después** del actual → `openFinishSheet()` (mismo criterio que `advanceAfterPreparation`).

Test: última working hecha + movilidad 0 incompleta **antes** → finish, no mobility.

### NAV-10 / NAV-11 — Cursor y omit última

- `resolveResumePosition`: nunca `exercises.size`. Último índice válido o señal `Finished`. `setActiveMode` no escribe `size`.
- Probe de hydrator incluye `omittedSetKeys` / `skippedExerciseIds` (paridad `firstIncompleteStep`).
- `omitSet`: si `firstIncompleteStep == null` → `openFinishSheet()`. No dejar `activeStepKey` null.

### NAV-03 — Toggle bilateral ↔ unilateral

Al cambiar modo en `UnilateralModeSelector` (`WorkoutStructureSheetsHost.kt:982`):

- Bilateral hecho `id_n` → clonar a `_L` y `_R` (mismo CompletedSet) **o** marcar solo el lado default y el otro pendiente. Canon: copiar a **ambos** lados (el usuario ya hizo el slot como uno).
- Unilateral `_L`/`_R` → si ambos existen, colapsar a `id_n` con el lado dominante (mayor trabajo); si uno, ese se vuelve bilateral.

Usar `remap` F0 helpers. No dejar claves viejas sumando volumen.

### NAV-04 — Un `isSetDone`

Extraer `fun Exercise.isSetDone(completed, setIdx)` usando `expectedSidesForSet` (`UnilateralRules`). Reemplazar los 5 callers. Continuity/God Mode dejan de exigir L+R cuando el plan es solo-L.

### NAV-06 / NAV-07

- `skipCurrentSupersetRound`: incluir el paso **actual** (no solo `drop(current+1)`).
- Tras skip de ejercicio, `currentSetIdx` / lado copiados de `nextIncompleteStepAfter`, no 0.

### NAV-08 — Drop / rest-pause

No marcar la serie done ni `nextSet` hasta: (a) sub-series completadas, o (b) el usuario descarta explícitamente (“solo serie principal”). Skip del flujo guiado **no** es (b) silencioso; pide confirmación o deja la serie abierta.

`techniqueStillOpen` no ponga rest=0 sobre la **siguiente** serie: o no avanzar, o rest de la principal.

`CLUSTER_SET` / myo: ocultar o deshabilitar en vivo (copy “próximamente”) si el recorder no los implementa.

### NAV-12 — Rest del step

Usar `currentWorkoutStep.restAfterKind` y `restAfterSeconds`. No `coerceAtLeast(10)` si el plan es 0. Entre lados: `restBetweenSidesSeconds` (0 = no timer, ya). Última serie de sesión: no forzar rest si `openFinishSheet` va a salir; si hay rest planificado, sí.

Re-registro (`wasExistingSet`) no relanza rest (mantener). NAV-14: no auto-regular la serie ya hecha; aplicar factor a la **siguiente incompleta**.

### NAV-13 — Números

- `toDoubleOrNull()` + `isFinite()` + `> 0` (salvo BODYWEIGHT peso 0 y failed).
- Reps: misma normalización `,` → `.` que el peso.
- No construir `RepRange(plannedTarget.toInt())` si `<= 0`; usar `effectiveRepRange()` que ya filtra `targetReps > 0` (`Session.kt:527-528`).
- TIME: UI y recorder de acuerdo (0 no graba salvo failed).

### NAV-15 — Empty slots

El pager no emite páginas `isEmptySlot`. El step machine ya las marca done.

### NAV-16 (P3)

`beginEditingSet` escribe `activeStepKey` de esa serie.

## Pruebas

| Test | Qué |
|------|-----|
| `WorkoutPagerSyncTest` | sticky target: current==target sin animar + swipe USER |
| `WorkoutStepNavigatorResumeTest` | wrap desde última working **no** vuelve a mobility; omit última abre finish (vía VM fake o navigator) |
| `WorkoutStepRulesTest` | empty slot no es página |
| `SetDoneUnilateralTest` | solo-L / L+R / bilateral, un helper |
| `RepRangeSafetyTest` | `plannedTargetV2=0` no throw |

```bash
./gradlew --no-daemon --console=plain testBaseDebugUnitTest --tests '*WorkoutPager*' --tests '*WorkoutStep*' --tests '*SetDone*' --tests '*RepRange*'
```

## Riesgos

- Quitar wrap puede cambiar sesiones de voz con cola (`voiceExerciseQueue`): el wrap **sí** queda para ese caso.
- Copiar bilateral → ambos lados (NAV-03) duplica tonelaje físico; es D1 (2 mitades = 1 lógica, tonelaje suma). Aceptable.
- Finish automático tras omit última: el usuario puede querer seguir añadiendo series; finish sheet se puede cerrar (ya hay hide).

## Fuera de alcance

God Mode undo (F4), persistencia al plan, voz parser, timers FGS.
