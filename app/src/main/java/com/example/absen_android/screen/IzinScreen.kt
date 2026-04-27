package com.example.absen_android.screen

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.absen_android.network.IzinData
import com.example.absen_android.network.RetrofitClient
import com.example.absen_android.utils.ImageUtils
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

// ── Constants ─────────────────────────────────────────────────────────────────
val JENIS_OPTIONS = listOf("Cuti", "Izin Sakit", "Izin Keperluan")

// ── Helpers ───────────────────────────────────────────────────────────────────
fun formatDateDisplay(millis: Long?): String {
    if (millis == null) return "-"
    return SimpleDateFormat("dd MMM yyyy", Locale("id")).format(Date(millis))
}

fun formatDateApi(millis: Long?): String {
    if (millis == null) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}

fun parseApiDate(dateStr: String?): Long? {
    if (dateStr == null) return null
    val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd")
    formats.forEach { fmt ->
        try { return SimpleDateFormat(fmt, Locale.getDefault()).parse(dateStr)?.time } catch (_: Exception) {}
    }
    return null
}

fun parseIzinError(errorBody: String?): String {
    if (errorBody == null) return "Terjadi kesalahan"
    return try {
        val json = Gson().fromJson(errorBody, JsonObject::class.java)
        val msg = json.get("message") ?: return "Terjadi kesalahan"
        when {
            msg.isJsonPrimitive -> msg.asString
            msg.isJsonObject    -> msg.asJsonObject.keySet().joinToString("\n") { key ->
                msg.asJsonObject.getAsJsonArray(key).joinToString("\n") { it.asString }
            }
            else -> "Terjadi kesalahan"
        }
    } catch (_: Exception) { "Terjadi kesalahan" }
}

fun createIzinImageFile(context: Context): File {
    val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir = context.getExternalFilesDir("izin") ?: context.filesDir
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "IZIN_$ts.jpg")
}

// ── IzinScreen ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IzinScreen() {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val token    = remember { SessionManager.getToken(context) }
    val isHrd    = remember { SessionManager.isHrd(context) }

    // ── Form state ────────────────────────────────────────────────────────────
    var jenis           by remember { mutableStateOf("") }
    var tglMulaiMillis  by remember { mutableStateOf<Long?>(null) }
    var tglSelesaiMillis by remember { mutableStateOf<Long?>(null) }
    var alasan          by remember { mutableStateOf("") }
    var buktiPhotoPath  by remember { mutableStateOf<String?>(null) }
    var buktiTaken      by remember { mutableStateOf(false) }
    var formError       by remember { mutableStateOf("") }
    var isSubmitting    by remember { mutableStateOf(false) }
    var showJenisMenu   by remember { mutableStateOf(false) }
    var showPickerMulai   by remember { mutableStateOf(false) }
    var showPickerSelesai by remember { mutableStateOf(false) }
    val dateMulaiState   = rememberDatePickerState()
    val dateSelesaiState = rememberDatePickerState()

    // ── List state ────────────────────────────────────────────────────────────
    var izinList      by remember { mutableStateOf<List<IzinData>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(false) }

    // ── HRD Modal state ───────────────────────────────────────────────────────
    var selectedIzin      by remember { mutableStateOf<IzinData?>(null) }
    var isLoadingDetail   by remember { mutableStateOf(false) }
    var isUpdating        by remember { mutableStateOf(false) }
    var showModal         by remember { mutableStateOf(false) }

    // ── Camera ────────────────────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) buktiTaken = !buktiTaken else buktiPhotoPath = null
    }
    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val f   = createIzinImageFile(context)
            val uri = FileProvider.getUriForFile(context, "com.example.absen_android.provider", f)
            buktiPhotoPath = f.absolutePath
            cameraLauncher.launch(uri)
        } else Toast.makeText(context, "Izin kamera diperlukan", Toast.LENGTH_SHORT).show()
    }

    // ── Functions ─────────────────────────────────────────────────────────────
    fun loadList() {
        if (token == null) return
        scope.launch {
            isLoadingList = true
            try {
                val res = RetrofitClient.instance.getListIzin("Bearer $token")
                if (res.isSuccessful) izinList = res.body()?.data?.data ?: emptyList()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally { isLoadingList = false }
        }
    }

    fun resetForm() {
        jenis = ""; tglMulaiMillis = null; tglSelesaiMillis = null
        alasan = ""; buktiPhotoPath = null; formError = ""
    }

    fun submitIzin() {
        when {
            jenis.isBlank()           -> { formError = "Jenis izin harus dipilih"; return }
            tglMulaiMillis == null    -> { formError = "Tanggal mulai harus diisi"; return }
            tglSelesaiMillis == null  -> { formError = "Tanggal selesai harus diisi"; return }
            tglSelesaiMillis!! < tglMulaiMillis!! -> { formError = "Tanggal selesai tidak boleh sebelum tanggal mulai"; return }
            alasan.isBlank()          -> { formError = "Alasan harus diisi"; return }
            token == null             -> { formError = "Sesi tidak valid"; return }
        }
        formError = ""
        scope.launch {
            isSubmitting = true
            try {
                fun String.rb() = toRequestBody("text/plain".toMediaTypeOrNull())
                val buktiPart = withContext(Dispatchers.IO) {
                    buktiPhotoPath?.let { path ->
                        val f = File(path)
                        if (!f.exists() || f.length() == 0L) return@let null
                        val comp = ImageUtils.compressImageToMaxSize(context, f, 100)
                        MultipartBody.Part.createFormData("bukti_sakit", comp.name,
                            comp.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                    }
                }
                val res = RetrofitClient.instance.submitIzin(
                    token       = "Bearer $token",
                    jenis       = jenis.rb(),
                    tglMulai    = formatDateApi(tglMulaiMillis).rb(),
                    tglSelesai  = formatDateApi(tglSelesaiMillis).rb(),
                    alasan      = alasan.rb(),
                    buktiSakit  = buktiPart
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    Toast.makeText(context, "✅ ${res.body()!!.message}", Toast.LENGTH_LONG).show()
                    resetForm(); loadList()
                } else {
                    Toast.makeText(context, "❌ ${parseIzinError(res.errorBody()?.string())}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "❌ ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally { isSubmitting = false }
        }
    }

    fun openIzinDetail(izin: IzinData) {
        if (!isHrd) return
        scope.launch {
            isLoadingDetail = true
            showModal = true
            try {
                val res = RetrofitClient.instance.findIzin("Bearer $token", izin.id)
                if (res.isSuccessful && res.body()?.status == true) {
                    selectedIzin = res.body()?.data
                } else {
                    selectedIzin = izin // fallback to list data
                }
            } catch (_: Exception) {
                selectedIzin = izin // fallback
            } finally { isLoadingDetail = false }
        }
    }

    fun updateIzinStatus(id: Int, status: String) {
        if (token == null) return
        scope.launch {
            isUpdating = true
            try {
                val res = RetrofitClient.instance.updateIzin("Bearer $token", id, status)
                if (res.isSuccessful && res.body()?.status == true) {
                    Toast.makeText(context, "✅ Status berhasil diubah ke $status", Toast.LENGTH_LONG).show()
                    showModal = false
                    selectedIzin = null
                    loadList()
                } else {
                    Toast.makeText(context, "❌ Gagal mengubah status", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "❌ ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally { isUpdating = false }
        }
    }

    LaunchedEffect(Unit) { loadList() }

    // ── Date Pickers ──────────────────────────────────────────────────────────
    if (showPickerMulai) {
        DatePickerDialog(
            onDismissRequest = { showPickerMulai = false },
            confirmButton = { TextButton(onClick = { tglMulaiMillis = dateMulaiState.selectedDateMillis; showPickerMulai = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPickerMulai = false }) { Text("Batal") } }
        ) { DatePicker(state = dateMulaiState) }
    }

    if (showPickerSelesai) {
        DatePickerDialog(
            onDismissRequest = { showPickerSelesai = false },
            confirmButton = { TextButton(onClick = { tglSelesaiMillis = dateSelesaiState.selectedDateMillis; showPickerSelesai = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPickerSelesai = false }) { Text("Batal") } }
        ) { DatePicker(state = dateSelesaiState) }
    }

    // ── HRD Detail Modal ──────────────────────────────────────────────────────
    if (showModal) {
        Dialog(onDismissRequest = { showModal = false; selectedIzin = null }) {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Detail Izin", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider()

                    if (isLoadingDetail) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (selectedIzin != null) {
                        val izin = selectedIzin!!
                        IzinInfoRow("Nama",    izin.nama_custom)
                        IzinInfoRow("Jenis",   izin.jenis)
                        IzinInfoRow("Mulai",   formatDateDisplay(parseApiDate(izin.tgl_mulai)))
                        IzinInfoRow("Selesai", formatDateDisplay(parseApiDate(izin.tgl_selesai)))
                        IzinInfoRow("Alasan",  izin.alasan)
                        IzinInfoRow("Divisi",  izin.divisi_custom ?: "-")
                        IzinInfoRow("Jabatan", izin.jabatan_custom ?: "-")
                        IzinInfoRow("Status",  izin.status)

                        HorizontalDivider()

                        // Action Buttons
                        if (isUpdating) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Approve
                                Button(
                                    onClick = { updateIzinStatus(izin.id, "Approved") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Approve", fontWeight = FontWeight.Bold)
                                }

                                // Reject
                                Button(
                                    onClick = { updateIzinStatus(izin.id, "Rejected") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Filled.Error, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reject", fontWeight = FontWeight.Bold)
                                }

                                // Cancel / Close
                                OutlinedButton(
                                    onClick = { showModal = false; selectedIzin = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Tutup")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Main Content ──────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Form — only shown for non-HRD ─────────────────────────────────────
        if (!isHrd) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pengajuan Izin", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider()

                    // Jenis
                    Text("Jenis Izin", fontSize = 13.sp, color = Color.Gray)
                    Box {
                        OutlinedTextField(
                            value = jenis,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Pilih jenis izin") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.clickable { showJenisMenu = true }) },
                            modifier = Modifier.fillMaxWidth().clickable { showJenisMenu = true },
                            isError = formError.isNotEmpty() && jenis.isBlank()
                        )
                        DropdownMenu(expanded = showJenisMenu, onDismissRequest = { showJenisMenu = false }) {
                            JENIS_OPTIONS.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = { jenis = opt; showJenisMenu = false; formError = "" })
                            }
                        }
                    }

                    // Tgl Mulai
                    Text("Tanggal Mulai", fontSize = 13.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = formatDateDisplay(tglMulaiMillis),
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Pilih tanggal mulai") },
                        trailingIcon = { Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.clickable { showPickerMulai = true }) },
                        modifier = Modifier.fillMaxWidth().clickable { showPickerMulai = true },
                        isError = formError.isNotEmpty() && tglMulaiMillis == null
                    )

                    // Tgl Selesai
                    Text("Tanggal Selesai", fontSize = 13.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = formatDateDisplay(tglSelesaiMillis),
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Pilih tanggal selesai") },
                        trailingIcon = { Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.clickable { showPickerSelesai = true }) },
                        modifier = Modifier.fillMaxWidth().clickable { showPickerSelesai = true },
                        isError = formError.isNotEmpty() && tglSelesaiMillis == null
                    )

                    // Alasan
                    Text("Alasan", fontSize = 13.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = alasan,
                        onValueChange = { alasan = it; formError = "" },
                        placeholder = { Text("Masukkan alasan izin") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 5,
                        isError = formError.isNotEmpty() && alasan.isBlank()
                    )

                    // Bukti Sakit
                    Text("Bukti Sakit (Opsional)", fontSize = 13.sp, color = Color.Gray)
                    val buktiUri = remember(buktiPhotoPath, buktiTaken) {
                        buktiPhotoPath?.let { path ->
                            val f = File(path)
                            if (f.exists() && f.length() > 0)
                                FileProvider.getUriForFile(context, "com.example.absen_android.provider", f)
                            else null
                        }
                    }
                    if (buktiUri != null) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("✅ Foto dipilih", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            Row {
                                TextButton(onClick = { camPermLauncher.launch(Manifest.permission.CAMERA) }) { Text("Ganti") }
                                TextButton(onClick = { buktiPhotoPath = null }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { camPermLauncher.launch(Manifest.permission.CAMERA) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ambil Foto Bukti", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (formError.isNotEmpty()) {
                        Text(formError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { submitIzin() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("  Mengirim...", color = Color.White)
                        } else {
                            Text("Ajukan Izin", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // ── List ──────────────────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isHrd) "Semua Pengajuan Izin" else "Riwayat Izin", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { loadList() }, enabled = !isLoadingList) {
                        if (isLoadingList) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (isHrd) {
                    Text("Tap item untuk approve/reject", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when {
                    isLoadingList -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    izinList.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Belum ada data izin", color = Color.Gray) }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        izinList.forEach { izin ->
                            IzinItemCard(
                                izin = izin,
                                isClickable = isHrd,
                                onClick = { openIzinDetail(izin) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Izin Item Card ────────────────────────────────────────────────────────────
@Composable
fun IzinItemCard(izin: IzinData, isClickable: Boolean = false, onClick: () -> Unit = {}) {
    val statusColor = when (izin.status.lowercase()) {
        "approved" -> Color(0xFF4CAF50)
        "rejected" -> MaterialTheme.colorScheme.error
        else       -> Color(0xFFFFA726)
    }
    val statusIcon = when (izin.status.lowercase()) {
        "approved" -> Icons.Filled.CheckCircle
        "rejected" -> Icons.Filled.Error
        else       -> Icons.Filled.HourglassEmpty
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(if (isClickable) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(izin.jenis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(izin.nama_custom, fontSize = 12.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(izin.status, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            HorizontalDivider()
            IzinInfoRow("Mulai",   formatDateDisplay(parseApiDate(izin.tgl_mulai)))
            IzinInfoRow("Selesai", formatDateDisplay(parseApiDate(izin.tgl_selesai)))
            IzinInfoRow("Alasan",  izin.alasan)
            if (isClickable) {
                Text("Tap untuk review →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

// ── Info Row ──────────────────────────────────────────────────────────────────
@Composable
fun IzinInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.width(70.dp))
        Text(": ", fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
