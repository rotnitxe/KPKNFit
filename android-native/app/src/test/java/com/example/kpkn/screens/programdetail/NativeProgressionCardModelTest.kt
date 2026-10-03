package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NativeProgressionIdentity
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolution
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** H-UI: nombre del ejercicio, texto llano, avisos que caducan y confirmaciones de la tarjeta. */
class NativeProgressionCardModelTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun identity(
        configurationId: String = "bench_press__dumbbell",
        side: String = "bilateral",
        convention: LoadQuantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
        loadMode: LoadModeV2 = LoadModeV2.LOAD,
    ) = NativeProgressionIdentity(
        recipeId = "recipe",
        recipeContentVersion = 1,
        configurationId = configurationId,
        loadMode = loadMode,
        unitMode = UnitModeV2.REPS,
        side = side,
        execution = "standard",
        slotPurpose = "F",
        quantityConvention = convention,
    )

    private fun proposal(
        id: String = "p1",
        kind: NativeProgressionProposalKind = NativeProgressionProposalKind.INCREASE_LOAD,
        identity: NativeProgressionIdentity = identity(),
        targetLoadKg: Double? = 22.0,
        targetConfigurationId: String? = null,
        explanation: String = "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna.",
    ) = NativeProgressionProposal(
        proposalId = id,
        kind = kind,
        identity = identity,
        sourceSessionId = "s1",
        sourceLogIds = listOf("l1", "l2"),
        targetConfigurationId = targetConfigurationId,
        targetLoadKg = targetLoadKg,
        explanation = explanation,
        createdAtMs = 1L,
    )

    private fun notice(
        id: String,
        resolvedAtMs: Long,
        userFacing: Boolean = true,
        identity: NativeProgressionIdentity? = identity(),
        reason: String = "Ya no quedan sesiones sin entrenar donde aplicar la propuesta; no se modificó nada.",
    ) = NativeProgressionResolution(
        proposalId = id,
        status = NativeProgressionResolutionStatus.EXPIRED,
        kind = NativeProgressionProposalKind.INCREASE_LOAD,
        resolvedAtMs = resolvedAtMs,
        reason = reason,
        userFacingNotice = userFacing,
        identity = identity,
    )

    private fun program(
        exercises: List<Exercise> = listOf(
            Exercise(id = "e1", name = "Press de banca con mancuernas", catalogConfigurationId = "bench_press__dumbbell"),
        ),
        proposals: List<NativeProgressionProposal> = emptyList(),
        audit: List<NativeProgressionResolution> = emptyList(),
    ) = Program(
        id = "prog",
        name = "Programa",
        macrocycles = listOf(
            Macrocycle(
                id = "mc",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "b",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "m",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "w",
                                        name = "Semana",
                                        sessions = listOf(Session(id = "s1", name = "Día 1", exercises = exercises)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        nativeProgressionProposals = proposals,
        nativeProgressionAudit = audit,
    )

    // ─── Nombre del ejercicio ───────────────────────────────────────────────

    @Test
    fun build_titlesEachProposalWithTheExerciseNameAndNeverTheInternalId() {
        val card = NativeProgressionCardModel.build(program(proposals = listOf(proposal())), now)

        val item = card.proposals.single()
        assertEquals("p1", item.id)
        assertEquals("Press de banca con mancuernas", item.title)
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna.",
            item.body,
        )
        assertFalse((item.title + item.body).contains("__"))
        assertFalse((item.title + item.body).contains("bench_press"))
    }

    @Test
    fun exerciseName_fallsBackToTheCatalogAndThenToAReadableId() {
        val empty = program(exercises = emptyList())

        assertEquals(
            "Press banca mancuernas",
            NativeProgressionCardModel.exerciseName(empty, identity()) { "Press banca mancuernas" },
        )
        assertEquals("Bench press", NativeProgressionCardModel.exerciseName(empty, identity("bench_press__barbell")))
    }

    @Test
    fun exerciseName_namesTheSideOfUnilateralExercises() {
        val program = program()

        assertEquals(
            "Press de banca con mancuernas (lado izquierdo)",
            NativeProgressionCardModel.exerciseName(program, identity(side = "left")),
        )
        assertEquals(
            "Press de banca con mancuernas (lado derecho)",
            NativeProgressionCardModel.exerciseName(program, identity(side = "right")),
        )
    }

    @Test
    fun build_rewritesProposalsSavedBeforeTheReadableTextRelease() {
        val legacy = proposal(
            explanation = "Dos exposiciones completas en el tope con RIR suficiente. Próxima carga propuesta: 22 kg por implemento.",
        )

        val body = NativeProgressionCardModel.build(program(proposals = listOf(legacy)), now).proposals.single().body

        assertEquals(
            "Lo hiciste dos veces llegando al máximo de repeticiones en todas las series. Propuesta: subir a 22 kg por mancuerna.",
            body,
        )
    }

    @Test
    fun build_rewritesLegacyVariantProposalsWithTheCatalogNameOfTheTarget() {
        val legacy = proposal(
            kind = NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT,
            identity = identity("knee_push_up__default", convention = LoadQuantityConvention.UNSPECIFIED, loadMode = LoadModeV2.BODYWEIGHT),
            targetLoadKg = null,
            targetConfigurationId = "push_up__flat",
            explanation = "Dos exposiciones completas en el tope con RIR suficiente. Sugerir la variante corporal curada push_up__flat; la primera carga queda sin resolver y se elige por RIR.",
        )

        val body = NativeProgressionCardModel.build(
            program(exercises = emptyList(), proposals = listOf(legacy)),
            now,
        ) { id -> if (id == "push_up__flat") "Flexión" else null }.proposals.single().body

        assertEquals(
            "Lo hiciste dos veces llegando al máximo de repeticiones en todas las series. " +
                "Propuesta: pasar a «Flexión», una variante más difícil.",
            body,
        )
    }

    // ─── Avisos ─────────────────────────────────────────────────────────────

    @Test
    fun build_showsOnlyRecentUserFacingNoticesNewestFirstAndCapsThem() {
        val audit = listOf(
            notice("old", now - 8 * day),
            notice("silent", now - 1 * day, userFacing = false),
            notice("n1", now - 6 * day),
            notice("n2", now - 5 * day),
            notice("n3", now - 4 * day),
            notice("n4", now - 1 * day),
        )

        val notices = NativeProgressionCardModel.build(program(audit = audit), now).notices

        assertEquals(listOf("n4", "n3", "n2"), notices.map { it.id })
        assertTrue(notices.all { it.title == "Press de banca con mancuernas" })
    }

    @Test
    fun build_hidesANoticeJustPastItsWeek_andKeepsOneJustInsideIt() {
        val limit = NativeProgressionCardModel.NOTICE_VISIBLE_MS
        val audit = listOf(
            notice("inside", now - limit),
            notice("outside", now - limit - 1),
        )

        val notices = NativeProgressionCardModel.build(program(audit = audit), now).notices

        assertEquals(listOf("inside"), notices.map { it.id })
    }

    @Test
    fun build_titlesANoticeWithoutIdentityGenerically() {
        val audit = listOf(notice("legacy", now - day, identity = null))

        val notice = NativeProgressionCardModel.build(program(audit = audit), now).notices.single()

        assertEquals("Progresión de carga", notice.title)
    }

    @Test
    fun pendingLabel_isSingularOrPlural() {
        val one = NativeProgressionCardModel.build(program(proposals = listOf(proposal("a"))), now)
        val two = NativeProgressionCardModel.build(program(proposals = listOf(proposal("a"), proposal("b"))), now)
        val none = NativeProgressionCardModel.build(program(), now)

        assertEquals("1 progresión por revisar", one.pendingLabel)
        assertEquals("2 progresiones por revisar", two.pendingLabel)
        assertEquals(2, two.pendingCount)
        assertNull(none.pendingLabel)
        assertEquals(0, none.pendingCount)
    }

    // ─── Confirmaciones ─────────────────────────────────────────────────────

    private fun resolution(status: NativeProgressionResolutionStatus, reason: String = "x") = NativeProgressionResolution(
        proposalId = "p1",
        status = status,
        kind = NativeProgressionProposalKind.INCREASE_LOAD,
        resolvedAtMs = now,
        reason = reason,
    )

    private fun confirm(
        accepted: Boolean,
        proposal: NativeProgressionProposal?,
        resolution: NativeProgressionResolution?,
    ) = NativeProgressionCardModel.confirmation(
        accepted = accepted,
        proposal = proposal,
        resolution = resolution,
        exerciseName = "Press de banca con mancuernas",
    ) { id -> if (id == "push_up__flat") "Flexión" else null }

    @Test
    fun confirmation_whenAppliedTellsWhatChanges() {
        val applied = resolution(NativeProgressionResolutionStatus.APPLIED)

        assertEquals(
            "Listo: Press de banca con mancuernas subirá a 22 kg por mancuerna en tus próximas sesiones.",
            confirm(true, proposal(), applied),
        )
        assertEquals(
            "Listo: Press de banca con mancuernas bajará a 20 kg por mancuerna en tus próximas sesiones.",
            confirm(
                true,
                proposal(kind = NativeProgressionProposalKind.REDUCE_LOAD, targetLoadKg = 20.0),
                applied,
            ),
        )
        assertEquals(
            "Listo: en tu próxima sesión de Press de banca con mancuernas elige una carga un poco mayor.",
            confirm(true, proposal(targetLoadKg = null), applied),
        )
        assertEquals(
            "Listo: en tu próxima sesión de Press de banca con mancuernas elige una carga un poco menor.",
            confirm(
                true,
                proposal(kind = NativeProgressionProposalKind.REDUCE_LOAD, targetLoadKg = null),
                applied,
            ),
        )
    }

    @Test
    fun confirmation_inAssistanceTalksAboutAssistance() {
        val assisted = identity("pull_up__assisted__machine", convention = LoadQuantityConvention.ASSISTANCE, loadMode = LoadModeV2.ASSISTED)
        val applied = resolution(NativeProgressionResolutionStatus.APPLIED)

        assertEquals(
            "Listo: Press de banca con mancuernas tendrá menos asistencia (20 kg) en tus próximas sesiones.",
            confirm(true, proposal(identity = assisted, targetLoadKg = 20.0), applied),
        )
        assertEquals(
            "Listo: Press de banca con mancuernas tendrá más asistencia (25 kg) en tus próximas sesiones.",
            confirm(
                true,
                proposal(kind = NativeProgressionProposalKind.REDUCE_LOAD, identity = assisted, targetLoadKg = 25.0),
                applied,
            ),
        )
        assertEquals(
            "Listo: en tu próxima sesión de Press de banca con mancuernas elige una asistencia un poco menor.",
            confirm(true, proposal(identity = assisted, targetLoadKg = null), applied),
        )
    }

    @Test
    fun confirmation_forAVariantNamesTheNewExercise() {
        val applied = resolution(NativeProgressionResolutionStatus.APPLIED)
        val harder = proposal(
            kind = NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT,
            targetLoadKg = null,
            targetConfigurationId = "push_up__flat",
        )
        val unknownTarget = harder.copy(targetConfigurationId = "desconocida__variante")

        assertEquals(
            "Listo: Press de banca con mancuernas pasa a «Flexión» en tus próximas sesiones.",
            confirm(true, harder, applied),
        )
        assertEquals(
            "Listo: Press de banca con mancuernas pasa a una variante más difícil en tus próximas sesiones.",
            confirm(true, unknownTarget, applied),
        )
        assertEquals(
            "Listo: Press de banca con mancuernas pasa a una variante más fácil en tus próximas sesiones.",
            confirm(true, unknownTarget.copy(kind = NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT), applied),
        )
    }

    @Test
    fun confirmation_whenRejectedOrExpiredOrUnknown() {
        assertEquals(
            "Propuesta rechazada. Press de banca con mancuernas se queda como está.",
            confirm(false, proposal(), resolution(NativeProgressionResolutionStatus.REJECTED)),
        )
        assertEquals(
            "No se aplicó la propuesta de Press de banca con mancuernas. Ya no quedan sesiones sin entrenar donde aplicar la propuesta; no se modificó nada.",
            confirm(
                true,
                proposal(),
                resolution(
                    NativeProgressionResolutionStatus.EXPIRED,
                    "Ya no quedan sesiones sin entrenar donde aplicar la propuesta; no se modificó nada.",
                ),
            ),
        )
        assertEquals("Propuesta aplicada.", confirm(true, proposal(), null))
        assertEquals("Propuesta rechazada.", confirm(false, proposal(), null))
        assertEquals(
            "Propuesta aplicada a Press de banca con mancuernas.",
            confirm(true, null, resolution(NativeProgressionResolutionStatus.APPLIED)),
        )
    }
}
