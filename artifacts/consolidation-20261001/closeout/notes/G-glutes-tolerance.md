# Paquete G · B-02 · Tolerancia blanda de glúteos (límite 16, techo 17,5)

Estado: **DONE** (B-02 cerrado). Solo flavor Base. Sin emulador, sin cambios de Room/versión, git solo lectura.

## Qué se hizo
- Segunda franja de severidad para glúteos en **planes propios**: ≤ 16 normal · 16 < x ≤ 17,5 «volumen alto» (SOFT, permitido con aviso) · > 17,5 HARD/COMPOSITION como antes. No se tocó `VolumeLandmarks.kt` ni `VolumeCalibrationEngine.kt`; ningún otro músculo ni plan no propio (PHUL/PHAT, legacy) tiene tolerancia.
- Modo «último recurso»: la cascada de §12.3 no cambió (sigue guiada por el MRV = 16). Solo al final, si lo único que queda es glúteos entre 16 y 17,5, el plan se entrega con aviso en vez de COMPOSITION.
- Mismo contador: `SessionCompositionPolicy.weeklyGroupSets(week, metadata)` (nuevo, público; extraído del bucle de `evaluateWeek` sin cambiar su lógica) lo usan la política y el ajustador. El ajustador mide glúteos con `max(VolumeCalculator, contador de la política)`. Verificado sobre las 523 configuraciones del catálogo: ambos contadores coinciden salvo 16 (deadlift/RDL unilaterales y abducciones de cadera, que ningún plan propio usa).
- Texto «> MRV» conservado en los hallazgos (SOFT y HARD), así que el filtro del ajustador (`attemptFit`) sigue igual.
- Aviso para la UI (B-03, ola 2): `PersonalizationReport.highVolume: List<HighVolumeNotice>` (músculo, series máximas de la semana, recomendado 16, techo 17,5) y `HighVolumeNotice.message` = «Glúteos 17 series (recomendado 16, tolerancia hasta 17,5)» (sin jerga). La misma frase se añade a `report.limitations` y a la descripción del programa. **B-03 debe leer `previewReport.highVolume`** (no `muscles`: este último mide solo la semana 1, el aviso usa el máximo semanal).
- Extra neutral de rendimiento: la tabla de músculos del catálogo se arma una vez por plan (antes en cada semana de cada intento del ajustador).

## Archivos tocados (ruta relativa al repo)
- `android-native/app/src/main/java/com/example/kpkn/domain/training/VolumeSoftBand.kt` — **creado** (`GLUTES_SOFT_BAND = 1.5`, `VolumeBand`, `HighVolumeNotice`, `VolumeSoftBand`; dominio puro).
- `.../domain/training/SessionCompositionPolicy.kt` — modificado (bloque W2/MRV ~l.754-777: SOFT en banda, HARD encima; `tallyWeek` + `weeklyGroupSets` l.650-692). Nota: el archivo tenía fin de línea mixto y el editor lo dejó todo en CRLF.
- `.../domain/training/SimpleCyclePersonalizer.kt` — modificado solo en regiones de volumen/ajustador: constructor con `glutesSoftBand` (l.140-154), `PersonalizationReport.highVolume` (l.115-120), `NativeFitAttempt.overSoftCeiling/highVolume` (l.640-650), `weeklySetsBySession`/`softCeilingOf`/`attemptFit` (l.929-1028), rechazo y nota (l.1201-1215), comprobación final y reporte (l.1250-1276). No se tocó nativeProgression ni ids de día/slot.
- Pruebas: `.../test/.../domain/training/SessionCompositionPolicyTest.kt` (modificado), `PlanGenerationCoverageT006Test.kt` (modificado), `NativeProfileRecipeAndFitterTest.kt` (modificado), `VolumeSoftBandTest.kt` (**creado**).
- `glutesSoftBand` en el constructor es un seam de pruebas: producción usa el valor por defecto; `require(0 ≤ banda ≤ 1,5)`; 0,0 = comportamiento anterior a B-02.

## Pruebas
- `SessionCompositionPolicyTest`: el test HARD existente sube de 17 a 19 series; nuevos `native_glutes_soft_band_is_soft_up_to_the_ceiling_and_hard_above_it` (16 → nada, 17 y 17,5 → SOFT, 18 y 19 → HARD) y `native_soft_band_does_not_extend_to_other_muscles_nor_to_legacy_recipes` (pecho 23 > 22 sigue HARD; legacy sigue SOFT sin marca).
- `VolumeSoftBandTest` (7): fronteras, solo glúteos, límite personal más bajo no se relaja, banda 0 = regla anterior, nota en lenguaje llano sin «MRV/BL/puente H», ruido decimal.
- `NativeProfileRecipeAndFitterTest` (+3, catálogo sintético con +0,5 de glúteo por serie): Músculo corporal 6d principiante 15,5 → 16,5: sin banda (banda 0) = COMPOSITION; con banda se entrega con aviso, el gemelo opcional se retira antes (la cascada no se detiene), política SOFT y mismo número; 18,5 → COMPOSITION con y sin banda; el ajustador rechaza una holgura > 1,5.
- `PlanGenerationCoverageT006Test`: Q2 ahora cuenta `rowsInSoftBand` y `highVolumeNotices` (aserto == 0); nuevo `Q2_calibrated_pass_changes_no_viable_row` (2.304 filas × {sin calibrar, piso, techo de `VolumeCalibrationEngine`} × {tolerancia apagada, encendida}; exige programa e informe IGUALES en toda fila viable antes, mismo motivo/minutos en las rechazadas, 0 HARD y política↔aviso coherentes).

## Cifras antes / después
«Antes» = g24 (Q1/Q2/Q3) y g27 (matriz, tras arreglo de la fila F); en la pasada calibrada «antes» = el mismo generador con banda 0,0 medido en la misma corrida.

| | Antes | Después (G-tests-1) |
|---|---|---|
| Q1 | 62.208 entradas · 497.664 candidatos · 0 filas vacías | idéntico |
| Q2 (sin calibrar) | 2.304 · viables 1.224 · APPARATUS_ABSENT 360 · TIME_BUDGET 504 · PROFILE_MISMATCH 216 · negativos físicos/tiempo 1.080 · 0 COMPOSITION | idéntico + `rowsInSoftBand=0` `highVolumeNotices=0` |
| Q2 requeridos | 58/58 | 58/58 |
| Q3 | 328 · viables 310 · TIME_BUDGET 18 · 3 bordes (MUSCLE/E2/3d@28, POWERBUILDING/E2/3d@23, ATHLETE/E0/3d@25) · cardio {30=10, 45=15, 59=15, 60=20} | idéntico |
| Matriz T-019 | 17 tests, 0 fallos (g27): A 12, A20 4 TIME_BUDGET, A21 4 (borde 20/21 min), B 6, C 2, D 2, E 1, F 2 (1+1), T001 1, T027 3, Q4 1+1+4 | idéntico |

Pasada calibrada (`[T006][Q2-cal]`, 2.304 filas cada una; la calibración deja el MRV de glúteos en `min(≥18, 16)` = 16):

| Escenario | Viables antes → después | Filas viables que cambian | Filas rescatadas por la banda (en banda) | Rechazos antes = después |
|---|---|---|---|---|
| sin calibrar | 1.224 → 1.224 | **0** | 0 | APPARATUS 360 · PROFILE 216 · TIME_BUDGET 504 |
| piso (POWERLIFTER 1,1,1,1) | 1.224 → 1.224 | **0** | 0 | ídem |
| techo (BODYBUILDER 3,3,3,3) | 1.224 → 1.224 | **0** | 0 | ídem |

Resultado: **0 filas viables cambian y 0 filas dependen de la banda**, también con calibración; tampoco aparece COMPOSITION por otros músculos (los MRV personales ≥ piso no las provocan en esta rejilla). La banda hoy es solo un seguro.

## Comandos Gradle
- `G-compile-1.log`: `compileBaseDebugKotlin compileBaseDebugUnitTestKotlin --continue` → exit 0 (todo UP-TO-DATE: la compilación de otro agente, encolada antes, ya incluyó mis fuentes y compiló sin errores).
- `G-tests-1.log` (exit 0, 6 min 47 s): 16 clases, **151 tests, 0 fallos**: SessionCompositionPolicyTest (18), VolumeSoftBandTest (7), NativeProfileRecipeAndFitterTest (32), PlanGenerationCoverageT006Test (5), SetupExecutableAvailabilityMatrixTest, NativeProfileSpecCatalogTest, NativePlanFailureMapperTest, LegacyNativeDurationConsistencyTest, ProgramRecipeValidatorTest, AuthoredRecipeCompositionTest, PersonalizedPlanCatalogTest, OnboardingSplitSelectionTest, EffectiveEquipmentContractTest, PriorityOrderOnlyTest, T004FirstRejectionDiagnosticTest, NativeRirWarmupAndIntegrationTest. Evidencia: `test-evidence/G-targeted/` (XML + summary.json).

## Para verificar el paquete (clases)
`*.SessionCompositionPolicyTest *.VolumeSoftBandTest *.NativeProfileRecipeAndFitterTest *.PlanGenerationCoverageT006Test *.SetupExecutableAvailabilityMatrixTest` (+ las 11 restantes de arriba).

## Riesgos abiertos / decisiones
- Docs: `docs/WIZARD_PLAN_DEVIATIONS.md` (A, B-01) dice «prohibido degradar W2-MRV a SOFT» y «severidad HARD de W2-MRV»; hay que enlazar el anexo de B-02 (tolerancia 16 → 17,5 solo glúteos/planes propios, aprobada por el dueño). Las notas de DEV-r2-01 en `SimpleCyclePersonalizer` (~l.918-921, «MRV real de glúteos (16)», «BL», «puente H») siguen con jerga: las reescribe B-03.
- Cuando un plan queda en banda la cascada ya retiró accesorios opcionales no relacionados con glúteos (comportamiento preexistente del ajustador, no dirigido por músculo).
- El aviso y la nota usan el máximo semanal de glúteos; `MuscleBudgetReport` sigue midiendo la semana 1.
- Fuera del paquete (no tocado): `NativeProfileSpec.kt`, `VolumeLandmarks.kt`, `VolumeCalibrationEngine.kt`, docs.
