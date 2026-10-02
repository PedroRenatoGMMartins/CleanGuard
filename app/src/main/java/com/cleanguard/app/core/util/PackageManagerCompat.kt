package com.cleanguard.app.core.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/** Chamadas ao PackageManager sem APIs depreciadas no Android 13+ e compatíveis com Android 8+. */
object PackageManagerCompat {

    @Suppress("DEPRECATION")
    fun packageInfo(pm: PackageManager, packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }

    @Suppress("DEPRECATION")
    fun installedApplications(pm: PackageManager, flags: Int = 0): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledApplications(flags)
        }

    @Suppress("DEPRECATION")
    fun applicationInfo(pm: PackageManager, packageName: String, flags: Int = 0): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            pm.getApplicationInfo(packageName, flags)
        }

    /** Pacotes que aparecem na tela de aplicativos (têm atividade LAUNCHER). */
    @Suppress("DEPRECATION")
    fun launcherPackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        return list.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    @Suppress("DEPRECATION")
    fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        packageInfo(pm, packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun isSystem(ai: ApplicationInfo): Boolean = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0

    fun isUpdatedSystem(ai: ApplicationInfo): Boolean = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}
