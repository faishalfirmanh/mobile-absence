package com.example.absen_android.screen

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val OverlayColor = Color(0xFF2FEDEA)

// ── File helper ───────────────────────────────────────────────────────────────
fun createImageFile(context: Context): File {
    val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir = context.getExternalFilesDir("photos") ?: context.filesDir
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "PHOTO_$ts.jpg")
}

// ── Oval cutout modifier ──────────────────────────────────────────────────────
fun Modifier.ovalCutout(bgColor: Color = OverlayColor): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawRect(color = bgColor)
            val padH = size.width  * 0.10f
            val padV = size.height * 0.08f
            val path = Path().apply {
                addOval(
                    Rect(
                        offset = Offset(padH, padV),
                        size   = Size(size.width - padH * 2, size.height - padV * 2)
                    )
                )
            }
            drawPath(path = path, color = Color.Transparent, style = Fill, blendMode = BlendMode.Clear)
        }

// ── Crop & mirror captured photo to match preview ─────────────────────────────
suspend fun cropAndSavePhoto(sourceFile: File, outputFile: File) = withContext(Dispatchers.IO) {
    try {
        val original = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return@withContext

        // Fix EXIF rotation
        val exif = ExifInterface(sourceFile.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        // Mirror front camera horizontally to match preview
        matrix.postScale(-1f, 1f, original.width / 2f, original.height / 2f)

        val rotated = Bitmap.createBitmap(
            original, 0, 0, original.width, original.height, matrix, true
        )

        // Center-crop to square — matches FILL_CENTER PreviewView behavior
        val cropSize = minOf(rotated.width, rotated.height)
        val startX   = (rotated.width  - cropSize) / 2
        val startY   = (rotated.height - cropSize) / 2
        val cropped  = Bitmap.createBitmap(rotated, startX, startY, cropSize, cropSize)

        FileOutputStream(outputFile).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        original.recycle()
        rotated.recycle()
        cropped.recycle()
        sourceFile.delete()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ── CameraX Dialog ────────────────────────────────────────────────────────────
@Composable
fun CameraOverlayDialog(
    onPhotoCaptured: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    // Executor — properly shut down when dialog closes
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            // ← KEY FIX: shutdown executor when composable leaves composition
            cameraExecutor.shutdown()
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }

    var isCameraReady  by remember { mutableStateOf(false) }
    var isCapturing    by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // Unbind camera when dialog closes
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    fun capturePhoto() {
        if (!isCameraReady || isCapturing) return
        isCapturing = true

        val rawFile   = createImageFile(context)
        val finalFile = createImageFile(context)
        val opts      = ImageCapture.OutputFileOptions.Builder(rawFile).build()

        imageCapture.takePicture(
            opts,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture. OutputFileResults) {
                    scope.launch {
                        cropAndSavePhoto(rawFile, finalFile)
                        isCapturing = false
                        onPhotoCaptured(finalFile)
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    scope.launch(Dispatchers.Main) {
                        isCapturing = false
                        Toast.makeText(
                            context,
                            "Gagal mengambil foto: ${exc.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    Dialog(
        onDismissRequest = {
            cameraProvider?.unbindAll()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
        ) {
            if (hasPermission) {

                // Layer 1: CameraX PreviewView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType          = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }

                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                cameraProvider = provider

                                val preview = Preview.Builder()
                                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                    .build()
                                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                                val useCaseGroup = previewView.viewPort?.let { vp ->
                                    UseCaseGroup.Builder()
                                        .addUseCase(preview)
                                        .addUseCase(imageCapture)
                                        .setViewPort(vp)
                                        .build()
                                }

                                provider.unbindAll()

                                if (useCaseGroup != null) {
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        useCaseGroup
                                    )
                                } else {
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                }
                                isCameraReady = true
                            }.onFailure { e ->
                                Toast.makeText(
                                    ctx,
                                    "Kamera tidak tersedia: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                // Layer 2: Cyan overlay with oval cutout
                Box(modifier = Modifier.fillMaxSize().ovalCutout(OverlayColor))

                // Layer 3: UI controls

                // Close button
                IconButton(
                    onClick  = {
                        cameraProvider?.unbindAll()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, "Tutup", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Hint
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Posisikan wajah dalam bingkai",
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Shutter button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White, CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCapturing || !isCameraReady) Color.Gray
                                    else Color.White
                                )
                                .clickable(enabled = isCameraReady && !isCapturing) {
                                    capturePhoto()
                                }
                        )
                    }
                }

                // Capturing overlay
                if (isCapturing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text("📸 Mengambil foto...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

            } else {
                // No permission
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Izin kamera diperlukan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                    ) { Text("Izinkan Kamera", color = Color.White) }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                    ) { Text("Batal", color = Color.Gray) }
                }
            }
        }
    }
}

// ── Photo result ──────────────────────────────────────────────────────────────
@Composable
fun OvalPhotoResult(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier = modifier.background(OverlayColor)) {
        Image(
            painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context).data(uri).crossfade(true).build()
            ),
            contentDescription = "Foto absensi",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val padH = size.width  * 0.10f
                    val padV = size.height * 0.08f
                    val mask = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                        addOval(Rect(Offset(padH, padV), Size(size.width - padH * 2, size.height - padV * 2)))
                    }
                    drawPath(path = mask, color = OverlayColor, style = Fill, blendMode = BlendMode.SrcOver)
                }
        )
    }
}

// ── Photo Section ─────────────────────────────────────────────────────────────
@Composable
fun PhotoSection(
    displayUri: Uri?,
    onOpenOverlayDialog: () -> Unit,
    onDeletePhoto: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Foto Wajah", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (displayUri != null) {
                Text(
                    "Hapus",
                    color    = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onDeletePhoto() }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (displayUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                OvalPhotoResult(uri = displayUri, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenOverlayDialog, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ambil Ulang Foto")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OverlayColor)
                    .clickable { onOpenOverlayDialog() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.80f)
                        .fillMaxWidth(0.50f)
                        .border(3.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CameraAlt, null, tint = Color.Black.copy(alpha = 0.55f), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap untuk ambil foto wajah", color = Color.Black.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("(Kamera depan • maks 100 KB)", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.5f))
                }
            }
        }
    }
}
