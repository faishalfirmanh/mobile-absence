package com.example.absen_android.screen

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.absen_android.network.AttendanceRequest
import com.example.absen_android.network.AttendanceResponse
import com.example.absen_android.network.RetrofitClient
import com.example.absen_android.utils.DeviceLocation
import com.example.absen_android.utils.DeviceUtils
import com.example.absen_android.utils.LocationResult
import com.example.absen_android.utils.LocationUtils
import com.example.absen_android.utils.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var location by remember { mutableStateOf<DeviceLocation?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf("") }
    var isMockDetected by remember { mutableStateOf(false) }
    var isSubmittingAbsence by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(LocationUtils.hasLocationPermission(context)) }

    val deviceId = remember { DeviceUtils.getDeviceId(context) }
    val deviceName = remember { DeviceUtils.getDeviceName() }
    val deviceModel = remember { DeviceUtils.getDeviceModel() }
    val deviceBrand = remember { DeviceUtils.getDeviceBrand() }
    val androidVersion = remember { DeviceUtils.getAndroidVersion() }
    val fullname = remember { SessionManager.getFullname(context) }
    val role = remember { SessionManager.getRole(context) }
    val token = remember { SessionManager.getToken(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    fun fetchLocation() {
        scope.launch {
            isLoadingLocation = true
            locationError = ""
            isMockDetected = false
            location = null

            when (val result = LocationUtils.getCurrentLocation(context)) {
                is LocationResult.Success -> {
                    location = result.location
                }
                is LocationResult.Error -> {
                    if (result.message == "MOCK_LOCATION_DETECTED") {
                        isMockDetected = true
                        locationError = "GPS palsu terdeteksi! Nonaktifkan aplikasi fake GPS."
                    } else {
                        locationError = result.message
                    }
                }
            }
            isLoadingLocation = false
        }
    }

    fun submitAbsence() {
        // Double check mock before submitting
        if (LocationUtils.isMockLocationEnabled(context)) {
            Toast.makeText(
                context,
                "⛔ GPS palsu terdeteksi! Absensi tidak dapat dilakukan.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (location == null) {
            Toast.makeText(context, "Lokasi belum tersedia, coba refresh GPS", Toast.LENGTH_SHORT).show()
            return
        }
        if (token == null) {
            Toast.makeText(context, "Sesi tidak valid, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isSubmittingAbsence = true
            try {
                val httpResponse = RetrofitClient.instance.submitAttendance(
                    token = "Bearer $token",
                    request = AttendanceRequest(
                        location_id = 1,
                        attendance_type = "check_in",
                        submitted_latitude = location!!.latitude,
                        submitted_longitude = location!!.longitude,
                        device_id = deviceId,
                        device_model = deviceModel,
                        gps_accuracy = location!!.accuracy
                    )
                )

                if (httpResponse.isSuccessful) {
                    val body = httpResponse.body()
                    if (body?.success == true) {
                        Toast.makeText(context, "✅ ${body.message}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "❌ ${body?.message ?: "Gagal absensi"}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorBody = httpResponse.errorBody()?.string()
                    val errorResponse = try {
                        Gson().fromJson(errorBody, AttendanceResponse::class.java)
                    } catch (e: Exception) { null }
                    val message = errorResponse?.message ?: "Terjadi kesalahan"
                    val distance = errorResponse?.distance_meters?.let { " (jarak: ${it}m)" } ?: ""
                    Toast.makeText(context, "❌ $message$distance", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "❌ Gagal terhubung ke server: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isSubmittingAbsence = false
            }
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) fetchLocation()
        else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = "Halo, $fullname", fontSize = 16.sp, color = Color.Gray)
        Text(text = "Role: $role", fontSize = 14.sp, color = Color.Gray)

        // Mock GPS Warning Banner
        if (isMockDetected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "GPS Palsu Terdeteksi!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Nonaktifkan aplikasi fake GPS lalu refresh lokasi.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Absensi Button
        Button(
            onClick = { submitAbsence() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSubmittingAbsence && location != null && !isMockDetected,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Gray
            )
        ) {
            if (isSubmittingAbsence) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Text(text = "  Mengirim...", color = Color.White, fontSize = 16.sp)
            } else {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "  Absensi Sekarang",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Device Info Card
        InfoCard(title = "Informasi Perangkat", icon = Icons.Filled.PhoneAndroid) {
            InfoRow(label = "Device ID", value = deviceId)
            InfoRow(label = "Nama Perangkat", value = deviceName)
            InfoRow(label = "Model", value = deviceModel)
            InfoRow(label = "Brand", value = deviceBrand)
            InfoRow(label = "Android", value = "v$androidVersion (SDK ${DeviceUtils.getSdkVersion()})")
        }

        // Location Card
        InfoCard(
            title = "Lokasi & GPS",
            icon = Icons.Filled.LocationOn,
            action = {
                IconButton(onClick = { fetchLocation() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        ) {
            if (isLoadingLocation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(text = "  Mendapatkan lokasi...", color = MaterialTheme.colorScheme.primary)
                }
            } else if (isMockDetected) {
                Text(
                    text = "⛔ Lokasi tidak dapat ditampilkan karena GPS palsu aktif.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { fetchLocation() }) {
                    Text("Coba Lagi")
                }
            } else if (location != null) {
                InfoRow(label = "Latitude", value = "%.6f".format(location!!.latitude))
                InfoRow(label = "Longitude", value = "%.6f".format(location!!.longitude))
                InfoRow(label = "Altitude", value = "%.2f meter".format(location!!.altitude))
                InfoRow(label = "Akurasi", value = "%.2f meter".format(location!!.accuracy))
                InfoRow(label = "Status GPS", value = "✅ Asli")
            } else {
                Text(
                    text = locationError.ifEmpty { "Lokasi belum tersedia" },
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (!permissionGranted) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        fetchLocation()
                    }
                }) {
                    Text("Izinkan Lokasi")
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    icon: ImageVector,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "  $title",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                action?.invoke()
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}