package com.cleanguard.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.data.settings.AppSettings
import com.cleanguard.app.data.settings.ThemeMode
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.domain.storage.MediaAccess
import com.cleanguard.app.security.db.DatabaseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val databaseInfo: DatabaseInfo? = null,
    val hasUsageAccess: Boolean = false,
    val mediaAccess: MediaAccess = MediaAccess.NONE,
    val appVersion: String = "",
    /** Esta versão não inclui nenhum cliente de serviço externo. */
    val remoteServiceAvailable: Boolean = false,
)

/** Usado pelas telas de Configurações e de Privacidade e permissões. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val access = MutableStateFlow(readAccess())

    val state: StateFlow<SettingsUiState> = combine(container.settings.settings, access) { settings, (usage, media) ->
        SettingsUiState(
            settings = settings,
            databaseInfo = container.malwareDatabase.info,
            hasUsageAccess = usage,
            mediaAccess = media,
            appVersion = container.appVersion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(appVersion = container.appVersion))

    private fun readAccess(): Pair<Boolean, MediaAccess> =
        container.usageStats.hasUsageAccess() to container.storageAnalyzer.mediaAccess()

    fun refreshPermissions() {
        access.value = readAccess()
    }

    fun setThemeMode(mode: ThemeMode) = container.settings.update { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = container.settings.update { it.copy(dynamicColor = enabled) }

    fun setUnusedThreshold(days: Int) = container.settings.update { it.copy(unusedThresholdDays = days) }

    fun setHashSystemApps(enabled: Boolean) = container.settings.update { it.copy(hashSystemApps = enabled) }

    fun setRemoteConsent(enabled: Boolean) = container.settings.update { it.copy(remoteLookupConsent = enabled) }

    fun setDemoMode(enabled: Boolean) {
        container.settings.update { it.copy(demoMode = enabled) }
        if (!enabled) {
            // Remove itens fictícios dos resultados guardados na sessão.
            container.session.lastScan.value?.let { report ->
                container.session.setScan(report.copy(assessments = report.assessments.filterNot { it.profile.isDemo }))
            }
            container.session.lastStorage.value?.let { report ->
                val demoIds = report.allItems().filter { it.source == FileSource.DEMO }.map { it.id }.toSet()
                fun List<com.cleanguard.app.domain.storage.FileItem>.clean() = filterNot { it.id in demoIds }
                container.session.setStorage(
                    report.copy(
                        largeFiles = report.largeFiles.clean(),
                        downloads = report.downloads.clean(),
                        temporaryFiles = report.temporaryFiles.clean(),
                        largeVideos = report.largeVideos.clean(),
                        largeAudios = report.largeAudios.clean(),
                        largeDocuments = report.largeDocuments.clean(),
                        duplicateGroups = report.duplicateGroups.filter { g -> g.files.none { it.id in demoIds } },
                    ),
                )
            }
        }
    }
}
