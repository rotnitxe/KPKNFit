# Auditoría — Onboarding de bienvenida + botón "Registrar medidas"

- **Fecha**: 2026-08-18
- **Plan**: `.opencode/plans/2026-08-17_auge-ring-drainage-audit.md` (pipeline en `construction`; sesión de auditoría sin permisos de edición de producto)
- **Alcance del diff auditado**: `MainActivity.kt` (+29), `Settings.kt` (+5), `HomeScreen.kt` (+49), `HomeViewModel.kt` (+98), `BodyProgressScreen.kt` (+20), nuevo `screens/home/components/WelcomeOnboardingOverlay.kt`
- **Evidencia de compilación**: `compileBaseDebugKotlin` → BUILD SUCCESSFUL (sesión previa; working tree sin cambios de producto desde entonces)

## Veredicto

**request_corrections** — 1 hallazgo MEDIUM + 2 LOW corregibles por el Constructor antes del cierre.

## Hallazgos priorizados

### 1. [MEDIUM] Flash del overlay de bienvenida en cada arranque para usuarios con onboarding completado

- **Evidencia**:
  - `data/repository/ProgramRepository.kt:491` — `_settings` inicializa en `Settings()` (→ `onboardingCompleted=false`).
  - `data/repository/ProgramRepository.kt:601` — `loadFromDb()` corre en `scope.launch` (async).
  - `data/repository/ProgramRepository.kt:698` — `_settings.value = settings` se publica recién al final de la carga, en `withContext(Dispatchers.Main)`.
  - `screens/home/HomeViewModel.kt:133` — `show = !settings.onboardingCompleted && !dismissed` → **true durante la carga**, aunque el usuario ya completó el onboarding.
- **Impacto**: en cada arranque, el scrim + tarjeta de bienvenida se muestran ~200–800 ms y luego desaparecen bruscamente. Visible para TODOS los usuarios existentes.
- **Fix recomendado**: agregar `repository.isReady` al `combine` de `onboardingState` (HomeViewModel.kt:125-141) y derivar `show = ready && !settings.onboardingCompleted && !dismissed`. Extraer la derivación a una función pura `onboardingStateFrom(settings, activePlanId, ready, dismissed)` junto al data class `OnboardingState` (HomeViewModel.kt:646-654) para poder testearla. Retrasa ~0.3 s la aparición para el primer uso (imperceptible) y elimina el flash.

### 2. [LOW] El scrim del overlay no consume toques → se puede operar la app "por detrás"

- **Evidencia**: `screens/home/components/WelcomeOnboardingOverlay.kt:57-62` — Box con `background` pero sin `pointerInput`. Los overlays raíz viven en zIndex 100-102 (`MainActivity.kt`), por lo que los toques fuera de la tarjeta atraviesan al `LazyColumn` de Home y a la `NavigationBar` → scroll y cambio de pestaña posibles durante la bienvenida.
- **Nota**: mismo patrón preexistente en `NutritionTodayGlassOverlay`, pero en un flujo modal de primera vez se percibe como bug.
- **Fix recomendado**: en el Box del scrim, `Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }` (los handlers de la tarjeta hija — botones, X, scroll interno — siguen funcionando porque están por encima en el hit-test).

### 3. [LOW] `nameSaved` se pierde al volver del wizard de nutrición

- **Evidencia**: `WelcomeOnboardingOverlay.kt:44-46` — `nameSaved`/`nameInput` son `rememberSaveable` locales del overlay; al navegar al `NutritionWizard` el overlay se desregistra (DisposableEffect en `HomeScreen.kt`) y al regresar se re-crea con `nameSaved=false` → el usuario vuelve a ver el campo de nombre aunque ya lo guardó.
- **Fix recomendado**: inicializar la confirmación desde el estado persistido: `LaunchedEffect(state.displayName) { if (state.displayName.isNotBlank() && state.displayName != "Usuario") nameSaved = true }`.

## Verificaciones OK

- **Migración JSON**: `data/db/Entities.kt:88` (`dbJson.decodeFromString`) con defaults → los 3 flags nuevos (`Settings.kt:11-13`) decodifican en `false` en instalaciones existentes; sin migración Room (DB v23 intacta). ✓
- **Check de nutrición derivado**: `activeNutritionPlanId` (NutritionRepository.kt:727) carga async y converge; con el Fix 1 el desfase queda oculto. ✓
- **Programa del onboarding no se auto-activa** (`addProgram`, ProgramRepository.kt:58-62) — consistente con el copy del overlay y con el flujo normal de creación. No es regresión. ✓
- **Upgrade path**: usuarios existentes ven el onboarding una vez (flags default `false`) — comportamiento decidido; avisar en release notes. ✓
- **Punto de corte `allTasksDone` + delay 1.8 s**: si la app muere durante el delay, al reabrir el overlay muestra los checks y completa — converge. ✓
- **`completeOnboarding` solo con ambas tareas; X = dismiss de sesión** (reaparece al reabrir) — según lo decidido por el usuario. ✓

## Pruebas dirigidas recomendadas (para el Constructor)

Nuevo `app/src/test/java/com/example/kpkn/screens/home/OnboardingStateDerivationTest.kt` sobre la función pura `onboardingStateFrom`:

1. `ready=false` → `show=false` (anti-flash, usuarios completados).
2. `onboardingCompleted=true` + `ready=true` → `show=false`.
3. Primer uso + `ready=true` → `show=true`, tareas falsas, displayName "Usuario".
4. `dismissed=true` → `show=false`.
5. Plan activo (`activePlanId != null`) → `nutritionDone=true`.
6. `onboardingNutritionDone=true` → `nutritionDone=true`.
7. `onboardingProgramDone=true` + plan activo → `allTasksDone=true`.
8. `username="Valen"` → `displayName="Valen"`.

Comando: `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.OnboardingStateDerivationTest'"`.
