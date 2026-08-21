package com.example.kpkn.data.repository

import android.content.Context
import android.util.Log
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSessionTemplateOrNull
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.domain.templates.SessionTemplateQualityRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Single source of truth for [SessionTemplate] instances.
 *
 * Architecture mirrors [ProgramRepository]:
 * - System templates are served from the static [SESSION_TEMPLATES_SYSTEM] catalog
 *   and are never written to the database.
 * - User templates are kept in a [MutableStateFlow] that is hydrated from Room on
 *   first access; durable commands publish to the flow only after Room succeeds.
 *
 * Obtain the singleton via [getInstance].
 */
class SessionTemplateRepository private constructor(context: Context) {

    private val db = KpknDatabase.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _userTemplates = MutableStateFlow<List<SessionTemplate>>(emptyList())
    private val _isReady = MutableStateFlow(false)
    private val templateWriteMutex = Mutex()

    /** Live list of user-created templates (excludes archived ones in [allTemplates]). */
    val userTemplates: StateFlow<List<SessionTemplate>> = _userTemplates.asStateFlow()
    /** UI must not treat a pre-hydration miss as a deleted USER template. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * Combined (system + user) template list, sorted by [SessionTemplate.sortOrder]
     * then creation date.  Archived user templates are excluded.
     */
    val allTemplates: StateFlow<List<SessionTemplate>> = _userTemplates
        .map { user -> publishedSystemTemplates() + visibleUserTemplates(user) }
        // Do not decode/materialize the full static catalog on Main before
        // Room hydration; the UI waits for [isReady] before showing content.
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Candidates accepted by the weekly generator; USER entries are explicit opt-in. Fail-closed: P0==0 && P1==0. */
    val generationTemplates: StateFlow<List<SessionTemplate>> = _userTemplates
        .map { user ->
            val index = catalogExerciseIndex()
            publishedSystemTemplates() + visibleUserTemplates(user).filter {
                val report = SessionTemplateQualityRules.audit(it, index)
                it.autoGenerationEligible && report.p0.isEmpty() && report.p1.isEmpty()
            }
        }
        // Keep the initial value empty: evaluating the full static catalog here
        // would run on the caller's thread while the repository is constructed.
        // The IO-backed collector materializes it after the first user snapshot.
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            val persisted = db.sessionTemplateDao().getAll().mapNotNull {
                it.toSessionTemplateOrNull() ?: run {
                    Log.w("SessionTemplateRepo", "Descartada plantilla corrupta id=${it.id}")
                    null
                }
            }
            // A save may happen while Room is hydrating. In-memory writes win so
            // a late hydration cannot erase a just-created user template.
            _userTemplates.update { current ->
                (current + persisted.filterNot { disk -> current.any { it.id == disk.id } })
            }
            _isReady.value = true
        }
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    fun getById(id: String): SessionTemplate? =
        SESSION_TEMPLATES_SYSTEM.firstOrNull { it.id == id }
            ?: _userTemplates.value.firstOrNull { it.id == id }

    suspend fun getByIdAfterReady(id: String): SessionTemplate? {
        isReady.filter { it }.first()
        return getById(id) ?: db.sessionTemplateDao().getById(id)?.toSessionTemplateOrNull()
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    /**
     * Inserts or updates a user template (identified by [SessionTemplate.id]).
     * Only [SessionTemplateSourceType.USER] templates can be saved; system
     * templates are read-only.
     */
    fun saveUserTemplate(template: SessionTemplate) {
        require(template.sourceType == SessionTemplateSourceType.USER) {
            "Only USER templates can be saved via SessionTemplateRepository."
        }
        scope.launch { saveUserTemplateNow(template) }
    }

    /** Durable write used by editor commands before confirming success to the user. */
    suspend fun saveUserTemplateNow(template: SessionTemplate): Result<Unit> {
        require(template.sourceType == SessionTemplateSourceType.USER) {
            "Only USER templates can be saved via SessionTemplateRepository."
        }
        return runCatching {
            templateWriteMutex.withLock {
                db.sessionTemplateDao().upsert(template.toEntity())
                // Publish only after durable success; a failed DAO write must
                // not leave a phantom template in the editor flow.
                _userTemplates.update { current -> mergeUserTemplate(current, template) }
            }
        }.onFailure { Log.e("SessionTemplateRepo", "upsert fallo id=${template.id}", it) }
    }

    /** Metadata/editor update; uses the same durable-before-flow ordering as create. */
    suspend fun updateUserTemplateNow(template: SessionTemplate): Result<Unit> =
        saveUserTemplateNow(template.copy(updatedAt = Instant.now().toString()))

    /**
     * Permanently removes a user template.  No-op for system templates.
     */
    fun deleteUserTemplate(id: String) {
        if (SESSION_TEMPLATES_SYSTEM.any { it.id == id }) return
        scope.launch { deleteUserTemplateNow(id) }
    }

    suspend fun deleteUserTemplateNow(id: String): Result<Unit> = runCatching {
        if (SESSION_TEMPLATES_SYSTEM.any { it.id == id }) return@runCatching
        templateWriteMutex.withLock {
            db.sessionTemplateDao().delete(id)
            _userTemplates.update { it.filterNot { template -> template.id == id } }
        }
    }.onFailure { Log.e("SessionTemplateRepo", "delete fallo id=$id", it) }

    /**
     * Soft-deletes a user template by setting [SessionTemplate.isArchived] = true.
     * Archived templates are hidden from [allTemplates] but remain in the database.
     */
    fun archiveUserTemplate(id: String) {
        scope.launch { archiveUserTemplateNow(id) }
    }

    suspend fun archiveUserTemplateNow(id: String): Result<Unit> {
        val template = _userTemplates.value.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Plantilla de usuario no encontrada: $id"))
        return saveUserTemplateNow(template.copy(isArchived = true, updatedAt = Instant.now().toString()))
    }

    /**
     * Restores an archived user template so it shows up in [allTemplates] again.
     */
    fun restoreUserTemplate(id: String) {
        scope.launch { restoreUserTemplateNow(id) }
    }

    suspend fun restoreUserTemplateNow(id: String): Result<Unit> {
        val template = _userTemplates.value.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Plantilla de usuario no encontrada: $id"))
        return saveUserTemplateNow(template.copy(isArchived = false, updatedAt = Instant.now().toString()))
    }

    private fun publishedSystemTemplates(): List<SessionTemplate> =
        SESSION_TEMPLATES_SYSTEM.filterNot {
            it.isArchived || it.publicationStatus == SessionTemplatePublicationStatus.HIDDEN_UNVERIFIED
        }

    private fun visibleUserTemplates(user: List<SessionTemplate>): List<SessionTemplate> =
        user.filterNot {
            it.isArchived || it.publicationStatus == SessionTemplatePublicationStatus.HIDDEN_UNVERIFIED
        }
            .sortedWith(compareBy<SessionTemplate> { it.sortOrder }.thenByDescending { it.createdAt.orEmpty() }.thenBy { it.id })

    private fun mergeUserTemplate(current: List<SessionTemplate>, template: SessionTemplate): List<SessionTemplate> {
        val index = current.indexOfFirst { it.id == template.id }
        return if (index < 0) listOf(template) + current
        else current.toMutableList().also { it[index] = template }
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: SessionTemplateRepository? = null

        fun getInstance(context: Context): SessionTemplateRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionTemplateRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }

        /**
         * Test-only lifecycle hook. ProgramRepository's Robolectric fixture
         * intentionally closes the shared Room singleton between tests; clear
         * this repository too so its StateFlow never points at a closed DAO.
         */
        internal fun resetForTests() {
            synchronized(this) {
                INSTANCE?.scope?.cancel()
                INSTANCE = null
            }
        }
    }
}
