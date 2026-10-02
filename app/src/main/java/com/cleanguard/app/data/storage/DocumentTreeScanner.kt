package com.cleanguard.app.data.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.cleanguard.app.core.util.HashUtils
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.domain.storage.StorageCalculator

/**
 * Lê uma pasta escolhida pelo usuário com o Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE).
 * É a alternativa oficial ao acesso amplo a arquivos: o usuário decide qual pasta compartilhar.
 *
 * Limitação do Android 11+: o seletor não permite escolher a raiz do armazenamento, a pasta
 * Download inteira nem Android/data e Android/obb.
 */
class DocumentTreeScanner(context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    /** Mantém a permissão da pasta após reiniciar o app. */
    fun persistAccess(treeUri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun scan(treeUri: Uri, maxItems: Int = MAX_ITEMS, maxDepth: Int = MAX_DEPTH): List<FileItem> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootLabel = labelForDocumentId(rootId)
        val result = ArrayList<FileItem>()
        val queue = ArrayDeque<Triple<String, String, Int>>() // (documentId, caminho legível, profundidade)
        queue.add(Triple(rootId, rootLabel, 0))

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )

        while (queue.isNotEmpty() && result.size < maxItems) {
            val (docId, path, depth) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            try {
                resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext() && result.size < maxItems) {
                        val childId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth + 1 <= maxDepth) queue.add(Triple(childId, "$path$name/", depth + 1))
                            continue
                        }
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val flags = if (c.isNull(5)) 0 else c.getInt(5)
                        result += FileItem(
                            id = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString(),
                            name = name,
                            location = path,
                            sizeBytes = size,
                            mimeType = mime,
                            category = StorageCalculator.categorize(name, mime, path),
                            dateModified = if (c.isNull(4)) 0L else c.getLong(4),
                            source = FileSource.DOCUMENT_TREE,
                            canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                        )
                    }
                }
            } catch (e: Exception) {
                // Pasta ilegível ou removida durante a leitura: segue para as próximas.
            }
        }
        return result
    }

    fun hashOf(item: FileItem): String? = try {
        resolver.openInputStream(Uri.parse(item.id))?.use { HashUtils.sha256(it) }
    } catch (e: Exception) {
        null
    }

    /** "primary:Documents/Notas" -> "Documents/Notas/" */
    fun labelForDocumentId(documentId: String): String {
        val path = documentId.substringAfter(':', "")
        return if (path.isEmpty()) "/" else "${path.trimEnd('/')}/"
    }

    private companion object {
        const val MAX_ITEMS = 10_000
        const val MAX_DEPTH = 8
    }
}
