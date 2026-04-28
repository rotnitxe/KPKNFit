package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toSessionTemplate
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Single source of truth for [SessionTemplate] instances.
 *
 * Architecture mirrors [ProgramRepository]:
 * - System templates are served from the static [SESSION_TEMPLATES_SYSTEM] catalog
 *   and are never written to the database.
 * - User templates are kept in a [MutableStateFlow] that is hydrated from Room on
 *   first access; writes are applied immediately to the flow and persisted
 *   asynchronously on a background coroutine.
 *
 * Obtain the singleton via [getInstance].
 */
class SessionTemplateRepository private constructor(context: Context) {

    private val db = KpknDatabase.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _userTemplates = MutableStateFlow<List<SessionTemplate>>(emptyList())

    /** Live list of user-created templates (excludes archived ones in [allTemplates]). */
    val userTemplates: StateFlow<List<SessionTemplate>> = _userTemplates.asStateFlow()

    /**
     * Combined (system + user) template list, sorted by [SessionTemplate.sortOrder]
     * then creation date.  Archived user templates are excluded.
     */
    val allTemplates: StateFlow<List<SessionTemplate>> = _userTemplates
        .map { user ->
            SESSION_TEMPLATES_SYSTEM + user
                .filterNot { it.isArchived }
                .sortedByDescending { it.createdAt }
        }
        .stateIn(scope, SharingStarted.Eagerly, SESSION_TEMPLATES_SYSTEM)

    init {
        scope.launch {
            val persisted = db.sessionTemplateDao().getAll().map { it.toSessionTemplate() }
            _userTemplates.value = persisted
        }
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    fun getById(id: String): SessionTemplate? =
        SESSION_TEMPLATES_SYSTEM.firstOrNull { it.id == id }
            ?: _userTemplates.value.firstOrNull { it.id == id }

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
        _userTemplates.update { current ->
            val idx = current.indexOfFirst { it.id == template.id }
            if (idx >= 0) current.toMutableList().also { it[idx] = template }
            else listOf(template) + current
        }
        scope.launch { db.sessionTemplateDao().upsert(template.toEntity()) }
    }

    /**
     * Permanently removes a user template.  No-op for system templates.
     */
    fun deleteUserTemplate(id: String) {
        if (SESSION_TEMPLATES_SYSTEM.any { it.id == id }) return
        _userTemplates.update { it.filterNot { t -> t.id == id } }
        scope.launch { db.sessionTemplateDao().delete(id) }
    }

    /**
     * Soft-deletes a user template by setting [SessionTemplate.isArchived] = true.
     * Archived templates are hidden from [allTemplates] but remain in the database.
     */
    fun archiveUserTemplate(id: String) {
        val template = _userTemplates.value.firstOrNull { it.id == id } ?: return
        saveUserTemplate(template.copy(isArchived = true, updatedAt = Instant.now().toString()))
    }

    /**
     * Restores an archived user template so it shows up in [allTemplates] again.
     */
    fun restoreUserTemplate(id: String) {
        val template = _userTemplates.value.firstOrNull { it.id == id } ?: return
        saveUserTemplate(template.copy(isArchived = false, updatedAt = Instant.now().toString()))
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: SessionTemplateRepository? = null

        fun getInstance(context: Context): SessionTemplateRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionTemplateRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
    }
}
