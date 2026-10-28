# Auditoría post-implementación de la ola de programas y barrido de duplicados y slop (27–28-oct-2026)

> Rama de trabajo: `fix/program-session-slop` (worktree `C:/kw/progfix`), integrada en `master` al terminar.
> Base auditada: `master` @ `c4491bc83` (la ola de programas T-01…T-124 ya integrada).
> Ejecución: subagentes de Claude Code revisados por el orquestador (desde el 28-oct, solo Sonnet/Haiku/Opus).

## 0. Qué se pidió y qué salió

El usuario pidió dos cosas el 27-oct: comprobar si la implementación de la ola de programas cumple su plan (bugs, cabos sueltos) y barrer las sesiones de **todos** los programas buscando ejercicios duplicados («Sentadilla barra alta» dos veces en la misma sesión) y cualquier otro defecto raro.

La auditoría (Parte I) encontró cinco caminos reales de duplicado y relleno repetido, nombres internos, textos desfasados y nueve cabos sueltos. Al construir la red que barre todos los programas aparecieron más defectos: duplicados en planes nativos, adaptados y originales (DUP-07), 7.388 chips con identificadores internos y dos recetas que nadie podía activar. Todo se corrigió y la red queda en **0 violaciones** con un techo de 0.

### Decisiones del usuario (27-oct)

1. Cuando un protocolo repite el mismo ejercicio, cada aparición tiene **identidad propia rotulada**. Si son variantes, son ejercicios independientes **enlazados** al principal cuando el autor los enlaza («rotular, pero dar identidad propia»).
2. Las variantes sin marca propia reciben **kilos estimados con aviso**.
3. Los accesorios de relleno de KPKN van **como máximo 2 días por semana**, rotando alternativas.

# PARTE I · INFORME DE AUDITORÍA

## 1. Duplicados de ejercicio en una sesión

| id | Gravedad | Causa | Dónde (master) | Ejemplo | Alcance |
|---|---|---|---|---|---|
| DUP-01 | CRÍTICA | La rotación de variantes por fase (T-71) cambia TODO ejercicio «principal» (HEAVY) de la familia sentadilla/banca/peso muerto a la misma variante destino, sin mirar la sesión | `RoutinePeriodizer.kt:334` → `rotateVariant` `:339-357`; `BlockVariantRotation.kt:11-29` | Powerlifting a medida, día de sentadilla (`DisciplineWeeks.plSquat()` `:387-391` = SQUAT MAIN + SQUAT SECONDARY, ambos HEAVY en `MovementPools.kt:796-799`): barra baja + con pausa → en acumulación «Sentadilla barra alta» ×2; en intensificación «Sentadilla con pausa» ×2 | Programas a medida «en bloques» (6 sem) y «periodizados» (12 sem) de Powerlifting (≥ 3 días) y Halterofilia; el periodizado es el recomendado a avanzados |
| DUP-01b | ALTA | Para novato o retorno, el segundo hueco de sentadilla de Powerlifting solo puede llenarse con la otra sentadilla trasera (pausa y frontal son `minLevel INT`); `SessionUse` solo impide repetir configuración y definición | `ExerciseSelector.kt:25-41`, `MovementPools.kt:796-799` | «Sentadilla barra baja» + «Sentadilla barra alta» el mismo día ya en la semana simple | Powerlifting a medida para principiantes, en las tres formas |
| DUP-06 | ALTA | Plantillas de Powerlifting: en el bloque de fuerza el principal de banca pasa a banca inclinada y el suplementario del arquetipo también es banca inclinada; el enlace `supplementalOf` hace que C2 no lo marque | `KpknAdvancedProgramRecipes.kt:129,161` (power-16-4, semanas 6–10), `:185,228` (power-20-5, semanas 7–11, el llamador pasa `t2ConfigurationId = BP_INC`); `DayArchetypes.plBenchHeavy` | Martes: «Press inclinado con barra» 4×4 y otra vez «Press inclinado con barra» 3×6 | Dos plantillas publicadas (anterior a esta ola) |
| DUP-03 | ALTA | El cambio de ejercicio (Program Detail T-73 y asistente) deja elegir un ejercicio que ya está en la sesión; las opciones solo excluyen el propio ejercicio. El cambio del asistente además alimenta la rotación | `ProgramExerciseSwap.kt:39-53`; `ExerciseSwapOptions.optionsFor` `:61-69` y llamadores `ProgramRepository.kt:1574`, `SetupWizardViewModel.kt:1800-1813`, `ExerciseSwapSuggestions.kt:29`; `SetupWeekLayouts.applyExerciseOverrides` `:117-149` | Cambiar la sentadilla con pausa por «barra alta» en un día que ya la tiene | Cualquier programa |
| DUP-02 | MEDIA | La adaptación por material elige el primer sustituto sin excluir lo que ya está en el día; C2 rechaza después la receta, así que no se ve el duplicado: el plan aparece «no disponible» | `PlanAdaptationResolver.pickCandidate` `:671-700`; rechazo en `AuthoredPlanMaterializer.requireComposition` `:229-239` | Sin rack: sentadilla barra alta y con pausa caen las dos a la sentadilla copa → plan descartado | Planes de autor con material parcial: se pierden planes ofrecibles |
| DUP-04 | MEDIA (decisión 1) | Protocolos que repiten el mismo ejercicio por diseño salen como dos tarjetas idénticas; `exerciseDisplayParts` (`ExerciseDisplayName.kt:33-55`) devuelve solo el nombre cuando hay configuración, así que ni siquiera el modificador «Velocidad» se ve | `TexasWendlerProtocols.kt:156-166` (5/3/1 BBB y FSL), `JuggernautSheikoSmolov.kt:203-205,226-228` (Sheiko `a`+`a2`), `:790-791` (Coan: velocidad + pesado), `BodybuildingNativeProtocols.kt:133-134,147-148` (PHAT: banca de velocidad + banca; sentadilla al cajón de velocidad + sentadilla) | «Sentadilla barra baja» seguida de «Sentadilla barra baja» | 6 protocolos publicados |
| DUP-05 | ALTA (proceso) | Ninguna prueba barre sesiones materializadas de todos los programas: `RoutineContractChecks` (que sí falla con «repite ejercicios», `T/.../generator/RoutineContractChecks.kt:57-61`) solo ve la semana base del generador; `RoutinePeriodizerTest` busca con `first {}` y no comprueba unicidad; C2/H4 solo ven recetas y aceptan `supplementalOf` | — | — | Por esto llegaron a master DUP-01/01b/03/06 |

## 2. Otros defectos raros («slop»)

| id | Gravedad | Hallazgo | Dónde |
|---|---|---|---|
| SLOP-01 | ALTA | Relleno repetido: accesorios que añade KPKN (`kpknAssist`, `source = KPKN_DEFAULT`) iguales en todos los días: Pallof 4×/sem (GZCLP, Juggernaut, Texas 4d), GHR 3× (power-20-5, UHF-9, Korte), Face pull 3–4× (Smolov/Smolov Jr con Lat+Face los 4 días, Westside, power-20-5), el mismo trío en los 3 días de Lilliebridge, rueda los 4 días de J&T y Rippler | `ClassicPlProtocols.kt:65-66,267-273,321-329`; `TexasWendlerProtocols.kt:289-291`; `MadcowNsunsGzcl.kt:304,351,460-470`; `JuggernautSheikoSmolov.kt:138-144,208-231,515-516,686-687`; `KpknAdvancedProgramRecipes.kt:199-231` |
| SLOP-02 | ALTA | power-16-4 y power-20-5: peso muerto con déficit 3×/semana en el bloque 1 (T1 jueves + T2 del lunes + T2 del sábado técnico) | `KpknAdvancedProgramRecipes.kt:128,184,199` |
| SLOP-03 | MEDIA | Semana de test (power-16-4 s.16, power-20-5 s.20): tras el test de banca del martes, el viernes «Banca volumen» hace singles al 90/95 %; el sábado «Sentadilla técnica» hace un single al 90 % cinco días después del test de sentadilla | `KpknAdvancedProgramRecipes.kt:151,216-217` |
| SLOP-04 | MEDIA | Etiquetas que no corresponden: body-12-3 «Torso B» es solo empuje (`bbPush`); kpkn-rp-style «Torso B» es solo tirón (`bbPull`): ninguno es un torso | `KpknAdvancedProgramRecipes.kt:285`; `BodybuildingNativeProtocols.kt:286` |
| SLOP-05 | MEDIA | El cardio de los planes propios se materializa con el nombre interno en inglés («walk», «bike stationary»…): `PlanMaterializer.kt:1021` usa `type.name.lowercase()` y la tarjeta cae a `exercise.name` porque no hay configuración de catálogo | `PlanMaterializer.kt:1019-1025`; `ExerciseDisplayName.kt:37-40` |
| SLOP-06 | MEDIA | Ficha de catálogo falsa: «Fuerza por esfuerzo (estilo RTS)» dice que solo la sentadilla va por RPE; la receta (T-13) ya lo hace en los tres | `PlanEditorialTable.kt:1103-1116` |
| SLOP-07 | BAJA | kpkn-rp-style promete «los accesorios sumando una serie por semana» y solo sube el primer accesorio de cada día | `BodybuildingNativeProtocols.kt:264,296-301`; `PlanEditorialTable.kt:1093-1096` |
| SLOP-08 | BAJA | Texto visible «Ciclo pausado al convertir a Avanzado» (se escapó de T-02) | `Program.kt:715` |

## 3. Implementación frente al plan

**Hecho como pedía el plan:** T-01–T-04, T-11, T-14, T-16–T-18, T-20, T-24, T-34, T-40–T-43, T-51–T-53, T-70, T-110, T-111, T-113–T-117, T-121, T-123, T-124 (T-17 y T-18 están con `(T17)`/`(T18)` sin corchetes: commits `4d088a16c`, `9f6823266`).

**Hecho distinto, aceptable** (desviaciones razonadas y documentadas): T-10 GZCLP cambia de etapa al fallar (`ProgressionRule.StagedVolume` + `GzclpProgression.kt`), que es más fiel al método original que las etapas por calendario del plan; T-21/22/23 (elegibilidad más estricta, una serie por grupo por check-in, previsualizar y confirmar); T-30–T-33 (revisión de bloque factual sin diagnóstico de recuperación, AUTO también pide confirmación); T-50; T-73 (previsualizar y confirmar); T-82/T-83 (proyector solo descriptivo); T-91/T-92/T-94 (rangos de planificación propuestos y confirmados en vez de landmarks automáticos); T-100–T-103 (claves por run, módulo propio); T-122.

**Retirado por causa ajena:** T-80/T-81 y la parte AUGE de T-82/T-83. AUGE se retiró de toda la app en la integración POST-RINGS (`2ba0e901f`, 24-oct). Consecuencia: `ProgramRepository.buildTransitionContext` (`:1234-1239`) devuelve solo `settings` y `buildWeeklyAutoregulationSignals` (`:1241-1270`) solo AMRAP y e1RM, así que las propuestas por fatiga (descarga insertada, escalar semana, retrasar pico, puerta de cierre de bloque) están inactivas. Es coherente con esa decisión, pero el KDoc todavía promete «a live AUGE readiness snapshot».

**Bugs y cabos sueltos:**

| id | Gravedad | Hallazgo | Dónde |
|---|---|---|---|
| IMP-01 | ALTA | La variante rotada (T-71), la plantilla powerbuild-16-4 (T-72, `withVariantReference`) y el cambio EQUIVALENT (T-73) se quedan sin kilos: en Powerlifting periodizado y powerbuild-16-4 los básicos de casi todo el programa salen sin carga. Se corrige con la decisión 2 | `RoutinePeriodizer.kt:349-356`; `KpknAdvancedProgramRecipes.kt:389-394`; `ProgramExerciseSwap.kt:45` |
| IMP-02 | ALTA | T-90 no existe: no hay respuestas derivadas (`DERIVED`); el «calibrador vivo» se quedó en un resumen de registros. La hoja dice «Perfil guardado · origen» y nunca hay otro origen que el declarado | `CalibrationEvidence.kt:37-80`; `ProgramVolumeCalibrationModule` |
| IMP-03 | MEDIA | Se quitó la puerta de calibración (T-93) sin la calibración sugerida por defecto: los programas nuevos sin calibrar tienen `volumeRecommendations` vacío y `scaleToCalibratedVolume` no escala. Además quedó código muerto (`TemplateApplyOutcome.RequiresCalibration`, `pendingCalibrationProtocol/Profile`, ramas en `HomeScreen.kt:419,514` y `ProgramsScreen.kt:247-250,330-337`) | `ProgramsViewModel.kt:185-257`; `ProgramProtocolEngine.kt:76` |
| IMP-04 | MEDIA | Sin salida si la persona nunca registra la última sesión obligatoria: `MissedSessionPolicy.afterAdvance` solo pide decisión al completar la última REQUIRED o al cerrar bloque, y `HOLD_INCOMPLETE` sigue sin petición | `MissedSessionPolicy.kt:63-69`; `ProgramProgressEngine.kt:969-983` |
| IMP-05 | MEDIA | Respuestas tragadas en silencio: un check-in que ya no casa con la semana (`WeeklyCheckinEngine.apply` `:51-56`), una petición de sesiones perdidas con ids viejos (`MissedSessionPolicy.resolve` `:89`) y la vuelta suave sin catálogo (`:95`) devuelven el programa sin cambios ni decisión ni mensaje | — |
| IMP-06 | BAJA | `InactivityDetector` calcula nivel 1/2 pero da el mismo mensaje | `InactivityDetector.kt:11-20` |
| IMP-07 | BAJA | `SeasonAdvisor`: `WEIGHT_CLASS_RISK` con severidad 2 aunque no haya tendencia de peso; `nextBlockGoal` se rellena y nunca se lee | `SeasonAdvisor.kt:35-37`; `ProgramRepository.kt:1376` |
| IMP-08 | BAJA | Día de competición: la barra inferior de Inicio muestra «Registrar» como si el meet ya hubiera pasado | `HomeScreen.kt:1162-1176` |
| IMP-09 | BAJA | Restos: `BlockReviewEngine.review` recibe `context` y no lo lee; `GzclProtocols.t1Stage/t2Stage(week)` con parámetro muerto; KDoc de `buildTransitionContext`; campos sin lector (`WeighInForecast.projectionAvailable`, `SeasonInput.nextBlockGoal`, `WeeklyCycleEvidence.muscleAnswers`, `VolumeCalibrationProfile.notes`, `ContinuationProposal.suggestedCatalogIds`) | varios |

**Calidad de programación (no es duplicado, se corrige de paso):** power-20-5 no hace sentadilla ni peso muerto de competición como principal hasta la semana 16 (todo el macrociclo en variantes); se acepta como estilo conjugado, pero el bloque de fuerza vuelve a la banca de competición (ver F-14).

---

# PARTE II · QUÉ SE HIZO

## 1. Tareas → commit → prueba

| Tarea | Qué | Commit | Prueba que lo fija |
|---|---|---|---|
| F-01, F-02 | `SessionSlopRules` y la red `ProgramSessionSlopAuditTest`, que barre todos los programas | `c20d79fb7`, `2526ccf1c` | `SessionSlopRulesTest`, `ProgramSessionSlopAuditTest` |
| F-02b | La red también materializa con «todo el material» (E7): 27 recetas escapaban porque no se materializaban con los otros tres materiales | `98bea10f8` | `ProgramSessionSlopAuditTest` |
| F-10 | El periodizador solo rota el primer básico de cada familia por sesión, y nunca hacia un ejercicio que ya está en la sesión (DUP-01) | `c96c10870` | `RoutinePeriodizerTest` |
| F-11 | La semana base no repite la sentadilla trasera (`SessionUse.families`) (DUP-01b) | `c01574345` | `RoutineContractChecks` (barridos del generador) |
| F-12 | La adaptación por material no elige un sustituto que ya está en el día (DUP-02) | `63b933b70` | `PlanAdaptationResolverTest` |
| F-13 | El cambio de ejercicio no ofrece ni aplica uno que ya está en la sesión (DUP-03) | `073b640c7` | `ExerciseSwapOptionsTest`, `ProgramExerciseSwapTest` |
| F-13b | Las mismas exclusiones en el reemplazo de la sesión en vivo, en el editor y en los cambios progresivos del asistente | `2cef26103`, `ce1620516` | `ProgramExerciseSwapTest`, `ExerciseOverridesTest` (Android) |
| F-14 | power-16-4 y power-20-5: sin banca inclinada doble, sin déficit 3 veces por semana y sin singles después del test (DUP-06, SLOP-02, SLOP-03) | `16161eb1a` | `ProtocolRecipeFidelityTest` |
| F-15 | Identidad propia de las apariciones que repite el autor: `occurrenceLabel`, enlace y tipo de relación (DUP-04, decisión 1) | `298d884f1` | `PlanMaterializerTest`, `ExerciseDisplayNameTest`, `SessionSlopRulesTest` |
| F-16 | Unicidad permanente: semanas periodizadas y todas las recetas de autor | `13216329e`, `f8d0ac8af` | `RoutinePeriodizerTest`, `ProtocolRecipeFidelityTest` |
| F-20 | `VariantLoadEstimates`: factores fijos de KPKN por familia (decisión 2) | `1c5c44d4a` | `VariantLoadEstimatesTest` |
| F-21 | `Exercise.loadEstimate` y kilos estimados en el materializador | `82b9a926c` | `PlanMaterializerTest` |
| F-22 | Kilos estimados en la rotación de variantes y en el cambio EQUIVALENT | `a93c63dc7` | `RoutinePeriodizerTest`, `ProgramExerciseSwapTest` |
| F-23 | Rótulo «carga estimada» en la tarjeta del programa y en la sesión en vivo | `3a47efd38` | `SessionPrescriptionSummaryTest` (Android) |
| F-24 | Decisión documentada en `docs/WIZARD_PLAN_DEVIATIONS.md` | `d773b6b33` | — |
| F-30 | Regla HARD C12: el relleno de KPKN va como mucho 2 días por semana | `44961471f` | `RecipeContractPolicyTest` |
| F-31 | El relleno rota desde el tercer día de la semana en 14 recetas (`rotateKpknFillers`) | `2e597607f` | `ProtocolRecipeFidelityTest`, `ProgramSessionSlopAuditTest` |
| F-40 | Textos desfasados (RTS, RP, «ciclo pausado», KDoc) y etapas GZCLP sin parámetro muerto (SLOP-06/07/08, IMP-09) | `fff7120ff` | `PlanCatalogEditorialContractTest` |
| F-41 | «Torso B» de verdad (`bbTorsoB`) en body-12-3 y kpkn-rp-style (SLOP-04) | `f50eb15d2` | `ProtocolRecipeFidelityTest` |
| F-42 | Las respuestas que no se pueden aplicar quedan registradas y se muestran (IMP-05) | `dd971dc34` | `WeeklyCheckinEngineTest`, `MissedSessionPolicyTest` |
| F-43 | Salida aunque falte la última sesión y mensaje propio para el nivel 2 de inactividad (IMP-04, IMP-06) | `89a793428` | `MissedSessionPolicyTest`, `InactivityDetectorTest` |
| F-44 | Riesgo de categoría de peso sin tendencia con severidad baja, y aviso anticipado del pico (IMP-07) | `06e010b84` | `SeasonAdvisorTest` |
| F-45 | Día del meet: la barra de Inicio ofrece «Modo competición» (IMP-08) | `71b00c440` | `HomeTopBarIslandTest` (Android) |
| F-46 | Calibración sugerida por defecto con la dosis estándar; se retira el código muerto de calibración (IMP-03) | `65a614d18`, `a25adab62` | `VolumeCalibrationEngineTest`, `ProgramsViewModelTest` (Android) |
| F-47 | Respuestas derivadas del calibrador a partir de los registros (IMP-02, T-90) | `874784446` | `CalibrationDerivationTest` |
| F-48 | El cardio sale en español (`CardioTypeLabels`) (SLOP-05) | `27cc139b7` | `CardioTypeLabelsTest`, `ProgramSessionSlopAuditTest` |
| F-30b | C12 solo se aplica a las recetas `LEGACY_STANDARD`: rechazaba los planes nativos y hacía desaparecer 36 programas | `86b62b7de` | `RecipeContractPolicyTest`, `NativeProfileRecipeAndFitterTest`, `ApproachMaterializationTest` |
| F-31b | La rotación del relleno prefiere el mismo material, luego el peso corporal y luego el resto (Smolov dejaba de pedir solo rack) | `75c79dd81` | `FixedRecipeEquipmentCompatibilityTest`, `ProtocolRecipeFidelityTest` |
| F-43b | El aviso de inactividad de nivel 2 conserva «Esto no indica si entrenaste fuera de KPKN»; el texto del plan («Llevas N días sin entrenar») afirmaba algo que KPKN no sabe | `784ce8fd9` | `InactivityDetectorTest`, `ProgramDetailViewModelTest` (Android) |
| F-60 | Red con techo 0 y suelo de 7.351 programas revisados; esperados actualizados (cardio en español, hiperextensión de Westside) | `3121dfcf4`, `f9d9e444b` | `ProgramSessionSlopAuditTest`, `PlanMaterializerCardioIdentityTest`, `RecipesMaterializeWithAllGearTest` |

## 2. Hallazgos nuevos durante la ejecución (no estaban en la Parte I)

| id | Hallazgo | Arreglo | Commit |
|---|---|---|---|
| DUP-07 | La red encontró duplicados en planes nativos (~372), adaptados (60) y originales (48). En Atleta completo, la banca de potencia era la misma banca de trabajo, y había sentadilla barra alta + barra baja. En Fuerza y músculo con mancuernas, el remo de R y el de V eran iguales y no había jalón. En PHUL/PHAT, los gemelos tenían dos configuraciones de la misma definición con el mismo nombre en pantalla. | `SimpleCyclePersonalizer` funde o cambia lo repetido | `d4a4ff4fc` |
| DUP-07 (recetas) | Texas 3d (`back_extension_lumbar`) y Westside (`reverse_hyper__machine`) usaban máquinas sin llave de material: **nadie** podía activarlas | Hiperextensión a 45° en las dos | `8d42642f0`, `f67cd4df9` |
| I-1 | El arreglo de DUP-07 había degradado la potencia de Atleta completo (sentadilla sin carga, press en suelo) | La potencia vuelve a la barra emparejada con el trabajo, como pide la especificación (§11.5, §14.3), con el rótulo «Potencia» como aparición enlazada | `5beaec554` |
| I-2 | Regla nueva: dos tarjetas de una sesión nunca se pintan con el mismo texto. Además, **7.388 chips** mostraban ids internos (`seated_machine`, `hands_elevated`…) | Los chips se rotulan en español en `ExerciseCatalogV2LegacyAdapter.optionLabel`; las reglas DUPLICATE_DISPLAY_NAME e INTERNAL_DISPLAY_CHIP quedan en la red | `9b8d1a395` |
| I-3 | El héroe de Inicio, el día del meet, no era coherente con la barra (F-45) | El héroe también ofrece «Modo competición» | `9ea3427a3` |

## 3. Evolución de la red (`ProgramSessionSlopAuditTest`)

La red materializa cada sesión de cada semana de:
- todos los planes de autor, propios y plantillas, con cuatro materiales (gimnasio completo, mancuernas y banco, gimnasio sin rack y todo el material);
- los planes nativos;
- los programas a medida (todos los perfiles, modos, niveles, de 2 a 6 días, 45 y 75 minutos), en semana simple, por bloques y periodizados.

Cada sesión pasa las reglas de `SessionSlopRules` y las del test (relleno, nombre repetido en pantalla, chip interno). El techo es un trinquete (`total <= CEILING`).

| Momento | Violaciones |
|---|---|
| `master`, 3 materiales (línea base) | 2.238 |
| + material «todo» (F-02b) | 2.464 |
| tras F-10 y F-11 (periodizador y semana base) | 1.280 |
| tras DUP-07 (nativos y adaptados) | 908 |
| tras F-15 y F-48 (identidad propia y cardio) | 162 (todo relleno) |
| tras F-31 (relleno rotado) y la integración | 0, pero con 7.315 programas: C12 hacía desaparecer 36 planes nativos (F-30b) |
| tras F-30b, F-31b y F-60 | **0** (techo 0) con **7.351 programas** y 186.435 sesiones (suelo 7.351) |

Desglose final por regla: DUPLICATE_CONFIGURATION 0 · DUPLICATE_DEFINITION 0 · SAME_MOVEMENT_FAMILY 0 · INTERNAL_NAME 0 · EMPTY_EXERCISE 0 · EMPTY_SESSION 0 · DUPLICATE_DISPLAY_NAME 0 · INTERNAL_DISPLAY_CHIP 0 · FILLER_OVER_WEEKLY_CAP 0.

Lección: una red que solo cuenta violaciones mejora si los programas desaparecen. Por eso ahora también tiene un suelo de programas revisados.

## 4. Verificación

- **Motor (F-60):** barrido JVM de `domain/training`, `data/protocols`, `data/programs`, `domain/onboarding`, `domain/templates` y `domain/exercises`: **277 clases, 2.701 pruebas, 0 fallos, 0 errores**. Quedan fuera 15 pruebas que dependen de Android, Room o pantallas y que el arnés JVM no compila: la lista está en la decisión 25.
- **Red de slop:** 0 violaciones en 7.351 programas y 186.435 sesiones, con un suelo de 7.351 programas revisados.
- **Android (F-61):** 315 pruebas de pantallas: Inicio, Programas, Program Detail, editor, catálogo, asistente, render sheets y competición. Fallaron 2:
  - `ProgramDetailViewModelTest.inactivity_notice…`: era un fallo real de F-43, que se corrigió en F-43b.
  - `CreateProgramTemplateSheetTest`: una excepción de Room que se escapó de otra prueba anterior (`Illegal connection pointer`, Room Invalidation Tracker), ajena a este trabajo.
  
  Repetición tras F-43b (`784ce8fd9`): `ProgramDetailViewModelTest` 112/112 y `CreateProgramTemplateSheetTest` 2/2 en verde; `assembleBaseDebug` correcto.
- **QA en emulador (`KPKN_Programs_Current_QA`):** solo parcial. El equipo estaba con carga 30–44 por las compilaciones de otras sesiones. El emulador se cayó dos veces (segfault al decodificar vídeo con `swiftshader`) y con 1,5 GB de RAM el sistema mataba la app. Con `-gpu host` y 3 GB se pudo usar:
  - **Verificado:** la app abre, con Inicio y el Program Detail del plan activo. El catálogo de planes muestra PHAT con los gemelos rotulados en español («gemelo de pie», «gemelo sentado», «gemelo donkey»), sin ids internos y sin ejercicios repetidos en ningún día.
  - **No verificado en pantalla** (cubierto por pruebas; conviene mirarlo en el teléfono): el Powerlifting periodizado a medida en las semanas 1, 5 y 9; el rótulo «Volumen BBB 5×10» de 5/3/1 BBB; power-16-4 en las semanas 6 y 16; los accesorios de Lilliebridge; el cardio en español y el rótulo «Potencia» de Atleta completo; las opciones del cambio de ejercicio.

## 5. Decisiones autorrespondidas (noche del 27 al 28-oct)

El usuario pidió no preguntar y decidir lo mejor para el proyecto. Las decisiones y sus razones:

1. Subagentes solo Sonnet high (orden del usuario 28-oct: nunca Fable). Los agentes Fable se pararon y se relevaron desde su diff en disco.
2. Red de slop con un 4.º material «E7 todo el material»: con el gimnasio completo del fixture, 27 recetas (Wendler, Texas, GZCL, Madcow…) no se materializaban y escapaban a la red.
3. Techo de la red como trinquete (`total <= CEILING`), bajado tras cada ola.
4. DUP-07 (nuevo): la red encontró duplicados en planes nativos (~372), adaptados (60) y originales (48) que la auditoría no tenía. Causas: Atleta completo (potencia de banca = banca de trabajo; potencia de sentadilla barra alta + sentadilla barra baja), Fuerza y músculo con mancuernas (remo de R y de V iguales sin jalón), PHUL/PHAT (gemelos: dos configuraciones de la misma definición, mismo nombre en pantalla).
5. F-15: la identidad de una aparición repetida es `occurrenceLabel` + `relationshipType` (`normalizedIdentityFields` borra `relativeToCanonicalExerciseId` cuando apunta a la misma configuración); solo se enlazan los suplementarios que repiten configuración, definición o familia (no todos, para no agrupar analítica).
6. F-46: la calibración sugerida por defecto es la dosis ESTÁNDAR (2,2,1,2 = suma 7), no 2,2,2,2 (suma 8 = AVANZADO, culturismo 18 series). Sin marcas declaradas no se supone fuerza alta.
7. F-13b: exclusiones también en el reemplazo de la sesión en vivo y del editor (líneas mínimas en sessioneditor/workout).
8. F-42: la petición se conserva cuando limpiarla perdería una petición vigente o impediría reintentar (otra semana, respuestas inválidas, vuelta suave sin catálogo).
9. F-44: aviso anticipado de pico con severidad 1 (el del bloque de pico sigue en 2).
10. F-45: la barra superior de Inicio en MEET_DAY ofrece «Modo competición»; el héroe también debe ser coherente (hecho en I-3).
11. DUP-07, potencia nativa: NO se cambia la potencia de sentadilla a «sentadilla sin carga» ni la de banca a press en suelo (lo que hizo el agente): la especificación (§11.5, §14.3) pide carga ligera con barra emparejada con la sentadilla de trabajo; con F-15 queda como aparición enlazada y rotulada «Potencia». Se conserva la fusión del remo duplicado.
12. Regla nueva en la red: DUPLICATE_DISPLAY_NAME — dos tarjetas de una sesión nunca se muestran con el mismo texto (lo que ve la persona), aunque sean configuraciones distintas.
13. Texas 3d (back_extension_lumbar sin llave de material) → hiperextensión 45°; Westside (reverse_hyper__machine sin llave) → hip thrust (después hiperextensión, decisión 22): antes NADIE podía activarlas.
14. I-2: los chips de opción eran ids internos del catálogo (7.388 en la red: `seated_machine`, `hands_elevated`…). Se rotulan en español en el adaptador (un solo sitio) en vez de en cada pantalla.
15. F-31 quita la `technique` del relleno al rotarlo (la técnica era del ejercicio original, no de la alternativa). Se añadió la alternativa OH_TRI → [PUSHDOWN, KICKBACK_CABLE] porque faltaba.
16. F-41 usa el remo con pecho apoyado de agarre ancho (CSR_WIDE): el CSR ya estaba en `bbTorso` y los dos torsos no deben compartir ejercicios.
17. F-23: la descripción de accesibilidad de la sesión en vivo dice «del básico» cuando no conoce el nombre del levantamiento de origen.
18. F-22: en el cambio de ejercicio solo se estiman kilos con SwapKind.EQUIVALENT. Una variante no equivalente no hereda la marca.
19. F-43: la petición por sesiones perdidas se evalúa al abrir el detalle del programa (init del ViewModel), sin trabajo en segundo plano.
20. F-47: las respuestas derivadas solo se muestran («Según tus registros 3/3»); nunca se guardan como declaradas, y la declarada gana mientras exista.
21. Merge de la ola 3: Westside junta DUP-07 y F-31 en la versión 3. Ninguna de las dos versiones intermedias llegó a publicarse, así que una sola subida basta.
22. Westside `rev` pasa del hip thrust a la hiperextensión a 45°. El hip thrust repetía la extensión de cadera del pull-through del mismo día, y la hiperextensión se parece más a la reverse hyper del autor.
23. El barrido completo destapó que C12 (relleno ≤ 2 días, HARD) también rechazaba los planes nativos: son diseño entero de KPKN (todos sus slots son KPKN_DEFAULT) y su frecuencia es intencionada. Desaparecían 36 programas sin que la red lo notara. C12 se limita a recetas LEGACY_STANDARD (métodos de autor con relleno de KPKN y plantillas), y la red gana un suelo de programas revisados: un plan que deja de materializarse no cuenta como mejora.
24. La rotación del relleno elige primero alternativas con el mismo material que el original, después peso corporal y al final el resto. Así el plan no pide aparatos nuevos (Smolov pasaba a exigir barra de dominadas y mancuernas por el jalón y el face pull rotados).
25. 15 pruebas que dependen de Android, Room o pantallas no compilan en el arnés JVM. Se excluyen solo en el barrido JVM de esta rama, con una lista local que se restaura al terminar (la plantilla compartida no se toca); las de pantallas que importan se corren en el carril Android.
26. GHR rota a curl de isquios en máquina (tumbado o sentado), no al curl nórdico: el nórdico exige un anclaje (`nordic_anchor`) que ni «todo el material» acredita, y con él cuatro recetas dejaban de materializarse.
27. Las corridas Android de `progfix3` y `progfix` murieron por falta de memoria del compilador Kotlin (-Xmx4g, valor por defecto de la cola). Se repiten con -Xmx6g, que la guía de Gradle permite. El daemon huérfano de la corrida fallida (PID 25348, `-Xmx4g`, desde las 04:23) sigue reteniendo el bloqueo de `C:\kw\progfix\android-native` y no tuve permiso para cerrarlo. Por eso el APK y las pruebas Android se compilan en un worktree desacoplado (`C:\kw\progfix-apk`, en `f9d9e444b` y después en `784ce8fd9`; se borra al terminar). PENDIENTE PARA EL USUARIO: cerrar ese proceso java (unos 2 GB de RAM).
28. F-43b: el plan pedía para el nivel 2 de inactividad el texto «Llevas N días sin entrenar». La prueba Android de Program Detail fija una regla de honestidad: KPKN solo ve sus registros. El nivel 2 conserva «Esto no indica si entrenaste fuera de KPKN» y añade la recomendación de repetir la semana con una vuelta suave.
29. QA en emulador: tope de unos 45 minutos (regla de no hundir tiempo). El equipo estaba saturado por las compilaciones de otras sesiones, y lo que no se pudo ver queda listado para revisarlo en el teléfono.

## 6. Fuera de alcance o heredado

- T-80/T-81 y la parte AUGE de T-82/T-83 siguen retiradas, porque AUGE salió de la app el 24-oct. El KDoc de `buildTransitionContext` ya lo dice (F-40).
- power-20-5 sigue sin sentadilla ni peso muerto de competición como principal hasta la semana 16. Se acepta como estilo conjugado.
