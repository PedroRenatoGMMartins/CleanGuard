package com.cleanguard.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("Seguir o sistema"),
    LIGHT("Claro"),
    DARK("Escuro"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Cores do papel de parede (Material You, Android 12+). */
    val dynamicColor: Boolean = false,
    /** Inclui apps e arquivos FICTÍCIOS para testar o app sem malware real. */
    val demoMode: Boolean = false,
    val unusedThresholdDays: Int = 30,
    /** Calcula SHA-256 também dos apps do sistema (mais lento). */
    val hashSystemApps: Boolean = false,
    /** Consentimento para consultar um serviço externo de reputação (nenhum configurado nesta versão). */
    val remoteLookupConsent: Boolean = false,
)

/** Preferências locais (SharedPreferences). Nada é sincronizado nem enviado para fora do aparelho. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, updated.themeMode.name)
            .putBoolean(KEY_DYNAMIC, updated.dynamicColor)
            .putBoolean(KEY_DEMO, updated.demoMode)
            .putInt(KEY_THRESHOLD, updated.unusedThresholdDays)
            .putBoolean(KEY_HASH_SYSTEM, updated.hashSystemApps)
            .putBoolean(KEY_REMOTE_CONSENT, updated.remoteLookupConsent)
            .apply()
        _settings.value = updated
    }

    private fun read(): AppSettings = AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM,
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        demoMode = prefs.getBoolean(KEY_DEMO, false),
        unusedThresholdDays = prefs.getInt(KEY_THRESHOLD, 30),
        hashSystemApps = prefs.getBoolean(KEY_HASH_SYSTEM, false),
        remoteLookupConsent = prefs.getBoolean(KEY_REMOTE_CONSENT, false),
    )

    companion object {
        private const val PREFS_NAME = "cleanguard_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_DEMO = "demo_mode"
        private const val KEY_THRESHOLD = "unused_threshold_days"
        private const val KEY_HASH_SYSTEM = "hash_system_apps"
        private const val KEY_REMOTE_CONSENT = "remote_lookup_consent"
        val THRESHOLD_OPTIONS = listOf(15, 30, 60, 90)
    }
}
