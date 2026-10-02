package com.cleanguard.app.ui.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.demo.DemoData
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.apps.AppFilter
import com.cleanguard.app.domain.apps.AppListFilter
import com.cleanguard.app.domain.apps.AppUsageClassifier
import com.cleanguard.app.domain.apps.InstalledApp
import com.cleanguard.app.domain.selection.Selection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppsUiState(
    val filter: AppFilter = AppFilter.ALL,
    val query: String = "",
    val visibleApps: List<InstalledApp> = emptyList(),
    val totalApps: Int = 0,
    val loading: Boolean = true,
    val hasUsageAccess: Boolean = false,
    val demoMode: Boolean = false,
    val thresholdDays: Int = AppUsageClassifier.DEFAULT_THRESHOLD_DAYS,
    val selection: Selection<String> = Selection(),
    val selectedBytes: Long = 0L,
    val selectedApps: List<InstalledApp> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val selfPackage: String = "",
) {
    val usageClassifier: AppUsageClassifier get() = AppUsageClassifier(thresholdDays)

    fun canUninstall(app: InstalledApp): Boolean = AppListFilter.canUninstall(app, selfPackage)
}

/** Usado pelas telas "Limpeza" e "Aplicativos não utilizados" (muda só o filtro inicial). */
class AppsViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val filter = MutableStateFlow(AppFilter.fromName(savedStateHandle.get<String>(ARG_FILTER)))
    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow(Selection<String>())

    private val baseState = combine(
        container.installedApps.snapshot,
        container.settings.settings,
        filter,
        query,
        selection,
    ) { snapshot, settings, currentFilter, currentQuery, currentSelection ->
        val now = System.currentTimeMillis()
        val all = snapshot?.apps.orEmpty().let { if (settings.demoMode) it + DemoData.apps(now) else it }
        val listFilter = AppListFilter(AppUsageClassifier(settings.unusedThresholdDays))
        AppsUiState(
            filter = currentFilter,
            query = currentQuery,
            visibleApps = listFilter.apply(all, currentFilter, now, currentQuery),
            totalApps = all.size,
            loading = snapshot == null,
            hasUsageAccess = snapshot?.hasUsageAccess == true,
            demoMode = settings.demoMode,
            thresholdDays = settings.unusedThresholdDays,
            selection = currentSelection,
            selectedBytes = currentSelection.sumOf(all, { it.packageName }, { it.totalSizeBytes }),
            selectedApps = currentSelection.selectedItems(all) { it.packageName },
            now = now,
            selfPackage = container.selfPackage,
        )
    }

    val state: StateFlow<AppsUiState> = combine(baseState, container.installedApps.isLoading) { s, refreshing ->
        s.copy(loading = s.loading || (refreshing && s.visibleApps.isEmpty()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState(filter = filter.value))

    val isRefreshing: StateFlow<Boolean> = container.installedApps.isLoading

    init {
        viewModelScope.launch { container.installedApps.ensureLoaded() }
    }

    fun setFilter(newFilter: AppFilter) {
        filter.value = newFilter
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun toggle(app: InstalledApp) {
        if (!AppListFilter.canUninstall(app, container.selfPackage)) return
        selection.update { it.toggle(app.packageName) }
    }

    fun selectAllVisible() {
        val visible = state.value.visibleApps
        selection.update { sel -> sel.selectAll(visible, { it.packageName }) { AppListFilter.canUninstall(it, container.selfPackage) } }
    }

    fun clearSelection() {
        selection.update { it.clear() }
    }

    fun selectedPackages(): List<String> = selection.value.keys.toList()

    fun refresh() {
        viewModelScope.launch { container.installedApps.refresh() }
    }

    /** Depois do fluxo oficial de desinstalação: atualiza a lista e limpa o que foi removido. */
    fun onUninstallFinished() {
        viewModelScope.launch {
            val snapshot = container.installedApps.refresh()
            val installed = snapshot.apps.map { it.packageName }.toSet()
            selection.update { it.retainOnly(installed) }
            container.session.lastScan.value?.let { report ->
                container.session.setScan(report.copy(assessments = report.assessments.filter {
                    it.profile.isDemo || it.profile.packageName in installed
                }))
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            val snapshot = container.installedApps.snapshot.value ?: return@launch
            val access = withContext(Dispatchers.IO) { container.installedApps.hasUsageAccess() }
            if (snapshot.hasUsageAccess != access) container.installedApps.refresh()
        }
    }

    companion object {
        const val ARG_FILTER = "filter"
    }
}
