package com.example.kpkn.domain.training

import com.example.kpkn.data.models.LoadQuantityConvention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H-UI: la redacción de las propuestas y avisos de progresión es español llano, sin jerga ni códigos. */
class NativeProgressionTextTest {

    @Test
    fun formatKg_usesDecimalCommaWithoutTrailingZeros() {
        assertEquals("20", NativeProgressionText.formatKg(20.0))
        assertEquals("62,5", NativeProgressionText.formatKg(62.5))
        assertEquals("21,25", NativeProgressionText.formatKg(21.25))
        assertEquals("100", NativeProgressionText.formatKg(100.0))
        assertEquals("0,5", NativeProgressionText.formatKg(0.5))
    }

    @Test
    fun timesLabel_readsAsSpanish() {
        assertEquals("una vez", NativeProgressionText.timesLabel(1))
        assertEquals("dos veces", NativeProgressionText.timesLabel(2))
        assertEquals("tres veces", NativeProgressionText.timesLabel(3))
        assertEquals("7 veces", NativeProgressionText.timesLabel(7))
    }

    @Test
    fun loadUnit_namesTheImplementOrTheBar() {
        val units = NativeProgressionText
        assertEquals(
            "kg por mancuerna",
            units.loadUnit(LoadQuantityConvention.PER_IMPLEMENT, NativeLoadConventions.StockKind.DUMBBELL),
        )
        assertEquals(
            "kg por kettlebell",
            units.loadUnit(LoadQuantityConvention.PER_IMPLEMENT, NativeLoadConventions.StockKind.KETTLEBELL),
        )
        assertEquals("kg por mano", units.loadUnit(LoadQuantityConvention.PER_IMPLEMENT, null))
        assertEquals(
            "kg en total",
            units.loadUnit(LoadQuantityConvention.TOTAL_EXTERNAL, NativeLoadConventions.StockKind.BARBELL),
        )
        assertEquals(
            "kg",
            units.loadUnit(LoadQuantityConvention.TOTAL_EXTERNAL, NativeLoadConventions.StockKind.MACHINE),
        )
        assertEquals("kg de lastre", units.loadUnit(LoadQuantityConvention.ADDITIONAL_BODYWEIGHT, null))
        assertEquals("kg", units.loadUnit(LoadQuantityConvention.ASSISTANCE, null))
        assertEquals("kg", units.loadUnit(LoadQuantityConvention.UNSPECIFIED, null))
    }

    @Test
    fun loadIncrease_matchesTheOwnersSampleSentence() {
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna.",
            NativeProgressionText.loadIncrease(
                times = 2,
                topReps = 12,
                fromKg = 20.0,
                toKg = 22.0,
                unit = "kg por mancuerna",
                assistance = false,
            ),
        )
    }

    @Test
    fun loadIncrease_withoutDeclaredMaterialAsksToPickALoad() {
        val text = NativeProgressionText.loadIncrease(
            times = 2,
            topReps = 12,
            fromKg = 20.0,
            toKg = null,
            unit = "kg",
            assistance = false,
        )

        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: en el próximo entrenamiento " +
                "elige una carga un poco mayor y deja las mismas repeticiones en reserva.",
            text,
        )
        assertTrue(text.contains("elige una carga"))
    }

    @Test
    fun loadIncrease_inAssistanceLowersTheAssistance() {
        assertEquals(
            "Lo hiciste dos veces con 10 reps en todas las series. Propuesta: bajar la asistencia de 25 a 20 kg.",
            NativeProgressionText.loadIncrease(2, 10, 25.0, 20.0, "kg", assistance = true),
        )
        assertTrue(
            NativeProgressionText.loadIncrease(2, 10, 25.0, null, "kg", assistance = true)
                .contains("elige una asistencia un poco menor"),
        )
    }

    @Test
    fun loadIncrease_fromAnUnknownLoadOmitsTheStartingPoint() {
        assertEquals(
            "Lo hiciste dos veces llegando al máximo de repeticiones en todas las series. " +
                "Propuesta: subir a 62,5 kg en total.",
            NativeProgressionText.loadIncrease(2, null, null, 62.5, "kg en total", assistance = false),
        )
    }

    @Test
    fun loadReduction_explainsWhatWentWrongAndProposesALighterLoad() {
        assertEquals(
            "Las últimas dos veces no llegaste a las 8 reps mínimas o terminaste con menos repeticiones " +
                "en reserva de las previstas. Propuesta: bajar de 22 a 20 kg por mancuerna.",
            NativeProgressionText.loadReduction(2, 8, 22.0, 20.0, "kg por mancuerna", assistance = false),
        )
        assertTrue(
            NativeProgressionText.loadReduction(2, 8, 22.0, null, "kg", assistance = false)
                .endsWith("Propuesta: en el próximo entrenamiento elige una carga un poco menor."),
        )
        assertTrue(
            NativeProgressionText.loadReduction(2, 8, 20.0, 25.0, "kg", assistance = true)
                .endsWith("Propuesta: subir la asistencia de 20 a 25 kg."),
        )
        assertTrue(
            NativeProgressionText.loadReduction(1, null, null, null, "kg", assistance = false)
                .startsWith("La última vez no llegaste al mínimo de repeticiones"),
        )
    }

    @Test
    fun bodyweightVariant_namesTheTargetVariantWhenKnown() {
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: pasar a «Flexión», una variante más difícil.",
            NativeProgressionText.bodyweightVariant(true, 2, 12, 8, "Flexión"),
        )
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: pasar a una variante más difícil.",
            NativeProgressionText.bodyweightVariant(true, 2, 12, 8, null),
        )
        assertEquals(
            "Las últimas dos veces no llegaste a las 8 reps mínimas o terminaste con menos repeticiones " +
                "en reserva de las previstas. Propuesta: pasar a «Flexión de rodillas», una variante más fácil.",
            NativeProgressionText.bodyweightVariant(false, 2, 12, 8, "Flexión de rodillas"),
        )
    }

    @Test
    fun notices_keepTheirKeyPhrasesAndExplainTheLimit() {
        assertEquals(
            "Ya no quedan sesiones sin entrenar donde aplicar esta progresión; tus registros se conservaron.",
            NativeProgressionText.noFutureSessionNotice(),
        )
        val increase = NativeProgressionText.increaseAtLimitNotice(
            2, 12, "el material declarado no tiene un par de mancuernas más pesado que 20 kg por mancuerna.",
        )
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series, pero el material declarado no tiene un par " +
                "de mancuernas más pesado que 20 kg por mancuerna. Se mantiene la carga actual; no se propone un cambio.",
            increase,
        )
        val reduce = NativeProgressionText.reduceAtLimitNotice(
            2, 8, "la carga ya es la de la barra vacía (20 kg) y no se puede bajar más.",
        )
        assertTrue(reduce, reduce.contains("barra vacía"))
        assertTrue(reduce, reduce.endsWith("Se mantiene la carga actual; considera una variante más fácil."))
        assertTrue(NativeProgressionText.noHarderVariantNotice(2, 12).startsWith("No hay una variante corporal más difícil"))
        assertTrue(NativeProgressionText.lateralVariantNotice().contains("se registró por lados"))
    }

    @Test
    fun everyText_isFreeOfInternalJargonAndIdentifiers() {
        val texts = listOf(
            NativeProgressionText.loadIncrease(2, 12, 20.0, 22.0, "kg por mancuerna", false),
            NativeProgressionText.loadIncrease(2, null, null, null, "kg", true),
            NativeProgressionText.loadReduction(2, 8, 22.0, 20.0, "kg", false),
            NativeProgressionText.loadReduction(2, null, null, null, "kg", true),
            NativeProgressionText.bodyweightVariant(true, 2, 12, 8, "Flexión"),
            NativeProgressionText.bodyweightVariant(false, 2, 12, 8, null),
            NativeProgressionText.noFutureSessionNotice(),
            NativeProgressionText.increaseAtLimitNotice(2, 12, "no hay más material."),
            NativeProgressionText.reduceAtLimitNotice(2, 8, "no hay menos material."),
            NativeProgressionText.lateralVariantNotice(),
            NativeProgressionText.noHarderVariantNotice(2, 12),
        )
        val jargon = listOf("RIR", "exposicion", "exposición", "slot", "identidad", "curada", "configuración", "__")
        texts.forEach { text ->
            jargon.forEach { word ->
                assertFalse("«$text» no debe contener «$word»", text.contains(word, ignoreCase = true))
            }
        }
    }
}
