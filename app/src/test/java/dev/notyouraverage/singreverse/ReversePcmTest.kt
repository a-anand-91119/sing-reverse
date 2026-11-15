package dev.notyouraverage.singreverse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReversePcmTest {
    @Test
    fun reversesSamplesNotBytes() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        assertArrayEquals(byteArrayOf(5, 6, 3, 4, 1, 2), reversePcm16(pcm))
    }

    @Test
    fun dropsTrailingHalfSampleAndHandlesEmpty() {
        assertArrayEquals(byteArrayOf(3, 4, 1, 2), reversePcm16(byteArrayOf(1, 2, 3, 4, 9)))
        assertArrayEquals(byteArrayOf(), reversePcm16(byteArrayOf()))
    }

    @Test
    fun doubleReverseIsOriginal() {
        val pcm = ByteArray(1000) { it.toByte() }
        assertArrayEquals(pcm, reversePcm16(reversePcm16(pcm)))
    }

    @Test
    fun waveformPeaksPerBucket() {
        // Samples 0, 16384, -32768, 8192 as 16-bit little-endian.
        val pcm = byteArrayOf(0, 0, 0, 0x40, 0, 0x80.toByte(), 0, 0x20)
        assertEquals(listOf(0.5f, 1f), waveform(pcm, 2))
    }

    @Test
    fun waveformEmptyIsFlat() {
        assertEquals(listOf(0f, 0f, 0f), waveform(byteArrayOf(), 3))
    }
}
