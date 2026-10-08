package com.example.kpkn.screens.onboarding.design.entreno.plan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La descripción plegada del detalle del programa nunca acaba en una palabra partida («peso muert…» con letra grande): se corta
 * en la última palabra entera que cabe y termina en «…».
 */
class EllipsizeAtWordTest {

    @Test
    fun aCutInTheMiddleOfAWordDropsThatWord() {
        // «uno dos tre|s cuatro»: la palabra que queda a medias se descarta.
        assertEquals("uno dos…", ellipsizeAtWord("uno dos tres cuatro", 11))
    }

    @Test
    fun aCutRightBeforeASpaceKeepsTheWholeWord() {
        assertEquals("uno dos tres…", ellipsizeAtWord("uno dos tres cuatro", 12))
    }

    @Test
    fun loosePunctuationBeforeTheCutGoesToo() {
        assertEquals("Hola, mundo…", ellipsizeAtWord("Hola, mundo. Adiós a todos", 12))
        assertEquals("Sentadilla, banca y peso…", ellipsizeAtWord("Sentadilla, banca y peso muerto, unos 75 min", 28))
    }

    @Test
    fun aTextThatAlreadyFitsIsReturnedAsIs() {
        assertEquals("corto", ellipsizeAtWord("corto", 5))
        assertEquals("corto", ellipsizeAtWord("corto", 99))
    }

    @Test
    fun aSingleLongWordIsTheOnlyCaseThatStillCuts() {
        assertEquals("Supercal…", ellipsizeAtWord("Supercalifragilistico", 8))
    }

    @Test
    fun aCutAtTheStartLeavesJustTheEllipsis() {
        assertEquals("…", ellipsizeAtWord("uno dos", 0))
    }
}
