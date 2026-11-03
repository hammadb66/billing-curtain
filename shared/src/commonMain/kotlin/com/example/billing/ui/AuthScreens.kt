package com.example.billing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    title: String, subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AppTheme.Bg, AppTheme.SurfaceAlt))
        ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(AppTheme.RadiusXl),
            color = AppTheme.Surface,
            border = BorderStroke(1.dp, AppTheme.Border),
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(20.dp),
        ) {
            Column(
                Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconCircle(Icons.Outlined.Lock, AppGreen, size = 56, iconSize = 28)
                Spacer(Modifier.height(4.dp))
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = AppTheme.TextPrimary)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = AppTheme.TextSecondary)
                Spacer(Modifier.height(4.dp))
                content()
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = AppTheme.Border)
                BrandFooter(centered = true)
            }
        }
    }
}

@Composable
fun CheckingScreen() {
    Box(Modifier.fillMaxSize().background(AppTheme.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppGreen, strokeWidth = 3.dp)
            Spacer(Modifier.height(20.dp))
            Text("Checking license...", color = AppTheme.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun ActivationScreen(
    license: LicenseManager, firebase: FirebaseApi,
    onActivated: () -> Unit, onDeveloperLoggedIn: (DevSession) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDev by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true; error = null
            val problem = license.activate(code)
            busy = false
            if (problem == null) onActivated() else error = problem
        }
    }

    AuthCard("Activate Software", "Add your SHOP CODE. (Internet Required)") {
        OutlinedTextField(
            value = code, onValueChange = { code = it },
            label = { Text("Shop Code") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border),
            leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = AppTheme.TextMuted) },
        )
        error?.let {
            Surface(color = AppTheme.DangerLight, shape = RoundedCornerShape(AppTheme.RadiusSm)) {
                Text(it, color = AppTheme.Danger, fontSize = 12.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Medium)
            }
        }
        Button(
            onClick = { submit() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Activate", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        TextButton(onClick = { showDev = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Developer login", fontSize = 12.sp, color = AppTheme.TextSecondary)
        }
    }

    if (showDev) {
        DeveloperLoginDialog(firebase, { showDev = false; onDeveloperLoggedIn(it) }, { showDev = false })
    }
}

@Composable
fun BlockedScreen(
    message: String, firebase: FirebaseApi,
    onRetry: () -> Unit, onDeveloperLoggedIn: (DevSession) -> Unit,
) {
    var showDev by remember { mutableStateOf(false) }
    AuthCard("Software Closed", "Subscription / License issue") {
        Surface(color = AppTheme.WarningLight, shape = RoundedCornerShape(AppTheme.RadiusSm)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = AppTheme.Warning, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(message, fontSize = 13.sp, color = AppTheme.TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) { Text("Try Again", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        TextButton(onClick = { showDev = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Developer login", fontSize = 12.sp, color = AppTheme.TextSecondary)
        }
    }
    if (showDev) {
        DeveloperLoginDialog(firebase, { showDev = false; onDeveloperLoggedIn(it) }, { showDev = false })
    }
}

@Composable
private fun DeveloperLoginDialog(
    firebase: FirebaseApi, onSuccess: (DevSession) -> Unit, onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true; error = null
            when (val r = firebase.developerSignIn(email.trim(), password)) {
                is DevResult.Success -> { busy = false; onSuccess(r.session) }
                is DevResult.Failed -> { busy = false; error = r.message }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Developer Login", fontWeight = FontWeight.Bold, color = AppTheme.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, singleLine = true, enabled = !busy,
                    shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border),
                )
                error?.let { Text(it, color = AppTheme.Danger, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = !busy, shape = RoundedCornerShape(AppTheme.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)) { Text("Login", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = AppTheme.TextSecondary) } },
        shape = RoundedCornerShape(AppTheme.RadiusLg), containerColor = AppTheme.Surface
    )
}

@Composable
fun LoginScreen(
    services: AppServices, offline: Boolean,
    onUserLoggedIn: (AppUser) -> Unit, onDeveloperLoggedIn: (DevSession) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        val name = username.trim()
        if (name.isEmpty() || password.isEmpty()) { error = "Enter the username and password."; return }
        scope.launch {
            busy = true; error = null
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
            } catch (e: Throwable) { error = "Login Issue: ${e.message ?: e}" }
            finally { busy = false }
        }
    }

    AuthCard("Billing System", "Log in to continue") {
        if (offline) {
            Surface(color = AppTheme.WarningLight, shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, null, tint = AppTheme.Warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No internet: Running in offline mode.", color = AppTheme.Warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border),
            leadingIcon = { Icon(Icons.Default.Person, null, tint = AppTheme.TextMuted) },
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = RoundedCornerShape(AppTheme.RadiusSm), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, unfocusedBorderColor = AppTheme.Border),
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = AppTheme.TextMuted) },
        )
        error?.let {
            Surface(color = AppTheme.DangerLight, shape = RoundedCornerShape(AppTheme.RadiusSm)) {
                Text(it, color = AppTheme.Danger, fontSize = 12.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Medium)
            }
        }
        Button(
            onClick = { submit() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(AppTheme.RadiusSm),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Login", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(
            text = "Need a new login or forgot password?\nContact the developer.",
            fontSize = 12.sp, color = AppTheme.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}