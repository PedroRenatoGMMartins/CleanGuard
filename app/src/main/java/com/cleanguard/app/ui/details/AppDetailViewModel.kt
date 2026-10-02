package com.cleanguard.app.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.demo.DemoData
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.apps.AppListFilter
import com.cleanguard.app.domain.apps.AppUsageClassifier
import com.cleanguard.app.domain.apps.InstalledApp
import com.cleanguard.app.security.model.AppRiskAssessment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppDetailUiState(
    val packageName: String,
    val loading: Boolean = true,
    val app: InstalledApp? = null,
    val assessment: AppRiskAssessment? = null,
    val assessing: Boolean = false,
    val uninstalled: Boolean = false,
    val thresholdDays: Int = AppUsageClassifier.DEFAULT_THRESHOLD_DAYS,
    val now: Long = System.currentTimeMillis(),
    val canUninstall: Boolean = false,
)

class AppDetailViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle.get<String>(ARG_PACKAGE)) { "packageName ausente" }

    private val _state = MutableStateFlow(AppDetailUiState(packageName = packageName))
    val state: StateFlow<AppDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val settings = container.settings.current
            val app = if (DemoData.isDemoPackage(packageName)) {
                DemoData.apps(now).firstOrNull { it.packageName == packageName }
            } else {
                container.installedApps.ensureLoaded().apps.firstOrNull { it.packageName == packageName }
            }
            val cached = container.session.lastScan.value?.find(packageName)
            val uninstalled = app == null && !DemoData.isDemoPackage(packageName) &&
                !withContext(Dispatchers.IO) { container.installedApps.isInstalled(packageName) }
            _state.update {
                it.copy(
                    loading = false,
                    app = app,
                    assessment = cached,
                    thresholdDays = settings.unusedThresholdDays,
                    now = now,
                    canUninstall = app != null && AppListFilter.canUninstall(app, container.selfPackage),
                    uninstalled = uninstalled,
                )
            }
            if (cached == null && app != null) assess()
        }
    }

    /** Calcula a classificação só deste app (quando ainda não houve verificação completa). */
    fun assess() {
        if (_state.value.assessing) return
        viewModelScope.launch {
            _state.update { it.copy(assessing = true) }
            val result = container.securityScanner.assessPackage(packageName, container.settings.current.hashSystemApps)
            _state.update { it.copy(assessment = result, assessing = false) }
        }
    }

    fun onUninstallFinished() {
        viewModelScope.launch {
            val stillInstalled = withContext(Dispatchers.IO) { container.installedApps.isInstalled(packageName) }
            if (!stillInstalled) {
                container.session.lastScan.value?.let { report ->
                    container.session.setScan(report.copy(assessments = report.assessments.filterNot { it.profile.packageName == packageName }))
                }
                _state.update { it.copy(uninstalled = true) }
                container.installedApps.refresh()
            }
        }
    }

    companion object {
        const val ARG_PACKAGE = "packageName"
    }
}
