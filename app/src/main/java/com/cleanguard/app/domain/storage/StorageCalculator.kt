package com.cleanguard.app.domain.storage

/**
 * Regras e cálculos de armazenamento. Código puro (testável na JVM).
 */
object StorageCalculator {

    const val MB: Long = 1_000_000L

    /** Limites usados para considerar um arquivo "grande". */
    const val LARGE_FILE_BYTES: Long = 100 * MB
    const val LARGE_VIDEO_BYTES: Long = 50 * MB
    const val LARGE_AUDIO_BYTES: Long = 10 * MB
    const val LARGE_DOCUMENT_BYTES: Long = 10 * MB

    /** Limite de tamanho para calcular hash ao procurar imagens duplicadas. */
    const val MAX_DUPLICATE_HASH_BYTES: Long = 50 * MB

    private val TEMP_EXTENSIONS = setOf(
        "tmp", "temp", "part", "partial", "crdownload", "download", "bak", "old", "log", "dmp", "thumbdata",
    )
    private val TEMP_NAMES = setOf("thumbs.db", ".ds_store", "desktop.ini")
    private val DOC_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "rtf", "csv", "epub",
    )
    private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

    fun usedFraction(totalBytes: Long, freeBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        val used = (totalBytes - freeBytes).coerceIn(0L, totalBytes)
        return used.toFloat() / totalBytes.toFloat()
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    fun isTemporary(name: String, location: String = ""): Boolean {
        val lower = name.lowercase()
        if (lower in TEMP_NAMES) return true
        if (lower.startsWith("~$") || lower.startsWith(".~")) return true
        if (extensionOf(lower) in TEMP_EXTENSIONS) return true
        return location.contains(".thumbnails", ignoreCase = true) ||
            location.contains("/cache/", ignoreCase = true) ||
            location.contains("/.temp", ignoreCase = true)
    }

    fun categorize(name: String, mimeType: String?, location: String = ""): FileCategory {
        if (isTemporary(name, location)) return FileCategory.TEMPORARY
        val mime = mimeType?.lowercase().orEmpty()
        val ext = extensionOf(name)
        return when {
            mime.startsWith("image/") -> FileCategory.IMAGE
            mime.startsWith("video/") -> FileCategory.VIDEO
            mime.startsWith("audio/") -> FileCategory.AUDIO
            mime == "application/vnd.android.package-archive" || ext == "apk" || ext == "apks" || ext == "xapk" ->
                FileCategory.APK
            ext in ARCHIVE_EXTENSIONS || mime == "application/zip" -> FileCategory.ARCHIVE
            ext in DOC_EXTENSIONS || mime == "application/pdf" || mime.startsWith("text/") -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    fun isLarge(item: FileItem): Boolean = when (item.category) {
        FileCategory.VIDEO -> item.sizeBytes >= LARGE_VIDEO_BYTES
        FileCategory.AUDIO -> item.sizeBytes >= LARGE_AUDIO_BYTES
        FileCategory.DOCUMENT -> item.sizeBytes >= LARGE_DOCUMENT_BYTES
        else -> item.sizeBytes >= LARGE_FILE_BYTES
    }

    /** Agrupa por tamanho: só arquivos com o mesmo tamanho podem ser idênticos. */
    fun duplicateCandidates(files: List<FileItem>): List<List<FileItem>> =
        files.asSequence()
            .filter { it.sizeBytes in 1..MAX_DUPLICATE_HASH_BYTES }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }
            .toList()

    /**
     * Confirma duplicatas comparando o SHA-256 do conteúdo.
     * @param hashOf retorna o hash do arquivo ou null se não for possível lê-lo.
     */
    fun groupDuplicates(files: List<FileItem>, hashOf: (FileItem) -> String?): List<DuplicateGroup> =
        duplicateCandidates(files)
            .flatMap { sameSize ->
                sameSize.mapNotNull { file -> hashOf(file)?.let { it to file } }
                    .groupBy({ it.first }, { it.second })
                    .filter { it.value.size > 1 }
                    .map { (hash, group) -> DuplicateGroup(hash, group) }
            }
            .sortedByDescending { it.wastedBytes }

    /** Espaço desperdiçado por um grupo de duplicatas (todas as cópias menos uma). */
    fun duplicateWaste(files: List<FileItem>): Long {
        if (files.size < 2) return 0L
        return files.sumOf { it.sizeBytes } - files.maxOf { it.sizeBytes }
    }

    /** Soma de bytes contando cada arquivo uma única vez. */
    fun totalBytes(files: Collection<FileItem>): Long =
        files.distinctBy { it.id }.sumOf { it.sizeBytes }

    fun reclaimableEstimate(report: StorageReport): Long {
        val candidates = (report.temporaryFiles + report.duplicateGroups.flatMap { it.suggestedToDelete })
            .distinctBy { it.id }
        return totalBytes(candidates)
    }

    /**
     * Estimativa do painel: apps não utilizados + cache de apps (quando disponível) + temporários/duplicatas.
     * Sempre exibida como "estimativa": nada é apagado sem confirmação.
     */
    fun dashboardReclaimable(unusedAppsBytes: Long, appCacheBytes: Long, storageReclaimable: Long): Long =
        unusedAppsBytes.coerceAtLeast(0) + appCacheBytes.coerceAtLeast(0) + storageReclaimable.coerceAtLeast(0)

    const val LIST_LIMIT = 200

    /** Monta o relatório a partir dos arquivos visíveis e das duplicatas já confirmadas. */
    fun buildReport(
        device: DeviceStorage,
        access: MediaAccess,
        files: List<FileItem>,
        duplicateGroups: List<DuplicateGroup>,
        now: Long,
    ): StorageReport {
        val all = files.distinctBy { it.id }
        fun List<FileItem>.top() = sortedByDescending { it.sizeBytes }.take(LIST_LIMIT)
        return StorageReport(
            device = device,
            mediaAccess = access,
            scannedFiles = all.size,
            largeFiles = all.filter { it.sizeBytes >= LARGE_FILE_BYTES }.top(),
            downloads = all.filter { it.isDownload }.top(),
            temporaryFiles = all.filter { it.category == FileCategory.TEMPORARY }.top(),
            largeVideos = all.filter { it.category == FileCategory.VIDEO && isLarge(it) }.top(),
            largeAudios = all.filter { it.category == FileCategory.AUDIO && isLarge(it) }.top(),
            largeDocuments = all.filter { it.category == FileCategory.DOCUMENT && isLarge(it) }.top(),
            duplicateGroups = duplicateGroups,
            folderItems = all.filter { it.source == FileSource.DOCUMENT_TREE }.top(),
            generatedAt = now,
        )
    }
}
