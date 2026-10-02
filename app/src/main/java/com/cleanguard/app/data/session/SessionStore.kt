package com.cleanguard.app.data.session

import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.StorageReport
import com.cleanguard.app.security.model.ScanReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resultados da sessão atual, mantidos apenas em memória (nada é gravado em disco ou enviado).
 * Permite que o painel mostre o resumo da última verificação e análise.
 */
class SessionStore {
    private val _lastScan = MutableStateFlow<ScanReport?>(null)
    val lastScan: StateFlow<ScanReport?> = _lastScan.asStateFlow()

    private val _lastStorage = MutableStateFlow<StorageReport?>(null)
    val lastStorage: StateFlow<StorageReport?> = _lastStorage.asStateFlow()

    /** Arquivos das pastas escolhidas pelo usuário (SAF) nesta sessão. */
    private val _folderItems = MutableStateFlow<List<FileItem>>(emptyList())
    val folderItems: StateFlow<List<FileItem>> = _folderItems.asStateFlow()

    fun setScan(report: ScanReport?) { _lastScan.value = report }

    fun setStorage(report: StorageReport?) { _lastStorage.value = report }

    fun addFolderItems(items: List<FileItem>) {
        _folderItems.value = (_folderItems.value + items).distinctBy { it.id }
    }

    fun removeFiles(ids: Set<String>) {
        _folderItems.value = _folderItems.value.filterNot { it.id in ids }
    }

    fun clearFolders() { _folderItems.value = emptyList() }
}
