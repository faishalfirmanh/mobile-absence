package com.example.absen_android.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.absen_android.network.RetrofitClient
import com.example.absen_android.utils.SessionManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    tokenExpiredMessage: String? = null   // shown when redirected from expired token
) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMessage    by remember { mutableStateOf("") }

    // Show token expired snackbar on first composition if message is provided
    if (tokenExpiredMessage != null) {
        androidx.compose.runtime.LaunchedEffect(tokenExpiredMessage) {
            snackbar.showSnackbar(
                message  = tokenExpiredMessage,
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(
            modifier          = Modifier.fillMaxSize().padding(padding),
            contentAlignment  = Alignment.Center
        ) {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape     = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier               = Modifier.padding(24.dp),
                    horizontalAlignment    = Alignment.CenterHorizontally,
                    verticalArrangement    = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Selamat Datang", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Silakan login untuk melanjutkan", fontSize = 14.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(4.dp))

                    // Username
                    OutlinedTextField(
                        value         = username,
                        onValueChange = { username = it; errorMessage = "" },
                        label         = { Text("Username") },
                        leadingIcon   = { Icon(Icons.Filled.Person, null) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        isError       = errorMessage.isNotEmpty()
                    )

                    // Password
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; errorMessage = "" },
                        label         = { Text("Password") },
                        leadingIcon   = { Icon(Icons.Filled.Lock, null) },
                        trailingIcon  = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    null
                                )
                            }
                        },
                        singleLine          = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                                              else PasswordVisualTransformation(),
                        modifier            = Modifier.fillMaxWidth(),
                        keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError             = errorMessage.isNotEmpty()
                    )

                    // Error message
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text     = errorMessage,
                            color    = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Login button
                    Button(
                        onClick  = {
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "Username dan password tidak boleh kosong"
                                return@Button
                            }
                            scope.launch {
                                isLoading    = true
                                errorMessage = ""
                                try {
                                    val httpRes = RetrofitClient.instance.login(username, password)
                                    val body    = httpRes.body()

                                    if (httpRes.isSuccessful &&
                                        body?.success == true &&
                                        body.token != null
                                    ) {
                                        // ── Save full response to file ────────
                                        SessionManager.saveSession(context, body)
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = body?.message ?: "Login gagal"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Gagal terhubung: ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(10.dp),
                        enabled  = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
