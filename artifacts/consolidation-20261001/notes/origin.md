# origin — NativeOriginAuthorPreservationTest.rematerializing_a_native_week_with_a_foreign_recipe_keeps_the_author_base

## Veredicto
DEFECTO DE PRODUCCION (el oraculo es correcto y vigente). Sin cambios en las aserciones existentes.

## Evidencia
* Fallo identico en g02 (linea base) y g04 (ola 1): esperado `[low_bar_back_squat__barbell, bench_press__barbell]`,
  real esas dos + `preacher_curl__machine, triceps_pushdown__bilateral__machine` (sesion nativa «Brazos») +
  `quads_prensa_piernas__bilateral, quads_extension_cuadriceps__machine__bilateral, seated_leg_curl__bilateral__machine,
  hip_thrust__bilateral__machine, calf_raise__bilateral__machine, lying_leg_curl__bilateral__machine` (sesion nativa «Piernas»).
  Es decir: la sesion reconstruida con la receta de autor (1 dia) es correcta; el resto son las DOS sesiones nativas
  restantes de la semana original (3 dias: Pecho/Brazos/Piernas, dias 1/3/5).
* `git show HEAD:...PlanMaterializer.kt`: en HEAD `rematerializeWeek` sustituia la semana entera por
  `materializeWeek(...).copy(id, name, progressionIndex)` y el test pasaba. El WIP de reparacion de sesion/editor
  (§14.5, seleccion `mergePreservedSessions`, identico en `snapshot-pre-wave1`) introdujo la fusion y su ultima linea
  `merged + existing.filter { it.id !in claimed }`: conserva TODA sesion existente sin contraparte, pensada para las
  «creadas por el usuario» (comentario de la funcion; test `restoreManualSessionFromPlan_session_without_recipe_counterpart...`
  con sesion sin `recipeDayId`). Una sesion nativa pendiente NO es creada por el usuario: deriva de un `DayRecipe` con id
  declarado (`d1-...`, `SimpleCyclePersonalizer.assembleNativeDay`) → `Session.id = rs_<sha>` y `Exercise.recipeDayId != null`.
* Plan: §14.4 «receta snapshot del programa → override aprobado → manual overrides → …» y §14.5 «El materializador
  conserva íntegro un override…; reconstruye las otras sesiones» (la receta manda sobre lo pendiente; se protege lo
  entrenado/congelado, no el residuo de otra receta). §5 D-101/§10.1 + Invariantes: la receta original no se modifica y
  cada derivada conserva identidad: la receta fuente del programa NO se sustituye (asercion final del test, intacta) y la
  base del autor no se mezcla con contenido de otra receta. Ningun apartado del plan dice que reconstruir con otra receta
  deba retener las sesiones pendientes de la anterior.
* Llamadores de produccion de `rematerializeWeek` (`ProgramAutoregulationEngine`, `ProgramDetailViewModel.rematerializePending`,
  `restoreSessionFromRecipe`) pasan la receta fuente del propio programa → mismo numero de dias → nunca quedan sesiones
  sin reclamar; el cambio solo actua cuando la receta reconstruida tiene menos dias (receta ajena / forma de semana distinta).

## Cambio (solo `PlanMaterializer.mergePreservedSessions` + helper privado `isRecipeDerived`)
`return merged + existing.filter { it.id !in claimed && (it.id in preserved || !isRecipeDerived(it)) }`
* conserva siempre las protegidas (`preserved` = entrenadas + congeladas por `ManualSessionOverride`);
* conserva las que no derivan de ninguna receta (id propio, sin `recipeDayId`: creadas por el usuario o recetas legacy);
* descarta la sesion pendiente derivada de un dia de receta sin contraparte en la receta reconstruida
  (`id` con prefijo `rs_` —constante `STABLE_SESSION_ID_PREFIX` que estaba sin uso— o algun `Exercise.recipeDayId != null`).
No se tocaron `rematerializeWeek`, `restoreSessionFromRecipe`, `removeManualSessionOverride` ni `materializeSlot`.
`restoreSessionFromRecipe` sigue rechazando igual (posicion < 0 o >= recipeDayCount → NO_RECIPE_COUNTERPART).

## Tests
* Confirma el arreglo: `NativeOriginAuthorPreservationTest.rematerializing_a_native_week_with_a_foreign_recipe_keeps_the_author_base`
  (ahora `[SQ_LOW, BP]`, sin aproximaciones, `sourceRecipe` intacta).
* Nuevo (guarda el limite del descarte): `rematerializing_a_native_week_with_a_foreign_recipe_keeps_frozen_and_user_created_sessions`
  (sesion nativa congelada por override + sesion de usuario sin `recipeDayId` sobreviven iguales; el resto = base del autor).
* Revisados por lectura (misma receta, mismo numero de dias, ids estables → nada sin reclamar, comportamiento identico):
  PlanMaterializerRematerializePreservationTest, ProgramRepositoryAutoregulationAcceptanceTest,
  ProgramAutoregulationResolutionTest, NativeWorkoutProgressionRuntimeTest, NativeProgressionEquipmentAndVariantTest,
  PlanMaterializerCardioIdentityTest, PlanMaterializerTest, T006PersistenceAndUseIntegrationTest,
  ProgramDetailViewModelTest (restore*), SessionEditorManualOverrideTest. Los tests de planes de autor de la ola 1
  (AuthoredPlanMaterializerTest, PlanAdaptationAuthoredRecipesTest) no llaman a `rematerializeWeek`.
* NO ejecutado Gradle (regla dura 1): verificacion pendiente del orquestador.
