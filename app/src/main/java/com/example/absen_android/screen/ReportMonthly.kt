package com.example.absen_android.screen

import android.graphics.Bitmap
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
fun ReportMonthlyScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var reportData by remember { mutableStateOf<List<ReportItem>>(emptyList()) }

    val calendar = Calendar.getInstance()
    var selectedMonth by remember { mutableStateOf(String.format("%02d", calendar.get(Calendar.MONTH) + 1)) }
    var selectedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR).toString()) }

    val captureController = rememberCaptureController()

    fun fetchData() {
        scope.launch {
            isLoading = true
            try {
                val token = SessionManager.getToken(context)
                val response = RetrofitClient.instance.getMonthlyReport(
                    token = "Bearer $token",
                    month = selectedMonth,
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
        Text("Laporan Bulanan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = selectedMonth,
                onValueChange = { selectedMonth = it },
                label = { Text("MM") },
                modifier = Modifier.width(70.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
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
                onClick = { ReportUtils.exportToExcel(context, "Report_Bulanan_${selectedMonth}_${selectedYear}", reportData) },
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
                            ReportUtils.saveBitmapToGallery(context, bitmap.asAndroidBitmap(), "Report_Bulanan_${selectedMonth}_${selectedYear}")
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
                    Row(modifier = Modifier.padding(8.dp)) {
                        ReportHeaderCell("Nama", 150.dp)
                        ReportHeaderCell("Hadir", 60.dp)
                        ReportHeaderCell("Sakit", 60.dp)
                        ReportHeaderCell("Izin", 60.dp)
                        ReportHeaderCell("Cuti", 60.dp)
                        ReportHeaderCell("Efektif", 70.dp)
                        ReportHeaderCell("Total Jam Kerja", 70.dp)
                    }
                    HorizontalDivider()
                    LazyColumn {
                        items(reportData) { item ->
                            Row(modifier = Modifier.padding(8.dp)) {
                                ReportContentCell(item.nama_karyawan, 150.dp)
                                ReportContentCell(item.hadir_mesin.toString(), 60.dp)
                                ReportContentCell(item.sakit.toString(), 60.dp)
                                ReportContentCell(item.izin.toString(), 60.dp)
                                ReportContentCell(item.cuti.toString(), 60.dp)
                                ReportContentCell(item.hari_efektif.toString(), 70.dp)
                                ReportContentCell(item.total_jam_masuk.toString(), 70.dp)
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
