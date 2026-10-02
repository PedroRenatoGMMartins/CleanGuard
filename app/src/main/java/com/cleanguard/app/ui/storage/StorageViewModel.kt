package com.cleanguard.app.ui.storage

import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.data.storage.DeleteSession
import com.cleanguard.app.data.storage.DeleteStep
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.domain.apps.InstalledApp
import com.cleanguard.app.domain.selection.Selection
import com.cleanguard.app.domain.storage.DeviceStorage
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.MediaAccess
import com.cleanguard.app.domain.storage.StorageCalculator
import com.cleanguard.app.domain.storage.StorageReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StorageTab(val label: String) {
    LARGE("Arquivos grandes"),
    DOWNLOADS("Downloads"),
    TEMPORARY("Temporários"),
    DUPLICATES("Imagens duplicadas"),
    VIDEOS("Vídeos grandes"),
    AUDIOS("Áudios grandes"),
    DOCUMENTS("Documentos grandes"),
    FOLDERS("Pastas escolhidas"),
    CACHE("Cache"),
}

data class StorageUiState(
    val device: DeviceStorage? = null,
    val mediaAccess: MediaAccess = MediaAccess.NONE,
    val report: StorageReport? = null,
    val analyzing: Boolean = false,
    val tab: StorageTab = StorageTab.LARGE,
    val selection: Selection<String> = Selection(),
    val deleting: Boolean = false,
    val message: String? = null,
    val demoMode: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val appsByCache: List<InstalledApp> = emptyList(),
    val ownCacheBytes: Long = 0L,
) {
    fun itemsFor(tab: StorageTab): List<FileItem> {
        val r = report ?: return emptyList()
        return when (tab) {
            StorageTab.LARGE -> r.largeFiles
            StorageTab.DOWNLOADS -> r.downloads
            StorageTab.TEMPORARY -> r.temporaryFiles
            StorageTab.DUPLICATES -> r.duplicateGroups.flatMap { it.files }
            StorageTab.VIDEOS -> r.largeVideos
            StorageTab.AUDIOS -> r.largeAudios
            StorageTab.DOCUMENTS -> r.largeDocuments
            StorageTab.FOLDERS -> r.folderItems
            StorageTab.CACHE -> emptyList()
        }
    }

    val selectedItems: List<FileItem>
        get() = report?.allItems()?.filter { selection.isSelected(it.id) }.orEmpty()

    val selectedBytes: Long get() = StorageCalculator.totalBytes(selectedItems)
}

class StorageViewModel(private val container: AppContainer) : ViewModel() {

    private val local = MutableStateFlow(
        StorageUiState(report = container.session.lastStorage.value),
    )

    val state: StateFlow<StorageUiState> = combine(
        local,
        container.installedApps.snapshot,
        container.settings.settings,
    ) { s, snapshot, settings ->
        s.copy(
            demoMode = settings.demoMode,
            hasUsageAccess = snapshot?.hasUsageAccess == true,
            appsByCache = snapshot?.apps.orEmpty()
                .filter { (it.storage?.cacheBytes ?: 0L) > 0L }
                .sortedByDescending { it.storage?.cacheBytes ?: 0L }
                .take(20),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** IntentSender da confirmação do sistema que a interface deve abrir. */
    private val _pendingConfirmation = MutableStateFlow<IntentSender?>(null)
    val pendingConfirmation: StateFlow<IntentSender?> = _pendingConfirmation.asStateFlow()

    private var deleteSession: DeleteSession? = null

    init {
        refreshAccess()
        viewModelScope.launch {
            container.installedApps.ensureLoaded()
            val own = withContext(Dispatchers.IO) { container.ownCache.sizeBytes() }
            local.update { it.copy(ownCacheBytes = own) }
        }
    }

    fun refreshAccess() {
        viewModelScope.launch {
            val access = container.storageAnalyzer.mediaAccess()
            val device = withContext(Dispatchers.IO) { container.storageAnalyzer.deviceStorage() }
            local.update { it.copy(mediaAccess = access, device = device) }
        }
    }

    fun analyze() {
        if (local.value.analyzing) return
        viewModelScope.launch {
            local.update { it.copy(analyzing = true) }
            try {
                val report = container.storageAnalyzer.analyze(
                    demoMode = container.settings.current.demoMode,
                    folderItems = container.session.folderItems.value,
                )
                container.session.setStorage(report)
                val validIds = report.allItems().map { it.id }.toSet()
                local.update {
                    it.copy(
                        report = report,
                        device = report.device,
                        mediaAccess = report.mediaAccess,
                        selection = it.selection.retainOnly(validIds),
                    )
                }
            } catch (e: Exception) {
                local.update { it.copy(message = "Não foi possível concluir a análise: ${e.message ?: "erro desconhecido"}") }
            } finally {
                local.update { it.copy(analyzing = false) }
            }
        }
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                container.treeScanner.persistAccess(uri)
                container.treeScanner.scan(uri)
            }
            container.session.addFolderItems(items)
            local.update { it.copy(tab = StorageTab.FOLDERS, message = "${items.size} arquivo(s) lido(s) da pasta escolhida.") }
            analyze()
        }
    }

    fun setTab(tab: StorageTab) = local.update { it.copy(tab = tab) }

    fun toggle(item: FileItem) {
        if (!item.canDelete) return
        local.update { it.copy(selection = it.selection.toggle(item.id)) }
    }

    fun selectAll(items: List<FileItem>) {
        local.update { s -> s.copy(selection = s.selection.selectAll(items, { it.id }) { it.canDelete }) }
    }

    fun clearSelection() = local.update { it.copy(selection = it.selection.clear()) }

    /** Seleciona as cópias sugeridas, mantendo o arquivo mais antigo de cada grupo. */
    fun selectSuggestedDuplicates() {
        val suggested = local.value.report?.duplicateGroups?.flatMap { it.suggestedToDelete }.orEmpty()
        selectAll(suggested)
    }

    fun deleteSelected() {
        val items = state.value.selectedItems.filter { it.canDelete }
        if (items.isEmpty() || local.value.deleting) return
        viewModelScope.launch {
            local.update { it.copy(deleting = true) }
            val step = withContext(Dispatchers.IO) { container.fileDeleter.start(items) }
            handle(step)
        }
    }

    fun onSystemConfirmationResult(approved: Boolean) {
        val session = deleteSession ?: return
        viewModelScope.launch {
            val step = withContext(Dispatchers.IO) { container.fileDeleter.onSystemConfirmationResult(session, approved) }
            handle(step)
        }
    }

    fun consumePendingConfirmation() {
        _pendingConfirmation.value = null
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun clearOwnCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { container.ownCache.clear() }
            val remaining = withContext(Dispatchers.IO) { container.ownCache.sizeBytes() }
            local.update { it.copy(ownCacheBytes = remaining, message = "Cache do CleanGuard limpo: ${Formatters.bytes(freed)} liberados.") }
        }
    }

    private fun handle(step: DeleteStep) {
        when (step) {
            is DeleteStep.NeedsSystemConfirmation -> {
                deleteSession = step.session
                _pendingConfirmation.value = step.intentSender
            }
            is DeleteStep.Finished -> {
                deleteSession = null
                val deleted = step.deletedIds
                container.session.removeFiles(deleted)
                val updatedReport = local.value.report?.let { removeFromReport(it, deleted) }
                updatedReport?.let { container.session.setStorage(it) }
                val msg = buildString {
                    if (deleted.isNotEmpty()) append("${deleted.size} arquivo(s) excluído(s). ")
                    if (step.failedIds.isNotEmpty()) append("${step.failedIds.size} não puderam ser excluídos. ")
                    if (step.cancelled) append("Exclusão cancelada na confirmação do sistema.")
                    if (isEmpty()) append("Nada foi excluído.")
                }.trim()
                local.update {
                    it.copy(
                        report = updatedReport,
                        deleting = false,
                        selection = it.selection.retainOnly(it.selection.keys - deleted),
                        message = msg,
                    )
                }
                refreshAccess()
            }
        }
    }

    private fun removeFromReport(report: StorageReport, ids: Set<String>): StorageReport {
        if (ids.isEmpty()) return report
        fun List<FileItem>.without() = filterNot { it.id in ids }
        return report.copy(
            largeFiles = report.largeFiles.without(),
            downloads = report.downloads.without(),
            temporaryFiles = report.temporaryFiles.without(),
            largeVideos = report.largeVideos.without(),
            largeAudios = report.largeAudios.without(),
            largeDocuments = report.largeDocuments.without(),
            folderItems = report.folderItems.without(),
            duplicateGroups = report.duplicateGroups
                .map { it.copy(files = it.files.without()) }
                .filter { it.files.size > 1 },
        )
    }

    companion object {
        /** No Android 11+ o sistema mostra a própria confirmação de exclusão de mídia. */
        val systemConfirmsMediaDeletion: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
