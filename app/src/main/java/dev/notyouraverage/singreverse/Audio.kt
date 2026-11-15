package dev.notyouraverage.singreverse

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Mono 16-bit PCM, kept in memory. Recording and playback both stop when the coroutine is cancelled. */
object Audio {
    private const val RATE = 44_100
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(out: ByteArrayOutputStream) = withContext(Dispatchers.IO) {
        val size = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING) * 2
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING, size)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@withContext
        }
        val buf = ByteArray(size)
        try {
            rec.startRecording()
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        } finally {
            rec.stop()
            rec.release()
        }
    }

    suspend fun play(pcm: ByteArray) = withContext(Dispatchers.IO) {
        val size = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, ENCODING)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(RATE).setEncoding(ENCODING)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(size)
            .build()
        try {
            track.play()
            var off = 0
            while (isActive && off < pcm.size) {
                val n = track.write(pcm, off, minOf(size, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            // Let the buffered tail finish before releasing, otherwise the last syllable gets cut off.
            while (isActive && track.playbackHeadPosition < off / 2) delay(20)
        } finally {
            track.stop()
            track.release()
        }
    }
}

/** Reverses sample order of 16-bit little-endian PCM while keeping each sample's two bytes intact. */
fun reversePcm16(pcm: ByteArray): ByteArray {
    val samples = pcm.size / 2
    val out = ByteArray(samples * 2)
    for (i in 0 until samples) {
        val src = (samples - 1 - i) * 2
        out[i * 2] = pcm[src]
        out[i * 2 + 1] = pcm[src + 1]
    }
    return out
}
