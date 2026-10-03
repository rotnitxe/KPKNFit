package com.example.kpkn.domain.training

import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.AdaptationPolicy
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.definitions.NativeCandidateTable
import com.example.kpkn.data.protocols.definitions.NativeCardioDefaults
import com.example.kpkn.data.protocols.definitions.NativeDoseLevel
import com.example.kpkn.data.protocols.definitions.NativeDoseTable
import com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.protocols.definitions.NativeWeekBuilder
import com.example.kpkn.data.protocols.definitions.NativeSlotKey
import com.example.kpkn.data.protocols.definitions.parseNativeArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * AC-F1 de T-004a: los cuatro planes propios existen con su disciplina real,
 * calendarios 1..6 (incluido 1 día), dosis/descansos/RIR por nivel, semana 6
 * de descarga y candidatos de configuración APPROVED en los dos assets.
 */
class NativeProfileSpecCatalogTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val ownIds = listOf(
        NativeProfileKind.STRENGTH.entryId,
        NativeProfileKind.MUSCLE.entryId,
        NativeProfileKind.POWERBUILDING.entryId,
        NativeProfileKind.COMPLETE_ATHLETE.entryId,
    )

    @Test
    fun four_own_profiles_are_published_with_real_discipline() {
        val entries = PersonalizedPlanCatalog.entries()
        val own = ownIds.map { id -> entries.first { it.id == id } }
        own.forEach { entry ->
            assertEquals("${entry.id} fuente", CatalogSource.NATIVE, entry.source)
            assertEquals("${entry.id} publicación", PublicationState.PUBLISHED, entry.publication)
            assertEquals("${entry.id} adaptación", AdaptationPolicy.CURATED_WEEKLY, entry.adaptation)
            assertEquals("${entry.id} frecuencias", 1..6, entry.supportedFrequencies)
            assertEquals("${entry.id} duración", CatalogDuration.FINITE_CYCLE, entry.duration)
            assertEquals("${entry.id} nivel base", CatalogLevel.BEGINNER, entry.level)
            assertEquals(
                "${entry.id} niveles",
                setOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED),
                entry.levels,
            )
            assertTrue("${entry.id} sin título", entry.title.isNotBlank())
            assertTrue("${entry.id} sin descripción", entry.description.isNotBlank())
        }
        // §11.1: cada disciplina etiquetada con su prescripción real.
        assertEquals(
            setOf(TrainingReference.POWERLIFTING),
            entries.first { it.id == NativeProfileKind.STRENGTH.entryId }.references,
        )
        assertEquals(
            setOf(TrainingReference.HYPERTROPHY),
            entries.first { it.id == NativeProfileKind.MUSCLE.entryId }.references,
        )
        assertEquals(
            setOf(TrainingReference.POWERBUILDING),
            entries.first { it.id == NativeProfileKind.POWERBUILDING.entryId }.references,
        )
        val athlete = entries.first { it.id == NativeProfileKind.COMPLETE_ATHLETE.entryId }
        assertTrue("Atleta no hereda una sola disciplina", athlete.references.isEmpty())
        assertEquals(
            setOf(TrainingCapability.STRENGTH, TrainingCapability.HYPERTROPHY, TrainingCapability.POWER, TrainingCapability.CARDIO),
            athlete.capabilities,
        )
        assertEquals(
            setOf(TrainingCapability.STRENGTH),
            entries.first { it.id == NativeProfileKind.STRENGTH.entryId }.capabilities,
        )
    }

    @Test
    fun historical_native_families_are_untouched() {
        val natives = PersonalizedPlanCatalog.entries()
            .filter { it.source == CatalogSource.NATIVE }
            .associateBy { it.sourceId }
        val historical = mapOf(
            "full-body" to (2..3),
            "gym-muscle" to (3..6),
            "machine-muscle" to (2..4),
            "home-training" to (2..4),
            "bodyweight" to (2..4),
            "return-training" to (2..3),
            "one-day" to (1..1),
            "strength-cardio" to (1..6),
        )
        historical.forEach { (sourceId, range) ->
            val entry = requireNotNull(natives[sourceId]) { "falta la familia histórica $sourceId" }
            assertEquals("rango de $sourceId", range, entry.supportedFrequencies)
            assertEquals("id de $sourceId", "native:$sourceId", entry.id)
        }
        // §14.3: el prefilto mixto legacy sigue apuntando solo a strength-cardio.
        val cardioScheduling = PersonalizedPlanCatalog.entries().filter { it.schedulesCardio }.map { it.id }
        assertEquals(listOf("native:strength-cardio"), cardioScheduling)
        assertEquals(12, natives.size)
    }

    @Test
    fun calendars_cover_days_1_to_6_including_single_day() {
        (1..6).forEach { days ->
            val strength = NativeProfileCalendars.strength(days)
            val muscleGeneral = NativeProfileCalendars.muscle(days, pullAvailable = true)
            val muscleNoPull = NativeProfileCalendars.muscle(days, pullAvailable = false)
            val powerbuilding = NativeProfileCalendars.powerbuilding(days)
            val athlete = NativeProfileCalendars.athlete(days, pullAvailable = true)
            val athleteNoPull = NativeProfileCalendars.athlete(days, pullAvailable = false)
            listOf(strength, muscleGeneral, muscleNoPull, powerbuilding, athlete, athleteNoPull).forEach { calendar ->
                assertEquals("calendario de $days días", days, calendar.size)
                assertTrue(calendar.all { it.slots.isNotEmpty() || it.cardio.name == "ONLY" })
            }
            // Los cuatro componentes de Atleta viven en la semana (§11.4):
            // ≥2 días = dos bloques de cardio con 2 o ≥4 días; con 3 días hay
            // exactamente un bloque dedicado (el segundo tras XA es opcional).
            if (days >= 2) {
                assertTrue("Atleta $days días sin potencia", athlete.count { day -> day.slots.any { it.intent == SlotIntent.P } } >= 2)
                val cardioBlocks = athlete.count { it.cardio != com.example.kpkn.data.protocols.definitions.NativeCardioRole.NONE }
                if (days == 3) {
                    assertTrue("Atleta 3 días debe tener el bloque dedicado", cardioBlocks >= 1)
                } else {
                    assertTrue("Atleta $days días con $cardioBlocks bloques de cardio", cardioBlocks >= 2)
                }
            }
            if (days == 3) {
                assertEquals(
                    listOf(NativeSlotKey.U, NativeSlotKey.C),
                    athlete[2].slots.map { it.key },
                )
            }
            if (days == 5) {
                assertEquals(
                    "el quinto día de Atleta termina con cardio + core, sin un accesorio U añadido",
                    listOf(NativeSlotKey.C),
                    athlete[4].slots.map { it.key },
                )
            }
            if (days <= 4) {
                assertTrue("Músculo $days días conserva BL sin sustitución", muscleNoPull
                    .filter { it.name == "BL" }
                    .all { day -> day.slots.map { it.key } == listOf(NativeSlotKey.S, NativeSlotKey.BG, NativeSlotKey.U, NativeSlotKey.G) })
            }
            if (days in 5..6) {
                val lowerDays = muscleNoPull.filter { it.name == "BL" }
                assertEquals("Músculo $days días conserva tres exposiciones inferiores", 3, lowerDays.size)
                assertEquals("Músculo $days días modifica solo dos puentes BL", 2, lowerDays.count { day ->
                    NativeSlotKey.B in day.slots.map { it.key } && NativeSlotKey.BG !in day.slots.map { it.key }
                })
                assertEquals("Músculo $days días conserva un puente BL", 1, lowerDays.count { day ->
                    NativeSlotKey.BG in day.slots.map { it.key }
                })
            }
            if (days in 1..5) {
                assertTrue("Atleta $days días conserva su asignación corporal previa", athleteNoPull
                    .filter { it.name.startsWith("XH") }
                    .all { day -> NativeSlotKey.BG in day.slots.map { it.key } })
            }
            if (days == 6) {
                val adaptedXh = athleteNoPull.single { it.name == "XH" }
                val adaptedD6 = athleteNoPull.last()
                assertEquals("Atleta 6 días cambia solo el puente de XH", listOf(
                    NativeSlotKey.S, NativeSlotKey.B, NativeSlotKey.B, NativeSlotKey.U, NativeSlotKey.C,
                ), adaptedXh.slots.map { it.key })
                assertEquals("Atleta 6 días cambia solo el puente de D6", listOf(
                    NativeSlotKey.U, NativeSlotKey.B, NativeSlotKey.B, NativeSlotKey.C,
                ), adaptedD6.slots.map { it.key })
            }
        }
        // Día dedicado de cardio con 3 días (§11.4).
        assertTrue(
            NativeProfileCalendars.athlete(3, pullAvailable = true)
                .any { it.cardio == com.example.kpkn.data.protocols.definitions.NativeCardioRole.DEDICATED_WITH_ACCESSORIES },
        )
        // Defaults de día de la semana §11.3.
        assertEquals(listOf(1), NativeProfileCalendars.DEFAULT_WEEKDAYS[1])
        assertEquals(listOf(1, 4), NativeProfileCalendars.DEFAULT_WEEKDAYS[2])
        assertEquals(listOf(1, 3, 5), NativeProfileCalendars.DEFAULT_WEEKDAYS[3])
        assertEquals(listOf(1, 2, 4, 5), NativeProfileCalendars.DEFAULT_WEEKDAYS[4])
        assertEquals(listOf(1, 2, 4, 5, 6), NativeProfileCalendars.DEFAULT_WEEKDAYS[5])
        assertEquals(listOf(1, 2, 3, 4, 5, 6), NativeProfileCalendars.DEFAULT_WEEKDAYS[6])
    }

    @Test
    fun dose_table_matches_section_11_2_exactly() {
        // F
        val fBeg = NativeDoseTable.doseFor(SlotIntent.F, NativeDoseLevel.BEGINNER, false)
        assertEquals(SlotRole.T1_MAIN, fBeg.role)
        assertEquals(2, fBeg.sets)
        assertEquals(4, fBeg.repsMin)
        assertEquals(6, fBeg.repsMax)
        assertEquals(3, fBeg.rir)
        assertEquals(180, fBeg.restSeconds)
        val fInt = NativeDoseTable.doseFor(SlotIntent.F, NativeDoseLevel.INTERMEDIATE, false)
        assertEquals(3, fInt.sets)
        assertEquals(3, fInt.repsMin)
        assertEquals(5, fInt.repsMax)
        assertEquals(2, fInt.rir)
        assertEquals(180, fInt.restSeconds)
        // Fv
        val fv = NativeDoseTable.doseFor(SlotIntent.FV, NativeDoseLevel.BEGINNER, false)
        assertEquals(SlotRole.T2_SUPPLEMENTAL, fv.role)
        assertEquals(2, fv.sets)
        assertEquals(6, fv.repsMin)
        assertEquals(8, fv.repsMax)
        assertEquals(120, fv.restSeconds)
        assertEquals(3, fv.rir)
        assertEquals(2, NativeDoseTable.doseFor(SlotIntent.FV, NativeDoseLevel.ADVANCED, false).sets)
        assertEquals(2, NativeDoseTable.doseFor(SlotIntent.FV, NativeDoseLevel.ADVANCED, false).rir)
        // H
        val hBeg = NativeDoseTable.doseFor(SlotIntent.H, NativeDoseLevel.BEGINNER, false)
        assertEquals(2, hBeg.sets)
        assertEquals(12, hBeg.repsMax)
        assertEquals(120, hBeg.restSeconds)
        val hInt = NativeDoseTable.doseFor(SlotIntent.H, NativeDoseLevel.INTERMEDIATE, false)
        assertEquals(3, hInt.sets)
        assertEquals(2, hInt.rir)
        // Cuerpo/bandas: H 8–15 (§11.2 nota).
        assertEquals(15, NativeDoseTable.doseFor(SlotIntent.H, NativeDoseLevel.INTERMEDIATE, true).repsMax)
        // I
        val iBeg = NativeDoseTable.doseFor(SlotIntent.I, NativeDoseLevel.BEGINNER, false)
        assertEquals(1, iBeg.sets)
        assertEquals(10, iBeg.repsMin)
        assertEquals(15, iBeg.repsMax)
        assertEquals(90, iBeg.restSeconds)
        assertEquals(2, NativeDoseTable.doseFor(SlotIntent.I, NativeDoseLevel.ADVANCED, false).sets)
        // C
        val cBeg = NativeDoseTable.doseFor(SlotIntent.C, NativeDoseLevel.BEGINNER, false)
        assertEquals(1, cBeg.sets)
        assertEquals(60, cBeg.restSeconds)
        assertEquals(2, NativeDoseTable.doseFor(SlotIntent.C, NativeDoseLevel.INTERMEDIATE, false).sets)
        // P
        val p = NativeDoseTable.doseFor(SlotIntent.P, NativeDoseLevel.ADVANCED, false)
        assertEquals(SlotRole.SPEED, p.role)
        assertEquals(3, p.sets)
        assertEquals(3, p.repsMin)
        assertEquals(5, p.rir)
        assertEquals(90, p.restSeconds)
        assertEquals(2, NativeDoseTable.doseFor(SlotIntent.P, NativeDoseLevel.BEGINNER, false).sets)
        // Descarga §12.1
        assertEquals(2, NativeDoseTable.deloadSets(3))
        assertEquals(1, NativeDoseTable.deloadSets(2))
        assertEquals(1, NativeDoseTable.deloadSets(1))
    }

    @Test
    fun week_plan_is_six_weeks_with_deload_split() {
        assertEquals(6, NativeWeekBuilder.WEEKS)
        assertEquals(6, NativeWeekBuilder.DELOAD_WEEK)
        // RIR §12.1: principiante RIR 3 siempre; intermedio/avanzado RIR 3 en
        // semana 1 y RIR 2 en semanas 2–5.
        (1..5).forEach { week -> assertEquals(3, NativeWeekBuilder.trainingRir(NativeDoseLevel.BEGINNER, week)) }
        assertEquals(3, NativeWeekBuilder.trainingRir(NativeDoseLevel.INTERMEDIATE, 1))
        (2..5).forEach { week -> assertEquals(2, NativeWeekBuilder.trainingRir(NativeDoseLevel.INTERMEDIATE, week)) }
    }

    @Test
    fun cardio_defaults_and_escalation_follow_section_11_4_and_12_4() {
        assertEquals(10, NativeCardioDefaults.defaultBlockMinutes(44))
        assertEquals(15, NativeCardioDefaults.defaultBlockMinutes(45))
        assertEquals(15, NativeCardioDefaults.defaultBlockMinutes(59))
        assertEquals(20, NativeCardioDefaults.defaultBlockMinutes(60))
        assertEquals(20, NativeCardioDefaults.defaultDedicatedMinutes(60))
        assertEquals(10, NativeCardioDefaults.defaultDedicatedMinutes(30))
        assertEquals(listOf(10, 15, 20, 30), NativeCardioDefaults.OFFERED_MINUTES)
    }

    @Test
    fun every_candidate_configuration_is_approved_in_both_assets() {
        val catalog = CatalogCompositionTestSupport.catalog
        val approved = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
            .filter { it.evidence.reviewStatus == com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2.APPROVED }
            .map { it.id }
            .toSet()
        val keys = NativeSlotKey.entries
        val used = keys.flatMap { key ->
            SlotIntent.entries.flatMap { intent ->
                NativeCandidateTable.candidatesFor(key, intent)
            }
        }.toSet()
        val missing = used - approved
        assertTrue("candidatos no APPROVED en el catálogo: $missing", missing.isEmpty())
        // Los arquetipos parsean y las claves son conocidas.
        parseNativeArchetype("PS:P,B:F,S:Fv,R:H,C:C").let { slots ->
            assertEquals(5, slots.size)
            assertEquals(SlotIntent.P, slots[0].intent)
            assertEquals(SlotIntent.FV, slots[2].intent)
        }
        assertFalse(NativeProfileKind.fromEntryId("native:full-body") != null)
        assertNotNull(NativeProfileKind.fromEntryId("native:complete-athlete-v2"))
    }
}
