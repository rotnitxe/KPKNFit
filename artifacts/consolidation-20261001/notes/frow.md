# Fila F: rechazo temporal tipado del generador histórico (2026-10-02)

Alcance: solo lectura/edición, sin Gradle/adb/emuladores (no se ejecutó nada). Base: `research/f-row/informe.md`
(§5 tabla 20..30, §6 y §8). Decisión del dueño: no se toca el generador ni su desempate, no hay poda de factibilidad.

## Archivos cambiados

1. `android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt` (CRLF conservado: CR=LF=1940, 0 LF sueltos)
2. `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupExecutableAvailabilityMatrixTest.kt` (LF)
3. `android-native/app/src/test/java/com/example/kpkn/domain/onboarding/NativePlanFailureMapperTest.kt` (LF)

No se tocó UI (`SetupTrainingSteps.kt`), ViewModel, `NativePlanFailureMapper`, build.gradle* ni el catálogo.

## Decisión de diseño

El informe cita «L402-409», que es `SimpleCyclePersonalizer.kt` (no el ViewModel). El único llamador de producción de
`personalize` es `SetupWizardViewModel.materializeProgram` (vía `OnboardingPlanGenerator`), y ese código ya hace
`result.program ?: throw (NativePlanFailureMapper.typedFailure(result.report) ?: SetupCandidateFailureException(...))`.
Por tanto basta con que el informe del personalizador traiga `reasonCode = "TIME_BUDGET"` + `maxSessionMinutes`:
`NativePlanFailureMapper` lo convierte en `PlanMaterializationException(SESSION_DURATION, TIME_BUDGET, requiredMinutes)`,
`PlanCandidateEvaluator` lo devuelve como `Rejected` con `requiredMinutes`, `candidateCache` lo memoriza por
`inputKey|planId`, y `CandidateIncompatibility` ya muestra «Este plan necesita N min por sesión; elegiste M min.» con la
acción «Editar tiempo» (comprobado en `SetupTrainingSteps.kt` L926/936/942: se activa con `reasonCode == TIME_BUDGET`
y `requiredMinutes != null`; la acción de material solo con `needsApparatusConfirmation`, que aquí es false).

Cambios en `SimpleCyclePersonalizer`:

- `personalize(programId, input, options)` conserva firma pública y defaults, y delega en
  `private fun personalizeAt(..., probeMinimumMinutes: Boolean)` (el cuerpo anterior, intacto salvo el punto de salida).
  La llamada pública usa `true`; las pruebas del sondeo usan `false` (nunca se anida un sondeo).
- Local `unavailable(message, reasonCode = null, maxMinutes = null)`: los llamadores existentes (`unavailable(msg)` y el
  lambda hacia `personalizeNative`) compilan igual.
- En la salida «sin sesión equilibrada» (antes L402-409, ahora ~L421-444), ANTES de devolver el `unavailable` heredado:
  `minimumViableMinutes(programId, input, options)`. Si devuelve N, el resultado es `unavailable(..., reasonCode =
  "TIME_BUDGET", maxMinutes = N)` con mensaje «No se puede completar una sesión equilibrada con M min por sesión: el
  mínimo real de este plan con tus respuestas es de N min. Amplía el tiempo o elige otro plan. dia1=2; ...» (variante
  con split: «El split 'X' no permite completar las sesiones con M min…»). Si devuelve null, se mantiene el
  mensaje y SIN razón tipada, es decir el comportamiento de antes (el ViewModel lo clasifica por texto: MATERIAL).
- `minimumViableMinutes` = `firstViableMinutes(input.availableMinutes) { m -> personalizeAt(... input.copy(availableMinutes = m)
  ..., probe = false).program != null }`. Misma generación y mismo `SessionDurationEstimator` que el resultado final (no hay
  otro estimador). Una prueba que lanza `IllegalArgumentException`/`IllegalStateException` (p. ej. `requireExecutable`) cuenta
  como NO viable; `CancellationException` (subtipo de IllegalStateException, se captura primero y se relanza) se propaga.
- `companion object`: `private const val MAX_SESSION_MINUTES = 100` e `internal fun firstViableMinutes(availableMinutes,
  viableAt)`, función pura y testeable:
  - `availableMinutes >= 100` => null sin probar nada.
  - Prueba el techo (100) UNA vez; si no es viable => null (rechazo de material/enfoque: un solo `personalize` extra, no ~80).
  - Si el techo es viable, barre `available+1 until 100` de menor a mayor y corta en el primer viable (mínimo exacto aunque
    la viabilidad no fuera monótona); si ninguno antes, devuelve 100.
- Coste: el sondeo solo se ejecuta cuando el resultado final ya sería «sin candidatos» (rama de salida), no en los éxitos. Coste
  extra por rechazo: 1 (techo) + (mínimo - pedido) generaciones; para la fila F a 20 min son 2 (100 y 21). Los barridos
  Q1/Q3/Q2 de `PlanGenerationCoverageT006Test` usan solo los cuatro planes propios v2 (no pasan por esta rama). Dentro del
  wizard, `candidateCache` evita repetir el sondeo para las mismas entradas. No añadí caché propia en el personalizador (se
  instancia por llamada en el ViewModel, así que sería inútil).

## Tests actualizados / añadidos

`SetupExecutableAvailabilityMatrixTest` (grupo F; el método conserva su nombre `T019_F_musculo_new_todo_el_material_3_dias_20min`
para no romper filtros `--tests`, ahora ejecuta DOS filas):
- `rowsF()`: F-m20 NEGATIVA (`expectedNegativeReason = TIME_BUDGET`, `negativeWitnessPlanId = "native:full-body"`,
  `expectedRequiredMinutes = independentHistoricalFullBodyFloorMinutes()`) y F-m21 POSITIVA (id `F-m$floor`, `minutes = floor`,
  `requiredWitnessPlanId = "native:full-body"`, y por tanto `assertProgramContract` + la aserción existente de que la sesión
  más larga mide exactamente el piso).
- `independentHistoricalFullBodyFloorMinutes()`: oráculo independiente del motor (180 s general + 3 × (60 s setup + 48 s serie
  de 12 reps × 4 s) + 2 familias compuestas × 360 s de aproximaciones (30+60, 30+90, 30+120) = 1224 s => ceil 20,4 = 21).
  Contrasté las constantes con `SessionDurationEstimator`/`calculateSessionTimeBreakdown` (180 s, 60 s, 45 s mínimo/serie con
  4 s por rep, 30 s + `restBetween` por aproximación) y con `presetWarmupDefinitions` (descansos 60/90/120 s para 40/60/80 %).
- KDoc de la clase (apartado «GRUPO F» con el desglose del informe §3-§4, aviso de que es el piso de ESTE desempate y no un
  mínimo físico) y contadores: de 24 a 25 filas (A12, B6, C2, D2, E1, F2). Ajustes de redacción en los comentarios de A, T027 y F.

`NativePlanFailureMapperTest` (nuevos, mismo estilo que el existente; catálogo real vía `CatalogCompositionTestSupport`):
- `theHistoricalFullBodyRejectionAtTwentyMinutesIsATypedTimeBudgetWithItsRealMinimum`: full-body, NEW, 3 días, 11 categorías, 20 min
  => `program == null`, `reasonCode == "TIME_BUDGET"`, `maxSessionMinutes == 21`, `typedFailure` => SESSION_DURATION/TIME_BUDGET/21;
  a 21 min hay programa sin `reasonCode` y cada sesión mide <= 21 con `SessionDurationEstimator`.
- `theSameCombinationWithMaterialThePlanCannotUseStaysAMaterialRejection`: `native:machine-muscle`, 3 días, 20 min, NEW, todas
  las categorías MENOS MÁQUINAS => sin programa, `reasonCode == null`, `maxSessionMinutes == null`, `typedFailure == null` (el
  ViewModel lo clasifica por texto: MATERIAL) y el texto menciona equipo/material. Ver riesgo 2.
- `theMinimumProbeStopsAtTheFirstViableMinuteAfterCheckingTheCeilingOnce` (llamadas [100, 21, 22, 23] => 23).
- `theMinimumProbeDoesNotSweepWhenEvenTheCeilingIsNotViable` (una sola llamada [100] => null): fija «sigue MATERIAL» en la
  rama del L402 sin depender del catálogo.
- `theMinimumProbeHandlesTheEdgesOfTheBudgetRange` (100 => null sin probar; 99 => 100).
- `theMinimumProbePropagatesCancellationInsteadOfSwallowingIt`.

## Comandos de verificación (no ejecutados; desde `android-native/`)

```
./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.domain.onboarding.NativePlanFailureMapperTest"
./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.screens.onboarding.SetupExecutableAvailabilityMatrixTest.T019_F_musculo_new_todo_el_material_3_dias_20min"
```
Regresión relacionada (generador histórico y rechazos):
```
./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.LegacyNativeDurationConsistencyTest" --tests "com.example.kpkn.domain.training.OnboardingSplitSelectionTest" --tests "com.example.kpkn.domain.training.EffectiveEquipmentContractTest" --tests "com.example.kpkn.domain.training.PriorityOrderOnlyTest" --tests "com.example.kpkn.screens.onboarding.SetupExecutableAvailabilityMatrixTest"
```
(Si el proyecto usa `./gradlew test` sin variante, el filtro `--tests` es el mismo.)

## Riesgos

1. Los números 21 de F-m20/F-m21 y del test unitario salen del port validado del informe (8 mediciones coincidentes), no de una
   ejecución. Si el generador a 20 min no cae por la salida «sin sesión equilibrada» o el mínimo real no es 21, fallan
   F-m20 (witness `native:full-body` != 21), F-m21 (`longestMeasured == 21` exige que algún día mida exactamente 21; el port da 21 en
   el día 1) y la primera prueba del mapeador. Es la señal buscada, pero conviene correrlos antes de dar el cambio por bueno.
2. «Material inexistente» del test unitario usa `native:machine-muscle` sin la categoría MÁQUINAS (candidatos vacíos => salida
   temprana «No hay una variante curada…»), no `native:full-body`: con full-body no pude garantizar sin ejecutar un caso que llegue
   al L402 y siga fallando a 100 min. La rama «el techo no es viable => no hay TIME_BUDGET» queda cubierta de forma determinista
   por los tests puros de `firstViableMinutes`.
3. Efecto en las filas A20 y T006-Q4-TimeNegative: los candidatos históricos que antes salían como MATERIAL (por texto) pueden
   salir ahora como TIME_BUDGET con minutos. Por lectura cumplen las aserciones existentes (etapa DURATION, mínimo > presupuesto,
   testigo único `native:muscle-foundation-v2` por `singleOrNull`, el máximo `requiredMinutes` sigue siendo > presupuesto). No
   debería romper, pero conviene correr esos grupos.
4. El sondeo cuesta 1-3 generaciones más por rechazo «sin sesión equilibrada» (cada una reconstruye `toLegacyConfigurationLookup`
   y el proveedor de metadatos). Aceptable en el wizard (caché por entrada); si algún barrido masivo llamara a `personalize`
   histórico miles de veces con combinaciones imposibles, notaría ~2x en esa rama.
5. El mensaje del rechazo temporal ya no contiene «equipo/material» (a propósito); los tests que juzgan la causa por texto solo
   miran etapa MATERIAL, y esta es DURATION. La UI mostrará además, en gris, este texto y el diagnóstico `dia1=2; ...`.
6. Heredado, sin cambio: la poda de factibilidad del informe §6 NO está (decisión del dueño); el 21 sigue dependiendo del
   desempate y un lote futuro de catálogo puede moverlo (el sondeo lo seguirá reportando con el valor real, pero la fila F
   fijaría 21).
