package com.cleanguard.app.domain.apps

import java.util.concurrent.TimeUnit

/** Indicador de frequência de uso exibido na lista de apps. */
enum class UsageLevel(val label: String) {
    UNKNOWN("Uso desconhecido"),
    INACTIVE("Sem uso recente"),
    LOW("Pouco usado"),
    MEDIUM("Uso moderado"),
    HIGH("Muito usado"),
}

/**
 * Regras de "app não utilizado" e de frequência de uso. Código puro para testes na JVM.
 *
 * Observação: o Android só guarda histórico de uso por um período limitado (varia por
 * fabricante/versão). "Sem registro" significa "sem uso no histórico disponível".
 */
class AppUsageClassifier(
    private val unusedThresholdDays: Int = DEFAULT_THRESHOLD_DAYS,
) {

    fun usageLevel(app: InstalledApp, now: Long): UsageLevel {
        val usage = app.usage ?: return UsageLevel.UNKNOWN
        if (isUnused(app, now)) return UsageLevel.INACTIVE
        val fg = usage.foregroundTimeLast30DaysMs
        return when {
            fg >= HIGH_USAGE_MS -> UsageLevel.HIGH
            fg >= MEDIUM_USAGE_MS -> UsageLevel.MEDIUM
            else -> UsageLevel.LOW
        }
    }

    /**
     * Um app é considerado "não utilizado" quando:
     *  - foi instalado pelo usuário (apps do sistema não podem ser desinstalados normalmente);
     *  - temos dados de uso (permissão concedida ou modo demo);
     *  - foi instalado há mais tempo que o limite; e
     *  - a última utilização é mais antiga que o limite (ou não há registro).
     */
    fun isUnused(app: InstalledApp, now: Long): Boolean {
        if (app.isSystemApp) return false
        val usage = app.usage ?: return false
        val threshold = TimeUnit.DAYS.toMillis(unusedThresholdDays.toLong())
        val installedLongAgo = app.firstInstallTime > 0 && now - app.firstInstallTime >= threshold
        if (!installedLongAgo) return false
        val reference = maxOf(usage.lastTimeUsed, 0L)
        return reference == 0L || now - reference >= threshold
    }

    fun daysSinceLastUse(app: InstalledApp, now: Long): Long? {
        val last = app.usage?.lastTimeUsed ?: return null
        if (last <= 0L) return null
        return TimeUnit.MILLISECONDS.toDays(now - last)
    }

    companion object {
        const val DEFAULT_THRESHOLD_DAYS = 30
        val HIGH_USAGE_MS: Long = TimeUnit.HOURS.toMillis(2)
        val MEDIUM_USAGE_MS: Long = TimeUnit.MINUTES.toMillis(10)
    }
}
