package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPersistedRuleDefaults
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.domain.workout.normalizeEditorScheduledTechniques
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorDraftPersistenceTest {
    @Test
    fun delayedAutosaveIsDiscardedAfterNewerRevisionForSameSession() = runTest {
        val storage = MemoryDraftStorage()
        val writer = SerialDraftWriter<String>(backgroundScope, storage) { it }
        val key = "program=p|week=w|macro=0|meso=0|editor=s"
        val oldRevision = writer.invalidate(key)
        val delayed = writer.enqueueWrite(key, oldRevision, "old")
        val currentRevision = writer.invalidate(key)
        val current = writer.enqueueWrite(key, currentRevision, "current")

        advanceUntilIdle()

        assertEquals(DraftWriteStatus.STALE, delayed.await().status)
        assertEquals(DraftWriteStatus.WRITTEN, current.await().status)
        assertEquals("current", storage.values[key])
    }

    @Test
    fun discardInvalidatesQueuedAutosaveAndRemovesExistingDraft() = runTest {
        val storage = MemoryDraftStorage().apply { values["session-a"] = "old" }
        val writer = SerialDraftWriter<String>(backgroundScope, storage) { it }
        val staleRevision = writer.invalidate("session-a")
        val delayed = writer.enqueueWrite("session-a", staleRevision, "stale")
        val clear = writer.enqueueClear("session-a")

        advanceUntilIdle()

        assertEquals(DraftWriteStatus.STALE, delayed.await().status)
        assertEquals(DraftWriteStatus.WRITTEN, clear.await().status)
        assertFalse(storage.values.containsKey("session-a"))
    }

    @Test
    fun failedWriteCanBeRetriedWithoutLosingTheSnapshot() = runTest {
        val storage = MemoryDraftStorage().apply { failedWritesRemaining = 1 }
        val writer = SerialDraftWriter<String>(backgroundScope, storage) { it }

        val failed = writer.enqueueLatestWrite("session", "snapshot")
        advanceUntilIdle()
        assertEquals(DraftWriteStatus.FAILED, failed.await().status)
        assertEquals(DraftWriteStatus.FAILED, writer.awaitLatest("session")?.status)
        assertFalse(storage.values.containsKey("session"))

        val retried = writer.enqueueLatestWrite("session", "snapshot")
        advanceUntilIdle()
        assertEquals(DraftWriteStatus.WRITTEN, retried.await().status)
        assertEquals(DraftWriteStatus.WRITTEN, writer.awaitLatest("session")?.status)
        assertEquals("snapshot", storage.values["session"])
    }

    @Test
    fun cancelledWriterOwnerCompletesAwaitWithFailureInsteadOfHanging() = runTest {
        val owner = CoroutineScope(SupervisorJob())
        val writer = SerialDraftWriter<String>(owner, MemoryDraftStorage()) { it }
        owner.cancel()

        val outcome = writer.enqueueLatestWrite("session", "snapshot").await()

        assertEquals(DraftWriteStatus.FAILED, outcome.status)
        assertTrue(outcome.failure is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun switchingSessionInvalidatesOldTimerAndKeepsNewSessionKeyIndependent() = runTest {
        val storage = MemoryDraftStorage()
        val writer = SerialDraftWriter<String>(backgroundScope, storage) { it }
        val oldKey = "program=p|week=w1|macro=0|meso=0|editor=old"
        val newKey = "program=p|week=w2|macro=0|meso=0|editor=new"
        val oldRevision = writer.invalidate(oldKey)
        val oldAutosave = writer.enqueueWrite(oldKey, oldRevision, "old-session")
        writer.invalidate(oldKey) // switchToSession invalidates work captured for the prior key
        val newAutosave = writer.enqueueLatestWrite(newKey, "new-session")

        advanceUntilIdle()

        assertEquals(DraftWriteStatus.STALE, oldAutosave.await().status)
        assertEquals(DraftWriteStatus.WRITTEN, newAutosave.await().status)
        assertFalse(storage.values.containsKey(oldKey))
        assertEquals("new-session", storage.values[newKey])
    }

    @Test
    fun generatedTimestampDoesNotMakeSessionDirtyButContentEditDoes() {
        val saved = Session(id = "session", name = "Día A", lastModifiedAtMs = 100L)
        val timestampOnly = saved.copy(lastModifiedAtMs = 200L)

        assertTrue(sameSessionEditorContent(saved, timestampOnly))
        assertFalse(
            SessionEditorUiState(
                session = timestampOnly,
                originalSession = saved,
            ).hasMeaningfulDraftChanges(),
        )
        assertTrue(
            SessionEditorUiState(
                session = timestampOnly.copy(name = "Día A cambiado"),
                originalSession = saved,
            ).hasMeaningfulDraftChanges(),
        )
    }

    @Test
    fun editorOnlyPreferencesUseSavedBaselineAndResetIsClean() {
        val saved = SessionEditorRulePreferences(
            partRuleDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 8, applyToNewItems = true)),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 9.0, rigidLimits = true),
        )
        val baseline = SessionEditorUiState(
            session = Session(id = "session", name = "Día A", lastModifiedAtMs = 100L),
            originalSession = Session(id = "session", name = "Día A", lastModifiedAtMs = 200L),
            partRuleDefaults = saved.partRuleDefaults,
            savedPartRuleDefaults = saved.partRuleDefaults,
            ruleLimits = saved.ruleLimits,
            savedRuleLimits = saved.ruleLimits,
        )
        assertFalse("normalización/timestamp no ensucia una sesión abierta", baseline.hasMeaningfulDraftChanges())
        assertFalse("un no-op de preferencias mantiene limpio el estado", baseline.copy(
            partRuleDefaults = saved.partRuleDefaults.toMap(),
            ruleLimits = saved.ruleLimits.copy(),
        ).hasMeaningfulDraftChanges())

        val changed = baseline.copy(
            partRuleDefaults = saved.partRuleDefaults + ("part-a" to saved.partRuleDefaults.getValue("part-a").copy(reps = 10)),
            ruleLimits = saved.ruleLimits.copy(maxRPE = 8.5),
        )
        assertFalse("solo preferencias no alteran la sesión de Room", changed.hasMeaningfulSessionChanges())
        assertTrue("ambas preferencias usan el baseline comprometido", changed.hasMeaningfulDraftChanges())
        assertFalse("descartar restaura el baseline limpio", changed.copy(
            partRuleDefaults = changed.savedPartRuleDefaults,
            ruleLimits = changed.savedRuleLimits,
        ).hasMeaningfulDraftChanges())
    }

    @Test
    fun legacySessionNormalizationDoesNotCreateFalseContentDirtyState() {
        val legacy = Session(
            id = "session-normalized",
            name = "Día A",
            exercises = listOf(
                Exercise(
                    id = "exercise",
                    name = "Press banca",
                    sets = listOf(ExerciseSet(id = "set", isDropSet = true)),
                ),
            ),
        )
        val editorNormalized = legacy.normalizeEditorScheduledTechniques()
        assertNotEquals("la frontera del editor agrega el marcador compatible", legacy, editorNormalized)
        assertTrue(sameSessionEditorContent(legacy, editorNormalized))
        // Real editor loading infers global defaults from the saved session.
        // The generic 3 x 10 defaults would be an actual pending global edit.
        val clean = SessionEditorUiState(
            session = editorNormalized,
            originalSession = legacy,
            ruleDefaults = legacy.inferredEditorRuleDefaults(),
        )
        assertFalse(clean.hasMeaningfulDraftChanges())
        assertTrue("a real global-default change must remain dirty", clean.copy(
            ruleDefaults = clean.ruleDefaults.copy(reps = clean.ruleDefaults.reps + 1),
        ).hasMeaningfulDraftChanges())
    }

    // ─── Global rule defaults: eight Room-backed core fields vs preference-record extras ───

    /** Every one of the 8 core and 10 extra fields differs from its default. */
    private val allNonDefaultDefaults = SessionEditorRuleDefaults(
        scope = RuleScope.COMPOUND_ISOLATION,
        setCount = 5,
        reps = 6,
        rpe = 7.5,
        normalRestSeconds = 150,
        betweenSidesRestSeconds = 20,
        supersetBetweenRestSeconds = 45,
        supersetRoundRestSeconds = 90,
        applyToNewItems = true,
        intensityType = DefaultIntensityType.RIR,
        compoundRestSeconds = 180,
        compoundReps = 5,
        compoundRpe = 8.5,
        compoundIntensityType = DefaultIntensityType.RPE,
        isolationRestSeconds = 60,
        isolationReps = 12,
        isolationRpe = 2.0,
        isolationIntensityType = DefaultIntensityType.RIR,
    )
    private val extrasA = allNonDefaultDefaults.extras()
    private val extrasB = extrasA.copy(scope = RuleScope.PER_GROUP, intensityType = DefaultIntensityType.FALLO, compoundReps = 3)
    private val core5 = SessionPersistedRuleDefaults(setCount = 3, reps = 5)

    private fun roomSession(core: SessionPersistedRuleDefaults? = null): Session =
        Session(id = "session", name = "Día A", lastModifiedAtMs = 100L, persistedRuleDefaults = core)

    private fun coreDefaults(core: SessionPersistedRuleDefaults): SessionEditorRuleDefaults =
        SessionEditorRuleDefaults.fromPersisted(core)

    private fun cleanState(): SessionEditorUiState {
        val saved = roomSession()
        return SessionEditorUiState(session = saved, originalSession = saved)
    }

    private fun draftOf(
        ruleDefaults: SessionEditorRuleDefaults,
        session: Session = roomSession(),
        baseline: SessionEditorRuleDefaults? = null,
        pinnedPreferences: Boolean = false,
    ) = PersistedSessionEditorDraft(
        programId = "program",
        sessionId = "session",
        weekId = "week",
        macroIndex = 0,
        mesoIndex = 0,
        session = session,
        ruleDefaults = ruleDefaults,
        committedPartRuleDefaults = if (pinnedPreferences || baseline != null) emptyMap() else null,
        committedRuleLimits = if (pinnedPreferences || baseline != null) SessionEditorRuleLimits() else null,
        committedRuleBaseline = baseline?.let { SessionEditorCommittedRuleBaseline(ruleDefaults = it) },
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun everyRuleDefaultsFieldIsEitherCoreOrExtra() {
        fun names(descriptor: SerialDescriptor): List<String> =
            (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        val all = names(SessionEditorRuleDefaults.serializer().descriptor)
        val core = names(SessionPersistedRuleDefaults.serializer().descriptor)
        val extras = names(SessionEditorGlobalRuleExtras.serializer().descriptor) - "version"

        assertTrue("a field cannot be both Room-backed and an extra: ${core.intersect(extras.toSet())}",
            core.intersect(extras.toSet()).isEmpty())
        assertEquals("every SessionEditorRuleDefaults field must be classified as core or extra",
            all.toSet(), (core + extras).toSet())
        assertEquals(all.size, core.size + extras.size)
    }

    @Test
    fun extrasRoundTripAndOnlyTheEightCoreFieldsReachRoom() {
        val full = allNonDefaultDefaults
        val coreOnly = SessionEditorRuleDefaults.fromPersisted(full.toPersisted())

        assertEquals(full, coreOnly.withExtras(full.extras()))
        assertEquals("Room's block carries no extra", SessionEditorGlobalRuleExtras(), coreOnly.extras())
        assertEquals(full.toPersisted(), full.withExtras(SessionEditorGlobalRuleExtras()).toPersisted())
        assertTrue(full.sameCoreAs(full.withExtras(SessionEditorGlobalRuleExtras())))
        assertFalse(full.sameCoreAs(full.copy(reps = full.reps + 1)))
        assertEquals(SessionEditorGlobalRuleExtras.CURRENT_VERSION, full.extras().version)
    }

    @Test
    fun preferenceRecordIsVersionedAndLegacyOrFutureRecordsStillDecode() {
        val json = sessionEditorStoreJson
        val v1 = json.decodeFromString<SessionEditorRulePreferences>(
            """{"partRuleDefaults":{},"ruleLimits":{"maxRPE":7.0}}""",
        )
        assertNull("a v1 record carries no extras", v1.globalRuleExtras)
        assertEquals(7.0, v1.ruleLimits.maxRPE ?: 0.0, 0.0)

        val v2 = SessionEditorRulePreferences(globalRuleExtras = extrasA)
        val encoded = json.encodeToString(v2)
        assertTrue(encoded, encoded.contains("\"globalRuleExtras\""))
        assertTrue(encoded, encoded.contains("\"version\":1"))
        assertEquals(v2, json.decodeFromString<SessionEditorRulePreferences>(encoded))

        val v2WithDefaults = json.encodeToString(SessionEditorRulePreferences(globalRuleExtras = SessionEditorGlobalRuleExtras()))
        assertTrue("v2 with default extras stays distinguishable from v1: $v2WithDefaults",
            v2WithDefaults.contains("\"globalRuleExtras\":{"))

        val future = json.decodeFromString<SessionEditorRulePreferences>(
            """{"partRuleDefaults":{},"ruleLimits":{},"unknownTopLevel":1,"globalRuleExtras":""" +
                """{"version":7,"futureField":"x","scope":"NOT_A_SCOPE","intensityType":"RIR",""" +
                """"compoundIntensityType":"NOT_A_TYPE","compoundReps":4}}""",
        )
        val extras = requireNotNull(future.globalRuleExtras)
        assertEquals(7, extras.version)
        assertEquals("an unknown enum degrades to the default instead of dropping the record", RuleScope.ALL_SESSION, extras.scope)
        assertEquals(DefaultIntensityType.RIR, extras.intensityType)
        assertNull(extras.compoundIntensityType)
        assertEquals(4, extras.compoundReps)
        assertEquals("the payload version never makes equal values compare different",
            extras.copy(version = SessionEditorGlobalRuleExtras.CURRENT_VERSION), extras.atCurrentVersion())
    }

    @Test
    fun draftWithVersionedBaselineRoundTripsAndOlderDraftsDecodeWithoutOne() {
        val json = sessionEditorStoreJson
        val draft = draftOf(allNonDefaultDefaults, baseline = SessionEditorRuleDefaults())

        val decoded = json.decodeFromString<PersistedSessionEditorDraft>(json.encodeToString(draft))
        assertEquals(draft.committedRuleBaseline, decoded.committedRuleBaseline)
        assertEquals(1, decoded.committedRuleBaseline?.version)
        assertEquals(allNonDefaultDefaults, decoded.ruleDefaults)

        val withoutKey = JsonObject(
            json.parseToJsonElement(json.encodeToString(draft.copy(committedRuleBaseline = null)))
                .jsonObject
                .filterKeys { it != "committedRuleBaseline" },
        )
        val older = json.decodeFromString<PersistedSessionEditorDraft>(withoutKey.toString())
        assertNull("a draft written before the baseline existed decodes with no baseline", older.committedRuleBaseline)
        assertEquals(allNonDefaultDefaults, older.ruleDefaults)
    }

    @Test
    fun extrasOnlyEditsAreNotContentChangesButAreDraftChangesUntilConfirmed() {
        val clean = cleanState()
        assertFalse(clean.hasMeaningfulDraftChanges())
        val base = clean.ruleDefaults
        val edits = mapOf(
            "scope" to base.copy(scope = RuleScope.COMPOUND_ISOLATION),
            "intensityType" to base.copy(intensityType = DefaultIntensityType.RIR),
            "compoundRestSeconds" to base.copy(compoundRestSeconds = 180),
            "compoundReps" to base.copy(compoundReps = 5),
            "compoundRpe" to base.copy(compoundRpe = 8.5),
            "compoundIntensityType" to base.copy(compoundIntensityType = DefaultIntensityType.RIR),
            "isolationRestSeconds" to base.copy(isolationRestSeconds = 60),
            "isolationReps" to base.copy(isolationReps = 12),
            "isolationRpe" to base.copy(isolationRpe = 2.0),
            "isolationIntensityType" to base.copy(isolationIntensityType = DefaultIntensityType.FALLO),
            "everyExtraAtOnce" to base.withExtras(extrasA),
        )
        edits.forEach { (label, edited) ->
            val dirty = clean.copy(ruleDefaults = edited)
            assertFalse("$label is not Room content: no override and no new lastModifiedAtMs", dirty.hasMeaningfulSessionChanges())
            assertTrue("$label is an unsaved preference", dirty.hasMeaningfulDraftChanges())
            assertFalse("$label is clean once the extras are confirmed",
                dirty.copy(savedRuleExtras = edited.extras()).hasMeaningfulDraftChanges())
            assertTrue("reverting $label to the confirmed value is clean again",
                !dirty.copy(ruleDefaults = base).hasMeaningfulDraftChanges())
        }
    }

    @Test
    fun everyCoreFieldEditRemainsAContentChange() {
        val clean = cleanState()
        val base = clean.ruleDefaults
        val edits = mapOf(
            "setCount" to base.copy(setCount = base.setCount + 1),
            "reps" to base.copy(reps = base.reps + 1),
            "rpe" to base.copy(rpe = base.rpe - 1.0),
            "normalRestSeconds" to base.copy(normalRestSeconds = base.normalRestSeconds + 15),
            "betweenSidesRestSeconds" to base.copy(betweenSidesRestSeconds = base.betweenSidesRestSeconds + 5),
            "supersetBetweenRestSeconds" to base.copy(supersetBetweenRestSeconds = base.supersetBetweenRestSeconds + 5),
            "supersetRoundRestSeconds" to base.copy(supersetRoundRestSeconds = base.supersetRoundRestSeconds + 5),
            "applyToNewItems" to base.copy(applyToNewItems = !base.applyToNewItems),
        )
        edits.forEach { (label, edited) ->
            val dirty = clean.copy(ruleDefaults = edited)
            assertTrue("$label is stored in Room, so it is a content change", dirty.hasMeaningfulSessionChanges())
            assertTrue(dirty.hasMeaningfulDraftChanges())
            assertTrue("confirming extras never hides a core change ($label)",
                dirty.copy(savedRuleExtras = edited.extras()).hasMeaningfulSessionChanges())
        }
    }

    @Test
    fun committedRuleDefaultsUseTheOriginalSessionCoreAndTheConfirmedExtras() {
        val original = roomSession(SessionPersistedRuleDefaults(setCount = 4, reps = 5))
        val state = SessionEditorUiState(
            session = original.copy(name = "editada"),
            originalSession = original,
            ruleDefaults = allNonDefaultDefaults,
            savedRuleExtras = extrasB,
        )
        val committed = state.committedRuleDefaults()
        assertEquals(original.persistedRuleDefaults, committed.toPersisted())
        assertEquals(extrasB, committed.extras())

        val inferredSource = Session(
            id = "inferred",
            name = "Sin bloque persistido",
            exercises = listOf(
                Exercise(
                    id = "exercise",
                    name = "Press",
                    restTime = 120,
                    sets = listOf(ExerciseSet("set", targetReps = 8, targetRPE = 7.0)),
                ),
            ),
        )
        val inferredState = SessionEditorUiState(session = inferredSource, originalSession = inferredSource)
        assertEquals(inferredSource.inferredEditorRuleDefaults(), inferredState.committedRuleDefaults())
        assertEquals(8, inferredState.committedRuleDefaults().reps)
        assertFalse(inferredState.copy(ruleDefaults = inferredState.committedRuleDefaults()).hasMeaningfulDraftChanges())
    }

    @Test
    fun resolve_noDraftNoRecord_usesRoomCoreAndDefaultExtras() {
        val room = roomSession(core5)

        val resolved = resolveGlobalRuleDefaults(room, stored = null, draft = null)

        assertEquals(coreDefaults(core5), resolved.current)
        assertEquals(SessionEditorGlobalRuleExtras(), resolved.savedExtras)
    }

    @Test
    fun resolve_storedExtrasOverlayTheCurrentRoomCoreAndNeverTheStoredOne() {
        val room = roomSession(core5.copy(reps = 8))
        val stored = SessionEditorRulePreferences(globalRuleExtras = extrasA)

        val resolved = resolveGlobalRuleDefaults(room, stored, draft = null)

        assertEquals("Room is the authority of the core", room.persistedRuleDefaults, resolved.current.toPersisted())
        assertEquals(8, resolved.current.reps)
        assertEquals(extrasA, resolved.current.extras())
        assertEquals(extrasA, resolved.savedExtras)
    }

    @Test
    fun resolve_v1RecordConfirmsDefaultExtras() {
        val room = roomSession(core5)
        val v1 = SessionEditorRulePreferences(ruleLimits = SessionEditorRuleLimits(maxRPE = 9.0))

        val resolved = resolveGlobalRuleDefaults(room, v1, draft = null)

        assertEquals(SessionEditorGlobalRuleExtras(), resolved.savedExtras)
        assertEquals(coreDefaults(core5), resolved.current)
    }

    @Test
    fun resolve_legacyDraftWithoutRecordMigratesItsOwnExtrasAsConfirmed() {
        val room = roomSession(core5)
        val draft = draftOf(coreDefaults(core5).withExtras(extrasA))

        val resolved = resolveGlobalRuleDefaults(room, stored = null, draft = draft)

        assertEquals(extrasA, resolved.savedExtras)
        assertEquals(draft.ruleDefaults, resolved.current)
    }

    @Test
    fun resolve_legacyDraftKeepsTheWholeDraftEvenWhenRoomMoved() {
        val room = roomSession(core5.copy(reps = 8))
        val draft = draftOf(coreDefaults(core5).copy(reps = 7).withExtras(extrasA))

        val resolved = resolveGlobalRuleDefaults(room, stored = null, draft = draft)

        assertEquals("without a baseline the draft wins as a whole", draft.ruleDefaults, resolved.current)
    }

    @Test
    fun resolve_legacyDraftWithRecordKeepsTheRecordExtrasAsConfirmed() {
        val room = roomSession(core5)
        val draft = draftOf(coreDefaults(core5).withExtras(extrasA))
        val stored = SessionEditorRulePreferences(globalRuleExtras = extrasB)

        val resolved = resolveGlobalRuleDefaults(room, stored, draft)

        assertEquals(extrasB, resolved.savedExtras)
        assertEquals("the draft's extras are the pending ones", extrasA, resolved.current.extras())
    }

    @Test
    fun resolve_d1DraftExtrasStayPendingAgainstAV1RecordOrNoRecord() {
        val room = roomSession(core5)
        val draft = draftOf(coreDefaults(core5).withExtras(extrasA), pinnedPreferences = true)

        listOf(null, SessionEditorRulePreferences()).forEach { stored ->
            val resolved = resolveGlobalRuleDefaults(room, stored, draft)
            assertEquals("D1 extras never reached a store: $stored", SessionEditorGlobalRuleExtras(), resolved.savedExtras)
            assertEquals(draft.ruleDefaults, resolved.current)
        }
    }

    @Test
    fun resolve_d2DraftFollowsRoomForUntouchedCoreFields() {
        val baseline = coreDefaults(core5)
        val draft = draftOf(baseline.withExtras(extrasA), baseline = baseline)
        // Room moved externally (reps 5 -> 8, setCount 3 -> 4) after the draft was written.
        val room = roomSession(core5.copy(reps = 8, setCount = 4))

        val resolved = resolveGlobalRuleDefaults(room, stored = null, draft = draft)

        assertEquals(8, resolved.current.reps)
        assertEquals(4, resolved.current.setCount)
        assertEquals(room.persistedRuleDefaults, resolved.current.toPersisted())
        assertEquals("the pending extras survive", extrasA, resolved.current.extras())
        assertEquals("no record: the baseline's extras are the confirmed ones", SessionEditorGlobalRuleExtras(), resolved.savedExtras)
    }

    @Test
    fun resolve_d2DraftKeepsTheUserEditedCoreFieldOverRoomButStillFollowsTheRest() {
        val baseline = coreDefaults(core5)
        val draft = draftOf(baseline.copy(reps = 7), baseline = baseline)
        val room = roomSession(core5.copy(reps = 8, setCount = 4))

        val resolved = resolveGlobalRuleDefaults(room, stored = null, draft = draft)

        assertEquals("the user touched reps: the draft wins", 7, resolved.current.reps)
        assertEquals("the user did not touch setCount: it follows Room", 4, resolved.current.setCount)
    }

    @Test
    fun resolve_d2StoredExtrasBeatTheBaselineExtras() {
        val baseline = coreDefaults(core5)
        val draft = draftOf(baseline.withExtras(extrasA), baseline = baseline)

        val resolved = resolveGlobalRuleDefaults(roomSession(core5), SessionEditorRulePreferences(globalRuleExtras = extrasB), draft)

        assertEquals(extrasB, resolved.savedExtras)
        assertEquals(extrasA, resolved.current.extras())
    }

    @Test
    fun resolve_d2WithInferredRoomCoreWhenNoBlockIsPersisted() {
        val room = Session(
            id = "session",
            name = "Sin bloque",
            exercises = listOf(
                Exercise(
                    id = "exercise",
                    name = "Press",
                    restTime = 120,
                    sets = listOf(ExerciseSet("set", targetReps = 8, targetRPE = 7.0)),
                ),
            ),
        )
        val inferred = room.inferredEditorRuleDefaults()
        val draft = draftOf(inferred.withExtras(extrasA), session = room, baseline = inferred)

        val resolved = resolveGlobalRuleDefaults(room, stored = null, draft = draft)

        assertEquals(inferred.withExtras(extrasA), resolved.current)
        assertEquals(SessionEditorGlobalRuleExtras(), resolved.savedExtras)
    }

    @Test
    fun threeWayMergeFollowsRoomForEachUntouchedCoreFieldAndKeepsDraftForEachTouchedOne() {
        val base = SessionEditorRuleDefaults()
        val shifted = allNonDefaultDefaults

        assertEquals("every untouched core field follows Room",
            shifted.toPersisted(), mergeCoreThreeWay(base = base, draft = base, room = shifted).toPersisted())
        assertEquals("every touched core field keeps the draft",
            shifted.toPersisted(), mergeCoreThreeWay(base = base, draft = shifted, room = base).toPersisted())

        val merged = mergeCoreThreeWay(
            base = base,
            draft = base.copy(normalRestSeconds = 30, scope = RuleScope.PER_GROUP),
            room = base.copy(reps = 9),
        )
        assertEquals(9, merged.reps)
        assertEquals(30, merged.normalRestSeconds)
        assertEquals("the extras are always the draft's", RuleScope.PER_GROUP, merged.scope)
    }

    @Test
    fun discardOnlyRewritesTheRecordWhenItDiffersFromTheBaseline() {
        val baseline = SessionEditorRulePreferences(
            partRuleDefaults = mapOf("part-a" to SessionEditorRuleDefaults(reps = 8)),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 9.0),
            globalRuleExtras = extrasA,
        )
        val empty = SessionEditorRulePreferences(globalRuleExtras = SessionEditorGlobalRuleExtras())

        assertFalse(discardMustWriteRulePreferences(RulePreferencesRead.Present(baseline), baseline))
        assertFalse("a v1 record equals a baseline with default extras",
            discardMustWriteRulePreferences(RulePreferencesRead.Present(SessionEditorRulePreferences()), empty))
        assertTrue(discardMustWriteRulePreferences(RulePreferencesRead.Present(baseline.copy(globalRuleExtras = extrasB)), baseline))
        assertTrue(discardMustWriteRulePreferences(RulePreferencesRead.Present(empty), baseline))

        assertFalse("nothing to preserve and no record", discardMustWriteRulePreferences(RulePreferencesRead.Absent, empty))
        assertTrue("a baseline that only lives in the draft must reach the store", discardMustWriteRulePreferences(RulePreferencesRead.Absent, baseline))
        assertFalse("an unreadable record is not replaced by an empty baseline", discardMustWriteRulePreferences(RulePreferencesRead.Unreadable, empty))
        assertTrue("an unreadable record is replaced only to preserve real data", discardMustWriteRulePreferences(RulePreferencesRead.Unreadable, baseline))
    }

    private class MemoryDraftStorage : DraftKeyValueStorage {
        val values = mutableMapOf<String, String>()
        var failedWritesRemaining = 0

        override suspend fun write(key: String, value: String): Boolean {
            if (failedWritesRemaining > 0) {
                failedWritesRemaining--
                return false
            }
            values[key] = value
            return true
        }

        override suspend fun remove(key: String): Boolean {
            values.remove(key)
            return true
        }
    }
}
