package com.cleanguard.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Formatação de valores para exibição (pt-BR). Código puro, sem dependência do Android,
 * para poder ser testado na JVM.
 */
object Formatters {

    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    /**
     * Formata bytes usando base 1000 (KB = 1000 bytes), igual às Configurações do Android 8+.
     */
    fun bytes(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1000) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1000 && unitIndex < units.lastIndex) {
            value /= 1000
            unitIndex++
        }
        val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
        return String.format(ptBr, pattern, value, units[unitIndex])
    }

    fun date(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "—"
        return SimpleDateFormat("dd/MM/yyyy", ptBr).format(Date(epochMillis))
    }

    fun dateTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "—"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr).format(Date(epochMillis))
    }

    /** "hoje", "ontem", "há 12 dias", "há 3 meses"... */
    fun relativeDays(epochMillis: Long, now: Long): String {
        if (epochMillis <= 0L) return "nunca registrado"
        val days = TimeUnit.MILLISECONDS.toDays(abs(now - epochMillis))
        return when {
            days == 0L -> "hoje"
            days == 1L -> "ontem"
            days < 30 -> "há $days dias"
            days < 60 -> "há 1 mês"
            days < 365 -> "há ${days / 30} meses"
            days < 730 -> "há 1 ano"
            else -> "há ${days / 365} anos"
        }
    }

    fun duration(millis: Long): String {
        if (millis <= 0L) return "0 min"
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L && minutes == 0L -> "< 1 min"
            hours == 0L -> "$minutes min"
            minutes == 0L -> "$hours h"
            else -> "$hours h $minutes min"
        }
    }

    fun percent(fraction: Float): String {
        val clamped = fraction.coerceIn(0f, 1f)
        return String.format(ptBr, "%.0f%%", clamped * 100)
    }
}
