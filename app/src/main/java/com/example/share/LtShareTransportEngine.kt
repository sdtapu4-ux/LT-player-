package com.example.share

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val TAG = "LtShareTransport"

@Serializable
data class HandshakeHelloFrame(
    val sessionId: String,
    val senderDeviceName: String,
    val senderPublicKey: String,
    val nonceHex: String,
    val hmacProof: String
)

@Serializable
data class HandshakeAckFrame(
    val accepted: Boolean,
    val receiverDeviceName: String = "",
    val reason: String = ""
)

@Serializable
data class EncryptedControlMessage(
    val type: String, // FILE_META, FILE_VERIFIED, TRANSFER_COMPLETE, CANCEL, ERROR
    val fileIndex: Int = 0,
    val fileName: String = "",
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val sha256Hex: String = "",
    val verified: Boolean = false,
    val message: String = ""
)

data class ReceivedFileHandle(
    val outputStream: OutputStream,
    val commit: () -> String?,
    val abort: () -> Unit
)

class LtShareTransportEngine(private val context: Context) {

    companion object {
        const val NSD_SERVICE_TYPE = "_ltshare._tcp."
        const val NEARBY_SERVICE_ID = "com.ltplayer.media.ltshare"
        const val MAX_FRAME_BYTES = 2 * 1024 * 1024
        const val CHUNK_SIZE_BYTES = 64 * 1024
    }

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var wifiP2pReceiver: BroadcastReceiver? = null

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    // Nearby incoming byte-frame queue when Nearby transport is used
    val nearbyIncomingFrames = LinkedBlockingQueue<ByteArray>()
    @Volatile
    var connectedNearbyEndpointId: String? = null
        private set

    fun getLocalDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return when {
            model.isEmpty() -> "LT PLAYER Device"
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isNotEmpty() -> "$manufacturer $model"
            else -> model
        }
    }

    fun resolveBestLocalIpv4(): String {
        return runCatching {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val candidates = mutableListOf<Pair<Int, String>>()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                val priority = when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("ap") || name.startsWith("swlan") -> 1
                    name.startsWith("p2p") -> 2
                    name.startsWith("eth") -> 3
                    else -> 4
                }
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        candidates.add(priority to (addr.hostAddress ?: ""))
                    }
                }
            }
            candidates.filter { it.second.isNotBlank() }.minByOrNull { it.first }?.second ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    fun createEphemeralServerSocket(): ServerSocket {
        return ServerSocket(0).apply {
            reuseAddress = true
            soTimeout = 0
        }
    }

    // --- NSD Local Wi-Fi Service Advertisement & Discovery ---

    fun registerNsdService(sessionIdPrefix: String, port: Int) {
        unregisterNsdService()
        val mgr = nsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "LTSHARE_${sessionIdPrefix.take(8)}"
            serviceType = NSD_SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD registered: ${nsdServiceInfo.serviceName}:${nsdServiceInfo.port}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        nsdRegistrationListener = listener
        runCatching { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun unregisterNsdService() {
        val listener = nsdRegistrationListener ?: return
        nsdRegistrationListener = null
        runCatching { nsdManager?.unregisterService(listener) }
    }

    fun stopNsdDiscovery() {
        val listener = nsdDiscoveryListener ?: return
        nsdDiscoveryListener = null
        runCatching { nsdManager?.stopServiceDiscovery(listener) }
    }

    // --- Wi-Fi Direct (P2P) Support ---

    fun hasNearbyWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startWifiDirect(
        createGroupForReceiver: Boolean,
        onPeersUpdated: (List<WifiDirectPeerInfo>) -> Unit,
        onConnectionChanged: (WifiP2pInfo) -> Unit,
        onStatusChanged: (Boolean, String) -> Unit
    ) {
        val mgr = wifiP2pManager
        if (mgr == null) {
            onStatusChanged(false, "Wi-Fi Direct hardware unavailable on this device.")
            return
        }
        if (!hasNearbyWifiPermission()) {
            onStatusChanged(false, "Nearby Wi-Fi permission required for Wi-Fi Direct.")
            return
        }
        if (wifiP2pChannel == null) {
            wifiP2pChannel = mgr.initialize(context, Looper.getMainLooper(), null)
        }
        val channel = wifiP2pChannel ?: return

        stopWifiDirectReceiver()
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        onStatusChanged(enabled, if (enabled) "Wi-Fi Direct Active" else "Wi-Fi Direct Disabled")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        if (hasNearbyWifiPermission()) {
                            mgr.requestPeers(channel) { list ->
                                val peers = list.deviceList.map { dev ->
                                    WifiDirectPeerInfo(
                                        deviceName = dev.deviceName.ifBlank { dev.deviceAddress },
                                        deviceAddress = dev.deviceAddress,
                                        statusLabel = when (dev.status) {
                                            WifiP2pDevice.CONNECTED -> "Connected"
                                            WifiP2pDevice.INVITED -> "Invited"
                                            WifiP2pDevice.AVAILABLE -> "Available"
                                            else -> "Unavailable"
                                        }
                                    )
                                }
                                onPeersUpdated(peers)
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        mgr.requestConnectionInfo(channel) { info ->
                            if (info != null) onConnectionChanged(info)
                        }
                    }
                }
            }
        }
        wifiP2pReceiver = receiver
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        if (createGroupForReceiver) {
            mgr.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onStatusChanged(true, "Wi-Fi Direct Group Owner Ready")
                }
                override fun onFailure(reason: Int) {
                    onStatusChanged(true, "Wi-Fi Direct ready (code $reason)")
                }
            })
        } else {
            mgr.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onStatusChanged(true, "Scanning Wi-Fi Direct peers...")
                }
                override fun onFailure(reason: Int) {
                    onStatusChanged(false, "Wi-Fi Direct peer scan error ($reason)")
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun connectWifiDirectPeer(deviceAddress: String, onResult: (Boolean, String) -> Unit) {
        val mgr = wifiP2pManager ?: return onResult(false, "Wi-Fi Direct unavailable")
        val channel = wifiP2pChannel ?: return onResult(false, "Wi-Fi Direct channel not initialized")
        if (!hasNearbyWifiPermission()) return onResult(false, "Missing Nearby Wi-Fi permission")
        val config = WifiP2pConfig().apply { this.deviceAddress = deviceAddress }
        mgr.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onResult(true, "Connecting via Wi-Fi Direct...")
            override fun onFailure(reason: Int) = onResult(false, "Wi-Fi Direct connect failed ($reason)")
        })
    }

    fun stopWifiDirectReceiver() {
        wifiP2pReceiver?.let { r ->
            runCatching { context.unregisterReceiver(r) }
        }
        wifiP2pReceiver = null
    }

    fun removeWifiDirectGroup() {
        val mgr = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        runCatching { mgr.removeGroup(channel, null) }
    }

    // --- Bluetooth Radio & Paired Devices Support ---

    fun getBluetoothSnapshot(): Pair<Boolean, List<BluetoothDeviceUi>> {
        val btMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = btMgr?.adapter
        if (adapter == null || !adapter.isEnabled) return false to emptyList()
        val hasConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!hasConnectPerm) return true to emptyList()

        val devices = runCatching {
            @SuppressLint("MissingPermission")
            val bonded = adapter.bondedDevices.orEmpty()
            bonded.map { dev ->
                @SuppressLint("MissingPermission")
                val name = dev.name ?: dev.address
                BluetoothDeviceUi(name = name, address = dev.address, isConnectedOrBonded = true)
            }
        }.getOrDefault(emptyList())
        return true to devices
    }

    fun buildBluetoothShareIntent(files: List<SelectedShareFile>): Intent? {
        if (files.isEmpty()) return null
        val uris = ArrayList<Uri>(files.map { Uri.parse(it.uriString) })
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = files.first().mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    // --- Nearby Connections Support ---

    fun startNearbyAdvertising(
        deviceName: String,
        onConnectionInitiated: (String, ConnectionInfo) -> Unit,
        onConnected: (String) -> Unit,
        onDisconnected: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        nearbyIncomingFrames.clear()
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        connectionsClient.startAdvertising(
            deviceName,
            NEARBY_SERVICE_ID,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                    onConnectionInitiated(endpointId, info)
                }
                override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        connectedNearbyEndpointId = endpointId
                        onConnected(endpointId)
                    } else {
                        onError("Nearby connection rejected or failed.")
                    }
                }
                override fun onDisconnected(endpointId: String) {
                    if (connectedNearbyEndpointId == endpointId) connectedNearbyEndpointId = null
                    onDisconnected(endpointId)
                }
            },
            options
        ).addOnFailureListener { e ->
            onError(e.localizedMessage ?: "Unable to start Nearby advertising.")
        }
    }

    fun startNearbyDiscovery(
        targetEndpointNameHint: String,
        localDeviceName: String,
        onEndpointFound: (String, String) -> Unit,
        onConnected: (String) -> Unit,
        onDisconnected: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        nearbyIncomingFrames.clear()
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        val lifecycle = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                connectionsClient.acceptConnection(endpointId, nearbyPayloadCallback)
            }
            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedNearbyEndpointId = endpointId
                    onConnected(endpointId)
                } else {
                    onError("Nearby connection failed.")
                }
            }
            override fun onDisconnected(endpointId: String) {
                if (connectedNearbyEndpointId == endpointId) connectedNearbyEndpointId = null
                onDisconnected(endpointId)
            }
        }
        connectionsClient.startDiscovery(
            NEARBY_SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    onEndpointFound(endpointId, info.endpointName)
                    if (info.endpointName.contains(targetEndpointNameHint, ignoreCase = true) ||
                        targetEndpointNameHint.isBlank()
                    ) {
                        connectionsClient.requestConnection(localDeviceName, endpointId, lifecycle)
                    }
                }
                override fun onEndpointLost(endpointId: String) {}
            },
            options
        ).addOnFailureListener { e ->
            onError(e.localizedMessage ?: "Nearby discovery unavailable.")
        }
    }

    fun acceptNearbyConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, nearbyPayloadCallback)
    }

    fun rejectNearbyConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
    }

    fun sendNearbyBytes(endpointId: String, data: ByteArray) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(data))
    }

    fun readNextNearbyFrame(timeoutMs: Long = 30_000L): ByteArray? {
        return nearbyIncomingFrames.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun stopNearby() {
        runCatching {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        }
        connectedNearbyEndpointId = null
        nearbyIncomingFrames.clear()
    }

    private val nearbyPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { nearbyIncomingFrames.offer(it) }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    // --- Encrypted Socket Framing ---

    fun writeRawFrame(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun readRawFrame(input: DataInputStream): ByteArray {
        val len = input.readInt()
        require(len in 1..MAX_FRAME_BYTES) { "Invalid frame size: $len" }
        val buf = ByteArray(len)
        input.readFully(buf)
        return buf
    }

    fun writeEncryptedFrame(out: DataOutputStream, encKey: ByteArray, seq: Long, plaintext: ByteArray) {
        val cipher = LtShareSecurity.encryptFrame(encKey, seq, plaintext)
        writeRawFrame(out, cipher)
    }

    fun readEncryptedFrame(input: DataInputStream, encKey: ByteArray, seq: Long): ByteArray {
        val cipher = readRawFrame(input)
        return LtShareSecurity.decryptFrame(encKey, seq, cipher)
    }

    // --- Safe Media/File Output Creation ---

    fun openReceivedFileOutput(fileName: String, mimeType: String): ReceivedFileHandle {
        val safeName = sanitizeFilename(fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/LT_SHARE")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val stream = resolver.openOutputStream(uri)
                if (stream != null) {
                    return ReceivedFileHandle(
                        outputStream = BufferedOutputStream(stream, CHUNK_SIZE_BYTES),
                        commit = {
                            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                            resolver.update(uri, done, null, null)
                            uri.toString()
                        },
                        abort = {
                            runCatching { stream.close() }
                            runCatching { resolver.delete(uri, null, null) }
                        }
                    )
                }
            }
        }

        val dir = context.getExternalFilesDir("LTShare") ?: File(context.filesDir, "LTShare")
        if (!dir.exists()) dir.mkdirs()
        var outFile = File(dir, safeName)
        var counter = 1
        while (outFile.exists()) {
            val base = safeName.substringBeforeLast('.')
            val ext = safeName.substringAfterLast('.', "")
            outFile = File(dir, if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${safeName}_$counter")
            counter++
        }
        val fos = BufferedOutputStream(FileOutputStream(outFile), CHUNK_SIZE_BYTES)
        return ReceivedFileHandle(
            outputStream = fos,
            commit = { Uri.fromFile(outFile).toString() },
            abort = {
                runCatching { fos.close() }
                runCatching { outFile.delete() }
            }
        )
    }

    fun queryUriMetadata(uri: Uri): SelectedShareFile {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "shared_file_${System.currentTimeMillis()}"
        var size = -1L
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { if (it.isNotBlank()) name = it }
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        if (size <= 0L) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) size = afd.length
                }
            }
        }
        return SelectedShareFile(
            uriString = uri.toString(),
            displayName = name,
            mimeType = mime,
            sizeBytes = size.coerceAtLeast(0L)
        )
    }

    fun computeUriSha256(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            return LtShareSecurity.computeSha256Hex(BufferedInputStream(stream, CHUNK_SIZE_BYTES))
        }
        error("Cannot read file for SHA-256 checksum: $uri")
    }

    private fun sanitizeFilename(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "lt_share_${System.currentTimeMillis()}.bin" }
    }
}
