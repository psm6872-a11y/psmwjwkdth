package com.example.danallacalendar.estimate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EstimateListViewModel @Inject constructor(
    private val repository: EstimateRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _isShareEnabled = MutableStateFlow(userPreferences.isShareEnabled())
    val isShareEnabled: StateFlow<Boolean> = _isShareEnabled.asStateFlow()

    fun toggleShareEnabled(enabled: Boolean) {
        userPreferences.setShareEnabled(enabled)
        _isShareEnabled.value = enabled
    }

    val estimateList: StateFlow<List<Estimate>> = repository.getEstimatesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteEstimate(estimate: Estimate) {
        viewModelScope.launch {
            try {
                repository.deleteFromFirestore(estimate.id)
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Failed to delete estimate: ${estimate.id}", e)
            }
        }
    }
}
