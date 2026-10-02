package com.cleanguard.app.domain.apps

import java.text.Collator
import java.util.Locale

enum class AppFilter(val label: String) {
    ALL("Todos"),
    UNUSED("Não utilizados recentemente"),
    LARGEST("Maior tamanho"),
    OLDEST("Mais antigos"),
    USER("Do usuário"),
    SYSTEM("Do sistema");

    companion object {
        fun fromName(name: String?): AppFilter = entries.firstOrNull { it.name == name } ?: ALL
    }
}

/** Filtro, ordenação e busca da lista de apps. Código puro (testável). */
class AppListFilter(
    private val usageClassifier: AppUsageClassifier,
) {
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply {
        strength = Collator.PRIMARY
    }

    fun apply(
        apps: List<InstalledApp>,
        filter: AppFilter,
        now: Long,
        query: String = "",
    ): List<InstalledApp> {
        val searched = if (query.isBlank()) apps else {
            val q = query.trim().lowercase()
            apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
        val byName = Comparator<InstalledApp> { a, b -> collator.compare(a.label, b.label) }
        return when (filter) {
            AppFilter.ALL -> searched.sortedWith(byName)
            AppFilter.UNUSED -> searched
                .filter { usageClassifier.isUnused(it, now) }
                // Nunca registrados primeiro, depois do uso mais antigo para o mais recente.
                .sortedWith(compareBy<InstalledApp> { it.usage?.lastTimeUsed ?: 0L }.thenByDescending { it.totalSizeBytes })
            AppFilter.LARGEST -> searched.sortedByDescending { it.totalSizeBytes }
            // Apps do sistema têm data de instalação de fábrica (não informativa), por isso
            // "Mais antigos" considera apenas apps do usuário.
            AppFilter.OLDEST -> searched.filter { it.isUserApp }.sortedBy { it.firstInstallTime }
            AppFilter.USER -> searched.filter { it.isUserApp }.sortedWith(byName)
            AppFilter.SYSTEM -> searched.filter { it.isSystemApp }.sortedWith(byName)
        }
    }

    companion object {
        /**
         * Somente apps do usuário podem ser desinstalados pelo fluxo oficial.
         * O próprio CleanGuard e apps fictícios do modo demo não são selecionáveis.
         */
        fun canUninstall(app: InstalledApp, selfPackage: String): Boolean =
            !app.isSystemApp && !app.isDemo && app.packageName != selfPackage
    }
}
