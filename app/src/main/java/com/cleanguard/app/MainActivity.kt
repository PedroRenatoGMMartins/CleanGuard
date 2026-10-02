package com.cleanguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanguard.app.data.settings.ThemeMode
import com.cleanguard.app.ui.navigation.CleanGuardNavHost
import com.cleanguard.app.ui.theme.CleanGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen oficial (API SplashScreen, compatível com Android 8+ via androidx.core).
        installSplashScreen().setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction { provider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CleanGuardApplication).container

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CleanGuardTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                CleanGuardNavHost()
            }
        }
    }
}
