# LV-C · Preparación en vivo: aproximación H5 y movilidad H6

**Worktree:** `C:\kw\u11`, rama `feat/screens-v2`. Continúa el trabajo sin commitear del agente anterior (13 archivos modificados, 5 nuevos) y lo termina con sus tres encargos extra.

## Qué se construyó

### H5 · Aproximación en vivo
- `WarmupMobilityLiveCards.kt` (nuevo grueso): tarjeta de aproximación dentro de la tarjeta en vivo de 312 × 425 dp, con fichas de serie de 85 × 76 dp iguales a las efectivas, lista desplazable dentro de la tarjeta y acciones dentro del marco.
- `PrepPhaseLiveLogic.kt` (nuevo): estado de fase de preparación (tipo, paso, descanso) sin tocar el estado global del entrenamiento.
- La base del % de las aproximaciones es la **carga de trabajo de la serie efectiva objetivo**; si falta, la capacidad (1RM). Una sola función: `WarmupLoadBase` (`domain/workout/WarmupLoadBase.kt`), consumida por el ViewModel (`recordWarmupHeavinessAndAwait`), por `WarmupRules` y por el carrusel de preparación del editor.
  - Prueba de la regla: trabajo 100 kg y 1RM 130 → aproximación al 50 % = 50 kg (`WarmupLoadBaseTest`, `WarmupRecordedLoadBaseVmTest`).

### H6 · Movilidad en vivo
- Tarjeta de movilidad con dosis por tiempo o por repeticiones, mismas fichas de 85 × 76 dp, fin de movilidad y mensajes dentro del marco. `WorkoutV2Body` integra ambas tarjetas sin elementos flotantes entre relator, tarjeta y línea de sesión.

### Encargos extra
1. **Voz a 48 dp con 760 dp de alto:** los botones de icono de la cabecera compacta mantienen su dibujo y Compose amplía la zona de toque a 48 dp (`touchBoundsInRoot` medido en `LiveHeaderTouchTargetsTest`, al 100 % y al 130 %; `WorkoutHeaderBar`).
2. **Texto del tope del calibrador:** RAISE y LOWER leen los topes reales de la política (1,05 / 0,92): «La subida se limita al 5 %…» solo cuando el factor quedó recortado; sin tope, «Las cargas suben en la misma proporción…» (`CalibrationEngine.raiseReason/lowerReason`; prueba con tope y sin tope de cada tipo en `CalibrationEngineTest`).
3. **`VoiceFirstUseDeviceTest.awaitChoicesLoaded`:** espera de 5 s → 15 s (androidTest, sin corrida de dispositivo en este paquete).

## Verificación

- Corrida `C:/kw/logs/lvc-7.log`: `:app:testBaseDebugUnitTest` con `WarmupLoadBaseTest`, `WarmupRulesTest`, `CalibrationEngineTest`, `LiveHeaderTouchTargetsTest`, `PrepLiveCardsUiTest`, `WarmupRecordedLoadBaseVmTest` y `LiveSessionPrepRenderSheetTest` → **BUILD SUCCESSFUL**: 8 clases, 99 pruebas, 0 fallos.
- Renders en `C:\kw\shots\lvc-renders\` (77 archivos): H5 y H6 a 880 y 740, al 100 % y al 130 %, con listas largas, descanso y fin de movilidad; medidas `v7-*-medidas.txt` confirman `live-card-frame 312 × 425 dp`.
- `PrepLiveCardsUiTest` (47 pruebas) mide la ley: marco de la tarjeta, fichas de 85 × 76 en aproximación y movilidad, objetivos táctiles ≥ 48 dp y ningún control fuera del contenedor, también a 360 × 740 con letra al 130 %.

## Commits

- `feat(workout): preparación en vivo H5 y H6 con base de aproximación en la carga de trabajo` (código y pruebas).
- `docs(screens-v2): informe de LV-C`.

## Riesgos de integración

- Toca `ExerciseEditorExpanded.kt` y `PreparationCarouselCards.kt` (zona de ED-C, u18): solo para pasar el carrusel del editor a `warmupTargetLoadKg` (la misma regla de base). ED-C debe partir de este cambio al fusionar.
- `SessionRevisionFlowDeviceTest` y `VoiceFirstUseDeviceTest` (androidTest) quedan sin corrida de dispositivo; se prueban en la QA de la fase 5.
- El informe del inventario de recetas en u11 queda desactualizado (dice 108/21); se regenera cuando u13 (INV) se fusione.
