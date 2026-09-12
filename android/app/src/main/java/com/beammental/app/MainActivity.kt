package com.beammental.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.beammental.app.data.Locator
import com.beammental.app.screens.AuthScreen
import com.beammental.app.screens.ChatScreen
import com.beammental.app.screens.LegalScreen
import com.beammental.app.screens.OnboardingScreen
import com.beammental.app.screens.OrbLab
import com.beammental.app.screens.PrehladScreen
import com.beammental.app.screens.SettingsScreen
import com.beammental.app.screens.VoiceScreen
import com.beammental.app.ui.components.BeamNavBar
import com.beammental.app.ui.components.NavItem
import com.beammental.app.ui.theme.BeamColors
import com.beammental.app.ui.theme.BeamTheme
import com.beammental.app.ui.theme.beamThemeByKey
import com.beammental.app.voice.DebugVoice
import kotlinx.coroutines.runBlocking
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChatBubble

enum class Screen { Auth, Onboarding, Chat, Prehlad, Legal, Settings, Voice }

private val NAV_ITEMS = listOf(
    NavItem("Rozhovor", Icons.Rounded.ChatBubble),
    NavItem("Prehľad", Icons.Outlined.Insights),
    NavItem("Nastavenia", Icons.Outlined.Settings),
)

private val NAV_SCREENS = listOf(Screen.Chat, Screen.Prehlad, Screen.Settings)

private fun screenDepth(s: Screen): Int = when (s) {
    Screen.Auth, Screen.Onboarding -> 0
    Screen.Chat -> 1
    Screen.Prehlad, Screen.Settings -> 2
    Screen.Legal -> 3
    Screen.Voice -> -1
}

private fun screenTransition(
    from: Screen,
    to: Screen,
): androidx.compose.animation.ContentTransform {
    val slideDur = 320
    val fadeDur = 200
    if (to == Screen.Voice) {
        return (
            fadeIn(tween(fadeDur)) + slideInVertically(tween(slideDur)) { it }
        ) togetherWith (
            fadeOut(tween(fadeDur)) + slideOutVertically(tween(slideDur)) { it / 3 }
        )
    }
    if (from == Screen.Voice) {
        return (
            fadeIn(tween(fadeDur)) + slideInVertically(tween(slideDur)) { it / 3 }
        ) togetherWith (
            fadeOut(tween(fadeDur)) + slideOutVertically(tween(slideDur)) { it }
        )
    }
    val fromDepth = screenDepth(from)
    val toDepth = screenDepth(to)
    val goingDeeper = toDepth > fromDepth
    return if (goingDeeper) {
        (
            fadeIn(tween(fadeDur)) + slideInHorizontally(tween(slideDur)) { it }
        ) togetherWith (
            fadeOut(tween(fadeDur)) + slideOutHorizontally(tween(slideDur)) { -it / 3 }
        )
    } else {
        (
            fadeIn(tween(fadeDur)) + slideInHorizontally(tween(slideDur)) { -it }
        ) togetherWith (
            fadeOut(tween(fadeDur)) + slideOutHorizontally(tween(slideDur)) { it / 3 }
        )
    }
}

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (BuildConfig.DEBUG && intent.getStringExtra("debug_screen") == "orb") {
            setContent { BeamTheme { OrbLab() } }
            return
        }

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
                val showsNav = current in NAV_SCREENS
                var navIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(current) {
                    val i = NAV_SCREENS.indexOf(current)
                    if (i >= 0) navIndex = i
                }

                Column(Modifier.fillMaxSize().background(BeamColors.Ink)) {
                    Box(Modifier.weight(1f).imePadding()) {
                        AnimatedContent(
                            targetState = current,
                            transitionSpec = {
                                screenTransition(initialState, targetState)
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
                                    onVoice = { screen = Screen.Voice },
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
                                Screen.Voice -> VoiceScreen(
                                    onClose = { screen = Screen.Chat },
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showsNav,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
                        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
                    ) {
                        BeamNavBar(
                            items = NAV_ITEMS,
                            selectedIndex = navIndex,
                            onSelect = { screen = NAV_SCREENS[it] },
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }
}
