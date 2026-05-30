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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp

object DeepLinkManager {
    var pendingInviteCode by mutableStateOf<String?>(null)
    var pendingInvitePerm by mutableStateOf<String?>(null)
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current.applicationContext
    val viewModel: CalendarViewModel = viewModel {
        val database = CalendarDatabase.getDatabase(
            context,
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
        val repository = CalendarRepository(database.eventDao())
        CalendarViewModel(repository, context)
    }

    val backStack = rememberNavBackStack(Main)

    // Deep Link Invite Dialog Overlay
    val pendingCode = DeepLinkManager.pendingInviteCode
    val pendingPerm = DeepLinkManager.pendingInvitePerm
    if (pendingCode != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                DeepLinkManager.pendingInviteCode = null
                DeepLinkManager.pendingInvitePerm = null
            },
            title = { androidx.compose.material3.Text("친구와 공유 초대", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                androidx.compose.material3.Text("친구의 공유 캘린더(코드: $pendingCode)에 참여하여 일정을 공유하시겠습니까?")
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.syncManager.joinHost("", pendingCode, "친구 기기", pendingPerm)
                        backStack.add(SyncCenter)
                        DeepLinkManager.pendingInviteCode = null
                        DeepLinkManager.pendingInvitePerm = null
                    }
                ) {
                    androidx.compose.material3.Text("수락")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        DeepLinkManager.pendingInviteCode = null
                        DeepLinkManager.pendingInvitePerm = null
                    }
                ) {
                    androidx.compose.material3.Text("거절")
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        )
    }

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
