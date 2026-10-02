@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cleanguard.app.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.security.model.AppRiskAssessment
import com.cleanguard.app.security.model.RiskLevel
import com.cleanguard.app.security.model.ScanReport
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.AppIcon
import com.cleanguard.app.ui.components.CgTopBar
import com.cleanguard.app.ui.components.EmptyState
import com.cleanguard.app.ui.components.RiskBadge
import com.cleanguard.app.ui.components.Tag
import com.cleanguard.app.ui.components.rememberUninstallQueue
import com.cleanguard.app.ui.theme.LocalRiskColors

@Composable
fun ScanResultsScreen(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onStartScan: () -> Unit,
    viewModel: SecurityViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val uninstallQueue = rememberUninstallQueue { viewModel.onUninstallFinished(it) }
    val report = state.lastReport

    Scaffold(topBar = { CgTopBar("Resultados da verificação", onBack = onBack) }) { padding ->
        if (report == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    icon = Icons.Filled.Security,
                    title = "Nenhuma verificação ainda",
                    message = "Faça uma verificação para ver os resultados aqui.",
                )
                Button(onClick = onStartScan) { Text("Verificar agora") }
            }
            return@Scaffold
        }

        val list = if (showAll) {
            report.assessments.sortedWith(compareByDescending<AppRiskAssessment> { it.level.ordinal }.thenBy { it.profile.label.lowercase() })
        } else {
            report.flagged
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SummaryCard(report) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text("Itens sinalizados (${report.flaggedCount})") })
                    FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text("Todos (${report.analyzedCount})") })
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.GppGood,
                        title = "Nenhum indicador suspeito",
                        message = "Nenhum app apresentou indicadores de risco nesta verificação.",
                    )
                }
            }
            items(list, key = { it.profile.packageName }) { assessment ->
                if (assessment.level == RiskLevel.SAFE) {
                    SafeRow(assessment, onClick = { onOpenDetails(assessment.profile.packageName) })
                } else {
                    FlaggedCard(
                        assessment = assessment,
                        canUninstall = !assessment.profile.isSystemApp && !assessment.profile.isDemo &&
                            assessment.profile.packageName != state.selfPackage,
                        onDetails = { onOpenDetails(assessment.profile.packageName) },
                        onUninstall = { uninstallQueue.start(listOf(assessment.profile.packageName)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(report: ScanReport) {
    val risk = LocalRiskColors.current
    val worst = when {
        report.suspiciousCount > 0 -> RiskLevel.SUSPICIOUS
        report.attentionCount > 0 -> RiskLevel.ATTENTION
        else -> RiskLevel.SAFE
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = risk.container(worst), contentColor = risk.onContainer(worst)),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text("Verificação concluída", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.size(12.dp))
            SummaryLine("Aplicativos analisados", report.analyzedCount.toString())
            SummaryLine("Itens suspeitos", report.flaggedCount.toString())
            SummaryLine("   ${RiskLevel.SUSPICIOUS.emoji} Potencialmente suspeitos", report.suspiciousCount.toString())
            SummaryLine("   ${RiskLevel.ATTENTION.emoji} Atenção", report.attentionCount.toString())
            SummaryLine("Aplicativos sem indicadores suspeitos", report.cleanCount.toString())
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "SHA-256 calculado para ${report.hashedApps} apps • Banco: ${report.databaseName}" +
                    (if (report.databaseIsDemo) " (somente demonstração)" else "") +
                    " • Duração: ${((report.finishedAt - report.startedAt) / 1000).coerceAtLeast(1)} s",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FlaggedCard(
    assessment: AppRiskAssessment,
    canUninstall: Boolean,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
) {
    val profile = assessment.profile
    val risk = LocalRiskColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(profile.packageName, profile.isDemo)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(profile.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RiskBadge(assessment.level)
                Spacer(Modifier.width(8.dp))
                Text("Pontuação ${assessment.score}", style = MaterialTheme.typography.labelMedium)
                if (profile.isDemo) {
                    Spacer(Modifier.width(8.dp))
                    Tag("DEMO")
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("Motivo da detecção", style = MaterialTheme.typography.labelLarge, color = risk.accent(assessment.level))
            assessment.findings.filter { it.weight > 0 }.take(3).forEach { finding ->
                Text("• ${finding.title}: ${finding.explanation}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
            if (assessment.relevantPermissions.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text("Permissões relevantes", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    assessment.relevantPermissions.map { it.group }.distinct().forEach { group ->
                        Tag(group, color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            profile.apkSha256?.let { sha ->
                Spacer(Modifier.size(8.dp))
                Text("SHA-256", style = MaterialTheme.typography.labelLarge)
                Text(sha, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDetails) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ver detalhes")
                }
                if (canUninstall && assessment.level != RiskLevel.SAFE) {
                    Button(
                        onClick = onUninstall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Desinstalar aplicativo")
                    }
                }
            }
            if (profile.isDemo) {
                Text(
                    "App fictício do modo demonstração — não existe no aparelho.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SafeRow(assessment: AppRiskAssessment, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(assessment.profile.packageName, assessment.profile.isDemo, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(assessment.profile.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(assessment.profile.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RiskBadge(RiskLevel.SAFE)
        }
    }
}
