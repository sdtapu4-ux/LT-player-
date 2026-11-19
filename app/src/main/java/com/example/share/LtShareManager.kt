package com.example.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.data.database.ShareHistoryDao
import com.example.data.database.ShareTransferHistoryEntity
import com.example.data.model.AudioItem
import com.example.data.model.VideoItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "LtShareManager"

class LtShareManager(
    private val context: Context,
    private val shareHistoryDao: ShareHistoryDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val transport = LtShareTransportEngine(context)
    val replayGuard = LtShareReplayGuard()

    private val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(
        ShareUiState(localDeviceName = transport.getLocalDeviceName())
    )
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var acceptJob: Job? = null
    private var countdownJob: Job? = null
    private var transferJob: Job? = null

    private var activeKeyPair: KeyPair? = null
    private var activeSessionToken: String = ""
    private var activeExpiresAtMs: Long = 0L
    private var activeEncKey: ByteArray? = null
    private var receiverApprovalDeferred: CompletableDeferred<Boolean>? = null

    private val txSeq = AtomicLong(1L)
    private val rxSeq = AtomicLong(1L)
    private val transferCancelled = AtomicBoolean(false)

    init {
        scope.launch {
            shareHistoryDao.observeRecent().collect { items ->
                _state.update { it.copy(history = items) }
            }
        }
        refreshRadios()
    }

    fun refreshRadios() {
        val (btEnabled, btDevices) = transport.getBluetoothSnapshot()
        val ip = transport.resolveBestLocalIpv4()
        _state.update {
            it.copy(
                localDeviceName = transport.getLocalDeviceName(),
                localIpAddress = ip,
                bluetoothEnabled = btEnabled,
                bluetoothDevices = btDevices
            )
        }
    }

    fun selectTransportMode(mode: ShareTransportMode) {
        _state.update { it.copy(selectedTransport = mode, errorMessage = null) }
        if (mode == ShareTransportMode.WIFI_DIRECT) {
            scanWifiDirectPeers()
        } else if (mode == ShareTransportMode.BLUETOOTH) {
            refreshRadios()
        }
    }

    fun setQrTtlSeconds(seconds: Int) {
        _state.update { it.copy(qrTtlSeconds = seconds.coerceIn(30, 600)) }
    }

    fun scanWifiDirectPeers() {
        transport.startWifiDirect(
            createGroupForReceiver = false,
            onPeersUpdated = { peers ->
                _state.update { it.copy(wifiDirectPeers = peers) }
            },
            onConnectionChanged = { info ->
                val goIp = info.groupOwnerAddress?.hostAddress.orEmpty()
                _state.update {
                    it.copy(
                        wifiDirectGroupInfo = if (info.groupFormed) "P2P Group Active ($goIp)" else ""
                    )
                }
            },
            onStatusChanged = { enabled, msg ->
                _state.update { it.copy(wifiDirectEnabled = enabled, connectionStatusText = msg) }
            }
        )
    }

    fun connectWifiDirectPeer(peer: WifiDirectPeerInfo) {
        transport.connectWifiDirectPeer(peer.deviceAddress) { ok, msg ->
            _state.update {
                it.copy(
                    connectionStatusText = msg,
                    errorMessage = if (ok) null else msg
                )
            }
        }
    }

    // =========================================================================
    // 1. RECEIVER: GENERATE QR SESSION
    // =========================================================================

    fun startReceivingSession(ttlSeconds: Int = _state.value.qrTtlSeconds) {
        cleanupTransportOnly(retireCurrentSession = true)
        _state.update {
            it.copy(
                role = ShareRole.RECEIVER,
                stage = SharePairingStage.GENERATING_QR,
                qrTtlSeconds = ttlSeconds,
                peerDeviceName = "",
                sasVerificationCode = "",
                selectedFiles = emptyList(),
                transferItems = emptyList(),
                errorMessage = null,
                connectionStatusText = "Starting secure local listener..."
            )
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val srv = transport.createEphemeralServerSocket()
                    serverSocket = srv
                    val port = srv.localPort
                    val hostIp = transport.resolveBestLocalIpv4()
                    val sessionId = LtShareSecurity.generateSessionId()
                    val token = LtShareSecurity.generateEphemeralToken()
                    val keyPair = LtShareSecurity.generateEcdhKeyPair()
                    val expiresAtMs = System.currentTimeMillis() + ttlSeconds * 1000L

                    activeKeyPair = keyPair
                    activeSessionToken = token
                    activeExpiresAtMs = expiresAtMs

                    val payload = QrSessionPayload(
                        v = 1,
                        sid = sessionId,
                        tok = token,
                        name = transport.getLocalDeviceName(),
                        ip = hostIp,
                        port = port,
                        mode = _state.value.selectedTransport.name,
                        pk = LtShareSecurity.encodePublicKey(keyPair.public),
                        exp = expiresAtMs
                    )
                    val qrContent = LtShareQrCodec.encode(payload)
                    val bitmap = LtShareQrCodec.generateQrBitmap(qrContent, 720)
                    transport.registerNsdService(sessionId, port)
                    Triple(payload, qrContent, bitmap)
                }
            }

            result.onSuccess { (payload, qrContent, bitmap) ->
                _state.update {
                    it.copy(
                        stage = SharePairingStage.WAITING_FOR_SCAN,
                        sessionId = payload.sid,
                        sessionTokenPreview = payload.tok.take(8) + "••••",
                        qrUriContent = qrContent,
                        qrBitmap = bitmap,
                        localIpAddress = payload.ip,
                        localPort = payload.port,
                        remainingSeconds = ttlSeconds,
                        connectionStatusText = "Waiting for sender to scan QR..."
                    )
                }
                if (_state.value.selectedTransport == ShareTransportMode.WIFI_DIRECT) {
                    transport.startWifiDirect(
                        createGroupForReceiver = true,
                        onPeersUpdated = { peers -> _state.update { it.copy(wifiDirectPeers = peers) } },
                        onConnectionChanged = { info ->
                            val goIp = info.groupOwnerAddress?.hostAddress.orEmpty()
                            if (goIp.isNotBlank()) {
                                _state.update { it.copy(wifiDirectGroupInfo = "P2P GO: $goIp") }
                            }
                        },
                        onStatusChanged = { enabled, msg ->
                            _state.update { it.copy(wifiDirectEnabled = enabled, connectionStatusText = msg) }
                        }
                    )
                }
                startCountdownTimer(payload.exp, isReceiver = true)
                startReceiverAcceptLoop(payload.sid)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        stage = SharePairingStage.ERROR,
                        errorMessage = err.localizedMessage ?: "Failed to start receiving session."
                    )
                }
            }
        }
    }

    fun regenerateQr() {
        startReceivingSession(_state.value.qrTtlSeconds)
    }

    fun cancelSession() {
        transferCancelled.set(true)
        cleanupTransportOnly(retireCurrentSession = true)
        _state.update {
            ShareUiState(
                localDeviceName = transport.getLocalDeviceName(),
                selectedTransport = it.selectedTransport,
                qrTtlSeconds = it.qrTtlSeconds,
                localIpAddress = transport.resolveBestLocalIpv4(),
                bluetoothEnabled = it.bluetoothEnabled,
                bluetoothDevices = it.bluetoothDevices,
                history = it.history
            )
        }
    }

    private fun startReceiverAcceptLoop(expectedSessionId: String) {
        acceptJob?.cancel()
        acceptJob = scope.launch(Dispatchers.IO) {
            val srv = serverSocket ?: return@launch
            while (isActive && !srv.isClosed) {
                val client = runCatching { srv.accept() }.getOrNull() ?: break
                try {
                    client.soTimeout = 30_000
                    val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                    val output = DataOutputStream(client.getOutputStream())

                    val rawHello = transport.readRawFrame(input)
                    val hello = wireJson.decodeFromString(HandshakeHelloFrame.serializer(), rawHello.toString(Charsets.UTF_8))

                    val now = System.currentTimeMillis()
                    if (hello.sessionId != expectedSessionId ||
                        now >= activeExpiresAtMs ||
                        replayGuard.isRetired(expectedSessionId)
                    ) {
                        val ack = HandshakeAckFrame(accepted = false, reason = "Session expired or invalid.")
                        transport.writeRawFrame(output, wireJson.encodeToString(HandshakeAckFrame.serializer(), ack).toByteArray())
                        client.close()
                        continue
                    }

                    val myPair = activeKeyPair ?: error("Missing ECDH keypair")
                    val senderPub = LtShareSecurity.decodePublicKey(hello.senderPublicKey)
                    val keys = LtShareSecurity.deriveSessionKeys(myPair.private, senderPub, expectedSessionId, activeSessionToken)

                    val verified = LtShareSecurity.verifyClientHelloProof(
                        authKey = keys.authKey,
                        sessionId = expectedSessionId,
                        token = activeSessionToken,
                        senderName = hello.senderDeviceName,
                        nonceHex = hello.nonceHex,
                        providedProof = hello.hmacProof
                    )
                    if (!verified) {
                        val ack = HandshakeAckFrame(accepted = false, reason = "Authentication token verification failed.")
                        transport.writeRawFrame(output, wireJson.encodeToString(HandshakeAckFrame.serializer(), ack).toByteArray())
                        client.close()
                        continue
                    }

                    // Require explicit confirmation from Receiver user! Never auto-trust unknown devices.
                    val deferred = CompletableDeferred<Boolean>()
                    receiverApprovalDeferred = deferred
                    _state.update {
                        it.copy(
                            stage = SharePairingStage.AWAITING_RECEIVER_CONFIRM,
                            peerDeviceName = hello.senderDeviceName,
                            sasVerificationCode = keys.sasCode,
                            connectionStatusText = "Confirm pairing with ${hello.senderDeviceName}"
                        )
                    }

                    val approved = deferred.await()
                    if (!approved) {
                        replayGuard.markRetired(expectedSessionId)
                        val ack = HandshakeAckFrame(
                            accepted = false,
                            receiverDeviceName = transport.getLocalDeviceName(),
                            reason = "Connection declined by receiver."
                        )
                        transport.writeRawFrame(output, wireJson.encodeToString(HandshakeAckFrame.serializer(), ack).toByteArray())
                        client.close()
                        _state.update {
                            it.copy(
                                stage = SharePairingStage.REJECTED,
                                connectionStatusText = "Pairing declined",
                                errorMessage = "You rejected connection from ${hello.senderDeviceName}."
                            )
                        }
                        break
                    }

                    // Single-use session lock: prevent any replay or reuse of this QR session
                    replayGuard.markRetired(expectedSessionId)
                    countdownJob?.cancel()
                    activeEncKey = keys.encKey
                    activeSocket = client
                    txSeq.set(1L)
                    rxSeq.set(1L)

                    val ack = HandshakeAckFrame(
                        accepted = true,
                        receiverDeviceName = transport.getLocalDeviceName()
                    )
                    transport.writeRawFrame(output, wireJson.encodeToString(HandshakeAckFrame.serializer(), ack).toByteArray())
                    runCatching { srv.close() }

                    _state.update {
                        it.copy(
                            stage = SharePairingStage.CONNECTED,
                            connectionStatusText = "Encrypted link established with ${hello.senderDeviceName}",
                            errorMessage = null
                        )
                    }

                    receiveEncryptedTransferLoop(client, input, output, keys.encKey, expectedSessionId, hello.senderDeviceName)
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Receiver handshake/transfer error", e)
                    runCatching { client.close() }
                }
            }
        }
    }

    fun approveIncomingPairing() {
        receiverApprovalDeferred?.complete(true)
    }

    fun rejectIncomingPairing() {
        receiverApprovalDeferred?.complete(false)
    }

    // =========================================================================
    // 2. SENDER: SCAN QR -> PREVIEW -> MUTUAL CONFIRMATION -> CONNECT
    // =========================================================================

    fun onQrScanned(rawQrText: String): Boolean {
        return when (val result = LtShareQrCodec.decodeAndValidate(rawQrText, replayGuard = replayGuard)) {
            is QrValidationResult.Invalid -> {
                _state.update {
                    it.copy(
                        stage = if (result.isExpired) SharePairingStage.EXPIRED else it.stage,
                        errorMessage = result.reason
                    )
                }
                false
            }
            is QrValidationResult.Valid -> {
                val payload = result.payload
                val keyPair = LtShareSecurity.generateEcdhKeyPair()
                val receiverPub = LtShareSecurity.decodePublicKey(payload.pk)
                val derived = LtShareSecurity.deriveSessionKeys(keyPair.private, receiverPub, payload.sid, payload.tok)

                activeKeyPair = keyPair
                activeSessionToken = payload.tok
                activeExpiresAtMs = payload.exp
                activeEncKey = derived.encKey

                val remaining = ((payload.exp - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                _state.update {
                    it.copy(
                        role = ShareRole.SENDER,
                        stage = SharePairingStage.QR_SCANNED_PREVIEW,
                        sessionId = payload.sid,
                        sessionTokenPreview = payload.tok.take(8) + "••••",
                        qrUriContent = rawQrText,
                        scannedPayload = payload,
                        peerDeviceName = payload.name,
                        sasVerificationCode = derived.sasCode,
                        remainingSeconds = remaining,
                        connectionStatusText = "Detected ${payload.name}",
                        errorMessage = null
                    )
                }
                startCountdownTimer(payload.exp, isReceiver = false)
                true
            }
        }
    }

    fun openPairingConfirmationFromPreview() {
        val payload = _state.value.scannedPayload ?: return
        if (System.currentTimeMillis() >= payload.exp) {
            _state.update {
                it.copy(
                    stage = SharePairingStage.EXPIRED,
                    errorMessage = "QR code expired before pairing. Scan a fresh QR."
                )
            }
            return
        }
        _state.update {
            it.copy(
                stage = SharePairingStage.AWAITING_SENDER_CONFIRM,
                connectionStatusText = "Verify 6-digit code & confirm pairing"
            )
        }
    }

    fun confirmSenderPairing() {
        val payload = _state.value.scannedPayload ?: return
        val myPair = activeKeyPair ?: return
        val now = System.currentTimeMillis()
        if (now >= payload.exp) {
            _state.update {
                it.copy(stage = SharePairingStage.EXPIRED, errorMessage = "QR code has expired.")
            }
            return
        }
        if (replayGuard.isRetired(payload.sid)) {
            _state.update {
                it.copy(stage = SharePairingStage.ERROR, errorMessage = "This session was already used or cancelled.")
            }
            return
        }

        _state.update {
            it.copy(
                stage = SharePairingStage.PAIRING_AUTHENTICATING,
                connectionStatusText = "Connecting & waiting for ${payload.name} to accept...",
                errorMessage = null
            )
        }

        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(payload.ip, payload.port), 10_000)
                    socket.soTimeout = 45_000

                    val output = DataOutputStream(socket.getOutputStream())
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                    val nonceHex = LtShareSecurity.randomNonceHex()
                    val authKey = LtShareSecurity.deriveSessionKeys(
                        myPair.private,
                        LtShareSecurity.decodePublicKey(payload.pk),
                        payload.sid,
                        payload.tok
                    ).authKey
                    val proof = LtShareSecurity.computeClientHelloProof(
                        authKey = authKey,
                        sessionId = payload.sid,
                        token = payload.tok,
                        senderName = transport.getLocalDeviceName(),
                        nonceHex = nonceHex
                    )
                    val hello = HandshakeHelloFrame(
                        sessionId = payload.sid,
                        senderDeviceName = transport.getLocalDeviceName(),
                        senderPublicKey = LtShareSecurity.encodePublicKey(myPair.public),
                        nonceHex = nonceHex,
                        hmacProof = proof
                    )
                    transport.writeRawFrame(output, wireJson.encodeToString(HandshakeHelloFrame.serializer(), hello).toByteArray())

                    val ackRaw = transport.readRawFrame(input)
                    val ack = wireJson.decodeFromString(HandshakeAckFrame.serializer(), ackRaw.toString(Charsets.UTF_8))
                    socket to ack
                }
            }

            outcome.onSuccess { (socket, ack) ->
                if (!ack.accepted) {
                    replayGuard.markRetired(payload.sid)
                    runCatching { socket.close() }
                    _state.update {
                        it.copy(
                            stage = SharePairingStage.REJECTED,
                            connectionStatusText = "Pairing declined",
                            errorMessage = ack.reason.ifBlank { "Receiver declined connection." }
                        )
                    }
                } else {
                    replayGuard.markRetired(payload.sid)
                    countdownJob?.cancel()
                    activeSocket = socket
                    txSeq.set(1L)
                    rxSeq.set(1L)
                    _state.update {
                        it.copy(
                            stage = SharePairingStage.CONNECTED,
                            peerDeviceName = ack.receiverDeviceName.ifBlank { payload.name },
                            connectionStatusText = "Connected & encrypted with ${payload.name}",
                            errorMessage = null
                        )
                    }
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        stage = SharePairingStage.ERROR,
                        connectionStatusText = "Connection failed",
                        errorMessage = "Could not reach ${payload.name} (${payload.ip}:${payload.port}). Ensure both devices are on the same Wi-Fi/Hotspot or Wi-Fi Direct group. (${err.localizedMessage})"
                    )
                }
            }
        }
    }

    fun rejectSenderPairing() {
        val sid = _state.value.sessionId
        replayGuard.markRetired(sid)
        cancelSession()
    }

    // =========================================================================
    // 3. FILE SELECTION & ENCRYPTED SHA-256 VERIFIED TRANSFER
    // =========================================================================

    fun addFilesFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val queried = uris.map { transport.queryUriMetadata(it) }
            _state.update { current ->
                val existing = current.selectedFiles.associateBy { it.uriString }
                val merged = (current.selectedFiles + queried.filter { !existing.containsKey(it.uriString) })
                current.copy(selectedFiles = merged)
            }
        }
    }

    fun addLibraryVideos(videos: List<VideoItem>) {
        val mapped = videos.map { v ->
            SelectedShareFile(
                uriString = v.uriString,
                displayName = v.displayName.ifBlank { "${v.title}.${v.containerExtension}" },
                mimeType = v.mimeType,
                sizeBytes = v.sizeBytes
            )
        }
        _state.update { current ->
            val existing = current.selectedFiles.map { it.uriString }.toSet()
            current.copy(selectedFiles = current.selectedFiles + mapped.filter { it.uriString !in existing })
        }
    }

    fun addLibraryAudio(tracks: List<AudioItem>) {
        val mapped = tracks.map { a ->
            val ext = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(a.mimeType)
                ?: a.mimeType.substringAfterLast('/').takeIf { it.isNotBlank() && it != "*" }
                ?: "mp3"
            SelectedShareFile(
                uriString = a.uriString,
                displayName = if (a.title.contains('.')) a.title else "${a.title}.$ext",
                mimeType = a.mimeType,
                sizeBytes = a.sizeBytes
            )
        }
        _state.update { current ->
            val existing = current.selectedFiles.map { it.uriString }.toSet()
            current.copy(selectedFiles = current.selectedFiles + mapped.filter { it.uriString !in existing })
        }
    }

    fun removeSelectedFile(uriString: String) {
        _state.update { current ->
            current.copy(selectedFiles = current.selectedFiles.filterNot { it.uriString == uriString })
        }
    }

    fun buildBluetoothShareFallbackIntent(): Intent? =
        transport.buildBluetoothShareIntent(_state.value.selectedFiles)

    fun startFileTransfer() {
        val files = _state.value.selectedFiles
        val socket = activeSocket
        val encKey = activeEncKey
        if (files.isEmpty() || socket == null || encKey == null || socket.isClosed) {
            _state.update { it.copy(errorMessage = "No active encrypted connection or files selected.") }
            return
        }

        transferCancelled.set(false)
        val initialItems = files.mapIndexed { idx, f ->
            FileTransferProgressItem(
                fileIndex = idx,
                fileName = f.displayName,
                mimeType = f.mimeType,
                totalBytes = f.sizeBytes
            )
        }
        val totalBytesAll = files.sumOf { it.sizeBytes.coerceAtLeast(1L) }

        _state.update {
            it.copy(
                stage = SharePairingStage.TRANSFERRING,
                transferItems = initialItems,
                totalTransferBytes = totalBytesAll,
                transferredBytes = 0L,
                transferSpeedBytesPerSec = 0L,
                estimatedRemainingSeconds = 0L,
                currentFileIndex = 0,
                connectionStatusText = "Sending encrypted files...",
                errorMessage = null
            )
        }

        transferJob?.cancel()
        transferJob = scope.launch(Dispatchers.IO) {
            try {
                val output = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                var totalSentGlobal = 0L
                val transferStartMs = System.currentTimeMillis()

                for ((index, file) in files.withIndex()) {
                    if (transferCancelled.get() || !isActive) break

                    updateTransferItem(index) { it.copy(status = TransferItemStatus.HASHING) }
                    val uri = Uri.parse(file.uriString)
                    val sha256 = transport.computeUriSha256(uri)

                    val meta = EncryptedControlMessage(
                        type = "FILE_META",
                        fileIndex = index,
                        fileName = file.displayName,
                        mimeType = file.mimeType,
                        sizeBytes = file.sizeBytes,
                        sha256Hex = sha256
                    )
                    transport.writeEncryptedFrame(
                        output,
                        encKey,
                        txSeq.getAndIncrement(),
                        wireJson.encodeToString(EncryptedControlMessage.serializer(), meta).toByteArray()
                    )

                    updateTransferItem(index) {
                        it.copy(sha256Hex = sha256, status = TransferItemStatus.TRANSFERRING)
                    }

                    val buf = ByteArray(LtShareTransportEngine.CHUNK_SIZE_BYTES)
                    var fileSent = 0L
                    var lastUiUpdateMs = System.currentTimeMillis()

                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bis = BufferedInputStream(stream, LtShareTransportEngine.CHUNK_SIZE_BYTES)
                        while (!transferCancelled.get() && isActive) {
                            val read = bis.read(buf)
                            if (read <= 0) break
                            val slice = if (read == buf.size) buf else buf.copyOf(read)
                            transport.writeEncryptedFrame(output, encKey, txSeq.getAndIncrement(), slice)
                            fileSent += read
                            totalSentGlobal += read

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdateMs >= 200L || fileSent >= file.sizeBytes) {
                                lastUiUpdateMs = now
                                val elapsedSec = ((now - transferStartMs).coerceAtLeast(1L)) / 1000.0
                                val speed = (totalSentGlobal / elapsedSec).toLong().coerceAtLeast(1L)
                                val remaining = ((totalBytesAll - totalSentGlobal).coerceAtLeast(0L)) / speed
                                updateTransferItem(index) { it.copy(bytesTransferred = fileSent) }
                                _state.update {
                                    it.copy(
                                        currentFileIndex = index,
                                        transferredBytes = totalSentGlobal,
                                        transferSpeedBytesPerSec = speed,
                                        estimatedRemainingSeconds = remaining
                                    )
                                }
                            }
                        }
                    } ?: error("Unable to open ${file.displayName} for reading.")

                    if (transferCancelled.get()) {
                        val cancelMsg = EncryptedControlMessage(type = "CANCEL", message = "Cancelled by sender")
                        runCatching {
                            transport.writeEncryptedFrame(
                                output,
                                encKey,
                                txSeq.getAndIncrement(),
                                wireJson.encodeToString(EncryptedControlMessage.serializer(), cancelMsg).toByteArray()
                            )
                        }
                        break
                    }

                    updateTransferItem(index) {
                        it.copy(bytesTransferred = fileSent, status = TransferItemStatus.VERIFYING_SHA256)
                    }

                    val ackBytes = transport.readEncryptedFrame(input, encKey, rxSeq.getAndIncrement())
                    val ackMsg = wireJson.decodeFromString(EncryptedControlMessage.serializer(), ackBytes.toString(Charsets.UTF_8))
                    if (ackMsg.type == "FILE_VERIFIED" && ackMsg.verified) {
                        updateTransferItem(index) {
                            it.copy(verified = true, status = TransferItemStatus.VERIFIED)
                        }
                        shareHistoryDao.insert(
                            ShareTransferHistoryEntity(
                                sessionId = _state.value.sessionId,
                                peerDeviceName = _state.value.peerDeviceName,
                                direction = "SENT",
                                fileName = file.displayName,
                                mimeType = file.mimeType,
                                sizeBytes = fileSent,
                                sha256Hex = sha256,
                                verified = true,
                                localUriString = file.uriString,
                                transportMode = _state.value.selectedTransport.name,
                                completedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        updateTransferItem(index) { it.copy(status = TransferItemStatus.FAILED) }
                        error("SHA-256 verification failed on receiver for ${file.displayName}")
                    }
                }

                if (!transferCancelled.get()) {
                    val doneMsg = EncryptedControlMessage(type = "TRANSFER_COMPLETE")
                    transport.writeEncryptedFrame(
                        output,
                        encKey,
                        txSeq.getAndIncrement(),
                        wireJson.encodeToString(EncryptedControlMessage.serializer(), doneMsg).toByteArray()
                    )
                    _state.update {
                        it.copy(
                            stage = SharePairingStage.TRANSFER_SUCCESS,
                            transferredBytes = totalBytesAll,
                            estimatedRemainingSeconds = 0L,
                            connectionStatusText = "All files verified with SHA-256",
                            selectedFiles = emptyList()
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            stage = SharePairingStage.CONNECTED,
                            connectionStatusText = "Transfer cancelled",
                            errorMessage = "Transfer was cancelled."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transfer send error", e)
                _state.update {
                    it.copy(
                        stage = SharePairingStage.ERROR,
                        errorMessage = e.localizedMessage ?: "Transfer interrupted."
                    )
                }
            }
        }
    }

    private suspend fun receiveEncryptedTransferLoop(
        socket: Socket,
        input: DataInputStream,
        output: DataOutputStream,
        encKey: ByteArray,
        sessionId: String,
        peerName: String
    ) {
        val receivedList = mutableListOf<FileTransferProgressItem>()
        var totalReceivedGlobal = 0L
        var batchStartMs = System.currentTimeMillis()

        try {
            socket.soTimeout = 0 // wait indefinitely while connected
            while (!socket.isClosed && !transferCancelled.get()) {
                val frame = transport.readEncryptedFrame(input, encKey, rxSeq.getAndIncrement())
                val ctrl = wireJson.decodeFromString(EncryptedControlMessage.serializer(), frame.toString(Charsets.UTF_8))

                when (ctrl.type) {
                    "FILE_META" -> {
                        if (receivedList.isEmpty()) batchStartMs = System.currentTimeMillis()
                        val item = FileTransferProgressItem(
                            fileIndex = ctrl.fileIndex,
                            fileName = ctrl.fileName,
                            mimeType = ctrl.mimeType,
                            totalBytes = ctrl.sizeBytes,
                            sha256Hex = ctrl.sha256Hex,
                            status = TransferItemStatus.TRANSFERRING
                        )
                        receivedList.add(item)
                        val totalBatchBytes = receivedList.sumOf { it.totalBytes.coerceAtLeast(1L) }

                        _state.update {
                            it.copy(
                                stage = SharePairingStage.TRANSFERRING,
                                transferItems = receivedList.toList(),
                                totalTransferBytes = totalBatchBytes,
                                currentFileIndex = receivedList.lastIndex,
                                connectionStatusText = "Receiving ${ctrl.fileName}..."
                            )
                        }

                        val handle = transport.openReceivedFileOutput(ctrl.fileName, ctrl.mimeType)
                        val digest = MessageDigest.getInstance("SHA-256")
                        var remaining = ctrl.sizeBytes
                        var receivedThisFile = 0L
                        var lastUiMs = System.currentTimeMillis()
                        var aborted = false

                        try {
                            while (remaining > 0L) {
                                if (transferCancelled.get()) {
                                    aborted = true
                                    break
                                }
                                val chunk = transport.readEncryptedFrame(input, encKey, rxSeq.getAndIncrement())
                                val maybeCancel = if (chunk.size < 256) {
                                    runCatching {
                                        wireJson.decodeFromString(EncryptedControlMessage.serializer(), chunk.toString(Charsets.UTF_8))
                                    }.getOrNull()
                                } else null
                                if (maybeCancel?.type == "CANCEL") {
                                    aborted = true
                                    break
                                }

                                handle.outputStream.write(chunk)
                                digest.update(chunk)
                                remaining -= chunk.size
                                receivedThisFile += chunk.size
                                totalReceivedGlobal += chunk.size

                                val now = System.currentTimeMillis()
                                if (now - lastUiMs >= 200L || remaining <= 0L) {
                                    lastUiMs = now
                                    val elapsedSec = ((now - batchStartMs).coerceAtLeast(1L)) / 1000.0
                                    val speed = (totalReceivedGlobal / elapsedSec).toLong().coerceAtLeast(1L)
                                    val eta = ((totalBatchBytes - totalReceivedGlobal).coerceAtLeast(0L)) / speed
                                    val idx = receivedList.lastIndex
                                    receivedList[idx] = receivedList[idx].copy(bytesTransferred = receivedThisFile)
                                    _state.update {
                                        it.copy(
                                            transferItems = receivedList.toList(),
                                            transferredBytes = totalReceivedGlobal,
                                            transferSpeedBytesPerSec = speed,
                                            estimatedRemainingSeconds = eta
                                        )
                                    }
                                }
                            }
                            handle.outputStream.flush()
                            handle.outputStream.close()
                        } catch (e: Exception) {
                            handle.abort()
                            throw e
                        }

                        if (aborted) {
                            handle.abort()
                            val idx = receivedList.lastIndex
                            receivedList[idx] = receivedList[idx].copy(status = TransferItemStatus.CANCELLED)
                            _state.update {
                                it.copy(
                                    stage = SharePairingStage.CONNECTED,
                                    transferItems = receivedList.toList(),
                                    connectionStatusText = "Transfer cancelled by peer"
                                )
                            }
                            continue
                        }

                        val actualSha = with(LtShareSecurity) { digest.digest().toHexString() }
                        val matched = actualSha.equals(ctrl.sha256Hex, ignoreCase = true)

                        if (matched) {
                            val savedUri = handle.commit()
                            val idx = receivedList.lastIndex
                            receivedList[idx] = receivedList[idx].copy(
                                bytesTransferred = receivedThisFile,
                                verified = true,
                                savedUriString = savedUri,
                                status = TransferItemStatus.VERIFIED
                            )
                            shareHistoryDao.insert(
                                ShareTransferHistoryEntity(
                                    sessionId = sessionId,
                                    peerDeviceName = peerName,
                                    direction = "RECEIVED",
                                    fileName = ctrl.fileName,
                                    mimeType = ctrl.mimeType,
                                    sizeBytes = receivedThisFile,
                                    sha256Hex = actualSha,
                                    verified = true,
                                    localUriString = savedUri,
                                    transportMode = _state.value.selectedTransport.name,
                                    completedAt = System.currentTimeMillis()
                                )
                            )
                            val ack = EncryptedControlMessage(
                                type = "FILE_VERIFIED",
                                fileIndex = ctrl.fileIndex,
                                sha256Hex = actualSha,
                                verified = true
                            )
                            transport.writeEncryptedFrame(
                                output,
                                encKey,
                                txSeq.getAndIncrement(),
                                wireJson.encodeToString(EncryptedControlMessage.serializer(), ack).toByteArray()
                            )
                        } else {
                            handle.abort()
                            val idx = receivedList.lastIndex
                            receivedList[idx] = receivedList[idx].copy(verified = false, status = TransferItemStatus.FAILED)
                            val ack = EncryptedControlMessage(
                                type = "FILE_VERIFIED",
                                fileIndex = ctrl.fileIndex,
                                sha256Hex = actualSha,
                                verified = false,
                                message = "SHA-256 mismatch"
                            )
                            transport.writeEncryptedFrame(
                                output,
                                encKey,
                                txSeq.getAndIncrement(),
                                wireJson.encodeToString(EncryptedControlMessage.serializer(), ack).toByteArray()
                            )
                        }
                        _state.update { it.copy(transferItems = receivedList.toList()) }
                    }
                    "TRANSFER_COMPLETE" -> {
                        _state.update {
                            it.copy(
                                stage = SharePairingStage.TRANSFER_SUCCESS,
                                estimatedRemainingSeconds = 0L,
                                connectionStatusText = "Received & SHA-256 verified ${receivedList.size} file(s)"
                            )
                        }
                        receivedList.clear()
                        totalReceivedGlobal = 0L
                    }
                    "CANCEL" -> {
                        _state.update {
                            it.copy(
                                stage = SharePairingStage.CONNECTED,
                                connectionStatusText = "Transfer cancelled"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (_state.value.stage != SharePairingStage.IDLE && _state.value.stage != SharePairingStage.TRANSFER_SUCCESS) {
                Log.w(TAG, "Receive loop ended: ${e.message}")
            }
        }
    }

    fun cancelTransfer() {
        transferCancelled.set(true)
        transferJob?.cancel()
        _state.update {
            it.copy(
                stage = SharePairingStage.CONNECTED,
                connectionStatusText = "Transfer cancelled",
                errorMessage = null
            )
        }
    }

    fun finishSuccessToConnected() {
        _state.update {
            it.copy(
                stage = SharePairingStage.CONNECTED,
                transferItems = emptyList(),
                transferredBytes = 0L,
                totalTransferBytes = 0L,
                transferSpeedBytesPerSec = 0L,
                estimatedRemainingSeconds = 0L
            )
        }
    }

    fun resetToHub() {
        cancelSession()
    }

    private fun startCountdownTimer(expiresAtMs: Long, isReceiver: Boolean) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val rem = ((expiresAtMs - System.currentTimeMillis()) / 1000L).toInt()
                if (rem <= 0) {
                    val stageNow = _state.value.stage
                    val stillWaiting = stageNow == SharePairingStage.WAITING_FOR_SCAN ||
                        stageNow == SharePairingStage.QR_SCANNED_PREVIEW ||
                        stageNow == SharePairingStage.AWAITING_SENDER_CONFIRM ||
                        stageNow == SharePairingStage.AWAITING_RECEIVER_CONFIRM
                    if (stillWaiting) {
                        replayGuard.markRetired(_state.value.sessionId)
                        if (isReceiver) {
                            runCatching { serverSocket?.close() }
                        }
                        _state.update {
                            it.copy(
                                remainingSeconds = 0,
                                stage = SharePairingStage.EXPIRED,
                                connectionStatusText = "QR session expired",
                                errorMessage = "QR code expired. Generate a new QR code to pair."
                            )
                        }
                    }
                    break
                }
                _state.update { it.copy(remainingSeconds = rem) }
                delay(1000L)
            }
        }
    }

    private fun updateTransferItem(index: Int, transform: (FileTransferProgressItem) -> FileTransferProgressItem) {
        _state.update { current ->
            val updated = current.transferItems.mapIndexed { i, item ->
                if (i == index) transform(item) else item
            }
            current.copy(transferItems = updated)
        }
    }

    private fun cleanupTransportOnly(retireCurrentSession: Boolean) {
        if (retireCurrentSession) {
            replayGuard.markRetired(_state.value.sessionId)
        }
        receiverApprovalDeferred?.cancel()
        receiverApprovalDeferred = null
        countdownJob?.cancel()
        acceptJob?.cancel()
        transferJob?.cancel()
        runCatching { serverSocket?.close() }
        runCatching { activeSocket?.close() }
        serverSocket = null
        activeSocket = null
        activeEncKey = null
        activeKeyPair = null
        transport.unregisterNsdService()
        transport.stopNsdDiscovery()
        transport.stopWifiDirectReceiver()
        transport.removeWifiDirectGroup()
        transport.stopNearby()
    }

    fun release() {
        cleanupTransportOnly(retireCurrentSession = true)
    }
}
