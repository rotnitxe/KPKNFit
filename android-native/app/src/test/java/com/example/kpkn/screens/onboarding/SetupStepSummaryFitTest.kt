package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.entreno.plan.ellipsizeAtWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File

/**
 * Las filas-resumen con los TEXTOS REALES de los nueve pasos de Entreno (más PLAN, la semana y las opciones de calibración) a
 * 360 y a 320 dp, con la letra al 100 y al 130 %: ninguna acaba a media palabra.
 *
 * El valor de la fila va en [WizardSpacing.SUMMARY_VALUE_LINES] líneas de Inter SemiBold de 16 sp; si no caben, `SummaryValue`
 * lo corta en la última palabra entera con «…» (`ellipsizeAtWord`) bajando un carácter por medición hasta que quepa. Aquí se
 * repite esa cuenta con la tipografía real (desde `res/font`, con las métricas de AWT sin kerning: algo más anchas que las de
 * Android, así que lo que cabe aquí cabe en el teléfono) y se parten las líneas solo en los espacios.
 */
class SetupStepSummaryFitTest {

    // ── La tipografía real y el reparto en líneas ────────────────────────────────

    private object Inter {
        private val font: Font = run {
            System.setProperty("java.awt.headless", "true")
            val candidates = listOf(File("src/main/res/font/wizard_body_semibold.ttf"), File("app/src/main/res/font/wizard_body_semibold.ttf"))
            val found = candidates.firstOrNull { it.exists() } ?: error("no encuentro la tipografía desde ${File(".").absolutePath}")
            Font.createFont(Font.TRUETYPE_FONT, found).deriveFont(1000f)
        }
        private val frc = FontRenderContext(null, true, true)

        /** Ancho en dp de [text] en Inter SemiBold de [sp] con la letra a [fontScale] (una pantalla 1x). */
        fun width(text: String, sp: Float, fontScale: Float): Float =
            (font.getStringBounds(text, frc).width * sp * fontScale / 1000.0).toFloat()
    }

    /** Una línea ya repartida: de [start] a [end] (sin el espacio del final) en el texto. */
    private data class Line(val start: Int, val end: Int)

    /** Reparte [text] en líneas de [widthDp] partiendo solo en los espacios; null si alguna palabra suelta no cabe en una línea. */
    private fun lines(text: String, widthDp: Float, fontScale: Float): List<Line>? {
        val result = mutableListOf<Line>()
        var lineStart = -1
        var lineEnd = -1
        var index = 0
        while (index < text.length) {
            if (text[index] == ' ') {
                index++
                continue
            }
            var wordEnd = index
            while (wordEnd < text.length && text[wordEnd] != ' ') wordEnd++
            val wordWidth = Inter.width(text.substring(index, wordEnd), VALUE_SP, fontScale)
            if (wordWidth > widthDp) return null
            if (lineStart < 0) {
                lineStart = index
                lineEnd = wordEnd
            } else if (Inter.width(text.substring(lineStart, wordEnd), VALUE_SP, fontScale) <= widthDp) {
                lineEnd = wordEnd
            } else {
                result += Line(lineStart, lineEnd)
                lineStart = index
                lineEnd = wordEnd
            }
            index = wordEnd
        }
        if (lineStart >= 0) result += Line(lineStart, lineEnd)
        return result
    }

    /** Lo que enseña la fila: el valor entero o, si en dos líneas no cabe, el corte que haría `SummaryValue`. */
    private fun shown(value: String, widthDp: Float, fontScale: Float): String {
        var keep = value.length
        var current = value
        repeat(60) {
            val laid = lines(current, widthDp, fontScale) ?: return current
            if (laid.size <= WizardSpacing.SUMMARY_VALUE_LINES) return current
            val visibleEnd = laid[WizardSpacing.SUMMARY_VALUE_LINES - 1].end
            val next = if (keep >= value.length) visibleEnd else minOf(visibleEnd, keep) - 1
            if (next !in 1 until keep) return current
            keep = next
            current = ellipsizeAtWord(value, keep)
        }
        return current
    }

    /** El ancho del valor en la fila: la pantalla menos márgenes (48), la marca (22), el aire (14 + 14) y el lápiz (18). */
    private fun valueWidth(screenDp: Float) = screenDp - 48f - 22f - 14f - 14f - 18f

    // ── Los textos reales ─────────────────────────────────────────────────────────

    private fun SetupWizardDraft.answered(step: SetupStepId): SetupWizardDraft =
        recordStepAnswer(step, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)

    private fun valueOf(page: SetupStepId, draft: SetupWizardDraft): String =
        setupStepSummary(page, SetupWizardState(draft = draft)).value

    private val allPlaces = TrainingPlace.entries.toSet()

    /** Cada texto que puede ver una persona en una fila de Entreno: con su paso y lo que contestó. */
    private val realTexts: List<Pair<String, String>> by lazy {
        val texts = mutableListOf<Pair<String, String>>()
        fun add(what: String, value: String) { texts += what to value }

        // Lugares: las siete combinaciones.
        for (mask in 1..7) {
            val chosen = TrainingPlace.entries.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            add("EQUIPMENT $chosen", valueOf(SetupStepId.EQUIPMENT, SetupWizardDraft().withPlaces(chosen)))
        }
        // Material: el de un gimnasio y el de los tres lugares.
        add("AVAILABILITY gimnasio", valueOf(SetupStepId.AVAILABILITY, SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM)).answered(SetupStepId.AVAILABILITY)))
        add("AVAILABILITY todo", valueOf(SetupStepId.AVAILABILITY, SetupWizardDraft().withPlaces(allPlaces).answered(SetupStepId.AVAILABILITY)))
        // Objetivo: los diez perfiles.
        for (profile in TrainingGoalProfile.entries) add("GOAL ${profile.name}", valueOf(SetupStepId.GOAL, SetupWizardDraft().withGoalProfile(profile)))
        // Día con más energía y días de entreno: todas las posibilidades de 1 a 7 días (las primeras de cada tamaño y las últimas).
        for (day in 1..7) add("FRESH_DAY $day", valueOf(SetupStepId.FRESH_DAY, SetupWizardDraft().withFreshestDay(day)))
        for (count in 1..7) {
            add("WEEKDAYS primeros $count", valueOf(SetupStepId.WEEKDAYS, SetupWizardDraft().withWeekdays((1..count).toSet())))
            add("WEEKDAYS últimos $count", valueOf(SetupStepId.WEEKDAYS, SetupWizardDraft().withWeekdays((8 - count..7).toSet())))
        }
        add("WEEKDAYS miércoles a sábado y lunes", valueOf(SetupStepId.WEEKDAYS, SetupWizardDraft().withWeekdays(setOf(1, 3, 4, 5, 6, 7))))
        // Tiempo por sesión: lo más largo («125 min»).
        for (minutes in listOf(30, 75, 125, 180)) add("SESSION_TIME $minutes", valueOf(SetupStepId.SESSION_TIME, SetupWizardDraft().withSessionMinutes(minutes)))
        // Capacidades: todas las habilidades que salen (primeras dos más «+N»).
        val skills = CapabilitySkill.entries
        add(
            "CAPABILITIES todas",
            valueOf(SetupStepId.CAPABILITIES, skills.fold(SetupWizardDraft()) { d, skill -> d.withCapability(skill, CapabilityLevel.MANY) }),
        )
        add("CAPABILITIES ninguna", valueOf(SetupStepId.CAPABILITIES, SetupWizardDraft().withCapability(skills.first(), CapabilityLevel.NONE)))
        // Músculos: los doce, y las parejas con los nombres más largos.
        add("PRIORITIES todos", valueOf(SetupStepId.PRIORITIES, SetupWizardDraft().withMuscles(MuscleSymbol.entries.take(5).toSet())))
        for (muscle in MuscleSymbol.entries) add("PRIORITIES $muscle", valueOf(SetupStepId.PRIORITIES, SetupWizardDraft().withMuscles(setOf(muscle))))
        // Marcas: una, dos y tres o más, en kg y en lb.
        val lifts = LiftMark.entries
        for (count in 1..lifts.size) {
            for (unit in listOf("kg", "lb")) {
                val draft = lifts.take(count).foldIndexed(SetupWizardDraft()) { i, d, lift -> d.withLiftMark(lift, 100.0 + 12.5 * i) }.withMarksUnit(unit)
                add("TRAINING_MAX $count $unit", valueOf(SetupStepId.TRAINING_MAX, draft))
            }
        }
        // La semana y las opciones de calibración y de experiencia.
        for (step in listOf(
            SetupStepId.EXPERIENCE, SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY,
            SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY, SetupStepId.CARDIO_TYPE, SetupStepId.CARDIO_TIME,
        )) {
            for (option in SetupStepDefinitions.options(step)) {
                add("$step ${option.value}", valueOf(step, SetupWizardDraft().withStepChoice(step, option.value, 1_700_000_000_000L)))
            }
        }
        // PLAN: el nombre editorial de cada programa del catálogo y de los «a medida».
        for (id in PersonalizedPlanCatalog.listedEntries().map { it.id } + PersonalizedPlanCatalog.GENERATED_IDS) {
            add("PLAN $id", valueOf(SetupStepId.PLAN, SetupWizardDraft(selectedCatalogId = id)))
        }
        texts
    }

    // ── Las pruebas ───────────────────────────────────────────────────────────────

    /** El corte es limpio: el valor entero, o un prefijo de palabras enteras seguido de «…». */
    private fun assertCleanCut(what: String, value: String, shown: String) {
        if (shown == value) return
        assertTrue("$what: «$shown» no acaba en «…»", shown.endsWith("…"))
        val prefix = shown.dropLast(1)
        assertTrue("$what: «$shown» no es un prefijo de «$value»", value.startsWith(prefix))
        val next = value.getOrNull(prefix.length)
        assertTrue(
            "$what: «$shown» corta «$value» a media palabra (sigue «$next»)",
            next == null || next.isWhitespace() || next in ",;:.-–—(",
        )
    }

    @Test
    fun theRealTextsAreMany() {
        // Sin esto un fallo al armar los textos dejaría las demás pruebas comprobando una lista vacía.
        assertTrue("textos reales: ${realTexts.size}", realTexts.size > 120)
        assertTrue(realTexts.any { it.first.startsWith("PLAN") })
        assertTrue(realTexts.any { it.first.startsWith("TRAINING_MAX 3") })
    }

    @Test
    fun noRealSummaryEndsInTheMiddleOfAWordAt360And320dpWithNormalAndLargeText() {
        for (screen in listOf(360f, 320f)) {
            for (scale in listOf(1f, 1.3f)) {
                val width = valueWidth(screen)
                for ((what, value) in realTexts) {
                    val laid = lines(value, width, scale)
                    assertTrue("$what a $screen dp (letra $scale): una palabra suelta no cabe en la línea: «$value»", laid != null)
                    assertCleanCut("$what a $screen dp (letra $scale)", value, shown(value, width, scale))
                }
            }
        }
    }

    @Test
    fun theTextsThatDoNotFitAreTheExceptionNotTheRule() {
        // Con 360 dp y la letra normal, cada texto real cabe entero en las dos líneas: la fila no corta nada.
        val width = valueWidth(360f)
        val cut = realTexts.filter { (_, value) -> shown(value, width, 1f) != value }
        assertTrue("textos cortados a 360 dp con letra normal: $cut", cut.isEmpty())
        // Con el 130 % pueden quedar unos pocos (listas largas de días o de marcas), nunca la mayoría.
        val wide = valueWidth(360f)
        val cutLarge = realTexts.filter { (_, value) -> shown(value, wide, 1.3f) != value }
        assertTrue("textos cortados a 360 dp con letra al 130 %: ${cutLarge.size} de ${realTexts.size}: $cutLarge", cutLarge.size <= realTexts.size / 10)
    }

    @Test
    fun theMarksListGetsShorterWithManyMarksInsteadOfCuttingNumbers() {
        val three = lifts(3)
        assertEquals("Sentadilla, Press banca +1", valueOf(SetupStepId.TRAINING_MAX, three))
        assertEquals(
            "Sentadilla 100 kg, Press banca 112,5 kg",
            valueOf(SetupStepId.TRAINING_MAX, lifts(2)),
        )
        assertEquals("Sentadilla 100 kg", valueOf(SetupStepId.TRAINING_MAX, lifts(1)))
    }

    private fun lifts(count: Int): SetupWizardDraft =
        LiftMark.entries.take(count).foldIndexed(SetupWizardDraft()) { i, d, lift -> d.withLiftMark(lift, 100.0 + 12.5 * i) }

    @Test
    fun theSummaryFitCutsAtTheLastWholeWordNeverInsideOne() {
        val value = "Fuerza y masa muscular a medida con un nombre larguísimo que no cabe en dos líneas de ninguna manera"
        val cut = shown(value, valueWidth(360f), 1f)
        assertTrue(cut, cut.endsWith("…"))
        assertCleanCut("nombre largo", value, cut)
        assertTrue("cabe en dos líneas: «$cut»", (lines(cut, valueWidth(360f), 1f) ?: emptyList()).size <= 2)
    }

    @Test
    fun theSummaryRowIsTallEnoughForTwoLinesAtEveryScale() {
        // Etiqueta (18) y dos líneas de valor (21) crecen con la letra; el aire (12) no.
        for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            val needed = 60f * scale + 12f
            assertTrue("a $scale la fila mide ${WizardSpacing.summaryRowHeightFor(scale).value}", WizardSpacing.summaryRowHeightFor(scale).value >= needed)
        }
    }

    private companion object {
        const val VALUE_SP = 16f
    }
}
