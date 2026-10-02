package com.cleanguard.app.data.apps

import android.content.pm.PackageManager
import android.os.Build

data class InstallerInfo(
    /** Pacote que concluiu a instalação (ex.: com.android.vending). */
    val installer: String?,
    /** Pacote que iniciou a instalação (Android 11+, quando o sistema informa). */
    val initiator: String?,
)

/** Lê a origem da instalação usando a API oficial adequada para cada versão do Android. */
object InstallerReader {

    @Suppress("DEPRECATION")
    fun read(pm: PackageManager, packageName: String): InstallerInfo = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = pm.getInstallSourceInfo(packageName)
            InstallerInfo(info.installingPackageName, info.initiatingPackageName)
        } else {
            InstallerInfo(pm.getInstallerPackageName(packageName), null)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        InstallerInfo(null, null)
    } catch (e: IllegalArgumentException) {
        InstallerInfo(null, null)
    } catch (e: SecurityException) {
        InstallerInfo(null, null)
    }
}
