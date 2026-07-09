package com.example.absen_android.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.absen_android.network.AttendanceDetailItem
import com.example.absen_android.network.RetrofitClient
import com.example.absen_android.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryAttendanceScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var isLoading      by remember { mutableStateOf(false) }
    var attendanceList by remember { mutableStateOf<List<AttendanceDetailItem>>(emptyList()) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }

    val userId = SessionManager.getUserId(context)

    fun fetchData() {
        scope.launch {
            isLoading    = true
            errorMessage = null
            try {
                val response = RetrofitClient.instance.getAttendanceDetail(
                    employeeId = userId,
                    key        = "namiroh123"
                )
                if (response.isSuccessful && response.body()?.status == true) {
                    attendanceList = response.body()?.data ?: emptyList()
                } else {
                    errorMessage = "Gagal memuat data absensi"
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { fetchData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "Riwayat Absensi",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.Black
            )
            IconButton(onClick = { fetchData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Body ─────────────────────────────────────────────────────────────────
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage ?: "", color = Color.Red, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { fetchData() }) { Text("Coba Lagi") }
                    }
                }
            }

            attendanceList.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada data absensi", color = Color.Gray, fontSize = 14.sp)
                }
            }

            else -> {
                // Total badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE3F2FD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = "Total ${attendanceList.size} catatan absensi",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color    = Color(0xFF1565C0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attendanceList) { item ->
                        AttendanceHistoryCard(item)
                    }
                }
            }
        }
    }
}

// ── Card per item absensi ──────────────────────────────────────────────────────
@Composable
fun AttendanceHistoryCard(item: AttendanceDetailItem) {

    val isCheckIn  = item.attendance_type == "check_in"
    val typeLabel  = if (isCheckIn) "Masuk" else "Keluar"
    val typeColor  = if (isCheckIn) Color(0xFF2E7D32) else Color(0xFF1565C0)

    // "approved" tidak ditampilkan — hanya pending & rejected yang perlu perhatian
    val (statusLabel, statusColor) = when (item.status.lowercase()) {
        "rejected" -> "Ditolak"  to Color(0xFFC62828)
        "pending"  -> "Menunggu" to Color(0xFFF57F17)
        else       -> ""         to Color.Transparent   // approved → hidden
    }

    // Format tanggal: "2026-05-05 08:00:00" → "05 Mei 2026"
    val formattedDate = formatDateTime(item.attendance_date, "dd MMM yyyy")

    // Format jam: "2026-05-05 08:00:00" → "08:00:00 WIB"
    val formattedTime = formatDateTime(item.attendance_time, "HH:mm:ss") + " WIB"

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Baris atas: tanggal + badge tipe
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = formattedDate,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = Color.Black
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = typeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text       = typeLabel,
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color      = typeColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Jam
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector     = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint            = Color.Gray,
                    modifier        = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = formattedTime, fontSize = 13.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Lokasi + jarak
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector     = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint            = Color.Gray,
                    modifier        = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text     = item.work_location?.location_name ?: "-",
                    fontSize = 13.sp,
                    color    = Color.DarkGray,
                    modifier = Modifier.weight(1f)
                )
                item.distance_meters?.let { dist ->
                    Text(
                        text     = "$dist m",
                        fontSize = 12.sp,
                        color    = Color.Gray
                    )
                }
            }

            // Device model (opsional, jika ada)
            item.device_model?.let { model ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = "📱 $model",
                    fontSize = 12.sp,
                    color    = Color.Gray
                )
            }

            // Badge status: hanya muncul jika bukan "approved"
            if (statusLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text       = statusLabel,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            color      = statusColor,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Helper: parse datetime dan format ulang ───────────────────────────────────
// Format utama dari database: "2026-05-05 08:00:00" (sudah WIB, tanpa konversi)
// Fallback: ISO 8601 dengan T dan Z (UTC → WIB)
private fun formatDateTime(raw: String, pattern: String): String {
    if (raw.isBlank()) return raw
    return try {
        val wib = TimeZone.getTimeZone("Asia/Jakarta")
        val utc = TimeZone.getTimeZone("UTC")

        // Pasangan (format_input, timezone_input) — dicoba berurutan
        val candidates = listOf(
            "yyyy-MM-dd HH:mm:ss"            to wib,   // format database utama
            "yyyy-MM-dd HH:mm:ss.SSS"        to wib,   // + milliseconds
            "yyyy-MM-dd HH:mm:ss.SSSSSS"     to wib,   // + microseconds
            "yyyy-MM-dd'T'HH:mm:ss"          to wib,   // ISO tanpa Z
            "yyyy-MM-dd'T'HH:mm:ss'Z'"       to utc,   // ISO UTC
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"   to utc,   // ISO UTC + ms
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'" to utc   // ISO UTC + μs
        )

        var parsedDate: Date? = null
        for ((fmt, tz) in candidates) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.isLenient = false          // ← kunci: strict, salah format = exception
                sdf.timeZone  = tz
                parsedDate    = sdf.parse(raw)
                if (parsedDate != null) break
            } catch (e: Exception) {
                // format tidak cocok, coba berikutnya
            }
        }

        if (parsedDate == null) return raw

        // Output selalu dalam WIB
        val outFmt = SimpleDateFormat(pattern, Locale("id", "ID"))
        outFmt.timeZone = wib
        outFmt.format(parsedDate)
    } catch (e: Exception) {
        raw
    }
}