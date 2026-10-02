package com.cleanguard.app.security.model

/** Classificação exibida ao usuário. A ordem importa (do menor para o maior risco). */
enum class RiskLevel(val emoji: String, val label: String, val description: String) {
    SAFE("🟢", "Sem indicadores suspeitos", "Nenhum indicador suspeito foi encontrado."),
    ATTENTION("🟡", "Atenção", "Possui características que merecem atenção, mas podem ser legítimas."),
    SUSPICIOUS("🔴", "Potencialmente suspeito", "Combina vários indicadores de risco ou consta no banco de assinaturas."),
}

/** Origem da instalação, quando o Android informa. */
enum class InstallSource(val label: String) {
    OFFICIAL_STORE("Loja oficial"),
    ALTERNATIVE_STORE("Loja alternativa"),
    SIDELOADED("Instalado manualmente (APK)"),
    ADB("Instalado via computador (ADB)"),
    PREINSTALLED("Pré-instalado"),
    UNKNOWN("Origem desconhecida"),
}

data class InstallOrigin(
    val source: InstallSource,
    val installerPackage: String?,
    /** Nome amigável do instalador (ex.: "Google Play Store"). */
    val installerName: String?,
)

/** Certificado de assinatura do APK. */
data class SignerCertificate(
    val sha256: String,
    val subject: String?,
    val issuer: String?,
    val notAfter: Long?,
) {
    val isDebugCertificate: Boolean
        get() = subject?.contains("CN=Android Debug", ignoreCase = true) == true
}

/**
 * Tudo o que a verificação de segurança sabe sobre um app.
 * Preenchido apenas com informações expostas por APIs oficiais do Android.
 */
data class SecurityProfile(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val hasLauncherEntry: Boolean,
    val targetSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val origin: InstallOrigin,
    val requestedPermissions: List<String>,
    val grantedPermissions: Set<String>,
    val declaresAccessibilityService: Boolean,
    val accessibilityServiceEnabled: Boolean,
    val declaresDeviceAdmin: Boolean,
    val deviceAdminActive: Boolean,
    val declaresNotificationListener: Boolean,
    val certificates: List<SignerCertificate>,
    /** SHA-256 do APK base, quando foi possível lê-lo. */
    val apkSha256: String?,
    val apkSizeBytes: Long,
    val isDemo: Boolean = false,
) {
    val signedWithDebugCertificate: Boolean get() = certificates.any { it.isDebugCertificate }
}

/** Um motivo concreto (e explicado) que contribuiu para a classificação. */
data class RiskFinding(
    val id: String,
    val title: String,
    val explanation: String,
    /** Pontos somados ao score. 0 = apenas informativo. */
    val weight: Int,
)

data class AppRiskAssessment(
    val profile: SecurityProfile,
    val level: RiskLevel,
    val score: Int,
    val findings: List<RiskFinding>,
    val relevantPermissions: List<SensitivePermissionInfo>,
    val malwareMatch: MalwareMatch?,
) {
    /** Resumo em uma frase para a lista de resultados. */
    val summary: String
        get() = when {
            malwareMatch != null -> "Corresponde a uma assinatura conhecida: ${malwareMatch.threatName}."
            findings.none { it.weight > 0 } -> level.description
            else -> findings.filter { it.weight > 0 }.sortedByDescending { it.weight }.first().title
        }
}

/** Permissão sensível com explicação em português. */
data class SensitivePermissionInfo(
    val permission: String,
    val group: String,
    val description: String,
    val weight: Int,
    val granted: Boolean,
)

enum class MatchType { APK_SHA256, CERTIFICATE_SHA256 }

data class MalwareMatch(
    val matchedValue: String,
    val matchType: MatchType,
    val threatName: String,
    val riskLevel: RiskLevel,
    /** Nome da base que encontrou o item (ex.: "Banco local de demonstração"). */
    val source: String,
)

data class ScanReport(
    val startedAt: Long,
    val finishedAt: Long,
    val assessments: List<AppRiskAssessment>,
    val databaseName: String,
    val databaseIsDemo: Boolean,
    val hashedApps: Int,
) {
    val analyzedCount: Int get() = assessments.size
    val suspiciousCount: Int get() = assessments.count { it.level == RiskLevel.SUSPICIOUS }
    val attentionCount: Int get() = assessments.count { it.level == RiskLevel.ATTENTION }
    val flaggedCount: Int get() = suspiciousCount + attentionCount
    val cleanCount: Int get() = assessments.count { it.level == RiskLevel.SAFE }

    /** Itens sinalizados, do mais grave para o menos grave. */
    val flagged: List<AppRiskAssessment>
        get() = assessments.filter { it.level != RiskLevel.SAFE }
            .sortedWith(compareByDescending<AppRiskAssessment> { it.level.ordinal }.thenByDescending { it.score })

    fun find(packageName: String): AppRiskAssessment? =
        assessments.firstOrNull { it.profile.packageName == packageName }
}
