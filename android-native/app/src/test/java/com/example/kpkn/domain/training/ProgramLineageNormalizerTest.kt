package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WeekVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramLineageNormalizerTest {

    @Test
    fun assignsDeterministicLineageAndSharesAcrossHomologousWeeks() {
        val program = Program(
            id = "prog-1",
            name = "Torso Pierna",
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso1",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "Semana 1",
                                            sessions = listOf(Session(id = "s1", name = "Torso", dayOfWeek = 1)),
                                        ),
                                        ProgramWeek(
                                            id = "w2",
                                            name = "Semana 2",
                                            sessions = listOf(Session(id = "s2", name = "Torso", dayOfWeek = 1)),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val normalized = ProgramLineageNormalizer.ensureSessionLineageIds(program)
        val w1Session = normalized.macrocycles.single().blocks.single().mesocycles.single().weeks[0].sessions.single()
        val w2Session = normalized.macrocycles.single().blocks.single().mesocycles.single().weeks[1].sessions.single()

        assertNotNull(w1Session.logicalLineageId)
        assertEquals(w1Session.logicalLineageId, w2Session.logicalLineageId)
        assertTrue(ProgramLineageNormalizer.hasFutureWeeksOfVariant(normalized, 0, WeekVariant.A))
        assertFalse(ProgramLineageNormalizer.hasFutureWeeksOfVariant(normalized, 1, WeekVariant.A))
    }

    @Test
    fun preservesExplicitExistingLineage() {
        val program = Program(
            id = "prog-2",
            name = "Custom",
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso1",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                Session(
                                                    id = "s1",
                                                    name = "A",
                                                    dayOfWeek = 1,
                                                    logicalLineageId = "custom:origin-1",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val normalized = ProgramLineageNormalizer.ensureSessionLineageIds(program)
        val s1 = normalized.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single()
        assertEquals("custom:origin-1", s1.logicalLineageId)
        assertFalse(ProgramLineageNormalizer.hasFutureWeeksOfVariant(normalized, 0, WeekVariant.A))
    }
}
