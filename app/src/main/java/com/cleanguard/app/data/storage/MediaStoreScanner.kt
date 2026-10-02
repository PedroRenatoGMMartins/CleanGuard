package com.cleanguard.app.data.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.cleanguard.app.core.util.HashUtils
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.domain.storage.StorageCalculator

/**
 * Lê arquivos de mídia via MediaStore (API oficial).
 *
 * Limitação do Android 10+: com as permissões de mídia, só fotos, vídeos e áudios (de qualquer
 * pasta, inclusive Download) ficam visíveis. PDFs/ZIPs/APKs de outros apps NÃO aparecem aqui —
 * para eles o usuário pode escolher uma pasta (ver [DocumentTreeScanner]).
 */
class MediaStoreScanner(context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    fun scan(maxItems: Int = MAX_ITEMS): List<FileItem> {
        val collection = MediaStore.Files.getContentUri(VOLUME_EXTERNAL)
        val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        @Suppress("DEPRECATION")
        val pathColumn = if (useRelativePath) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            pathColumn,
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"
        val result = ArrayList<FileItem>()

        resolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val pathCol = c.getColumnIndexOrThrow(pathColumn)

            while (c.moveToNext() && result.size < maxItems) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: continue
                val size = c.getLong(sizeCol)
                val mime = c.getString(mimeCol)
                val mediaType = c.getInt(typeCol)
                val rawPath = c.getString(pathCol).orEmpty()
                val location = if (useRelativePath) rawPath else legacyLocation(rawPath)
                result += FileItem(
                    id = contentUriFor(mediaType, id).toString(),
                    name = name,
                    location = location,
                    sizeBytes = size,
                    mimeType = mime,
                    category = StorageCalculator.categorize(name, mime, location),
                    dateModified = c.getLong(dateCol) * 1000L,
                    source = FileSource.MEDIA_STORE,
                )
            }
        }
        return result
    }

    /** SHA-256 do conteúdo (usado para confirmar imagens duplicadas). */
    fun hashOf(item: FileItem): String? = try {
        resolver.openInputStream(Uri.parse(item.id))?.use { HashUtils.sha256(it) }
    } catch (e: Exception) {
        null
    }

    /**
     * URIs das coleções específicas (Images/Video/Audio) — exigidas por
     * MediaStore.createDeleteRequest no Android 11+.
     */
    private fun contentUriFor(mediaType: Int, id: Long): Uri {
        val base = when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri(VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(base, id)
    }

    /** Android 8/9: converte "/storage/emulated/0/DCIM/Camera/x.jpg" em "DCIM/Camera/". */
    private fun legacyLocation(fullPath: String): String {
        val folder = fullPath.substringBeforeLast('/', "")
        val relative = folder.replace(Regex("^/storage/emulated/\\d+/?"), "")
        return if (relative.isEmpty()) "/" else "$relative/"
    }

    private companion object {
        const val VOLUME_EXTERNAL = "external"
        const val MAX_ITEMS = 20_000
    }
}
