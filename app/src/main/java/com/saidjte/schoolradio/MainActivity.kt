package com.saidjte.schoolradio

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saidjte.schoolradio.core.theme.SchoolRadioTheme
import com.saidjte.schoolradio.core.ui.AppIcons
import com.saidjte.schoolradio.data.settings.AppSettings
import com.saidjte.schoolradio.di.ServiceLocator
import com.saidjte.schoolradio.feature.dashboard.DashboardScreen
import com.saidjte.schoolradio.feature.library.LibraryScreen
import com.saidjte.schoolradio.feature.player.PlayerBar
import com.saidjte.schoolradio.feature.playlists.PlaylistsScreen
import com.saidjte.schoolradio.feature.recordings.RecordingsScreen
import com.saidjte.schoolradio.feature.scheduling.ScheduleScreen
import com.saidjte.schoolradio.feature.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceLocator.init(this)
        ServiceLocator.playback.connect()

        // صلاحية الإشعارات (أندرويد 13+) — لإشعار المشغل الخلفي
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // اتجاه عربي صريح RTL على مستوى الجذر
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SchoolRadioTheme(themeMode = themeMode()) {
                    AppScaffold()
                }
            }
        }
    }

    @Composable
    private fun themeMode(): Int {
        val settings by ServiceLocator.settings.settings
            .collectAsStateWithLifecycle(initialValue = AppSettings())
        return settings.themeMode
    }
}

@Composable
private fun AppScaffold() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                PlayerBar(snackbar)
                NavigationBar(tonalElevation = 3.dp) {
                    // عدد التسجيلات — شارة حية على تبويب الأرشيف
                    val recCount by ServiceLocator.recordingsRepo.observeAll()
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    NavigationItem.entries.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(AppRoute.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (item.route == AppRoute.RECORDINGS && recCount.isNotEmpty()) {
                                    BadgedBox(badge = {
                                        Badge { Text("${recCount.size}", fontSize = 10.sp) }
                                    }) {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = AppRoute.DASHBOARD,
                enterTransition = {
                    fadeIn(tween(240)) + scaleIn(
                        initialScale = 0.97f, animationSpec = tween(240)
                    )
                },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = {
                    fadeOut(tween(160)) + scaleOut(
                        targetScale = 0.97f, animationSpec = tween(160)
                    )
                }
            ) {
                composable(AppRoute.DASHBOARD) {
                    DashboardScreen(
                        snackbar = snackbar,
                        onNavigateToSchedule = { nav.navigateSafely(AppRoute.SCHEDULING) },
                        onNavigateToLibrary = { nav.navigateSafely(AppRoute.LIBRARY) }
                    )
                }
                composable(AppRoute.LIBRARY) { LibraryScreen(snackbar) }
                composable(AppRoute.PLAYLISTS) { PlaylistsScreen(snackbar) }
                composable(AppRoute.RECORDINGS) { RecordingsScreen(snackbar) }
                composable(AppRoute.SCHEDULING) { ScheduleScreen(snackbar) }
                composable(AppRoute.SETTINGS) { SettingsScreen(snackbar) }
            }
        }
    }
}

/** تنقّل بلا تكرار أعلى المكدس */
private fun NavHostController.navigateSafely(route: String) {
    navigate(route) {
        popUpTo(AppRoute.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

object AppRoute {
    const val DASHBOARD = "dashboard"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val RECORDINGS = "recordings"
    const val SCHEDULING = "scheduling"
    const val SETTINGS = "settings"
}

enum class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME(AppRoute.DASHBOARD, "الرئيسية", Icons.Filled.Home),
    LIBRARY(AppRoute.LIBRARY, "المكتبة", Icons.Filled.PlayArrow),
    PLAYLISTS(AppRoute.PLAYLISTS, "القوائم", Icons.Filled.List),
    RECORDINGS(AppRoute.RECORDINGS, "التسجيلات", AppIcons.LibraryMusic),
    SCHEDULE(AppRoute.SCHEDULING, "الجدولة", Icons.Filled.DateRange),
    SETTINGS(AppRoute.SETTINGS, "الإعدادات", Icons.Filled.Settings),
}
