package com.example.danallacalendar

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.ui.platform.LocalContext
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.theme.DanallaCalendarTheme
import com.example.danallacalendar.ui.screens.CalendarMainScreen
import com.example.danallacalendar.ui.screens.AddEditEventScreen
import com.example.danallacalendar.ui.screens.SearchScreen
import com.example.danallacalendar.ui.viewmodel.CalendarViewModel
import com.example.danallacalendar.ui.nickname.NicknameScreen
import com.example.danallacalendar.ui.nickname.NicknameViewModel
import com.example.danallacalendar.ui.room.RoomScreen
import com.example.danallacalendar.ui.room.RoomViewModel
import com.example.danallacalendar.backup.BackupScreen
import com.example.danallacalendar.estimate.EstimateListScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    private val calendarViewModel: CalendarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            DanallaCalendarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(userPreferences)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "danallacalendar") {
                if (uri.host == "join") {
                    val code = uri.getQueryParameter("code")
                    if (!code.isNullOrBlank()) {
                        userPreferences.setLastRoomCode(code)
                        calendarViewModel.loginToRoom(code)
                    }
                } else if (uri.host == "view") {
                    val dateMillisStr = uri.getQueryParameter("dateMillis")
                    val dateMillis = dateMillisStr?.toLongOrNull()
                    if (dateMillis != null) {
                        calendarViewModel.selectDate(dateMillis)
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(userPreferences: UserPreferences) {
    val navController = rememberNavController()

    // Determine initial route
    val initialRoute = if (userPreferences.getNickname().isNullOrBlank()) "nickname"
                       else if (userPreferences.getLastRoomCode().isNullOrBlank()) "room"
                       else "calendar"

    NavHost(navController = navController, startDestination = initialRoute) {
        composable(
            route = "estimate?moveDate={moveDate}&departure={departure}&destination={destination}&phone={phone}&copyFromEstimateJson={copyFromEstimateJson}&scheduleId={scheduleId}",
            arguments = listOf(
                navArgument("moveDate") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("departure") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("destination") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("phone") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("copyFromEstimateJson") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("scheduleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val viewModel: com.example.danallacalendar.estimate.EstimateViewModel = hiltViewModel()
            val scheduleId = backStackEntry.arguments?.getString("scheduleId")
            com.example.danallacalendar.estimate.EstimateScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    if (viewModel.isSaved && !scheduleId.isNullOrBlank()) {
                        val popped = navController.popBackStack("calendar", inclusive = false)
                        if (!popped) {
                            navController.popBackStack()
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable("nickname") {
            val viewModel: NicknameViewModel = hiltViewModel()
            NicknameScreen(
                viewModel = viewModel,
                onNavigateToRoom = {
                    navController.navigate("room") {
                        popUpTo("nickname") { inclusive = true }
                    }
                }
            )
        }

        composable("room") {
            val activity = LocalContext.current as ComponentActivity
            val calendarViewModel: CalendarViewModel = hiltViewModel(activity)
            val viewModel: RoomViewModel = hiltViewModel()
            RoomScreen(
                viewModel = viewModel,
                onNavigateToCalendar = {
                    val code = userPreferences.getLastRoomCode()
                    calendarViewModel.loginToRoom(code)
                    navController.navigate("calendar") {
                        popUpTo("room") { inclusive = true }
                    }
                },
                onNavigateToNickname = {
                    navController.navigate("nickname") {
                        popUpTo("room") { inclusive = true }
                    }
                }
            )
        }

        composable("calendar") {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: CalendarViewModel = hiltViewModel(activity)
            CalendarMainScreen(
                viewModel = viewModel,
                onNavigateToAddEditEvent = { id ->
                    val route = if (id != null) "add_edit_event?id=$id" else "add_edit_event"
                    navController.navigate(route)
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                },
                onNavigateToBackup = {
                    navController.navigate("backup")
                },
                onNavigateToEstimate = {
                    navController.navigate("estimate")
                },
                onNavigateToEstimateList = {
                    navController.navigate("estimate_list")
                },
                onExitRoom = {
                    userPreferences.setLastRoomCode("")
                    navController.navigate("room") {
                        popUpTo("calendar") { inclusive = true }
                    }
                }
            )
        }

        composable("estimate_list") {
            EstimateListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEstimateCopy = { estimateJson ->
                    val encodedJson = android.net.Uri.encode(estimateJson)
                    navController.navigate("estimate?copyFromEstimateJson=$encodedJson")
                }
            )
        }

        composable(
            route = "add_edit_event?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val activity = LocalContext.current as ComponentActivity
            val viewModel: CalendarViewModel = hiltViewModel(activity)
            val eventIdStr = backStackEntry.arguments?.getString("id")
            val eventId = eventIdStr?.toIntOrNull()
            AddEditEventScreen(
                eventId = eventId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEstimate = { moveDate, departure, destination, phone, scheduleId, copyFromEstimateJson ->
                    val encDate = android.net.Uri.encode(moveDate)
                    val encDep = android.net.Uri.encode(departure)
                    val encDest = android.net.Uri.encode(destination)
                    val encPhone = android.net.Uri.encode(phone)
                    val encJson = if (copyFromEstimateJson != null) android.net.Uri.encode(copyFromEstimateJson) else null
                    val route = buildString {
                        append("estimate?moveDate=$encDate&departure=$encDep&destination=$encDest&phone=$encPhone")
                        if (scheduleId != null) {
                            append("&scheduleId=$scheduleId")
                        }
                        if (encJson != null) {
                            append("&copyFromEstimateJson=$encJson")
                        }
                    }
                    navController.navigate(route)
                },
                viewModel = viewModel
            )
        }

        composable("search") {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: CalendarViewModel = hiltViewModel(activity)
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = viewModel
            )
        }

        composable("backup") {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: CalendarViewModel = hiltViewModel(activity)
            val categories by viewModel.categories.collectAsStateWithLifecycle()
            val defaultCalendarId = categories.firstOrNull()?.id ?: 1
            BackupScreen(
                onNavigateBack = { navController.popBackStack() },
                defaultCalendarId = defaultCalendarId
            )
        }
    }
}
