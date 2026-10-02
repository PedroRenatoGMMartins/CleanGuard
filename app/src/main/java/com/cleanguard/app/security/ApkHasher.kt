package com.cleanguard.app.security

import android.content.pm.ApplicationInfo
import com.cleanguard.app.core.util.HashUtils
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Calcula o SHA-256 do APK base de um app. O arquivo base.apk dos apps instalados é legível por
 * qualquer app (é assim que o sistema o executa), então não há acesso a dados privados.
 * Resultados ficam em cache em memória até o app ser atualizado.
 */
class ApkHasher(
    private val maxApkBytes: Long = DEFAULT_MAX_APK_BYTES,
) {
    private val cache = ConcurrentHashMap<String, String>()

    fun hash(ai: ApplicationInfo, lastUpdateTime: Long): String? {
        val path = ai.sourceDir ?: return null
        val file = File(path)
        if (!file.canRead()) return null
        val size = file.length()
        if (size <= 0 || size > maxApkBytes) return null

        val key = "${ai.packageName}|$lastUpdateTime|$size"
        cache[key]?.let { return it }
        val hash = try {
            FileInputStream(file).use { HashUtils.sha256(it) }
        } catch (e: Exception) {
            return null
        }
        cache[key] = hash
        return hash
    }

    companion object {
        /** APKs gigantes (jogos) são pulados para a verificação não demorar demais. */
        const val DEFAULT_MAX_APK_BYTES: Long = 1_500_000_000L
    }
}
