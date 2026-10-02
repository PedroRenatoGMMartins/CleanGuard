@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleanguard.app.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.core.util.HashUtils
import com.cleanguard.app.core.util.SystemIntents
import com.cleanguard.app.domain.apps.AppUsageClassifier
import com.cleanguard.app.domain.apps.InstalledApp
import com.cleanguard.app.security.SensitivePermissions
import com.cleanguard.app.security.model.AppRiskAssessment
import com.cleanguard.app.security.model.RiskLevel
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.AppIcon
import com.cleanguard.app.ui.components.CgTopBar
import com.cleanguard.app.ui.components.EmptyState
import com.cleanguard.app.ui.components.KeyValueRow
import com.cleanguard.app.ui.components.LoadingState
import com.cleanguard.app.ui.components.RiskBadge
import com.cleanguard.app.ui.components.SectionTitle
import com.cleanguard.app.ui.components.Tag
import com.cleanguard.app.ui.components.rememberUninstallQueue
import com.cleanguard.app.ui.theme.LocalRiskColors

@Composable
fun AppDetailScreen(
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uninstallQueue = rememberUninstallQueue { viewModel.onUninstallFinished() }
    val app = state.app
    val assessment = state.assessment

    Scaffold(topBar = { CgTopBar(app?.label ?: assessment?.profile?.label ?: "Detalhes do aplicativo", onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingState("Carregando…", Modifier.padding(padding))
            state.uninstalled -> EmptyState(
                icon = Icons.Filled.CheckCircle,
                title = "Aplicativo removido",
                message = "${state.packageName} não está mais instalado.",
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderCard(state.packageName, app, assessment, state.assessing)

                // Ações (todas abrem telas oficiais do Android)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canUninstall) {
                        Button(
                            onClick = { uninstallQueue.start(listOf(state.packageName)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Desinstalar aplicativo")
                        }
                    }
                    OutlinedButton(
                        onClick = { SystemIntents.openAppDetails(context, state.packageName) },
                        enabled = app?.isDemo != true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Info do app")
                    }
                }
                if (app?.isDemo != true) {
                    TextButton(onClick = { SystemIntents.start(context, SystemIntents.playStore(state.packageName)) }) {
                        Icon(Icons.Filled.Store, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ver na loja de apps")
                    }
                }

                if (assessment != null && assessment.level != RiskLevel.SAFE) {
                    RemovalGuide(assessment, app, onUninstall = { uninstallQueue.start(listOf(state.packageName)) }, canUninstall = state.canUninstall)
                }

                if (app != null) UsageCard(app, state.thresholdDays, state.now)
                if (assessment != null) {
                    SecurityCard(assessment)
                    PermissionsCard(assessment)
                    IntegrityCard(assessment)
                } else if (!state.assessing) {
                    OutlinedButton(onClick = viewModel::assess, modifier = Modifier.fillMaxWidth()) { Text("Analisar segurança deste app") }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun HeaderCard(packageName: String, app: InstalledApp?, assessment: AppRiskAssessment?, assessing: Boolean) {
    val label = app?.label ?: assessment?.profile?.label ?: packageName
    val isDemo = app?.isDemo ?: assessment?.profile?.isDemo ?: false
    val isSystem = app?.isSystemApp ?: assessment?.profile?.isSystemApp ?: false
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName, isDemo, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Text(packageName, style = MaterialTheme.typography.bodySmall)
                Text("Versão ${app?.versionName ?: assessment?.profile?.versionName ?: "—"}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                isDemo -> Tag("DEMO (fictício)")
                isSystem -> Tag("App do sistema")
                else -> Tag("Instalado pelo usuário")
            }
            when {
                assessment != null -> RiskBadge(assessment.level)
                assessing -> Text("Avaliando segurança…", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun UsageCard(app: InstalledApp, thresholdDays: Int, now: Long) {
    val classifier = AppUsageClassifier(thresholdDays)
    DetailCard {
        SectionTitle("Uso e armazenamento")
        KeyValueRow(
            "Tamanho",
            Formatters.bytes(app.totalSizeBytes) + if (app.isSizeApproximate) " (somente APK — conceda \"Acesso ao uso\" para o tamanho real)" else "",
        )
        app.storage?.let {
            KeyValueRow("App / Dados / Cache", "${Formatters.bytes(it.appBytes)} / ${Formatters.bytes(it.dataBytes)} / ${Formatters.bytes(it.cacheBytes)}")
        }
        KeyValueRow("Instalado em", Formatters.date(app.firstInstallTime))
        KeyValueRow("Última atualização", Formatters.date(app.lastUpdateTime))
        val usage = app.usage
        KeyValueRow(
            "Última utilização",
            when {
                usage == null -> "Indisponível (sem \"Acesso ao uso\")"
                usage.lastTimeUsed <= 0L -> "Sem registro no histórico do Android"
                else -> "${Formatters.relativeDays(usage.lastTimeUsed, now)} (${Formatters.dateTime(usage.lastTimeUsed)})"
            },
        )
        KeyValueRow(
            "Frequência de uso",
            classifier.usageLevel(app, now).label + (usage?.let { " • ${Formatters.duration(it.foregroundTimeLast30DaysMs)} nos últimos 30 dias" } ?: ""),
        )
    }
}

@Composable
private fun SecurityCard(assessment: AppRiskAssessment) {
    val risk = LocalRiskColors.current
    DetailCard {
        SectionTitle("Análise de segurança")
        Row(verticalAlignment = Alignment.CenterVertically) {
            RiskBadge(assessment.level)
            Spacer(Modifier.width(8.dp))
            Text("Pontuação ${assessment.score}", style = MaterialTheme.typography.labelLarge)
        }
        Text(assessment.level.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        assessment.malwareMatch?.let { match ->
            Spacer(Modifier.size(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = risk.suspiciousContainer, contentColor = risk.onSuspiciousContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Ameaça: ${match.threatName}", style = MaterialTheme.typography.titleSmall)
                    Text("Fonte: ${match.source}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        val (scored, info) = assessment.findings.partition { it.weight > 0 }
        if (scored.isEmpty()) {
            Text("Nenhum indicador suspeito encontrado.", style = MaterialTheme.typography.bodyMedium)
        }
        scored.forEach { f ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = risk.accent(assessment.level), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("${f.title} (+${f.weight})", style = MaterialTheme.typography.titleSmall)
                    Text(f.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        info.forEach { f ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = risk.safe, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(f.title, style = MaterialTheme.typography.titleSmall)
                    Text(f.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(assessment: AppRiskAssessment) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val all = assessment.profile.requestedPermissions
    val informative = SensitivePermissions.relevant(all, assessment.profile.grantedPermissions, includeInformational = true)
    DetailCard {
        SectionTitle("Permissões relevantes")
        if (informative.isEmpty()) {
            Text("Nenhuma permissão sensível solicitada.", style = MaterialTheme.typography.bodyMedium)
        }
        informative.forEach { p ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Tag(
                        if (p.granted) "Concedida" else "Não concedida",
                        color = if (p.granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (p.granted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(p.description, style = MaterialTheme.typography.bodySmall)
                Text(SensitivePermissions.shortName(p.permission), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        TextButton(onClick = { expanded = !expanded }) {
            Text("Todas as permissões solicitadas (${all.size})")
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            all.sorted().forEach {
                Text(
                    "• ${SensitivePermissions.shortName(it)}" + if (it in assessment.profile.grantedPermissions) " ✓" else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun IntegrityCard(assessment: AppRiskAssessment) {
    val p = assessment.profile
    DetailCard {
        SectionTitle("Origem, assinatura e integridade")
        KeyValueRow("Origem da instalação", p.origin.source.label + (p.origin.installerName?.let { " — $it" } ?: ""))
        p.origin.installerPackage?.let { KeyValueRow("Pacote instalador", it, monospace = true) }
        KeyValueRow("Target SDK", p.targetSdk.toString())
        KeyValueRow(
            "SHA-256 do APK",
            p.apkSha256 ?: if (p.isSystemApp) "Não calculado (app do sistema — ative em Configurações)" else "Não foi possível ler o APK",
            monospace = p.apkSha256 != null,
        )
        if (p.certificates.isEmpty()) {
            KeyValueRow("Certificado", "Não disponível")
        }
        p.certificates.forEach { cert ->
            KeyValueRow("Certificado (titular)", cert.subject ?: "—")
            KeyValueRow("SHA-256 do certificado", HashUtils.fingerprint(cert.sha256), monospace = true)
            cert.notAfter?.let { KeyValueRow("Válido até", Formatters.date(it)) }
            if (cert.isDebugCertificate) {
                Text("⚠️ Certificado de depuração (build de teste)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Orientação passo a passo para remover um app sinalizado usando somente telas oficiais. */
@Composable
private fun RemovalGuide(assessment: AppRiskAssessment, app: InstalledApp?, onUninstall: () -> Unit, canUninstall: Boolean) {
    val context = LocalContext.current
    val risk = LocalRiskColors.current
    val p = assessment.profile
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = risk.container(assessment.level), contentColor = risk.onContainer(assessment.level)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Como remover este app com segurança", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Se você não reconhece este app ou não precisa dele, siga os passos. Se o reconhece e confia nele, " +
                    "os indicadores podem ser apenas características legítimas.",
                style = MaterialTheme.typography.bodySmall,
            )
            var step = 1
            if (p.accessibilityServiceEnabled) {
                GuideStep(step++, "Desative o serviço de acessibilidade dele.", "Abrir Acessibilidade") {
                    SystemIntents.start(context, SystemIntents.accessibilitySettings())
                }
            }
            if (p.deviceAdminActive) {
                GuideStep(step++, "Remova-o dos apps administradores do dispositivo (senão a desinstalação fica bloqueada).", "Abrir Segurança") {
                    SystemIntents.start(context, SystemIntents.securitySettings())
                }
            }
            if (canUninstall) {
                GuideStep(step++, "Desinstale pelo diálogo oficial do Android.", "Desinstalar", onUninstall)
            } else if (p.isSystemApp) {
                GuideStep(step++, "Apps do sistema não podem ser desinstalados, mas podem ser desativados.", "Abrir info do app") {
                    SystemIntents.openAppDetails(context, p.packageName)
                }
            } else if (app?.isDemo == true || p.isDemo) {
                GuideStep(step++, "Este é um app fictício do modo demonstração — não há nada a remover.", null, null)
            }
            GuideStep(step, "Depois, rode o Google Play Protect (Play Store > Play Protect) e troque senhas importantes se o app teve acesso a SMS ou à tela.", null, null)
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String, actionLabel: String?, onAction: (() -> Unit)?) {
    Column {
        Text("$number. $text", style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
