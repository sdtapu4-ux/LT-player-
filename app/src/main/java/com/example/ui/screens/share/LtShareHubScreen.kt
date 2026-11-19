package com.example.ui.screens.share

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.share.ShareTransportMode
import com.example.share.ShareUiState
import com.example.share.WifiDirectPeerInfo
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.OnElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtShareHubScreen(
    shareState: ShareUiState,
    onSelectTransport: (ShareTransportMode) -> Unit,
    onGenerateQrClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onScanWifiDirectPeers: () -> Unit,
    onConnectWifiDirectPeer: (WifiDirectPeerInfo) -> Unit,
    onRefreshRadios: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val nearbyWifiPermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onScanWifiDirectPeers()
        onRefreshRadios()
    }

    fun requestConnectivityPermissions() {
        val list = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }.toTypedArray()
        nearbyWifiPermsLauncher.launch(list)
    }

    val hasNearbyPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Share, contentDescription = null, tint = ElectricGreen)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("LT SHARE", fontWeight = FontWeight.Bold)
                            Text(
                                "Encrypted QR Pairing & High-Speed P2P",
                                style = MaterialTheme.typography.labelSmall,
                                color = PremiumBlue
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ltshare_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshRadios, modifier = Modifier.testTag("ltshare_refresh_btn")) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Radios")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Security & Device Hero Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(listOf(ElectricGreen.copy(alpha = 0.55f), PremiumBlue.copy(alpha = 0.55f))),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ElectricGreen.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ElectricGreen)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(shareState.localDeviceName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "LAN IP: ${shareState.localIpAddress.ifBlank { "Offline" }} • ${shareState.encryptionCipherLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(16.dp))
                            Text(
                                "Short-lived tokens • Mutual confirmation • SHA-256 verified",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricGreen
                            )
                        }
                    }
                }
            }

            // Receive Files (Generate QR) & Send Files (Scan QR) Primary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // RECEIVE FILES CARD
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ElectricGreen.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = ElectricGreen)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Receive Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Host an encrypted session and show a temporary QR code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = onGenerateQrClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricGreen,
                                    contentColor = OnElectricGreen
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("generate_qr_button")
                            ) {
                                Text("Generate QR Code", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    // SEND FILES CARD
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PremiumBlue.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = PremiumBlue)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Send Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Scan receiver's QR code to authenticate and stream files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = onScanQrClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PremiumBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_qr_button")
                            ) {
                                Text("Scan QR Code", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Transport Mode Selector (Local Wi-Fi, Wi-Fi Direct, Nearby, Bluetooth)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Transport Channel", style = MaterialTheme.typography.titleMedium, color = ElectricGreen)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            shareState.selectedTransport.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ShareTransportMode.entries) { mode ->
                                val selected = shareState.selectedTransport == mode
                                val icon = when (mode) {
                                    ShareTransportMode.LOCAL_WIFI -> Icons.Rounded.Wifi
                                    ShareTransportMode.WIFI_DIRECT -> Icons.Rounded.WifiTethering
                                    ShareTransportMode.NEARBY_CONNECTIONS -> Icons.Rounded.Devices
                                    ShareTransportMode.BLUETOOTH -> Icons.Rounded.Bluetooth
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectTransport(mode) },
                                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(mode.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricGreen,
                                        selectedLabelColor = OnElectricGreen,
                                        selectedLeadingIconColor = OnElectricGreen
                                    ),
                                    modifier = Modifier.testTag("transport_chip_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Wi-Fi Direct & Nearby Permissions / Peers Panel
            if (!hasNearbyPerm) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enable Wi-Fi Direct & Bluetooth Discovery", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Required by Android for P2P peer discovery without a router.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { requestConnectivityPermissions() },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = OnElectricGreen)
                            ) {
                                Text("Grant")
                            }
                        }
                    }
                }
            }

            if (shareState.selectedTransport == ShareTransportMode.WIFI_DIRECT || shareState.wifiDirectPeers.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Wi-Fi Direct Peers", style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = {
                                    if (hasNearbyPerm) onScanWifiDirectPeers() else requestConnectivityPermissions()
                                }) {
                                    Text("Scan Peers")
                                }
                            }
                            if (shareState.wifiDirectGroupInfo.isNotBlank()) {
                                Text(shareState.wifiDirectGroupInfo, style = MaterialTheme.typography.labelSmall, color = ElectricGreen)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (shareState.wifiDirectPeers.isEmpty()) {
                                Text(
                                    "No Wi-Fi Direct peers detected yet. Tap Scan Peers or pair via QR.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                shareState.wifiDirectPeers.forEach { peer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onConnectWifiDirectPeer(peer) }
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(peer.deviceName, style = MaterialTheme.typography.titleSmall)
                                            Text(peer.deviceAddress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(peer.statusLabel, style = MaterialTheme.typography.labelSmall, color = ElectricGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bluetooth Paired Devices Panel
            if (shareState.selectedTransport == ShareTransportMode.BLUETOOTH) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bluetooth Status", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (shareState.bluetoothEnabled) "Enabled" else "Off",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (shareState.bluetoothEnabled) ElectricGreen else AmberGold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            if (shareState.bluetoothDevices.isEmpty()) {
                                Text(
                                    "Pair a device in Android Bluetooth settings or grant Nearby permissions to view paired devices.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                shareState.bluetoothDevices.forEach { dev ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = PremiumBlue, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(dev.name, style = MaterialTheme.typography.titleSmall)
                                            Text(dev.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Verified Share History
            if (shareState.history.isNotEmpty()) {
                item {
                    Text(
                        "Recent Verified Transfers",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(shareState.history.take(8), key = { it.id }) { record ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = "Verified",
                                tint = if (record.verified) ElectricGreen else AmberGold
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(record.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${record.direction} • ${record.peerDeviceName} • ${FormatUtils.formatFileSize(record.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "SHA-256 ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
