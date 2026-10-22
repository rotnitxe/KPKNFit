package com.example.kpkn.data.repository

import com.example.kpkn.data.models.CompetitionRecord
import kotlinx.coroutines.flow.StateFlow
import r4.jvm.unavailable

/** Only the nullable boundary in ProgramKeyDateEngine; no repository is constructed by these tests. */
class CompetitionRepository {
    init { unavailable("CompetitionRepository constructor") }
    val records: StateFlow<List<CompetitionRecord>> get() = unavailable("CompetitionRepository.records")
    fun upsert(record: CompetitionRecord): Unit = unavailable("CompetitionRepository.upsert")
    fun delete(id: String): Unit = unavailable("CompetitionRepository.delete")
}
