package com.cleanguard.app.data.storage

import android.content.Context
import com.cleanguard.app.demo.DemoData
import com.cleanguard.app.domain.storage.FileCategory
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.domain.storage.MediaAccess
import com.cleanguard.app.domain.storage.StorageCalculator
import com.cleanguard.app.domain.storage.StorageReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Orquestra a análise de armazenamento usando apenas o que o Android permite ler. */
class StorageAnalyzer(
    private val context: Context,
    private val deviceStorage: DeviceStorageProvider,
    private val mediaScanner: MediaStoreScanner,
    private val treeScanner: DocumentTreeScanner,
) {

    fun mediaAccess(): MediaAccess = MediaPermissions.access(context)

    fun deviceStorage() = deviceStorage.read()

    suspend fun analyze(demoMode: Boolean, folderItems: List<FileItem>): StorageReport = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val access = MediaPermissions.access(context)
        val mediaFiles = if (access != MediaAccess.NONE) {
            runCatching { mediaScanner.scan() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val demoFiles = if (demoMode) DemoData.files(now) else emptyList()
        val all = (mediaFiles + folderItems + demoFiles).distinctBy { it.id }

        val imageCandidates = StorageCalculator
            .duplicateCandidates(all.filter { it.category == FileCategory.IMAGE })
            .flatten()
            .take(MAX_FILES_TO_HASH)
        val duplicates = StorageCalculator.groupDuplicates(imageCandidates) { hashFor(it) }

        StorageCalculator.buildReport(deviceStorage.read(), access, all, duplicates, now)
    }

    private fun hashFor(item: FileItem): String? = when (item.source) {
        FileSource.DEMO -> DemoData.demoFileHash(item)
        FileSource.MEDIA_STORE -> mediaScanner.hashOf(item)
        FileSource.DOCUMENT_TREE -> treeScanner.hashOf(item)
    }

    private companion object {
        const val MAX_FILES_TO_HASH = 400
    }
}
