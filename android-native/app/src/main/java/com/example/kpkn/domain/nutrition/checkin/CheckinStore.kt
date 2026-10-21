package com.example.kpkn.domain.nutrition.checkin

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** La decisión se publica después del guardado; aplicar exige un recibo durable del plan. */
interface CheckinStore {
    val records: Flow<List<CheckinRecord>>
    suspend fun apply(plan: CheckinPlan)
    suspend fun decline(weekKey: LocalDate, proposal: CheckinProposal?)
    suspend fun postpone(weekKey: LocalDate, today: LocalDate, proposal: CheckinProposal?)
}
