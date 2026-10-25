package com.example.kpkn.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@SQLiteMode(SQLiteMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TrainingObservationRepositoryReconnectTest {
    @get:Rule val roomRule = RoomTestRule()

    @Test fun originalContextSurvivesRoomReconnectWithTheSameRepository() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = TrainingObservationRepository(context)
        try {
            repository.recordContext("reconnect-before", "session-before", 1234L, emptyList(), "Original antes de reconectar")
            KpknDatabase.closeInstance()
            repository.recordContext("reconnect-after", "session-after", 5678L, listOf("shoulder"), "Original después de reconectar")

            val rows = KpknDatabase.getInstance(context).trainingObservationDao().all()
                .filter { it.sourceIdentity in setOf("reconnect-before", "reconnect-after") }
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.provenance == "explicit_self_report;optional;not_validated_scale" })
            val before = Json.parseToJsonElement(rows.first { it.sourceIdentity == "reconnect-before" }.payload).jsonObject
            val after = Json.parseToJsonElement(rows.first { it.sourceIdentity == "reconnect-after" }.payload).jsonObject
            assertEquals("Original antes de reconectar", before.getValue("note").jsonPrimitive.content)
            assertEquals("Original después de reconectar", after.getValue("note").jsonPrimitive.content)
            assertEquals("1234", before.getValue("capturedAtMs").jsonPrimitive.content)
            assertEquals("5678", after.getValue("capturedAtMs").jsonPrimitive.content)
        } finally {
            KpknDatabase.closeInstance()
        }
    }
}
