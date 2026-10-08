package com.example.kpkn.screens.onboarding.design.entreno.plan

import com.example.kpkn.data.programs.PlanEditorialTable
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import kotlin.math.min

/**
 * El ajuste de los títulos de marca (`fitTitle`): la lógica, con un medidor sintético, y los TÍTULOS REALES del catálogo de
 * portadas medidos con la tipografía real (Syne, desde `res/font`, con las métricas de AWT sin kerning: algo más anchas que las
 * de Android, así que lo que cabe aquí cabe en el teléfono).
 *
 * El criterio es el del paquete: ninguna palabra se parte ni acaba en «…»; a 360 dp, con la letra al 100 % y al 130 % (y con el
 * 200 %, que estos títulos topan en el 130 %), cada título cabe a 13 sp o más. En una pantalla de 320 dp con la letra grande tres
 * títulos bajan hasta 11 sp (el último recurso): ahí lo que se exige es que cada palabra quepa entera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanFitTest {

    // ------------------------------------------------------------ la lógica, con un medidor sintético

    /** Cada letra mide [em] veces el tamaño; un texto se parte entre palabras donde no cabe. */
    private class Synthetic(private val em: Float = 0.6f) : TitleMeasure {
        override fun wordWidthPx(word: String, sp: Float): Float = word.length * em * sp

        override fun lineCount(text: String, sp: Float, widthPx: Float): Int = greedyLines(text, widthPx) { wordWidthPx(it, sp) }
    }

    @Test
    fun theLargestSizeWhereEveryWordFitsWins() {
        // «Powerbuilding» (13 letras × 0,6): cabe en 150 px a 19 sp (148,2) y no a 20 (156).
        val fit = fitTitle("Powerbuilding a medida", 150f, 25f, 13f, 3, Synthetic())
        assertEquals(19f, fit.sp, 0f)
        assertTrue(fit.wordsFit)
        assertTrue(fit.lines <= 3)
    }

    @Test
    fun aTitleThatFitsAtTheMaximumIsNotTouched() {
        val fit = fitTitle("Fuerza KPKN", 300f, 24f, 13f, 3, Synthetic())
        assertEquals(24f, fit.sp, 0f)
        assertEquals(1, fit.lines)
    }

    @Test
    fun whenTheLinesDoNotFitAtTheMinimumTheTitleTakesTheLinesItNeedsInsteadOfBeingCut() {
        // Cuatro palabras de 12 letras en 100 px: a 14 sp cada una mide 100,8 → no caben; a 13 sp (93,6) sí, pero una por línea.
        val text = "Repeticiones Repeticiones Repeticiones Repeticiones"
        val fit = fitTitle(text, 100f, 20f, 13f, 3, Synthetic())
        assertTrue(fit.wordsFit)
        assertEquals(13f, fit.sp, 0f)
        assertTrue("sin cortar: ${fit.lines} líneas", fit.lines > 3)
    }

    @Test
    fun belowTheMinimumOnlyForAWordThatDoesNotFitAtIt() {
        // «Complemento» (11 letras): a 13 sp mide 85,8 px; en 80 px solo cabe por debajo del mínimo (12 sp = 79,2).
        val fit = fitTitle("Complemento de peso", 80f, 20f, 13f, 3, Synthetic())
        assertEquals(12f, fit.sp, 0f)
        assertTrue(fit.wordsFit)
    }

    @Test
    fun saysSoWhenNoSizeFits() {
        val fit = fitTitle("Complemento", 40f, 20f, 13f, 3, Synthetic())
        assertFalse(fit.wordsFit)
        assertEquals(13f - TITLE_HARD_MIN_DELTA_SP, fit.sp, 0f)
    }

    @Test
    fun theLegacyShrinkStillStopsAtItsMinimum() {
        assertEquals(10f, fitTitleSp(24f, 8f) { it * 10f <= 100f }, 0f)
        assertEquals(14f, fitTitleSp(24f, 14f) { false }, 0f)
    }

    @Test
    fun theCardGrowsWithTheLargeTextAndKeepsItsCap() {
        // 312 dp de página (360 dp de pantalla): dos tercios con letra normal, tres cuartos con el 130 %, y nunca más de 300 dp.
        assertEquals(312f * 0.66f, carouselCardWidthDp(312f, 1f), 0.01f)
        assertEquals(312f * 0.74f, carouselCardWidthDp(312f, 1.3f), 0.01f)
        assertEquals(312f * 0.74f, carouselCardWidthDp(312f, 2f), 0.01f)
        assertEquals(300f, carouselCardWidthDp(900f, 1f), 0f)
        assertTrue(carouselCardWidthDp(312f, 1.15f) in carouselCardWidthDp(312f, 1f)..carouselCardWidthDp(312f, 1.3f))
    }

    @Test
    fun theBannerGivesTheTextMoreRoomWithLargeText() {
        assertEquals(1.25f, bannerTextWeight(1f), 0.0001f)
        assertEquals(1.9f, bannerTextWeight(1.3f), 0.0001f)
        assertEquals(1.9f, bannerTextWeight(2f), 0.0001f)
        assertTrue(bannerTitleWidthDp(360f, 1.3f) > bannerTitleWidthDp(360f, 1f))
    }

    // ------------------------------------------------------------ los títulos reales, con la tipografía real

    /** La tipografía real medida con AWT: `advance` en unidades de 1000 por em. */
    private class Syne(file: String, private val fontScale: Float, private val letterSpacingSp: Float) : TitleMeasure {
        private val font: Font = run {
            System.setProperty("java.awt.headless", "true")
            val candidates = listOf(File("src/main/res/font/$file"), File("app/src/main/res/font/$file"))
            val found = candidates.firstOrNull { it.exists() } ?: error("no encuentro $file desde ${File(".").absolutePath}")
            Font.createFont(Font.TRUETYPE_FONT, found).deriveFont(1000f)
        }
        private val frc = FontRenderContext(null, true, true)

        private fun width(text: String, sp: Float): Float {
            // Estos títulos topan la letra del sistema en el 130 % (TITLE_FONT_SCALE_CAP): sp × escala = dp en una pantalla 1x.
            val scale = min(fontScale, TITLE_FONT_SCALE_CAP)
            val advance = font.getStringBounds(text, frc).width
            return (advance * sp * scale / 1000.0 + text.length * letterSpacingSp * scale).toFloat()
        }

        override fun wordWidthPx(word: String, sp: Float): Float = width(word, sp)

        override fun lineCount(text: String, sp: Float, widthPx: Float): Int = greedyLines(text, widthPx) { width(it, sp) }
    }

    /** Los 65 títulos del catálogo (los propios, los de autor y los diez «a medida» del generador). */
    private val titles: List<String> = PlanEditorialTable.byId.values.map { it.displayName }.distinct()

    private fun coverLetterSpacing(): Float = coverTitleStyle(14f).letterSpacing.value

    private data class Cover(val title: String, val fit: TitleFit)

    private fun posterFits(screenDp: Float, fontScale: Float): List<Cover> {
        val card = carouselCardWidthDp(screenDp - 48f, fontScale)
        val measure = Syne("syne_extrabold.ttf", fontScale, coverLetterSpacing())
        return titles.map { Cover(it, fitTitle(it, posterTitleWidthDp(card), posterTitleMaxSp(card), COVER_TITLE_MIN_SP, 3, measure)) }
    }

    private fun bannerFits(screenDp: Float, fontScale: Float): List<Cover> {
        val measure = Syne("syne_extrabold.ttf", fontScale, coverLetterSpacing())
        return titles.map { Cover(it, fitTitle(it, bannerTitleWidthDp(screenDp, fontScale), 25f, COVER_TITLE_MIN_SP, 3, measure)) }
    }

    private fun assertAllFitAtTheMinimumOrMore(what: String, covers: List<Cover>) {
        for ((title, fit) in covers) {
            assertTrue("$what: «$title» no cabe entera (${fit.sp} sp)", fit.wordsFit)
            assertTrue("$what: «$title» baja de ${COVER_TITLE_MIN_SP} sp (${fit.sp})", fit.sp >= COVER_TITLE_MIN_SP)
            assertTrue("$what: «$title» ocupa ${fit.lines} líneas", fit.lines <= 5)
        }
    }

    private fun assertEveryWordFits(what: String, covers: List<Cover>) {
        for ((title, fit) in covers) assertTrue("$what: «$title» no cabe entera ni al último tamaño (${fit.sp} sp)", fit.wordsFit)
    }

    @Test
    fun theCatalogHasTheTitlesTheBriefSpeaksAbout() {
        assertTrue("65 fichas, 10 de ellas «a medida»", titles.size >= 60)
        assertTrue(titles.any { it.startsWith("Powerbuilding") })
        assertTrue(titles.any { it.startsWith("Complemento") })
    }

    @Test
    fun everyCoverTitleFitsInThePosterAt360dpWithNormalAndLargeLetters() {
        for (scale in listOf(1f, 1.3f, 2f)) assertAllFitAtTheMinimumOrMore("cartel 360 dp, letra $scale", posterFits(360f, scale))
    }

    @Test
    fun everyCoverTitleFitsInTheDetailBannerAt360dpWithNormalAndLargeLetters() {
        for (scale in listOf(1f, 1.3f, 2f)) assertAllFitAtTheMinimumOrMore("banda 360 dp, letra $scale", bannerFits(360f, scale))
    }

    @Test
    fun everyCoverTitleFitsOnAWidePhone() {
        for (scale in listOf(1f, 1.3f)) {
            assertAllFitAtTheMinimumOrMore("cartel 411 dp, letra $scale", posterFits(411f, scale))
            assertAllFitAtTheMinimumOrMore("banda 411 dp, letra $scale", bannerFits(411f, scale))
        }
    }

    @Test
    fun onANarrowPhoneEveryWordStillFitsEvenIfThreeTitlesGoDownToTheLastSize() {
        for (scale in listOf(1f, 1.3f)) {
            assertEveryWordFits("cartel 320 dp, letra $scale", posterFits(320f, scale))
            assertEveryWordFits("banda 320 dp, letra $scale", bannerFits(320f, scale))
        }
        // Con la letra normal, solo el título más largo del catálogo baja del mínimo.
        val below = posterFits(320f, 1f).filter { it.fit.sp < COVER_TITLE_MIN_SP }
        assertTrue("bajan del mínimo: ${below.map { it.title }}", below.size <= 1)
        // Y con el 130 %, a lo sumo tres («Powerbuilding a medida», «Armwrestling a medida» y el complemento de peso muerto).
        val belowLarge = posterFits(320f, 1.3f).filter { it.fit.sp < COVER_TITLE_MIN_SP }
        assertTrue("bajan del mínimo con letra grande: ${belowLarge.map { it.title }}", belowLarge.size <= 3)
    }

    @Test
    fun theNamesOfTheGoalRowsFitAt360dpAndAtLargeText() {
        // El nombre de una fila de objetivo: la fila de 312 dp menos el símbolo (56), los márgenes (14 + 4) y la marca de «hecho» (26).
        val width = goalNameWidthDp(312f)
        assertEquals(212f, width, 0.01f)
        for (scale in listOf(1f, 1.3f)) {
            val measure = Syne("syne_extrabold.ttf", scale, -0.1f)
            for (profile in TrainingGoalProfile.entries) {
                val fit = fitTitle(profile.label, width, 17f, 13f, 3, measure)
                assertTrue("«${profile.label}» no cabe entera a letra $scale (${fit.sp} sp)", fit.wordsFit && fit.sp >= 13f)
            }
        }
    }

    private companion object {
        /** El reparto de líneas más simple: cada palabra a su línea cuando no cabe junto a la anterior (una palabra más ancha ocupa la suya). */
        fun greedyLines(text: String, widthPx: Float, widthOf: (String) -> Float): Int {
            var lines = 1
            var current = ""
            for (word in text.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isEmpty() || widthOf(candidate) <= widthPx) {
                    current = candidate
                } else {
                    lines++
                    current = word
                }
            }
            return lines
        }
    }
}
