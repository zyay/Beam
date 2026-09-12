package com.beammental.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.Locator
import com.beammental.app.ui.components.BeamButton
import com.beammental.app.ui.components.BeamTextField
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.effects.BeamSize
import com.beammental.app.ui.effects.borderBeam
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthed: (onboarded: Boolean) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        error = null
        if (mode == 1 && !consent) {
            error = "Pre vytvorenie účtu najprv potvrď súhlas s podmienkami."
            return
        }
        busy = true
        scope.launch {
            val result = if (mode == 1)
                Locator.api.signup(email.trim(), password, name.trim().ifBlank { null })
            else
                Locator.api.login(email.trim(), password)
            busy = false
            if (result.ok) onAuthed(result.hasProfile)
            else error = result.error
        }
    }

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        MeshBackground(Modifier.matchParentSize(), intensity = if (busy) 1f else 0.6f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).imePadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(Modifier.height(40.dp))
                    val awake = name.isNotBlank() || email.isNotBlank() || password.isNotBlank()
                    Entrance(0) {
                        MascotBlob(
                            modifier = Modifier.size(56.dp),
                            blobSize = 56.dp,
                            animation = when {
                                busy -> "thinking"
                                awake -> "curious"
                                else -> "sleeping"
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Entrance(1) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row {
                                Text("Beam", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist)
                                Text(" · mental health", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Fog)
                            }
                            Text("Kľudné miesto na rozhovor, keď ti nie je ľahko.", fontSize = 14.sp, color = BeamColors.Fog)
                        }
                    }
                    Spacer(Modifier.height(28.dp))

                    Entrance(2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BeamColors.Line, RoundedCornerShape(24.dp))
                                .background(BeamColors.Card, RoundedCornerShape(24.dp))
                                .borderBeam(size = BeamSize.MD, shape = RoundedCornerShape(24.dp), strength = 0.7f)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf("Prihlásenie", "Registrácia").forEachIndexed { i, label ->
                                val tabBg by animateColorAsState(
                                    if (mode == i) BeamColors.Accent else Color.Transparent,
                                    tween(200), label = "tab",
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(tabBg, RoundedCornerShape(20.dp))
                                        .clickable { mode = i; error = null }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label,
                                        fontSize = 14.sp,
                                        fontWeight = if (mode == i) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (mode == i) BeamColors.AccentInk else BeamColors.Fog,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    AnimatedVisibility(visible = mode == 1, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            BeamTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = "Ako sa voláš?",
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    AuthField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "E-mail",
                        keyboardType = KeyboardType.Email,
                    )
                    Spacer(Modifier.height(10.dp))
                    AuthField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = if (mode == 1) "Heslo (aspoň 8 znakov)" else "Heslo",
                        keyboardType = KeyboardType.Password,
                        password = true,
                        onDone = { submit() },
                    )

                    AnimatedVisibility(visible = mode == 1, enter = expandVertically(), exit = shrinkVertically()) {
                        Row(
                            Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = consent,
                                onCheckedChange = { consent = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BeamColors.Accent,
                                    checkmarkColor = BeamColors.AccentInk,
                                    uncheckedColor = BeamColors.Fog,
                                ),
                            )
                            Text(
                                "Súhlasím s Všeobecnými podmienkami a spracovaním údajov. Nie som mladší/ia ako 16 rokov.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = BeamColors.Fog,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { consent = !consent },
                            )
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xFFFF9FB0), fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(18.dp))
                    Entrance(3) {
                        BeamButton(
                            label = if (mode == 0) "Prihlásiť sa" else "Vytvoriť účet",
                            onClick = { submit() },
                            busy = busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }

            Text(
                "Beam nie je zdravotnícka pomôcka ani náhrada odbornej pomoci.\nV kríze volaj 0800 900 900 (nonstop).",
                fontSize = 12.sp,
                color = BeamColors.Fog,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    var showPassword by remember { mutableStateOf(false) }
    BeamTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailing = if (password) {
            {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (showPassword) "Skryť heslo" else "Ukázať heslo",
                        tint = BeamColors.Fog,
                    )
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Entrance(index: Int, content: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 90L)
        appear.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f))
    }
    Box(Modifier.graphicsLayer {
        alpha = appear.value
        translationY = (1f - appear.value) * 14.dp.toPx()
    }) { content() }
}
