package com.example.absen_android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.absen_android.screen.HistoryAttendanceScreen
import com.example.absen_android.screen.HomeScreen
import com.example.absen_android.screen.IzinScreen
import com.example.absen_android.screen.LoginScreen
import com.example.absen_android.screen.LogoutScreen
import com.example.absen_android.screen.ReportMonthlyScreen
import com.example.absen_android.screen.ReportYearScreen
import com.example.absen_android.screen.SplashScreen
import com.example.absen_android.utils.SessionManager

// ── Routes ────────────────────────────────────────────────────────────────────
object Routes {
    const val SPLASH             = "splash"
    const val LOGIN              = "login?expired={expired}"
    const val HOME               = "home"
    const val IZIN               = "izin"
    const val REPORT_MONTHLY     = "report_monthly"
    const val HISTORY_ATTENDANCE = "history_attendance"
    const val REPORT_YEAR        = "report_year"
    const val LOGOUT             = "logout"

    fun loginRoute(expired: Boolean = false) = "login?expired=$expired"
}

// ── Bottom Nav Items ──────────────────────────────────────────────────────────
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

// HRD: semua menu termasuk laporan bulanan & tahunan
val bottomNavItems = listOf(
    BottomNavItem("Home",    Icons.Filled.Home,        Routes.HOME),
    BottomNavItem("Izin",    Icons.Filled.Description, Routes.IZIN),
    BottomNavItem("Riwayat", Icons.Filled.AccessTime,  Routes.HISTORY_ATTENDANCE), // ← BARU
    BottomNavItem("Bulan",   Icons.Filled.DateRange,   Routes.REPORT_MONTHLY),
    BottomNavItem("Tahun",   Icons.Filled.Summarize,   Routes.REPORT_YEAR),
    BottomNavItem("Logout",  Icons.Filled.ExitToApp,   Routes.LOGOUT)
)

// User biasa: Home, Izin, Riwayat, Logout
val commonNavItems = listOf(
    BottomNavItem("Home",    Icons.Filled.Home,        Routes.HOME),
    BottomNavItem("Izin",    Icons.Filled.Description, Routes.IZIN),
    BottomNavItem("Riwayat", Icons.Filled.AccessTime,  Routes.HISTORY_ATTENDANCE), // ← BARU
    BottomNavItem("Logout",  Icons.Filled.ExitToApp,   Routes.LOGOUT)
)

val allNavRoutes = bottomNavItems.map { it.route }

// ── App Navigation ────────────────────────────────────────────────────────────
@Composable
fun AppNavigation() {
    val context       = LocalContext.current
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route
    val isHrd         = SessionManager.isHrd(context)
    val displayNavItems = if (isHrd) bottomNavItems else commonNavItems
    val showBottomBar   = allNavRoutes.any { currentRoute?.startsWith(it) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    displayNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.SPLASH,
            modifier         = Modifier.padding(innerPadding)
        ) {
            // ── Splash ────────────────────────────────────────────────────────
            composable(Routes.SPLASH) {
                SplashScreen(
                    onSessionValid = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                    onSessionInvalid = { tokenExpired ->
                        navController.navigate(Routes.loginRoute(tokenExpired)) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            // ── Login ─────────────────────────────────────────────────────────
            composable(
                route     = Routes.LOGIN,
                arguments = listOf(navArgument("expired") {
                    type         = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val expired = backStackEntry.arguments?.getBoolean("expired") ?: false
                LoginScreen(
                    onLoginSuccess      = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.loginRoute()) { inclusive = true }
                        }
                    },
                    tokenExpiredMessage = if (expired) "Sesi telah berakhir, silakan login kembali" else null
                )
            }

            // ── Home ──────────────────────────────────────────────────────────
            composable(Routes.HOME)   { HomeScreen() }

            // ── Izin ──────────────────────────────────────────────────────────
            composable(Routes.IZIN)   { IzinScreen() }

            // ── History Attendance ────────────────────────────────────────────
            composable(Routes.HISTORY_ATTENDANCE) { HistoryAttendanceScreen() } // ← BARU

            // ── Report Monthly ────────────────────────────────────────────────
            composable(Routes.REPORT_MONTHLY) { ReportMonthlyScreen() }

            // ── Report Year ───────────────────────────────────────────────────
            composable(Routes.REPORT_YEAR) { ReportYearScreen() }

            // ── Logout ────────────────────────────────────────────────────────
            composable(Routes.LOGOUT) {
                LogoutScreen(
                    onLogoutConfirmed = {
                        SessionManager.clearSession(context)
                        navController.navigate(Routes.loginRoute()) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}