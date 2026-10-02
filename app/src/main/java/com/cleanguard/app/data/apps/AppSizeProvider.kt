package com.cleanguard.app.data.apps

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import com.cleanguard.app.domain.apps.AppStorage
import java.io.File

/**
 * Tamanho dos apps.
 *  - Sempre: soma dos arquivos APK (base + splits), que são legíveis por qualquer app.
 *  - Com "Acesso ao uso": tamanho real via StorageStatsManager (app + dados + cache).
 */
class AppSizeProvider(context: Context) {

    private val storageStats: StorageStatsManager? = context.getSystemService(StorageStatsManager::class.java)

    fun apkSize(ai: ApplicationInfo): Long {
        val paths = buildList {
            ai.sourceDir?.let { add(it) }
            ai.splitSourceDirs?.let { addAll(it) }
        }
        return paths.sumOf { path -> runCatching { File(path).length() }.getOrDefault(0L) }
    }

    /** Retorna null sem permissão ou se o sistema não informar. */
    fun storage(ai: ApplicationInfo): AppStorage? {
        val ssm = storageStats ?: return null
        return try {
            val stats = ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, Process.myUserHandle())
            val cache = stats.cacheBytes
            AppStorage(appBytes = stats.appBytes, dataBytes = stats.dataBytes, cacheBytes = cache)
        } catch (e: SecurityException) {
            null
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: java.io.IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
