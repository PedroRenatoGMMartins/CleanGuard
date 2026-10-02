package com.cleanguard.app.security

import com.cleanguard.app.security.model.AppRiskAssessment
import com.cleanguard.app.security.model.InstallSource
import com.cleanguard.app.security.model.MalwareMatch
import com.cleanguard.app.security.model.MatchType
import com.cleanguard.app.security.model.RiskFinding
import com.cleanguard.app.security.model.RiskLevel
import com.cleanguard.app.security.model.SecurityProfile
import com.cleanguard.app.security.model.SensitivePermissionInfo

/**
 * Classificador heurístico de risco. Código puro (testável na JVM).
 *
 * Princípios:
 *  - Nenhuma regra isolada transforma um app em "vírus". Permissões sensíveis são comuns em apps legítimos.
 *  - Cada ponto somado gera um [RiskFinding] com explicação para o usuário.
 *  - Apenas uma correspondência no banco de assinaturas leva direto a 🔴.
 *  - Apps do sistema (pré-instalados) só são sinalizados por correspondência no banco — permissões
 *    amplas são normais para eles e não indicam nada.
 */
class RiskClassifier(
    private val attentionThreshold: Int = ATTENTION_THRESHOLD,
    private val suspiciousThreshold: Int = SUSPICIOUS_THRESHOLD,
) {

    fun assess(profile: SecurityProfile, malwareMatch: MalwareMatch?): AppRiskAssessment {
        val findings = mutableListOf<RiskFinding>()
        val relevantPermissions = SensitivePermissions.relevant(profile.requestedPermissions, profile.grantedPermissions)

        if (malwareMatch != null) {
            findings += RiskFinding(
                id = "malware_db",
                title = "Corresponde a uma assinatura do banco de ameaças",
                explanation = "O ${if (malwareMatch.matchType == MatchType.APK_SHA256) "hash SHA-256 do APK" else "certificado de assinatura"} " +
                    "corresponde à entrada \"${malwareMatch.threatName}\" em: ${malwareMatch.source}.",
                weight = 100,
            )
        }

        if (profile.isSystemApp && !profile.isUpdatedSystemApp) {
            findings += RiskFinding(
                id = "system_app",
                title = "App do sistema",
                explanation = "Pré-instalado pelo fabricante. É normal que apps do sistema tenham permissões amplas.",
                weight = 0,
            )
            return build(profile, findings, relevantPermissions, malwareMatch)
        }

        addOriginFindings(profile, findings)
        addPermissionFindings(profile, findings)
        addCapabilityFindings(profile, findings)
        addPackageFindings(profile, findings)
        addCombinationFindings(profile, findings)

        return build(profile, findings, relevantPermissions, malwareMatch)
    }

    private fun build(
        profile: SecurityProfile,
        findings: List<RiskFinding>,
        relevantPermissions: List<SensitivePermissionInfo>,
        malwareMatch: MalwareMatch?,
    ): AppRiskAssessment {
        val score = findings.sumOf { it.weight }
        val level = when {
            malwareMatch != null -> maxOf(RiskLevel.SUSPICIOUS, malwareMatch.riskLevel)
            score >= suspiciousThreshold -> RiskLevel.SUSPICIOUS
            score >= attentionThreshold -> RiskLevel.ATTENTION
            else -> RiskLevel.SAFE
        }
        return AppRiskAssessment(profile, level, score, findings.sortedByDescending { it.weight }, relevantPermissions, malwareMatch)
    }

    private fun addOriginFindings(p: SecurityProfile, out: MutableList<RiskFinding>) {
        when (p.origin.source) {
            InstallSource.SIDELOADED -> out += RiskFinding(
                "sideloaded", "Instalado fora de uma loja oficial",
                "Foi instalado a partir de um arquivo APK (instalador: ${p.origin.installerName ?: "desconhecido"}). " +
                    "Apps fora das lojas oficiais não passam pela análise das lojas.",
                2,
            )
            InstallSource.UNKNOWN -> out += RiskFinding(
                "unknown_origin", "Origem da instalação desconhecida",
                "O Android não informou qual app instalou este aplicativo.",
                2,
            )
            InstallSource.ADB -> out += RiskFinding(
                "adb", "Instalado por um computador (ADB)",
                "Comum para desenvolvedores. Se você não instalou por um computador, merece atenção.",
                1,
            )
            InstallSource.ALTERNATIVE_STORE -> out += RiskFinding(
                "alt_store", "Instalado por loja alternativa",
                "Instalado por ${p.origin.installerName}. Lojas alternativas não são oficiais do fabricante nem do Google.",
                1,
            )
            InstallSource.OFFICIAL_STORE -> out += RiskFinding(
                "official_store", "Instalado por loja oficial",
                "Instalado por ${p.origin.installerName}.",
                0,
            )
            InstallSource.PREINSTALLED -> Unit
        }
    }

    private fun addPermissionFindings(p: SecurityProfile, out: MutableList<RiskFinding>) {
        val groups = SensitivePermissions.sensitiveGroups(p.requestedPermissions)
        if (groups.size >= 3) {
            out += RiskFinding(
                "sensitive_groups",
                "Solicita várias permissões sensíveis",
                "Este aplicativo solicita acesso a ${joinPt(groups.map { it.lowercase() }.take(5))}. " +
                    "Essas permissões podem ser legítimas, mas merecem atenção.",
                2,
            )
        } else if (groups.isNotEmpty()) {
            out += RiskFinding(
                "sensitive_some",
                "Solicita permissões sensíveis",
                "Acesso a ${joinPt(groups.map { it.lowercase() })}. Comum em muitos apps legítimos.",
                0,
            )
        }

        val sensitiveCount = SensitivePermissions.relevant(p.requestedPermissions, emptySet()).size
        if (sensitiveCount >= MANY_PERMISSIONS) {
            out += RiskFinding(
                "many_permissions", "Solicita muitas permissões",
                "Pede $sensitiveCount permissões sensíveis — bem acima do que a maioria dos apps precisa.",
                1,
            )
        }

        val perms = p.requestedPermissions.toSet()
        if (perms.any { it in SensitivePermissions.SMS_PERMISSIONS }) {
            out += RiskFinding(
                "sms", "Pode ler SMS",
                "Pode ler mensagens SMS, onde chegam códigos de verificação de bancos e contas.",
                1,
            )
        }
        if (SensitivePermissions.OVERLAY in perms) {
            out += RiskFinding(
                "overlay", "Pode sobrepor outros apps",
                "Pode desenhar janelas sobre outros apps. Usado por apps de bolha/legenda, mas também para imitar telas de login.",
                1,
            )
        }
        if (SensitivePermissions.INSTALL_PACKAGES in perms) {
            out += RiskFinding(
                "install_packages", "Pode pedir para instalar outros apps",
                "Pode iniciar a instalação de outros APKs (você ainda precisaria confirmar).",
                1,
            )
        }
        if (SensitivePermissions.ALL_FILES in perms) {
            out += RiskFinding(
                "all_files", "Acesso a todos os arquivos",
                "Pode ler e alterar todos os arquivos do armazenamento compartilhado.",
                1,
            )
        }
        if (SensitivePermissions.BACKGROUND_LOCATION in perms) {
            out += RiskFinding(
                "bg_location", "Localização em segundo plano",
                "Pode saber sua localização mesmo quando você não está usando o app.",
                1,
            )
        }
    }

    private fun addCapabilityFindings(p: SecurityProfile, out: MutableList<RiskFinding>) {
        if (p.accessibilityServiceEnabled) {
            out += RiskFinding(
                "accessibility_on", "Serviço de acessibilidade ATIVO",
                "Pode ler o conteúdo da tela e tocar em botões em seu nome. Essencial para apps de acessibilidade, " +
                    "mas muito explorado por golpes. Desative em Configurações > Acessibilidade se não reconhecer.",
                3,
            )
        } else if (p.declaresAccessibilityService) {
            out += RiskFinding(
                "accessibility", "Oferece serviço de acessibilidade",
                "Pode pedir para ler a tela e agir em seu nome, caso você ative o serviço.",
                1,
            )
        }
        if (p.deviceAdminActive) {
            out += RiskFinding(
                "device_admin_on", "Administrador do dispositivo ATIVO",
                "Apps administradores podem bloquear a tela e dificultar a desinstalação. " +
                    "Para remover, desative em Configurações > Segurança > Apps de administração.",
                3,
            )
        } else if (p.declaresDeviceAdmin) {
            out += RiskFinding(
                "device_admin", "Pode pedir para ser administrador",
                "Declara recursos de administração do dispositivo.",
                1,
            )
        }
        if (p.declaresNotificationListener) {
            out += RiskFinding(
                "notification_listener", "Pode ler notificações",
                "Se autorizado, pode ler o conteúdo das suas notificações (incluindo códigos).",
                1,
            )
        }
    }

    private fun addPackageFindings(p: SecurityProfile, out: MutableList<RiskFinding>) {
        if (!p.hasLauncherEntry) {
            out += RiskFinding(
                "no_launcher", "Não aparece na lista de apps",
                "Não tem ícone na tela de aplicativos. Pode ser um complemento legítimo, mas apps maliciosos costumam se esconder assim.",
                2,
            )
        }
        if (p.signedWithDebugCertificate) {
            out += RiskFinding(
                "debug_cert", "Assinado com certificado de depuração",
                "Apps publicados normalmente usam certificado de produção. Certificado de depuração indica build de teste ou reempacotado.",
                2,
            )
        }
        if (p.targetSdk in 1 until LEGACY_TARGET_SDK) {
            out += RiskFinding(
                "legacy_target", "Feito para versões muito antigas do Android",
                "Tem targetSdk ${p.targetSdk}: em versões antigas, permissões eram concedidas sem perguntar ao usuário.",
                2,
            )
        }
        val impersonates = SYSTEM_PREFIXES.any { p.packageName.startsWith(it) }
        if (impersonates && p.origin.source != InstallSource.OFFICIAL_STORE && !p.isSystemApp) {
            out += RiskFinding(
                "impersonation", "Nome de pacote semelhante ao do sistema",
                "O pacote ${p.packageName} usa um prefixo de apps do sistema/Google, mas não veio de uma loja oficial.",
                3,
            )
        }
    }

    private fun addCombinationFindings(p: SecurityProfile, out: MutableList<RiskFinding>) {
        val perms = p.requestedPermissions.toSet()
        val notFromStore = p.origin.source != InstallSource.OFFICIAL_STORE
        val hasAccessibility = p.declaresAccessibilityService || p.accessibilityServiceEnabled
        val hasOverlay = SensitivePermissions.OVERLAY in perms
        val hasSms = perms.any { it in SensitivePermissions.SMS_PERMISSIONS }

        if (hasAccessibility && hasOverlay && (hasSms || SensitivePermissions.INSTALL_PACKAGES in perms)) {
            out += RiskFinding(
                "banking_trojan_pattern", "Combinação de alto risco",
                "Acessibilidade + sobreposição de tela + SMS/instalação de apps é a combinação usada por trojans bancários. " +
                    "Isso não prova que o app é malicioso, mas é um forte motivo para revisar.",
                3,
            )
        }
        if (hasSms && notFromStore) {
            out += RiskFinding(
                "sms_sideloaded", "Leitura de SMS em app fora da loja",
                "Apps instalados fora das lojas oficiais com acesso a SMS podem capturar códigos de verificação.",
                2,
            )
        }
        if (!p.hasLauncherEntry && notFromStore && SensitivePermissions.sensitiveGroups(perms).size >= 2) {
            out += RiskFinding(
                "hidden_sensitive", "App oculto com permissões sensíveis",
                "Sem ícone, instalado fora da loja e com permissões sensíveis.",
                2,
            )
        }
    }

    private fun joinPt(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " e " + items.last()
    }

    companion object {
        const val ATTENTION_THRESHOLD = 3
        const val SUSPICIOUS_THRESHOLD = 7
        const val MANY_PERMISSIONS = 10
        const val LEGACY_TARGET_SDK = 23
        private val SYSTEM_PREFIXES = listOf("com.android.", "com.google.android.", "android.")
    }
}
