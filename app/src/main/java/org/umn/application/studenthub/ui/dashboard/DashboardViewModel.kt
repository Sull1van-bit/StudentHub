package org.umn.application.studenthub.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.umn.application.studenthub.data.repository.DashboardRepository

class DashboardViewModel(
    private val repository: DashboardRepository = DashboardRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            combine(
                repository.getNextSchedule(),
                repository.getFinanceSummary(),
            ) { schedule, finance ->
                DashboardUiState(
                    userName = "Sullivan",
                    nextSchedule = schedule,
                    financeSummary = finance,
                    isLoading = false,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
}
