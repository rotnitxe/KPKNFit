package com.example.kpkn.screens.onboarding

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toBodyObservation
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.domain.onboarding.SetupStepId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Entreno v2 (QP, teléfono real) · las observaciones corporales de dos altas del mismo día no se pisan.
 *
 * El borrador del asistente tiene un id canónico por alcance («setup-wizard:training_only»): tras activar se borra y el siguiente
 * asistente del mismo alcance parte con el MISMO id. Si las filas se identificaran por ese id y la fecha, una segunda alta del
 * mismo día que vuelve a declarar el peso chocaría con la primera («La observación … ya existe y no fue creada por este alta») y
 * no dejaría activar. Se identifican por el `commitId` del borrador: único por asistente y estable al reintentar el mismo alta.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupActivationPayloadIdsTest {
    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val weighted = setOf(SetupStepId.WEIGHT, SetupStepId.BODY_FAT)

    private fun draft(commitId: String, draftId: String = "setup-wizard:training_only", weightKg: Double = 64.0) = SetupWizardDraft(
        draftId = draftId,
        commitId = commitId,
        weightKg = weightKg,
        bodyFatPercent = 24.0,
        bodyFatSource = SetupBodyFatSource.VISUAL_ESTIMATE,
        declaredSteps = weighted,
    )

    private fun request(draft: SetupWizardDraft) = SetupCommitRequest(
        draft.commitId, draft.draftId, Settings(), null, null, false, false,
        bodyObservations = SetupActivationPayload.bodyObservations(draft),
    )

    @Test
    fun two_wizards_with_the_same_canonical_draft_id_write_rows_with_different_ids() {
        val first = SetupActivationPayload.bodyObservations(draft(commitId = "alta-a"))
        val second = SetupActivationPayload.bodyObservations(draft(commitId = "alta-b", weightKg = 64.5))
        assertEquals("peso y grasa", 2, first.size)
        assertEquals(2, second.size)
        assertTrue(
            "ninguna fila de la segunda alta comparte id con la primera: ${first.map { it.id }} / ${second.map { it.id }}",
            first.map { it.id }.intersect(second.map { it.id }.toSet()).isEmpty(),
        )
    }

    @Test
    fun the_same_wizard_retrying_its_activation_writes_the_same_ids() {
        val ids = { SetupActivationPayload.bodyObservations(draft(commitId = "alta-a")).map { it.id }.sorted() }
        assertEquals("el reintento del mismo alta es idempotente", ids(), ids())
    }

    @Test
    fun the_row_ids_keep_their_kind_and_date_so_they_stay_readable() {
        val ids = SetupActivationPayload.bodyObservations(draft(commitId = "alta-a")).map { it.id }
        assertTrue(ids.any { "/weight/" in it })
        assertTrue(ids.any { "/bodyfat/" in it })
        assertTrue("empiezan por el commit del alta: $ids", ids.all { it.startsWith("alta-a/") })
    }

    @Test
    fun a_draft_without_commit_id_still_gets_a_stable_key_from_its_draft_id() {
        val ids = SetupActivationPayload.bodyObservations(draft(commitId = "", draftId = "borrador-x")).map { it.id }
        assertTrue("sin commitId usa el id del borrador: $ids", ids.all { it.startsWith("borrador-x/") })
    }

    @Test
    fun a_second_real_activation_the_same_day_with_the_same_canonical_draft_id_goes_through() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        // Como en el teléfono: el mismo borrador canónico, otro asistente (otro commitId) y el peso declarado de nuevo.
        coordinator.commit(request(draft(commitId = "alta-a")))
        coordinator.commit(request(draft(commitId = "alta-b", weightKg = 64.5)))

        val saved = db.bodyProgressDao().getAllObservations().map { it.toBodyObservation() }
        assertEquals("peso y grasa de cada alta", 4, saved.size)
        assertEquals(setOf(64.0, 64.5), saved.filter { it?.unitSi == "kg" }.map { it?.valueSi }.toSet())
    }

    @Test
    fun replaying_the_same_activation_does_not_duplicate_or_fail() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        val first = coordinator.commit(request(draft(commitId = "alta-a")))
        val again = coordinator.commit(request(draft(commitId = "alta-a")))
        assertEquals(first, again)
        assertEquals(2, db.bodyProgressDao().getAllObservations().size)
    }
}
