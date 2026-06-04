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
    onSessionValid: () -> Unit,
    onSessionInvalid: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val token = SessionManager.getToken(context)

        if (token == null) {
            onSessionInvalid()
            return@LaunchedEffect
        }

        try {
            val httpResponse = RetrofitClient.instance.validateToken("Bearer $token")
            if (httpResponse.isSuccessful) {
                // Jika token valid (HTTP 200), lanjut ke Home
                onSessionValid()
            } else {
                // Hanya hapus sesi jika token memang tidak valid (e.g., 401 Unauthorized)
                if (httpResponse.code() == 401) {
                    SessionManager.clearSession(context)
                }
                onSessionInvalid()
            }
        } catch (e: Exception) {
            // Jika terjadi error jaringan/server, tetap izinkan ke Home 
            // agar user tidak terlempar keluar saat offline (jika sudah punya token)
            onSessionValid()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Absensi",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "An-Namiroh",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Memeriksa sesi...",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}