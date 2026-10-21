package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * D5 (2.6): las escalas de capacidad por canal viven en [AugeParameters] y son una prueba de identidad.
 *
 * La capacidad del ancla y la del forward comparten la misma función; esta prueba compara esa función con la
 * fórmula suelta que había en el motor, bit a bit y para todos los tipos de atleta. Sin reloj real.
 */
class AugeChannelScalesTest {

    private fun tanques(tipo: AthleteType) =
        AugeFatigueEngine.calculatePersonalizedBatteryTanks(Settings(athleteType = tipo))

    @Test
    fun enthusiastTablePreservesEveryValue() {
        val tanques = tanques(AthleteType.ENTHUSIAST)
        // Antes → después: el tanque no cambia y la capacidad sale de la misma multiplicación.
        assertEquals(258.75, tanques.cns, 0.0)
        assertEquals(297.5625, AugeParameters.energyCapacity(tanques.cns), 0.0)
        assertEquals(4140.0, tanques.spinal, 0.0)
        assertEquals(82.8, AugeParameters.spinalCapacity(tanques.spinal), 0.0)
        assertEquals(2600.0, AugeFatigueEngine.getAthleteCapacity(Settings(athleteType = AthleteType.ENTHUSIAST)), 0.0)
        assertEquals(90.0, AugeParameters.C_MUSCLE, 0.0)
        assertEquals(75.0, AugeParameters.C_ENERGY, 0.0)
        assertEquals(70.0, AugeParameters.C_SPINAL, 0.0)
    }

    @Test
    fun allAthleteTypesAreBitExactAgainstTheLooseFormula() {
        for (tipo in AthleteType.entries) {
            val tanques = tanques(tipo)
            val energiaSuelta = max(80.0, tanques.cns * 1.15)
            val columnaSuelta = max(70.0, tanques.spinal * 0.02)
            assertEquals(
                "Energía $tipo: tanque ${tanques.cns}",
                energiaSuelta,
                AugeParameters.energyCapacity(tanques.cns),
                0.0,
            )
            assertEquals(
                "Columna $tipo: tanque ${tanques.spinal}",
                columnaSuelta,
                AugeParameters.spinalCapacity(tanques.spinal),
                0.0,
            )
        }
    }

    @Test
    fun minimumsKeepTheSameOrderOfOperations() {
        // Tanques por debajo del mínimo: devuelve el mínimo, como el `max` suelto.
        assertEquals(80.0, AugeParameters.energyCapacity(0.0), 0.0)
        assertEquals(80.0, AugeParameters.energyCapacity(69.56521739130434), 0.0)
        assertEquals(70.0, AugeParameters.spinalCapacity(0.0), 0.0)
        assertEquals(70.0, AugeParameters.spinalCapacity(3499.9999999999995), 0.0)
        // Umbral exacto: por debajo manda el mínimo; un pelo por encima, el producto.
        assertEquals(80.0, AugeParameters.energyCapacity(80.0 / 1.15), 0.0)
        assertEquals(70.0, AugeParameters.spinalCapacity(70.0 / 0.02), 0.0)
        assertTrue(AugeParameters.energyCapacity(80.0 / 1.15 + 1e-9) > 80.0)
        assertTrue(AugeParameters.spinalCapacity(70.0 / 0.02 + 1e-9) > 70.0)
    }
}
