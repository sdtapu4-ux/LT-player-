package com.example.ui.screens.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.share.SharePairingStage
import com.example.share.ShareRole
import com.example.share.ShareUiState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.OnElectricGreen
import com.example.ui.theme.PremiumBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtShareGenerateQrScreen(
    shareState: ShareUiState,
    onSelectTtlSeconds: (Int) -> Unit,
    onRegenerateQr: () -> Unit,
    onOpenQrPreview: () -> Unit,
    onCancelSession: () -> Unit,
    onBack: () -> Unit
) {
    val isExpired = shareState.stage == SharePairingStage.EXPIRED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate QR Code", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("gen_qr_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (shareState.qrBitmap != null) {
                        IconButton(onClick = onOpenQrPreview, modifier = Modifier.testTag("open_qr_preview_btn")) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = "QR Preview", tint = ElectricGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Connection Status & Timer Strip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(shareState.localDeviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            shareState.connectionStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isExpired) CoralError else ElectricGreen
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (shareState.remainingSeconds <= 20) CoralError.copy(alpha = 0.2f) else ElectricGreen.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = "Countdown Timer",
                                tint = if (shareState.remainingSeconds <= 20) CoralError else ElectricGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatCountdown(shareState.remainingSeconds),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (shareState.remainingSeconds <= 20) CoralError else ElectricGreen,
                                modifier = Modifier.testTag("qr_countdown_timer")
                            )
                        }
                    }
                }
            }

            // QR Code Display Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, if (isExpired) CoralError else ElectricGreen, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bmp = shareState.qrBitmap
                    if (shareState.stage == SharePairingStage.GENERATING_QR) {
                        Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ElectricGreen)
                        }
                    } else if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .size(252.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .clickable(onClick = onOpenQrPreview)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "LT SHARE Pairing QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("generated_qr_image"),
                                alpha = if (isExpired) 0.22f else 1f
                            )
                            if (isExpired) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        "QR EXPIRED\nTap Regenerate",
                                        color = CoralError,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Session ${shareState.sessionId.take(8).uppercase()} • Token ${shareState.sessionTokenPreview}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PremiumBlue
                    )
                    Text(
                        "Host ${shareState.localIpAddress}:${shareState.localPort} (${shareState.selectedTransport.label})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Configurable Expiry Selector
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("QR Expiry", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60 to "1 min", 180 to "3 min", 300 to "5 min").forEach { (sec, label) ->
                            FilterChip(
                                selected = shareState.qrTtlSeconds == sec,
                                onClick = {
                                    onSelectTtlSeconds(sec)
                                    onRegenerateQr()
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricGreen,
                                    selectedLabelColor = OnElectricGreen
                                )
                            )
                        }
                    }
                }
            }

            // Security Notice
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(16.dp))
                Text(
                    "Contains only temporary session token & ECDH P-256 public key. Never shares files, passwords or location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions: Regenerate & Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRegenerateQr,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = OnElectricGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("regenerate_qr_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Regenerate QR", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCancelSession,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cancel_qr_session_button")
                ) {
                    Icon(Icons.Rounded.Cancel, contentDescription = null, tint = CoralError, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel Session", color = CoralError, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtShareQrPreviewScreen(
    shareState: ShareUiState,
    onContinueToConfirm: () -> Unit,
    onRescanQr: () -> Unit,
    onBack: () -> Unit
) {
    val isSenderPreview = shareState.role == ShareRole.SENDER && shareState.scannedPayload != null
    val scanned = shareState.scannedPayload

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSenderPreview) "QR Preview & Device Info" else "QR Fullscreen Preview", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("qr_preview_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isSenderPreview && shareState.qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(310.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(3.dp, ElectricGreen, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = shareState.qrBitmap.asImageBitmap(),
                        contentDescription = "Enlarged Pairing QR",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    "Ask the sender to point their camera at this code",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Expires in ${formatCountdown(shareState.remainingSeconds)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricGreen
                )
            } else if (scanned != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, ElectricGreen, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Valid LT SHARE QR Detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Ready for mutual pairing verification", style = MaterialTheme.typography.bodySmall, color = ElectricGreen)
                            }
                        }

                        PreviewDetailRow("Detected Device", scanned.name)
                        PreviewDetailRow("Local Endpoint", "${scanned.ip}:${scanned.port}")
                        PreviewDetailRow("Transport Mode", scanned.mode)
                        PreviewDetailRow("Session ID", scanned.sid.take(12).uppercase())
                        PreviewDetailRow("Security", "ECDH P-256 + Ephemeral Token")
                        PreviewDetailRow("Expires In", formatCountdown(shareState.remainingSeconds))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = AmberGold)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Never trust unknown devices automatically. Next step requires confirmation on both phones.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
                    onClick = onContinueToConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = OnElectricGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("qr_preview_continue_btn")
                ) {
                    Icon(Icons.Rounded.Devices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Proceed to Pairing Confirmation", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRescanQr,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Scan Different QR")
                }
            }
        }
    }
}

@Composable
private fun PreviewDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

fun formatCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(mins, secs)
}
