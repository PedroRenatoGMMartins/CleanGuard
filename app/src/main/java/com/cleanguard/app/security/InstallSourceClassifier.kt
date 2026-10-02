package com.cleanguard.app.security

import com.cleanguard.app.security.model.InstallOrigin
import com.cleanguard.app.security.model.InstallSource

/**
 * Classifica a origem da instalação a partir do pacote instalador informado pelo Android
 * (PackageManager.getInstallSourceInfo no Android 11+). Código puro (testável).
 */
object InstallSourceClassifier {

    private val OFFICIAL_STORES = mapOf(
        "com.android.vending" to "Google Play Store",
        "com.google.android.feedback" to "Google Play Store",
        "com.sec.android.app.samsungapps" to "Galaxy Store",
        "com.huawei.appmarket" to "Huawei AppGallery",
        "com.amazon.venezia" to "Amazon Appstore",
        "com.xiaomi.market" to "Xiaomi GetApps",
        "com.xiaomi.mipicks" to "Xiaomi GetApps",
        "com.heytap.market" to "OPPO App Market",
        "com.oppo.market" to "OPPO App Market",
        "com.bbk.appstore" to "vivo App Store",
        "com.vivo.appstore" to "vivo App Store",
        "org.fdroid.fdroid" to "F-Droid",
    )

    private val ALTERNATIVE_STORES = mapOf(
        "com.aurora.store" to "Aurora Store",
        "com.apkpure.aegon" to "APKPure",
        "com.uptodown" to "Uptodown",
        "com.looker.droidify" to "Droid-ify",
    )

    private val PACKAGE_INSTALLERS = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
    )

    private const val SHELL = "com.android.shell"

    fun classify(installer: String?, initiator: String?, isSystemApp: Boolean, isUpdatedSystemApp: Boolean): InstallOrigin {
        val pkg = installer ?: initiator
        if (isSystemApp && !isUpdatedSystemApp) {
            return InstallOrigin(InstallSource.PREINSTALLED, pkg, "Fabricante / sistema")
        }
        if (pkg == null) {
            return if (isSystemApp) InstallOrigin(InstallSource.PREINSTALLED, null, "Fabricante / sistema")
            else InstallOrigin(InstallSource.UNKNOWN, null, null)
        }
        OFFICIAL_STORES[pkg]?.let { return InstallOrigin(InstallSource.OFFICIAL_STORE, pkg, it) }
        ALTERNATIVE_STORES[pkg]?.let { return InstallOrigin(InstallSource.ALTERNATIVE_STORE, pkg, it) }
        if (pkg == SHELL || initiator == SHELL) return InstallOrigin(InstallSource.ADB, pkg, "ADB (computador)")
        if (pkg in PACKAGE_INSTALLERS) return InstallOrigin(InstallSource.SIDELOADED, pkg, "Instalador de pacotes (arquivo APK)")
        // Qualquer outro app que instalou este (gerenciador de arquivos, navegador, outro app...).
        return InstallOrigin(InstallSource.SIDELOADED, pkg, pkg)
    }
}
