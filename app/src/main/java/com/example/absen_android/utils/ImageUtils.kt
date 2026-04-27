package com.example.absen_android.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    fun compressImageToMaxSize(context: Context, sourceFile: File, maxSizeKb: Int = 100): File {
        val maxBytes = maxSizeKb * 1024

        var bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            ?: return sourceFile

        bitmap = fixRotation(bitmap, sourceFile.absolutePath)
        bitmap = scaleDownIfNeeded(bitmap)

        val outputFile = File(
            context.getExternalFilesDir("photos") ?: context.filesDir,
            "compressed_${sourceFile.name}"
        )

        // Try decreasing quality first
        var quality = 90
        var outputStream = FileOutputStream(outputFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        outputStream.flush()
        outputStream.close()

        while (outputFile.length() > maxBytes && quality > 10) {
            quality -= 10
            outputStream = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
            outputStream.close()
        }

        // If still too big, scale down further
        if (outputFile.length() > maxBytes) {
            var scaleFactor = 0.8f
            while (outputFile.length() > maxBytes && scaleFactor > 0.1f) {
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scaleFactor).toInt(),
                    (bitmap.height * scaleFactor).toInt(),
                    true
                )
                outputStream = FileOutputStream(outputFile)
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.flush()
                outputStream.close()
                scaledBitmap.recycle()
                scaleFactor -= 0.1f
            }
        }

        bitmap.recycle()
        return outputFile
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxWidth: Int = 1280, maxHeight: Int = 1280): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (width * ratio).toInt(),
            (height * ratio).toInt(),
            true
        )
    }

    private fun fixRotation(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
