package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de copy de la página larga sobre el catálogo [SetupStepDefinitions], para
 * todos los pasos que no son solo lectura de borradores antiguos (`legacyOnly`):
 *
 * - El título cabe en [MAX_TITLE_LENGTH] caracteres (44) y es una pregunta «¿…?».
 * - El subtítulo, si existe, cabe en [MAX_SUBTITLE_LENGTH] caracteres.
 *
 * Los nombres de bloque y de vista previa ([LABEL_TITLES]) son etiquetas, no
 * preguntas: quedan fuera de las dos primeras reglas. La lista es cerrada, así que
 * un título nuevo que no sea una pregunta tiene que añadirse aquí a propósito.
 */
class SetupStepCopyRulesTest {

    private val productive: List<SetupStepDefinition> =
        SetupStepDefinitions.definitions.values.filterNot { it.legacyOnly }

    @Test
    fun theCatalogHasProductiveStepsToCheck() {
        // Sin esto, un filtro roto dejaría las demás reglas comprobando una lista vacía.
        assertTrue("el catálogo productivo no puede estar vacío", productive.isNotEmpty())
    }

    @Test
    fun titlesFitInFortyFourCharactersExceptBlockAndPreviewNames() {
        val offenders = productive
            .filter { it.title !in LABEL_TITLES && it.title.length > MAX_TITLE_LENGTH }
            .map { "${it.id}: «${it.title}» (${it.title.length} caracteres)" }
        assertTrue("Títulos de más de $MAX_TITLE_LENGTH caracteres: $offenders", offenders.isEmpty())
    }

    @Test
    fun titlesAreQuestionsExceptBlockAndPreviewNames() {
        val offenders = productive
            .filter { it.title !in LABEL_TITLES && !(it.title.startsWith("¿") && it.title.endsWith("?")) }
            .map { "${it.id}: «${it.title}»" }
        assertTrue("Títulos que no son una pregunta «¿…?»: $offenders", offenders.isEmpty())
    }

    @Test
    fun subtitlesFitInOneHundredCharacters() {
        val offenders = productive
            .filter { (it.subtitle?.length ?: 0) > MAX_SUBTITLE_LENGTH }
            .map { "${it.id}: ${it.subtitle?.length} caracteres" }
        assertTrue("Subtítulos de más de $MAX_SUBTITLE_LENGTH caracteres: $offenders", offenders.isEmpty())
    }

    @Test
    fun everyLabelExceptionIsStillTheTitleOfAStep() {
        // La lista de etiquetas no puede quedarse con entradas muertas tras un cambio de copy.
        val titles = productive.mapTo(mutableSetOf()) { it.title }
        val dead = LABEL_TITLES.filterNot { it in titles }
        assertTrue("Excepciones que ya no son el título de ningún paso: $dead", dead.isEmpty())
    }

    private companion object {
        /**
         * 44 y no 40: «¿Cuál es tu porcentaje de grasa corporal?» (41) es la pregunta que pide la persona y no admite
         * un recorte sin perder «porcentaje». El título sigue cabiendo en dos líneas de `stepTitle` (28 sp) en 390 dp.
         */
        const val MAX_TITLE_LENGTH = 44
        const val MAX_SUBTITLE_LENGTH = 100

        /** Nombres de bloque, vistas previas, revisión y el alias: etiquetas, no preguntas. */
        val LABEL_TITLES = setOf(
            "Datos básicos",
            "Entreno",
            "Nutrición",
            "Rings",
            "Tu programa a medida",
            "Así queda tu semana",
            "Tu plan de alimentación",
            "Tus RINGS",
            "Revisión y activación",
            "Pon tu alias",
        )
    }
}
