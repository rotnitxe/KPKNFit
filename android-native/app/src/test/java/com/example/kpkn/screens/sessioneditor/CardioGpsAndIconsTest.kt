package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.sessioneditor.components.cardioShowsGpsCell
import com.example.kpkn.screens.sessioneditor.design.cardioTypeIconSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ER2b: la celda GPS solo en los cardios al aire libre, y cada tipo de cardio con su propio icono. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CardioGpsAndIconsTest {

    @Test
    fun laCeldaGpsSoloAparecEnLosCardiosAlAireLibre() {
        assertFalse(cardioShowsGpsCell(CardioDetails(type = CardioType.AIR_BIKE)))
        assertFalse(cardioShowsGpsCell(CardioDetails(type = CardioType.BIKE_STATIONARY)))
        assertTrue(cardioShowsGpsCell(CardioDetails(type = CardioType.WALK)))
        assertTrue(cardioShowsGpsCell(CardioDetails(type = CardioType.RUN_OUTDOOR)))
    }

    /** Un cardio de interior con GPS puesto de antes conserva la celda, para poder quitarlo. */
    @Test
    fun unCardioDeInteriorConGpsPuestoConservaLaCelda() {
        assertTrue(cardioShowsGpsCell(CardioDetails(type = CardioType.AIR_BIKE, requiresGps = true)))
    }

    /**
     * Antes, la elíptica, el remo y el SkiErg compartían icono, y la escaladora y el trineo otro. Ahora solo comparten las familias
     * de la misma máquina: las cintas y las bicis. Diez iconos distintos para doce tipos.
     */
    @Test
    fun soloComparteIconoLaMismaFamiliaDeMaquina() {
        val specs = CardioType.entries.associateWith { cardioTypeIconSpec(it) }
        assertEquals(10, specs.values.toSet().size)
        assertEquals(specs.getValue(CardioType.TREADMILL), specs.getValue(CardioType.CURVED_TREADMILL))
        assertEquals(specs.getValue(CardioType.BIKE_STATIONARY), specs.getValue(CardioType.BIKE_OUTDOOR))
    }
}
