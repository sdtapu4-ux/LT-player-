package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.FavoriteMediaEntity
import com.example.data.database.LTPlayerDatabase
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistItemEntity
import com.example.data.database.ShareTransferHistoryEntity
import com.example.data.model.ActivePlaybackEngine
import com.example.data.model.DecoderPreference
import com.example.data.model.MediaFailureCause
import com.example.data.model.PlaybackEnginePreference
import com.example.data.preferences.SettingsDataStore
import com.example.data.scanner.CodecInspector
import com.example.download.DownloadRepository
import com.example.share.LtShareQrCodec
import com.example.share.LtShareReplayGuard
import com.example.share.LtShareSecurity
import com.example.share.QrSessionPayload
import com.example.share.QrValidationResult
import com.example.share.ShareTransportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: LTPlayerDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LTPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun appBranding_matchesLTPlayer() {
        assertEquals("LT PLAYER", context.getString(R.string.app_name))
    }

    @Test
    fun roomDatabase_playlistsFavoritesAndShareHistoryPersistProperly() = runTest {
        val playlistId = db.playlistDao().insertPlaylist(
            PlaylistEntity(name = "Cinema Favorites", createdAt = 100L, updatedAt = 100L)
        )
        db.playlistDao().insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                uriString = "content://media/external/video/media/42",
                title = "Sample 4K Clip",
                durationMs = 120_000L,
                isVideo = true,
                sortOrder = 0,
                addedAt = 100L
            )
        )
        val items = db.playlistDao().observeItems(playlistId).first()
        assertEquals(1, items.size)
        assertEquals("Sample 4K Clip", items[0].title)

        db.favoriteMediaDao().insert(
            FavoriteMediaEntity("content://media/external/video/media/42", "Sample 4K Clip", true, 100L)
        )
        assertTrue(db.favoriteMediaDao().isFavorite("content://media/external/video/media/42"))

        db.shareHistoryDao().insert(
            ShareTransferHistoryEntity(
                sessionId = "sid-123",
                peerDeviceName = "Pixel 9",
                direction = "RECEIVED",
                fileName = "trailer.mp4",
                mimeType = "video/mp4",
                sizeBytes = 1024L,
                sha256Hex = "abc123",
                verified = true,
                localUriString = "content://downloads/1",
                transportMode = ShareTransportMode.LOCAL_WIFI.name,
                completedAt = 200L
            )
        )
        val history = db.shareHistoryDao().observeRecent().first()
        assertEquals(1, history.size)
        assertTrue(history[0].verified)
    }

    @Test
    fun downloadRepository_blocksInsecureAndDrmManifestUrls() {
        val repo = DownloadRepository(context, db.downloadDao())
        assertTrue(repo.validateAuthorizedUrl("https://cdn.example.com/movie.mp4").isSuccess)
        assertFalse(repo.validateAuthorizedUrl("http://cdn.example.com/movie.mp4").isSuccess)
        assertFalse(repo.validateAuthorizedUrl("https://cdn.example.com/stream.m3u8").isSuccess)
        assertFalse(repo.validateAuthorizedUrl("https://cdn.example.com/manifest.mpd").isSuccess)
    }

    @Test
    fun ltShare_qrCodecValidatesRejectsExpiredAndBlocksReplay() {
        val rxKeys = LtShareSecurity.generateEcdhKeyPair()
        val now = 1_700_000_000_000L
        val sessionId = LtShareSecurity.generateSessionId()
        val token = LtShareSecurity.generateEphemeralToken()
        val payload = QrSessionPayload(
            v = 1,
            sid = sessionId,
            tok = token,
            name = "Receiver Phone",
            ip = "192.168.1.42",
            port = 45678,
            mode = ShareTransportMode.LOCAL_WIFI.name,
            pk = LtShareSecurity.encodePublicKey(rxKeys.public),
            exp = now + 120_000L
        )
        val qrText = LtShareQrCodec.encode(payload)
        val qrBmp = LtShareQrCodec.generateQrBitmap(qrText, 240)
        assertEquals(240, qrBmp.width)
        assertEquals(240, qrBmp.height)

        val replayGuard = LtShareReplayGuard()
        val validResult = LtShareQrCodec.decodeAndValidate(qrText, now + 10_000L, replayGuard)
        assertTrue(validResult is QrValidationResult.Valid)
        assertEquals("Receiver Phone", (validResult as QrValidationResult.Valid).payload.name)

        // Expired QR must be rejected
        val expiredResult = LtShareQrCodec.decodeAndValidate(qrText, now + 150_000L, replayGuard)
        assertTrue(expiredResult is QrValidationResult.Invalid)
        assertTrue((expiredResult as QrValidationResult.Invalid).isExpired)

        // Retired session must be blocked against replay
        replayGuard.markRetired(sessionId)
        val replayResult = LtShareQrCodec.decodeAndValidate(qrText, now + 10_000L, replayGuard)
        assertTrue(replayResult is QrValidationResult.Invalid)
    }

    @Test
    fun ltShare_ecdhHandshakeAndAesGcmAndSha256MatchBetweenPeers() {
        val rxPair = LtShareSecurity.generateEcdhKeyPair()
        val txPair = LtShareSecurity.generateEcdhKeyPair()
        val sessionId = LtShareSecurity.generateSessionId()
        val token = LtShareSecurity.generateEphemeralToken()

        val txDerived = LtShareSecurity.deriveSessionKeys(txPair.private, rxPair.public, sessionId, token)
        val rxDerived = LtShareSecurity.deriveSessionKeys(rxPair.private, txPair.public, sessionId, token)

        assertEquals(txDerived.sasCode, rxDerived.sasCode)
        assertArrayEquals(txDerived.encKey, rxDerived.encKey)

        val nonce = LtShareSecurity.randomNonceHex()
        val proof = LtShareSecurity.computeClientHelloProof(txDerived.authKey, sessionId, token, "Sender Pixel", nonce)
        assertTrue(LtShareSecurity.verifyClientHelloProof(rxDerived.authKey, sessionId, token, "Sender Pixel", nonce, proof))
        assertFalse(LtShareSecurity.verifyClientHelloProof(rxDerived.authKey, sessionId, "wrong-token", "Sender Pixel", nonce, proof))

        val originalChunk = "LT SHARE AES-256-GCM encrypted payload check 1234567890".toByteArray()
        val encrypted = LtShareSecurity.encryptFrame(txDerived.encKey, 1L, originalChunk)
        val decrypted = LtShareSecurity.decryptFrame(rxDerived.encKey, 1L, encrypted)
        assertArrayEquals(originalChunk, decrypted)

        val shaA = LtShareSecurity.computeSha256Hex(ByteArrayInputStream(originalChunk))
        val shaB = LtShareSecurity.computeSha256Hex(ByteArrayInputStream(decrypted))
        assertEquals(shaA, shaB)
        assertEquals(64, shaA.length)
    }

    @Test
    fun settingsDataStore_persistsMultiEngineAndAudioCompatibilitySettings() = runTest {
        val store = SettingsDataStore(context)
        store.updateCompatibilitySettings(
            enginePreference = PlaybackEnginePreference.VLC,
            decoderPreference = DecoderPreference.SOFTWARE,
            preferredAudioLanguage = "bn",
            audioDelayMs = 350L,
            audioBoostPercent = 165
        )
        val updated = store.settingsFlow.first()
        assertEquals(PlaybackEnginePreference.VLC, updated.enginePreference)
        assertEquals(DecoderPreference.SOFTWARE, updated.decoderPreference)
        assertEquals("bn", updated.preferredAudioLanguage)
        assertEquals(350L, updated.audioDelayMs)
        assertEquals(165, updated.audioBoostPercent)
    }

    @Test
    fun codecInspector_diagnosesCorruptedMissingAndDecoderFailuresAccurately() {
        val emptyCorruptFile = File(context.cacheDir, "corrupt_sample.mkv").apply {
            writeBytes(byteArrayOf())
        }
        val corruptDiag = CodecInspector.diagnoseFailure(
            context = context,
            uri = Uri.fromFile(emptyCorruptFile),
            containerExtension = "mkv",
            errorDetail = "ERROR_CODE_PARSING_CONTAINER_MALFORMED",
            attemptedEngines = listOf(ActivePlaybackEngine.MEDIA3, ActivePlaybackEngine.VLC),
            decoderPreference = DecoderPreference.AUTO
        )
        assertEquals(MediaFailureCause.CORRUPTED_FILE, corruptDiag.cause)
        emptyCorruptFile.delete()

        val missingFile = File(context.cacheDir, "does_not_exist_12345.avi")
        val missingDiag = CodecInspector.diagnoseFailure(
            context = context,
            uri = Uri.fromFile(missingFile),
            containerExtension = "avi",
            errorDetail = "ENOENT",
            attemptedEngines = listOf(ActivePlaybackEngine.MEDIA3, ActivePlaybackEngine.VLC),
            decoderPreference = DecoderPreference.AUTO
        )
        assertEquals(MediaFailureCause.UNREADABLE_SOURCE, missingDiag.cause)
    }
}
