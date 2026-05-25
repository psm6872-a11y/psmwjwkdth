package com.example.danallacalendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.danallacalendar.data.CalendarDatabase
import com.example.danallacalendar.data.CalendarRepository
import com.example.danallacalendar.ui.screens.AddEditEventScreen
import com.example.danallacalendar.ui.screens.CalendarMainScreen
import com.example.danallacalendar.ui.screens.SearchScreen
import com.example.danallacalendar.ui.screens.SyncCenterScreen
import com.example.danallacalendar.ui.viewmodel.CalendarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Composable
fun MainNavigation() {
    val context = LocalContext.current.applicationContext
    val viewModel: CalendarViewModel = viewModel {
        val database = CalendarDatabase.getDatabase(
            context,
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
        val repository = CalendarRepository(database.eventDao())
        CalendarViewModel(repository)
    }

    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                CalendarMainScreen(
                    onNavigate = { navKey -> backStack.add(navKey) },
                    viewModel = viewModel
                )
            }
            entry<AddEditEvent> { key ->
                AddEditEventScreen(
                    eventId = key.eventId,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel
                )
            }
            entry<Search> {
                SearchScreen(
                    onNavigate = { navKey -> backStack.add(navKey) },
                    onNavigateBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel
                )
            }
            entry<SyncCenter> {
                SyncCenterScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel
                )
            }
        }
    )
}
