package com.example.absen_android.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.absen_android.network.ReportItem
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

object ReportUtils {

    fun exportToExcel(context: Context, fileName: String, data: List<ReportItem>) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Report")

            // Header
            val headerRow = sheet.createRow(0)
            val headers = listOf("Nama", "Hadir", "Sakit", "Izin", "Cuti", "Efektif")
            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).setCellValue(header)
            }

            // Data
            data.forEachIndexed { index, item ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(item.nama_karyawan)
                row.createCell(1).setCellValue(item.hadir_mesin.toDouble())
                row.createCell(2).setCellValue(item.sakit.toDouble())
                row.createCell(3).setCellValue(item.izin.toDouble())
                row.createCell(4).setCellValue(item.cuti.toDouble())
                row.createCell(5).setCellValue(item.hari_efektif.toDouble())
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.xlsx")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri: Uri? = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
                outputStream?.use { os ->
                    workbook.write(os)
                }
                Toast.makeText(context, "Excel disimpan di Downloads", Toast.LENGTH_LONG).show()
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal export Excel: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
            }

            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
                outputStream?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
                Toast.makeText(context, "Gambar disimpan di Galeri", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal simpan gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
