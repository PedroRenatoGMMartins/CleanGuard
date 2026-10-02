@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleanguard.app.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.core.util.SystemIntents
import com.cleanguard.app.domain.apps.AppFilter
import com.cleanguard.app.domain.apps.InstalledApp
import com.cleanguard.app.domain.apps.UsageLevel
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.AppIcon
import com.cleanguard.app.ui.components.CgTopBar
import com.cleanguard.app.ui.components.EmptyState
import com.cleanguard.app.ui.components.InfoBanner
import com.cleanguard.app.ui.components.LoadingState
import com.cleanguard.app.ui.components.Tag
import com.cleanguard.app.ui.components.rememberUninstallQueue
import com.cleanguard.app.ui.theme.LocalRiskColors

/**
 * Tela "Limpeza" (todos os apps) e "Aplicativos não utilizados" (filtro inicial UNUSED).
 */
@Composable
fun AppListScreen(
    title: String,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    viewModel: AppsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    val uninstallQueue = rememberUninstallQueue { viewModel.onUninstallFinished() }
    val classifier = remember(state.thresholdDays) { state.usageClassifier }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(
        topBar = {
            CgTopBar(title = title, onBack = onBack) {
                if (!state.selection.isEmpty) {
                    IconButton(onClick = viewModel::clearSelection) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpar seleção")
                    }
                }
                IconButton(onClick = viewModel::selectAllVisible) {
                    Icon(Icons.Filled.SelectAll, contentDescription = "Selecionar todos os removíveis")
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !state.selection.isEmpty) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${state.selection.count} selecionado(s)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Cerca de ${Formatters.bytes(state.selectedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { showConfirm = true }, enabled = !uninstallQueue.isRunning) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Desinstalar selecionados")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (refreshing && state.visibleApps.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Buscar por nome ou pacote") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AppFilter.entries.toList()) { filter ->
                    val selected = state.filter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }

            when {
                state.loading -> LoadingState("Lendo aplicativos instalados…")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!state.hasUsageAccess) {
                        item {
                            InfoBanner(
                                title = if (state.filter == AppFilter.UNUSED) "Acesso ao uso necessário" else "Dados de uso indisponíveis",
                                message = "Sem a permissão \"Acesso ao uso\", o Android não informa a data da última utilização " +
                                    "nem o tamanho real (mostramos só o tamanho do APK). Você concede isso nas Configurações do sistema." +
                                    if (state.demoMode) " Os apps DEMO têm dados fictícios de uso." else "",
                                icon = Icons.Filled.Timelapse,
                                actionLabel = "Conceder acesso ao uso",
                                onAction = { SystemIntents.openUsageAccess(context) },
                            )
                        }
                    }
                    item {
                        Text(
                            text = filterDescription(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    if (state.visibleApps.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Filled.SearchOff,
                                title = "Nenhum app aqui",
                                message = if (state.filter == AppFilter.UNUSED && state.hasUsageAccess) {
                                    "Nenhum app instalado por você ficou sem uso nos últimos ${state.thresholdDays} dias."
                                } else {
                                    "Tente outro filtro ou outra busca."
                                },
                            )
                        }
                    }
                    items(state.visibleApps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            selected = state.selection.isSelected(app.packageName),
                            selectable = state.canUninstall(app),
                            usageLevel = classifier.usageLevel(app, state.now),
                            lastUsedText = lastUsedText(app, state.now),
                            onToggle = { viewModel.toggle(app) },
                            onClick = { onOpenApp(app.packageName) },
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        val selected = state.selectedApps
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Desinstalar ${selected.size} app(s)?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selected.take(8).forEach { Text("• ${it.label}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (selected.size > 8) Text("… e mais ${selected.size - 8}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O Android vai abrir a tela oficial de desinstalação para CADA app. " +
                            "Você confirma ou cancela um por um.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    uninstallQueue.start(viewModel.selectedPackages())
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancelar") } },
        )
    }
}

private fun filterDescription(state: AppsUiState): String {
    val count = state.visibleApps.size
    return when (state.filter) {
        AppFilter.ALL -> "$count apps instalados"
        AppFilter.UNUSED -> "$count apps sem uso há ${state.thresholdDays}+ dias (apenas apps instalados por você)"
        AppFilter.LARGEST -> "$count apps, do maior para o menor"
        AppFilter.OLDEST -> "$count apps do usuário, do mais antigo para o mais recente"
        AppFilter.USER -> "$count apps instalados por você"
        AppFilter.SYSTEM -> "$count apps do sistema (não podem ser desinstalados; podem ser desativados nas Configurações)"
    }
}

private fun lastUsedText(app: InstalledApp, now: Long): String {
    val usage = app.usage ?: return "Último uso: indisponível"
    if (usage.lastTimeUsed <= 0L) return "Sem registro de uso"
    return "Último uso: ${Formatters.relativeDays(usage.lastTimeUsed, now)} (${Formatters.date(usage.lastTimeUsed)})"
}

@Composable
private fun AppRow(
    app: InstalledApp,
    selected: Boolean,
    selectable: Boolean,
    usageLevel: UsageLevel,
    lastUsedText: String,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (selectable) {
                    Checkbox(checked = selected, onCheckedChange = { onToggle() })
                } else {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Não pode ser desinstalado por aqui",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            AppIcon(packageName = app.packageName, isDemo = app.isDemo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    when {
                        app.isDemo -> Tag("DEMO", color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        app.isSystemApp -> Tag("Sistema", color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Tag("Usuário")
                    }
                }
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(Formatters.bytes(app.totalSizeBytes))
                        if (app.isSizeApproximate) append(" (APK)")
                        append(" • Instalado em ")
                        append(Formatters.date(app.firstInstallTime))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsageMeter(usageLevel)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${usageLevel.label} • $lastUsedText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Indicador de frequência de uso em 4 barras. */
@Composable
fun UsageMeter(level: UsageLevel) {
    val filled = when (level) {
        UsageLevel.UNKNOWN -> 0
        UsageLevel.INACTIVE -> 1
        UsageLevel.LOW -> 2
        UsageLevel.MEDIUM -> 3
        UsageLevel.HIGH -> 4
    }
    val risk = LocalRiskColors.current
    val activeColor: Color = when (level) {
        UsageLevel.INACTIVE -> risk.attention
        UsageLevel.UNKNOWN -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(4) { index ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((6 + index * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index < filled) activeColor else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
