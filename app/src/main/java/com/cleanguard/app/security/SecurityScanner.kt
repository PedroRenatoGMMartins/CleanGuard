package com.cleanguard.app.security

import android.content.Context
import com.cleanguard.app.core.util.PackageManagerCompat
import com.cleanguard.app.demo.DemoData
import com.cleanguard.app.security.db.MalwareDatabase
import com.cleanguard.app.security.model.AppRiskAssessment
import com.cleanguard.app.security.model.ScanReport
import com.cleanguard.app.security.model.SecurityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

sealed interface ScanProgress {
    data class Running(val current: Int, val total: Int, val currentLabel: String) : ScanProgress {
        val fraction: Float get() = if (total == 0) 0f else current.toFloat() / total
    }

    data class Finished(val report: ScanReport) : ScanProgress
}

/**
 * Security Scan: percorre os apps instalados, monta o perfil de cada um, consulta o banco de
 * assinaturas e aplica as heurísticas. Todo o processamento é local.
 */
class SecurityScanner(
    context: Context,
    private val reader: SecurityProfileReader,
    private val database: MalwareDatabase,
    private val classifier: RiskClassifier,
) {
    private val appContext = context.applicationContext

    fun scan(hashSystemApps: Boolean, demoMode: Boolean): Flow<ScanProgress> = flow {
        val startedAt = System.currentTimeMillis()
        val pm = appContext.packageManager
        val packages = PackageManagerCompat.installedApplications(pm)
            .map { it.packageName }
            .filter { it != appContext.packageName } // o CleanGuard não se autoavalia
            .sorted()
        val demoProfiles = if (demoMode) DemoData.securityProfiles(startedAt) else emptyList()
        val total = packages.size + demoProfiles.size

        val capabilities = reader.activeCapabilities()
        val launcher = reader.launcherPackages()
        val results = ArrayList<AppRiskAssessment>(total)
        var hashed = 0
        var index = 0

        emit(ScanProgress.Running(0, total, "Preparando…"))

        for (pkg in packages) {
            currentCoroutineContext().ensureActive()
            index++
            val profile = reader.read(pkg, capabilities, launcher, hashSystemApps) ?: continue
            if (profile.apkSha256 != null) hashed++
            results += assess(profile)
            emit(ScanProgress.Running(index, total, profile.label))
        }
        for (profile in demoProfiles) {
            index++
            if (profile.apkSha256 != null) hashed++
            results += assess(profile)
            emit(ScanProgress.Running(index, total, profile.label))
        }

        val dbInfo = database.info
        emit(
            ScanProgress.Finished(
                ScanReport(
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis(),
                    assessments = results,
                    databaseName = dbInfo.name,
                    databaseIsDemo = dbInfo.isDemo,
                    hashedApps = hashed,
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

    /** Avalia um único app (tela de detalhes, quando ainda não houve verificação completa). */
    suspend fun assessPackage(packageName: String, hashSystemApps: Boolean): AppRiskAssessment? =
        withContext(Dispatchers.IO) {
            if (DemoData.isDemoPackage(packageName)) {
                DemoData.securityProfiles(System.currentTimeMillis())
                    .firstOrNull { it.packageName == packageName }
                    ?.let { assess(it) }
            } else {
                reader.read(packageName, reader.activeCapabilities(), reader.launcherPackages(), hashSystemApps)
                    ?.let { assess(it) }
            }
        }

    private suspend fun assess(profile: SecurityProfile): AppRiskAssessment {
        val match = profile.apkSha256?.let { database.lookupSha256(it) }
            ?: profile.certificates.firstNotNullOfOrNull { database.lookupSignature(it.sha256) }
        return classifier.assess(profile, match)
    }
}
