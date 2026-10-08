package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete A · A.C4, parte pura (curaduría de programas, 2026-10-03): prioridad de los rechazos y tabla completa de
 * textos y botones de [PlanRejectionPresenter] (C.P11). JVM puro: ni catálogo ni Android.
 */
class PlanRejectionPresenterTest {

    private fun view(
        reason: PlanRejectionReason?,
        planId: String? = "native:other",
        requiredMinutes: Int? = null,
        apparatusKey: String? = null,
        needsConfirmation: Boolean = false,
        missing: List<String> = emptyList(),
        stage: PlanEvaluationStage? = null,
    ) = RejectionView(
        planId = planId,
        reasonCode = reason,
        requiredMinutes = requiredMinutes,
        apparatusKey = apparatusKey,
        needsApparatusConfirmation = needsConfirmation,
        missingRequirements = missing,
        stage = stage,
    )

    // ─── primary: cuál de los rechazos se cuenta ───────────────────────────────────────────────

    @Test
    fun anEmptyListHasNoPrimaryRejection() {
        assertNull(PlanRejectionPresenter.primary(emptyList(), ownPlanId = "native:own"))
        assertNull(PlanRejectionPresenter.primary(emptyList(), ownPlanId = null))
    }

    @Test
    fun theRejectionOfTheProfilesOwnPlanWinsOverEverythingElse() {
        val unknownWithKey = view(PlanRejectionReason.APPARATUS_UNKNOWN, "native:a", apparatusKey = "squat_rack")
        val own = view(PlanRejectionReason.FREQUENCY, "native:own")
        val timeBudget = view(PlanRejectionReason.TIME_BUDGET, "native:c", requiredMinutes = 35)
        val rejections = listOf(unknownWithKey, timeBudget, own)

        assertSame(own, PlanRejectionPresenter.primary(rejections, ownPlanId = "native:own"))
        assertSame(
            "sin plan propio conocido manda el aparato con llave",
            unknownWithKey,
            PlanRejectionPresenter.primary(rejections, ownPlanId = null),
        )
        assertSame(
            "un plan propio que no está entre los rechazos no cuenta",
            unknownWithKey,
            PlanRejectionPresenter.primary(rejections, ownPlanId = "native:not-there"),
        )
    }

    @Test
    fun anUnknownApparatusWithAConfirmableKeyComesBeforeTimeBudget() {
        val timeBudget = view(PlanRejectionReason.TIME_BUDGET, "native:a", requiredMinutes = 32)
        val unknownWithKey = view(PlanRejectionReason.APPARATUS_UNKNOWN, "native:b", apparatusKey = "bench_flat")

        assertSame(unknownWithKey, PlanRejectionPresenter.primary(listOf(timeBudget, unknownWithKey), ownPlanId = null))
    }

    @Test
    fun anUnknownApparatusWithoutAKeyIsNotMoreActionableThanTimeBudget() {
        val unknownWithoutKey = view(PlanRejectionReason.APPARATUS_UNKNOWN, "native:a")
        val timeBudget = view(PlanRejectionReason.TIME_BUDGET, "native:b", requiredMinutes = 40)

        assertSame(timeBudget, PlanRejectionPresenter.primary(listOf(unknownWithoutKey, timeBudget), ownPlanId = null))
    }

    @Test
    fun amongTimeBudgetRejectionsTheOneNeedingTheFewestMinutesWins() {
        val slow = view(PlanRejectionReason.TIME_BUDGET, "native:slow", requiredMinutes = 61)
        val unknownMinutes = view(PlanRejectionReason.TIME_BUDGET, "native:none", requiredMinutes = null)
        val fast = view(PlanRejectionReason.TIME_BUDGET, "native:fast", requiredMinutes = 38)
        val tie = view(PlanRejectionReason.TIME_BUDGET, "native:tie", requiredMinutes = 38)

        assertSame(fast, PlanRejectionPresenter.primary(listOf(slow, unknownMinutes, fast, tie), ownPlanId = null))
        assertSame(
            "sin minutos conocidos va el último, pero sigue siendo un TIME_BUDGET",
            unknownMinutes,
            PlanRejectionPresenter.primary(listOf(unknownMinutes), ownPlanId = null),
        )
        assertSame(
            "los minutos conocidos van antes que los desconocidos",
            slow,
            PlanRejectionPresenter.primary(listOf(unknownMinutes, slow), ownPlanId = null),
        )
    }

    @Test
    fun timeBudgetComesBeforeProfileAndAbsentRejections() {
        val profile = view(PlanRejectionReason.PROFILE_MISMATCH, "native:a")
        val timeBudget = view(PlanRejectionReason.TIME_BUDGET, "native:b", requiredMinutes = 90)

        assertSame(timeBudget, PlanRejectionPresenter.primary(listOf(profile, timeBudget), ownPlanId = null))
    }

    @Test
    fun profileAndAbsentRejectionsComeBeforeTheRestAndKeepTheirOrder() {
        val frequency = view(PlanRejectionReason.FREQUENCY, "native:a")
        val absent = view(PlanRejectionReason.APPARATUS_ABSENT, "native:b")
        val profile = view(PlanRejectionReason.PROFILE_MISMATCH, "native:c")

        assertSame(absent, PlanRejectionPresenter.primary(listOf(frequency, absent, profile), ownPlanId = null))
        assertSame(profile, PlanRejectionPresenter.primary(listOf(frequency, profile, absent), ownPlanId = null))
    }

    @Test
    fun withNothingMoreActionableTheFirstRejectionIsTheOne() {
        val frequency = view(PlanRejectionReason.FREQUENCY, "native:a")
        val split = view(PlanRejectionReason.SPLIT, "native:b")
        val internal = view(PlanRejectionReason.COMPOSITION, "native:c")

        assertSame(frequency, PlanRejectionPresenter.primary(listOf(frequency, split, internal), ownPlanId = null))
        assertSame(internal, PlanRejectionPresenter.primary(listOf(internal, split), ownPlanId = null))
    }

    // ─── present: tabla de C.P11 ───────────────────────────────────────────────────────────────

    private val context = PresentationContext(
        userMinutes = 30,
        goalLabel = "Músculo",
        daysChosen = 3,
        disciplineLabelOf = { planId -> if (planId == "native:strength-foundation-v2") "Powerlifting" else null },
        planDaysOf = { planId -> if (planId == "native:four-days") 4..4 else null },
    )

    private fun present(rejection: RejectionView, ctx: PresentationContext = context) =
        PlanRejectionPresenter.present(rejection, ctx)

    private fun RejectionPresentation.assertIs(text: String, primary: RejectionAction?, secondary: RejectionAction? = null) {
        assertEquals(text, this.text)
        assertEquals(primary, this.primary)
        assertEquals(secondary, this.secondary)
    }

    @Test
    fun aCatalogErrorOffersToRetryWithoutChangingTheAnswers() {
        val expected = "No encontramos plan por un error de catálogo. Puedes reintentar sin cambiar tus respuestas."
        present(view(PlanRejectionReason.CATALOG_NOT_READY)).assertIs(expected, RejectionAction.Retry)
        present(view(reason = null, planId = null, stage = PlanEvaluationStage.CATALOG)).assertIs(expected, RejectionAction.Retry)
    }

    @Test
    fun aProfileMismatchNamesTheDisciplineAndTheGoalAndOffersToChangeTheGoalOrSeeAlternatives() {
        present(view(PlanRejectionReason.PROFILE_MISMATCH, "native:strength-foundation-v2")).assertIs(
            "Este plan es de powerlifting; tu objetivo es músculo.",
            RejectionAction.ChangeGoal,
            RejectionAction.SeeAlternatives,
        )
        // Sin la disciplina o sin el objetivo, el texto se queda con lo que sí sabe.
        present(view(PlanRejectionReason.PROFILE_MISMATCH, "native:unknown-discipline")).assertIs(
            "Este plan no corresponde a tu objetivo: músculo.",
            RejectionAction.ChangeGoal,
            RejectionAction.SeeAlternatives,
        )
        present(
            view(PlanRejectionReason.PROFILE_MISMATCH, "native:strength-foundation-v2"),
            context.copy(goalLabel = null),
        ).assertIs(
            "Este plan es de powerlifting, que no es tu objetivo.",
            RejectionAction.ChangeGoal,
            RejectionAction.SeeAlternatives,
        )
        present(view(PlanRejectionReason.PROFILE_MISMATCH, null), PresentationContext()).assertIs(
            "Este plan no corresponde a tu objetivo.",
            RejectionAction.ChangeGoal,
            RejectionAction.SeeAlternatives,
        )
    }

    @Test
    fun aFrequencyMismatchComparesTheDaysOfThePlanWithTheOnesChosenWithTheRightPlural() {
        val four = view(PlanRejectionReason.FREQUENCY, "native:four-days")
        present(four).assertIs("Este plan usa 4 días distintos; elegiste 3 días.", RejectionAction.ChangeDays)
        present(four, context.copy(planDaysOf = { 1..1 })).assertIs(
            "Este plan usa 1 día distinto; elegiste 3 días.",
            RejectionAction.ChangeDays,
        )
        present(four, context.copy(daysChosen = 1)).assertIs(
            "Este plan usa 4 días distintos; elegiste 1 día.",
            RejectionAction.ChangeDays,
        )
        present(four, context.copy(planDaysOf = { 3..5 }, daysChosen = 2)).assertIs(
            "Este plan usa de 3 a 5 días; elegiste 2 días.",
            RejectionAction.ChangeDays,
        )
        present(four, context.copy(planDaysOf = { null })).assertIs(
            "Este plan no se adapta a los días que elegiste (3 días).",
            RejectionAction.ChangeDays,
        )
        present(four, context.copy(planDaysOf = { null }, daysChosen = 1)).assertIs(
            "Este plan no se adapta a los días que elegiste (1 día).",
            RejectionAction.ChangeDays,
        )
        present(four, PresentationContext(planDaysOf = { 2..2 })).assertIs(
            "Este plan usa 2 días distintos.",
            RejectionAction.ChangeDays,
        )
        present(four, PresentationContext()).assertIs(
            "Este plan no se adapta a los días que elegiste.",
            RejectionAction.ChangeDays,
        )
    }

    @Test
    fun aSplitMismatchOffersToChangeTheSplit() {
        present(view(PlanRejectionReason.SPLIT)).assertIs(
            "El reparto elegido no encaja con este plan.",
            RejectionAction.ChangeSplit,
        )
    }

    @Test
    fun anUnknownApparatusSaysWhatIsMissingAndOffersToConfirmIt() {
        present(
            view(PlanRejectionReason.APPARATUS_UNKNOWN, apparatusKey = "squat_rack", missing = listOf("rack"), needsConfirmation = true),
        ).assertIs("Falta confirmar si tienes rack.", RejectionAction.ConfirmApparatus)
        present(
            view(
                PlanRejectionReason.APPARATUS_UNKNOWN,
                apparatusKey = "squat_rack",
                missing = listOf("rack", "bench"),
                needsConfirmation = true,
            ),
        ).assertIs("Falta confirmar si tienes rack y banco.", RejectionAction.ConfirmApparatus)
        // Solo la llave, sin lista de requisitos (ruta heredada).
        present(view(PlanRejectionReason.APPARATUS_UNKNOWN, apparatusKey = "pullup_bar")).assertIs(
            "Falta confirmar si tienes barra de dominadas.",
            RejectionAction.ConfirmApparatus,
        )
    }

    @Test
    fun anUnknownApparatusThatCannotBeConfirmedOffersAlternativesInstead() {
        present(view(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("nordic_anchor"))).assertIs(
            "Falta confirmar si tienes todo el material de este plan.",
            RejectionAction.SeeAlternatives,
        )
        // Una marca heredada sin llave no convierte el requisito en confirmable.
        present(view(PlanRejectionReason.APPARATUS_UNKNOWN, needsConfirmation = true)).assertIs(
            "Falta confirmar si tienes todo el material de este plan.",
            RejectionAction.SeeAlternatives,
        )
    }

    @Test
    fun anAbsentApparatusNamesWhatTheAnswersDenyAndOffersToConfirmOrSeeAlternatives() {
        // «, que dijiste que no tienes» no lleva pronombre, así que concuerda igual con «rack», «mancuernas» o «barra y carga».
        present(view(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("rack"))).assertIs(
            "Este plan necesita rack, que dijiste que no tienes.",
            RejectionAction.ConfirmApparatus,
            RejectionAction.SeeAlternatives,
        )
        present(view(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("rack", "bench"))).assertIs(
            "Este plan necesita rack y banco, que dijiste que no tienes.",
            RejectionAction.ConfirmApparatus,
            RejectionAction.SeeAlternatives,
        )
        // La barra es una categoría, no una llave del panel: se nombra con su palabra llana.
        present(view(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("barbell"))).assertIs(
            "Este plan necesita barra y carga, que dijiste que no tienes.",
            RejectionAction.SeeAlternatives,
        )
        present(view(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("barbell", "dumbbells"))).assertIs(
            "Este plan necesita barra y carga y mancuernas, que dijiste que no tienes.",
            RejectionAction.SeeAlternatives,
        )
        present(view(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("dumbbells"))).assertIs(
            "Este plan necesita mancuernas, que dijiste que no tienes.",
            RejectionAction.SeeAlternatives,
        )
        present(view(PlanRejectionReason.APPARATUS_ABSENT)).assertIs(
            "Este plan necesita material que dijiste que no tienes.",
            RejectionAction.SeeAlternatives,
        )
        listOf("ghd", "ab_wheel", "safety_bar", "hex_bar", "plate", "trx").forEach { token ->
            present(view(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf(token))).assertIs(
                "Este plan necesita material que dijiste que no tienes.", RejectionAction.SeeAlternatives,
            )
        }
    }

    @Test
    fun noValidSubstitutionExplainsThatTheCutIsNeverSilentAndOffersAlternatives() {
        present(view(PlanRejectionReason.NO_VALID_SUBSTITUTION)).assertIs(
            "No hay un cambio de ejercicio que respete este plan; no recortamos el plan en silencio.",
            RejectionAction.SeeAlternatives,
        )
    }

    @Test
    fun timeBudgetSaysTheExactMinutesAndOffersToAdjustToThem() {
        val presentation = present(view(PlanRejectionReason.TIME_BUDGET, requiredMinutes = 31))
        presentation.assertIs(
            "Con las series mínimas este plan necesita 31 min por sesión y elegiste 30.",
            RejectionAction.SetMinutes(31),
        )
        assertEquals("Ajustar a 31 min", presentation.primary?.label)
        assertEquals(listOf<RejectionAction>(RejectionAction.SetMinutes(31)), presentation.actions)

        present(view(PlanRejectionReason.TIME_BUDGET, requiredMinutes = 31), PresentationContext()).assertIs(
            "Con las series mínimas este plan necesita 31 min por sesión.",
            RejectionAction.SetMinutes(31),
        )
        present(view(PlanRejectionReason.TIME_BUDGET, requiredMinutes = 100)).assertIs(
            "Con las series mínimas este plan necesita 100 min por sesión y elegiste 30.",
            RejectionAction.SetMinutes(100),
        )
        // El propio mínimo del reloj vale como destino del ajuste.
        present(view(PlanRejectionReason.TIME_BUDGET, requiredMinutes = EntrenoStepValues.SESSION_MINUTES_MIN), PresentationContext()).assertIs(
            "Con las series mínimas este plan necesita 30 min por sesión.",
            RejectionAction.SetMinutes(30),
        )
    }

    @Test
    fun timeBudgetWithoutAMinuteCountThatTheWizardAcceptsDoesNotPromiseAnAdjustment() {
        listOf(null, 101, 0, 29, 25).forEach { required ->
            present(view(PlanRejectionReason.TIME_BUDGET, requiredMinutes = required)).assertIs(
                "Con las series mínimas este plan no cabe en los 30 min que elegiste.",
                RejectionAction.SeeAlternatives,
            )
        }
        present(view(PlanRejectionReason.TIME_BUDGET, requiredMinutes = null), PresentationContext()).assertIs(
            "Con las series mínimas este plan no cabe en el tiempo que elegiste.",
            RejectionAction.SeeAlternatives,
        )
    }

    @Test
    fun internalFailuresSayTheAnswersDoNotChangeAndOfferToRetry() {
        val expected = "Algo falló al preparar este plan. Tus respuestas no cambian."
        listOf(
            PlanRejectionReason.INTERNAL_MATERIALIZATION,
            PlanRejectionReason.UNRESOLVED_CONFIGURATION,
            PlanRejectionReason.COMPOSITION,
            PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE,
        ).forEach { reason ->
            present(view(reason)).assertIs(expected, RejectionAction.Retry)
        }
        present(view(reason = null)).assertIs(expected, RejectionAction.Retry)
    }

    @Test
    fun aRejectionWithoutCodeThatNeedsConfirmationIsTreatedAsAnUnknownApparatus() {
        present(view(reason = null, needsConfirmation = true, apparatusKey = "bench_flat")).assertIs(
            "Falta confirmar si tienes banco.",
            RejectionAction.ConfirmApparatus,
        )
    }

    @Test
    fun anUnavailableRecipeAndAnUnsuitableLevelOfferAlternatives() {
        present(view(PlanRejectionReason.RECIPE_UNAVAILABLE)).assertIs(
            "Este plan no está disponible ahora.",
            RejectionAction.SeeAlternatives,
        )
        present(view(PlanRejectionReason.LEVEL_UNSUITABLE)).assertIs(
            "Este plan no es adecuado para tu nivel.",
            RejectionAction.SeeAlternatives,
        )
    }

    // ─── Propiedades de toda la tabla ──────────────────────────────────────────────────────────

    @Test
    fun everyReasonHasATextAndAtMostTwoButtonsWithTheMainOneFirst() {
        PlanRejectionReason.entries.forEach { reason ->
            val presentation = present(
                view(reason, requiredMinutes = 31, apparatusKey = "squat_rack", missing = listOf("rack", "bench")),
            )
            assertTrue("$reason: texto vacío", presentation.text.isNotBlank())
            assertNotNull("$reason: sin botón principal", presentation.primary)
            assertTrue("$reason: más de dos botones", presentation.actions.size in 1..2)
            if (presentation.secondary != null) {
                assertTrue("$reason: el secundario solo existe con el principal", presentation.primary != null)
            }
        }
    }

    @Test
    fun theTextNeverCarriesRawIdsTokensOrEngineText() {
        val raw = view(
            reason = null,
            planId = "native:strength-foundation-v2",
            missing = listOf("machine_config:leg_press__bilateral__machine", "nordic_anchor"),
            apparatusKey = null,
        )
        val reasons: List<PlanRejectionReason?> = PlanRejectionReason.entries + listOf(null)
        reasons.forEach { reason ->
            val presentation = present(raw.copy(reasonCode = reason, requiredMinutes = 31))
            val text = presentation.text
            assertTrue("$reason: id del plan en «$text»", "native" !in text && "foundation" !in text)
            assertTrue("$reason: token o id de configuración en «$text»", "_" !in text && "machine_config" !in text)
            assertTrue("$reason: token en «$text»", "nordic" !in text)
        }
    }

    @Test
    fun anInlineLabelKeepsItsAcronymsAndLowercasesTheRest() {
        // «Barra EZ» es una llave del panel: la sigla no se rompe al ponerla en medio de una frase.
        present(view(PlanRejectionReason.APPARATUS_UNKNOWN, apparatusKey = "ez_bar")).assertIs(
            "Falta confirmar si tienes barra EZ.",
            RejectionAction.ConfirmApparatus,
        )
        assertEquals("Rack de sentadilla", PlanRejectionPresenter.panelLabelOf("squat_rack"))
        assertNull(PlanRejectionPresenter.panelLabelOf("not_a_panel_key"))
    }

    // ─── H14: el texto y el botón de un toque nombran el mismo requisito ───────────────────────

    @Test
    fun theShortNamesAreTheOnesOfTheOneTapButton() {
        assertEquals("rack", PlanRejectionPresenter.shortLabelOf("squat_rack"))
        assertEquals("banco", PlanRejectionPresenter.shortLabelOf("bench_flat"))
        assertEquals("banco regulable", PlanRejectionPresenter.shortLabelOf("bench_adjustable"))
        assertEquals("banco predicador", PlanRejectionPresenter.shortLabelOf("preacher_bench"))
        assertEquals("barra de dominadas", PlanRejectionPresenter.shortLabelOf("pullup_bar"))
        assertEquals("paralelas", PlanRejectionPresenter.shortLabelOf("dip_bars"))
        assertEquals("barra baja", PlanRejectionPresenter.shortLabelOf("low_bar_support"))
        assertEquals("barra EZ", PlanRejectionPresenter.shortLabelOf("ez_bar"))
        // Una llave del panel sin nombre corto usa la etiqueta del panel en minúscula; una que no es del panel, ninguna.
        assertEquals("prensa de piernas", PlanRejectionPresenter.shortLabelOf("leg_press"))
        assertNull(PlanRejectionPresenter.shortLabelOf("not_a_panel_key"))
    }

    @Test
    fun theTextNamesTheKeyThatTheContextResolvesForEachRequirement() {
        val missingRackAndBench = view(
            PlanRejectionReason.APPARATUS_UNKNOWN,
            apparatusKey = "squat_rack",
            missing = listOf("rack", "bench"),
            needsConfirmation = true,
        )
        // Por defecto, la primera llave del panel que acredita el requisito: el banco plano.
        present(missingRackAndBench).assertIs("Falta confirmar si tienes rack y banco.", RejectionAction.ConfirmApparatus)
        // El asistente resuelve `bench` con la llave que sigue sin responder: con el banco plano ya negado es el
        // regulable, y el texto lo dice igual que el botón («Sí, tengo rack y banco regulable»).
        val adjustable = context.copy(
            apparatusKeyOf = { token -> if (token == "bench") "bench_adjustable" else SetupApparatusPanel.keyForToken(token) },
        )
        present(missingRackAndBench, adjustable).assertIs(
            "Falta confirmar si tienes rack y banco regulable.",
            RejectionAction.ConfirmApparatus,
        )
        // El mismo criterio vale para el material ausente.
        present(view(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("bench")), adjustable).assertIs(
            "Este plan necesita banco regulable, que dijiste que no tienes.",
            RejectionAction.ConfirmApparatus,
            RejectionAction.SeeAlternatives,
        )
        // Un requisito sin llave del panel se nombra con su palabra llana.
        present(
            view(PlanRejectionReason.APPARATUS_UNKNOWN, apparatusKey = "squat_rack", missing = listOf("rack", "barbell"), needsConfirmation = true),
            adjustable,
        ).assertIs("Falta confirmar si tienes rack y barra y carga.", RejectionAction.ConfirmApparatus)
    }

    // ─── H12: el rechazo de perfil del plan propio de Fuerza y músculo sale del presentador ─────

    @Test
    fun theOwnFuerzaYMusculoPlanExplainsTheRequirementWithWeightsInsteadOfExternalResistance() {
        val own = "native:powerbuilding-foundation-v2"
        val ownContext = context.copy(
            goalLabel = "Fuerza y músculo",
            ownPlanId = own,
            goalProfile = PlanGoalProfile.STRENGTH_MUSCLE,
        )

        present(view(PlanRejectionReason.PROFILE_MISMATCH, own), ownContext).assertIs(
            PlanRejectionPresenter.OWN_POWERBUILDING_TEXT,
            RejectionAction.ChangeGoal,
            RejectionAction.SeeAlternatives,
        )
        assertEquals(
            "Fuerza y músculo necesita pesas en los ejercicios principales: barra con rack y banco, o mancuernas.",
            PlanRejectionPresenter.OWN_POWERBUILDING_TEXT,
        )
        assertFalse(PlanRejectionPresenter.OWN_POWERBUILDING_TEXT, "resistencia externa" in PlanRejectionPresenter.OWN_POWERBUILDING_TEXT)
    }

    @Test
    fun theOwnPowerbuildingTextOnlyAppliesToTheProfileRejectionOfThatOwnPlan() {
        val own = "native:powerbuilding-foundation-v2"
        val ownContext = context.copy(
            goalLabel = "Fuerza y músculo",
            ownPlanId = own,
            goalProfile = PlanGoalProfile.STRENGTH_MUSCLE,
        )

        // Otro plan con el mismo motivo, otro objetivo o un contexto sin plan propio: la frase de disciplina de siempre.
        present(view(PlanRejectionReason.PROFILE_MISMATCH, "native:strength-foundation-v2"), ownContext).assertIs(
            "Este plan es de powerlifting; tu objetivo es fuerza y músculo.",
            RejectionAction.ChangeGoal,
            RejectionAction.SeeAlternatives,
        )
        val musclePresentation = present(
            view(PlanRejectionReason.PROFILE_MISMATCH, own),
            ownContext.copy(goalLabel = "Músculo", goalProfile = PlanGoalProfile.MUSCLE),
        )
        assertNotEquals(PlanRejectionPresenter.OWN_POWERBUILDING_TEXT, musclePresentation.text)
        val withoutOwnPlan = present(view(PlanRejectionReason.PROFILE_MISMATCH, own), context)
        assertNotEquals(PlanRejectionPresenter.OWN_POWERBUILDING_TEXT, withoutOwnPlan.text)
        // Los demás motivos del plan propio conservan su texto.
        present(view(PlanRejectionReason.TIME_BUDGET, own, requiredMinutes = 31), ownContext).assertIs(
            "Con las series mínimas este plan necesita 31 min por sesión y elegiste 30.",
            RejectionAction.SetMinutes(31),
        )
    }
}
