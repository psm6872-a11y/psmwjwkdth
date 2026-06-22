package com.example.danallacalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.data.TrashItem
import com.example.danallacalendar.data.repository.CalendarRepository
import com.example.danallacalendar.estimate.Estimate
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: CalendarRepository
) : ViewModel() {

    val trashItems: StateFlow<List<TrashItem>> = repository.getTrashItemsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Prune expired trash items (older than 7 days) on init
        viewModelScope.launch {
            repository.pruneTrash()
        }
    }

    fun restoreItem(item: TrashItem) {
        viewModelScope.launch {
            try {
                val gson = Gson()
                if (item.itemType == "EVENT") {
                    val event = gson.fromJson(item.serializedJson, Event::class.java)
                    repository.restoreEvent(event)
                } else if (item.itemType == "ESTIMATE") {
                    val estimate = gson.fromJson(item.serializedJson, Estimate::class.java)
                    repository.restoreEstimate(estimate)
                }
                // Once restored, delete from trash permanently
                repository.deleteTrashItemPermanently(item)
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to restore item", e)
            }
        }
    }

    fun deletePermanently(item: TrashItem) {
        viewModelScope.launch {
            repository.deleteTrashItemPermanently(item)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.clearTrash()
        }
    }
}
