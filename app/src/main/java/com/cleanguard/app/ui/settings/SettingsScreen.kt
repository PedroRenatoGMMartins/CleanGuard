@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleanguard.app.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.SystemIntents
import com.cleanguard.app.data.settings.SettingsRepository
import com.cleanguard.app.data.settings.ThemeMode
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.CgTopBar
import com.cleanguard.app.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    Scaffold(topBar = { CgTopBar("Configurações", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard("Aparência") {
                ListItem(
                    headlineContent = { Text("Tema") },
                    supportingContent = {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                    colors = transparentItem(),
                )
                val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SwitchItem(
                    title = "Cores do papel de parede",
                    subtitle = if (dynamicSupported) "Material You (Android 12+)" else "Disponível a partir do Android 12",
                    icon = Icons.Filled.Palette,
                    checked = settings.dynamicColor && dynamicSupported,
                    enabled = dynamicSupported,
                    onChange = viewModel::setDynamicColor,
                )
            }

            SettingsCard("Aplicativos não utilizados") {
                ListItem(
                    headlineContent = { Text("Considerar sem uso após") },
                    supportingContent = {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsRepository.THRESHOLD_OPTIONS.forEach { days ->
                                FilterChip(
                                    selected = settings.unusedThresholdDays == days,
                                    onClick = { viewModel.setUnusedThreshold(days) },
                                    label = { Text("$days dias") },
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.HourglassEmpty, contentDescription = null) },
                    colors = transparentItem(),
                )
                ListItem(
                    headlineContent = { Text("Acesso ao uso") },
                    supportingContent = {
                        Text(if (state.hasUsageAccess) "Concedido — toque para gerenciar" else "Não concedido — necessário para \"última utilização\" e tamanho real")
                    },
                    leadingContent = { Icon(Icons.Filled.Timelapse, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { SystemIntents.openUsageAccess(context) },
                    colors = transparentItem(),
                )
            }

            SettingsCard("Verificação de segurança") {
                SwitchItem(
                    title = "Calcular SHA-256 dos apps do sistema",
                    subtitle = "Mais completo, porém mais lento. Por padrão, só apps do usuário e do sistema atualizados.",
                    icon = Icons.Filled.Fingerprint,
                    checked = settings.hashSystemApps,
                    onChange = viewModel::setHashSystemApps,
                )
                state.databaseInfo?.let { db ->
                    ListItem(
                        headlineContent = { Text("Banco de assinaturas: ${db.name}") },
                        supportingContent = { Text("${db.entryCount} assinaturas • ${db.description}") },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                        colors = transparentItem(),
                    )
                }
                SwitchItem(
                    title = "Consultar serviço externo de reputação",
                    subtitle = if (state.remoteServiceAvailable) {
                        "Envia apenas hashes SHA-256, nunca o APK ou a lista de apps."
                    } else {
                        "Indisponível nesta versão: nenhum serviço está configurado e o app não tem acesso à internet."
                    },
                    icon = Icons.Filled.Public,
                    checked = settings.remoteLookupConsent && state.remoteServiceAvailable,
                    enabled = state.remoteServiceAvailable,
                    onChange = viewModel::setRemoteConsent,
                )
            }

            SettingsCard("Testes") {
                SwitchItem(
                    title = "Modo demonstração",
                    subtitle = "Adiciona apps e arquivos FICTÍCIOS (marcados como DEMO) para testar detecção, seleção e exclusão. " +
                        "Nenhum malware real é usado.",
                    icon = Icons.Filled.Science,
                    checked = settings.demoMode,
                    onChange = viewModel::setDemoMode,
                )
            }

            SettingsCard("Privacidade") {
                ListItem(
                    headlineContent = { Text("Privacidade e permissões") },
                    supportingContent = { Text("O que o CleanGuard acessa, por quê e o que fica no aparelho") },
                    leadingContent = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenPrivacy),
                    colors = transparentItem(),
                )
            }

            Text(
                "CleanGuard ${state.appVersion} • processamento 100% local • sem anúncios • sem acesso à internet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionTitle(title, Modifier.padding(horizontal = 16.dp))
            content()
        }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = transparentItem(),
    )
}

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)
