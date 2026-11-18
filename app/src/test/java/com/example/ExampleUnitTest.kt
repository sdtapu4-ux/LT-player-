package com.example

import com.example.data.scanner.CodecInspector
import com.example.data.scanner.SubtitleParser
import com.example.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun subtitleParser_parsesSrtVttAndAssWithDelay() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello LT PLAYER

            2
            00:00:04,000 --> 00:00:06,000
            Second line
        """.trimIndent()
        val srtCues = SubtitleParser.parseSrt(srt)
        assertEquals(2, srtCues.size)
        assertEquals("Hello LT PLAYER", srtCues[0].text)
        assertNotNull(SubtitleParser.findCueAt(srtCues, 2000L, 0L))
        assertNull(SubtitleParser.findCueAt(srtCues, 2000L, 1500L))

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
    fun codecInspector_validatesContainers() {
        val (mp4Ok, mp4Note) = CodecInspector.quickContainerCheck("mp4", "video/mp4")
        assertTrue(mp4Ok)
        assertNull(mp4Note)

        val (wmvOk, wmvNote) = CodecInspector.quickContainerCheck("wmv", "video/x-ms-wmv")
        assertTrue(wmvOk)
        assertNotNull(wmvNote)

        val (badOk, badNote) = CodecInspector.quickContainerCheck("xyz", "application/octet-stream")
        assertFalse(badOk)
        assertNotNull(badNote)
    }
}
