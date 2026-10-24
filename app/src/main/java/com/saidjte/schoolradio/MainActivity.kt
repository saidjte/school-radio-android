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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
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
import com.saidjte.schoolradio.feature.onair.OnAirScreen
import com.saidjte.schoolradio.feature.player.PlayerBar
import com.saidjte.schoolradio.feature.playlists.PlaylistsScreen
import com.saidjte.schoolradio.feature.recordings.RecordingsScreen
import com.saidjte.schoolradio.feature.scheduling.ScheduleScreen
import com.saidjte.schoolradio.feature.scripts.ScriptsScreen
import com.saidjte.schoolradio.feature.scripts.TeleprompterScreen
import com.saidjte.schoolradio.feature.settings.SettingsScreen
import com.saidjte.schoolradio.feature.studio.StudioScreen

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
            // شاشة العرض على الهواء وقارئ السكربت تحتاجان كل المساحة — بلا شريط سفلي
            if (currentRoute != AppRoute.ON_AIR && currentRoute != AppRoute.TELEPROMPTER) {
            Column {
                PlayerBar(
                    snackbar,
                    onOpenOnAir = { nav.navigateSafely(AppRoute.ON_AIR) }
                )
                // شريط تنقل عائم بحواف دائرية وظل ناعم — لمسة عصرية (v0.7.0)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 10.dp
                ) {
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
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
                                // إصلاح v0.5.0: سطر واحد بخط مصغّر — لم تعد التسميات
                                // (التسجيلات…) تلتف على سطرين فوق أيقونتها في الشاشات الضيقة
                                label = {
                                    Text(
                                        item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 9.5.sp
                                    )
                                }
                            )
                        }
                    }
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
                        onNavigateToLibrary = { nav.navigateSafely(AppRoute.LIBRARY) },
                        onOpenOnAir = { nav.navigateSafely(AppRoute.ON_AIR) },
                        onOpenSettings = { nav.navigateSafely(AppRoute.SETTINGS) },
                        onOpenStudio = { nav.navigateSafely(AppRoute.STUDIO) },
                        onOpenScripts = { nav.navigateSafely(AppRoute.SCRIPTS) }
                    )
                }
                composable(AppRoute.STUDIO) { StudioScreen(snackbar) }
                composable(AppRoute.LIBRARY) { LibraryScreen(snackbar) }
                composable(AppRoute.PLAYLISTS) { PlaylistsScreen(snackbar) }
                composable(AppRoute.RECORDINGS) {
                    RecordingsScreen(
                        snackbar,
                        onScheduleClick = { nav.navigateSafely(AppRoute.SCHEDULING) }
                    )
                }
                composable(AppRoute.SCHEDULING) { ScheduleScreen(snackbar) }
                composable(AppRoute.SCRIPTS) {
                    ScriptsScreen(
                        snackbar,
                        onRead = { script ->
                            ServiceLocator.setTeleprompterScript(script)
                            nav.navigate(AppRoute.TELEPROMPTER)
                        }
                    )
                }
                composable(AppRoute.SETTINGS) { SettingsScreen(snackbar) }
                composable(AppRoute.ON_AIR) { OnAirScreen(onExit = { nav.popBackStack() }) }
                composable(AppRoute.TELEPROMPTER) {
                    // السكربت الحالي يُمرر عبر ServiceLocator (الكائنات لا تمر بمسارات التنقل)
                    val script by ServiceLocator.teleprompterScript
                        .collectAsStateWithLifecycle()
                    script?.let { s ->
                        TeleprompterScreen(script = s, onExit = { nav.popBackStack() })
                    }
                }
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
    const val STUDIO = "studio"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val RECORDINGS = "recordings"
    const val SCHEDULING = "scheduling"
    const val SCRIPTS = "scripts"
    const val SETTINGS = "settings"
    const val ON_AIR = "on_air"
    const val TELEPROMPTER = "teleprompter"
}

enum class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME(AppRoute.DASHBOARD, "الرئيسية", Icons.Filled.Home),
    STUDIO(AppRoute.STUDIO, "الاستوديو", AppIcons.Tune),
    LIBRARY(AppRoute.LIBRARY, "المكتبة", Icons.Filled.PlayArrow),
    PLAYLISTS(AppRoute.PLAYLISTS, "القوائم", Icons.Filled.List),
    RECORDINGS(AppRoute.RECORDINGS, "التسجيلات", AppIcons.LibraryMusic),
    SCHEDULE(AppRoute.SCHEDULING, "الجدولة", Icons.Filled.DateRange),
    SCRIPTS(AppRoute.SCRIPTS, "السكربتات", AppIcons.Article),
}
