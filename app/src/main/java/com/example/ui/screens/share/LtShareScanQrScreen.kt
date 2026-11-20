package com.example.ui.screens.share

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.share.ShareUiState
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.OnElectricGreen
import com.example.ui.theme.PremiumBlue
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtShareScanQrScreen(
    shareState: ShareUiState,
    onQrDetected: (String) -> Boolean,
    onValidQrProceed: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var lastAnalyzedValue by remember { mutableStateOf("") }
    var lastScanAttemptMs by remember { mutableStateOf(0L) }
    var scanHandled by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            cameraController.setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(
                    listOf(barcodeScanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    mainExecutor
                ) { result ->
                    if (scanHandled) return@MlKitAnalyzer
                    val barcodes = result.getValue(barcodeScanner) ?: return@MlKitAnalyzer
                    val raw = barcodes.firstOrNull()?.rawValue?.trim().orEmpty()
                    if (raw.isEmpty()) return@MlKitAnalyzer

                    val now = System.currentTimeMillis()
                    if (raw == lastAnalyzedValue && now - lastScanAttemptMs < 2000L) {
                        return@MlKitAnalyzer
                    }
                    lastAnalyzedValue = raw
                    lastScanAttemptMs = now

                    if (onQrDetected(raw)) {
                        scanHandled = true
                        onValidQrProceed()
                    }
                }
            )
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            runCatching {
                cameraController.enableTorch(false)
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { barcodeScanner.close() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scan_qr_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasCameraPermission) {
                        IconButton(
                            onClick = {
                                torchEnabled = !torchEnabled
                                cameraController.enableTorch(torchEnabled)
                            },
                            modifier = Modifier.testTag("scan_qr_torch_btn")
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = if (torchEnabled) "Turn Flashlight Off" else "Turn Flashlight On",
                                tint = if (torchEnabled) ElectricGreen else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    IconButton(
                        onClick = { showManualDialog = true },
                        modifier = Modifier.testTag("scan_qr_paste_btn")
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste Pairing Code")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            controller = cameraController
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Animated Scan Laser Reticle
                QrScannerReticleOverlay()

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!shareState.errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CoralError.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    shareState.errorMessage,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = ElectricGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Align the receiver's LT SHARE QR code inside the frame. Expired or tampered QRs are rejected automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Camera Permission Rationale Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(ElectricGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Camera Permission Needed",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "LT SHARE uses your camera only for real-time QR device pairing via CameraX & ML Kit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = OnElectricGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("grant_camera_perm_btn")
                    ) {
                        Text("Allow Camera Access", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Paste Pairing URI Instead", color = PremiumBlue)
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        var pasted by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Enter LT SHARE QR Payload") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste an ltshare://v1?p=... pairing link:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        placeholder = { Text("ltshare://v1?p=...") },
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showManualDialog = false
                    if (onQrDetected(pasted)) {
                        scanHandled = true
                        onValidQrProceed()
                    }
                }) { Text("Validate") }
            },
            dismissButton = { TextButton(onClick = { showManualDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QrScannerReticleOverlay() {
    val infinite = rememberInfiniteTransition(label = "qr_laser")
    val sweep by infinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frameSize = size.minDimension * 0.66f
        val left = (size.width - frameSize) / 2f
        val top = (size.height - frameSize) / 2f - size.height * 0.05f
        val right = left + frameSize
        val bottom = top + frameSize

        // Dim outer frame gently
        drawRect(Color.Black.copy(alpha = 0.45f), Offset.Zero, Size(size.width, top))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, top), Size(left, frameSize))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(right, top), Size(size.width - right, frameSize))

        drawRoundRect(
            color = ElectricGreen.copy(alpha = 0.45f),
            topLeft = Offset(left, top),
            size = Size(frameSize, frameSize),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 3f)
        )

        // Bright corner brackets
        val corner = frameSize * 0.14f
        val stroke = 8f
        val bracketColor = ElectricGreen
        // Top-left
        drawLine(bracketColor, Offset(left, top + corner), Offset(left, top), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
        // Top-right
        drawLine(bracketColor, Offset(right - corner, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
        // Bottom-left
        drawLine(bracketColor, Offset(left, bottom - corner), Offset(left, bottom), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
        // Bottom-right
        drawLine(bracketColor, Offset(right - corner, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, bottom - corner), Offset(right, bottom), stroke, StrokeCap.Round)

        // Sweeping laser beam
        val beamY = top + frameSize * sweep
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, ElectricGreen, PremiumBlue, Color.Transparent),
                startX = left,
                endX = right
            ),
            start = Offset(left + 14f, beamY),
            end = Offset(right - 14f, beamY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
    }
}
