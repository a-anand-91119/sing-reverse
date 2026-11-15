package dev.notyouraverage.singreverse

import org.junit.Assert.assertArrayEquals
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
}
