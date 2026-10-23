package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.domain.training.ProgramAutoregulationEngine
import com.example.kpkn.domain.training.Cal5TestSupport.recipeOf
import com.example.kpkn.domain.training.Cal5TestSupport.templateRecipeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** CAL5 · la vista previa pura «qué provocará este AMRAP según su programa» para la tarjeta en vivo (CAL3). */
class AmrapProgramPreviewTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val profile = Cal5TestSupport.fullProfile()
    private val lowTm = PowerliftingProfile(squat1RM = 167.0, squatTM = 150.0)

    private fun preview(
        id: String,
        hit: AmrapHit,
        mode: AutoregulationMode = AutoregulationMode.PROPOSE,
        p: PowerliftingProfile? = profile,
        frozenBefore: Int? = null,
        slotRole: SlotRole? = null,
        plannedVolumeReps: Int? = null,
        tierCheck: ProgramAutoregulationEngine.GzclpTierCheck? = null,
    ) = AmrapProgramPreview.preview(
        recipeOf(id), mode, p, hit, frozenBefore, tierCheck, slotRole, plannedVolumeReps,
    )

    // ─── nSuns ───────────────────────────────────────────────────────────────────

    @Test
    fun nsuns_shows_the_tm_change_of_the_table_and_who_decides_it() {
        val hit = AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 1, actualReps = 5, weightKg = 171.0)

        val propose = preview("nsuns-531-lp-4d", hit)
        assertEquals("nSuns: TM +5 kg la semana próxima.", propose.headline)
        assertEquals(AmrapEffectKind.TM_CHANGE, propose.kind)
        assertEquals(AmrapEffectTiming.NEXT_WEEK, propose.timing)
        assertEquals(AmrapEffectGate.PROPOSE, propose.gate)
        assertTrue(propose.needsConfirmation)
        assertEquals(5.0, propose.tmDeltaKg!!, 1e-9)
        assertEquals(185.0, propose.newTmKg!!, 1e-9)
        assertTrue(propose.detail!!, propose.detail!!.contains("180 → 185 kg"))

        val auto = preview("nsuns-531-lp-4d", hit, AutoregulationMode.AUTO)
        assertEquals(AmrapEffectGate.AUTO, auto.gate)
        assertFalse(auto.needsConfirmation)
        assertTrue(auto.detail!!.contains("Se aplica solo"))

        val off = preview("nsuns-531-lp-4d", hit, AutoregulationMode.OFF)
        assertEquals(AmrapEffectGate.OFF, off.gate)
        assertEquals(AmrapEffectTiming.NONE, off.timing)
        assertTrue(off.headline, off.headline.contains("desactivada"))
    }

    @Test
    fun nsuns_says_when_the_table_does_not_move_the_tm() {
        val oneRep = preview("nsuns-531-lp-4d", AmrapHit(LiftSlot.SQUAT, 95.0, 1, 1, weightKg = 171.0))
        assertEquals(AmrapEffectKind.NO_CHANGE, oneRep.kind)
        assertEquals("nSuns: el TM no cambia: con 1 rep la tabla de nSuns no sube el TM.", oneRep.headline)
        val light = preview("nsuns-531-lp-4d", AmrapHit(LiftSlot.SQUAT, 70.0, 1, 12, weightKg = 126.0))
        assertTrue(light.headline, light.headline.contains("menos del 85 % del TM"))
    }

    // ─── 5/3/1 ───────────────────────────────────────────────────────────────────

    @Test
    fun wendler_records_the_e1rm_and_leaves_the_tm_for_the_cycle_close() {
        val effect = preview("wendler-531-bbb", AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 5, actualReps = 8, weightKg = 100.0))
        assertEquals("5/3/1: récord de repeticiones; el TM no se mueve y sube al cerrar el ciclo.", effect.headline)
        assertEquals(AmrapEffectKind.CYCLE_CLOSE, effect.kind)
        assertEquals(AmrapEffectTiming.END_OF_CYCLE, effect.timing)
        assertEquals(AmrapEffectGate.METHOD, effect.gate)
        assertEquals(126.64, effect.e1rmKg!!, 1e-9)
        assertNull(effect.tmDeltaKg)
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.text.contains("TM +5 kg al cerrar el ciclo"))
        assertNull(effect.warning)
    }

    @Test
    fun wendler_warns_before_the_reset_after_two_frozen_cycles() {
        val short = AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 5, actualReps = 3, weightKg = 100.0)
        val first = preview("wendler-531-bbb", short)
        assertTrue(first.headline, first.headline.contains("se congela"))
        assertNull(first.warning)
        val second = preview("wendler-531-bbb", short, frozenBefore = 1)
        assertTrue(second.warning!!, second.warning!!.contains("se reinicia un 10 %"))
        assertTrue(second.warning!!, second.warning!!.contains("de 180 a 162 kg"))
    }

    // ─── Juggernaut ──────────────────────────────────────────────────────────────

    @Test
    fun juggernaut_shows_the_tm_after_the_wave() {
        val effect = preview("juggernaut-2", AmrapHit(LiftSlot.SQUAT, 85.0, 5, 13, weightKg = 127.5), p = lowTm)
        assertEquals("Juggernaut: TM 170 kg tras esta ola.", effect.headline)
        assertEquals(AmrapEffectKind.BLOCK_CLOSE, effect.kind)
        assertEquals(AmrapEffectTiming.END_OF_BLOCK, effect.timing)
        assertEquals(AmrapEffectGate.METHOD, effect.gate)
        assertEquals(20.0, effect.tmDeltaKg!!, 1e-9)
        assertEquals(170.0, effect.newTmKg!!, 1e-9)
    }

    @Test
    fun juggernaut_keeps_the_tm_and_warns_when_the_standard_is_not_beaten() {
        val effect = preview("juggernaut-2", AmrapHit(LiftSlot.SQUAT, 85.0, 5, 3, weightKg = 127.5), p = lowTm)
        assertEquals("Juggernaut: el TM no cambia tras esta ola.", effect.headline)
        assertTrue(effect.warning!!, effect.warning!!.contains("quizá esté alto"))
        assertNull(effect.newTmKg)
    }

    // ─── GZCLP ───────────────────────────────────────────────────────────────────

    @Test
    fun gzclp_says_the_amrap_does_not_change_the_load_and_counts_ten_reps_at_most() {
        val effect = preview(
            "gzclp",
            AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 3, actualReps = 14, weightKg = 153.0),
            slotRole = SlotRole.T1_MAIN,
            plannedVolumeReps = 15,
        )
        assertTrue(effect.headline, effect.headline.contains("la AMRAP no cambia la carga"))
        assertTrue(effect.detail!!, effect.detail!!.contains("Cuentan 10 reps"))
        assertTrue(effect.detail!!, effect.detail!!.contains("Etapa 1: 5×3+"))
        assertEquals(AmrapEffectTiming.END_OF_CYCLE, effect.timing)
        assertNull(effect.tmDeltaKg)
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.conditional)
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.text.contains("15 reps"))
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.text.length <= 90)
    }

    @Test
    fun gzclp_warns_about_the_restart_when_stage_3_falls_short() {
        val effect = preview(
            "gzclp",
            AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 1, actualReps = 0, weightKg = 153.0),
            slotRole = SlotRole.T1_MAIN,
            plannedVolumeReps = 10,
        )
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.conditional)
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.text.contains("nuevo 5RM ×85 %"))
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.text.length <= 90)
        assertNull("one logged AMRAP is not aggregate GZCLP close evidence", effect.warning)
    }

    // ─── J&T, SBS, registro y calibrador ─────────────────────────────────────────

    @Test
    fun jacked_and_tan_compares_the_rep_max_with_the_implied_one_rm() {
        val effect = preview("gzcl-jt-2", AmrapHit(LiftSlot.SQUAT, 77.5, 10, 10, weightKg = 140.0), p = lowTm)
        assertEquals("J&T: TM +2,5 % la semana próxima.", effect.headline)
        assertEquals(2.5, effect.tmDeltaPercent!!, 1e-9)
        assertEquals(154.0, effect.newTmKg!!, 1e-9)
        assertEquals(186.7, effect.e1rmKg!!, 1e-9)
    }

    @Test
    fun sbs_style_shows_the_percent_per_extra_rep() {
        val effect = preview("kpkn-sbs-rtf", AmrapHit(LiftSlot.SQUAT, 75.0, 6, 9, weightKg = 135.0))
        assertEquals("Estilo SBS: TM +1,5 % la semana próxima.", effect.headline)
    }

    @Test
    fun record_only_programs_and_programs_without_a_recipe_say_so() {
        val cube = preview("cube-method", AmrapHit(LiftSlot.DEADLIFT, 70.0, 8, 10, weightKg = 150.0))
        assertTrue(cube.isRecordOnly)
        assertEquals(AmrapEffectKind.RECORD_ONLY, cube.kind)
        assertTrue(cube.headline, cube.headline.startsWith("AMRAP de registro"))
        assertEquals(199.95, cube.e1rmKg!!, 1e-9)
        assertNull(cube.tmDeltaKg)

        val free = AmrapProgramPreview.preview(null, AutoregulationMode.OFF, null, AmrapHit(null, null, 5, 7, weightKg = 60.0))
        assertTrue(free.isRecordOnly)
        assertEquals("Sin regla de progresión: se guarda en tu historial y tus récords; no cambia tu carga.", free.headline)
    }

    @Test
    fun the_power_template_calibrator_shows_the_proposed_tm_change_or_why_it_does_not_apply() {
        val recipe = templateRecipeOf("power-12-3")
        fun calibrator(rir: Int?) = AmrapHit(
            LiftSlot.SQUAT, 71.0, prescribedReps = 5, actualReps = 6, weightKg = 180.0, purpose = SetPurpose.CALIBRATOR,
            calibrationScope = CalibrationScope.NEXT_SESSIONS, declaredRir = rir, configurationId = CatalogIds.SQ_LOW,
        )
        val raise = AmrapProgramPreview.preview(recipe, AutoregulationMode.PROPOSE, profile, calibrator(1))
        assertEquals("Calibrador: TM +5 % la semana próxima.", raise.headline)
        assertEquals(189.0, raise.newTmKg!!, 1e-9)
        assertTrue(raise.needsConfirmation)

        val noEffort = AmrapProgramPreview.preview(recipe, AutoregulationMode.PROPOSE, profile, calibrator(null))
        assertEquals(AmrapEffectKind.NO_CHANGE, noEffort.kind)
        assertEquals("Calibrador: Falta declarar la reserva o el fallo.", noEffort.headline)

        val off = AmrapProgramPreview.preview(recipe, AutoregulationMode.OFF, profile, calibrator(1))
        assertTrue(off.headline, off.headline.contains("desactivada"))
    }

    // ─── APRE ────────────────────────────────────────────────────────────────────

    @Test
    fun apre_shows_the_load_of_set_4_and_the_xrm_of_next_week() {
        val set3 = AmrapHit(LiftSlot.SQUAT, 81.52, prescribedReps = 10, actualReps = 12, weightKg = 150.0, isLastAmrapOfExercise = false)
        val toSet4 = preview("apre-mann", set3)
        assertEquals("APRE: serie 4 con 155 kg.", toSet4.headline)
        assertEquals(AmrapEffectKind.NEXT_SET_LOAD, toSet4.kind)
        assertEquals(AmrapEffectTiming.SAME_SESSION, toSet4.timing)
        assertEquals(155.0, toSet4.nextSetLoadKg!!, 1e-9)
        assertEquals("APRE 10: serie 4 con 155 kg hoy.", toSet4.observerNote!!.text)

        val set4 = AmrapHit(LiftSlot.SQUAT, 81.52, prescribedReps = 10, actualReps = 17, weightKg = 155.0, isLastAmrapOfExercise = true)
        val toNextWeek = preview("apre-mann", set4)
        assertEquals("APRE 10: propone XRM 162,5 kg para la próxima semana.", toNextWeek.headline)
        assertEquals(AmrapEffectKind.NEXT_WEEK_XRM, toNextWeek.kind)
        assertEquals(AmrapEffectTiming.NEXT_WEEK, toNextWeek.timing)
        assertEquals(AmrapEffectGate.PROPOSE, toNextWeek.gate)
        assertEquals(162.5, toNextWeek.nextWeekXrmKg!!, 1e-9)
        assertEquals("APRE 10: propone XRM 162,5 kg para la próxima semana.", toNextWeek.observerNote!!.text)
        assertTrue(toNextWeek.needsConfirmation)
        assertNull(toNextWeek.tmDeltaPercent)
        assertNull(toNextWeek.newTmKg)

        val auto = AmrapProgramPreview.preview(recipeOf("apre-mann"), AutoregulationMode.AUTO, profile, set4)
        assertEquals(AmrapEffectGate.AUTO, auto.gate)
        assertTrue(auto.observerNote!!.text, auto.observerNote!!.text.contains("AUTO"))
        assertTrue(auto.observerNote!!.text, auto.observerNote!!.text.contains("162,5 kg"))
        assertEquals(AmrapEffectKind.NEXT_WEEK_XRM, auto.kind)
        assertEquals(AmrapEffectTiming.NEXT_WEEK, auto.timing)
        assertFalse(auto.needsConfirmation)
        assertNull(auto.tmDeltaPercent)
        assertNull(auto.newTmKg)

        val off = AmrapProgramPreview.preview(recipeOf("apre-mann"), AutoregulationMode.OFF, profile, set4)
        assertEquals(AmrapEffectGate.OFF, off.gate)
        assertEquals(AmrapEffectTiming.NONE, off.timing)
        assertEquals(AmrapEffectKind.NO_CHANGE, off.kind)
        assertTrue(off.observerNote!!.text, off.observerNote!!.text.contains("OFF no lo aplica"))

        val noProfile = AmrapProgramPreview.preview(recipeOf("apre-mann"), AutoregulationMode.PROPOSE, null, set4)
        assertEquals("APRE 10: propone XRM 162,5 kg para la próxima semana.", noProfile.observerNote!!.text)
        assertEquals(162.5, noProfile.nextWeekXrmKg!!, 1e-9)
        assertTrue(noProfile.needsConfirmation)
        assertNull(noProfile.tmDeltaPercent)
        assertNull(noProfile.newTmKg)
        assertFalse(noProfile.observerNote!!.text, noProfile.observerNote!!.text.contains("TM"))
        assertFalse(noProfile.observerNote!!.text, noProfile.observerNote!!.text.contains("0 kg"))
    }
}
