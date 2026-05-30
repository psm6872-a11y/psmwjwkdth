package com.example.danallacalendar

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

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
            if (uri.scheme == "danallacalendar" && uri.host == "join") {
                val code = uri.getQueryParameter("code")
                if (!code.isNullOrBlank()) {
                    userPreferences.setLastRoomCode(code)
                    // If nickname is already set, it will auto-route to calendar on next navigation/restart
                }
            }
        }
    }
}

@Composable
fun AppNavigation(userPreferences: UserPreferences) {
    val navController = rememberNavController()

    // Determine initial route
    val initialRoute = when {
        userPreferences.getNickname().isEmpty() -> "nickname"
        userPreferences.getLastRoomCode().isEmpty() -> "room"
        else -> "calendar"
    }

    NavHost(navController = navController, startDestination = initialRoute) {
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
            val viewModel: RoomViewModel = hiltViewModel()
            RoomScreen(
                viewModel = viewModel,
                onNavigateToCalendar = {
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
                onExitRoom = {
                    userPreferences.setLastRoomCode("")
                    navController.navigate("room") {
                        popUpTo("calendar") { inclusive = true }
                    }
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
    }
}
