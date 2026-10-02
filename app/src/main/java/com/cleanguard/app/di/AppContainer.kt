package com.cleanguard.app.di

import android.content.Context
import com.cleanguard.app.core.util.PackageManagerCompat
import com.cleanguard.app.data.apps.AppSizeProvider
import com.cleanguard.app.data.apps.InstalledAppsRepository
import com.cleanguard.app.data.apps.UsageStatsProvider
import com.cleanguard.app.data.session.SessionStore
import com.cleanguard.app.data.settings.SettingsRepository
import com.cleanguard.app.data.storage.DeviceStorageProvider
import com.cleanguard.app.data.storage.DocumentTreeScanner
import com.cleanguard.app.data.storage.FileDeleter
import com.cleanguard.app.data.storage.MediaStoreScanner
import com.cleanguard.app.data.storage.OwnCacheCleaner
import com.cleanguard.app.data.storage.StorageAnalyzer
import com.cleanguard.app.security.ApkHasher
import com.cleanguard.app.security.RiskClassifier
import com.cleanguard.app.security.SecurityProfileReader
import com.cleanguard.app.security.SecurityScanner
import com.cleanguard.app.security.db.ConsentGatedMalwareDatabase
import com.cleanguard.app.security.db.LocalDemoMalwareDatabase
import com.cleanguard.app.security.db.MalwareDatabase

/** Injeção de dependências manual (simples e sem bibliotecas extras). */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = SettingsRepository(appContext)
    val usageStats = UsageStatsProvider(appContext)
    private val appSizes = AppSizeProvider(appContext)
    val installedApps = InstalledAppsRepository(appContext, usageStats, appSizes)

    val deviceStorage = DeviceStorageProvider(appContext)
    private val mediaScanner = MediaStoreScanner(appContext)
    val treeScanner = DocumentTreeScanner(appContext)
    val storageAnalyzer = StorageAnalyzer(appContext, deviceStorage, mediaScanner, treeScanner)
    val fileDeleter = FileDeleter(appContext)
    val ownCache = OwnCacheCleaner(appContext)

    /**
     * Banco de assinaturas. Hoje: apenas o banco local de DEMONSTRAÇÃO e nenhum serviço remoto.
     * Para conectar um serviço legítimo, passe uma implementação de RemoteThreatIntelClient.
     */
    val malwareDatabase: MalwareDatabase = ConsentGatedMalwareDatabase(
        local = LocalDemoMalwareDatabase(),
        remote = null,
        hasConsent = { settings.current.remoteLookupConsent },
    )

    val riskClassifier = RiskClassifier()
    val securityScanner = SecurityScanner(
        appContext,
        SecurityProfileReader(appContext, ApkHasher()),
        malwareDatabase,
        riskClassifier,
    )

    val session = SessionStore()

    val selfPackage: String get() = appContext.packageName

    val appVersion: String = runCatching {
        PackageManagerCompat.packageInfo(appContext.packageManager, appContext.packageName, 0).versionName
    }.getOrNull() ?: "—"
}
