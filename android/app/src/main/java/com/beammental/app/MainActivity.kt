package com.beammental.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.beammental.app.data.Locator
import com.beammental.app.screens.AuthScreen
import com.beammental.app.screens.ChatScreen
import com.beammental.app.screens.LegalScreen
import com.beammental.app.screens.OnboardingScreen
import com.beammental.app.screens.OrbLab
import com.beammental.app.screens.PrehladScreen
import com.beammental.app.screens.SettingsScreen
import com.beammental.app.screens.VoiceScreen
import com.beammental.app.ui.theme.BeamColors
import com.beammental.app.ui.theme.BeamTheme
import com.beammental.app.ui.theme.beamThemeByKey
import com.beammental.app.voice.DebugVoice
import kotlinx.coroutines.runBlocking

enum class Screen { Auth, Onboarding, Chat, Prehlad, Legal, Settings }

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // debug builds only: `am start --es debug_screen orb` opens the orb lab
        // so the volumetric march can be judged on-device without a live call
        if (BuildConfig.DEBUG && intent.getStringExtra("debug_screen") == "orb") {
            setContent { BeamTheme { OrbLab() } }
            return
        }

        // debug builds only: `am start --es debug_screen voice` opens the real
        // VoiceScreen driven by a scripted session so the redesigned orb UI can
        // be screenshotted on-device without a live Gemini call
        if (BuildConfig.DEBUG && intent.getStringExtra("debug_screen") == "voice") {
            setContent {
                BeamTheme {
                    val preview = remember { DebugVoice() }
                    VoiceScreen(onClose = {}, preview = preview)
                }
            }
            return
        }

        Locator.init(this)

        // restore session synchronously once, then let the UI drive state
        runBlocking {
            BeamColors.apply(beamThemeByKey(Locator.session.theme()))
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
                            onPrehlad = { screen = Screen.Prehlad },
                            onSettings = { screen = Screen.Settings },
                        )
                        Screen.Prehlad -> PrehladScreen(
                            onBack = { screen = Screen.Chat },
                        )
                        Screen.Legal -> LegalScreen(
                            onBack = { screen = Screen.Chat },
                        )
                        Screen.Settings -> SettingsScreen(
                            onPrehlad = { screen = Screen.Prehlad },
                            onLegal = { screen = Screen.Legal },
                            onBack = { screen = Screen.Chat },
                            onLoggedOut = { screen = Screen.Auth },
                        )
                    }
                }
            }
        }
    }
}
