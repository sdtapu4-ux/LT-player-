package com.example.share

import android.graphics.Bitmap
import com.example.data.database.ShareTransferHistoryEntity
import kotlinx.serialization.Serializable

enum class ShareTransportMode(val label: String, val description: String) {
    LOCAL_WIFI("Local Wi-Fi", "High-speed encrypted LAN / Hotspot TCP + NSD"),
    WIFI_DIRECT("Wi-Fi Direct", "Direct P2P group without router"),
    NEARBY_CONNECTIONS("Nearby", "Cross-band Nearby P2P discovery & transfer"),
    BLUETOOTH("Bluetooth", "Bluetooth paired device & system share")
}

enum class ShareRole {
    NONE,
    RECEIVER,
    SENDER
}

enum class SharePairingStage {
    IDLE,
    GENERATING_QR,
    WAITING_FOR_SCAN,
    QR_SCANNED_PREVIEW,
    AWAITING_SENDER_CONFIRM,
    AWAITING_RECEIVER_CONFIRM,
    PAIRING_AUTHENTICATING,
    CONNECTED,
    TRANSFERRING,
    TRANSFER_SUCCESS,
    EXPIRED,
    REJECTED,
    ERROR
}

enum class TransferItemStatus {
    QUEUED,
    HASHING,
    TRANSFERRING,
    VERIFYING_SHA256,
    VERIFIED,
    FAILED,
    CANCELLED
}

/**
 * Temporary QR payload containing ONLY short-lived connection metadata.
 * Never holds private files, passwords, permanent secrets, or location coordinates.
 */
@Serializable
data class QrSessionPayload(
    val v: Int = 1,
    val sid: String,
    val tok: String,
    val name: String,
    val ip: String,
    val port: Int,
    val p2p: String = "",
    val mode: String = ShareTransportMode.LOCAL_WIFI.name,
    val pk: String,
    val exp: Long
)

data class WifiDirectPeerInfo(
    val deviceName: String,
    val deviceAddress: String,
    val statusLabel: String
)

data class BluetoothDeviceUi(
    val name: String,
    val address: String,
    val isConnectedOrBonded: Boolean
)

data class SelectedShareFile(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class FileTransferProgressItem(
    val fileIndex: Int,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val bytesTransferred: Long = 0L,
    val sha256Hex: String = "",
    val verified: Boolean = false,
    val savedUriString: String? = null,
    val status: TransferItemStatus = TransferItemStatus.QUEUED
)

data class ShareUiState(
    val role: ShareRole = ShareRole.NONE,
    val stage: SharePairingStage = SharePairingStage.IDLE,
    val selectedTransport: ShareTransportMode = ShareTransportMode.LOCAL_WIFI,
    val localDeviceName: String = "",
    val peerDeviceName: String = "",
    val sessionId: String = "",
    val sessionTokenPreview: String = "",
    val qrUriContent: String = "",
    val qrBitmap: Bitmap? = null,
    val scannedPayload: QrSessionPayload? = null,
    val qrTtlSeconds: Int = 180,
    val remainingSeconds: Int = 0,
    val sasVerificationCode: String = "",
    val connectionStatusText: String = "Ready to pair",
    val encryptionCipherLabel: String = "ECDH P-256 + AES-256-GCM + SHA-256",
    val localIpAddress: String = "",
    val localPort: Int = 0,
    val selectedFiles: List<SelectedShareFile> = emptyList(),
    val transferItems: List<FileTransferProgressItem> = emptyList(),
    val totalTransferBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val transferSpeedBytesPerSec: Long = 0L,
    val estimatedRemainingSeconds: Long = 0L,
    val currentFileIndex: Int = 0,
    val wifiDirectEnabled: Boolean = false,
    val wifiDirectPeers: List<WifiDirectPeerInfo> = emptyList(),
    val wifiDirectGroupInfo: String = "",
    val bluetoothEnabled: Boolean = false,
    val bluetoothDevices: List<BluetoothDeviceUi> = emptyList(),
    val nearbyDiscoveredEndpoints: List<String> = emptyList(),
    val history: List<ShareTransferHistoryEntity> = emptyList(),
    val errorMessage: String? = null
) {
    val overallProgressFraction: Float
        get() = if (totalTransferBytes > 0L) {
            (transferredBytes.toFloat() / totalTransferBytes.toFloat()).coerceIn(0f, 1f)
        } else if (transferItems.isNotEmpty() && transferItems.all { it.status == TransferItemStatus.VERIFIED }) {
            1f
        } else 0f
}
