package com.example.ui.screens.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioItem
import com.example.data.model.VideoItem
import com.example.share.SharePairingStage
import com.example.share.ShareRole
import com.example.share.ShareUiState
import com.example.share.TransferItemStatus
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.OnElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtSharePairingConfirmScreen(
    shareState: ShareUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onBack: () -> Unit
) {
    val isAuthenticating = shareState.stage == SharePairingStage.PAIRING_AUTHENTICATING
    val isReceiver = shareState.role == ShareRole.RECEIVER

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pairing Confirmation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pairing_confirm_back_btn")) {
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, ElectricGreen, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(40.dp))
                    Text(
                        if (isReceiver) "Incoming Pairing Request" else "Confirm Device Connection",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DevicePillCard(label = "This Device", deviceName = shareState.localDeviceName, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Rounded.CompareArrows,
                            contentDescription = null,
                            tint = ElectricGreen,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        DevicePillCard(label = "Peer Device", deviceName = shareState.peerDeviceName, modifier = Modifier.weight(1f))
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ElectricGreen.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("6-DIGIT SECURITY CODE (SAS)", style = MaterialTheme.typography.labelSmall, color = ElectricGreen)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = shareState.sasVerificationCode.chunked(3).joinToString(" "),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElectricGreen,
                                modifier = Modifier.testTag("pairing_sas_code")
                            )
                            Text(
                                "Verify both phones display this identical code before accepting.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Text(
                        shareState.connectionStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumBlue
                    )
                }
            }

            if (!shareState.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CoralError.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        shareState.errorMessage,
                        color = CoralError,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (isAuthenticating) {
                CircularProgressIndicator(color = ElectricGreen)
                Text("Waiting for peer confirmation...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(52.dp).testTag("pairing_reject_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = CoralError)
                        Spacer(Modifier.width(6.dp))
                        Text("Reject", color = CoralError, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = OnElectricGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(52.dp).testTag("pairing_accept_btn")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isReceiver) "Accept Device" else "Confirm & Pair", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtShareConnectedDeviceScreen(
    shareState: ShareUiState,
    libraryVideos: List<VideoItem>,
    libraryAudio: List<AudioItem>,
    onAddUris: (List<android.net.Uri>) -> Unit,
    onAddLibraryVideos: (List<VideoItem>) -> Unit,
    onAddLibraryAudio: (List<AudioItem>) -> Unit,
    onRemoveFile: (String) -> Unit,
    onStartTransfer: () -> Unit,
    onBluetoothFallback: () -> Unit,
    onDisconnect: () -> Unit
) {
    var showLibraryPicker by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        onAddUris(uris)
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onAddUris(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connected Device", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDisconnect, modifier = Modifier.testTag("connected_disconnect_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Disconnect")
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
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, ElectricGreen, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(ElectricGreen.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = ElectricGreen)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(shareState.peerDeviceName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "Authenticated • ${shareState.selectedTransport.label} • SAS ${shareState.sasVerificationCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricGreen
                                )
                            }
                        }
                    }
                }

                if (shareState.role == ShareRole.RECEIVER) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = ElectricGreen, modifier = Modifier.size(36.dp))
                                Text("Encrypted Receiving Channel Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Waiting for ${shareState.peerDeviceName} to select and send files. Received files are verified with SHA-256 and saved to Downloads/LT_SHARE.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text("Select Files to Send", style = MaterialTheme.typography.titleMedium, color = ElectricGreen)
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).testTag("pick_visual_media_btn")
                            ) {
                                Icon(Icons.Rounded.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Video / Photo")
                            }
                            OutlinedButton(
                                onClick = { documentPicker.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).testTag("pick_any_files_btn")
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Docs / Any")
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showLibraryPicker = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("pick_lt_library_btn")
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pick from LT PLAYER Videos & Music (${libraryVideos.size + libraryAudio.size})")
                        }
                    }

                    if (shareState.selectedFiles.isNotEmpty()) {
                        item {
                            val totalSize = shareState.selectedFiles.sumOf { it.sizeBytes }
                            Text(
                                "${shareState.selectedFiles.size} file(s) queued • ${FormatUtils.formatFileSize(totalSize)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = PremiumBlue
                            )
                        }
                        items(shareState.selectedFiles, key = { it.uriString }) { file ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Description, contentDescription = null, tint = ElectricGreen)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${FormatUtils.formatFileSize(file.sizeBytes)} • ${file.mimeType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onRemoveFile(file.uriString) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (shareState.role == ShareRole.SENDER) {
                    Button(
                        onClick = onStartTransfer,
                        enabled = shareState.selectedFiles.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = OnElectricGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_transfer_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send Encrypted Files (${shareState.selectedFiles.size})", fontWeight = FontWeight.Bold)
                    }
                    if (shareState.selectedFiles.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onBluetoothFallback,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = PremiumBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share via System Bluetooth")
                        }
                    }
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Disconnect Session", color = CoralError)
                }
            }
        }
    }

    if (showLibraryPicker) {
        val chosenVideos = remember { mutableStateListOf<VideoItem>() }
        val chosenAudio = remember { mutableStateListOf<AudioItem>() }
        AlertDialog(
            onDismissRequest = { showLibraryPicker = false },
            title = { Text("Pick LT PLAYER Media") },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(libraryVideos, key = { "v_${it.uriString}" }) { v ->
                        val checked = chosenVideos.any { it.uriString == v.uriString }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) chosenVideos.removeAll { it.uriString == v.uriString } else chosenVideos.add(v)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(FormatUtils.formatFileSize(v.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(libraryAudio, key = { "a_${it.uriString}" }) { a ->
                        val checked = chosenAudio.any { it.uriString == a.uriString }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) chosenAudio.removeAll { it.uriString == a.uriString } else chosenAudio.add(a)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = PremiumBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(FormatUtils.formatFileSize(a.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddLibraryVideos(chosenVideos.toList())
                    onAddLibraryAudio(chosenAudio.toList())
                    showLibraryPicker = false
                }) { Text("Add (${chosenVideos.size + chosenAudio.size})") }
            },
            dismissButton = { TextButton(onClick = { showLibraryPicker = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtShareTransferProgressScreen(
    shareState: ShareUiState,
    onCancelTransfer: () -> Unit
) {
    val pct = (shareState.overallProgressFraction * 100f).toInt()
    val speedLabel = "${FormatUtils.formatFileSize(shareState.transferSpeedBytesPerSec)}/s"
    val etaLabel = formatCountdown(shareState.estimatedRemainingSeconds.toInt())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Transfer", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("$pct%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = ElectricGreen)
                    LinearProgressIndicator(
                        progress = { shareState.overallProgressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = ElectricGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speed: $speedLabel", style = MaterialTheme.typography.labelLarge, color = ElectricGreen, modifier = Modifier.testTag("transfer_speed_label"))
                        Text("ETA: $etaLabel", style = MaterialTheme.typography.labelLarge, color = PremiumBlue, modifier = Modifier.testTag("transfer_eta_label"))
                    }
                    Text(
                        "${FormatUtils.formatFileSize(shareState.transferredBytes)} / ${FormatUtils.formatFileSize(shareState.totalTransferBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shareState.transferItems, key = { it.fileIndex }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.status.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (item.status) {
                                        TransferItemStatus.VERIFIED -> ElectricGreen
                                        TransferItemStatus.FAILED -> CoralError
                                        else -> PremiumBlue
                                    }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            val frac = if (item.totalBytes > 0L) (item.bytesTransferred.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = ElectricGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onCancelTransfer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("cancel_transfer_btn")
            ) {
                Icon(Icons.Rounded.Cancel, contentDescription = null, tint = CoralError)
                Spacer(Modifier.width(8.dp))
                Text("Cancel Transfer", color = CoralError, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtShareTransferSuccessScreen(
    shareState: ShareUiState,
    onPlayMediaUri: (String) -> Unit,
    onSendMore: () -> Unit,
    onDone: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer Complete", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, ElectricGreen, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(56.dp))
                    Text("SHA-256 Verified Transfer Complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "${shareState.transferItems.size} file(s) • ${FormatUtils.formatFileSize(shareState.totalTransferBytes)} with ${shareState.peerDeviceName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shareState.transferItems, key = { it.fileIndex }) { item ->
                    val isPlayable = item.savedUriString != null &&
                        (item.mimeType.startsWith("video/") || item.mimeType.startsWith("audio/"))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = ElectricGreen)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "SHA-256: ${item.sha256Hex.take(16)}… • ${if (item.verified) "Verified ✓" else "Unverified"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.verified) ElectricGreen else AmberGold
                                )
                            }
                            if (isPlayable && item.savedUriString != null) {
                                IconButton(onClick = { onPlayMediaUri(item.savedUriString) }) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play in LT PLAYER", tint = ElectricGreen)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSendMore,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text(if (shareState.role == ShareRole.SENDER) "Send More" else "Stay Connected")
                }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = OnElectricGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp).testTag("transfer_success_done_btn")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DevicePillCard(label: String, deviceName: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(deviceName.ifBlank { "Device" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
