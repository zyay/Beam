package com.beammental.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beammental.app.data.Locator
import com.beammental.app.screens.AuthScreen
import com.beammental.app.screens.ChatScreen
import com.beammental.app.screens.OnboardingScreen
import com.beammental.app.screens.SettingsScreen
import com.beammental.app.ui.theme.BeamTheme
import kotlinx.coroutines.runBlocking

enum class Screen { Auth, Onboarding, Chat, Settings }

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Locator.init(this)

        // restore session synchronously once, then let the UI drive state
        runBlocking {
            val token = Locator.session.token()
            screen = when {
                token == null -> Screen.Auth
                !Locator.session.onboarded() -> Screen.Onboarding
                else -> Screen.Chat
            }
        }

        setContent {
            BeamTheme {
                val current = screen ?: return@BeamTheme
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                    },
                    label = "nav",
                ) { s ->
                    when (s) {
                        Screen.Auth -> AuthScreen(
                            onAuthed = { onboarded ->
                                screen = if (onboarded) Screen.Chat else Screen.Onboarding
                            }
                        )
                        Screen.Onboarding -> OnboardingScreen(
                            onDone = { screen = Screen.Chat }
                        )
                        Screen.Chat -> ChatScreen(
                            onSettings = { screen = Screen.Settings }
                        )
                        Screen.Settings -> SettingsScreen(
                            onBack = { screen = Screen.Chat },
                            onLoggedOut = { screen = Screen.Auth },
                        )
                    }
                }
            }
        }
    }
}
