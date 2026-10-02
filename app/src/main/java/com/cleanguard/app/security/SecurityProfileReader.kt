package com.cleanguard.app.security

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import com.cleanguard.app.core.util.PackageManagerCompat
import com.cleanguard.app.data.apps.InstallerReader
import com.cleanguard.app.security.model.SecurityProfile

/** Recursos que estão ATIVOS no aparelho (lidos uma vez por verificação). */
data class ActiveCapabilities(
    val enabledAccessibilityPackages: Set<String>,
    val activeAdminPackages: Set<String>,
)

/**
 * Monta o [SecurityProfile] de cada app usando somente APIs públicas:
 * PackageManager (permissões, componentes, assinaturas), AccessibilityManager e DevicePolicyManager.
 */
class SecurityProfileReader(
    context: Context,
    private val apkHasher: ApkHasher,
) {
    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    fun activeCapabilities(): ActiveCapabilities {
        val accessibility = try {
            appContext.getSystemService(AccessibilityManager::class.java)
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                ?.toSet()
        } catch (e: Exception) {
            null
        }
        val admins = try {
            appContext.getSystemService(DevicePolicyManager::class.java)
                ?.activeAdmins
                ?.map { it.packageName }
                ?.toSet()
        } catch (e: Exception) {
            null
        }
        return ActiveCapabilities(accessibility.orEmpty(), admins.orEmpty())
    }

    fun launcherPackages(): Set<String> = runCatching { PackageManagerCompat.launcherPackages(pm) }.getOrDefault(emptySet())

    /**
     * @param hashSystemApps se false, o SHA-256 só é calculado para apps do usuário e
     * apps do sistema atualizados (os mais relevantes e rápidos).
     */
    fun read(
        packageName: String,
        capabilities: ActiveCapabilities,
        launcherPackages: Set<String>,
        hashSystemApps: Boolean,
    ): SecurityProfile? {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            SignatureInspector.signatureFlag
        val info: PackageInfo = try {
            PackageManagerCompat.packageInfo(pm, packageName, flags)
        } catch (e: Exception) {
            // Alguns apps têm componentes demais para uma única chamada; tenta sem componentes.
            try {
                PackageManagerCompat.packageInfo(pm, packageName, PackageManager.GET_PERMISSIONS or SignatureInspector.signatureFlag)
            } catch (e2: Exception) {
                return null
            }
        }
        val ai = info.applicationInfo ?: return null
        val isSystem = PackageManagerCompat.isSystem(ai)
        val isUpdatedSystem = PackageManagerCompat.isUpdatedSystem(ai)

        val requested = info.requestedPermissions?.toList().orEmpty()
        val requestedFlags = info.requestedPermissionsFlags
        val granted = requested.filterIndexed { index, _ ->
            val f = requestedFlags?.getOrNull(index) ?: 0
            f and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        }.toSet()

        val services = info.services.orEmpty()
        val receivers = info.receivers.orEmpty()
        val installer = InstallerReader.read(pm, packageName)
        val shouldHash = !isSystem || isUpdatedSystem || hashSystemApps

        return SecurityProfile(
            packageName = packageName,
            label = ai.loadLabel(pm).toString().ifBlank { packageName },
            versionName = info.versionName,
            isSystemApp = isSystem,
            isUpdatedSystemApp = isUpdatedSystem,
            hasLauncherEntry = packageName in launcherPackages,
            targetSdk = ai.targetSdkVersion,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            origin = InstallSourceClassifier.classify(installer.installer, installer.initiator, isSystem, isUpdatedSystem),
            requestedPermissions = requested,
            grantedPermissions = granted,
            declaresAccessibilityService = services.any { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE },
            accessibilityServiceEnabled = packageName in capabilities.enabledAccessibilityPackages,
            declaresDeviceAdmin = receivers.any { it.permission == Manifest.permission.BIND_DEVICE_ADMIN },
            deviceAdminActive = packageName in capabilities.activeAdminPackages,
            declaresNotificationListener = services.any { it.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE },
            certificates = SignatureInspector.certificates(info),
            apkSha256 = if (shouldHash) apkHasher.hash(ai, info.lastUpdateTime) else null,
            apkSizeBytes = ai.sourceDir?.let { runCatching { java.io.File(it).length() }.getOrNull() } ?: 0L,
        )
    }
}
