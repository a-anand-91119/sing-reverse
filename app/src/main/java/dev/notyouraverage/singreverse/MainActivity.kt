package dev.notyouraverage.singreverse

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { GameScreen() } }
    }
}

private val AmberInk = Color(0xFF2A1A00)

/** Player 2's color. */
@Composable
private fun amber() = if (isSystemInDarkTheme()) Color(0xFFFFC56E) else Color(0xFFFFB547)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFB8C5FF), onPrimary = Color(0xFF142978),
        primaryContainer = Color(0xFF273C78), onPrimaryContainer = Color(0xFFDEE5FF),
        secondaryContainer = Color(0xFF273C78), onSecondaryContainer = Color(0xFFDEE5FF),
        background = Color(0xFF101727), surface = Color(0xFF101727),
        onBackground = Color(0xFFE6EBFC), onSurface = Color(0xFFE6EBFC),
        onSurfaceVariant = Color(0xFFBAC4DF), outline = Color(0xFF8591AE), outlineVariant = Color(0xFF2E3A58),
    ) else lightColorScheme(
        primary = Color(0xFF284DDB), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1A348B),
        secondaryContainer = Color(0xFFE0E7FF), onSecondaryContainer = Color(0xFF1A348B),
        background = Color(0xFFF4F6FF), surface = Color(0xFFF4F6FF),
        onBackground = Color(0xFF17264B), onSurface = Color(0xFF17264B),
        onSurfaceVariant = Color(0xFF526080), outline = Color(0xFF727F9E), outlineVariant = Color(0xFFD5DCF2),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class Stage { Sing, Handoff, Copy, Guess, Reveal }

private const val BARS = 30

@SuppressLint("MissingPermission") // Recording starts only after the microphone permission check.
@Composable
fun GameScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clips = remember { mutableStateListOf(ByteArray(0), ByteArray(0)) }
    var stage by remember { mutableStateOf(Stage.Sing) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    // Whatever is running (playback or recording) lives in one job so Back or a new action cancels it.
    var job by remember { mutableStateOf<Job?>(null) }
    var recJob by remember { mutableStateOf<Job?>(null) }
    var recording by remember { mutableStateOf<Int?>(null) }
    var playing by remember { mutableStateOf<String?>(null) } // clip slot + 'f'orward / 'b'ackwards, e.g. "0b"
    var playStart by remember { mutableLongStateOf(0L) } // System.nanoTime() when sound started; 0 = not yet
    var levels by remember { mutableStateOf(emptyList<Float>()) }
    var recordedBytes by remember { mutableIntStateOf(0) }
    var pendingSlot by remember { mutableStateOf<Int?>(null) }

    fun say(text: Int) { scope.launch { snackbar.showSnackbar(context.getString(text)) } }

    fun stop() {
        job?.cancel()
        job = null
        playing = null
        recording = null
    }

    fun run(block: suspend CoroutineScope.() -> Unit) {
        stop()
        val next = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                say(R.string.audio_failed)
            } finally {
                if (job === self) stop()
            }
        }
        job = next
        next.start()
    }

    suspend fun play(key: String) {
        val pcm = clips[key[0].digitToInt()]
        playStart = 0L
        playing = key
        Audio.play(if (key[1] == 'b') reversePcm16(pcm) else pcm) { playStart = System.nanoTime() }
        playing = null
    }

    /** Records into [slot] until Stop (cancels only [recJob]) or the 10-second cap. Returns true if sound was kept. */
    suspend fun record(slot: Int): Boolean = coroutineScope {
        val out = ByteArrayOutputStream()
        levels = emptyList()
        recordedBytes = 0
        recording = slot
        val rec = launch { Audio.record(out) { levels = levels + it; recordedBytes = out.size() } }
        recJob = rec
        try {
            rec.join()
        } finally {
            recJob = null
            recording = null
            // Keep the take only if Stop or the 10 s cap ended it; Back cancels the whole job and discards it.
            if (out.size() > 0 && isActive) {
                clips[slot] = out.toByteArray()
                if (slot == 0) clips[1] = ByteArray(0)
            }
        }
        if (out.size() == 0) say(R.string.record_failed)
        out.size() > 0
    }

    fun recordFlow(slot: Int) = run { if (record(slot) && slot == 0) play("0b") }

    val permission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val slot = pendingSlot
        pendingSlot = null
        if (granted && slot != null) recordFlow(slot) else if (!granted) say(R.string.mic_needed)
    }

    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    fun startRecording(slot: Int) {
        if (hasMic()) return recordFlow(slot)
        stop()
        pendingSlot = slot
        permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    /** Playhead position for [key] while it plays, read at draw time so only the bars redraw. */
    fun head(key: String?): (() -> Float)? {
        if (key == null || playing != key) return null
        val nanos = clips[key[0].digitToInt()].size * 1e9f / Audio.BYTES_PER_SECOND
        return { if (playStart == 0L) 0f else (System.nanoTime() - playStart) / nanos }
    }

    fun toggle(key: String) = if (playing == key) stop() else run { play(key) }

    /** Moves to [next] and plays [key] there, so each screen opens with its sound. */
    fun go(next: Stage, key: String) {
        stage = next
        run { play(key) }
    }

    BackHandler(enabled = stage != Stage.Sing || job != null) {
        stop()
        stage = when (stage) {
            Stage.Sing, Stage.Handoff -> Stage.Sing
            Stage.Copy -> Stage.Copy // Player 2 has the phone; going back would expose Player 1's original.
            Stage.Guess -> Stage.Copy
            Stage.Reveal -> Stage.Guess
        }
    }

    val c = MaterialTheme.colorScheme
    val p2 = amber()
    val wave0 = remember(clips[0]) { waveform(clips[0], BARS) }
    val wave1 = remember(clips[1]) { waveform(clips[1], BARS) }
    val isRecording = recording != null
    fun seconds(pcm: ByteArray) = pcm.size / Audio.BYTES_PER_SECOND.toFloat()

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = if (stage == Stage.Handoff) p2 else c.background) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = 24.dp)) {
                if (stage == Stage.Handoff) {
                    Handoff(p2) { go(Stage.Copy, "0b") }
                } else {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Header(stage, p2)
                        @Composable
                        fun liveCard(title: Int) = SoundCard(stringResource(title), stringResource(R.string.timer, recordedBytes /
                            Audio.BYTES_PER_SECOND.toFloat()), metaColor = c.error, border = c.error) {
                            Bars(levels.takeLast(BARS), if (stage == Stage.Sing) c.primary else p2, null)
                        }

                        when (stage) {
                            Stage.Sing -> {
                                val has = clips[0].isNotEmpty()
                                Title(if (recording == 0) R.string.recording_title else if (has) R.string.sing_done_title else R.string.sing_title)
                                if (!isRecording) Hint(if (has) R.string.sing_done_hint else R.string.sing_hint)
                                when {
                                    recording == 0 -> liveCard(R.string.your_phrase)
                                    !has -> EmptyCard(stringResource(R.string.your_phrase))
                                    else -> SoundCard(
                                        stringResource(R.string.your_phrase),
                                        when (playing) {
                                            "0b" -> stringResource(R.string.playing_backwards)
                                            "0f" -> stringResource(R.string.playing)
                                            else -> stringResource(R.string.seconds, seconds(clips[0]))
                                        },
                                        metaColor = if (playing != null) c.primary else c.onSurfaceVariant,
                                        border = if (playing != null) c.primary else c.outlineVariant,
                                    ) {
                                        Bars(if (playing == "0b") wave0.reversed() else wave0, c.primary, head(playing))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Pill(if (playing == "0f") R.string.stop else R.string.normal, playing == "0f") { toggle("0f") }
                                            Pill(if (playing == "0b") R.string.stop else R.string.backwards, playing == "0b") { toggle("0b") }
                                        }
                                    }
                                }
                            }

                            Stage.Copy -> {
                                val has = clips[1].isNotEmpty()
                                Title(when {
                                    recording == 1 -> R.string.recording_title
                                    has -> R.string.copy_done_title
                                    else -> R.string.copy_record_title
                                })
                                if (!has && !isRecording) Hint(R.string.copy_hint)
                                SoundCard(
                                    stringResource(R.string.sound_to_copy),
                                    if (playing == "0b") stringResource(R.string.playing_backwards) else stringResource(R.string.seconds, seconds(clips[0])),
                                    metaColor = if (playing == "0b") c.primary else c.onSurfaceVariant,
                                    border = if (playing == "0b") c.primary else c.outlineVariant,
                                ) {
                                    Bars(wave0.reversed(), c.primary, head("0b"))
                                    Row { Pill(if (playing == "0b") R.string.stop else R.string.hear_again, playing == "0b", !isRecording) { toggle("0b") } }
                                }
                                when {
                                    recording == 1 -> liveCard(R.string.your_copy)
                                    !has -> EmptyCard(stringResource(R.string.your_copy))
                                    else -> SoundCard(
                                        stringResource(R.string.your_copy),
                                        if (playing == "1f") stringResource(R.string.playing) else stringResource(R.string.seconds, seconds(clips[1])),
                                        border = if (playing == "1f") c.primary else c.outlineVariant,
                                    ) {
                                        Bars(wave1, p2, head("1f"))
                                        Row { Pill(if (playing == "1f") R.string.stop else R.string.play, playing == "1f") { toggle("1f") } }
                                    }
                                }
                            }

                            Stage.Guess -> {
                                Title(R.string.guess_title)
                                SoundCard(
                                    stringResource(R.string.copy_backwards),
                                    if (playing == "1b") stringResource(R.string.playing_backwards) else stringResource(R.string.seconds, seconds(clips[1])),
                                    metaColor = if (playing == "1b") c.primary else c.onSurfaceVariant,
                                    border = if (playing == "1b") c.primary else c.outlineVariant,
                                ) { Bars(wave1.reversed(), p2, head("1b")) }
                                Column(
                                    Modifier.fillMaxWidth().background(p2.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(stringResource(R.string.guess_hint), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.guess_hint_more), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                                }
                                // Player 1's sound stays available, but only backwards: forwards would give the answer away.
                                SoundCard(
                                    stringResource(R.string.sound_to_copy),
                                    if (playing == "0b") stringResource(R.string.playing_backwards) else stringResource(R.string.seconds, seconds(clips[0])),
                                    metaColor = if (playing == "0b") c.primary else c.onSurfaceVariant,
                                    border = if (playing == "0b") c.primary else c.outlineVariant,
                                ) {
                                    Bars(wave0.reversed(), c.primary, head("0b"))
                                    Row { Pill(if (playing == "0b") R.string.stop else R.string.hear_again, playing == "0b") { toggle("0b") } }
                                    Text(stringResource(R.string.original_locked), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                                }
                            }

                            else -> {
                                Title(R.string.reveal_title)
                                SoundCard(
                                    stringResource(R.string.original),
                                    if (playing == "0f") stringResource(R.string.playing) else stringResource(R.string.seconds, seconds(clips[0])),
                                    metaColor = if (playing == "0f") c.primary else c.onSurfaceVariant,
                                    border = if (playing == "0f") c.primary else c.outlineVariant,
                                ) { Bars(wave0, c.primary, head("0f")) }
                                Hint(R.string.reveal_hint)
                                SoundCard(
                                    stringResource(R.string.copy_backwards),
                                    if (playing == "1b") stringResource(R.string.playing_backwards) else stringResource(R.string.seconds, seconds(clips[1])),
                                    border = if (playing == "1b") c.primary else c.outlineVariant,
                                ) {
                                    Bars(wave1.reversed(), p2, head("1b"))
                                    Row { Pill(if (playing == "1b") R.string.stop else R.string.play, playing == "1b") { toggle("1b") } }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = c.outlineVariant)
                    @Composable
                    fun playButton(key: String, container: Color, content: Color) = RoundButton(
                        if (playing == key) R.drawable.ic_stop else R.drawable.ic_play,
                        stringResource(if (playing == key) R.string.stop else R.string.play), container, content,
                    ) { toggle(key) }
                    when (stage) {
                        Stage.Sing, Stage.Copy -> {
                            val slot = if (stage == Stage.Sing) 0 else 1
                            val ready = clips[slot].isNotEmpty() && !isRecording
                            Dock(
                                left = { SideAction(R.drawable.ic_redo, R.string.redo, ready) { stop(); clips[slot] = ByteArray(0) } },
                                center = {
                                    if (isRecording) RoundButton(R.drawable.ic_stop, stringResource(R.string.stop_recording), c.error, c.onError) {
                                        recJob?.cancel()
                                    } else RoundButton(R.drawable.ic_mic, stringResource(R.string.record), c.primary, c.onPrimary) { startRecording(slot) }
                                },
                                right = {
                                    SideAction(
                                        R.drawable.ic_next,
                                        if (!ready) R.string.record_first else if (slot == 0) R.string.pass_phone else R.string.hear_backwards,
                                        ready, primary = true,
                                    ) { if (slot == 0) { stop(); stage = Stage.Handoff } else go(Stage.Guess, "1b") }
                                },
                            )
                        }
                        Stage.Guess -> Dock(
                            left = { SideAction(R.drawable.ic_redo, R.string.copy_again, true) { stop(); clips[1] = ByteArray(0); stage = Stage.Copy } },
                            center = { playButton("1b", p2, AmberInk) },
                            right = { SideAction(R.drawable.ic_check, R.string.lock_in, true, primary = true) { stop(); confirmLock = true } },
                        )
                        else -> Dock(
                            left = {},
                            center = { playButton("0f", c.primary, c.onPrimary) },
                            right = { SideAction(R.drawable.ic_add, R.string.new_round, true, primary = true) { confirmReset = true } },
                        )
                    }
                }
            }
        }
    }
    if (confirmLock) AlertDialog(
        onDismissRequest = { confirmLock = false },
        title = { Text(stringResource(R.string.lock_title)) },
        text = { Text(stringResource(R.string.lock_text)) },
        confirmButton = {
            TextButton(onClick = { confirmLock = false; go(Stage.Reveal, "0f") }) { Text(stringResource(R.string.lock_confirm)) }
        },
        dismissButton = { TextButton(onClick = { confirmLock = false }) { Text(stringResource(R.string.not_yet)) } },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.new_round_question)) },
        text = { Text(stringResource(R.string.reset_hint)) },
        confirmButton = {
            TextButton(onClick = {
                stop()
                clips[0] = ByteArray(0)
                clips[1] = ByteArray(0)
                stage = Stage.Sing
                confirmReset = false
            }) { Text(stringResource(R.string.new_round)) }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.keep_playing)) } },
    )
}

@Composable
private fun Header(stage: Stage, p2: Color) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val player2 = stage == Stage.Copy || stage == Stage.Guess
            if (!player2) Box(Modifier.size(10.dp).background(c.primary, CircleShape))
            if (stage != Stage.Sing) Box(Modifier.size(10.dp).background(p2, CircleShape))
            Text(
                stringResource(when { stage == Stage.Sing -> R.string.player_one; player2 -> R.string.player_two; else -> R.string.both_players }),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            )
        }
        Text(stringResource(R.string.step_of, when (stage) { Stage.Sing -> 1; Stage.Handoff, Stage.Copy -> 2; else -> 3 }),
            style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
    }
}

@Composable
private fun Title(text: Int) = Text(
    stringResource(text), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
)

@Composable
private fun Hint(text: Int) =
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SoundCard(
    title: String, meta: String, metaColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: Color = MaterialTheme.colorScheme.outlineVariant, content: @Composable ColumnScope.() -> Unit,
) {
    val emphasized = border != MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.fillMaxWidth().border(if (emphasized) 2.dp else 1.dp, border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // Width cap so a long note can't squeeze the title at large text sizes.
            Text(meta, style = MaterialTheme.typography.labelLarge, color = metaColor, textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 150.dp))
        }
        content()
    }
}

@Composable
private fun EmptyCard(title: String) {
    val line = MaterialTheme.colorScheme.outline
    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(line, cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.empty), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Hint(R.string.tap_mic)
    }
}

/** Level bars, scaled to the loudest bar. A slot per bar is fixed so a live recording fills left to right. */
@Composable
private fun Bars(values: List<Float>, color: Color, progress: (() -> Float)?, height: Dp = 36.dp) {
    val head = MaterialTheme.colorScheme.error
    // Ticks every display frame while playing; read only inside the Canvas so each frame is a redraw, not a recomposition.
    var frame by remember { mutableLongStateOf(0L) }
    if (progress != null) LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val slot = size.width / BARS
        val peak = (values.maxOrNull() ?: 0f).coerceAtLeast(0.02f)
        values.forEachIndexed { i, v ->
            val h = (v / peak).coerceIn(0.12f, 1f) * size.height
            drawRoundRect(color, Offset(i * slot + slot * 0.15f, (size.height - h) / 2), Size(slot * 0.7f, h), CornerRadius(slot * 0.35f))
        }
        if (progress != null && frame >= 0) drawRect(head, Offset(progress().coerceIn(0f, 1f) * size.width, 0f), Size(2.dp.toPx(), size.height))
    }
}

@Composable
private fun RowScope.Pill(label: Int, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val modifier = Modifier.weight(1f).heightIn(min = 48.dp)
    if (active) Button(onClick, modifier, enabled) { Text(stringResource(label), fontWeight = FontWeight.Bold) }
    else FilledTonalButton(onClick, modifier, enabled) { Text(stringResource(label), fontWeight = FontWeight.Bold) }
}

@Composable
private fun Dock(left: @Composable () -> Unit, center: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { left() }
        center()
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { right() }
    }
}

@Composable
private fun SideAction(icon: Int, label: Int, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when {
        !enabled -> c.onSurface.copy(alpha = 0.08f) to c.onSurfaceVariant
        primary -> c.primary to c.onPrimary
        else -> c.primaryContainer to c.onPrimaryContainer
    }
    Column(
        Modifier.widthIn(min = 72.dp).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(44.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = if (primary && enabled) c.primary else c.onSurfaceVariant)
    }
}

@Composable
private fun RoundButton(
    icon: Int, description: String, container: Color, content: Color, size: Dp = 76.dp, enabled: Boolean = true, onClick: () -> Unit,
) = Surface(
    onClick = onClick, enabled = enabled, shape = CircleShape,
    color = if (enabled) container else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    contentColor = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(size).semantics { contentDescription = description },
) {
    Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(size * 0.42f)) }
}

@Composable
private fun Handoff(p2: Color, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.step_of, 2), style = MaterialTheme.typography.labelLarge, color = AmberInk.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).wrapContentHeight())
        Spacer(Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_redo), contentDescription = null, tint = AmberInk, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.handoff_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
            color = AmberInk, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.handoff_hint), style = MaterialTheme.typography.bodyLarge, color = AmberInk.copy(alpha = 0.8f))
        Spacer(Modifier.weight(1f))
        Button(onStart, Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberInk, contentColor = p2)) {
            Text(stringResource(R.string.handoff_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
