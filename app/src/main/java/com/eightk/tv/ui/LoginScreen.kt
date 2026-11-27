package com.eightk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eightk.tv.data.XtreamClient
import com.eightk.tv.data.loginAny
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: (XtreamClient) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }

    fun submit() {
        if (loading) return
        if (user.isBlank() || pass.isBlank()) { error = "Shkruaj username dhe password"; return }
        loading = true; error = null
        scope.launch {
            try {
                onLoggedIn(loginAny(user.trim(), pass.trim()))
            } catch (e: Exception) {
                error = e.message ?: "Lidhja dështoi"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { userFocus.requestFocus() } }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = .25f),
        focusedLabelColor = Color.White, unfocusedLabelColor = TextDim,
        focusedContainerColor = Color.White.copy(alpha = .06f), unfocusedContainerColor = Color.White.copy(alpha = .03f),
        cursorColor = Accent,
    )

    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // Majtas: logo dhe slogani
        Column(Modifier.weight(1f).padding(start = 80.dp)) {
            Row {
                Text("8K", fontSize = 96.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("tv", fontSize = 96.sp, fontWeight = FontWeight.Black, color = Accent)
            }
            Text("Kanalet, filmat dhe serialet e tua", fontSize = 22.sp, color = TextDim)
            Text("në një vend.", fontSize = 22.sp, color = TextDim)
        }
        // Djathtas: forma e hyrjes
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(460.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF14181E).copy(alpha = .92f)).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Hyr", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Vendos të dhënat e abonimit", color = TextDim)
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Username") }, singleLine = true, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(userFocus),
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Password") }, singleLine = true, colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(passFocus),
                )
                error?.let { Text(it, color = Color(0xFFFF6B6B)) }
                Spacer(Modifier.height(4.dp))
                TvCard(
                    Modifier.fillMaxWidth().height(56.dp),
                    bg = Accent, scaleOnFocus = 1.03f, onClick = { submit() },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                Text("  Duke u lidhur…", fontSize = 16.sp)
                            }
                        } else {
                            Text("HYR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
