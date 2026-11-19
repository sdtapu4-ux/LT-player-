package com.example.share

import android.util.Base64
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Collections
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DerivedSessionKeys(
    val authKey: ByteArray,
    val encKey: ByteArray,
    val sasCode: String
)

object LtShareSecurity {
    private val secureRandom = SecureRandom()

    fun generateSessionId(): String = UUID.randomUUID().toString()

    fun generateEphemeralToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return encodeBase64Url(bytes)
    }

    fun generateEcdhKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String =
        encodeBase64Url(publicKey.encoded)

    fun decodePublicKey(encoded: String): PublicKey {
        val bytes = decodeBase64Url(encoded)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(bytes))
    }

    fun deriveSessionKeys(
        privateKey: PrivateKey,
        peerPublicKey: PublicKey,
        sessionId: String,
        token: String
    ): DerivedSessionKeys {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()

        val salt = MessageDigest.getInstance("SHA-256")
            .digest("$sessionId|$token".toByteArray(Charsets.UTF_8))
        val prk = hmacSha256(salt, sharedSecret)

        val authKey = hkdfExpand(prk, "LTSHARE-AUTH-V1".toByteArray(Charsets.UTF_8), 32)
        val encKey = hkdfExpand(prk, "LTSHARE-ENC-V1".toByteArray(Charsets.UTF_8), 32)
        val sasBytes = hkdfExpand(prk, "LTSHARE-SAS-V1".toByteArray(Charsets.UTF_8), 4)

        val rawInt = ByteBuffer.wrap(sasBytes).int and 0x7FFFFFFF
        val sasCode = "%06d".format(rawInt % 1_000_000)

        return DerivedSessionKeys(authKey = authKey, encKey = encKey, sasCode = sasCode)
    }

    fun computeClientHelloProof(
        authKey: ByteArray,
        sessionId: String,
        token: String,
        senderName: String,
        nonceHex: String
    ): String {
        val msg = "CLIENT_HELLO|$sessionId|$token|$senderName|$nonceHex".toByteArray(Charsets.UTF_8)
        return encodeBase64Url(hmacSha256(authKey, msg))
    }

    fun verifyClientHelloProof(
        authKey: ByteArray,
        sessionId: String,
        token: String,
        senderName: String,
        nonceHex: String,
        providedProof: String
    ): Boolean {
        val expected = decodeBase64Url(computeClientHelloProof(authKey, sessionId, token, senderName, nonceHex))
        val actual = runCatching { decodeBase64Url(providedProof) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    fun encryptFrame(encKey: ByteArray, sequenceNumber: Long, plaintext: ByteArray): ByteArray {
        val iv = buildNonce(sequenceNumber)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(128, iv))
        val aad = ByteBuffer.allocate(8).putLong(sequenceNumber).array()
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decryptFrame(encKey: ByteArray, sequenceNumber: Long, ciphertext: ByteArray): ByteArray {
        val iv = buildNonce(sequenceNumber)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(128, iv))
        val aad = ByteBuffer.allocate(8).putLong(sequenceNumber).array()
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun computeSha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
        }
        return digest.digest().toHexString()
    }

    fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    fun randomNonceHex(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return bytes.toHexString()
    }

    private fun buildNonce(sequenceNumber: Long): ByteArray {
        val bb = ByteBuffer.allocate(12)
        bb.putInt(0x4C545331) // "LTS1"
        bb.putLong(sequenceNumber)
        return bb.array()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, out, offset, copyLen)
            offset += copyLen
            counter++
        }
        return out
    }

    fun encodeBase64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decodeBase64Url(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

/**
 * Thread-safe replay guard that prevents reuse of expired, cancelled, or previously paired sessions.
 */
class LtShareReplayGuard {
    private val retiredSessions = Collections.synchronizedSet(LinkedHashSet<String>())

    fun isRetired(sessionId: String): Boolean = synchronized(retiredSessions) {
        retiredSessions.contains(sessionId)
    }

    fun markRetired(sessionId: String) {
        if (sessionId.isBlank()) return
        synchronized(retiredSessions) {
            retiredSessions.add(sessionId)
            if (retiredSessions.size > 500) {
                val it = retiredSessions.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }
}
