package app.xkeepalive.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.xkeepalive.ui.guide.GuideScreen
import app.xkeepalive.ui.home.HomeScreen
import app.xkeepalive.ui.log.LogScreen
import app.xkeepalive.ui.settings.SettingsScreen
import app.xkeepalive.ui.test.ProtocolScreen
import app.xkeepalive.ui.theme.Accent
import app.xkeepalive.ui.theme.Ink
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.viewmodel.MainViewModel

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab("home", "Home"),
    Tab("guide", "Guide"),
    Tab("log", "Log"),
    Tab("settings", "Settings"),
)

@Composable
fun XBackgroundRoot(viewModel: MainViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val consent by viewModel.consentVisible.collectAsStateWithLifecycle()
    val askNotification by viewModel.askNotification.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotificationResult(granted) }

    LaunchedEffect(askNotification) {
        if (askNotification && Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route != "protocol") {
                NavigationBar(containerColor = Ink) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                indicatorColor = Accent.copy(alpha = 0.16f),
                                unselectedIconColor = Mist,
                                unselectedTextColor = Mist,
                            ),
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    when (tab.route) {
                                        "guide" -> Icons.Filled.Info
                                        "log" -> Icons.Filled.Menu
                                        "settings" -> Icons.Filled.Settings
                                        else -> Icons.Filled.Home
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    settings = settings,
                    session = session,
                    events = events,
                    apps = apps,
                    appsLoading = appsLoading,
                    onToggle = viewModel::setAutomation,
                    onSelect = viewModel::selectApp,
                    onRefreshApps = viewModel::refreshApps,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onOpenShizuku = viewModel::openShizukuApp,
                )
            }
            composable("guide") {
                GuideScreen(
                    onDeveloperOptions = viewModel::openDeveloperOptions,
                    onOpenShizuku = viewModel::openShizukuApp,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onUsageAccess = viewModel::openUsageAccess,
                )
            }
            composable("log") {
                LogScreen(events = events, onClear = viewModel::clearLog)
            }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    session = session,
                    onRestore = viewModel::setRestoreOnBoot,
                    onPoll = viewModel::setPollInterval,
                    onUsage = viewModel::openUsageAccess,
                    onOwnBattery = viewModel::openOwnBattery,
                    onGamePower = viewModel::openGamePower,
                    onProtocol = { nav.navigate("protocol") },
                )
            }
            composable("protocol") {
                ProtocolScreen(
                    tests = events.filter { it.kind == "TEST" },
                    onSave = viewModel::recordTest,
                )
            }
        }
    }

    if (consent) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConsent,
            title = { Text("Start monitoring?") },
            text = {
                Text(
                    "X-KeepAlive will read which app is in the foreground, only to follow the game you chose. If Shizuku is running and you authorize it, the app uses documented ADB commands: Doze whitelist, standby bucket, idle state, and background app ops, then reads the result back. The game is not started, closed, or controlled.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmConsent) { Text("Start") } },
            dismissButton = { TextButton(onClick = viewModel::dismissConsent) { Text("Cancel") } },
        )
    }
}
