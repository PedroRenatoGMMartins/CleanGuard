package com.cleanguard.app.data.storage

import android.annotation.TargetApi
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource

/** Próximo passo de uma exclusão. */
sealed interface DeleteStep {
    /** O Android precisa mostrar a confirmação dele. A interface deve lançar o [intentSender]. */
    data class NeedsSystemConfirmation(val intentSender: IntentSender, val session: DeleteSession) : DeleteStep

    data class Finished(val deletedIds: Set<String>, val failedIds: Set<String>, val cancelled: Boolean) : DeleteStep
}

/** Estado de uma exclusão em andamento (pode envolver várias confirmações do sistema no Android 10). */
class DeleteSession internal constructor() {
    internal val deleted = mutableSetOf<String>()
    internal val failed = mutableSetOf<String>()
    internal val queue = ArrayDeque<FileItem>()
    internal var awaiting: List<FileItem> = emptyList()
    internal var retryAfterApproval = false
    internal var cancelled = false

    internal fun finished() = DeleteStep.Finished(deleted.toSet(), failed.toSet(), cancelled)
}

/**
 * Exclusão de arquivos SEMPRE com confirmação:
 *  1. O CleanGuard mostra nome, local, tamanho e tipo e pede confirmação (na interface).
 *  2. Para mídia no Android 11+, o próprio sistema mostra outra confirmação (MediaStore.createDeleteRequest).
 *  3. No Android 10, cada item de outro app gera uma confirmação do sistema (RecoverableSecurityException).
 *  4. Para pastas escolhidas pelo usuário (SAF), usa DocumentsContract.deleteDocument.
 */
class FileDeleter(context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    fun start(items: List<FileItem>): DeleteStep {
        val session = DeleteSession()
        val unique = items.distinctBy { it.id }

        // Itens do modo demonstração não existem de verdade: só saem da lista.
        unique.filter { it.source == FileSource.DEMO }.forEach { session.deleted += it.id }

        unique.filter { it.source == FileSource.DOCUMENT_TREE }.forEach { item ->
            val ok = runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(item.id)) }.getOrDefault(false)
            if (ok) session.deleted += item.id else session.failed += item.id
        }

        val media = unique.filter { it.source == FileSource.MEDIA_STORE }
        if (media.isEmpty()) return session.finished()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = runCatching { createDeleteRequest(media) }.getOrNull()
            if (sender == null) {
                media.forEach { session.failed += it.id }
                return session.finished()
            }
            session.awaiting = media
            session.retryAfterApproval = false
            return DeleteStep.NeedsSystemConfirmation(sender, session)
        }

        session.queue.addAll(media)
        return continueQueue(session)
    }

    /** Chamar com o resultado da tela de confirmação do sistema. */
    fun onSystemConfirmationResult(session: DeleteSession, approved: Boolean): DeleteStep {
        val awaiting = session.awaiting
        session.awaiting = emptyList()
        if (!approved) {
            session.cancelled = true
            session.queue.clear()
            return session.finished()
        }
        if (session.retryAfterApproval) {
            // Android 10: depois da aprovação, a exclusão é tentada de novo.
            awaiting.forEach { deleteDirect(it, session) }
        } else {
            // Android 11+: o próprio sistema já excluiu os itens aprovados.
            awaiting.forEach { session.deleted += it.id }
        }
        return continueQueue(session)
    }

    private fun continueQueue(session: DeleteSession): DeleteStep {
        while (session.queue.isNotEmpty()) {
            val item = session.queue.removeFirst()
            try {
                val rows = resolver.delete(Uri.parse(item.id), null, null)
                if (rows > 0) session.deleted += item.id else session.failed += item.id
            } catch (e: SecurityException) {
                val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) recoverableSender(e) else null
                if (sender != null) {
                    session.awaiting = listOf(item)
                    session.retryAfterApproval = true
                    return DeleteStep.NeedsSystemConfirmation(sender, session)
                }
                session.failed += item.id
            } catch (e: Exception) {
                session.failed += item.id
            }
        }
        return session.finished()
    }

    private fun deleteDirect(item: FileItem, session: DeleteSession) {
        val rows = runCatching { resolver.delete(Uri.parse(item.id), null, null) }.getOrDefault(0)
        if (rows > 0) session.deleted += item.id else session.failed += item.id
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun createDeleteRequest(items: List<FileItem>): IntentSender =
        MediaStore.createDeleteRequest(resolver, items.map { Uri.parse(it.id) }).intentSender

    @TargetApi(Build.VERSION_CODES.Q)
    private fun recoverableSender(e: SecurityException): IntentSender? =
        (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
}
