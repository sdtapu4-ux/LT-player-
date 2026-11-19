package com.example

import com.example.data.model.ActivePlaybackEngine
import com.example.data.model.DecoderPreference
import com.example.data.model.PlaybackEnginePreference
import com.example.data.scanner.CodecInspector
import com.example.data.scanner.SubtitleParser
import com.example.player.EngineFallbackDecision
import com.example.player.PlaybackEnginePolicy
import com.example.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ExampleUnitTest {

    @Test
    fun formatUtils_formatsDurationAndFileSize() {
        assertEquals("00:00", FormatUtils.formatDuration(0L))
        assertEquals("01:05", FormatUtils.formatDuration(65_000L))
        assertEquals("1:01:05", FormatUtils.formatDuration(3_665_000L))
        assertEquals("0 B", FormatUtils.formatFileSize(0L))
        assertEquals("1.0 MB", FormatUtils.formatFileSize(1024L * 1024L))
    }

    @Test
    fun subtitleParser_parsesSrtVttAssBengaliAndUtf16BomWithDelay() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello LT PLAYER

            2
            00:00:04,000 --> 00:00:06,000
            আমার সোনার বাংলা
        """.trimIndent()
        val srtCues = SubtitleParser.parseSrt(srt)
        assertEquals(2, srtCues.size)
        assertEquals("Hello LT PLAYER", srtCues[0].text)
        assertEquals("আমার সোনার বাংলা", srtCues[1].text)
        assertNotNull(SubtitleParser.findCueAt(srtCues, 2000L, 0L))
        assertNull(SubtitleParser.findCueAt(srtCues, 2000L, 1500L))

        val utf16Bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "1\n00:00:01,000 --> 00:00:03,000\nবাংলা সাবটাইটেল\n".toByteArray(Charsets.UTF_16LE)
        val decodedUtf16 = SubtitleParser.decodeBytes(utf16Bytes, "ISO-8859-1")
        val utf16Cues = SubtitleParser.parseAuto(decodedUtf16, "movie.srt")
        assertEquals(1, utf16Cues.size)
        assertEquals("বাংলা সাবটাইটেল", utf16Cues[0].text)

        val vtt = """
            WEBVTT

            00:00:02.000 --> 00:00:05.000
            WebVTT cue
        """.trimIndent()
        val vttCues = SubtitleParser.parseVtt(vtt)
        assertEquals(1, vttCues.size)
        assertEquals("WebVTT cue", vttCues[0].text)

        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:04.50,Default,,0,0,0,,{\i1}Styled ASS{\i0}\NLine 2
        """.trimIndent()
        val assCues = SubtitleParser.parseAssSsa(ass)
        assertEquals(1, assCues.size)
        assertEquals("Styled ASS\nLine 2", assCues[0].text)
    }

    @Test
    fun codecInspector_supportsAllRequiredContainersAndDetectsMagicBytes() {
        val required = listOf(
            "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "3gp",
            "mpeg", "mpg", "ts", "mts", "m2ts", "vob", "ogv", "m4v", "asf", "mxf"
        )
        required.forEach { ext ->
            assertTrue("Missing container $ext", ext in CodecInspector.supportedVideoContainers)
        }

        val (mp4Ok, mp4Note) = CodecInspector.quickContainerCheck("mp4", "video/mp4")
        assertTrue(mp4Ok)
        assertNull(mp4Note)

        val (wmvOk, wmvNote) = CodecInspector.quickContainerCheck("wmv", "video/x-ms-wmv")
        assertTrue(wmvOk)
        assertNotNull(wmvNote)

        val (badOk, badNote) = CodecInspector.quickContainerCheck("xyz", "application/octet-stream")
        assertFalse(badOk)
        assertNotNull(badNote)

        assertEquals(
            "MP4",
            CodecInspector.detectContainerFromHeaderBytes(
                byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte())
            )
        )
        assertEquals(
            "MOV",
            CodecInspector.detectContainerFromHeaderBytes(
                byteArrayOf(0, 0, 0, 0x14, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'q'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), ' '.code.toByte())
            )
        )
        assertEquals(
            "MKV",
            CodecInspector.detectContainerFromHeaderBytes(
                byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + "matroska".toByteArray()
            )
        )
        assertEquals(
            "WEBM",
            CodecInspector.detectContainerFromHeaderBytes(
                byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + "webm".toByteArray()
            )
        )
        assertEquals(
            "AVI",
            CodecInspector.detectContainerFromHeaderBytes("RIFF1234AVI ".toByteArray())
        )
        assertEquals(
            "FLV",
            CodecInspector.detectContainerFromHeaderBytes(byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'V'.code.toByte(), 1))
        )
        assertEquals(
            "ASF/WMV",
            CodecInspector.detectContainerFromHeaderBytes(
                byteArrayOf(0x30, 0x26, 0xB2.toByte(), 0x75, 0x8E.toByte(), 0x66, 0xCF.toByte(), 0x11)
            )
        )
        assertEquals(
            "OGV",
            CodecInspector.detectContainerFromHeaderBytes("OggS\u0000".toByteArray())
        )
        assertEquals(
            "MPEG/VOB",
            CodecInspector.detectContainerFromHeaderBytes(byteArrayOf(0x00, 0x00, 0x01, 0xBA.toByte()))
        )
        assertEquals(
            "MXF",
            CodecInspector.detectContainerFromHeaderBytes(byteArrayOf(0x06, 0x0E, 0x2B, 0x34))
        )
        val tsPacket = ByteArray(192).apply {
            this[0] = 0x47
            this[188] = 0x47
        }
        assertEquals("TS", CodecInspector.detectContainerFromHeaderBytes(tsPacket))
        val m2tsPacket = ByteBuffer.allocate(200).array().apply {
            this[4] = 0x47
            this[196] = 0x47
        }
        assertEquals("M2TS", CodecInspector.detectContainerFromHeaderBytes(m2tsPacket))
    }

    @Test
    fun playbackEnginePolicy_switchesMedia3ToVlcOnceAndPreventsInfiniteLoops() {
        assertEquals(ActivePlaybackEngine.MEDIA3, PlaybackEnginePolicy.selectInitialEngine(PlaybackEnginePreference.AUTO))
        assertEquals(ActivePlaybackEngine.MEDIA3, PlaybackEnginePolicy.selectInitialEngine(PlaybackEnginePreference.MEDIA3))
        assertEquals(ActivePlaybackEngine.VLC, PlaybackEnginePolicy.selectInitialEngine(PlaybackEnginePreference.VLC))

        // AUTO: Media3 fails -> automatically switch to LibVLC
        val firstFallback = PlaybackEnginePolicy.decideOnFailure(
            preference = PlaybackEnginePreference.AUTO,
            failedEngine = ActivePlaybackEngine.MEDIA3,
            attemptedEngines = setOf(ActivePlaybackEngine.MEDIA3),
            decoderPreference = DecoderPreference.AUTO,
            softwareFallbackAlreadyTriedInEngine = false,
            isDecoderInitFailure = true
        )
        assertTrue(firstFallback is EngineFallbackDecision.SwitchEngine)
        assertEquals(ActivePlaybackEngine.VLC, (firstFallback as EngineFallbackDecision.SwitchEngine).nextEngine)

        // AUTO: LibVLC fails with decoder init issue (HW -> SW inside LibVLC)
        val vlcSwRetry = PlaybackEnginePolicy.decideOnFailure(
            preference = PlaybackEnginePreference.AUTO,
            failedEngine = ActivePlaybackEngine.VLC,
            attemptedEngines = setOf(ActivePlaybackEngine.MEDIA3, ActivePlaybackEngine.VLC),
            decoderPreference = DecoderPreference.AUTO,
            softwareFallbackAlreadyTriedInEngine = false,
            isDecoderInitFailure = true
        )
        assertTrue(vlcSwRetry is EngineFallbackDecision.RetryWithSoftwareDecoder)

        // AUTO: LibVLC fails terminally after SW already tried -> never bounce back to Media3
        val terminalAfterBoth = PlaybackEnginePolicy.decideOnFailure(
            preference = PlaybackEnginePreference.AUTO,
            failedEngine = ActivePlaybackEngine.VLC,
            attemptedEngines = setOf(ActivePlaybackEngine.MEDIA3, ActivePlaybackEngine.VLC),
            decoderPreference = DecoderPreference.AUTO,
            softwareFallbackAlreadyTriedInEngine = true,
            isDecoderInitFailure = true
        )
        assertTrue(terminalAfterBoth is EngineFallbackDecision.TerminalFailure)

        // Forced Media3: decoder init error retries software decoder first, then terminates
        val media3SwRetry = PlaybackEnginePolicy.decideOnFailure(
            preference = PlaybackEnginePreference.MEDIA3,
            failedEngine = ActivePlaybackEngine.MEDIA3,
            attemptedEngines = setOf(ActivePlaybackEngine.MEDIA3),
            decoderPreference = DecoderPreference.AUTO,
            softwareFallbackAlreadyTriedInEngine = false,
            isDecoderInitFailure = true
        )
        assertTrue(media3SwRetry is EngineFallbackDecision.RetryWithSoftwareDecoder)
        val media3Terminal = PlaybackEnginePolicy.decideOnFailure(
            preference = PlaybackEnginePreference.MEDIA3,
            failedEngine = ActivePlaybackEngine.MEDIA3,
            attemptedEngines = setOf(ActivePlaybackEngine.MEDIA3),
            decoderPreference = DecoderPreference.AUTO,
            softwareFallbackAlreadyTriedInEngine = true,
            isDecoderInitFailure = true
        )
        assertTrue(media3Terminal is EngineFallbackDecision.TerminalFailure)
    }
}
