package com.example.absen_android.screen

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun createImageFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir("photos") ?: context.filesDir
    if (!storageDir.exists()) storageDir.mkdirs()
    return File(storageDir, "PHOTO_${timestamp}.jpg")
}

fun getUriForFile(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(
        context,
        "com.example.absen_android.provider",
        file
    )
}

// Parse error message from API — handles both string and object formats
fun parseErrorMessage(errorBody: String?): String {
    if (errorBody == null) return "Terjadi kesalahan"
    return try {
        val json = Gson().fromJson(errorBody, JsonObject::class.java)
        val message = json.get("message")
        when {
            message == null -> "Terjadi kesalahan"
            message.isJsonPrimitive -> message.asString
            message.isJsonObject -> {
                // e.g. {"photo": ["The photo must not be greater than 120 kilobytes."]}
                val msgObj = message.asJsonObject
                val sb = StringBuilder()
                msgObj.keySet().forEach { key ->
                    val errors = msgObj.getAsJsonArray(key)
                    errors.forEach { sb.appendLine(it.asString) }
                }
                sb.toString().trim()
            }
            message.isJsonArray -> {
                message.asJsonArray.joinToString("\n") { it.asString }
            }
            else -> "Terjadi kesalahan"
        }
    } catch (e: Exception) {
        "Terjadi kesalahan"
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var location by remember { mutableStateOf<DeviceLocation?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf("") }
    var isMockDetected by remember { mutableStateOf(false) }
    var isSubmittingAbsence by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(LocationUtils.hasLocationPermission(context)) }
    var attendanceType by remember { mutableStateOf("check_in") }
    var currentPhotoPath by remember { mutableStateOf<String?>(null) }
    var photoTaken by remember { mutableStateOf(false) }

    val deviceId = remember { DeviceUtils.getDeviceId(context) }
    val deviceName = remember { DeviceUtils.getDeviceName() }
    val deviceModel = remember { DeviceUtils.getDeviceModel() }
    val deviceBrand = remember { DeviceUtils.getDeviceBrand() }
    val androidVersion = remember { DeviceUtils.getAndroidVersion() }
    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }
        catch (e: Exception) { "1.0" }
    }
    val fullname = remember { SessionManager.getFullname(context) }
    val role = remember { SessionManager.getRole(context) }
    val token = remember { SessionManager.getToken(context) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoTaken = !photoTaken
        } else {
            currentPhotoPath = null
            Toast.makeText(context, "Foto dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val file = createImageFile(context)
                val uri = getUriForFile(context, file)
                currentPhotoPath = file.absolutePath
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Izin kamera diperlukan", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    fun openCamera() {
        try {
            val file = createImageFile(context)
            val uri = getUriForFile(context, file)
            currentPhotoPath = file.absolutePath
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun fetchLocation() {
        scope.launch {
            isLoadingLocation = true
            locationError = ""
            isMockDetected = false
            location = null
            when (val result = LocationUtils.getCurrentLocation(context)) {
                is LocationResult.Success -> location = result.location
                is LocationResult.Error -> {
                    if (result.message == "MOCK_LOCATION_DETECTED") {
                        isMockDetected = true
                    } else {
                        locationError = result.message
                    }
                }
            }
            isLoadingLocation = false
        }
    }

    fun submitAbsence() {
        if (LocationUtils.isMockLocationEnabled(context)) {
            Toast.makeText(context, "⛔ GPS palsu terdeteksi!", Toast.LENGTH_LONG).show()
            return
        }
        if (location == null) {
            Toast.makeText(context, "Lokasi belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }
        if (token == null) {
            Toast.makeText(context, "Sesi tidak valid, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isSubmittingAbsence = true
            try {
                fun String.toRB() = this.toRequestBody("text/plain".toMediaTypeOrNull())

                // Compress photo on IO thread before upload
                val photoPart = withContext(Dispatchers.IO) {
                    currentPhotoPath?.let { path ->
                        val originalFile = File(path)
                        if (!originalFile.exists() || originalFile.length() == 0L) return@let null

                        // Compress to max 100 KB
                        val compressed = ImageUtils.compressImageToMaxSize(
                            context = context,
                            sourceFile = originalFile,
                            maxSizeKb = 100
                        )

                        val sizeKb = compressed.length() / 1024
                        val requestBody = compressed.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("photo", compressed.name, requestBody)
                    }
                }

                val httpResponse = RetrofitClient.instance.submitAttendance(
                    token = "Bearer $token",
                    locationId = "1".toRB(),
                    attendanceType = attendanceType.toRB(),
                    latitude = location!!.latitude.toString().toRB(),
                    longitude = location!!.longitude.toString().toRB(),
                    deviceId = deviceId.toRB(),
                    deviceModel = deviceModel.toRB(),
                    deviceBrand = deviceBrand.toRB(),
                    androidVersion = androidVersion.toRB(),
                    appVersion = appVersion.toRB(),
                    gpsAccuracy = location!!.accuracy.toString().toRB(),
                    photo = photoPart
                )

                if (httpResponse.isSuccessful) {
                    val body = httpResponse.body()
                    if (body?.success == true) {
                        Toast.makeText(context, "✅ ${body.message}", Toast.LENGTH_LONG).show()
                        currentPhotoPath = null
                    } else {
                        Toast.makeText(context, "❌ ${body?.message ?: "Gagal absensi"}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Parse error body — handles both string and object message formats
                    val errorBodyStr = httpResponse.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorBodyStr)
                    Toast.makeText(context, "❌ $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Gagal: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isSubmittingAbsence = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (LocationUtils.hasLocationPermission(context)) {
                    scope.launch { fetchLocation() }
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val displayUri = remember(currentPhotoPath, photoTaken) {
        currentPhotoPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() > 0) getUriForFile(context, file) else null
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

        if (isMockDetected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    Column {
                        Text("GPS Palsu Terdeteksi!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Nonaktifkan fake GPS lalu refresh.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
            }
        }

        // Attendance Type Selector
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tipe Absensi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = attendanceType == "check_in",
                        onClick = { attendanceType = "check_in" },
                        label = { Text("Check In", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = if (attendanceType == "check_in") FontWeight.Bold else FontWeight.Normal) }
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = attendanceType == "check_out",
                        onClick = { attendanceType = "check_out" },
                        label = { Text("Check Out", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = if (attendanceType == "check_out") FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }

        // Photo Card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Foto", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (displayUri != null) {
                        Text("Hapus", color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                            modifier = Modifier.clickable { currentPhotoPath = null })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (displayUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(context).data(displayUri).crossfade(true).build()
                        ),
                        contentDescription = "Foto absensi",
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { openCamera() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ambil Ulang Foto")
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { openCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap untuk ambil foto", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            Text("(Opsional, maks 100 KB)", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // Absensi Button
        Button(
            onClick = { submitAbsence() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSubmittingAbsence && location != null && !isMockDetected,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (attendanceType == "check_in") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                disabledContainerColor = Color.Gray
            )
        ) {
            if (isSubmittingAbsence) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Text("  Mengirim...", color = Color.White, fontSize = 16.sp)
            } else {
                Icon(Icons.Filled.AccessTime, null, modifier = Modifier.size(20.dp))
                Text("  ${if (attendanceType == "check_in") "Check In Sekarang" else "Check Out Sekarang"}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        InfoCard(title = "Informasi Perangkat", icon = Icons.Filled.PhoneAndroid) {
            InfoRow("Device ID", deviceId)
            InfoRow("Nama Perangkat", deviceName)
            InfoRow("Model", deviceModel)
            InfoRow("Brand", deviceBrand)
            InfoRow("Android", "v$androidVersion (SDK ${DeviceUtils.getSdkVersion()})")
            InfoRow("App Version", appVersion)
        }

        InfoCard(
            title = "Lokasi & GPS",
            icon = Icons.Filled.LocationOn,
            action = {
                IconButton(onClick = { fetchLocation() }, enabled = !isLoadingLocation) {
                    if (isLoadingLocation) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        ) {
            if (isLoadingLocation) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("  Mendapatkan lokasi...", color = MaterialTheme.colorScheme.primary)
                }
            } else if (isMockDetected) {
                Text("⛔ GPS palsu aktif.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { fetchLocation() }) { Text("Coba Lagi") }
            } else if (location != null) {
                InfoRow("Latitude", "%.6f".format(location!!.latitude))
                InfoRow("Longitude", "%.6f".format(location!!.longitude))
                InfoRow("Altitude", "%.2f meter".format(location!!.altitude))
                InfoRow("Akurasi", "%.2f meter".format(location!!.accuracy))
                InfoRow("Status GPS", "✅ Asli")
            } else {
                Text(locationError.ifEmpty { "Lokasi belum tersedia" }, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (!permissionGranted) locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    else fetchLocation()
                }) { Text("Izinkan Lokasi") }
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
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}