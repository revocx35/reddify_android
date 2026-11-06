package io.github.revocx35.reddify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.revocx35.reddify.BuildConfig
import io.github.revocx35.reddify.core.ApiException
import io.github.revocx35.reddify.core.AuthState
import io.github.revocx35.reddify.core.parseServerUrl
import io.github.revocx35.reddify.core.userMessage
import io.github.revocx35.reddify.data.AppGraph
import io.github.revocx35.reddify.data.AuthentikSignIn
import io.github.revocx35.reddify.ui.LocalEnv
import io.github.revocx35.reddify.ui.Logo
import io.github.revocx35.reddify.ui.theme.R
import kotlinx.coroutines.launch

@Composable
private fun field(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false, number: Boolean = false) = OutlinedTextField(
    value, onChange, label = { Text(label) }, singleLine = true,
    visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (number) KeyboardType.NumberPassword else KeyboardType.Uri),
    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = R.text, unfocusedBorderColor = R.muted, focusedLabelColor = R.text, cursorColor = R.accent),
    modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun BigButton(text: String, enabled: Boolean = true, outline: Boolean = false, onClick: () -> Unit) {
    if (outline) {
        OutlinedButton(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50)) { Text(text, color = R.text, fontWeight = FontWeight.Bold) }
    } else {
        Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = R.accent, contentColor = Color.Black)) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * First screen: server address → (authentik sign-in in the browser if the server is behind it) → local login if
 * the server still wants one. [onSignedIn] gets the server's auth state with `me` set.
 */
@Composable
fun SignInScreen(graph: AppGraph, initialError: String?, onSignedIn: (AuthState) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(graph.serverUrl) }
    var stage by remember { mutableStateOf("server") } // server | authentik | local
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var needTotp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(initialError) }

    fun check() {
        val url = parseServerUrl(server)
        if (url == null) {
            error = "Enter the https address of your Reddify server."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                if (graph.serverUrl != url.toString()) graph.serverUrl = url.toString()
                val st = graph.api.value.state()
                if (st.me != null) onSignedIn(st) else stage = "local"
            } catch (e: ApiException) {
                if (e.code == ApiException.AUTHENTIK) stage = "authentik" else error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color.Black))).statusBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Logo(56.dp)
        Text("Reddify", style = MaterialTheme.typography.headlineLarge, color = R.text)
        Spacer(Modifier.height(16.dp))
        when (stage) {
            "server" -> {
                Text("Your Reddify server", color = R.sub)
                field("Server address (e.g. music.example.com)", server, { server = it })
                BigButton(if (busy) "Connecting…" else "Continue", !busy) { check() }
            }
            "authentik" -> {
                Text("This server signs you in with authentik.", color = R.sub)
                BigButton(if (busy) "Waiting for the browser…" else "Sign in with authentik", !busy) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val api = graph.api.value
                            AuthentikSignIn.signIn(ctx, graph.authentik, graph.http, api.base, null, BuildConfig.AUTHENTIK_CLIENT_ID)
                            val st = graph.api.value.state()
                            if (st.me != null) onSignedIn(st) else stage = "local"
                        } catch (e: Exception) {
                            error = e.userMessage().takeIf { it != "Something went wrong." } ?: (e.message ?: "Sign-in failed.")
                        } finally {
                            busy = false
                        }
                    }
                }
                BigButton("Change server", outline = true) { stage = "server" }
            }
            "local" -> {
                Text("Log in to ${parseServerUrl(server)?.host ?: "Reddify"}", color = R.sub)
                field("Username", username, { username = it })
                field("Password", password, { password = it }, password = true)
                if (needTotp) field("Code from your authenticator app", totp, { totp = it }, number = true)
                BigButton(if (busy) "Logging in…" else "Log In", !busy && username.isNotBlank() && password.isNotEmpty()) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val r = graph.api.value.login(username.trim(), password, totp.takeIf { needTotp })
                            graph.saveToken(r.token)
                            if (r.mustChangePassword) error = "Set your own password on the web page first (you signed in with a temporary one)."
                            onSignedIn(graph.api.value.state())
                        } catch (e: ApiException) {
                            if (e.code == "totp_required") needTotp = true else error = e.userMessage()
                        } finally {
                            busy = false
                        }
                    }
                }
                BigButton("Change server", outline = true) { stage = "server" }
            }
        }
        error?.let { Text(it, color = R.danger) }
    }
}

@Composable
fun SettingsScreen(displayName: String, onSignOut: () -> Unit) {
    val env = LocalEnv.current
    val scope = rememberCoroutineScope()
    val status = rememberLoad("spstatus") { env.graph.api.value.spotifyStatus() }
    var cookie by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackRow()
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = R.text)
        Text("Signed in as $displayName on ${env.graph.api.value.base.host}", color = R.sub)
        Text("Spotify connection", style = MaterialTheme.typography.titleLarge, color = R.text, modifier = Modifier.padding(top = 12.dp))
        val s = status.data
        when {
            status.error != null -> Text(status.error, color = R.danger)
            s == null -> Text("Checking…", color = R.sub)
            else -> {
                Text("Browser: ${s.browser} • Spotify: ${if (s.loggedIn) "logged in" else "not logged in (search still works)"}", color = R.sub)
                Text(if (s.mode == "shared") "Everyone shares one Spotify browser." else "You have your own Spotify browser.", color = R.sub)
                if (s.canManage) {
                    Text("To log in, open Reddify on the web (Settings → Spotify connection) and type your Spotify login on the browser screen, or paste your sp_dc cookie here:", color = R.sub, style = MaterialTheme.typography.bodySmall)
                    field("sp_dc cookie value", cookie, { cookie = it }, password = true)
                    BigButton("Use this Spotify login", cookie.length > 20) {
                        scope.launch {
                            msg = try { env.graph.api.value.spotifyCookie(cookie.trim()); cookie = ""; status.reload(); "Spotify login saved." } catch (e: Exception) { e.userMessage() }
                        }
                    }
                }
            }
        }
        msg?.let { Text(it, color = R.accent) }
        Spacer(Modifier.height(12.dp))
        Text("On this phone: ${env.graph.offline.tracks.value.size} songs, ${env.graph.offline.bytesUsed() / 1_048_576} MB", color = R.sub)
        BigButton("Log out", outline = true, onClick = onSignOut)
        Text("Reddify ${BuildConfig.VERSION_NAME}", color = R.muted, style = MaterialTheme.typography.bodySmall)
    }
}
