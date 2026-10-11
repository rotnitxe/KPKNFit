package com.example.kpkn.domain.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures.Gear
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramExecutionContract
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contrato central de §15.2/§15.4 para los planes de autor: el programa que el
 * evaluador entrega como `Ready` (el que ve el preview) es EXACTAMENTE el que
 * activa el coordinador de Room y el que se reabre, con la procedencia
 * (ORIGINAL/ADAPTED, edición, slotChanges) intacta. El puerto del evaluador
 * llama a la misma [AuthoredPlanMaterializer.prepare] que el ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AuthoredPlansActivationParityTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun installCatalog() {
            CatalogCompositionTestSupport.install()
        }
    }

    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = KpknDatabase.createInMemory(app)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun <T> room(block: suspend () -> T): T = runBlocking { block() }

    private fun requestFor(
        planId: String,
        gear: Gear,
        level: CatalogLevel = CatalogLevel.INTERMEDIATE,
    ): PlanCandidateRequest {
        val catalog = CatalogCompositionTestSupport.catalog
        val entry = AuthoredPlanFixtures.entry(planId)
        val days = entry.supportedFrequencies.first
        return PlanCandidateRequest(
            inputKey = "parity|$planId|${gear.label}",
            goalProfile = PlanGoalProfile.STRENGTH_MUSCLE,
            level = level,
            focus = TrainingFocus.FULL_BODY,
            reference = TrainingReference.POWERBUILDING,
            daysPerWeek = days,
            weekdays = if (days == 4) setOf(1, 2, 4, 5) else setOf(1, 2, 4, 5, 6),
            minutesPerSession = 100,
            effectiveEquipment = gear.equipment.tokens,
            planCatalogRevision = PersonalizedPlanCatalog.REVISION,
            exerciseCatalogRevision = catalog.catalogRevision,
        )
    }

    /** Puerto idéntico al del ViewModel: misma preparación, misma receta efectiva. */
    private fun portFor(gear: Gear, programId: String) = PlanMaterializationPort { entry, _ ->
        val program = AuthoredPlanMaterializer.prepare(
            AuthoredPlanFixtures.request(entry, gear, programId = programId),
        )
        PlanMaterializationOutcome(program, program.sourceRecipe)
    }

    private fun evaluate(planId: String, gear: Gear, programId: String): PlanCandidateEvaluation {
        val catalog = CatalogCompositionTestSupport.catalog
        return runBlocking {
            PlanCandidateEvaluator.evaluate(
                request = requestFor(planId, gear),
                snapshot = PlanCatalogSnapshot(
                    entries = PersonalizedPlanCatalog.entries(),
                    planRevision = PersonalizedPlanCatalog.REVISION,
                    exerciseCatalogRevision = catalog.catalogRevision,
                ),
                entryId = planId,
                engine = portFor(gear, programId),
            )
        }
    }

    private fun commit(program: Program): SetupCommitResult = room {
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = program.id,
                draftId = null,
                settings = Settings(),
                program = program,
                nutritionPlan = null,
                activateProgram = true,
                activateNutrition = false,
            ),
        )
    }

    @Test
    fun theReadyOriginalIsExactlyWhatRoomActivatesAndReopens() {
        val programId = "parity-phul-original"
        val evaluation = evaluate(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, AuthoredPlanFixtures.fullGym, programId)
        assertTrue("PHUL original con gimnasio completo a 100 min debe quedar listo: $evaluation", evaluation is PlanCandidateEvaluation.Ready)
        val ready = evaluation as PlanCandidateEvaluation.Ready

        assertEquals(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, ready.planId)
        assertEquals(ready.preparedPlan.sourceRecipe, ready.recipeSnapshot)
        assertEquals(PlanProvenanceClass.ORIGINAL, ready.provenance?.category)
        assertTrue("el evaluador midió cada sesión con el estimador común", ready.durationBreakdown.fitsInto(100))
        assertTrue(ProgramExecutionContract.validate(ready.preparedPlan).isEmpty())

        val result = commit(ready.preparedPlan)

        assertEquals(SetupCommitResult(programId, programId, null, emptyList()), result)
        val reopened = checkNotNull(room { db.programDao().getById(programId)?.toProgram() }) { "programa no persistido" }
        assertEquals("lo activado == lo previsualizado (receta, sesiones, ids, procedencia)", ready.preparedPlan, reopened)
        assertEquals(programId, room { db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId })
        assertNotNull(room { db.setupCommitReceiptDao().get(programId) })
        assertEquals(PlanProvenanceClass.ORIGINAL, reopened.planProvenance?.category)
        assertTrue(reopened.planProvenance?.sourceEdition.orEmpty().contains("2021-05-26"))

        // Reintento con el MISMO commit: mismo recibo, ni un programa más.
        assertEquals(result, commit(ready.preparedPlan))
        assertEquals(1, room { db.programDao().getAll() }.size)
        assertEquals(1, room { db.setupCommitReceiptDao().getAll() }.size)
    }

    @Test
    fun theReadyAdaptationKeepsItsSlotChangesThroughActivationAndReopen() {
        val programId = "parity-phul-adapted"
        val evaluation = evaluate(AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID, AuthoredPlanFixtures.gymWithoutRack, programId)
        assertTrue("PHUL adaptado sin rack debe quedar listo: $evaluation", evaluation is PlanCandidateEvaluation.Ready)
        val ready = evaluation as PlanCandidateEvaluation.Ready

        // El Ready publica la receta EFECTIVA (derivada del resolver) y su procedencia, no la del catálogo.
        val recipe = requireNotNull(ready.recipeSnapshot)
        assertTrue(recipe.id.startsWith("${AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID}~adapted-"))
        assertEquals(recipe.provenance, ready.provenance)
        assertEquals(ready.preparedPlan.planProvenance, ready.provenance)
        assertEquals(PlanProvenanceClass.ADAPTED, ready.provenance?.category)
        // Paquete A · B4: sin rack, banca e inclinada (a mancuernas) y las dos sentadillas de barra (a Smith).
        assertEquals(4, ready.provenance?.slotChanges?.size)

        commit(ready.preparedPlan)

        val reopened = checkNotNull(room { db.programDao().getById(programId)?.toProgram() }) { "programa no persistido" }
        assertEquals(ready.preparedPlan, reopened)
        assertEquals(ready.provenance, reopened.planProvenance)
        assertEquals(ready.provenance, reopened.sourceRecipe?.provenance)
        assertEquals(
            "el catálogo vigente no reescribe la adaptación activada",
            AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID,
            reopened.structureTemplateId,
        )
    }

    @Test
    fun anAdaptationWithoutASubstituteIsRejectedBeforeAnythingCanBePublished() {
        val evaluation = evaluate(AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID, AuthoredPlanFixtures.bodyweightOnly, "parity-rejected")

        assertTrue("sin material no hay Ready: $evaluation", evaluation is PlanCandidateEvaluation.Rejected)
        val rejected = evaluation as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.MATERIAL, rejected.stage)
        assertEquals(PlanRejectionReason.NO_VALID_SUBSTITUTION, rejected.reasonCode)
        assertEquals(listOf("inc-db=incline_bench_press__dumbbells"), rejected.affectedSlots)
        assertTrue(rejected.reasonCode != PlanRejectionReason.INTERNAL_MATERIALIZATION)

        // Sin Ready no existe un programa que activar: Room sigue intacto.
        assertNull(room { db.programDao().getById("parity-rejected") })
        assertTrue(room { db.programDao().getAll() }.isEmpty())
        assertTrue(room { db.setupCommitReceiptDao().getAll() }.isEmpty())
        assertNull(room { db.stateDao().getActiveProgram() })
    }

    @Test
    fun anOriginalMissingMaterialIsRejectedWithTheMissingTokensAndNeverModified() {
        val evaluation = evaluate(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID, AuthoredPlanFixtures.dumbbellsAndBench, "parity-phat-db")

        assertTrue("PHAT original sin barra ni máquinas no es viable: $evaluation", evaluation is PlanCandidateEvaluation.Rejected)
        val rejected = evaluation as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.MATERIAL, rejected.stage)
        assertEquals(PlanRejectionReason.APPARATUS_ABSENT, rejected.reasonCode)
        assertTrue(rejected.details.orEmpty().startsWith(AuthoredPlanMaterializer.MATERIAL_PREFIX))
        assertTrue("barbell" in rejected.details.orEmpty().substringAfter(":"))
    }
}
