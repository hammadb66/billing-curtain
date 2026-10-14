package com.example.billing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppServices
import com.example.billing.auth.AppUser
import com.example.billing.auth.DevResult
import com.example.billing.auth.DevSession
import com.example.billing.auth.FirebaseApi
import com.example.billing.auth.LicenseManager
import com.example.billing.auth.LoginResult
import kotlinx.coroutines.launch

/** Sab auth screens ka common dabba (beech me safed card). */
@Composable
private fun AuthCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(16.dp),
        ) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppGreen)
                if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = Color.Gray)
                content()
                Spacer(Modifier.height(4.dp))
                BrandFooter(centered = true)
            }
        }
    }
}

/** Software khulte hi license check hote waqt */
@Composable
fun CheckingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppGreen)
            Spacer(Modifier.height(16.dp))
            Text("Checking license...")
        }
    }
}

/** Pehli dafa: shop code likhwana */
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
        subtitle = "Add your SHOPE CODE. (InterConnect Required)",
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Shop Code") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Activate")
            }
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

/** Subscription khatam / license OFF. Developer yahan se seedha Admin Panel me ja sakta hai. */
@Composable
fun BlockedScreen(
    message: String,
    firebase: FirebaseApi,
    onRetry: () -> Unit,
    onDeveloperLoggedIn: (DevSession) -> Unit,
) {
    var showDev by remember { mutableStateOf(false) }

    AuthCard(title = "The software is closed.", subtitle = "Subscription / License issue") {
        Text(message, fontSize = 15.sp)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
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

/** Developer ka email aur password (Firebase se check hota hai). */
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
                is DevResult.Success -> {
                    busy = false
                    onSuccess(r.session)
                }
                is DevResult.Failed -> {
                    busy = false
                    error = r.message
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Developer login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { submit() }) { Text("Login") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Rozana ka login. Developer apna email likhe to admin panel khulta hai. */
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
                    // email = developer login (Firebase se check hota hai)
                    when (val r = services.firebase.developerSignIn(name, password)) {
                        is DevResult.Success -> onDeveloperLoggedIn(r.session)
                        is DevResult.Failed -> error = r.message
                    }
                } else {
                    when (val r = services.auth.login(name, password)) {
                        is LoginResult.Success -> onUserLoggedIn(r.user)
                        LoginResult.Wrong -> error = "Incorrect username or password."
                        LoginResult.Disabled -> error = "This account is closed. Contact the developer."
                        is LoginResult.Locked ->
                            error = "Too many failed attempts. Try again after ${r.seconds} seconds."
                    }
                }
            } catch (e: Throwable) {
                error = "Login Issue: ${e.message ?: e}"
            } finally {
                busy = false
            }
        }
    }

    AuthCard(title = "Billing System", subtitle = "Log in") {
        if (offline) {
            Text(
                "No internet connection: Running in offline mode.",
                color = Color(0xFFE65100),
                fontSize = 12.sp,
            )
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Login")
            }
        }
        Text(
            text = "Need a new login or forgot your password?\n Contact the developer.",
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }
}
