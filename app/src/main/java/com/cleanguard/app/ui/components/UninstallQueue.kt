package com.cleanguard.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.cleanguard.app.core.util.SystemIntents

/**
 * Fila de desinstalação: abre o diálogo OFICIAL do Android para um app de cada vez.
 * O usuário confirma (ou cancela) cada um — nada é desinstalado silenciosamente.
 */
class UninstallQueue internal constructor() {
    private val pending = ArrayDeque<String>()
    private val processed = mutableListOf<String>()
    internal var launcher: ActivityResultLauncher<Intent>? = null
    internal var onFinished: (List<String>) -> Unit = {}

    var isRunning by mutableStateOf(false)
        private set

    /** Quantos ainda faltam (para exibir "2 de 5"). */
    var remaining by mutableStateOf(0)
        private set

    fun start(packages: List<String>) {
        if (packages.isEmpty() || isRunning) return
        pending.clear()
        processed.clear()
        pending.addAll(packages.distinct())
        isRunning = true
        next()
    }

    internal fun next() {
        val pkg = pending.removeFirstOrNull()
        remaining = pending.size
        if (pkg == null) {
            isRunning = false
            onFinished(processed.toList())
            return
        }
        processed += pkg
        val activeLauncher = launcher
        if (activeLauncher == null) {
            next()
            return
        }
        try {
            activeLauncher.launch(SystemIntents.uninstall(pkg))
        } catch (e: ActivityNotFoundException) {
            next()
        }
    }
}

@Composable
fun rememberUninstallQueue(onFinished: (processedPackages: List<String>) -> Unit): UninstallQueue {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val queue = remember { UninstallQueue() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Independentemente do resultado (confirmado ou cancelado), segue para o próximo.
        queue.next()
    }
    SideEffect {
        queue.launcher = launcher
        queue.onFinished = { currentOnFinished(it) }
    }
    return queue
}
