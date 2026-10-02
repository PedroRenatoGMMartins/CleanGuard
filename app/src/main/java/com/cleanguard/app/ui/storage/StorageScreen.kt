@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleanguard.app.ui.storage

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.core.util.SystemIntents
import com.cleanguard.app.data.storage.MediaPermissions
import com.cleanguard.app.domain.storage.FileCategory
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.domain.storage.MediaAccess
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.AppIcon
import com.cleanguard.app.ui.components.CgTopBar
import com.cleanguard.app.ui.components.EmptyState
import com.cleanguard.app.ui.components.InfoBanner
import com.cleanguard.app.ui.components.LoadingState
import com.cleanguard.app.ui.components.Tag
import com.cleanguard.app.ui.components.UsageRing

@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: StorageViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshAccess()
        viewModel.analyze()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }
    val systemDeleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onSystemConfirmationResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pendingConfirmation) {
        val sender = pendingConfirmation ?: return@LaunchedEffect
        viewModel.consumePendingConfirmation()
        systemDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.consumeMessage()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    Scaffold(
        topBar = {
            CgTopBar("Análise de armazenamento", onBack = onBack) {
                IconButton(onClick = viewModel::analyze, enabled = !state.analyzing) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Analisar novamente")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(visible = !state.selection.isEmpty) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${state.selection.count} arquivo(s)", style = MaterialTheme.typography.titleSmall)
                            Text(Formatters.bytes(state.selectedBytes), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = viewModel::clearSelection) { Text("Limpar") }
                        Button(onClick = { showDeleteDialog = true }, enabled = !state.deleting) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Excluir")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { OverviewCard(state, onOpenSystemStorage = { SystemIntents.openManageStorage(context) }) }

            if (state.mediaAccess != MediaAccess.FULL) {
                item {
                    InfoBanner(
                        title = if (state.mediaAccess == MediaAccess.PARTIAL) "Acesso parcial à mídia" else "Permissão de mídia necessária",
                        message = "Para encontrar fotos, vídeos e áudios grandes ou duplicados, o CleanGuard precisa da permissão de " +
                            "mídia do Android. Nada é enviado para fora do aparelho.",
                        icon = Icons.Filled.PhotoLibrary,
                        actionLabel = "Permitir acesso à mídia",
                        onAction = { permissionLauncher.launch(MediaPermissions.required()) },
                    )
                }
            }

            item {
                InfoBanner(
                    title = "O que o Android permite analisar",
                    message = "Fotos, vídeos e áudios de qualquer pasta (com a permissão de mídia). PDFs, ZIPs, APKs e outros " +
                        "arquivos só aparecem se você escolher a pasta. O Android não permite ler arquivos privados de outros apps, " +
                        "a pasta Android/data nem a raiz de Download inteira.",
                    icon = Icons.Filled.Info,
                    actionLabel = "Escolher uma pasta para analisar",
                    onAction = { folderLauncher.launch(null) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            when {
                state.analyzing -> item { LoadingState("Analisando arquivos… (imagens duplicadas são comparadas por SHA-256)") }
                state.report == null -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = viewModel::analyze, modifier = Modifier.padding(8.dp)) {
                            Icon(Icons.Filled.Storage, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Iniciar análise")
                        }
                    }
                }
                else -> {
                    item { CategoryChips(state, onSelect = viewModel::setTab) }
                    when (state.tab) {
                        StorageTab.CACHE -> cacheSection(state, viewModel)
                        StorageTab.DUPLICATES -> duplicatesSection(state, viewModel)
                        else -> fileListSection(state, viewModel)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            files = state.selectedItems,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteSelected()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun OverviewCard(state: StorageUiState, onOpenSystemStorage: () -> Unit) {
    val device = state.device
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsageRing(fraction = device?.usedFraction ?: 0f, diameter = 110.dp, strokeWidth = 12.dp) {
                    Text(device?.let { Formatters.percent(it.usedFraction) } ?: "…", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Usado: ${device?.let { Formatters.bytes(it.usedBytes) } ?: "…"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Livre: ${device?.let { Formatters.bytes(it.freeBytes) } ?: "…"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Total: ${device?.let { Formatters.bytes(it.totalBytes) } ?: "…"}", style = MaterialTheme.typography.bodyMedium)
                    state.report?.let {
                        Text(
                            "${it.scannedFiles} arquivos analisados • até ${Formatters.bytes(it.reclaimableEstimateBytes)} em temporários e duplicatas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            OutlinedButton(onClick = onOpenSystemStorage, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir \"Liberar espaço\" do Android")
            }
        }
    }
}

@Composable
private fun CategoryChips(state: StorageUiState, onSelect: (StorageTab) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StorageTab.entries.toList()) { tab ->
            val count = when (tab) {
                StorageTab.DUPLICATES -> state.report?.duplicateGroups?.size ?: 0
                StorageTab.CACHE -> state.appsByCache.size
                else -> state.itemsFor(tab).size
            }
            FilterChip(
                selected = state.tab == tab,
                onClick = { onSelect(tab) },
                label = { Text("${tab.label} ($count)") },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.fileListSection(state: StorageUiState, viewModel: StorageViewModel) {
    val files = state.itemsFor(state.tab)
    if (files.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Filled.CleaningServices,
                title = "Nada encontrado aqui",
                message = when (state.tab) {
                    StorageTab.FOLDERS -> "Escolha uma pasta (ex.: Documents) para listar PDFs, ZIPs e outros arquivos."
                    StorageTab.DOWNLOADS -> "Sem arquivos de mídia na pasta Download. Outros tipos exigem escolher uma subpasta."
                    else -> "Nenhum arquivo acessível nesta categoria."
                },
            )
        }
        return
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${files.size} arquivo(s) • ${Formatters.bytes(files.sumOf { it.sizeBytes })}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.selectAll(files) }) { Text("Selecionar todos") }
        }
    }
    items(files, key = { it.id }) { file ->
        FileRow(file, selected = state.selection.isSelected(file.id), onToggle = { viewModel.toggle(file) })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.duplicatesSection(state: StorageUiState, viewModel: StorageViewModel) {
    val groups = state.report?.duplicateGroups.orEmpty()
    if (groups.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Filled.ContentCopy,
                title = "Nenhuma imagem duplicada",
                message = "Comparamos imagens de mesmo tamanho pelo conteúdo (SHA-256).",
            )
        }
        return
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${groups.size} grupo(s) • ${Formatters.bytes(groups.sumOf { it.wastedBytes })} em cópias",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = viewModel::selectSuggestedDuplicates) { Text("Selecionar cópias") }
        }
    }
    groups.forEachIndexed { index, group ->
        item(key = "group-$index-${group.sha256}") {
            Text(
                "Grupo ${index + 1}: ${group.files.size} cópias idênticas • ${Formatters.bytes(group.wastedBytes)} desperdiçados",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(group.files, key = { "dup-${it.id}" }) { file ->
            FileRow(file, selected = state.selection.isSelected(file.id), onToggle = { viewModel.toggle(file) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.cacheSection(state: StorageUiState, viewModel: StorageViewModel) {
    item {
        InfoBanner(
            title = "Sobre o cache de outros apps",
            message = "Desde o Android 6, apps comuns não podem limpar o cache de outros apps — isso é exclusivo do sistema. " +
                "O CleanGuard mostra quais apps têm mais cache e abre a tela oficial do app, onde você toca em \"Limpar cache\".",
            icon = Icons.Filled.Info,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cache do CleanGuard", style = MaterialTheme.typography.titleSmall)
                    Text(Formatters.bytes(state.ownCacheBytes), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = viewModel::clearOwnCache) { Text("Limpar") }
            }
        }
    }
    if (!state.hasUsageAccess) {
        item {
            val context = LocalContext.current
            InfoBanner(
                title = "Acesso ao uso necessário",
                message = "O tamanho do cache de cada app só é informado pelo Android com a permissão \"Acesso ao uso\".",
                icon = Icons.Filled.Timelapse,
                actionLabel = "Conceder acesso ao uso",
                onAction = { SystemIntents.openUsageAccess(context) },
            )
        }
    } else {
        items(state.appsByCache, key = { "cache-${it.packageName}" }) { app ->
            val context = LocalContext.current
            Card(
                onClick = { SystemIntents.openAppDetails(context, app.packageName) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, app.isDemo, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Cache: ${Formatters.bytes(app.storage?.cacheBytes ?: 0L)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Abrir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: FileItem, selected: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        enabled = file.canDelete,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = file.canDelete)
            Icon(categoryIcon(file.category), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (file.source == FileSource.DEMO) {
                        Spacer(Modifier.width(6.dp))
                        Tag("DEMO", color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                Text(file.location.ifEmpty { "/" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.bytes(file.sizeBytes)} • ${file.category.label} • ${Formatters.date(file.dateModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!file.canDelete) {
                    Text("O provedor desta pasta não permite excluir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(files: List<FileItem>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val total = Formatters.bytes(files.sumOf { it.sizeBytes })
    val hasMedia = files.any { it.source == FileSource.MEDIA_STORE }
    val demoOnly = files.all { it.source == FileSource.DEMO }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (demoOnly) Icons.Filled.Science else Icons.Filled.Delete, contentDescription = null) },
        title = { Text("Excluir ${files.size} arquivo(s)? ($total)") },
        text = {
            Column {
                Text("Confira antes de excluir:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                Box(Modifier.heightIn(max = 300.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(files, key = { it.id }) { file ->
                            Column {
                                Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Local: ${file.location.ifEmpty { "/" }}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Tamanho: ${Formatters.bytes(file.sizeBytes)} • Tipo: ${file.category.label}" +
                                        (file.mimeType?.let { " ($it)" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                HorizontalDivider(Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        demoOnly -> "Itens DEMO são fictícios: apenas sairão da lista."
                        hasMedia && StorageViewModel.systemConfirmsMediaDeletion ->
                            "O Android mostrará uma confirmação própria para as fotos, vídeos e áudios."
                        else -> "Esta ação não pode ser desfeita."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.VIDEO -> Icons.Filled.VideoFile
    FileCategory.AUDIO -> Icons.Filled.AudioFile
    FileCategory.DOCUMENT -> Icons.Filled.Description
    FileCategory.ARCHIVE -> Icons.Filled.FolderZip
    FileCategory.APK -> Icons.Filled.Android
    FileCategory.TEMPORARY -> Icons.Filled.AutoDelete
    FileCategory.OTHER -> Icons.Filled.FilePresent
}
