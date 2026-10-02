package com.cleanguard.app

import com.cleanguard.app.demo.DemoData
import com.cleanguard.app.security.InstallSourceClassifier
import com.cleanguard.app.security.RiskClassifier
import com.cleanguard.app.security.db.LocalDemoMalwareDatabase
import com.cleanguard.app.security.model.InstallSource
import com.cleanguard.app.security.model.MalwareMatch
import com.cleanguard.app.security.model.MatchType
import com.cleanguard.app.security.model.RiskLevel
import com.cleanguard.app.security.model.SecurityProfile
import com.cleanguard.app.security.model.SignerCertificate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes da classificação de risco e do banco de assinaturas (sem malware real). */
class RiskClassificationTest {

    private val classifier = RiskClassifier()
    private val p = "android.permission."

    private fun profile(
        pkg: String = "com.example.app",
        installer: String? = "com.android.vending",
        perms: List<String> = emptyList(),
        system: Boolean = false,
        launcher: Boolean = true,
        accessibility: Boolean = false,
        accessibilityOn: Boolean = false,
        adminOn: Boolean = false,
        debugCert: Boolean = false,
        targetSdk: Int = 34,
    ) = SecurityProfile(
        packageName = pkg,
        label = pkg,
        versionName = "1",
        isSystemApp = system,
        isUpdatedSystemApp = false,
        hasLauncherEntry = launcher,
        targetSdk = targetSdk,
        firstInstallTime = 1,
        lastUpdateTime = 1,
        origin = InstallSourceClassifier.classify(installer, null, system, false),
        requestedPermissions = perms.map { p + it },
        grantedPermissions = emptySet(),
        declaresAccessibilityService = accessibility,
        accessibilityServiceEnabled = accessibilityOn,
        declaresDeviceAdmin = adminOn,
        deviceAdminActive = adminOn,
        declaresNotificationListener = false,
        certificates = listOf(
            SignerCertificate("ab".repeat(32), if (debugCert) "CN=Android Debug,O=Android,C=US" else "CN=Dev", null, null),
        ),
        apkSha256 = null,
        apkSizeBytes = 1,
    )

    @Test
    fun `app da Play Store com permissoes comuns e seguro`() {
        val result = classifier.assess(profile(perms = listOf("CAMERA", "INTERNET")), null)
        assertEquals(RiskLevel.SAFE, result.level)
    }

    @Test
    fun `permissoes sensiveis sozinhas nao tornam um app suspeito`() {
        val result = classifier.assess(profile(perms = listOf("READ_SMS", "READ_CONTACTS", "RECORD_AUDIO")), null)
        assertTrue(result.level != RiskLevel.SUSPICIOUS)
        val explanation = result.findings.first { it.id == "sensitive_groups" }.explanation
        assertTrue(explanation.contains("sms") && explanation.contains("contatos") && explanation.contains("microfone"))
        assertTrue(explanation.contains("podem ser legítimas"))
    }

    @Test
    fun `app instalado fora da loja com permissoes sensiveis pede atencao`() {
        val result = classifier.assess(
            profile(installer = "com.google.android.packageinstaller", perms = listOf("RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_CONTACTS")),
            null,
        )
        assertEquals(RiskLevel.ATTENTION, result.level)
        assertTrue(result.findings.any { it.id == "sideloaded" })
    }

    @Test
    fun `combinacao de trojan bancario e potencialmente suspeita`() {
        val result = classifier.assess(
            profile(
                installer = "com.android.chrome",
                perms = listOf("RECEIVE_SMS", "READ_SMS", "SYSTEM_ALERT_WINDOW"),
                accessibility = true,
                accessibilityOn = true,
            ),
            null,
        )
        assertEquals(RiskLevel.SUSPICIOUS, result.level)
        assertTrue(result.findings.any { it.id == "banking_trojan_pattern" })
        assertTrue(result.score >= RiskClassifier.SUSPICIOUS_THRESHOLD)
    }

    @Test
    fun `correspondencia no banco leva direto a potencialmente suspeito`() {
        val match = MalwareMatch("x", MatchType.APK_SHA256, "Teste", RiskLevel.SUSPICIOUS, "Banco teste")
        val result = classifier.assess(profile(), match)
        assertEquals(RiskLevel.SUSPICIOUS, result.level)
        assertTrue(result.summary.contains("Teste"))
    }

    @Test
    fun `apps do sistema nao sao sinalizados por permissoes`() {
        val result = classifier.assess(
            profile(system = true, installer = null, perms = listOf("READ_SMS", "SEND_SMS", "READ_CALL_LOG", "RECORD_AUDIO", "CAMERA"), launcher = false),
            null,
        )
        assertEquals(RiskLevel.SAFE, result.level)
    }

    @Test
    fun `nome que imita o sistema e certificado de depuracao contam pontos`() {
        val result = classifier.assess(profile(pkg = "com.android.systemupdate", installer = "com.android.chrome", debugCert = true), null)
        assertTrue(result.findings.any { it.id == "impersonation" })
        assertTrue(result.findings.any { it.id == "debug_cert" })
        assertEquals(RiskLevel.SUSPICIOUS, result.level)
    }

    @Test
    fun `todo motivo pontuado tem explicacao e nenhum texto chama o app de virus`() {
        val result = classifier.assess(
            profile(installer = null, perms = listOf("READ_SMS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES"), launcher = false, adminOn = true, targetSdk = 19),
            null,
        )
        result.findings.forEach {
            assertTrue(it.title.isNotBlank() && it.explanation.isNotBlank())
            assertFalse(it.explanation.contains("vírus", ignoreCase = true))
        }
    }

    @Test
    fun `origem da instalacao`() {
        assertEquals(InstallSource.OFFICIAL_STORE, InstallSourceClassifier.classify("com.android.vending", null, false, false).source)
        assertEquals(InstallSource.ALTERNATIVE_STORE, InstallSourceClassifier.classify("com.aurora.store", null, false, false).source)
        assertEquals(InstallSource.SIDELOADED, InstallSourceClassifier.classify("com.google.android.packageinstaller", null, false, false).source)
        assertEquals(InstallSource.ADB, InstallSourceClassifier.classify(null, "com.android.shell", false, false).source)
        assertEquals(InstallSource.UNKNOWN, InstallSourceClassifier.classify(null, null, false, false).source)
        assertEquals(InstallSource.PREINSTALLED, InstallSourceClassifier.classify(null, null, true, false).source)
    }

    @Test
    fun `banco demo encontra apenas assinaturas de teste`() = runBlocking {
        val db = LocalDemoMalwareDatabase()
        assertTrue(db.info.isDemo)
        assertFalse(db.info.usesNetwork)
        assertNotNull(db.lookupSha256(LocalDemoMalwareDatabase.EICAR_SHA256))
        assertTrue(db.isKnownThreat(LocalDemoMalwareDatabase.DEMO_SAMPLE_SHA256.uppercase()))
        assertEquals(RiskLevel.SUSPICIOUS, db.riskLevel(LocalDemoMalwareDatabase.DEMO_SAMPLE_SHA256))
        assertTrue(db.threatName(LocalDemoMalwareDatabase.DEMO_SAMPLE_SHA256)!!.contains("fictícia"))
        assertNotNull(db.lookupSignature(LocalDemoMalwareDatabase.DEMO_CERT_SHA256))
        // Um hash de APK não deve corresponder como certificado, e vice-versa.
        assertNull(db.lookupSignature(LocalDemoMalwareDatabase.EICAR_SHA256))
        assertNull(db.lookupSha256("0".repeat(64)))
    }

    @Test
    fun `modo demonstracao produz os tres niveis de risco`() = runBlocking {
        val db = LocalDemoMalwareDatabase()
        val results = DemoData.securityProfiles(now = 1_780_000_000_000L).associate { prof ->
            val match = prof.apkSha256?.let { db.lookupSha256(it) }
                ?: prof.certificates.firstNotNullOfOrNull { db.lookupSignature(it.sha256) }
            prof.packageName.removePrefix(DemoData.PACKAGE_PREFIX) to classifier.assess(prof, match).level
        }
        assertEquals(RiskLevel.SUSPICIOUS, results["flashlight"])
        assertEquals(RiskLevel.SUSPICIOUS, results["fakebanker"])
        assertEquals(RiskLevel.ATTENTION, results["recorder"])
        assertEquals(RiskLevel.SAFE, results["photoeditor"])
        assertEquals(RiskLevel.SAFE, results["notes"])
    }
}
