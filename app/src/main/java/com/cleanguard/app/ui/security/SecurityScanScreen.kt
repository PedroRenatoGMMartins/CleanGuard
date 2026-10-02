@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleanguard.app.ui.security

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cleanguard.app.core.util.Formatters
import com.cleanguard.app.ui.AppViewModelProvider
import com.cleanguard.app.ui.components.CgTopBar
import com.cleanguard.app.ui.components.InfoBanner
import com.cleanguard.app.ui.components.SectionTitle
import com.cleanguard.app.ui.components.Tag
import com.cleanguard.app.ui.components.UsageRing

@Composable
fun SecurityScanScreen(
    onBack: () -> Unit,
    onShowResults: () -> Unit,
    viewModel: SecurityViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finishedEvent) {
        if (state.finishedEvent) {
            viewModel.consumeFinishedEvent()
            onShowResults()
        }
    }

    val transition = rememberInfiniteTransition(label = "shield")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Scaffold(topBar = { CgTopBar("Verificação de segurança", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.demoMode) {
                InfoBanner(
                    title = "Modo demonstração",
                    message = "Apps DEMO fictícios serão incluídos para você ver como o CleanGuard sinaliza riscos.",
                    icon = Icons.Filled.Science,
                )
            }

            UsageRing(
                fraction = if (state.running) state.fraction else if (state.lastReport != null) 1f else 0f,
                diameter = 200.dp,
                strokeWidth = 16.dp,
                color = MaterialTheme.colorScheme.secondary,
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(84.dp).scale(if (state.running) pulse else 1f),
                )
            }

            if (state.running) {
                Text("Analisando ${state.current} de ${state.total}", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = viewModel::cancelScan) { Text("Cancelar") }
            } else {
                Text(
                    if (state.lastReport == null) "Pronto para verificar seus apps" else "Última verificação: ${Formatters.dateTime(state.lastReport?.finishedAt)}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = viewModel::startScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar verificação")
                }
                if (state.lastReport != null) {
                    OutlinedButton(onClick = onShowResults, modifier = Modifier.fillMaxWidth()) { Text("Ver último resultado") }
                }
            }

            state.error?.let {
                InfoBanner(
                    title = "Não foi possível concluir",
                    message = it,
                    icon = Icons.Filled.Error,
                    actionLabel = "OK",
                    onAction = viewModel::consumeError,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("O que é verificado")
                    listOf(
                        "Origem da instalação (Play Store, outra loja, APK manual, ADB)",
                        "Permissões sensíveis e quantidade de permissões",
                        "Serviços de acessibilidade, administrador do dispositivo e leitura de notificações",
                        "Apps ocultos (sem ícone) e nomes que imitam apps do sistema",
                        "Certificado de assinatura (ex.: certificado de depuração)",
                        "Hash SHA-256 do APK comparado ao banco de assinaturas",
                    ).forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            state.databaseInfo?.let { db ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Storage, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(db.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (db.isDemo) Tag("DEMO")
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(db.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${db.entryCount} assinaturas • ${if (db.usesNetwork) "consulta online autorizada" else "100% local"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Text(
                "Nenhuma ferramenta detecta 100% das ameaças. O CleanGuard aponta indicadores de risco usando apenas " +
                    "informações que o Android disponibiliza a apps comuns; ele não tem acesso de sistema nem de root.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
