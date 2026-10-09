package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.capabilitiesBodyweightRoute
import com.example.kpkn.screens.onboarding.design.entreno.capabilityTag
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withMaterial
import com.example.kpkn.screens.onboarding.withPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los pasos del punto de partida, probados sin el ViewModel: cada contenido recibe lo elegido y avisa con sus lambdas. «Ninguno»
 * es exclusivo; cardio y estilo de mesa son de elección única; las repeticiones de la ruta de peso corporal son reglas (con «No
 * lo sé» y «Obligatoria»), y la sentadilla a una pierna conserva sus niveles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StartingPointStepsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun noneIsExclusiveWhenAGroupIsAvoided() {
        val toggled = mutableListOf<AvoidGroup>()
        var noneCount = 0
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AvoidGroupsContent(
                    selected = setOf(AvoidGroup.DEADLIFT),
                    onToggle = { toggled += it },
                    onNone = { noneCount++ },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(AVOID_NONE_TAG).assertIsOff()
        rule.onNodeWithTag(avoidTag(AvoidGroup.DEADLIFT)).assertIsOn()
        rule.onNodeWithTag(avoidTag(AvoidGroup.DEADLIFT)).assertContentDescriptionEquals("Peso muerto, evitar")
        rule.onNodeWithTag(avoidTag(AvoidGroup.BARBELL_SQUAT)).assertContentDescriptionEquals("Sentadilla con barra, sin evitar")
        rule.onNodeWithTag(AVOID_NONE_TAG).performClick()
        assertEquals(1, noneCount)
        assertTrue(toggled.isEmpty())
    }

    @Test
    fun tappingAGroupWhenNoneIsOnAvoidsIt() {
        val toggled = mutableListOf<AvoidGroup>()
        var noneCount = 0
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AvoidGroupsContent(
                    selected = emptySet(),
                    onToggle = { toggled += it },
                    onNone = { noneCount++ },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(AVOID_NONE_TAG).assertIsOn()
        rule.onNodeWithTag(avoidTag(AvoidGroup.JUMPS)).performClick()
        assertEquals(listOf(AvoidGroup.JUMPS), toggled)
        assertEquals(0, noneCount)
    }

    @Test
    fun cardioBaseIsASingleChoiceThatTappingTheChosenOneDoesNotClear() {
        val chosen = mutableListOf<CardioBase>()
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CardioBaseContent(selected = CardioBase.MODERATE, onChoose = { chosen += it })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(cardioBaseTag(CardioBase.MODERATE)).assertIsSelected()
        rule.onNodeWithTag(cardioBaseTag(CardioBase.LOW)).assertIsNotSelected()
        rule.onNodeWithTag(cardioBaseTag(CardioBase.LOW)).performClick()
        rule.onNodeWithTag(cardioBaseTag(CardioBase.MODERATE)).performClick()
        assertEquals(listOf(CardioBase.LOW), chosen)
    }

    @Test
    fun armStyleIsASingleChoiceThatTappingTheChosenOneDoesNotClear() {
        val chosen = mutableListOf<ArmStyle>()
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ArmStyleContent(selected = ArmStyle.HOOK, onChoose = { chosen += it })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(armStyleTag(ArmStyle.HOOK)).assertIsSelected()
        rule.onNodeWithTag(armStyleTag(ArmStyle.PRESS)).assertIsNotSelected()
        rule.onNodeWithTag(armStyleTag(ArmStyle.PRESS)).performClick()
        rule.onNodeWithTag(armStyleTag(ArmStyle.HOOK)).performClick()
        assertEquals(listOf(ArmStyle.PRESS), chosen)
    }

    @Test
    fun theRepsRuleDeclaresRepsAndTheUnknownToggleClearsThem() {
        val written = mutableListOf<Pair<CapabilitySkill, Int?>>()
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CapabilityRepsRows(
                    skills = listOf(CapabilitySkill.PULL_UP, CapabilitySkill.PUSH_UP, CapabilitySkill.PISTOL_SQUAT),
                    reps = mapOf(CapabilitySkill.PUSH_UP to 8),
                    levels = mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.SOME),
                    required = setOf(CapabilitySkill.PULL_UP),
                    onReps = { skill, count -> written += skill to count },
                    onLevel = { _, _ -> },
                )
            }
        }
        rule.waitForIdle()
        // Las dominadas son obligatorias: no tienen «No lo sé», y su regla empieza sin declarar.
        rule.onNodeWithText("Obligatoria").assertExists()
        rule.onNodeWithTag(repsUnknownTag(CapabilitySkill.PULL_UP)).assertDoesNotExist()
        rule.onNodeWithTag(repsTag(CapabilitySkill.PULL_UP)).assertContentDescriptionEquals("Dominadas, repeticiones: sin declarar")
        // Flexiones: con 8 declaradas, «No lo sé» está apagado y tocarlo lo borra.
        rule.onNodeWithTag(repsUnknownTag(CapabilitySkill.PUSH_UP)).assertIsOff()
        rule.onNodeWithTag(repsUnknownTag(CapabilitySkill.PUSH_UP)).performClick()
        rule.waitForIdle()
        assertEquals(CapabilitySkill.PUSH_UP to null, written.last())
        // Mover la regla de dominadas declara sus repeticiones.
        rule.onNodeWithTag(repsTag(CapabilitySkill.PULL_UP)).performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        rule.waitForIdle()
        assertEquals(CapabilitySkill.PULL_UP to 9, written.last())
    }

    @Test
    fun theSideSquatKeepsItsThreeLevelsOutOfTheRepsRule() {
        val levels = mutableListOf<Pair<CapabilitySkill, CapabilityLevel>>()
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CapabilityRepsRows(
                    skills = listOf(CapabilitySkill.PISTOL_SQUAT),
                    reps = emptyMap(),
                    levels = emptyMap(),
                    required = emptySet(),
                    onReps = { _, _ -> },
                    onLevel = { skill, level -> levels += skill to level },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(repsTag(CapabilitySkill.PISTOL_SQUAT)).assertDoesNotExist()
        rule.onNodeWithTag(capabilityTag(CapabilitySkill.PISTOL_SQUAT, CapabilityLevel.MANY)).performClick()
        assertEquals(listOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.MANY), levels)
    }

    @Test
    fun theRouteIsCalisthenicsOrOnlyBodyweightAndNotTheGym() {
        assertTrue(SetupWizardDraft().withGoalProfile(TrainingGoalProfile.CALISTHENICS).capabilitiesBodyweightRoute())
        assertTrue(
            SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME))
                .withMaterial(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)).capabilitiesBodyweightRoute(),
        )
        assertFalse(
            SetupWizardDraft().withGoalProfile(TrainingGoalProfile.STRENGTH_MUSCLE)
                .withPlaces(setOf(TrainingPlace.GYM)).capabilitiesBodyweightRoute(),
        )
    }
}
