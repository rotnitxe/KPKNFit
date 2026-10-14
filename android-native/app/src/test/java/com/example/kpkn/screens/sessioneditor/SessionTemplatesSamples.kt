package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplateKind
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.Difficulty

/**
 * Datos de muestra de la pestaña Plantillas del asistente (SE4b): dos plantillas propias, una archivada, cuatro del catálogo y un índice de
 * ejercicios con la carga que necesita la fatiga. Solo los usan la hoja de render y las pruebas de la pestaña.
 */
object SessionTemplatesSamples {

    private fun info(
        id: String,
        nombre: String,
        efc: Double,
        cnc: Double,
        ssc: Double,
        vararg musculos: Pair<String, MuscleRole>,
    ) = ExerciseMuscleInfo(
        id = id,
        name = nombre,
        equipment = "barra",
        efc = efc,
        cnc = cnc,
        ssc = ssc,
        involvedMuscles = musculos.map { (muscle, role) -> InvolvedMuscle(muscle, role) },
    )

    private val press = info("press-banca", "Press banca", 3.0, 3.0, 0.7, "Pectorales" to MuscleRole.PRIMARY, "Tríceps" to MuscleRole.SECONDARY)
    private val elevaciones = info("elevaciones-laterales", "Elevaciones laterales", 1.5, 1.0, 0.2, "Deltoides" to MuscleRole.PRIMARY)
    private val triceps = info("extension-triceps", "Extensión de tríceps", 1.5, 1.0, 0.2, "Tríceps" to MuscleRole.PRIMARY)
    private val sentadilla = info("sentadilla", "Sentadilla", 3.5, 3.5, 1.4, "Cuádriceps" to MuscleRole.PRIMARY, "Glúteos" to MuscleRole.SECONDARY)
    private val prensa = info("prensa-pierna", "Prensa de pierna", 2.5, 2.5, 0.6, "Cuádriceps" to MuscleRole.PRIMARY)
    private val curl = info("curl-femoral", "Curl femoral", 1.5, 1.5, 0.3, "Isquiosurales" to MuscleRole.PRIMARY)
    private val remo = info("remo-barra", "Remo con barra", 3.0, 2.5, 1.0, "Dorsales" to MuscleRole.PRIMARY, "Bíceps" to MuscleRole.SECONDARY)

    /** Índice de ejercicios con la carga de cada uno: sin él, la fatiga de las filas no tiene datos. */
    val exerciseIndex: Map<String, ExerciseMuscleInfo> =
        listOf(press, elevaciones, triceps, sentadilla, prensa, curl, remo).associateBy { it.id }

    private fun ejercicio(id: String, datos: ExerciseMuscleInfo, series: Int): Exercise = Exercise(
        id = id,
        name = datos.name,
        catalogConfigurationId = datos.id,
        sets = (1..series).map { SessionEditorSamples.workSet("$id-$it", 6, 8, 2) },
    )

    private fun plantilla(
        id: String,
        nombre: String,
        origen: SessionTemplateSourceType,
        nivel: Difficulty,
        foco: SessionTemplateFocusCategory?,
        ejercicios: List<Exercise>,
    ): SessionTemplate = SessionTemplate(
        id = id,
        sourceType = origen,
        name = nombre,
        description = "",
        kind = SessionTemplateKind.TRAINING,
        publicationStatus = SessionTemplatePublicationStatus.VERIFIED,
        difficulty = nivel,
        focusCategory = foco,
        exerciseCount = ejercicios.size,
        session = Session(id = "$id-sesion", name = nombre, exercises = ejercicios),
    )

    /** Dos plantillas propias: una de empuje y otra de pierna, con su fatiga. */
    val propias: List<SessionTemplate> = listOf(
        plantilla(
            "u-empuje", "Empuje de hoy", SessionTemplateSourceType.USER, Difficulty.INTERMEDIO, SessionTemplateFocusCategory.PECHO,
            listOf(ejercicio("u-empuje-1", press, 4), ejercicio("u-empuje-2", elevaciones, 3), ejercicio("u-empuje-3", triceps, 3)),
        ),
        plantilla(
            "u-pierna", "Pierna pesada", SessionTemplateSourceType.USER, Difficulty.AVANZADO, SessionTemplateFocusCategory.PIERNAS,
            listOf(ejercicio("u-pierna-1", sentadilla, 5), ejercicio("u-pierna-2", prensa, 4), ejercicio("u-pierna-3", curl, 3)),
        ),
    )

    /** Una plantilla propia archivada: sale de los resultados de aplicar, pero se puede restaurar. */
    val archivadas: List<SessionTemplate> = listOf(
        plantilla(
            "u-tiron", "Tirón de prueba", SessionTemplateSourceType.USER, Difficulty.INTERMEDIO, SessionTemplateFocusCategory.ESPALDA,
            listOf(ejercicio("u-tiron-1", remo, 4)),
        ),
    )

    /** Plantillas del catálogo de KPKN, de los cuatro grupos (y una de cuerpo completo, que se ve como «Cuerpo completo»). */
    val catalogo: List<SessionTemplate> = listOf(
        plantilla(
            "c-torso", "Torso A", SessionTemplateSourceType.SYSTEM, Difficulty.INTERMEDIO, SessionTemplateFocusCategory.PECHO,
            listOf(ejercicio("c-torso-1", press, 4), ejercicio("c-torso-2", remo, 4)),
        ),
        plantilla(
            "c-pierna", "Pierna A", SessionTemplateSourceType.SYSTEM, Difficulty.INTERMEDIO, SessionTemplateFocusCategory.PIERNAS,
            listOf(ejercicio("c-pierna-1", sentadilla, 4), ejercicio("c-pierna-2", prensa, 3)),
        ),
        plantilla(
            "c-completo", "Cuerpo completo base", SessionTemplateSourceType.SYSTEM, Difficulty.PRINCIPIANTE, SessionTemplateFocusCategory.FULL_BODY,
            listOf(ejercicio("c-completo-1", sentadilla, 3), ejercicio("c-completo-2", press, 3)),
        ),
        plantilla(
            "c-brazos", "Brazos y hombros", SessionTemplateSourceType.SYSTEM, Difficulty.AVANZADO, SessionTemplateFocusCategory.BRAZOS,
            listOf(ejercicio("c-brazos-1", elevaciones, 4), ejercicio("c-brazos-2", triceps, 4)),
        ),
    )
}
