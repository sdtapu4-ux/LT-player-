package com.example.share

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.json.Json

sealed interface QrValidationResult {
    data class Valid(val payload: QrSessionPayload) : QrValidationResult
    data class Invalid(val reason: String, val isExpired: Boolean = false) : QrValidationResult
}

object LtShareQrCodec {
    private const val SCHEME = "ltshare"
    private const val HOST = "v1"
    private const val PARAM_PAYLOAD = "p"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: QrSessionPayload): String {
        val rawJson = json.encodeToString(QrSessionPayload.serializer(), payload)
        val encoded = LtShareSecurity.encodeBase64Url(rawJson.toByteArray(Charsets.UTF_8))
        return "$SCHEME://$HOST?$PARAM_PAYLOAD=$encoded"
    }

    fun decodeAndValidate(
        rawQrText: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        replayGuard: LtShareReplayGuard? = null
    ): QrValidationResult {
        val trimmed = rawQrText.trim()
        if (!trimmed.startsWith("$SCHEME://$HOST")) {
            return QrValidationResult.Invalid("Not a valid LT SHARE QR code.")
        }

        val encodedPayload = runCatching {
            val uri = Uri.parse(trimmed)
            uri.getQueryParameter(PARAM_PAYLOAD)
        }.getOrNull() ?: extractParamFallback(trimmed)

        if (encodedPayload.isNullOrBlank()) {
            return QrValidationResult.Invalid("QR payload is missing connection metadata.")
        }

        val payload = runCatching {
            val jsonBytes = LtShareSecurity.decodeBase64Url(encodedPayload)
            json.decodeFromString(QrSessionPayload.serializer(), jsonBytes.toString(Charsets.UTF_8))
        }.getOrElse {
            return QrValidationResult.Invalid("Corrupted or tampered LT SHARE QR payload.")
        }

        if (payload.v != 1) {
            return QrValidationResult.Invalid("Unsupported LT SHARE protocol version v${payload.v}.")
        }
        if (payload.sid.isBlank() || payload.tok.length < 16 || payload.name.isBlank()) {
            return QrValidationResult.Invalid("Incomplete session credentials in QR code.")
        }
        if (payload.exp <= nowEpochMs) {
            return QrValidationResult.Invalid(
                reason = "This QR code has expired. Ask the receiver to tap Regenerate QR.",
                isExpired = true
            )
        }
        if (replayGuard?.isRetired(payload.sid) == true) {
            return QrValidationResult.Invalid(
                reason = "Session already used or cancelled (replay blocked)."
            )
        }
        val mode = runCatching { ShareTransportMode.valueOf(payload.mode) }.getOrNull()
            ?: return QrValidationResult.Invalid("Unsupported transport mode in QR.")

        if (mode == ShareTransportMode.LOCAL_WIFI && (payload.ip.isBlank() || payload.port !in 1024..65535)) {
            return QrValidationResult.Invalid("Invalid host IP or port for Local Wi-Fi transfer.")
        }

        val validKey = runCatching { LtShareSecurity.decodePublicKey(payload.pk) }.isSuccess
        if (!validKey) {
            return QrValidationResult.Invalid("Invalid ephemeral ECDH public key in QR.")
        }

        return QrValidationResult.Valid(payload)
    }

    private fun extractParamFallback(raw: String): String? {
        val idx = raw.indexOf("$PARAM_PAYLOAD=")
        if (idx == -1) return null
        return raw.substring(idx + PARAM_PAYLOAD.length + 1).substringBefore('&')
    }

    fun generateQrBitmap(content: String, sizePx: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }
}
