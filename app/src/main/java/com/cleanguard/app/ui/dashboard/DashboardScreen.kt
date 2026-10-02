@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleanguard.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.core.util.SystemIntents
import com.cleanguard.app.security.model.RiskLevel
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.BigActionButton
import com.cleanguard.app.ui.components.InfoBanner
import com.cleanguard.app.ui.components.StatTile
import com.cleanguard.app.ui.components.UsageRing
import com.cleanguard.app.ui.theme.LocalRiskColors

@Composable
fun DashboardScreen(
    onOpenCleanup: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenUnused: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CleanGuard") },
                navigationIcon = {
                    IconButton(onClick = onOpenPrivacy) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = "Privacidade e permissões")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.demoMode) {
                item {
                    InfoBanner(
                        title = "Modo demonstração ativo",
                        message = "Apps e arquivos marcados como DEMO são fictícios e servem só para testar o CleanGuard.",
                        icon = Icons.Filled.Science,
                    )
                }
            }

            item { StorageCard(state) }
            item { SecurityStatusCard(state, onOpenSecurity = onOpenSecurity, onOpenResults = onOpenResults) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Apps instalados",
                        value = if (state.loading) "…" else state.installedCount.toString(),
                        icon = Icons.Filled.Apps,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCleanup,
                    )
                    StatTile(
                        label = "Pouco utilizados",
                        value = state.unusedCount?.toString() ?: "—",
                        icon = Icons.Filled.HourglassEmpty,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenUnused,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Suspeitos encontrados",
                        value = state.flaggedCount?.toString() ?: "—",
                        icon = Icons.Filled.Security,
                        modifier = Modifier.weight(1f),
                        accent = if ((state.flaggedCount ?: 0) > 0) LocalRiskColors.current.attention else MaterialTheme.colorScheme.primary,
                        onClick = if (state.flaggedCount != null) onOpenResults else onOpenSecurity,
                    )
                    StatTile(
                        label = "Pode ser liberado*",
                        value = if (state.loading) "…" else Formatters.bytes(state.reclaimableBytes),
                        icon = Icons.Filled.DeleteSweep,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenStorage,
                    )
                }
            }

            if (!state.loading && !state.hasUsageAccess) {
                item {
                    InfoBanner(
                        title = "Permita o acesso ao uso",
                        message = "Para saber quais apps você não usa e o tamanho real de cada um, o Android exige que você " +
                            "ative o CleanGuard em \"Acesso ao uso\". Os dados ficam só no aparelho.",
                        icon = Icons.Filled.Timelapse,
                        actionLabel = "Abrir configuração",
                        onAction = { SystemIntents.openUsageAccess(context) },
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigActionButton(
                        title = "Limpar celular",
                        subtitle = "Ver e remover apps",
                        icon = Icons.Filled.CleaningServices,
                        onClick = onOpenCleanup,
                        modifier = Modifier.weight(1f),
                    )
                    BigActionButton(
                        title = "Verificar segurança",
                        subtitle = "Analisar apps instalados",
                        icon = Icons.Filled.Security,
                        onClick = onOpenSecurity,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigActionButton(
                        title = "Apps não utilizados",
                        subtitle = "Apps sem uso recente",
                        icon = Icons.Filled.HourglassEmpty,
                        onClick = onOpenUnused,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    BigActionButton(
                        title = "Analisar armazenamento",
                        subtitle = "Arquivos grandes e duplicados",
                        icon = Icons.Filled.Storage,
                        onClick = onOpenStorage,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tudo é analisado no aparelho. *Estimativa: nada é apagado sem sua confirmação.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageCard(state: DashboardUiState) {
    val device = state.device
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            UsageRing(fraction = device?.usedFraction ?: 0f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (device == null) "…" else Formatters.percent(device.usedFraction),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text("em uso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Armazenamento interno", style = MaterialTheme.typography.titleMedium)
                LabeledValue("Utilizado", device?.let { Formatters.bytes(it.usedBytes) } ?: "…")
                LabeledValue("Livre", device?.let { Formatters.bytes(it.freeBytes) } ?: "…")
                LabeledValue("Total", device?.let { Formatters.bytes(it.totalBytes) } ?: "…")
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SecurityStatusCard(state: DashboardUiState, onOpenSecurity: () -> Unit, onOpenResults: () -> Unit) {
    val risk = LocalRiskColors.current
    val flagged = state.flaggedCount
    val level = when {
        flagged == null -> null
        state.suspiciousCount > 0 -> RiskLevel.SUSPICIOUS
        flagged > 0 -> RiskLevel.ATTENTION
        else -> RiskLevel.SAFE
    }
    val container = level?.let { risk.container(it) } ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val content = level?.let { risk.onContainer(it) } ?: MaterialTheme.colorScheme.onSurface
    val icon = when (level) {
        null -> Icons.Filled.Security
        RiskLevel.SAFE -> Icons.Filled.GppGood
        RiskLevel.ATTENTION -> Icons.Filled.GppMaybe
        RiskLevel.SUSPICIOUS -> Icons.Filled.GppBad
    }
    val title = when (level) {
        null -> "Nenhuma verificação nesta sessão"
        RiskLevel.SAFE -> "Nenhum indicador suspeito encontrado"
        RiskLevel.ATTENTION -> "$flagged ${if (flagged == 1) "item merece" else "itens merecem"} atenção"
        RiskLevel.SUSPICIOUS -> "${state.suspiciousCount} potencialmente ${if (state.suspiciousCount == 1) "suspeito" else "suspeitos"}"
    }
    val subtitle = if (state.lastScanAt == null) "Toque para verificar seus apps" else "Última verificação: ${Formatters.dateTime(state.lastScanAt)}"

    Card(
        onClick = if (flagged == null) onOpenSecurity else onOpenResults,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
            }
        }
    }
}
