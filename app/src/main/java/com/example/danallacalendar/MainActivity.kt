package com.example.danallacalendar

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.view.WindowManager
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
import com.example.danallacalendar.ui.screens.StatisticsScreen
import com.example.danallacalendar.ui.viewmodel.CalendarViewModel
import com.example.danallacalendar.ui.nickname.NicknameScreen
import com.example.danallacalendar.ui.nickname.NicknameViewModel
import com.example.danallacalendar.ui.room.RoomScreen
import com.example.danallacalendar.ui.room.RoomViewModel
import com.example.danallacalendar.backup.BackupScreen
import com.example.danallacalendar.estimate.EstimateListScreen
import com.example.danallacalendar.ui.screens.TrashScreen
import com.example.danallacalendar.ui.screens.BlacklistScreen
import com.example.danallacalendar.ui.viewmodel.TrashViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var appUpdateManager: com.google.android.play.core.appupdate.AppUpdateManager

    private val calendarViewModel: CalendarViewModel by viewModels()

    private val installStateUpdatedListener = com.google.android.play.core.install.InstallStateUpdatedListener { state ->
        if (state.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
            calendarViewModel.setUpdateDownloaded(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager.registerListener(installStateUpdatedListener)

        // Check for app update and record baseline time
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (packageInfo.longVersionCode and 0xFFFFFFFFL).toInt()
            } else {
                packageInfo.versionCode
            }
            val lastVersionCode = userPreferences.getLastInstalledVersionCode()
            if (currentVersionCode != lastVersionCode) {
                userPreferences.setLastAppUpdateTime(System.currentTimeMillis())
                userPreferences.setLastInstalledVersionCode(currentVersionCode)
                android.util.Log.d("MainActivity", "App version changed from $lastVersionCode to $currentVersionCode. Recorded update time.")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to check version code for update baseline", e)
        }

        // Request all required runtime permissions at once on app launch
        val permissionsToRequest = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungrantedPermissions = permissionsToRequest.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (ungrantedPermissions.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                ungrantedPermissions.toTypedArray(),
                101
            )
        }

        handleIntent(intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }

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
        setIntent(intent)
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
                    val changeTitle = uri.getQueryParameter("title")
                    val changeBody = uri.getQueryParameter("body")
                    val syncId = uri.getQueryParameter("syncId")
                    val isCompleteNotification = changeTitle?.contains("완료 버튼") == true

                    if (!syncId.isNullOrBlank() && !isCompleteNotification) {
                        calendarViewModel.setHighlightedEventSyncId(syncId)
                    }
                    if ((!changeTitle.isNullOrBlank() || !changeBody.isNullOrBlank()) && !isCompleteNotification) {
                        calendarViewModel.setPendingChangeNotification(changeTitle ?: "알림", changeBody ?: "")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
                calendarViewModel.setUpdateDownloaded(true)
            } else if (appUpdateInfo.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE,
                        this,
                        5001
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateUpdatedListener)
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
                onNavigateToTrash = {
                    navController.navigate("trash")
                },
                onNavigateToBlacklist = {
                    navController.navigate("blacklist")
                },
                onNavigateToStatistics = { isCreator ->
                    navController.navigate("statistics?isCreator=$isCreator")
                },
                onExitRoom = {
                    userPreferences.setLastRoomCode("")
                    navController.navigate("room") {
                        popUpTo("calendar") { inclusive = true }
                    }
                }
            )
        }

        composable("trash") {
            val viewModel: TrashViewModel = hiltViewModel()
            TrashScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "blacklist",
            deepLinks = listOf(
                androidx.navigation.navDeepLink {
                    uriPattern = "danallacalendar://blacklist"
                }
            )
        ) {
            BlacklistScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "estimate_list?highlightId={highlightId}",
            arguments = listOf(
                navArgument("highlightId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                androidx.navigation.navDeepLink {
                    uriPattern = "danallacalendar://estimate?highlightId={highlightId}"
                }
            )
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            EstimateListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEstimateCopy = { estimateJson ->
                    val encodedJson = android.net.Uri.encode(estimateJson)
                    navController.navigate("estimate?copyFromEstimateJson=$encodedJson")
                },
                highlightId = highlightId
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
            ),
            deepLinks = listOf(
                androidx.navigation.navDeepLink {
                    uriPattern = "danallacalendar://event?id={id}"
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

        composable(
            route = "statistics?isCreator={isCreator}",
            arguments = listOf(
                navArgument("isCreator") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val isCreator = backStackEntry.arguments?.getBoolean("isCreator") ?: false
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() },
                isCreator = isCreator
            )
        }
    }
}
