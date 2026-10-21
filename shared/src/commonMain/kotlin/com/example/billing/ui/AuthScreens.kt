package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.auth.*
import kotlinx.coroutines.launch

@Composable
private fun AuthCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF4F5F7)), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(16.dp),
        ) {
            Column(
                Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = Color.Gray)
                content()
                Spacer(Modifier.height(4.dp))
                BrandFooter(centered = true)
            }
        }
    }
}

@Composable
fun CheckingScreen() {
    Box(Modifier.fillMaxSize().background(Color(0xFFF4F5F7)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppGreen)
            Spacer(Modifier.height(16.dp))
            Text("Checking license...", color = Color(0xFF2C3E50), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ActivationScreen(
    license: LicenseManager,
    firebase: FirebaseApi,
    onActivated: () -> Unit,
    onDeveloperLoggedIn: (DevSession) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDev by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            val problem = license.activate(code)
            busy = false
            if (problem == null) onActivated() else error = problem
        }
    }

    AuthCard(
        title = "Activate Software",
        subtitle = "Add your SHOP CODE. (Internet Required)",
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Shop Code") },
            singleLine = true,
            enabled = !busy,
            shape = RoundedCornerShape(6.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
        Button(
            onClick = { submit() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Activate", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { showDev = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Developer login", fontSize = 12.sp, color = Color.Gray)
        }
    }

    if (showDev) {
        DeveloperLoginDialog(
            firebase = firebase,
            onSuccess = { showDev = false; onDeveloperLoggedIn(it) },
            onDismiss = { showDev = false },
        )
    }
}

@Composable
fun BlockedScreen(
    message: String,
    firebase: FirebaseApi,
    onRetry: () -> Unit,
    onDeveloperLoggedIn: (DevSession) -> Unit,
) {
    var showDev by remember { mutableStateOf(false) }

    AuthCard(title = "Software Closed", subtitle = "Subscription / License issue") {
        Text(message, fontSize = 14.sp, color = Color.DarkGray)
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) { Text("Try Again", fontWeight = FontWeight.Bold) }

        TextButton(onClick = { showDev = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Developer login", fontSize = 12.sp, color = Color.Gray)
        }
    }

    if (showDev) {
        DeveloperLoginDialog(
            firebase = firebase,
            onSuccess = { showDev = false; onDeveloperLoggedIn(it) },
            onDismiss = { showDev = false },
        )
    }
}

@Composable
private fun DeveloperLoginDialog(
    firebase: FirebaseApi,
    onSuccess: (DevSession) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            when (val r = firebase.developerSignIn(email.trim(), password)) {
                is DevResult.Success -> { busy = false; onSuccess(r.session) }
                is DevResult.Failed -> { busy = false; error = r.message }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Developer Login", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, singleLine = true, enabled = !busy,
                    shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = { Button(onClick = { submit() }, enabled = !busy, shape = RoundedCornerShape(6.dp)) { Text("Login", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        shape = RoundedCornerShape(8.dp), containerColor = Color.White
    )
}

@Composable
fun LoginScreen(
    services: AppServices,
    offline: Boolean,
    onUserLoggedIn: (AppUser) -> Unit,
    onDeveloperLoggedIn: (DevSession) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        val name = username.trim()
        if (name.isEmpty() || password.isEmpty()) {
            error = "Enter the username and password."
            return
        }
        scope.launch {
            busy = true
            error = null
            try {
                if ('@' in name) {
                    when (val r = services.firebase.developerSignIn(name, password)) {
                        is DevResult.Success -> onDeveloperLoggedIn(r.session)
                        is DevResult.Failed -> error = r.message
                    }
                } else {
                    when (val r = services.auth.login(name, password)) {
                        is LoginResult.Success -> onUserLoggedIn(r.user)
                        LoginResult.Wrong -> error = "Incorrect username or password."
                        LoginResult.Disabled -> error = "Account closed. Contact developer."
                        is LoginResult.Locked -> error = "Too many failures. Try after ${r.seconds}s."
                    }
                }
            } catch (e: Throwable) {
                error = "Login Issue: ${e.message ?: e}"
            } finally {
                busy = false
            }
        }
    }

    AuthCard(title = "Billing System", subtitle = "Log in to continue") {
        if (offline) {
            Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                Text("No internet: Running in offline mode.", color = Color(0xFFE65100), fontSize = 12.sp, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
            }
        }
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
        Button(
            onClick = { submit() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Login", fontWeight = FontWeight.Bold)
        }
        Text(
            text = "Need a new login or forgot password?\nContact the developer.",
            fontSize = 12.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}