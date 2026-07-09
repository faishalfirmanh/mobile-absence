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

        // ── Step 1: Tidak ada sesi sama sekali → ke Login ─────────────────────
        if (!SessionManager.hasSession(context)) {
            onSessionInvalid(false)
            return@LaunchedEffect
        }

        // ── Step 2: Sesi ada tapi token null → bersihkan & ke Login ──────────
        val token = SessionManager.getToken(context)
        if (token.isNullOrBlank()) {
            SessionManager.clearSession(context)
            onSessionInvalid(false)
            return@LaunchedEffect
        }

        // ── Step 3: Validasi token ke server ──────────────────────────────────
        try {
            val response = RetrofitClient.instance.validateToken("Bearer $token")
            when {
                // Token valid → langsung ke Home
                response.isSuccessful && response.body()?.success == true -> {
                    onSessionValid()
                }

                // 401 = token benar-benar expired di server → hapus sesi, Login
                response.code() == 401 -> {
                    SessionManager.clearSession(context)
                    onSessionInvalid(true)
                }

                // Error server lain (500, 503, dsb) → JANGAN hapus sesi,
                // biarkan masuk dengan sesi yang masih ada
                else -> {
                    onSessionValid()
                }
            }
        } catch (e: Exception) {
            // Tidak ada koneksi / timeout → JANGAN hapus sesi.
            // User tetap bisa masuk dengan data sesi yang tersimpan.
            // Sesi hanya dihapus jika server benar-benar bilang 401.
            onSessionValid()
        }
    }

    // ── Splash UI ─────────────────────────────────────────────────────────────
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Filled.AccessTime,
            contentDescription = null,
            modifier           = Modifier.size(80.dp),
            tint               = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text       = "Absensi",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary
        )
        Text(
            text     = "An-Namiroh Group",
            fontSize = 18.sp,
            color    = MaterialTheme.colorScheme.secondary
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