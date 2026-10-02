@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleanguard.app.ui.privacy

import android.os.Build
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.SystemIntents
import com.cleanguard.app.domain.storage.MediaAccess
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.InfoBanner
import com.cleanguard.app.ui.components.CgTopBar
import com.cleanguard.app.ui.components.SectionTitle
import com.cleanguard.app.ui.components.Tag
import com.cleanguard.app.ui.settings.SettingsViewModel

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    Scaffold(topBar = { CgTopBar("Privacidade e permissões", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoBanner(
                title = "Tudo fica no seu aparelho",
                message = "O CleanGuard não declara a permissão de Internet. A análise é 100% local e nada sobre seus apps " +
                    "ou arquivos é enviado para servidores.",
                icon = Icons.Filled.WifiOff,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            SectionTitle("Permissões que o CleanGuard usa")

            PermissionCard(
                icon = Icons.Filled.Apps,
                name = "Ver todos os apps instalados",
                technical = "QUERY_ALL_PACKAGES",
                why = "Necessária para a verificação de segurança analisar todos os apps, e não só alguns. " +
                    "Lemos nome, pacote, permissões declaradas, assinatura e o arquivo APK (para o SHA-256).",
                status = "Concedida na instalação",
            )
            PermissionCard(
                icon = Icons.Filled.Timelapse,
                name = "Acesso ao uso (opcional)",
                technical = "PACKAGE_USAGE_STATS",
                why = "Permite saber a última vez que cada app foi usado e o tamanho real (app + dados + cache). " +
                    "Só você pode ativar, nas Configurações do Android.",
                status = if (state.hasUsageAccess) "Concedida" else "Não concedida",
                actionLabel = "Gerenciar",
                onAction = { SystemIntents.openUsageAccess(context) },
            )
            PermissionCard(
                icon = Icons.Filled.Delete,
                name = "Pedir desinstalação de apps",
                technical = "REQUEST_DELETE_PACKAGES",
                why = "Permite ABRIR o diálogo oficial de desinstalação do Android. O CleanGuard nunca desinstala nada sozinho: " +
                    "você confirma cada app na tela do sistema.",
                status = "Concedida na instalação",
            )
            PermissionCard(
                icon = Icons.Filled.PhotoLibrary,
                name = "Fotos, vídeos e áudios (opcional)",
                technical = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "READ_MEDIA_IMAGES / VIDEO / AUDIO" else "READ_EXTERNAL_STORAGE",
                why = "Usada na análise de armazenamento para achar arquivos grandes e imagens duplicadas. " +
                    "Para excluir, o Android mostra a confirmação dele (Android 10+).",
                status = when (state.mediaAccess) {
                    MediaAccess.FULL -> "Concedida"
                    MediaAccess.PARTIAL -> "Parcial"
                    MediaAccess.NONE -> "Não concedida"
                },
                actionLabel = "Gerenciar",
                onAction = { SystemIntents.openAppDetails(context, context.packageName) },
            )
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                PermissionCard(
                    icon = Icons.Filled.PhotoLibrary,
                    name = "Excluir arquivos de mídia (Android 8 e 9)",
                    technical = "WRITE_EXTERNAL_STORAGE",
                    why = "Nas versões antigas, excluir fotos/vídeos de outros apps exige esta permissão. Sempre pedimos confirmação antes.",
                    status = "Somente Android 8/9",
                )
            }
            PermissionCard(
                icon = Icons.Filled.Folder,
                name = "Pastas que você escolher",
                technical = "Storage Access Framework",
                why = "Para analisar PDFs, ZIPs e outros arquivos, você escolhe uma pasta no seletor do Android. " +
                    "O CleanGuard só acessa essa pasta — nada além dela.",
                status = "Sob demanda",
            )

            SectionTitle("O que o CleanGuard NÃO usa")
            CardBlock {
                listOf(
                    "Internet (nenhum dado sai do aparelho)",
                    "Serviço de acessibilidade",
                    "Administrador do dispositivo",
                    "Acesso a todos os arquivos (MANAGE_EXTERNAL_STORAGE)",
                    "Instalar apps (o CleanGuard nunca instala APKs)",
                    "Root ou qualquer técnica para contornar proteções do Android",
                    "Localização, contatos, SMS, câmera ou microfone",
                    "Código baixado de fontes externas",
                ).forEach { BulletRow(Icons.Filled.Block, it) }
            }

            SectionTitle("Dados que permanecem no aparelho")
            CardBlock {
                listOf(
                    "Lista de apps, permissões, assinaturas e hashes: lidos na hora e mantidos só na memória.",
                    "Resultados da verificação e da análise: só na memória, apagados ao fechar o app.",
                    "Configurações (tema, modo demonstração etc.): salvas localmente, sem backup na nuvem.",
                    "Pastas escolhidas: o Android guarda a autorização; você pode revogá-la nas configurações do app.",
                ).forEach { BulletRow(Icons.Filled.PhoneAndroid, it) }
            }

            SectionTitle("Dados enviados para serviços externos")
            CardBlock {
                BulletRow(Icons.Filled.CloudOff, "Nenhum. Esta versão não envia nenhuma informação para fora do aparelho.")
                BulletRow(
                    Icons.Filled.CloudOff,
                    "O app está preparado para, no futuro, consultar um serviço legítimo de reputação. Se isso for ativado, " +
                        "seria enviado APENAS o hash SHA-256 dos APKs (nunca o arquivo, a lista de apps ou dados pessoais), " +
                        "e somente após seu consentimento explícito em Configurações.",
                )
            }

            OutlinedButton(
                onClick = { SystemIntents.openAppDetails(context, context.packageName) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Gerenciar permissões do CleanGuard") }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    name: String,
    technical: String,
    why: String,
    status: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(technical, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tag(status)
            }
            Text("Por quê: $why", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun BulletRow(icon: ImageVector, text: String) {
    Row {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
