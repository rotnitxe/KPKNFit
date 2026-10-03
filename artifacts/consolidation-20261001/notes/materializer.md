# materializer — T006 igualdad completa de W2 (3 rojos) + NativeOriginAuthorPreservationTest (2 rojos)

Sin Gradle/adb/emuladores (regla dura 1): todo verificado por lectura, XML JUnit y script de diff. Verificación pendiente del orquestador.

## (1) T006: `targetDurationMinutes` perdido al reconstruir W2

### Evidencia
Script (python) sobre `TEST-...T006PersistenceAndUseIntegrationTest.xml`: se extraen los `expected:<..> but was:<..>` de
`fourNative...` (strength-barbell), `muscleE0...` y `athleteE0...` y se comparan token a token (`difflib`, tokens por `,`/`(`/`[`):

| test | tokens | bloques distintos | contenido del único tipo de diferencia |
|---|---|---|---|
| fourNative... (strength-barbell) | 2316 | 3 | `targetDurationMinutes=29/24/26` -> `null` |
| muscleE0... | 4013 | 6 | `15,22,15,23,15,23` -> `null` |
| athleteE0... | 4013 | 6 | `27,21,29,27,21,24` -> `null` |

Es decir, tras el sello **no hay una segunda divergencia** en ninguna de las 3 semanas: ids, partes, series, calentamientos, cardio, SPEED,
`dayOfWeek`, cargas y `loadReference` ya son idénticos. Las aserciones previas (`identityOf`, `cardioOf`, `speedOf`) ya pasaban.
Limitación: en `fourNative...` el bucle aborta en el primer perfil (strength-barbell); los otros 4 perfiles (mancuernas, powerbuilding,
atleta corporal, músculo corporal) no se ven en el XML. Se razonó por lectura que comparten la misma ruta (todos con días `[1,3,5]`
-> `startDay = 1`, mismo `planWarmupConfig`, misma receta), ver "Riesgos".

### Traza (causa raíz)
1. Generación (`SimpleCyclePersonalizer.personalizeNative`): `fit.allSessionMinutes[i] = SessionDurationEstimator.estimate(session_i).totalMinutes`
   sobre el programa materializado por `PlanMaterializer.materialize`, y `Program.withSessionDurations(fit)` copia ese minuto a
   `Session.targetDurationMinutes` de TODAS las sesiones de TODAS las semanas (l.1444-1469; ruta legada: l.425 con el mismo estimador).
   `PlanMaterializer.materialize/materializeDay` nunca sella (l.848-860: el `Session(...)` no lleva `targetDurationMinutes`).
2. Reconstrucción (`rematerializeWeek`): `materializeWeek(...)` produce sesiones SIN sello. `mergePreservedSessions` devuelve
   `fresh` (rama `sameId`) o `adoptSessionIdentity(candidate, fresh)` (rama por día/posición): ambas toman TODO el contenido de `new` y solo
   conservan identidad (`id`, partes, ejercicios, sets, calentamientos). El sello de la sesión previa se pierde -> `null`.
   En T006 `weekOccurrence = 2` != 1 con el que se materializó, así que los ids estables no coinciden y se usa `adoptSessionIdentity`;
   en producción (`applyMutations`, ocurrencia 1) es la rama `sameId`. Mismo efecto.
3. Mismo defecto en HEAD (`rematerializeWeek` reemplazaba la semana entera con `materializeWeek(...).copy(id,name,progressionIndex)`):
   el rojo lo destapa la aserción nueva de igualdad completa, no una regresión de la ola.

Veredicto: DEFECTO DE PRODUCCION (el oráculo es correcto). Fundamento: plan §12.2 («toda selección usa el mismo cálculo de duración real»),
§14.4 («idénticos al reconstruir la misma ocurrencia»), §14.5 (la reconstrucción no pierde campos). Consumidores del sello:
`WorkoutPacingController`/`WorkoutV2Body` (límite de la sesión), `SessionEditorScreen`/`RulesSheet` (límite/sugerencias del Time Coach),
`LegacyNativeDurationConsistencyTest` (contrato `targetDurationMinutes == estimator.totalMinutes`). Tras aceptar una propuesta AUGE o pulsar
«Re-materializar», las semanas posteriores de un plan propio quedaban sin límite de tiempo, aunque la semana 1 sí lo tenía.

### Por qué esta solución (y no sellar siempre en `materializeDay`)
* `SessionDurationEstimator` depende solo del contenido (series, descansos, calentamientos, cardio, suelos de duración); ignora ids, cargas y
  el propio `Session.targetDurationMinutes` (doc de la clase l.49-51; `calculateSessionTimeBreakdown` no lee identidad ni pesos). La normalización
  de Room (`normalizedIdentityFields`, `collapseDuplicateSessionLayout`) solo toca campos de identidad / duplicados sueltos que `materializeDay`
  nunca genera, así que `estimate(fresh sin normalizar) == estimate(persistido)`.
* Sellar siempre en `materializeDay` cambiaría la salida de `materialize` para planes de autor/plantillas (sin sello hoy) y activaría un límite
  de ritmo que nunca tuvieron: viola «plantilla/legado sin receta: el comportamiento actual no debe cambiar».
* Conservar el valor previo tal cual dejaría un límite obsoleto cuando una propuesta de volumen recorta series, y al «Restaurar desde el plan»
  de una sesión editada devolvería el límite elegido por el usuario en vez del del plan.

### Cambio (solo `PlanMaterializer.kt`)
* `rematerializeWeek`: `foreignRecipe` (ver (2)) y `sealDurations = nativeCurate` se pasan a `mergePreservedSessions`.
* `mergePreservedSessions(..., sealDurations = false, foreignRecipe = false)`; nuevo `resealDuration(previous, rebuilt, sealDurations)`:
  si el plan es propio (`program.isNativeCuratedRecipe(recipe)`), la sesión sustituida ya llevaba sello y la reconstruida no, el sello es
  `SessionDurationEstimator.estimate(rebuilt).totalMinutes` (se aplica tras `adoptSessionIdentity`, sobre la sesión final). Sin sello previo, o
  con receta ajena/de autor/plantilla, no se toca nada. Una sesión congelada o entrenada no se reconstruye y conserva su límite (p. ej. 99 min
  elegido a mano). No se duplica la fórmula (misma llamada que el generador).
* `SimpleCyclePersonalizer.kt` NO se tocó (su sellado ya usa el mismo estimador).

### Exactitud por lectura (receta sin cambios)
* Mismas entradas: misma `WeekRecipe` (`weekRecipeSourceFor`/`progressionIndex`), `scaleWeekRecipe(1.0, 1.0)` devuelve la receta, mismo
  `planWarmupSteps` (persistido en `planWarmupConfig` por `nativeSkeleton`), mismo `nativeCurate`, `profile = null` (`powerliftingProfile` nulo en
  planes propios), `referencePool` solo afecta pesos (no leídos por el estimador). Cardio: `cardioPartOf` sella `part.targetDurationMinutes`
  igual en ambas. Resultado: mismo minuto que el generador para fuerza, músculo y atleta con cardio/SPEED.
* Diferencias legítimas: una propuesta de volumen (`volumeFactor < 1`) recorta series -> el sello baja con el contenido (test nuevo);
  semana 1 vs 2 no difieren salvo las del propio contenido (RIR no entra en el estimador).

## (2) NativeOriginAuthorPreservationTest (2 rojos tras el arreglo de la ola 2)

### Causa raíz
El test usa `catalogEntryId = "native:machine-muscle"`. `NativeProfileKind.fromEntryId` solo reconoce las 4 entradas v2
(`native:strength-foundation-v2`, ...) o los `sourceId` desnudos: `native:machine-muscle` es el **plan nativo histórico**
(`PersonalizedPlanCatalog.nativeSpecs`), ruta legada de `SimpleCyclePersonalizer` (l.196-515), no `personalizeNative`. Esa ruta:
* produce UNA semana repetible `"$programId-week"` (por eso el XML solo muestra 10 ejercicios, no 6 semanas);
* sesiones `id = "$programId-session-$i"` (sin prefijo `rs_`), ejercicios `"$programId-s$i-e$k"`;
* `sourceRecipe` con `DayRecipe(id = null, label = session.name, weekday = absoluto)`: **no hay `recipeDayId` en ningún ejercicio**.

`isRecipeDerived(session)` (arreglo de la ola 2: `id.startsWith("rs_") || any recipeDayId != null`) es siempre `false` para esas sesiones, así
que `existing.filter { it.id !in claimed && (it.id in preserved || !isRecipeDerived(it)) }` las conserva todas. La nota `origin.md` trazó el
caso v2 (ids estables), no el histórico que usa el test. Traza con valores (test 1): `existing = [native-origin-session-0 (Pecho, d1),
-1 (Brazos, d3), -2 (Piernas, d5)]`, `fresh` = 1 sesión del autor con `dayOfWeek = 1` (día sin `weekday`, `startDay = 1`) -> reclama
`-0` por día y adopta su identidad; `-1`/`-2` sin reclamar, `preserved = {}`, `isRecipeDerived = false` -> se conservan:
`[SQ, BP] + [preacher_curl, triceps_pushdown] + [prensa, extensión, curl femoral, hip thrust, gemelo, lying leg curl]`. Exactamente el XML.
En el test 2 (`frozen = última`, + sesión de usuario) queda `[SQ, BP, preacher_curl, triceps_pushdown]` (la «Brazos» pendiente).

### Veredicto
DEFECTO DE PRODUCCION (de alcance; el contrato del oráculo 1 vigente desde HEAD) + FIXTURE del oráculo 2 incoherente con §14.5.
* HEAD sustituía la semana entera, y el test 1 (de HEAD) fijó «receta ajena -> solo la base del autor». §14.5: «El materializador conserva
  íntegro un override...; reconstruye las otras sesiones»: la política es freeze por marca; lo no marcado ni entrenado se reconstruye.
* Evidencia de que toda sesión creada/guardada por el usuario queda marcada: `SessionEditorViewModelNavigation.saveSession` (l.442-443 añade
  la sesión nueva y l.459 `freezeManualEdits` marca `draft.id` siempre; transferencias -> `freezeTransferredSessionOverrides`). Una búsqueda de
  `sessions + ...` en `main` no encontró otro camino que añada sesiones a una semana. La sesión «de usuario» del test 2 estaba **sin marca**,
  algo que el editor no produce (solo existiría en datos anteriores a la marca, cubiertos por la rama de receta propia).

### Cambio (solo `PlanMaterializer.kt`)
`foreignRecipe = program.sourceRecipe?.let { it.id != recipe.id } == true` en `rematerializeWeek` y, en `mergePreservedSessions`:
`existing.filter { s -> s.id !in claimed && (s.id in preserved || !(foreignRecipe || isRecipeDerived(s))) }`.
* Receta ajena (otro id que la fuente): toda sesión pendiente sin contraparte es residuo de la receta anterior, también la del plan histórico sin
  ids de día. Lo protegido (`preserved` = entrenadas + congeladas/marcadas, incluidas las creadas con el editor) sobrevive.
* Receta propia (todos los llamadores de producción: `ProgramAutoregulationEngine`, `ProgramDetailViewModel.rematerializePending`,
  `restoreSessionFromRecipe` pasan `program.sourceRecipe`): comportamiento idéntico al de la ola 2; las sesiones sin contraparte y sin marca
  (datos previos a la marca) se siguen conservando. `restoreSessionFromRecipe` / NO_RECIPE_COUNTERPART no cambian.
* Descartada la alternativa «heurística por etiqueta/posición del día de la receta propia»: añade código especulativo en una ruta que
  ningún llamador de producción usa, y contradice la política de §14.5 (freeze por marca).

### Tests
* `NativeOriginAuthorPreservationTest`
  * `...keeps_the_author_base`: SIN cambios (pasa con el fix).
  * `...keeps_frozen_and_user_created_sessions`: mismas aserciones; el fixture ahora marca la sesión del usuario con
    `withManualSessionOverride` (como hace el editor real) además de la congelada; KDoc explica el porqué. No se debilitó ninguna aserción.
  * Nuevo `rematerializing_with_the_programs_own_recipe_keeps_unmarked_extra_sessions`: guarda el límite del descarte (receta propia + sesión
    extra sin marca -> se conserva intacta y las del plan mantienen ids).
* Nuevo `PlanMaterializerDurationSealTest` (5 tests, sin Room/Android): sello idéntico tras reconstruir con receta propia (y == estimador);
  sin sello previo no se inventa; propuesta de volumen 0.5 -> sello menor == estimador; sesión congelada conserva su límite (99) y la pendiente
  se resella; receta ajena -> sin sello y sin residuo.
* Revisados por lectura (sin cambio de comportamiento): PlanMaterializerRematerializePreservationTest (misma receta, `sourceRecipe.id == recipe.id`,
  programas sin sello y no nativos), ProgramRepositoryAutoregulationAcceptanceTest, ProgramAutoregulationResolutionTest,
  NativeWorkoutProgressionRuntimeTest, NativeProgressionEquipmentAndVariantTest, PlanMaterializerCardioIdentityTest, PlanMaterializerTest,
  PlanWarmupConfigPersistenceTest, NativeRirWarmupAndIntegrationTest (rebuild nativo sin aserciones de duración),
  ProgramDetailViewModelTest `restore*` (fixtures no nativos: `sealDurations = false`; `sourceRecipe` propia -> no ajena),
  SessionEditorManualOverrideTest (receta propia), T006 test 5 (aserciones de `contentOf`/`identityOf`, no de duración).

## Riesgos / observaciones (no cambiados)
* `fourNative...`: solo se vio el primer perfil; los otros 4 deberían converger por la misma ruta, pero no se pudo comprobar su XML.
* PRE-EXISTENTE (también en HEAD): `rematerializeWeek` llama `materializeWeek(..., trainingDays = null, startDay = 1, ...)`. Las recetas v2 usan
  `weekday` RELATIVO al día de inicio (`recipeWeekday`) y `materialize` rota con `weekStartDay`; la reconstrucción rota con 1. Un plan propio que no
  empiece en lunes (p. ej. `[2,4,6]`) desplazaría `dayOfWeek` de la semana reconstruida (rama `sameId`, ocurrencia 1, ruta de `applyMutations`).
  T006 solo usa semanas que empiezan en 1. No se corrigió: la receta del plan histórico guarda weekdays ABSOLUTOS y rotar con `weekStartDay`
  los rompería; el arreglo correcto exige distinguir ambas convenciones (o migrar la histórica) y queda para el orquestador.
* `applyMutations` solo registra la receta efectiva si `working.macrocycles != beforeWeeks`: antes, el sello perdido hacía que TODA reconstrucción de
  un plan propio contara como cambio; ahora una propuesta sin efecto real (p. ej. intensidad sobre RIR) deja el programa igual, que es lo que
  pide §14.4 («no aceptar una propuesta sin efecto»). Ningún test existente depende del cambio espurio (los de autorregulación usan programas no nativos).

## Verificación sugerida
`cd android-native && ./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.NativeOriginAuthorPreservationTest" --tests "com.example.kpkn.domain.training.PlanMaterializer*" --tests "com.example.kpkn.data.onboarding.T006PersistenceAndUseIntegrationTest" --tests "com.example.kpkn.screens.programdetail.ProgramDetailViewModelTest" --tests "com.example.kpkn.domain.training.NativeWorkoutProgressionRuntimeTest" --tests "com.example.kpkn.domain.training.ProgramAutoregulation*" --tests "com.example.kpkn.data.repository.ProgramRepositoryAutoregulationAcceptanceTest" --tests "com.example.kpkn.screens.sessioneditor.SessionEditorManualOverrideTest"`
