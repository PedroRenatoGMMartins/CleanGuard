package com.cleanguard.app.data.apps

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.cleanguard.app.domain.apps.AppUsage
import java.util.concurrent.TimeUnit

/**
 * Dados de uso via UsageStatsManager. Exige "Acesso ao uso" (PACKAGE_USAGE_STATS), que só o
 * usuário pode conceder nas Configurações. Sem ela, os métodos retornam null e a interface
 * explica a limitação.
 */
class UsageStatsProvider(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        // checkOpNoThrow(String, Int, String) existe desde o Android 4.4 e não é depreciado no SDK 36.
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * @return mapa pacote -> uso, ou null sem permissão. Pacotes ausentes do mapa não têm
     * registro de uso no histórico que o sistema mantém.
     */
    fun queryUsage(now: Long = System.currentTimeMillis()): Map<String, AppUsage>? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val longRange = safeQuery(usm, now - TimeUnit.DAYS.toMillis(365), now)
        val lastMonth = safeQuery(usm, now - TimeUnit.DAYS.toMillis(30), now)
        return (longRange.keys + lastMonth.keys).associateWith { pkg ->
            val last = maxOf(lastUsed(longRange[pkg]), lastUsed(lastMonth[pkg]))
            AppUsage(
                lastTimeUsed = last,
                foregroundTimeLast30DaysMs = lastMonth[pkg]?.totalTimeInForeground ?: 0L,
            )
        }
    }

    private fun safeQuery(usm: UsageStatsManager, begin: Long, end: Long): Map<String, UsageStats> =
        try {
            usm.queryAndAggregateUsageStats(begin, end) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

    private fun lastUsed(stats: UsageStats?): Long {
        if (stats == null) return 0L
        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            maxOf(stats.lastTimeUsed, stats.lastTimeVisible)
        } else {
            stats.lastTimeUsed
        }
        // Alguns aparelhos retornam valores inválidos (próximos de 0) para pacotes sem uso.
        return if (value < MIN_VALID_TIMESTAMP) 0L else value
    }

    private companion object {
        /** 01/01/2010 — qualquer valor anterior é tratado como "sem registro". */
        const val MIN_VALID_TIMESTAMP = 1_262_304_000_000L
    }
}
