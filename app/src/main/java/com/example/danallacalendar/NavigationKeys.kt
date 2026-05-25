package com.example.danallacalendar

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Main : NavKey

@Serializable
data class AddEditEvent(val eventId: Int? = null) : NavKey

@Serializable
data object Search : NavKey

@Serializable
data object SyncCenter : NavKey

