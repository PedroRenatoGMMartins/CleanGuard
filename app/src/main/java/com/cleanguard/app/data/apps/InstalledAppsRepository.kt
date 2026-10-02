package com.cleanguard.app.data.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.cleanguard.app.core.util.PackageManagerCompat
import com.cleanguard.app.domain.apps.AppUsage
import com.cleanguard.app.domain.apps.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppsSnapshot(
    val apps: List<InstalledApp>,
    val hasUsageAccess: Boolean,
    val loadedAt: Long,
)

/**
 * Lista os apps instalados usando apenas APIs públicas. Mantém o último resultado em memória
 * para que o painel e as listas compartilhem os mesmos dados.
 */
class InstalledAppsRepository(
    context: Context,
    private val usageStats: UsageStatsProvider,
    private val sizes: AppSizeProvider,
) {
    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow<AppsSnapshot?>(null)
    val snapshot: StateFlow<AppsSnapshot?> = _snapshot.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val selfPackage: String get() = appContext.packageName

    suspend fun ensureLoaded(): AppsSnapshot = _snapshot.value ?: refresh()

    suspend fun refresh(): AppsSnapshot = mutex.withLock {
        _isLoading.value = true
        try {
            val result = withContext(Dispatchers.IO) { load() }
            _snapshot.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    fun hasUsageAccess(): Boolean = usageStats.hasUsageAccess()

    fun isInstalled(packageName: String): Boolean = PackageManagerCompat.isInstalled(pm, packageName)

    private fun load(): AppsSnapshot {
        val now = System.currentTimeMillis()
        val usageMap = usageStats.queryUsage(now)
        val launcherPackages = runCatching { PackageManagerCompat.launcherPackages(pm) }.getOrDefault(emptySet())
        val apps = PackageManagerCompat.installedApplications(pm)
            .mapNotNull { ai -> runCatching { toInstalledApp(ai, launcherPackages, usageMap) }.getOrNull() }
        return AppsSnapshot(apps = apps, hasUsageAccess = usageMap != null, loadedAt = now)
    }

    private fun toInstalledApp(
        ai: ApplicationInfo,
        launcherPackages: Set<String>,
        usageMap: Map<String, AppUsage>?,
    ): InstalledApp {
        val pkg = ai.packageName
        val info = PackageManagerCompat.packageInfo(pm, pkg, 0)
        val installer = InstallerReader.read(pm, pkg)
        return InstalledApp(
            packageName = pkg,
            label = ai.loadLabel(pm).toString().ifBlank { pkg },
            isSystemApp = PackageManagerCompat.isSystem(ai),
            isUpdatedSystemApp = PackageManagerCompat.isUpdatedSystem(ai),
            hasLauncherEntry = pkg in launcherPackages,
            isEnabled = ai.enabled,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            versionName = info.versionName,
            versionCode = PackageManagerCompat.versionCode(info),
            targetSdk = ai.targetSdkVersion,
            apkSizeBytes = sizes.apkSize(ai),
            storage = if (usageMap != null) sizes.storage(ai) else null,
            // Com permissão, ausência no histórico = "sem registro de uso" (lastTimeUsed = 0).
            usage = usageMap?.let { it[pkg] ?: AppUsage(lastTimeUsed = 0L, foregroundTimeLast30DaysMs = 0L) },
            installerPackage = installer.installer ?: installer.initiator,
        )
    }
}
