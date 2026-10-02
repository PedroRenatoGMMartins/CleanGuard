package com.cleanguard.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.demo.DemoData
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.apps.AppUsageClassifier
import com.cleanguard.app.domain.storage.DeviceStorage
import com.cleanguard.app.domain.storage.StorageCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val loading: Boolean = true,
    val device: DeviceStorage? = null,
    val installedCount: Int = 0,
    val userAppsCount: Int = 0,
    val hasUsageAccess: Boolean = false,
    /** null quando não dá para saber (sem "Acesso ao uso" e sem modo demo). */
    val unusedCount: Int? = null,
    val unusedBytes: Long = 0L,
    /** null = nenhuma verificação nesta sessão. */
    val flaggedCount: Int? = null,
    val suspiciousCount: Int = 0,
    val lastScanAt: Long? = null,
    val reclaimableBytes: Long = 0L,
    val reclaimableIncludesStorage: Boolean = false,
    val demoMode: Boolean = false,
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val device = MutableStateFlow<DeviceStorage?>(null)

    val state: StateFlow<DashboardUiState> = combine(
        container.installedApps.snapshot,
        container.session.lastScan,
        container.session.lastStorage,
        container.settings.settings,
        device,
    ) { snapshot, scan, storage, settings, deviceStorage ->
        val now = System.currentTimeMillis()
        val realApps = snapshot?.apps.orEmpty()
        val apps = if (settings.demoMode) realApps + DemoData.apps(now) else realApps
        val classifier = AppUsageClassifier(settings.unusedThresholdDays)
        val hasUsage = snapshot?.hasUsageAccess == true
        val unused = apps.filter { classifier.isUnused(it, now) }
        val unusedBytes = unused.sumOf { it.totalSizeBytes }
        val cacheBytes = apps.sumOf { it.storage?.cacheBytes ?: 0L }
        DashboardUiState(
            loading = snapshot == null,
            device = deviceStorage,
            installedCount = apps.size,
            userAppsCount = apps.count { it.isUserApp },
            hasUsageAccess = hasUsage,
            unusedCount = if (hasUsage || settings.demoMode) unused.size else null,
            unusedBytes = unusedBytes,
            flaggedCount = scan?.flaggedCount,
            suspiciousCount = scan?.suspiciousCount ?: 0,
            lastScanAt = scan?.finishedAt,
            reclaimableBytes = StorageCalculator.dashboardReclaimable(
                unusedAppsBytes = unusedBytes,
                appCacheBytes = cacheBytes,
                storageReclaimable = storage?.reclaimableEstimateBytes ?: 0L,
            ),
            reclaimableIncludesStorage = storage != null,
            demoMode = settings.demoMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            device.value = withContext(Dispatchers.IO) { container.deviceStorage.read() }
            container.installedApps.refresh()
        }
    }

    /** Ao voltar das Configurações do Android: recarrega se a permissão de uso mudou. */
    fun onResume() {
        viewModelScope.launch {
            device.value = withContext(Dispatchers.IO) { container.deviceStorage.read() }
            val snapshot = container.installedApps.snapshot.value
            val access = withContext(Dispatchers.IO) { container.installedApps.hasUsageAccess() }
            if (snapshot != null && snapshot.hasUsageAccess != access) container.installedApps.refresh()
        }
    }
}
