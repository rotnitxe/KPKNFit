package com.example.kpkn.domain.biomechanics.profiles

/**
 * Lo que el data layer sabe del ejercicio (catálogo v2 + sesión). El resolver solo mira los ids: el [name] y la
 * [laterality] no deciden nada (el nombre falla con «curl femoral», «encogimientos», «hip thrust»…, y NOT_APPLICABLE
 * cubre las mancuernas). Se rellena en el data layer (`ExerciseRefResolver`, VT4c).
 *
 * @property definitionId definición del catálogo (`bench_press`); `null` si no es del catálogo.
 * @property configurationId configuración exacta (`bench_press__barbell`); solo decide los gemelos sentado
 *   (`calf_raise__bilateral__seated_machine`), el resto viaja con la referencia.
 * @property movementPatternId patrón de movimiento del catálogo (`horizontal_push`); `null` si no lo hay.
 * @property equipmentId material (`barbell`, `smith_machine`, `dumbbells`…).
 * @property isCustom ejercicio propio del usuario: sin elección del perfil no se resuelve.
 * @property userProfileOverride perfil elegido por el usuario; gana a todo.
 * @property guessedMovementPatternId ejercicio propio: conjetura de `ExercisePatternDetector` (la pone el data layer).
 */
data class ExerciseRef(
    val definitionId: String?,
    val configurationId: String?,
    val movementPatternId: String?,
    val equipmentId: String?,
    val laterality: String?,
    val name: String,
    val isCustom: Boolean,
    val userProfileOverride: AnalysisProfileId? = null,
    val guessedMovementPatternId: String? = null,
)
