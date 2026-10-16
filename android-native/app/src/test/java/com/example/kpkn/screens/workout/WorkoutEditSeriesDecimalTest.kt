package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** LR1d: el 1RM local del cajón de editar acepta coma decimal («102,5»), como el resto de campos de peso de la app. */
class WorkoutEditSeriesDecimalTest {

    @Test
    fun el1RMDelPrAceptaComaDecimal() {
        val conComa = localPrEstimated1RM("102,5", "1", TrainingMode.REPS)
        val conPunto = localPrEstimated1RM("102.5", "1", TrainingMode.REPS)
        assertNotNull("«102,5» debe dar un 1RM, igual que «102.5»", conComa)
        assertEquals(conPunto!!, conComa!!, 1e-9)
    }
}
