package org.umn.application.studenthub.ui.dashboard

import org.umn.application.studenthub.data.model.FinanceSummary
import org.umn.application.studenthub.data.model.ScheduleItem

data class DashboardUiState(
    val userName: String = "Sullivan",
    val nextSchedule: ScheduleItem? = null,
    val financeSummary: FinanceSummary? = null,
    val isLoading: Boolean = false
)
