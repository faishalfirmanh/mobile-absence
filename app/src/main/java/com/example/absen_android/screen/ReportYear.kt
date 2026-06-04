package com.example.absen_android.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.absen_android.network.ReportItem
import com.example.absen_android.network.RetrofitClient
import com.example.absen_android.utils.ReportUtils
import com.example.absen_android.utils.SessionManager
import dev.shreyaspatil.capturable.capturable
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalComposeApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ReportYearScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var reportData by remember { mutableStateOf<List<ReportItem>>(emptyList()) }

    val calendar = Calendar.getInstance()
    var selectedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR).toString()) }

    val captureController = rememberCaptureController()

    fun fetchData() {
        scope.launch {
            isLoading = true
            try {
                val token = SessionManager.getToken(context)
                val response = RetrofitClient.instance.getYearlyReport(
                    token = "Bearer $token",
                    year = selectedYear
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    reportData = response.body()?.data ?: emptyList()
                } else {
                    Toast.makeText(context, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Laporan Tahunan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = selectedYear,
                onValueChange = { selectedYear = it },
                label = { Text("YYYY") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { fetchData() }) {
                Text("Cari")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            Button(
                onClick = {
                    ReportUtils.exportToExcel(
                        context,
                        "Report_Tahunan_${selectedYear}",
                        reportData
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Excel", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val bitmap = captureController.captureAsync().await()
                            ReportUtils.saveBitmapToGallery(
                                context,
                                bitmap.asAndroidBitmap(),
                                "Report_Tahunan_${selectedYear}"
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal capture: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Image", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .capturable(captureController)
                    .background(Color.White)
                    .horizontalScroll(scrollState)
            ) {
                Column {
                    // ── HEADER: background abu, teks HITAM BOLD ──
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFE0E0E0))
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        ReportHeaderCell(text = "Nama",    width = 150.dp)
                        ReportHeaderCell(text = "Hadir",   width = 60.dp)
                        ReportHeaderCell(text = "Sakit",   width = 60.dp)
                        ReportHeaderCell(text = "Izin",    width = 60.dp)
                        ReportHeaderCell(text = "Cuti",    width = 60.dp)
                        ReportHeaderCell(text = "Efektif", width = 70.dp)
                    }

                    HorizontalDivider(color = Color.Gray, thickness = 1.dp)

                    // ── ISI TABEL: teks HITAM ──
                    LazyColumn {
                        items(reportData) { item ->
                            Row(
                                modifier = Modifier
                                    .background(Color.White)
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                ReportContentCell(text = item.nama_karyawan,          width = 150.dp)
                                ReportContentCell(text = item.hadir_mesin.toString(), width = 60.dp)
                                ReportContentCell(text = item.sakit.toString(),        width = 60.dp)
                                ReportContentCell(text = item.izin.toString(),         width = 60.dp)
                                ReportContentCell(text = item.cuti.toString(),         width = 60.dp)
                                ReportContentCell(text = item.hari_efektif.toString(), width = 70.dp)
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

// ── COMPOSABLE HEADER: Bold, Hitam, dijamin terlihat ──
@Composable
fun ReportHeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = Color.Black,          // ← HITAM eksplisit
        textAlign = TextAlign.Start
    )
}

// ── COMPOSABLE KONTEN: Normal, Hitam, dijamin terlihat ──
@Composable
fun ReportContentCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = Color.Black,          // ← HITAM eksplisit
        textAlign = TextAlign.Start
    )
}
