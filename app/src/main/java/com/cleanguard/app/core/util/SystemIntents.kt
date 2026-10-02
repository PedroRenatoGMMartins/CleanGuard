package com.cleanguard.app.core.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings

/**
 * Intents para telas OFICIAIS do sistema. O CleanGuard nunca desinstala, apaga ou altera nada
 * sozinho: ele abre a tela do Android e o usuário decide.
 */
object SystemIntents {

    /**
     * Abre o diálogo oficial de desinstalação do Android. Exige a permissão
     * REQUEST_DELETE_PACKAGES (declarada no manifesto) e SEMPRE pede confirmação ao usuário.
     */
    fun uninstall(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)

    /** Tela "Informações do app" (desativar, forçar parada, limpar cache/dados, permissões). */
    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    /** Tela "Acesso ao uso" — a única forma de conceder PACKAGE_USAGE_STATS. */
    fun usageAccessSettings(packageName: String?): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            // Em várias versões o sistema abre direto na página do app quando o pacote é informado.
            if (packageName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                data = Uri.fromParts("package", packageName, null)
            }
        }

    /** Tela do sistema "Liberar espaço" / gerenciador de armazenamento. */
    fun manageStorage(): Intent = Intent(StorageManager.ACTION_MANAGE_STORAGE)

    fun internalStorageSettings(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun securitySettings(): Intent = Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun appPermissionSettings(packageName: String): Intent = appDetails(packageName)

    /** Página do app na Play Store (abre o app da loja; o CleanGuard não acessa a internet). */
    fun playStore(packageName: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))

    /**
     * Inicia a atividade com segurança: se não existir no aparelho, tenta as alternativas
     * informadas. Retorna false se nenhuma puder ser aberta.
     */
    fun start(context: Context, intent: Intent, vararg fallbacks: Intent): Boolean {
        for (candidate in listOf(intent, *fallbacks)) {
            try {
                if (context !is Activity) candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(candidate)
                return true
            } catch (e: ActivityNotFoundException) {
                // tenta a próxima alternativa
            } catch (e: SecurityException) {
                // tenta a próxima alternativa
            }
        }
        return false
    }

    fun openUsageAccess(context: Context): Boolean =
        start(context, usageAccessSettings(context.packageName), usageAccessSettings(null), Intent(Settings.ACTION_SETTINGS))

    fun openAppDetails(context: Context, packageName: String): Boolean =
        start(context, appDetails(packageName), Intent(Settings.ACTION_APPLICATION_SETTINGS))

    fun openManageStorage(context: Context): Boolean =
        start(context, manageStorage(), internalStorageSettings(), Intent(Settings.ACTION_SETTINGS))
}
