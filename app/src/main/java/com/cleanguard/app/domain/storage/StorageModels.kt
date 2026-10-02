package com.cleanguard.app.domain.storage

/** Espaço total e livre do armazenamento interno. */
data class DeviceStorage(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = StorageCalculator.usedFraction(totalBytes, freeBytes)
}

enum class FileCategory(val label: String) {
    IMAGE("Imagem"),
    VIDEO("Vídeo"),
    AUDIO("Áudio"),
    DOCUMENT("Documento"),
    ARCHIVE("Arquivo compactado"),
    APK("Instalador (APK)"),
    TEMPORARY("Temporário"),
    OTHER("Outro"),
}

/** De onde o arquivo foi listado — define como ele pode ser excluído. */
enum class FileSource {
    /** MediaStore (fotos, vídeos, áudios). Exclusão via confirmação do sistema no Android 10+. */
    MEDIA_STORE,

    /** Pasta escolhida pelo usuário via Storage Access Framework. */
    DOCUMENT_TREE,

    /** Item fictício do modo de demonstração. */
    DEMO,
}

data class FileItem(
    /** URI de conteúdo (content://...) — identificador único. */
    val id: String,
    val name: String,
    /** Pasta/local legível ("Download/", "DCIM/Camera/"...). */
    val location: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val category: FileCategory,
    val dateModified: Long,
    val source: FileSource,
    /** Para itens de pasta (SAF): o provedor permite excluir? */
    val canDelete: Boolean = true,
) {
    val isDownload: Boolean
        get() = location.startsWith("Download", ignoreCase = true) ||
            location.contains("/Download/", ignoreCase = true)
}

data class DuplicateGroup(
    val sha256: String,
    val files: List<FileItem>,
) {
    /** Espaço recuperável mantendo uma única cópia. */
    val wastedBytes: Long get() = StorageCalculator.duplicateWaste(files)

    /** Sugestão: manter o arquivo mais antigo (provável original) e apagar as cópias. */
    val suggestedToDelete: List<FileItem>
        get() = files.sortedBy { it.dateModified }.drop(1)
}

/** Nível de acesso à mídia concedido pelo usuário. */
enum class MediaAccess { FULL, PARTIAL, NONE }

data class StorageReport(
    val device: DeviceStorage,
    val mediaAccess: MediaAccess,
    val scannedFiles: Int,
    val largeFiles: List<FileItem>,
    val downloads: List<FileItem>,
    val temporaryFiles: List<FileItem>,
    val largeVideos: List<FileItem>,
    val largeAudios: List<FileItem>,
    val largeDocuments: List<FileItem>,
    val duplicateGroups: List<DuplicateGroup>,
    val folderItems: List<FileItem>,
    val generatedAt: Long,
) {
    /** Estimativa do que pode ser liberado (sem contar itens repetidos entre categorias). */
    val reclaimableEstimateBytes: Long
        get() = StorageCalculator.reclaimableEstimate(this)

    fun allItems(): List<FileItem> =
        (largeFiles + downloads + temporaryFiles + largeVideos + largeAudios + largeDocuments +
            duplicateGroups.flatMap { it.files } + folderItems).distinctBy { it.id }
}
