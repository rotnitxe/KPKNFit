package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatAnswerRecord
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupDraftCompatibilityTest {
    private fun answer(
        id: WizChatQuestionId,
        number: Double?,
        source: WizChatAnswerSource = WizChatAnswerSource.DECLARED,
        revision: Int = 1,
    ) = WizChatAnswerRecord(id, WizChatAnswerKind.NUMBER, numberValue = number, source = source, revision = revision)

    private fun choice(id: WizChatQuestionId, text: String, revision: Int = 4) =
        WizChatAnswerRecord(id, WizChatAnswerKind.CHOICE, textValue = text, revision = revision)

    private fun nameAnswer() = WizChatAnswerRecord(
        WizChatQuestionId.P_NAME,
        WizChatAnswerKind.TEXT,
        textValue = "Ana",
    )

    /** Build an in-order persisted cursor; out-of-order fixtures belong in repair tests. */
    private fun progressAt(
        step: SetupStepId,
        context: SetupStepContext,
        initial: SetupStepProgress = SetupStepProgress.initial(context),
        answerOverrides: Map<SetupStepId, SetupAnswerProvenance> = emptyMap(),
    ): SetupStepProgress {
        val route = SetupStepGraph.stepIds(context)
        require(step in route)
        var progress = initial
        route.takeWhile { it != step }.forEach { previous ->
            progress = progress.at(previous, context).recordAnswer(
                previous,
                answerOverrides[previous] ?: SetupAnswerProvenance.USER_DECLARED,
                com.example.kpkn.domain.onboarding.SetupValueState.DECLARED,
            )
        }
        progress = progress.at(step, context)
        val additional = answerOverrides.filterKeys { it !in progress.answers }
        return if (additional.isEmpty()) progress
        else progress.copy(answers = progress.answers + additional)
    }

    private fun draft(
        scope: String = "full",
        current: WizChatQuestionId = WizChatQuestionId.T_DAYS,
        terminal: Boolean = false,
        answers: List<WizChatAnswerRecord>,
        age: Int? = 30,
        height: Double? = 175.0,
        weight: Double? = 72.0,
    ) = SetupWizardDraft(
        draftScope = scope,
        // Ya normalizado por §15.4 (repair rellena PERSONALIZE): el helper
        // representa un borrador nativo estable para probar la autoridad de
        // vitales/espejo, no la reparación de camino.
        trainingPath = SetupTrainingPath.PERSONALIZE,
        ageYears = age,
        heightCm = height,
        weightKg = weight,
        wizChat = WizChatProgress(
            draftScope = scope,
            currentQuestionId = current,
            stage = com.example.kpkn.domain.onboarding.WizChatGraph.stageFor(current),
            acceptedAnswers = answers,
            terminal = terminal,
        ),
    )

    @Test
    fun oldDraftWithOmittedVitalsRewindsToTheFirstPendingOne() {
        val old = draft(
            answers = listOf(
                nameAnswer(),
                answer(WizChatQuestionId.P_AGE, null, WizChatAnswerSource.OMITTED),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, null, WizChatAnswerSource.OMITTED),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertEquals(WizChatQuestionId.P_AGE, repaired.wizChat.currentQuestionId)
        assertEquals(WizChatStage.PROFILE, repaired.wizChat.stage)
        // El progreso de pasos también se reanuda en la vital pendiente más temprana.
        assertEquals(SetupStepId.AGE, repaired.stepProgress.currentStepId)
        // El JSON legacy se conserva íntegro (incluidos los OMITTED): la
        // exclusión es de autoridad, no un borrado irreversible de datos.
        assertEquals(
            listOf(WizChatQuestionId.P_NAME, WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT, WizChatQuestionId.P_WEIGHT),
            repaired.wizChat.acceptedAnswers.map { it.questionId },
        )
        assertEquals(
            listOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_WEIGHT),
            SetupDraftCompatibility.pendingMandatoryVitals(repaired),
        )
        // Las vitales omitidas nunca entran en la procedencia del nuevo progreso.
        assertNull(repaired.stepProgress.answers[SetupStepId.AGE])
    }

    @Test
    fun onlyPendingVitalsAreAskedAgainAfterRepair() {
        val old = draft(
            current = WizChatQuestionId.REVIEW,
            terminal = true,
            answers = listOf(
                nameAnswer(),
                answer(WizChatQuestionId.P_AGE, 31.0),
                answer(WizChatQuestionId.P_HEIGHT, null, WizChatAnswerSource.OMITTED),
                answer(WizChatQuestionId.P_WEIGHT, 70.4),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertEquals(WizChatQuestionId.P_HEIGHT, repaired.wizChat.currentQuestionId)
        assertEquals(31, repaired.ageYears)
        assertEquals(70.4, repaired.weightKg!!, 0.0001)
        assertEquals(listOf(WizChatQuestionId.P_HEIGHT), SetupDraftCompatibility.pendingMandatoryVitals(repaired))
        assertEquals(SetupStepId.HEIGHT, repaired.stepProgress.currentStepId)
    }

    @Test
    fun suggestedProfileValuesDoNotCountAsDeclaredAnswers() {
        val fresh = draft(
            current = WizChatQuestionId.P_NAME,
            answers = emptyList(),
        )
        assertEquals(
            listOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT, WizChatQuestionId.P_WEIGHT),
            SetupDraftCompatibility.pendingMandatoryVitals(fresh),
        )
        assertFalse(SetupDraftCompatibility.declaredVitals(fresh).contains(WizChatQuestionId.P_AGE))
    }

    @Test
    fun ringsOnlyDraftsNeverAskForProfileVitals() {
        val rings = draft(
            scope = "rings_only",
            current = WizChatQuestionId.R_RECENT,
            answers = emptyList(),
            age = null,
            height = null,
            weight = null,
        )
        assertEquals(emptyList<WizChatQuestionId>(), SetupDraftCompatibility.pendingMandatoryVitals(rings))
        val repaired = SetupDraftCompatibility.repair(rings)
        // El payload legacy se conserva completo y no se piden vitales.
        assertEquals(rings.wizChat, repaired.wizChat)
        assertNull(repaired.ageYears)
        assertNull(repaired.heightCm)
        assertNull(repaired.weightKg)
    }

    @Test
    fun completeDraftsKeepTheLegacyCursorAndDeclaredVitals() {
        val complete = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(complete)
        assertEquals(complete.wizChat.currentQuestionId, repaired.wizChat.currentQuestionId)
        assertEquals(30, repaired.ageYears)
        assertEquals(175.0, repaired.heightCm!!, 0.0001)
        assertEquals(72.0, repaired.weightKg!!, 0.0001)
        assertTrue(SetupDraftCompatibility.pendingMandatoryVitals(complete).isEmpty())
        // El progreso queda migrado y sin bloques completados por posición.
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, repaired.stepProgress.origin)
        assertEquals(emptySet<SetupWizardBlock>(), repaired.stepProgress.completedBlocks)
    }

    @Test
    fun contradictoryGoalAndStyleFromOldFlowsResolveToTheGoal() {
        val old = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
                choice(WizChatQuestionId.T_STYLE, "Hipertrofia"),
            ),
        ).copy(
            goal = SetupGoal.STRENGTH,
            volumeAnswers = SetupVolumeAnswers(
                style = com.example.kpkn.data.models.TrainingStyle.BODYBUILDER,
                technique = 2, consistency = 2, strength = 2, mobility = 2,
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertEquals(com.example.kpkn.data.models.TrainingStyle.POWERLIFTER, repaired.volumeAnswers.style)
        assertTrue(repaired.wizChat.acceptedAnswers.none { it.questionId == WizChatQuestionId.T_STYLE })
        assertEquals(com.example.kpkn.data.models.TrainingStyle.POWERLIFTER, repaired.volumeCalibrationProfile?.trainingStyle)
        assertTrue(repaired.volumeRecommendations.isNotEmpty())
    }

    @Test
    fun broadGoalsKeepTheirExplicitlyChosenFocus() {
        val old = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        ).copy(
            goal = SetupGoal.HEALTH,
            volumeAnswers = SetupVolumeAnswers(
                style = com.example.kpkn.data.models.TrainingStyle.POWERBUILDER,
                technique = 2, consistency = 2, strength = 2, mobility = 2,
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)
        assertEquals(com.example.kpkn.data.models.TrainingStyle.POWERBUILDER, repaired.volumeAnswers.style)
    }

    @Test
    fun legacyWizChatDraftMigratesToTheMatchingStepAndKeepsEveryAnswer() {
        val old = draft(
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(
                nameAnswer(),
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
                choice(WizChatQuestionId.P_EXPERIENCE, "Tengo experiencia"),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        // The r2 route keeps equation sex in BASICS. It is still pending in this
        // legacy payload, so the stable cursor resumes there before training.
        assertEquals(SetupStepId.EQUATION_SEX, repaired.stepProgress.currentStepId)
        assertEquals(SetupWizardBlock.BASICS, repaired.stepProgress.block)
        assertEquals(emptySet<SetupWizardBlock>(), repaired.stepProgress.completedBlocks)
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, repaired.stepProgress.origin)
        // La migración conserva el payload legacy completo y solo añade procedencia.
        assertEquals(old.wizChat.acceptedAnswers, repaired.wizChat.acceptedAnswers)
        assertEquals(old.ageYears, repaired.ageYears)
        assertEquals(old.weightKg, repaired.weightKg)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, repaired.stepProgress.answers[SetupStepId.AGE])
        assertEquals(SetupAnswerProvenance.USER_DECLARED, repaired.stepProgress.answers[SetupStepId.EXPERIENCE])
    }

    @Test
    fun nSexMigratesToEquationSexOnlyWhenExplicit() {
        val vitals = listOf(
            nameAnswer(),
            answer(WizChatQuestionId.P_AGE, 30.0),
            answer(WizChatQuestionId.P_HEIGHT, 175.0),
            answer(WizChatQuestionId.P_WEIGHT, 72.0),
        )

        val explicit = draft(
            current = WizChatQuestionId.N_SEX,
            answers = vitals + choice(WizChatQuestionId.N_SEX, "Femenino", revision = 5),
        )
        val explicitRepaired = SetupDraftCompatibility.repair(explicit)
        assertEquals(SetupStepId.EQUATION_SEX, explicitRepaired.stepProgress.currentStepId)
        assertEquals(
            SetupAnswerProvenance.USER_DECLARED,
            explicitRepaired.stepProgress.answers[SetupStepId.EQUATION_SEX],
        )
        assertFalse(SetupStepId.NUTRITION_SEX in explicitRepaired.stepProgress.answers)

        val neutral = draft(
            current = WizChatQuestionId.N_SEX,
            answers = vitals + choice(WizChatQuestionId.N_SEX, "Prefiero no responder", revision = 5),
        )
        val neutralRepaired = SetupDraftCompatibility.repair(neutral)
        // El cursor se reanuda en el paso mapeado, pero sin procedencia: la
        // respuesta neutral no cuenta como declarada y el paso queda pendiente.
        assertEquals(SetupStepId.EQUATION_SEX, neutralRepaired.stepProgress.currentStepId)
        assertNull(neutralRepaired.stepProgress.answers[SetupStepId.EQUATION_SEX])
        assertTrue(neutralRepaired.wizChat.acceptedAnswers.any { it.questionId == WizChatQuestionId.N_SEX })
    }

    @Test
    fun genderIdentityIsNeverConvertedIntoEquationSex() {
        val old = draft(
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
                choice(WizChatQuestionId.P_GENDER, "Mujer"),
                WizChatAnswerRecord(WizChatQuestionId.T_HOME_EQUIPMENT, WizChatAnswerKind.MULTI_CHOICE,
                    values = listOf("Bandas", "Mancuernas"), revision = 5),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertNull(repaired.stepProgress.answers[SetupStepId.GENDER])
        assertNull(repaired.stepProgress.answers[SetupStepId.HOME_EQUIPMENT])
        assertNull(repaired.stepProgress.answers[SetupStepId.EQUATION_SEX])
        // Las respuestas legacy se conservan en el espejo, intactas.
        assertTrue(repaired.wizChat.acceptedAnswers.any { it.questionId == WizChatQuestionId.P_GENDER })
        assertTrue(repaired.wizChat.acceptedAnswers.any { it.questionId == WizChatQuestionId.T_HOME_EQUIPMENT })
    }

    @Test
    fun nonConvertibleDraftsArePreservedUntilTheUserDiscardsThem() {
        val broken = draft(
            scope = "nutrition_only",
            current = WizChatQuestionId.T_WEEKDAYS,
            answers = listOf(answer(WizChatQuestionId.P_WEIGHT, 72.0)),
            age = null,
            height = null,
        ).copy(includeTraining = false, selectedCatalogId = "plan-legacy")
        val repaired = SetupDraftCompatibility.repair(broken)

        assertEquals(SetupProgressOrigin.NOT_CONVERTIBLE, repaired.stepProgress.origin)
        // Ningún dato se borra: el borrador se conserva tal cual.
        assertEquals(broken.wizChat, repaired.wizChat)
        assertEquals(broken.weightKg, repaired.weightKg)
        assertEquals("plan-legacy", repaired.selectedCatalogId)
        assertEquals(SetupStepId.WEEKDAYS, repaired.stepProgress.currentStepId)
        assertEquals(broken.stepProgress.answers, repaired.stepProgress.answers)
        assertEquals(broken.stepProgress.pendingReview, repaired.stepProgress.pendingReview)
        assertEquals(repaired, SetupDraftCompatibility.repair(repaired))
    }

    @Test
    fun stepMigrationIsIdempotentAndNativeProgressIsLeftUntouched() {
        val old = draft(
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        )
        val migrated = SetupDraftCompatibility.repair(old)
        assertEquals(migrated.stepProgress, SetupDraftCompatibility.repair(migrated).stepProgress)

        val rings = draft(
            scope = "rings_only",
            current = WizChatQuestionId.R_RECENT,
            answers = emptyList(),
            age = null,
            height = null,
            weight = null,
        )
        val ringsOnlyDraft = rings.copy(includeTraining = false, includeNutrition = false)
        val ringsContext = ringsOnlyDraft.stepContext()
        val native = ringsOnlyDraft.copy(
            stepProgress = SetupStepProgress.initial(ringsContext)
                .recordAnswer(
                    SetupStepId.RINGS_RECENT,
                    SetupAnswerProvenance.USER_DECLARED,
                    com.example.kpkn.domain.onboarding.SetupValueState.DECLARED,
                )
                .at(SetupStepId.RINGS_MUSCLE_FEELING, ringsContext),
        )
        assertEquals(native, SetupDraftCompatibility.repair(native))
    }

    /**
     * Reabrir un borrador a mitad del bloque de Entreno deja el cursor donde estaba: los días se marcan «por revisar» con su propia
     * primera respuesta y confirmarlos cierra esa marca. Un paso marcado y NO vuelto a confirmar (días cambiados por una reparación del
     * plan) sí devuelve el cursor a él.
     */
    @Test
    fun reopeningAMidBlockDraftKeepsTheCursorUnlessAnEarlierStepIsReallyPending() {
        val base = draft(
            scope = "training_only",
            answers = listOf(
                nameAnswer(),
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        ).copy(includeNutrition = false)
        val context = base.stepContext()
        val declared = com.example.kpkn.domain.onboarding.SetupValueState.DECLARED

        // Días respondidos y confirmados después de marcarse: el cursor sigue en el tiempo por sesión.
        val confirmedAfterFlag = progressAt(SetupStepId.WEEKDAYS, context)
            .withPendingReview(setOf(SetupStepId.WEEKDAYS))
            .recordAnswer(SetupStepId.WEEKDAYS, SetupAnswerProvenance.USER_DECLARED, declared)
            .at(SetupStepId.SESSION_TIME, context)
        val reopened = SetupDraftCompatibility.repair(base.copy(stepProgress = confirmedAfterFlag))
        assertEquals(SetupStepId.SESSION_TIME, reopened.stepProgress.currentStepId)

        // Un paso marcado que nadie ha vuelto a confirmar sí manda: el cursor vuelve a él.
        val stillPending = progressAt(SetupStepId.SESSION_TIME, context).withPendingReview(setOf(SetupStepId.WEEKDAYS))
        val resumed = SetupDraftCompatibility.repair(base.copy(stepProgress = stillPending))
        assertEquals(SetupStepId.WEEKDAYS, resumed.stepProgress.currentStepId)
        assertEquals(stillPending.answers, resumed.stepProgress.answers)
    }

    @Test
    fun nativeDraftStrandedOnLegacyGenderCursorResumesKeepingAnswersAndOrigin() {
        val stranded = draft(
            current = WizChatQuestionId.P_GENDER,
            answers = listOf(
                nameAnswer(),
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        ).copy(
            stepProgress = SetupStepProgress(
                origin = SetupProgressOrigin.NATIVE,
                currentStepId = SetupStepId.GENDER,
                block = SetupWizardBlock.BASICS,
                visited = listOf(SetupStepId.NAME, SetupStepId.GENDER),
                answers = mapOf(SetupStepId.AGE to SetupAnswerProvenance.USER_DECLARED),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(stranded)

        // El cursor sale de GENDER y de las vitales conservadas al primer paso
        // pendiente. La identidad antigua no responde al sexo de cálculo.
        assertEquals(SetupStepId.EQUATION_SEX, repaired.stepProgress.currentStepId)
        assertEquals(SetupWizardBlock.BASICS, repaired.stepProgress.block)
        // Jamás se marca NOT_CONVERTIBLE y el origen nativo se conserva.
        assertEquals(SetupProgressOrigin.NATIVE, repaired.stepProgress.origin)
        // Respuestas y espejo intactos; identidad nunca convertida en sexo.
        assertEquals(stranded.stepProgress.answers, repaired.stepProgress.answers)
        assertNull(repaired.stepProgress.answers[SetupStepId.EQUATION_SEX])
        assertEquals(stranded.wizChat.acceptedAnswers, repaired.wizChat.acceptedAnswers)
        assertEquals(30, repaired.ageYears)
        assertEquals(72.0, repaired.weightKg!!, 0.0001)
    }

    @Test
    fun nativeRingsDraftStrandedOnLegacyStartResumesInsideTheRingsBlock() {
        val stranded = draft(
            scope = "rings_only",
            current = WizChatQuestionId.R_START,
            answers = emptyList(),
            age = null,
            height = null,
            weight = null,
        ).copy(
            includeTraining = false,
            includeNutrition = false,
            stepProgress = SetupStepProgress(
                origin = SetupProgressOrigin.NATIVE,
                currentStepId = SetupStepId.RINGS_START,
                block = SetupWizardBlock.RINGS,
                visited = listOf(SetupStepId.RINGS_RECENT, SetupStepId.RINGS_START),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(stranded)

        assertEquals(SetupStepId.RINGS_RECENT, repaired.stepProgress.currentStepId)
        assertEquals(SetupWizardBlock.RINGS, repaired.stepProgress.block)
        assertEquals(SetupProgressOrigin.NATIVE, repaired.stepProgress.origin)
        assertEquals(emptySet<SetupWizardBlock>(), repaired.stepProgress.completedBlocks)
        // El espejo legacy se conserva tal cual (el cursor del paso se reparó).
        assertEquals(stranded.wizChat, repaired.wizChat)
    }

    @Test
    fun nativeTypedVitalsCountAsDeclaredAndNeverRewindTheCursor() {
        val nativeBase = draft(
            current = WizChatQuestionId.T_WEEKDAYS,
            answers = emptyList(),
        )
        val native = nativeBase.copy(
            stepProgress = progressAt(
                SetupStepId.WEEKDAYS,
                nativeBase.stepContext(),
                answerOverrides = mapOf(SetupStepId.WEIGHT to SetupAnswerProvenance.SUGGESTED),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(native)

        // Los registros tipeados del progreso cuentan: nada queda pendiente.
        assertEquals(emptyList<WizChatQuestionId>(), SetupDraftCompatibility.pendingMandatoryVitals(repaired))
        // Ni el espejo legacy ni el puntero nativo se tocan.
        assertEquals(native.wizChat.currentQuestionId, repaired.wizChat.currentQuestionId)
        assertEquals(SetupStepId.WEEKDAYS, repaired.stepProgress.currentStepId)
        assertEquals(SetupProgressOrigin.NATIVE, repaired.stepProgress.origin)
        assertEquals(native.stepProgress.answers, repaired.stepProgress.answers)
        assertEquals(native.stepProgress.revision, repaired.stepProgress.revision)
        assertEquals(native.ageYears, repaired.ageYears)
        assertEquals(native.heightCm, repaired.heightCm)
        assertEquals(native.weightKg, repaired.weightKg)
    }

    @Test
    fun fakeOldNativeProgressKeepsItsTypedRecordsInsteadOfBeingMigrated() {
        val fakeOld = draft(
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        ).copy(
            // Origen por defecto (NOT_CONVERTIBLE) con cursor y registros
            // tipeados: parece "old", pero es progreso nativo con un vital
            // confirmado — no puede migrarse ni rebobinarse.
            stepProgress = SetupStepProgress(
                currentStepId = SetupStepId.HEIGHT,
                block = SetupWizardBlock.BASICS,
                visited = listOf(SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT),
                answers = mapOf(
                    SetupStepId.NAME to SetupAnswerProvenance.USER_DECLARED,
                    SetupStepId.AGE to SetupAnswerProvenance.USER_DECLARED,
                ),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(fakeOld)

        assertEquals(SetupProgressOrigin.NATIVE, repaired.stepProgress.origin)
        assertEquals(SetupStepId.HEIGHT, repaired.stepProgress.currentStepId)
        assertEquals(fakeOld.stepProgress.answers, repaired.stepProgress.answers)
        assertEquals(fakeOld.wizChat.currentQuestionId, repaired.wizChat.currentQuestionId)
        assertEquals(emptySet<SetupWizardBlock>(), repaired.stepProgress.completedBlocks)
        // Idempotente: una segunda reparación no vuelve a tocar nada.
        assertEquals(repaired.stepProgress, SetupDraftCompatibility.repair(repaired).stepProgress)
    }

    @Test
    fun importedOrUnknownLegacySourcesNeverLookDeclaredInTheNewProgress() {
        val old = draft(
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(
                nameAnswer(),
                answer(WizChatQuestionId.P_AGE, 30.0, source = WizChatAnswerSource.IMPORTED),
                answer(WizChatQuestionId.P_HEIGHT, 175.0, source = WizChatAnswerSource.UNKNOWN),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
                choice(WizChatQuestionId.P_EXPERIENCE, "Tengo experiencia", revision = 5),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        // El espejo legacy conserva los valores intactos (IMPORTED/UNKNOWN incluidos)…
        assertEquals(old.wizChat.acceptedAnswers, repaired.wizChat.acceptedAnswers)
        // …pero IMPORTED/UNKNOWN jamás cuentan como declarados: el vital sigue
        // pendiente aunque exista un número en el JSON.
        assertEquals(
            listOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT),
            SetupDraftCompatibility.pendingMandatoryVitals(repaired),
        )
        assertNull(repaired.stepProgress.answers[SetupStepId.AGE])
        assertNull(repaired.stepProgress.answers[SetupStepId.HEIGHT])
        assertEquals(SetupAnswerProvenance.USER_DECLARED, repaired.stepProgress.answers[SetupStepId.WEIGHT])
        assertEquals(SetupAnswerProvenance.USER_DECLARED, repaired.stepProgress.answers[SetupStepId.EXPERIENCE])
        // Al ser progreso legacy con vitales sin declarar, el cursor del espejo
        // rebobina a la primera vital pendiente y la migración arranca allí.
        assertEquals(WizChatQuestionId.P_AGE, repaired.wizChat.currentQuestionId)
        assertEquals(SetupStepId.AGE, repaired.stepProgress.currentStepId)
        assertEquals(SetupWizardBlock.BASICS, repaired.stepProgress.block)
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, repaired.stepProgress.origin)
        assertEquals(emptySet<SetupWizardBlock>(), repaired.stepProgress.completedBlocks)
        // Los valores numéricos no se pierden ni se inventan: siguen donde estaban.
        assertEquals(30, repaired.ageYears)
        assertEquals(175.0, repaired.heightCm!!, 0.0001)
        assertEquals(72.0, repaired.weightKg!!, 0.0001)
    }

    @Test
    fun nativeValuesWinOverTheStaleLegacyMirrorAndStayIdempotent() {
        val nativeDraft = draft(
            current = WizChatQuestionId.T_WEEKDAYS,
            answers = listOf(
                // Espejo viejo: peso 70 y textos crudos de la versión anterior.
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 70.0),
                WizChatAnswerRecord(
                    WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT,
                    textValue = "Ana", revision = 2,
                ),
            ),
        ).copy(
            // Valores nativos más nuevos que el espejo.
            ageYears = 31,
            heightCm = 176.0,
            weightKg = 75.0,
        )
        val native = nativeDraft.copy(
            stepProgress = progressAt(
                SetupStepId.WEEKDAYS,
                nativeDraft.stepContext(),
                answerOverrides = mapOf(SetupStepId.WEIGHT to SetupAnswerProvenance.SUGGESTED),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(native)

        // La autoridad nativa gana: no se revierte 75 kg a los 70 del espejo.
        assertEquals(31, repaired.ageYears)
        assertEquals(176.0, repaired.heightCm!!, 0.0001)
        assertEquals(75.0, repaired.weightKg!!, 0.0001)
        // Textos crudos y payload legacy intactos, cursor y procedencia igual.
        assertEquals(native.wizChat.acceptedAnswers, repaired.wizChat.acceptedAnswers)
        assertEquals("Ana", repaired.wizChat.acceptedAnswers.firstOrNull {
            it.questionId == WizChatQuestionId.P_NAME
        }?.textValue)
        assertEquals(native.wizChat.currentQuestionId, repaired.wizChat.currentQuestionId)
        assertEquals(SetupStepId.WEEKDAYS, repaired.stepProgress.currentStepId)
        assertEquals(native.stepProgress.answers, repaired.stepProgress.answers)
        assertEquals(SetupProgressOrigin.NATIVE, repaired.stepProgress.origin)
        assertEquals(emptyList<WizChatQuestionId>(), SetupDraftCompatibility.pendingMandatoryVitals(repaired))
        // Idempotente: reparar de nuevo no cambia nada.
        assertEquals(native, repaired)
        assertEquals(repaired, SetupDraftCompatibility.repair(repaired))
    }

    @Test
    fun nativeDeletedWeightStaysNullAndIsNotResurrectedFromTheMirror() {
        val nativeBase = draft(
            current = WizChatQuestionId.P_HEIGHT,
            weight = null,
            answers = listOf(answer(WizChatQuestionId.P_WEIGHT, 70.0)),
        )
        val native = nativeBase.copy(
            stepProgress = progressAt(
                SetupStepId.HEIGHT,
                nativeBase.stepContext(),
                answerOverrides = mapOf(
                    SetupStepId.HEIGHT to SetupAnswerProvenance.USER_DECLARED,
                    // El registro nativo existe: el campo es autoridad nativa.
                    SetupStepId.WEIGHT to SetupAnswerProvenance.USER_DECLARED,
                ),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(native)

        // Un dato nativo borrado sigue borrado: no se rellena 70 kg desde el espejo.
        assertNull(repaired.weightKg)
        // El JSON legacy conserva su número (podría presentarse como sugerencia),
        // pero el vital permanece PENDIENTE para M1, que decide al confirmar.
        assertTrue(repaired.wizChat.acceptedAnswers.any { it.questionId == WizChatQuestionId.P_WEIGHT })
        assertEquals(
            listOf(WizChatQuestionId.P_WEIGHT),
            SetupDraftCompatibility.pendingMandatoryVitals(repaired),
        )
        // Ni cursor ni procedencia se tocan.
        assertEquals(WizChatQuestionId.P_HEIGHT, repaired.wizChat.currentQuestionId)
        assertEquals(SetupStepId.HEIGHT, repaired.stepProgress.currentStepId)
        assertEquals(SetupProgressOrigin.NATIVE, repaired.stepProgress.origin)
        assertEquals(native.stepProgress.answers, repaired.stepProgress.answers)
        assertEquals(repaired, SetupDraftCompatibility.repair(repaired))
    }

    @Test
    fun catalogRevisionChangeKeepsEveryAnswerAndOnlyFlagsTheSelectionForReview() {
        val baseDraft = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        )
        val context = SetupStepContext(nutritionStarted = true)
        val base = baseDraft.copy(
            selectedCatalogId = "plan-v1",
            stepProgress = progressAt(SetupStepId.PLAN, context),
        )

        // Sin cambio de catálogo no se toca nada.
        assertEquals(base, SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-1") { true })

        // El plan desapareció del catálogo nuevo: se conserva todo el borrador
        // y solo se marca la selección como pendiente de revisión.
        val missing = SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-2") { false }
        assertEquals("rev-2", missing.catalogRevision)
        assertNull(missing.selectedCatalogId)
        assertEquals(base.wizChat.acceptedAnswers, missing.wizChat.acceptedAnswers)
        assertEquals(base.ageYears, missing.ageYears)
        assertEquals(base.weightKg, missing.weightKg)
        assertEquals(base.stepProgress.answers, missing.stepProgress.answers)
        assertEquals(SetupStepId.PLAN, missing.stepProgress.currentStepId)
        assertTrue(SetupStepId.PLAN in missing.stepProgress.pendingReview)
        assertTrue(SetupPreviewKind.PLAN_CANDIDATES in missing.stepProgress.stalePreviews)
        assertTrue(SetupPreviewKind.EXERCISES in missing.stepProgress.stalePreviews)
        assertFalse(missing.wizChat.terminal)

        // El plan sigue existiendo: se conserva la selección y se pide revisar.
        val kept = SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-2") { it == "plan-v1" }
        assertEquals("plan-v1", kept.selectedCatalogId)
        assertTrue(SetupStepId.PLAN in kept.stepProgress.pendingReview)
        assertEquals(base.wizChat.acceptedAnswers, kept.wizChat.acceptedAnswers)
    }
}
