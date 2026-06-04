package com.example.absen_android.screen

import android.Manifest
import android.content.Context
import android.net.Uri
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.absen_android.network.AttendanceResponse
import com.example.absen_android.network.RetrofitClient
import com.example.absen_android.utils.DeviceLocation
import com.example.absen_android.utils.DeviceUtils
import com.example.absen_android.utils.ImageUtils
import com.example.absen_android.utils.LocationResult
import com.example.absen_android.utils.LocationUtils
import com.example.absen_android.utils.SessionManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

fun getUriForFile(context: android.content.Context, file: File): Uri =
    FileProvider.getUriForFile(context, "com.example.absen_android.provider", file)

fun parseAttendanceError(errorBody: String?): String {
    if (errorBody == null) return "Terjadi kesalahan"
    return try {
        val json = Gson().fromJson(errorBody, JsonObject::class.java)
        val msg  = json.get("message") ?: return "Terjadi kesalahan"
        when {
            msg.isJsonPrimitive -> msg.asString
            msg.isJsonObject    -> msg.asJsonObject.keySet().joinToString("\n") { key ->
                msg.asJsonObject.getAsJsonArray(key).joinToString("\n") { it.asString }
            }
            else -> "Terjadi kesalahan"
        }
    } catch (_: Exception) { "Terjadi kesalahan" }
}

@Composable
fun HomeScreen() {
    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Location
    var location          by remember { mutableStateOf<DeviceLocation?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError     by remember { mutableStateOf("") }
    var isMockDetected    by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(LocationUtils.hasLocationPermission(context)) }

    // Form
    var attendanceType    by remember { mutableStateOf("check_in") }
    var isSubmitting      by remember { mutableStateOf(false) }

    // Photo — stored as file path for stability
    var currentPhotoPath  by remember { mutableStateOf<String?>(null) }
    var photoTaken        by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }

    // Device info
    val deviceId       = remember { DeviceUtils.getDeviceId(context) }
    val deviceName     = remember { DeviceUtils.getDeviceName() }
    val deviceModel    = remember { DeviceUtils.getDeviceModel() }
    val deviceBrand    = remember { DeviceUtils.getDeviceBrand() }
    val androidVersion = remember { DeviceUtils.getAndroidVersion() }
    val appVersion     = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }
        catch (_: Exception) { "1.0" }
    }
    val fullname = remember { SessionManager.getFullname(context) }
    val role     = remember { SessionManager.getRole(context) }
    val token    = remember { SessionManager.getToken(context) }

    // Location permission
    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    fun fetchLocation() {
        scope.launch {
            isLoadingLocation = true
            locationError = ""
            isMockDetected = false
            location = null
            when (val r = LocationUtils.getCurrentLocation(context)) {
                is LocationResult.Success -> location = r.location
                is LocationResult.Error   -> {
                    if (r.message == "MOCK_LOCATION_DETECTED") isMockDetected = true
                    else locationError = r.message
                }
            }
            isLoadingLocation = false
        }
    }

    fun submitAbsence() {
        if (LocationUtils.isMockLocationEnabled(context)) {
            Toast.makeText(context, "⛔ GPS palsu terdeteksi!", Toast.LENGTH_LONG).show(); return
        }
        if (location == null) {
            Toast.makeText(context, "Lokasi belum tersedia", Toast.LENGTH_SHORT).show(); return
        }
        if (token == null) {
            Toast.makeText(context, "Sesi tidak valid", Toast.LENGTH_SHORT).show(); return
        }
        scope.launch {
            isSubmitting = true
            try {
                fun String.rb() = toRequestBody("text/plain".toMediaTypeOrNull())

                val photoPart = withContext(Dispatchers.IO) {
                    currentPhotoPath?.let { path ->
                        val f = File(path)
                        if (!f.exists() || f.length() == 0L) return@let null
                        val comp = ImageUtils.compressImageToMaxSize(context, f, 100)
                        MultipartBody.Part.createFormData(
                            "photo", comp.name,
                            comp.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        )
                    }
                }

                val res = RetrofitClient.instance.submitAttendance(
                    token          = "Bearer $token",
                    locationId     = "1".rb(),
                    attendanceType = attendanceType.rb(),
                    latitude       = location!!.latitude.toString().rb(),
                    longitude      = location!!.longitude.toString().rb(),
                    deviceId       = deviceId.rb(),
                    deviceModel    = deviceModel.rb(),
                    deviceBrand    = deviceBrand.rb(),
                    androidVersion = androidVersion.rb(),
                    appVersion     = appVersion.rb(),
                    gpsAccuracy    = location!!.accuracy.toString().rb()
//                    photo          = photoPart
                )

                if (res.isSuccessful) {
                    val body = res.body()
                    if (body?.success == true) {
                        Toast.makeText(context, "✅ ${body.message}", Toast.LENGTH_LONG).show()
                        currentPhotoPath = null
                    } else {
                        Toast.makeText(context, "❌ ${body?.message ?: "Gagal"}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val msg = parseAttendanceError(res.errorBody()?.string())
                    Toast.makeText(context, "❌ $msg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "❌ ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally { isSubmitting = false }
        }
    }

    // Auto-refresh GPS on resume
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (LocationUtils.hasLocationPermission(context)) scope.launch { fetchLocation() }
                else locPermLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Derive display URI from stable file path
    val displayUri = remember(currentPhotoPath, photoTaken) {
        currentPhotoPath?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0) getUriForFile(context, f) else null
        }
    }

    // ── CameraX Overlay Dialog ────────────────────────────────────────────────
//    if (showOverlayDialog) {
//        CameraOverlayDialog(
//            onPhotoCaptured = { file ->
//                currentPhotoPath  = file.absolutePath
//                photoTaken        = !photoTaken
//                showOverlayDialog = false
//            },
//            onDismiss = { showOverlayDialog = false }
//        )
//    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Halo, $fullname", fontSize = 16.sp, color = Color.Gray)
        Text("Role: $role", fontSize = 14.sp, color = Color.Gray)

        // Mock GPS warning
        if (isMockDetected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    Column {
                        Text("GPS Palsu Terdeteksi!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Nonaktifkan fake GPS lalu refresh.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
            }
        }

        // Attendance type
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tipe Absensi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = attendanceType == "check_in",
                        onClick  = { attendanceType = "check_in" },
                        label    = { Text("Check In", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = if (attendanceType == "check_in") FontWeight.Bold else FontWeight.Normal) }
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = attendanceType == "check_out",
                        onClick  = { attendanceType = "check_out" },
                        label    = { Text("Check Out", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = if (attendanceType == "check_out") FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }

        // Photo section with CameraX overlay
//        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
//            Column(modifier = Modifier.padding(16.dp)) {
//                PhotoSection(
//                    displayUri          = displayUri,
//                    onOpenOverlayDialog = { showOverlayDialog = true },
//                    onDeletePhoto       = { currentPhotoPath = null }
//                )
//            }
//        }

        // Absensi button
        Button(
            onClick  = { submitAbsence() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(12.dp),
            enabled  = !isSubmitting && location != null && !isMockDetected,
            colors   = ButtonDefaults.buttonColors(
                containerColor         = if (attendanceType == "check_in") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                disabledContainerColor = Color.Gray
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Text("  Mengirim...", color = Color.White, fontSize = 16.sp)
            } else {
                Icon(Icons.Filled.AccessTime, null, modifier = Modifier.size(20.dp))
                Text(
                    "  ${if (attendanceType == "check_in") "Check In Sekarang" else "Check Out Sekarang"}",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Device info
        InfoCard(title = "Informasi Perangkat", icon = Icons.Filled.PhoneAndroid) {
            InfoRow("Device ID",      deviceId)
            InfoRow("Nama Perangkat", deviceName)
            InfoRow("Model",          deviceModel)
            InfoRow("Brand",          deviceBrand)
            InfoRow("Android",        "v$androidVersion (SDK ${DeviceUtils.getSdkVersion()})")
            InfoRow("App Version",    appVersion)
        }

        // Location
        InfoCard(
            title  = "Lokasi & GPS",
            icon   = Icons.Filled.LocationOn,
            action = {
                IconButton(onClick = { fetchLocation() }, enabled = !isLoadingLocation) {
                    if (isLoadingLocation) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        ) {
            when {
                isLoadingLocation -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("  Mendapatkan lokasi...", color = MaterialTheme.colorScheme.primary)
                }
                isMockDetected -> {
                    Text("⛔ GPS palsu aktif.", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { fetchLocation() }) { Text("Coba Lagi") }
                }
                location != null -> {
                    InfoRow("Latitude",   "%.6f".format(location!!.latitude))
                    InfoRow("Longitude",  "%.6f".format(location!!.longitude))
                    InfoRow("Altitude",   "%.2f meter".format(location!!.altitude))
                    InfoRow("Akurasi",    "%.2f meter".format(location!!.accuracy))
                    InfoRow("Status GPS", "✅ Asli")
                }
                else -> {
                    Text(locationError.ifEmpty { "Lokasi belum tersedia" }, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        if (!permissionGranted) locPermLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )) else fetchLocation()
                    }) { Text("Izinkan Lokasi") }
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, icon: ImageVector, action: @Composable (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("  $title", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
