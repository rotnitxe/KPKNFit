package com.example.kpkn.screens.onboarding.design.entreno.plan

import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise

/*
 * La ficha del asistente reutilizada (Arreglos v1, escenas p-catalog y p-about): la misma hoja de [PlanDetailOverlay] abre un plan
 * del catálogo y «Sobre el programa». Aquí está lo que esos dos usos añaden a [PlanDetailModel] (chips, ejercicios con su imagen,
 * la semana día por día, «Cargas y cambios», el pie y la acción secundaria) y cómo se busca la imagen de un ejercicio en KPKN.
 */

/**
 * Una fila de «Ejercicios principales»: el nombre, una línea pequeña opcional («Torso · 3 × 3–5») y la imagen del ejercicio que
 * tiene KPKN ([imageResId]). Sin imagen la fila va sin miniatura: nunca un glifo, una inicial ni un icono en su lugar.
 */
data class PlanFichaExercise(val name: String, val detail: String? = null, val imageResId: Int? = null)

/** Un ejercicio de un día desplegado: su nombre y la prescripción corta («3 × 3–5 · RIR 2»). */
data class PlanFichaLine(val name: String, val prescription: String)

/**
 * Un día de «Tu semana» día por día. [day] es el día de la semana (1 = lunes … 7 = domingo); [label] su abreviatura («Lun»).
 * Un día de descanso ([rest]) va en gris y no se despliega; uno de entreno lleva [title] («Tren superior»), [subtitle]
 * («7 ejercicios · ~75 min») y [lines], que se ven al desplegarlo. [glyphOf] es el nombre que decide el glifo del día.
 */
data class PlanFichaDay(
    val day: Int,
    val label: String,
    val title: String,
    val subtitle: String? = null,
    val rest: Boolean = false,
    val lines: List<PlanFichaLine> = emptyList(),
    val glyphOf: String? = null,
)

/** El icono de una fila de una sección extra: la diana de las cargas de referencia o las flechas de un cambio. */
enum class PlanFichaRowIcon { TARGET, SWAP }

/** Una fila de una sección extra («Cargas de referencia» · «1 por resolver»). */
data class PlanFichaRow(val icon: PlanFichaRowIcon, val title: String, val subtitle: String)

/** Una sección extra, entre «Tu semana» y «Por qué este programa» («Cargas y cambios»). */
data class PlanFichaSection(val title: String, val rows: List<PlanFichaRow>)

/**
 * Lo que la ficha del catálogo y la de «Sobre el programa» traen además de [PlanDetailModel]. Lo que llega vacío no se pinta: la
 * sección desaparece.
 *
 * - [chips]: enfoque, material, alcance… debajo de la descripción.
 * - [exercises]: «Ejercicios principales» con su imagen de KPKN (sustituye a [PlanDetailModel.mainExercises]).
 * - [days]: «Tu semana» día por día, de lunes a domingo; [openDay] es el que llega desplegado. [weekNote] es el texto de «Tu semana»
 *   cuando el plan no tiene semana fija («Se genera con tus días, tu tiempo y tu material.»).
 * - [sections]: secciones extra («Cargas y cambios»).
 * - [primaryLabel]: el botón del pie fijo («Configurar este plan»); null = ficha de solo lectura, sin pie.
 * - [secondaryLabel]: la acción secundaria al final («Ver cómo funciona», «Crear una copia»).
 */
data class PlanFichaContent(
    val chips: List<String> = emptyList(),
    val exercises: List<PlanFichaExercise> = emptyList(),
    val days: List<PlanFichaDay> = emptyList(),
    val openDay: Int? = null,
    val weekNote: String? = null,
    val sections: List<PlanFichaSection> = emptyList(),
    val primaryLabel: String? = null,
    val secondaryLabel: String? = null,
)

/** Una ficha lista para pintar: el modelo de la hoja del asistente y lo que añade este uso. */
data class PlanFicha(val detail: PlanDetailModel, val content: PlanFichaContent)

/** Textos fijos de la ficha reutilizada (lienzo Arreglos v1). */
object PlanFichaCopy {
    const val EYEBROW_CATALOG = "Plan del catálogo"
    const val EYEBROW_PROGRAM = "Tu programa"
    const val EYEBROW_PROGRAM_ACTIVE = "Tu programa · Activo"
    const val REST = "Descanso"
    const val LOADS_AND_CHANGES = "Cargas y cambios"
    const val REFERENCE_LOADS = "Cargas de referencia"
    const val HOW_IT_WORKS = "Ver cómo funciona"

    /** Abreviaturas de los días, de lunes a domingo. */
    val WEEKDAY_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
}

/** Marcas de prueba de la ficha reutilizada. */
internal const val PLAN_FICHA_SECONDARY_TAG = "plan-ficha-secondary"
internal fun planFichaDayTag(day: Int): String = "plan-ficha-day-$day"

/** «~75 min»: los minutos redondeados a 5 (mínimo 5). */
fun approxMinutesLabel(minutes: Int): String = "~${roundToFive(minutes)} min"

private fun roundToFive(minutes: Int): Int = (((minutes + 2) / 5) * 5).coerceAtLeast(5)

/** Una sesión de un día de la semana: su título, sus ejercicios, sus minutos (0 = sin estimar) y el nombre que decide su glifo. */
data class PlanFichaSession(val title: String, val lines: List<PlanFichaLine>, val minutes: Int, val glyphOf: String?)

/**
 * «Tu semana» día por día: los siete días de lunes a domingo; los que no tienen sesión son «Descanso». Un día con dos sesiones las
 * junta («Tirón · y Cardio y potencia», «5 + 4 ejercicios · ~50 + 30 min»), como el miércoles del lienzo.
 */
fun planFichaWeek(sessionsByDay: Map<Int, List<PlanFichaSession>>): List<PlanFichaDay> = (1..7).map { day ->
    val label = PlanFichaCopy.WEEKDAY_SHORT[day - 1]
    val sessions = sessionsByDay[day].orEmpty()
    if (sessions.isEmpty()) {
        PlanFichaDay(day = day, label = label, title = PlanFichaCopy.REST, rest = true)
    } else {
        PlanFichaDay(
            day = day,
            label = label,
            title = sessions.joinToString(" · y ") { it.title },
            subtitle = sessionsSubtitle(sessions),
            lines = sessions.flatMap { it.lines },
            glyphOf = sessions.firstNotNullOfOrNull { it.glyphOf },
        )
    }
}

/** «7 ejercicios · ~75 min»; con dos sesiones, «5 + 4 ejercicios · ~50 + 30 min». Sin minutos, solo los ejercicios. */
private fun sessionsSubtitle(sessions: List<PlanFichaSession>): String {
    val counts = sessions.map { it.lines.size }
    val exercises = if (counts.size == 1) {
        SpanishPlurals.exercises(counts.single())
    } else {
        counts.joinToString(" + ") + " ejercicios"
    }
    val minutes = sessions.map { it.minutes }
    if (minutes.any { it <= 0 }) return exercises
    val time = if (minutes.size == 1) approxMinutesLabel(minutes.single()) else "~" + minutes.joinToString(" + ") { roundToFive(it).toString() } + " min"
    return "$exercises · $time"
}

// ---------------------------------------------------------------- la imagen de KPKN de un ejercicio

/**
 * La imagen que tiene KPKN de un ejercicio por su configuración del catálogo (lo que dan las recetas): la definición sale del
 * catálogo aprobado y la búsqueda es la de siempre ([ExerciseTechniqueImageLookup.resolveImageResId], con la configuración como
 * autoridad). Null si KPKN no tiene imagen de ese ejercicio.
 */
fun kpknImageOfConfiguration(configurationId: String?): Int? {
    val config = configurationId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val definition = CatalogDefinitionIndex.definitionOf(config)
    return ExerciseTechniqueImageLookup.resolveImageResId(
        catalogDefinitionId = definition,
        exerciseDbId = null,
        exerciseId = definition ?: config,
        catalogConfigurationId = config,
    )
}

/** La imagen de KPKN de un ejercicio de un programa: la misma búsqueda que la miniatura del editor de sesiones (`SessionExerciseThumb`). */
fun kpknImageOfExercise(exercise: Exercise): Int? = ExerciseTechniqueImageLookup.resolveImageResId(
    catalogDefinitionId = exercise.catalogDefinitionId,
    exerciseDbId = exercise.exerciseDbId,
    exerciseId = exercise.id,
    catalogConfigurationId = exercise.catalogConfigurationId,
    selectedImplementation = exercise.selectedExecutionOption ?: exercise.selectedAspects?.values?.firstOrNull(),
)

/**
 * La imagen de KPKN de un ejercicio de la semana tipo del asistente, como la ficha del ejercicio (`ExerciseInfoOverlay`): por su
 * configuración (la cambiada, si la persona lo cambió) y su definición. Sin identidad de catálogo, null.
 */
internal fun kpknImageOfRevealExercise(exercise: SetupPlanRevealExercise): Int? {
    val swapped = exercise.swappedConfigId
    val config = swapped ?: exercise.catalogConfigurationId
    // Un ejercicio cambiado ya no es de la definición original: manda su configuración.
    val definition = if (swapped != null) CatalogDefinitionIndex.definitionOf(swapped) else exercise.catalogDefinitionId
    if (config == null && definition == null) return null
    return ExerciseTechniqueImageLookup.resolveImageResId(
        catalogDefinitionId = definition,
        exerciseDbId = null,
        exerciseId = exercise.instanceId,
        catalogConfigurationId = config,
    )
}

/**
 * Las filas de «Ejercicios principales» del asistente: cada nombre con la imagen de KPKN del primer ejercicio de la semana tipo que
 * se llama igual ([byDay]). Si ninguno se llama así, [imageOf] no se consulta y la fila va sin miniatura.
 */
internal fun wizardMainExerciseRows(
    names: List<String>,
    byDay: Map<Int, List<SetupPlanRevealExercise>>,
    imageOf: (SetupPlanRevealExercise) -> Int? = ::kpknImageOfRevealExercise,
): List<PlanFichaExercise> {
    val all = byDay.toSortedMap().values.flatten()
    return names.take(MAX_MAIN_EXERCISES).map { name ->
        val match = all.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
        PlanFichaExercise(name = name, imageResId = match?.let(imageOf))
    }
}

/** Configuración del catálogo → su definición, sacado una vez del catálogo aprobado (se rehace si el catálogo cambia). */
private object CatalogDefinitionIndex {
    @Volatile
    private var cache: Pair<Any, Map<String, String>>? = null

    fun definitionOf(configurationId: String): String? {
        val catalog = approvedExerciseCatalogV2() ?: return null
        val current = cache
        val index = if (current != null && current.first === catalog) {
            current.second
        } else {
            val built = catalog.families
                .flatMap { family -> family.definitions }
                .flatMap { definition -> definition.configurations.map { it.id.trim().lowercase() to definition.id } }
                .toMap()
            cache = catalog to built
            built
        }
        return index[configurationId.trim().lowercase()]
    }
}
