package com.example.absen_android.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.absen_android.network.RetrofitClient
import com.example.absen_android.utils.SessionManager

@Composable
fun SplashScreen(
    onSessionValid:   () -> Unit,
    onSessionInvalid: (tokenExpired: Boolean) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // ── Step 1: Check if session file exists ──────────────────────────────
        if (!SessionManager.hasSession(context)) {
            // No file → go to login
            onSessionInvalid(false)
            return@LaunchedEffect
        }

        // ── Step 2: File exists → get token ───────────────────────────────────
        val token = SessionManager.getToken(context)
        if (token == null) {
            SessionManager.clearSession(context)
            onSessionInvalid(false)
            return@LaunchedEffect
        }

        // ── Step 3: Hit GET /get_user to validate token ───────────────────────
        try {
            val response = RetrofitClient.instance.validateToken("Bearer $token")
            when {
                response.isSuccessful && response.body()?.success == true -> {
                    // Token valid → go to Home
                    onSessionValid()
                }
                response.code() == 401 -> {
                    // Token expired
                    SessionManager.clearSession(context)
                    onSessionInvalid(true)   // true = show "token expired" message
                }
                else -> {
                    // Other error — clear and go to login
                    SessionManager.clearSession(context)
                    onSessionInvalid(false)
                }
            }
        } catch (e: Exception) {
            // Network error — still go to login safely
            SessionManager.clearSession(context)
            onSessionInvalid(false)
        }
    }

    // ── Splash UI ─────────────────────────────────────────────────────────────
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector  = Icons.Filled.AccessTime,
            contentDescription = null,
            modifier     = Modifier.size(80.dp),
            tint         = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text       = "Absensi",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary
        )
        Text(
            text  = "Al-Hidayah",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text     = "Memeriksa sesi...",
            fontSize = 14.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
