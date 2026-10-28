package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioTypeLabelsTest {
    @Test fun everyCardioTypeHasASpanishLabelThatIsNotItsInternalName() {
        CardioType.entries.forEach { type ->
            val label = CardioTypeLabels.label(type)
            assertTrue("$type sin rótulo", label.isNotBlank())
            assertFalse("$type conserva su nombre interno", label == type.name.lowercase().replace('_', ' '))
            assertFalse("$type lleva guion bajo", label.contains('_'))
        }
        assertEquals("Caminata", CardioTypeLabels.label(CardioType.WALK))
        assertEquals("Bicicleta estática", CardioTypeLabels.label(CardioType.BIKE_STATIONARY))
    }
}
