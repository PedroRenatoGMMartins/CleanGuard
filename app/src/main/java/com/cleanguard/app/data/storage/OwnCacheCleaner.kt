package com.cleanguard.app.data.storage

import android.content.Context
import java.io.File

/**
 * Limpa o cache do PRÓPRIO CleanGuard. O Android 6+ não permite que apps comuns limpem o cache
 * de outros apps (a permissão CLEAR_APP_CACHE é exclusiva do sistema); para eles, o CleanGuard
 * abre a tela oficial "Informações do app", onde o usuário toca em "Limpar cache".
 */
class OwnCacheCleaner(private val context: Context) {

    fun sizeBytes(): Long = cacheDirs().sumOf { dir -> dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }

    /** @return bytes liberados. */
    fun clear(): Long {
        val before = sizeBytes()
        cacheDirs().forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
        return (before - sizeBytes()).coerceAtLeast(0L)
    }

    private fun cacheDirs(): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir).filter { it.exists() }
}
