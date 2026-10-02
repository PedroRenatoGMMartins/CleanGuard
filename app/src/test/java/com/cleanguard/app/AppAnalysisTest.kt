package com.cleanguard.app

import com.cleanguard.app.domain.apps.AppFilter
import com.cleanguard.app.domain.apps.AppListFilter
import com.cleanguard.app.domain.apps.AppStorage
import com.cleanguard.app.domain.apps.AppUsage
import com.cleanguard.app.domain.apps.AppUsageClassifier
import com.cleanguard.app.domain.apps.InstalledApp
import com.cleanguard.app.domain.apps.UsageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Testes da análise de aplicativos (uso, filtros e ordenação). */
class AppAnalysisTest {

    private val now = 1_780_000_000_000L
    private fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)

    private fun app(
        pkg: String,
        label: String = pkg,
        system: Boolean = false,
        installedDaysAgo: Long = 365,
        lastUsedDaysAgo: Long? = 1,
        fgMinutes: Long = 60,
        size: Long = 10_000_000,
        usageKnown: Boolean = true,
    ) = InstalledApp(
        packageName = pkg,
        label = label,
        isSystemApp = system,
        isUpdatedSystemApp = false,
        hasLauncherEntry = true,
        isEnabled = true,
        firstInstallTime = daysAgo(installedDaysAgo),
        lastUpdateTime = daysAgo(installedDaysAgo),
        versionName = "1.0",
        versionCode = 1,
        targetSdk = 34,
        apkSizeBytes = size / 2,
        storage = AppStorage(appBytes = size / 2, dataBytes = size / 2, cacheBytes = 0),
        usage = if (usageKnown) AppUsage(lastUsedDaysAgo?.let { daysAgo(it) } ?: 0L, TimeUnit.MINUTES.toMillis(fgMinutes)) else null,
        installerPackage = "com.android.vending",
    )

    private val classifier = AppUsageClassifier(unusedThresholdDays = 30)

    @Test
    fun `app sem uso ha mais que o limite e considerado nao utilizado`() {
        assertTrue(classifier.isUnused(app("a", lastUsedDaysAgo = 45), now))
        assertFalse(classifier.isUnused(app("b", lastUsedDaysAgo = 5), now))
    }

    @Test
    fun `app sem nenhum registro de uso e instalado ha muito tempo e nao utilizado`() {
        assertTrue(classifier.isUnused(app("a", lastUsedDaysAgo = null), now))
    }

    @Test
    fun `app recem instalado nunca e marcado como nao utilizado`() {
        assertFalse(classifier.isUnused(app("a", installedDaysAgo = 3, lastUsedDaysAgo = null), now))
    }

    @Test
    fun `apps do sistema e apps sem dados de uso nunca sao marcados`() {
        assertFalse(classifier.isUnused(app("sys", system = true, lastUsedDaysAgo = 300), now))
        assertFalse(classifier.isUnused(app("x", usageKnown = false), now))
    }

    @Test
    fun `indicador de frequencia de uso`() {
        assertEquals(UsageLevel.UNKNOWN, classifier.usageLevel(app("a", usageKnown = false), now))
        assertEquals(UsageLevel.INACTIVE, classifier.usageLevel(app("b", lastUsedDaysAgo = 90), now))
        assertEquals(UsageLevel.LOW, classifier.usageLevel(app("c", fgMinutes = 2), now))
        assertEquals(UsageLevel.MEDIUM, classifier.usageLevel(app("d", fgMinutes = 30), now))
        assertEquals(UsageLevel.HIGH, classifier.usageLevel(app("e", fgMinutes = 300), now))
    }

    @Test
    fun `limite configuravel muda a classificacao`() {
        val strict = AppUsageClassifier(unusedThresholdDays = 15)
        val lenient = AppUsageClassifier(unusedThresholdDays = 90)
        val a = app("a", lastUsedDaysAgo = 20)
        assertTrue(strict.isUnused(a, now))
        assertFalse(lenient.isUnused(a, now))
    }

    @Test
    fun `filtros e ordenacao`() {
        val apps = listOf(
            app("com.big", "Zeta", size = 900_000_000, lastUsedDaysAgo = 2),
            app("com.old", "Alfa", installedDaysAgo = 2000, lastUsedDaysAgo = 100),
            app("com.sys", "Sistema", system = true, size = 2_000_000_000),
            app("com.mid", "Beta", installedDaysAgo = 500, lastUsedDaysAgo = null),
        )
        val filter = AppListFilter(classifier)

        assertEquals(listOf("Alfa", "Beta", "Sistema", "Zeta"), filter.apply(apps, AppFilter.ALL, now).map { it.label })
        assertEquals(listOf("com.sys", "com.big"), filter.apply(apps, AppFilter.LARGEST, now).take(2).map { it.packageName })
        assertEquals("com.old", filter.apply(apps, AppFilter.OLDEST, now).first().packageName)
        assertTrue(filter.apply(apps, AppFilter.OLDEST, now).none { it.isSystemApp })
        assertEquals(listOf("com.sys"), filter.apply(apps, AppFilter.SYSTEM, now).map { it.packageName })
        assertEquals(3, filter.apply(apps, AppFilter.USER, now).size)
        // Não utilizados: o sem registro aparece primeiro.
        assertEquals(listOf("com.mid", "com.old"), filter.apply(apps, AppFilter.UNUSED, now).map { it.packageName })
    }

    @Test
    fun `busca por nome ou pacote ignora maiusculas`() {
        val apps = listOf(app("com.example.camera", "Câmera"), app("com.example.notes", "Notas"))
        val result = AppListFilter(classifier).apply(apps, AppFilter.ALL, now, query = "NOTES")
        assertEquals(listOf("com.example.notes"), result.map { it.packageName })
    }

    @Test
    fun `somente apps do usuario podem ser desinstalados`() {
        assertTrue(AppListFilter.canUninstall(app("a"), selfPackage = "com.cleanguard.app"))
        assertFalse(AppListFilter.canUninstall(app("s", system = true), selfPackage = "com.cleanguard.app"))
        assertFalse(AppListFilter.canUninstall(app("com.cleanguard.app"), selfPackage = "com.cleanguard.app"))
        assertFalse(AppListFilter.canUninstall(app("d").copy(isDemo = true), selfPackage = "com.cleanguard.app"))
    }
}
