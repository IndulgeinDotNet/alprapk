package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.PlateSighting
import com.example.data.remote.LivePlateCandidate
import com.example.data.remote.OfflinePlateScanner
import com.example.ui.components.PlateBadge
import com.example.ui.components.SeverityBadge
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    isScanning: Boolean,
    isAutoContinuousScanEnabled: Boolean,
    lastScannedResult: PlateSighting?,
    liveTrackingCandidate: LivePlateCandidate?,
    offlineScanner: OfflinePlateScanner,
    onToggleAutoScan: () -> Unit,
    onCaptureImage: (Bitmap) -> Unit,
    onAutoPlateCaptured: (LivePlateCandidate, Bitmap) -> Unit,
    onUpdateLiveCandidate: (LivePlateCandidate?) -> Unit,
    onViewSightingDetail: (PlateSighting) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] ?: false
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    onCaptureImage(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    // Fast scanning reticle animation
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TechDarkBg)
            .testTag("scanner_screen")
    ) {
        // High Frame-Rate Real-Time Video ALPR Stream
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val analysisExecutor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        // High-speed low-latency continuous stream image analyzer
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

                                offlineScanner.textRecognizer.process(inputImage)
                                    .addOnSuccessListener { visionText ->
                                        val candidate = offlineScanner.analyzeLiveText(visionText)

                                        ContextCompat.getMainExecutor(ctx).execute {
                                            onUpdateLiveCandidate(candidate)
                                        }

                                        // When candidate reaches multi-frame consensus threshold, snip vehicle and record
                                        if (candidate != null && candidate.isLockedAndReady && isAutoContinuousScanEnabled) {
                                            val frameBitmap = imageProxyToBitmap(imageProxy)
                                            if (frameBitmap != null) {
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    onAutoPlateCaptured(candidate, frameBitmap)
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis,
                                capture
                            )
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Viewfinder placeholder when camera permission is requested
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070D18)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "Camera Permission Required",
                        tint = TechCyanPrimary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Camera & GPS Access Required",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Point camera at vehicle plates for real-time optical recognition",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            permissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TechCyanPrimary)
                    ) {
                        Text("Grant Permissions", color = Color(0xFF381E72), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Tactical AR HUD Overlay & Laser Scanning Viewfinder
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val boxW = w * 0.88f
            val boxH = h * 0.35f
            val boxLeft = (w - boxW) / 2
            val boxTop = (h - boxH) / 2.5f

            // Corner Brackets
            val cornerLen = 36f
            val strokeW = 4f
            val cornerColor = if (liveTrackingCandidate != null) AlertGreen else TechCyanPrimary

            // Top-Left
            drawLine(cornerColor, Offset(boxLeft, boxTop), Offset(boxLeft + cornerLen, boxTop), strokeW)
            drawLine(cornerColor, Offset(boxLeft, boxTop), Offset(boxLeft, boxTop + cornerLen), strokeW)
            // Top-Right
            drawLine(cornerColor, Offset(boxLeft + boxW, boxTop), Offset(boxLeft + boxW - cornerLen, boxTop), strokeW)
            drawLine(cornerColor, Offset(boxLeft + boxW, boxTop), Offset(boxLeft + boxW, boxTop + cornerLen), strokeW)
            // Bottom-Left
            drawLine(cornerColor, Offset(boxLeft, boxTop + boxH), Offset(boxLeft + cornerLen, boxTop + boxH), strokeW)
            drawLine(cornerColor, Offset(boxLeft, boxTop + boxH), Offset(boxLeft, boxTop + boxH - cornerLen), strokeW)
            // Bottom-Right
            drawLine(cornerColor, Offset(boxLeft + boxW, boxTop + boxH), Offset(boxLeft + boxW - cornerLen, boxTop + boxH), strokeW)
            drawLine(cornerColor, Offset(boxLeft + boxW, boxTop + boxH), Offset(boxLeft + boxW, boxTop + boxH - cornerLen), strokeW)

            // Scanning Laser Line
            val laserY = boxTop + (boxH * laserPosition)
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        TechCyanPrimary.copy(alpha = 0.8f),
                        Color.White,
                        TechCyanPrimary.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                ),
                start = Offset(boxLeft, laserY),
                end = Offset(boxLeft + boxW, laserY),
                strokeWidth = 3f
            )
        }

        // Live AR Target Lock Indicator with Temporal Consensus Gauge
        liveTrackingCandidate?.let { candidate ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-50).dp),
                contentAlignment = Alignment.Center
            ) {
                val isLocked = candidate.consensusHits >= 3
                val borderColor = if (isLocked) AlertGreen else TechCyanPrimary

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.92f),
                    border = BorderStroke(2.dp, borderColor),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isLocked) AlertGreen else TechCyanPrimary)
                        )
                        Column {
                            Text(
                                text = candidate.plateNumber,
                                color = if (isLocked) AlertGreen else TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isLocked) "VERIFIED CONSENSUS LOCK" else "ACQUIRING (${candidate.consensusHits}/3 FRAMES)",
                                    color = if (isLocked) AlertGreen else TechCyanDim,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Status Bar: Optical Stream Status
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TechDarkBg.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, if (isAutoContinuousScanEnabled) AlertGreen.copy(alpha = 0.8f) else TechCyanPrimary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAutoContinuousScanEnabled) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AlertGreen)
                        )
                        Text(
                            text = "LIVE SENTRY: AUTO-LOGGING DETECTED PLATES",
                            color = AlertGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TechCyanPrimary)
                        )
                        Text(
                            text = "MANUAL SHUTTER MODE",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Live Auto-Captured Vehicle Snapshot Banner
        AnimatedVisibility(
            visible = lastScannedResult != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 125.dp, start = 16.dp, end = 16.dp)
        ) {
            lastScannedResult?.let { sighting ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewSightingDetail(sighting) }
                        .testTag("last_auto_sighting_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = TechSurface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, if (sighting.isFlagged) AlertRed else TechCyanPrimary),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Snipped Vehicle Thumbnail Preview
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TechDarkBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!sighting.snapshotUri.isNullOrBlank() && File(sighting.snapshotUri).exists()) {
                                AsyncImage(
                                    model = File(sighting.snapshotUri),
                                    contentDescription = "Snipped Vehicle",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = TechCyanPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlateBadge(
                                    plateNumber = sighting.plateNumber,
                                    stateOrRegion = sighting.stateOrRegion
                                )
                                if (sighting.isFlagged) {
                                    SeverityBadge(sighting.alertSeverity)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "📍 ${sighting.locationName}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Snipped & Geotagged • ${formatTimestamp(sighting.timestamp)}",
                                color = TechCyanDim,
                                fontSize = 10.sp
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View Dossier",
                            tint = TechCyanPrimary
                        )
                    }
                }
            }
        }

        // Bottom Capture Controls Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(115.dp),
            color = TechDarkBg.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, TechCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Gallery import button
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TechSurfaceVariant)
                        .testTag("pick_gallery_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Pick Image",
                        tint = TechCyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Sentry Mode Auto-Log Toggle Button
                Surface(
                    modifier = Modifier
                        .clickable { onToggleAutoScan() }
                        .testTag("toggle_sentry_mode_button"),
                    shape = RoundedCornerShape(24.dp),
                    color = if (isAutoContinuousScanEnabled) AlertGreen.copy(alpha = 0.2f) else TechSurfaceVariant,
                    border = BorderStroke(
                        1.5.dp,
                        if (isAutoContinuousScanEnabled) AlertGreen else TechCardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoContinuousScanEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Auto Sentry Mode",
                            tint = if (isAutoContinuousScanEnabled) AlertGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isAutoContinuousScanEnabled) "AUTO-LOG: ON" else "AUTO-LOG: OFF",
                            color = if (isAutoContinuousScanEnabled) AlertGreen else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Manual Shutter Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(TechCyanPrimary.copy(alpha = 0.2f))
                        .clickable {
                            val capture = imageCapture
                            if (capture != null) {
                                val executor = Executors.newSingleThreadExecutor()
                                capture.takePicture(
                                    executor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bmp = imageProxyToBitmap(image)
                                            image.close()
                                            if (bmp != null) {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    onCaptureImage(bmp)
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            exception.printStackTrace()
                                        }
                                    }
                                )
                            }
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(TechCyanPrimary)
                            .shadow(6.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Plate",
                            tint = Color(0xFF381E72),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Camera Lens Flip
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TechSurfaceVariant)
                        .testTag("switch_camera_lens_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Universal ImageProxy to Bitmap converter supporting YUV_420_888 and JPEG formats
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        imageProxy.toBitmap()
    } catch (e: Throwable) {
        try {
            if (imageProxy.format == ImageFormat.YUV_420_888) {
                val yBuffer = imageProxy.planes[0].buffer
                val uBuffer = imageProxy.planes[1].buffer
                val vBuffer = imageProxy.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
                val imageBytes = out.toByteArray()
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } else {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (ex: Throwable) {
            null
        }
    }
}
