package org.umn.application.studenthub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.umn.application.studenthub.data.model.FinanceSummary
import org.umn.application.studenthub.data.model.ScheduleItem

class DashboardRepository {
    fun getNextSchedule(): Flow<ScheduleItem> {
        return flowOf(
            ScheduleItem(
                title = "Mobile Application Programming",
                timeInfo = "Hari ini, 10:00",
                room = "Ruang C301",
                remainingTasks = 1,
            )
        )
    }

    fun getFinanceSummary(): Flow<FinanceSummary> {
        return flowOf(
            FinanceSummary(
                remainingBudgetFormatted = "Rp 35.000",
                todayExpenseFormatted = "Pengeluaran hari ini: Rp 15.000",
            )
        )
    }
}
