package com.cleanguard.app.demo

import com.cleanguard.app.core.util.HashUtils
import com.cleanguard.app.domain.apps.AppStorage
import com.cleanguard.app.domain.apps.AppUsage
import com.cleanguard.app.domain.apps.InstalledApp
import com.cleanguard.app.domain.storage.FileCategory
import com.cleanguard.app.domain.storage.FileItem
import com.cleanguard.app.domain.storage.FileSource
import com.cleanguard.app.security.InstallSourceClassifier
import com.cleanguard.app.security.db.LocalDemoMalwareDatabase
import com.cleanguard.app.security.model.SecurityProfile
import com.cleanguard.app.security.model.SignerCertificate
import java.util.concurrent.TimeUnit

/**
 * Dados FICTÍCIOS do modo demonstração. Nenhum malware real é usado — apenas descrições de apps
 * inventados e hashes de textos, para testar a interface e as regras de detecção.
 */
object DemoData {

    const val PACKAGE_PREFIX = "com.cleanguard.demo."
    private const val P = "android.permission."
    private const val MB = 1_000_000L

    private data class DemoApp(
        val app: InstalledApp,
        val profile: SecurityProfile,
    )

    private fun days(n: Long) = TimeUnit.DAYS.toMillis(n)

    private fun build(now: Long): List<DemoApp> {
        fun app(
            pkg: String, label: String, installer: String?, installedDaysAgo: Long, lastUsedDaysAgo: Long?,
            fg30Min: Long, sizeMb: Long, cacheMb: Long, launcher: Boolean = true, targetSdk: Int = 34,
        ) = InstalledApp(
            packageName = PACKAGE_PREFIX + pkg,
            label = "$label (DEMO)",
            isSystemApp = false,
            isUpdatedSystemApp = false,
            hasLauncherEntry = launcher,
            isEnabled = true,
            firstInstallTime = now - days(installedDaysAgo),
            lastUpdateTime = now - days(installedDaysAgo / 2),
            versionName = "1.0-demo",
            versionCode = 1,
            targetSdk = targetSdk,
            apkSizeBytes = sizeMb * MB / 3,
            storage = AppStorage(appBytes = sizeMb * MB / 3, dataBytes = sizeMb * MB * 2 / 3, cacheBytes = cacheMb * MB),
            usage = AppUsage(
                lastTimeUsed = lastUsedDaysAgo?.let { now - days(it) } ?: 0L,
                foregroundTimeLast30DaysMs = TimeUnit.MINUTES.toMillis(fg30Min),
            ),
            installerPackage = installer,
            isDemo = true,
        )

        fun profile(
            a: InstalledApp, perms: List<String>, accessibility: Boolean = false, accessibilityOn: Boolean = false,
            admin: Boolean = false, adminOn: Boolean = false, notif: Boolean = false, debugCert: Boolean = false,
            apkSha: String? = HashUtils.sha256("demo-apk:" + a.packageName), certSha: String = HashUtils.sha256("demo-cert:" + a.packageName),
        ) = SecurityProfile(
            packageName = a.packageName,
            label = a.label,
            versionName = a.versionName,
            isSystemApp = false,
            isUpdatedSystemApp = false,
            hasLauncherEntry = a.hasLauncherEntry,
            targetSdk = a.targetSdk,
            firstInstallTime = a.firstInstallTime,
            lastUpdateTime = a.lastUpdateTime,
            origin = InstallSourceClassifier.classify(a.installerPackage, null, isSystemApp = false, isUpdatedSystemApp = false),
            requestedPermissions = perms.map { P + it },
            grantedPermissions = perms.map { P + it }.toSet(),
            declaresAccessibilityService = accessibility,
            accessibilityServiceEnabled = accessibilityOn,
            declaresDeviceAdmin = admin,
            deviceAdminActive = adminOn,
            declaresNotificationListener = notif,
            certificates = listOf(
                SignerCertificate(
                    sha256 = certSha,
                    subject = if (debugCert) "CN=Android Debug,O=Android,C=US" else "CN=Demo Developer,O=Demo,C=BR",
                    issuer = if (debugCert) "CN=Android Debug,O=Android,C=US" else "CN=Demo Developer,O=Demo,C=BR",
                    notAfter = now + days(3650),
                ),
            ),
            apkSha256 = apkSha,
            apkSizeBytes = a.apkSizeBytes,
            isDemo = true,
        )

        val flashlight = app("flashlight", "Lanterna Turbo", "com.google.android.packageinstaller", 20, 1, 15, 45, 12, launcher = false)
        val banker = app("fakebanker", "Atualização do Banco", "com.android.chrome", 10, 2, 5, 18, 2)
        val recorder = app("recorder", "Gravador de Voz", "com.aurora.store", 200, 45, 0, 90, 30)
        val photo = app("photoeditor", "Editor de Fotos", "com.android.vending", 400, 120, 0, 350, 120)
        val game = app("oldgame", "Jogo de Corrida", "com.android.vending", 700, null, 0, 1_200, 250)
        val notes = app("notes", "Bloco de Notas", "com.android.vending", 300, 0, 240, 25, 3)

        return listOf(
            DemoApp(
                flashlight,
                profile(
                    flashlight,
                    listOf("READ_SMS", "RECEIVE_SMS", "READ_CONTACTS", "RECORD_AUDIO", "SYSTEM_ALERT_WINDOW", "CAMERA"),
                    accessibility = true, accessibilityOn = true, debugCert = true,
                ),
            ),
            DemoApp(
                banker,
                profile(
                    banker,
                    listOf("RECEIVE_SMS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES"),
                    apkSha = LocalDemoMalwareDatabase.DEMO_SAMPLE_SHA256,
                ),
            ),
            DemoApp(recorder, profile(recorder, listOf("RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_CONTACTS"))),
            DemoApp(photo, profile(photo, listOf("CAMERA", "READ_MEDIA_IMAGES"))),
            DemoApp(game, profile(game, listOf("INTERNET"))),
            DemoApp(notes, profile(notes, emptyList())),
        )
    }

    fun apps(now: Long): List<InstalledApp> = build(now).map { it.app }

    fun securityProfiles(now: Long): List<SecurityProfile> = build(now).map { it.profile }

    fun isDemoPackage(packageName: String): Boolean = packageName.startsWith(PACKAGE_PREFIX)

    /** Arquivos fictícios: nada é apagado de verdade no modo demonstração. */
    fun files(now: Long): List<FileItem> {
        fun file(id: String, name: String, location: String, sizeMb: Long, mime: String?, category: FileCategory, daysAgo: Long) =
            FileItem(
                id = "demo://$id",
                name = name,
                location = location,
                sizeBytes = sizeMb * MB,
                mimeType = mime,
                category = category,
                dateModified = now - days(daysAgo),
                source = FileSource.DEMO,
            )
        return listOf(
            file("v1", "viagem_praia_4k.mp4", "DCIM/Camera/", 1_850, "video/mp4", FileCategory.VIDEO, 300),
            file("v2", "aniversario.mp4", "DCIM/Camera/", 420, "video/mp4", FileCategory.VIDEO, 120),
            file("a1", "podcast_episodio_12.mp3", "Download/", 85, "audio/mpeg", FileCategory.AUDIO, 90),
            file("d1", "manual_impressora.pdf", "Download/", 32, "application/pdf", FileCategory.DOCUMENT, 400),
            file("k1", "app_desconhecido.apk", "Download/", 48, "application/vnd.android.package-archive", FileCategory.APK, 60),
            file("t1", "video_incompleto.mp4.crdownload", "Download/", 230, null, FileCategory.TEMPORARY, 30),
            file("t2", "relatorio.tmp", "Documents/", 4, null, FileCategory.TEMPORARY, 10),
            file("i1", "IMG_20250101_120000.jpg", "DCIM/Camera/", 6, "image/jpeg", FileCategory.IMAGE, 270),
            file("i2", "IMG_20250101_120000 (1).jpg", "Pictures/WhatsApp/", 6, "image/jpeg", FileCategory.IMAGE, 260),
            file("i3", "IMG_20250101_120000 (2).jpg", "Download/", 6, "image/jpeg", FileCategory.IMAGE, 250),
        )
    }

    /** Hash fictício para os arquivos demo: i1, i2 e i3 são "idênticos". */
    fun demoFileHash(item: FileItem): String? = when (item.id) {
        "demo://i1", "demo://i2", "demo://i3" -> HashUtils.sha256("demo-duplicate-image")
        else -> HashUtils.sha256(item.id)
    }
}
