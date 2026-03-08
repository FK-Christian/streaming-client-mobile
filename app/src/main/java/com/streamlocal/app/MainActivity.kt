package com.streamlocal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.streamlocal.app.data.preferences.AppPreferences
import com.streamlocal.app.ui.navigation.AppNavigation
import com.streamlocal.app.ui.navigation.Routes
import com.streamlocal.app.ui.splash.SplashScreen
import com.streamlocal.app.ui.theme.StreamLocalTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val preferences by lazy { AppPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StreamLocalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    AppEntryPoint(preferences = preferences)
                }
            }
        }
    }
}

/**
 * Point d'entrée de l'application.
 *
 * Pendant la lecture asynchrone des préférences (DataStore), affiche le [SplashScreen].
 * Dès que la destination est déterminée, un [Crossfade] enchaîne en douceur
 * vers la navigation principale (LOGIN ou SERVER_SETUP).
 */
@Composable
fun AppEntryPoint(preferences: AppPreferences) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val serverUrl = preferences.serverUrlFlow.first()
        startDestination = if (serverUrl.isNullOrBlank()) {
            Routes.SERVER_SETUP
        } else {
            Routes.LOGIN
        }
    }

    // Crossfade : null → splash, string → navigation (transition 400ms)
    Crossfade(
        targetState   = startDestination,
        animationSpec = tween(400),
        label         = "splash_to_nav"
    ) { destination ->
        if (destination == null) {
            SplashScreen()
        } else {
            AppNavigation(startDestination = destination)
        }
    }
}
