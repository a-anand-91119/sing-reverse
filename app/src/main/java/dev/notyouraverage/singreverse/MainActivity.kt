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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { GameScreen() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFB8C5FF), onPrimary = Color(0xFF142978),
        primaryContainer = Color(0xFF273C78), onPrimaryContainer = Color(0xFFDEE5FF),
        background = Color(0xFF101727), surface = Color(0xFF101727),
        onBackground = Color(0xFFE6EBFC), onSurface = Color(0xFFE6EBFC),
        onSurfaceVariant = Color(0xFFBAC4DF), outline = Color(0xFF8591AE),
    ) else lightColorScheme(
        primary = Color(0xFF284DDB), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1A348B),
        background = Color(0xFFF4F6FF), surface = Color(0xFFF4F6FF),
        onBackground = Color(0xFF17264B), onSurface = Color(0xFF17264B),
        onSurfaceVariant = Color(0xFF526080), outline = Color(0xFF727F9E),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class Action { Record, Play, Reverse }

@SuppressLint("MissingPermission") // Recording starts only after the microphone permission check.
@Composable
fun GameScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf(ByteArray(0), ByteArray(0)) }
    var step by remember { mutableIntStateOf(0) }
    var challengeHeard by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Pair<Int, Action>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var waitingForPermission by remember { mutableStateOf<Int?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val busy = active != null || waitingForPermission != null

    fun start(slot: Int, action: Action) {
        active = slot to action
        job = scope.launch {
            try {
                when (action) {
                    Action.Record -> {
                        val out = ByteArrayOutputStream()
                        try { Audio.record(out) } finally {
                            if (out.size() > 0) {
                                clips[slot] = out.toByteArray()
                                if (slot == 0) {
                                    clips[1] = ByteArray(0)
                                    challengeHeard = false
                                }
                            }
                        }
                        if (out.size() == 0) snackbar.showSnackbar(context.getString(R.string.record_failed))
                    }
                    Action.Play -> Audio.play(clips[slot])
                    Action.Reverse -> {
                        Audio.play(reversePcm16(clips[slot]))
                        if (step == 1 && slot == 0) challengeHeard = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(context.getString(R.string.audio_failed))
            } finally {
                active = null
            }
        }
    }

    val permission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val slot = waitingForPermission
        waitingForPermission = null
        if (granted && slot != null) start(slot, Action.Record)
        else if (!granted) scope.launch { snackbar.showSnackbar(context.getString(R.string.mic_needed)) }
    }

    fun onClick(slot: Int, action: Action) {
        if (active == slot to action) {
            job?.cancel()
            return
        }
        if (busy) return
        if (action == Action.Record && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            waitingForPermission = slot
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else start(slot, action)
    }

    BackHandler(enabled = step > 0 || active != null) {
        if (active != null) job?.cancel() else step--
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    if (step > 0) TextButton(onClick = { step-- }, enabled = !busy) { Text(stringResource(R.string.back)) }
                }
                Spacer(Modifier.height(24.dp))
                TurnProgress(step)
                Spacer(Modifier.height(40.dp))
                val slot = if (step == 0) 0 else 1
                val hasClip = clips[slot].isNotEmpty()
                val recording = active?.second == Action.Record
                val title = when {
                    recording -> R.string.recording_title
                    step == 2 -> R.string.reveal_title
                    step == 1 && hasClip -> R.string.ready_title
                    step == 1 -> R.string.copy_title
                    hasClip -> R.string.flip_title
                    else -> R.string.sing_title
                }
                val hint = when {
                    recording -> R.string.recording_hint
                    step == 2 -> R.string.reveal_hint
                    step == 1 && hasClip -> R.string.ready_hint
                    step == 1 && challengeHeard -> R.string.copy_record_hint
                    step == 1 -> R.string.copy_hint
                    hasClip -> R.string.flip_hint
                    else -> R.string.sing_hint
                }
                Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Spacer(Modifier.height(12.dp))
                Text(stringResource(hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))

                if (step < 2) {
                    val revealAction = step == 1 && hasClip && !recording
                    val listenFirst = step == 1 && !hasClip && !challengeHeard
                    val recordAction = recording || (!hasClip && !listenFirst)
                    val action = if (recordAction) Action.Record else Action.Reverse
                    val targetSlot = if (step == 1 && !recordAction) 0 else slot
                    val playing = active == targetSlot to Action.Reverse
                    val label = when {
                        recording -> R.string.stop_recording
                        recordAction -> R.string.record
                        revealAction -> R.string.reveal
                        playing -> R.string.stop_listening
                        else -> R.string.listen_backwards
                    }
                    val status = when {
                        recording -> stringResource(R.string.recording_status)
                        playing -> stringResource(R.string.playing_status)
                        hasClip -> stringResource(R.string.clip_ready, clips[slot].size / 2 / 44_100f)
                        listenFirst -> stringResource(R.string.tap_to_listen)
                        else -> stringResource(R.string.tap_to_start)
                    }
                    Button(
                        onClick = {
                            if (revealAction) {
                                step = 2
                                onClick(1, Action.Reverse)
                            } else onClick(targetSlot, action)
                        },
                        enabled = !busy || (!revealAction && active == targetSlot to action),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).semantics { stateDescription = status },
                        shape = RoundedCornerShape(32.dp),
                        colors = if (recording) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError,
                        ) else ButtonDefaults.buttonColors(),
                        contentPadding = PaddingValues(24.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(if (recording || playing) R.drawable.ic_stop else if (recordAction) R.drawable.ic_mic else R.drawable.ic_reverse),
                                contentDescription = null, modifier = Modifier.size(48.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(status, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (hasClip && !recording) {
                        if (step == 0) {
                            GameButton(R.string.pass_phone, enabled = !busy) { step++ }
                            Spacer(Modifier.height(8.dp))
                        } else {
                            GameButton(if (active == 0 to Action.Reverse) R.string.stop_listening else R.string.hear_again,
                                enabled = !busy || active == 0 to Action.Reverse) { onClick(0, Action.Reverse) }
                        }
                        TextButton(onClick = { onClick(slot, Action.Record) }, enabled = !busy) { Text(stringResource(R.string.record_again)) }
                        TextButton(onClick = { onClick(slot, Action.Play) }, enabled = !busy || active == slot to Action.Play) {
                            Text(stringResource(if (active == slot to Action.Play) R.string.stop_listening else R.string.listen_recording))
                        }
                    } else if (step == 1 && challengeHeard && !recording) {
                        GameButton(if (active == 0 to Action.Reverse) R.string.stop_listening else R.string.hear_again,
                            enabled = !busy || active == 0 to Action.Reverse) { onClick(0, Action.Reverse) }
                    }
                } else {
                    GameButton(if (active == 1 to Action.Reverse) R.string.stop_listening else R.string.play_reveal,
                        enabled = !busy || active == 1 to Action.Reverse, primary = true) { onClick(1, Action.Reverse) }
                    Spacer(Modifier.height(16.dp))
                    GameButton(if (active == 0 to Action.Play) R.string.stop_listening else R.string.play_original,
                        enabled = !busy || active == 0 to Action.Play) { onClick(0, Action.Play) }
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { step = 1; onClick(1, Action.Record) }, enabled = !busy) { Text(stringResource(R.string.try_again)) }
                    TextButton(onClick = { confirmReset = true }, enabled = !busy) { Text(stringResource(R.string.new_round)) }
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(if (step == 0) R.string.player_one else if (step == 1) R.string.player_two else R.string.both_players),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.new_round_question)) },
        text = { Text(stringResource(R.string.reset_hint)) },
        confirmButton = { TextButton(onClick = {
            clips[0] = ByteArray(0)
            clips[1] = ByteArray(0)
            step = 0
            challengeHeard = false
            confirmReset = false
        }) { Text(stringResource(R.string.new_round)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.keep_playing)) } },
    )
}

@Composable
private fun TurnProgress(step: Int) {
    val names = listOf(R.string.sing_step, R.string.copy_step, R.string.reveal_step)
    Row(Modifier.fillMaxWidth().semantics { stateDescription = "${step + 1} / 3" }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        names.forEachIndexed { index, name ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(
                    if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(2.dp),
                ))
                Text(stringResource(name), style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GameButton(label: Int, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (primary) Button(onClick, modifier, enabled, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(20.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    } else FilledTonalButton(onClick, modifier, enabled, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(20.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
