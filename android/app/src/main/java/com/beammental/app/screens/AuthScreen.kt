package com.beammental.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.Locator
import com.beammental.app.ui.effects.BlueprintGrid
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthed: (onboarded: Boolean) -> Unit) {
    var mode by remember { mutableIntStateOf(0) } // 0 login, 1 signup
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        error = null
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

    Column(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        Box(Modifier.weight(1f)) {
            BlueprintGrid(Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(40.dp))
                MascotBlob(modifier = Modifier.size(30.dp), blobSize = 30.dp)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("Beam", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist)
                    Text(" · mental health", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Fog)
                }
                Text("Kľudné miesto na rozhovor, keď ti nie je ľahko.", fontSize = 14.sp, color = BeamColors.Fog)
                Spacer(Modifier.height(28.dp))

                // tab switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BeamColors.Line, RoundedCornerShape(24.dp))
                        .background(BeamColors.Card, RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("Prihlásenie", "Registrácia").forEachIndexed { i, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (mode == i) BeamColors.Sage else Color.Transparent,
                                    RoundedCornerShape(20.dp),
                                )
                                .clickable(enabled = true) { mode = i; error = null }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = if (mode == i) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (mode == i) BeamColors.SageInk else BeamColors.Fog,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                AnimatedVisibility(visible = mode == 1, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        Field(Icons.Outlined.Person, "Ako sa voláš?", name, { name = it })
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Field(Icons.Outlined.Mail, "E-mail", email, { email = it }, KeyboardType.Email)
                Spacer(Modifier.height(10.dp))
                Field(
                    Icons.Outlined.Lock,
                    if (mode == 1) "Heslo (aspoň 8 znakov)" else "Heslo",
                    password,
                    { password = it },
                    KeyboardType.Password,
                    password = true,
                )

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF9FB0), fontSize = 14.sp)
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable(enabled = !busy) { submit() },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .alpha(if (busy) 0.5f else 1f)
                            .background(
                                BeamColors.Sage,
                                RoundedCornerShape(16.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (busy) {
                                CircularProgressIndicator(color = BeamColors.SageInk, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Rounded.Send, null, tint = BeamColors.SageInk, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (mode == 0) "Prihlásiť sa" else "Vytvoriť účet",
                                    color = BeamColors.SageInk,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
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

@Composable
private fun Field(
    icon: ImageVector,
    placeholder: String,
    value: String,
    onValue: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(placeholder, color = BeamColors.Fog) },
        leadingIcon = { Icon(icon, null, tint = BeamColors.Fog) },
        trailingIcon = if (password) {
            {
                Icon(
                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (showPassword) "Skryť heslo" else "Ukázať heslo",
                    tint = BeamColors.Fog,
                    modifier = Modifier.clickable { showPassword = !showPassword },
                )
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BeamColors.Sage,
            unfocusedBorderColor = BeamColors.Line,
            focusedContainerColor = Color(0xFF1D1913),
            unfocusedContainerColor = Color(0xFF1D1913),
            cursorColor = BeamColors.Sage,
        ),
        visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
