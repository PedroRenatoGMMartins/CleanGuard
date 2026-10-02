package com.cleanguard.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cleanguard.app.domain.apps.AppFilter
import com.cleanguard.app.ui.apps.AppListScreen
import com.cleanguard.app.ui.apps.AppsViewModel
import com.cleanguard.app.ui.dashboard.DashboardScreen
import com.cleanguard.app.ui.details.AppDetailScreen
import com.cleanguard.app.ui.details.AppDetailViewModel
import com.cleanguard.app.ui.privacy.PrivacyScreen
import com.cleanguard.app.ui.security.ScanResultsScreen
import com.cleanguard.app.ui.security.SecurityScanScreen
import com.cleanguard.app.ui.settings.SettingsScreen
import com.cleanguard.app.ui.storage.StorageScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val APPS = "apps?${AppsViewModel.ARG_FILTER}={${AppsViewModel.ARG_FILTER}}"
    const val STORAGE = "storage"
    const val SECURITY = "security"
    const val RESULTS = "results"
    const val DETAIL = "app/{${AppDetailViewModel.ARG_PACKAGE}}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun apps(filter: AppFilter) = "apps?${AppsViewModel.ARG_FILTER}=${filter.name}"
    fun detail(packageName: String) = "app/${Uri.encode(packageName)}"
}

@Composable
fun CleanGuardNavHost(navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.popBackStack() }
    val openApp: (String) -> Unit = { navController.navigate(Routes.detail(it)) }

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenCleanup = { navController.navigate(Routes.apps(AppFilter.ALL)) },
                onOpenSecurity = { navController.navigate(Routes.SECURITY) },
                onOpenResults = { navController.navigate(Routes.RESULTS) },
                onOpenUnused = { navController.navigate(Routes.apps(AppFilter.UNUSED)) },
                onOpenStorage = { navController.navigate(Routes.STORAGE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(
            route = Routes.APPS,
            arguments = listOf(
                navArgument(AppsViewModel.ARG_FILTER) {
                    type = NavType.StringType
                    defaultValue = AppFilter.ALL.name
                },
            ),
        ) { entry ->
            val filter = AppFilter.fromName(entry.arguments?.getString(AppsViewModel.ARG_FILTER))
            AppListScreen(
                title = if (filter == AppFilter.UNUSED) "Aplicativos não utilizados" else "Limpeza",
                onBack = back,
                onOpenApp = openApp,
            )
        }
        composable(Routes.STORAGE) { StorageScreen(onBack = back) }
        composable(Routes.SECURITY) {
            SecurityScanScreen(
                onBack = back,
                onShowResults = {
                    navController.navigate(Routes.RESULTS) {
                        popUpTo(Routes.SECURITY) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.RESULTS) {
            ScanResultsScreen(
                onBack = back,
                onOpenDetails = openApp,
                onStartScan = {
                    navController.navigate(Routes.SECURITY) {
                        popUpTo(Routes.RESULTS) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(AppDetailViewModel.ARG_PACKAGE) { type = NavType.StringType }),
        ) {
            AppDetailScreen(onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onOpenPrivacy = { navController.navigate(Routes.PRIVACY) })
        }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = back) }
    }
}
