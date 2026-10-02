package com.cleanguard.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.security.ScanProgress
import com.cleanguard.app.security.db.DatabaseInfo
import com.cleanguard.app.security.model.ScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ScanRunState(
    val running: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentLabel: String = "",
    val error: String? = null,
    val finishedEvent: Boolean = false,
)

data class SecurityUiState(
    val running: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentLabel: String = "",
    val error: String? = null,
    val finishedEvent: Boolean = false,
    val lastReport: ScanReport? = null,
    val databaseInfo: DatabaseInfo? = null,
    val demoMode: Boolean = false,
    val hashSystemApps: Boolean = false,
    val selfPackage: String = "",
) {
    val fraction: Float get() = if (total <= 0) 0f else current.toFloat() / total
}

/** Usado pela tela de verificação e pela tela de resultados. */
class SecurityViewModel(private val container: AppContainer) : ViewModel() {

    private val run = MutableStateFlow(ScanRunState())
    private var scanJob: Job? = null

    val state: StateFlow<SecurityUiState> = combine(
        run,
        container.session.lastScan,
        container.settings.settings,
    ) { r, report, settings ->
        SecurityUiState(
            running = r.running,
            current = r.current,
            total = r.total,
            currentLabel = r.currentLabel,
            error = r.error,
            finishedEvent = r.finishedEvent,
            lastReport = report,
            databaseInfo = container.malwareDatabase.info,
            demoMode = settings.demoMode,
            hashSystemApps = settings.hashSystemApps,
            selfPackage = container.selfPackage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityUiState(lastReport = container.session.lastScan.value))

    fun startScan() {
        if (run.value.running) return
        val settings = container.settings.current
        run.value = ScanRunState(running = true, currentLabel = "Preparando…")
        scanJob = viewModelScope.launch {
            container.securityScanner.scan(settings.hashSystemApps, settings.demoMode)
                .catch { e -> run.update { it.copy(running = false, error = "Falha na verificação: ${e.message ?: "erro desconhecido"}") } }
                .collect { progress ->
                    when (progress) {
                        is ScanProgress.Running -> run.update {
                            it.copy(current = progress.current, total = progress.total, currentLabel = progress.currentLabel)
                        }
                        is ScanProgress.Finished -> {
                            container.session.setScan(progress.report)
                            run.update { it.copy(running = false, finishedEvent = true) }
                        }
                    }
                }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        run.update { it.copy(running = false, currentLabel = "Verificação cancelada") }
    }

    fun consumeFinishedEvent() = run.update { it.copy(finishedEvent = false) }

    fun consumeError() = run.update { it.copy(error = null) }

    /** Depois da tela oficial de desinstalação: remove do relatório os apps que não existem mais. */
    fun onUninstallFinished(packages: List<String>) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                packages.filterNot { container.installedApps.isInstalled(it) }.toSet()
            }
            if (removed.isNotEmpty()) {
                container.session.lastScan.value?.let { report ->
                    container.session.setScan(report.copy(assessments = report.assessments.filterNot { it.profile.packageName in removed }))
                }
            }
            container.installedApps.refresh()
        }
    }
}
