package com.cleanguard.app.domain.apps

/** Informações de um aplicativo instalado, obtidas apenas por APIs públicas do Android. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val hasLauncherEntry: Boolean,
    val isEnabled: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val versionName: String?,
    val versionCode: Long,
    val targetSdk: Int,
    /** Soma do APK base e dos APKs divididos (splits). Sempre disponível. */
    val apkSizeBytes: Long,
    /** Tamanho real (app + dados + cache). Só disponível com "Acesso ao uso" concedido. */
    val storage: AppStorage?,
    /** Dados de uso. Só disponível com "Acesso ao uso" concedido. */
    val usage: AppUsage?,
    val installerPackage: String?,
    /** Apps fictícios do modo de demonstração (não existem no aparelho). */
    val isDemo: Boolean = false,
) {
    /** Melhor estimativa de espaço ocupado. */
    val totalSizeBytes: Long get() = storage?.totalBytes ?: apkSizeBytes

    /** true quando só sabemos o tamanho do APK (sem dados e cache). */
    val isSizeApproximate: Boolean get() = storage == null

    val isUserApp: Boolean get() = !isSystemApp
}

data class AppStorage(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long get() = appBytes + dataBytes
}

data class AppUsage(
    /** Última utilização conhecida pelo sistema (0 = nenhum registro no histórico disponível). */
    val lastTimeUsed: Long,
    /** Tempo em primeiro plano nos últimos 30 dias. */
    val foregroundTimeLast30DaysMs: Long,
)
