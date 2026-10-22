package com.example.kpkn.data.repository

import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * STUBS DEL ARNÉS JVM de los repositorios Android (data/repository, Room + Context). Solo la superficie que usa el
 * código puro: en el arnés no hay repositorio, así que getInstance() falla y el código cae a su respaldo (sin datos).
 */

class CompetitionRepository private constructor() {
    private val _records = MutableStateFlow<List<CompetitionRecord>>(emptyList())
    val records: StateFlow<List<CompetitionRecord>> = _records.asStateFlow()

    fun upsert(record: CompetitionRecord) {}

    fun delete(id: String) {}

    companion object {
        fun getInstance(): CompetitionRepository =
            throw IllegalStateException("CompetitionRepository no disponible en el arnés JVM")
    }
}

class NutritionRepository private constructor() {
    private val _dailyGoalSnapshots = MutableStateFlow<List<DailyGoalSnapshot>>(emptyList())
    val dailyGoalSnapshots: StateFlow<List<DailyGoalSnapshot>> = _dailyGoalSnapshots.asStateFlow()

    val activeNutritionPlan: NutritionPlan?
        get() = null

    companion object {
        fun getInstance(): NutritionRepository =
            throw IllegalStateException("NutritionRepository no disponible en el arnés JVM")
    }
}
