---
title: Live workout visual language (roadmap + isthmus)
date: 2026-08-25
flags: []
status: approved
---

# Live workout visual language

## Resumen

Una sola idea visual: roadmap blanco fino continuo sobre negro; istmo que nace hacia tarjeta gris clara; sin marcos redundantes; tarjeta centrada aunque cambie el punto activo. Integrar trabajo parcial existente (`WorkoutLive*`, header, pager, stepper, cards) sin reset/checkout/stash.

## Rutas

- Host: `WorkoutV2Body.kt`, `WorkoutSetPager.kt`, `WorkoutScreen.kt`
- Layout live: `WorkoutLiveStageLayout.kt`, `WorkoutLivePeek.kt`, `WorkoutLiveWorkingPages.kt`, `WorkoutLiveFormat.kt`
- Cards: `SetExecutionCard.kt` (+ controllers/extractos; no rewrap de `SetInputCardV2` / overlays viejos en stage)
- Stepper / dock: `WorkoutRoadmapBar.kt`, `WorkoutCommandDock.kt`, `WorkoutHeaderBar.kt`, `WorkoutUiTokens.kt`
- Sync: `WorkoutPagerSync.kt`, `WorkoutVisualModels.kt`, `WorkoutUiModels.kt`
- Tests: unit (`WorkoutVisualModelsTest`, `WorkoutPagerSyncTest`, métricas live) + Compose androidTest workout + fix `ConceptosClaveUiTest` → `CanonicalKnowledgeOverlay`
- Fuera de alcance: Room, persistence, domain, iOS, backend

## Impacto

- UI live session: lenguaje visual unificado (rail continuo, nodos 23dp/48dp hit, M/A, peek 88–128dp, Haze real en header)
- Gestos: VerticalPager = 1 ejercicio; horizontal = series; bloqueo con teclado/overlays
- Autoridad de estado: `WorkoutUiState` / `activeStepKey` / índices; sync programático vs gesto vía `WorkoutPagerSyncCoordinator`
- Roadmap inferior más discreto; capacidades de grabación/timers/GPS solo en página activa

## Pruebas

- Unit: métricas 1/5/12+ nodos, ventana adaptativa, anclas exactas, M/A, cola +, peek limits, pager stability
- Compose: variantes force/mobility/approach/unilateral/superset/cardio; 48dp; card centrada; ancla; overflow; preview bloqueada; gestos
- `compileBaseDebugKotlin`, `compileBaseDebugAndroidTestKotlin`, androidTests workout, `testBaseDebugUnitTest`, `assembleBaseDebug`
- Install `installBaseDebug` en emulator-5554 (preservar data); force-stop + `MainActivity`; screenshots/UI tree/logcat escenarios DoD

## Riesgos

- Árbol concurrentemente modificado: integrar sobre diffs actuales; no tocar archivos ajenos innecesarios
- Regresión sync pager ↔ `activeStepKey` (no rebind reverse sync a cambios programáticos)
- Preview pages no deben arrancar timers/GPS/`RecordActionHolder`
- Haze: source y effect hermanos; header después del scroll content
- AndroidTest bloqueado por `CanonicalKnowledgeTooltip` obsoleto → actualizar a overlay
- Emulador único `device`; no instalar en físico
