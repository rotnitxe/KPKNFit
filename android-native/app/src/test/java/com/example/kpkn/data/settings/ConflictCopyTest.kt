package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.*
import com.example.kpkn.data.db.*
import com.example.kpkn.data.sync.*
import com.example.kpkn.domain.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34])
@OptIn(ExperimentalCoroutinesApi::class)
class ConflictCopyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val json = Json { encodeDefaults = true }
    private lateinit var db: KpknDatabase
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        KpknDatabase.closeInstance(); ProfileStorage.initialize(context); context.deleteDatabase("kpkn.db")
        db = KpknDatabase.getInstance(context)
    }
    @After fun cleanup() { KpknDatabase.closeInstance(); context.deleteDatabase("kpkn.db"); Dispatchers.resetMain() }

    private val data = object : PrivatePayloadTransfer {
        override fun stage(payload: JsonObject) = PrivateDataFiles.stage(context, payload)
        override fun readStaged(reference: PrivateDataReference) = PrivateDataFiles.read(context, reference)
        override suspend fun upload(generation: Long, reference: PrivateDataReference) { readStaged(reference) }
        override suspend fun download(generation: Long, reference: PrivateDataReference) = readStaged(reference)
    }
    private val media = object : SyncMediaTransfer {
        override suspend fun upload(generation: Long, payload: JsonObject?) { error("No media expected") }
        override suspend fun download(generation: Long, payload: JsonObject?) { error("No media expected") }
    }
    private fun operation(payload: JsonObject) = PersonalOperation(operationId = "conflict", entityId = "original",
        sourceId = "original", deviceId = "device-a", accountGeneration = 1, baseRevision = 3,
        kind = "program", changedAtEpochMs = 1000, zoneId = "America/Santiago", unitSystem = "METRIC", deleted = false, payload = payload)
    private suspend fun seed(incoming: PersonalOperation) {
        val sql = db.openHelper.writableDatabase
        val local = buildJsonObject { put("id", "original"); put("name", "Local edit") }
        val current = buildJsonObject { put("id", "original"); put("name", "Account version") }
        PersonalRecordCodec(context).apply(sql, "program", "original", local, false)
        val journal = db.syncJournalDao()
        journal.configure(SyncConfigurationEntity(accountGeneration = 1, deviceId = "device-a"))
        journal.confirm(SyncShadowEntity("programs", "original", 4, current.toString(), false))
        journal.upsert(SyncMutationEntity("programs", "original", 1, false, 3, "conflict",
            json.encodeToString(PersonalOperation.serializer(), incoming), 1, "CONFLICT"))
    }
    private fun repo(call: suspend (String, JsonObject) -> JsonObject) = RoomSyncRepository(context, "owner", db,
        object : AccountCalls {
            override suspend fun call(uid: String, method: String, data: JsonObject, freshAuthentication: Boolean): JsonObject {
                assertEquals("owner", uid); return call(method, data)
            }
        }, data, media)

    @Test fun copyLostReply() = runBlocking(Dispatchers.IO) {
        val incoming = operation(buildJsonObject { put("id", "original"); put("name", "Incoming"); put("references", "original-history") })
        seed(incoming)
        var saved: PersonalOperation? = null
        var calls = 0
        val repository = repo { method, args ->
            assertEquals("resolveConflict", method)
            assertEquals(4L, args.getValue("expectedRevision").jsonPrimitive.long)
            val candidate = json.decodeFromJsonElement(PersonalOperation.serializer(), args.getValue("operation"))
            if (saved == null) saved = candidate else assertEquals(saved, candidate)
            if (++calls == 1) throw AccountServiceException("UNAVAILABLE")
            buildJsonObject { put("status", "APPLIED"); put("revision", 1) }
        }
        assertTrue(runCatching { repository.resolveConflict("conflict", "KEEP_BOTH") }.isFailure)
        assertEquals(1, db.programDao().getAllHeaders().size)
        // A new repository represents a restarted client after the server committed but the reply was lost.
        repo { method, args ->
            assertEquals("resolveConflict", method)
            assertEquals(saved, json.decodeFromJsonElement(PersonalOperation.serializer(), args.getValue("operation")))
            buildJsonObject { put("status", "APPLIED"); put("revision", 1) }
        }.resolveConflict("conflict", "KEEP_BOTH")
        val copy = requireNotNull(saved)
        assertEquals(2, db.programDao().getAllHeaders().size)
        assertEquals("Account version", db.programDao().getById("original")!!.name)
        assertEquals("Incoming", db.programDao().getById(copy.entityId)!!.name)
        assertEquals(4L, db.syncJournalDao().shadow("programs", "original")!!.serverRevision)
        assertEquals(1L, db.syncJournalDao().shadow("programs", copy.entityId)!!.serverRevision)
        assertEquals(0, db.syncJournalDao().pendingCount())
        assertEquals(JsonPrimitive("original-history"), copy.payload!!["references"])
    }
    @Test fun copyLargeJson() = runBlocking(Dispatchers.IO) {
        val payload = buildJsonObject { put("id", "original"); put("name", "Large"); put("futureFields", "x".repeat(600000)) }
        seed(operation(payload).copy(payload = null, payloadRef = data.stage(payload)))
        var candidate: PersonalOperation? = null
        repo { _, args ->
            candidate = json.decodeFromJsonElement(PersonalOperation.serializer(), args.getValue("operation"))
            assertNull(candidate!!.payload); assertNotNull(candidate!!.payloadRef)
            assertEquals(payload["futureFields"], data.readStaged(candidate!!.payloadRef!!)["futureFields"])
            buildJsonObject { put("status", "APPLIED"); put("revision", 1) }
        }.resolveConflict("conflict", "KEEP_BOTH")
        val copy = db.programDao().getById(candidate!!.entityId)!!
        assertEquals(payload["futureFields"], Json.parseToJsonElement(copy.data).jsonObject["futureFields"])
        assertEquals(4L, db.syncJournalDao().shadow("programs", "original")!!.serverRevision)
    }
    @Test fun singletonAndDelete() {
        val payload = buildJsonObject { put("id", "original") }
        val original = operation(payload)
        assertFalse(ConflictCopies.supported(original.copy(kind = "settings"), payload))
        assertFalse(ConflictCopies.supported(original.copy(deleted = true), payload))
        assertFalse(ConflictCopies.supported(original, JsonObject(emptyMap())))
        assertNotEquals(ConflictCopies.candidate("A", "same", original, payload).entityId,
            ConflictCopies.candidate("B", "same", original, payload).entityId)
    }
    @Test fun privateUnknownFields() = runBlocking(Dispatchers.IO) {
        val payload = Json.parseToJsonElement("""{"id":"observation","metric":"WEIGHT","valueSi":70.0,"unitSi":"kg",
            "timestampEpochMs":1000,"zoneId":"UTC","source":"MANUAL","method":"SCALE","quality":"DECLARED",
            "futurePrecision":{"notes":"${"x".repeat(600000)}"}}""").jsonObject
        val ref = data.stage(payload)
        val codec = PersonalRecordCodec(context)
        codec.apply(db.openHelper.writableDatabase, "body_observation", "observation", payload, false)
        val shadow = buildJsonObject { put("kpknPrivateDataReference", json.encodeToJsonElement(PrivateDataReference.serializer(), ref)) }
        db.syncJournalDao().confirm(SyncShadowEntity("body_observations", "observation", 1, shadow.toString(), false))
        db.openHelper.writableDatabase.execSQL("UPDATE body_observations SET valueSi=71 WHERE id='observation'")
        val outgoing = requireNotNull(codec.snapshot(db.openHelper.readableDatabase, "body_observations", "observation"))
        assertEquals(payload["futurePrecision"], outgoing["futurePrecision"])
        assertEquals(71.0, outgoing.getValue("valueSi").jsonPrimitive.double, 0.0)
    }
    @Test fun preserveByteHash() {
        val bytes = "{\n  \"id\" : \"spaced\", \"future\" : true\n}".toByteArray()
        val ref = PrivateDataReference(PersonalRecordCodec.sha256(bytes), bytes.size.toLong())
        val source = File(context.cacheDir, "download.part").also { it.writeBytes(bytes) }
        PrivateDataFiles.publish(context, source, ref)
        assertArrayEquals(bytes, PrivateDataFiles.file(context, ref.sha256).readBytes())
        assertEquals(JsonPrimitive("spaced"), PrivateDataFiles.read(context, ref)["id"])
        val damaged = File(context.cacheDir, "damaged.part").also { it.writeBytes(bytes.copyOf().also { arr -> arr[0] = 0 }) }
        assertTrue(runCatching { PrivateDataFiles.publish(context, damaged, ref) }.isFailure)
        assertArrayEquals(bytes, PrivateDataFiles.file(context, ref.sha256).readBytes())
    }
}
