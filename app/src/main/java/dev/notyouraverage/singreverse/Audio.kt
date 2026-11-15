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
    const val BYTES_PER_SECOND = RATE * 2
    const val MAX_BYTES = BYTES_PER_SECOND * 10
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(out: ByteArrayOutputStream, onLevel: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val size = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING) * 2
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING, size)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@withContext
        }
        val buf = ByteArray(size)
        try {
            rec.startRecording()
            while (isActive && out.size() < MAX_BYTES) {
                val n = rec.read(buf, 0, minOf(buf.size, MAX_BYTES - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
                onLevel(waveform(buf.copyOf(n), 1)[0])
            }
        } finally {
            rec.stop()
            rec.release()
        }
    }

    /** Plays [pcm]; [onStart] fires once when sound actually starts coming out, so the UI can run its own smooth playhead. */
    suspend fun play(pcm: ByteArray, onStart: () -> Unit = {}) = withContext(Dispatchers.IO) {
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
            var started = false
            var off = 0
            while (isActive && off < pcm.size) {
                val n = track.write(pcm, off, minOf(size, pcm.size - off))
                if (n <= 0) break
                off += n
                if (!started && track.playbackHeadPosition > 0) { started = true; onStart() }
            }
            // Let the buffered tail finish before releasing, otherwise the last syllable gets cut off.
            while (isActive && track.playbackHeadPosition < off / 2) {
                if (!started && track.playbackHeadPosition > 0) { started = true; onStart() }
                delay(20)
            }
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

/** Peak level (0..1) of each of [bars] equal slices of 16-bit little-endian PCM. */
fun waveform(pcm: ByteArray, bars: Int): List<Float> {
    val samples = pcm.size / 2
    return List(bars) { bar ->
        var peak = 0
        for (i in samples * bar / bars until samples * (bar + 1) / bars) {
            val v = (pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)
            peak = maxOf(peak, kotlin.math.abs(v))
        }
        peak / 32768f
    }
}
