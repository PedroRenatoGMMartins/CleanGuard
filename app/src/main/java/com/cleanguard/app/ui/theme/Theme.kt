package com.cleanguard.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cleanguard.app.security.model.RiskLevel

private val LightColors = lightColorScheme(
    primary = Color(0xFF1558C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF00897B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4F1E7),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF6750A4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF22005D),
    background = Color(0xFFF5F8FF),
    onBackground = Color(0xFF181C22),
    surface = Color(0xFFF5F8FF),
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3FB),
    surfaceContainer = Color(0xFFEAEEF7),
    surfaceContainerHigh = Color(0xFFE4E8F1),
    surfaceContainerHighest = Color(0xFFDEE2EC),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF0B3D91),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFF5FD8C8),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB4F1E7),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378A),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF0E1420),
    onBackground = Color(0xFFE1E2E9),
    surface = Color(0xFF0E1420),
    onSurface = Color(0xFFE1E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF090E18),
    surfaceContainerLow = Color(0xFF161C28),
    surfaceContainer = Color(0xFF1A202C),
    surfaceContainerHigh = Color(0xFF242A37),
    surfaceContainerHighest = Color(0xFF2F3542),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Cores semânticas da classificação de risco (independentes do Material You). */
@Immutable
data class RiskColors(
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val onAttentionContainer: Color,
    val suspicious: Color,
    val suspiciousContainer: Color,
    val onSuspiciousContainer: Color,
) {
    fun accent(level: RiskLevel): Color = when (level) {
        RiskLevel.SAFE -> safe
        RiskLevel.ATTENTION -> attention
        RiskLevel.SUSPICIOUS -> suspicious
    }

    fun container(level: RiskLevel): Color = when (level) {
        RiskLevel.SAFE -> safeContainer
        RiskLevel.ATTENTION -> attentionContainer
        RiskLevel.SUSPICIOUS -> suspiciousContainer
    }

    fun onContainer(level: RiskLevel): Color = when (level) {
        RiskLevel.SAFE -> onSafeContainer
        RiskLevel.ATTENTION -> onAttentionContainer
        RiskLevel.SUSPICIOUS -> onSuspiciousContainer
    }
}

private val LightRisk = RiskColors(
    safe = Color(0xFF1B8A4B), safeContainer = Color(0xFFD3F5DE), onSafeContainer = Color(0xFF00391A),
    attention = Color(0xFFB26A00), attentionContainer = Color(0xFFFFE6C2), onAttentionContainer = Color(0xFF3D2300),
    suspicious = Color(0xFFC62828), suspiciousContainer = Color(0xFFFFDAD6), onSuspiciousContainer = Color(0xFF410002),
)

private val DarkRisk = RiskColors(
    safe = Color(0xFF6FDB9A), safeContainer = Color(0xFF0E4A2A), onSafeContainer = Color(0xFFD3F5DE),
    attention = Color(0xFFFFC266), attentionContainer = Color(0xFF5A3A00), onAttentionContainer = Color(0xFFFFE6C2),
    suspicious = Color(0xFFFF8A80), suspiciousContainer = Color(0xFF7A1212), onSuspiciousContainer = Color(0xFFFFDAD6),
)

val LocalRiskColors = staticCompositionLocalOf { LightRisk }

@Composable
fun CleanGuardTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalRiskColors provides if (darkTheme) DarkRisk else LightRisk) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CleanGuardTypography,
            content = content,
        )
    }
}
