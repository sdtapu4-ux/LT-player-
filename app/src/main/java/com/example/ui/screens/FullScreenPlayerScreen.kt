package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.AppSettings
import com.example.data.model.AspectRatioMode
import com.example.data.model.SubtitlePreferences
import com.example.data.scanner.SubtitleParser
import com.example.player.PlayerController
import com.example.player.PlayerUiState
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayerScreen(
    playerController: PlayerController,
    playerState: PlayerUiState,
    settings: AppSettings,
    onBack: () -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onUpdateSubtitlePrefs: (SubtitlePreferences) -> Unit,
    onToggleBackgroundAudio: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudType by remember { mutableStateOf("SEEK") }

    // Keep screen awake during video playback
    DisposableEffect(settings.keepScreenAwake) {
        if (settings.keepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (!settings.backgroundAudioEnabled) {
                playerController.pause()
            }
        }
    }

    // Auto-hide controls after 3.5s while playing
    LaunchedEffect(controlsVisible, playerState.isPlaying, isLocked) {
        if (controlsVisible && playerState.isPlaying && !isLocked) {
            delay(3500L)
            controlsVisible = false
        }
    }

    // Auto-clear gesture HUD
    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(1100L)
            gestureHudText = null
        }
    }

    BackHandler {
        if (!settings.backgroundAudioEnabled) {
            playerController.pause()
        }
        onBack()
    }

    val resizeMode = when (playerState.aspectRatioMode) {
        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    }

    val activeExternalCue = remember(
        playerState.externalSubtitleCues,
        playerState.currentPositionMs,
        settings.subtitlePrefs.delayMs,
        settings.subtitlePrefs.enabled
    ) {
        if (!settings.subtitlePrefs.enabled) null
        else SubtitleParser.findCueAt(
            playerState.externalSubtitleCues,
            playerState.currentPositionMs,
            settings.subtitlePrefs.delayMs
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val totalHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                view.player = playerController.playerOrNull
                view.resizeMode = resizeMode
                view.subtitleView?.visibility =
                    if (settings.subtitlePrefs.enabled && playerState.externalSubtitleCues.isEmpty()) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale
                )
        )

        // Gesture Capture Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (isLocked) return@detectTapGestures
                            if (offset.x < totalWidthPx * 0.45f) {
                                playerController.seekBackward10s()
                                gestureHudType = "SEEK"
                                gestureHudText = "-10s"
                            } else if (offset.x > totalWidthPx * 0.55f) {
                                playerController.seekForward10s()
                                gestureHudType = "SEEK"
                                gestureHudText = "+10s"
                            } else {
                                playerController.togglePlayPause()
                            }
                        }
                    )
                }
                .then(
                    if (!isLocked) {
                        Modifier
                            .pointerInput(settings.gestureBrightnessEnabled, settings.gestureVolumeEnabled) {
                                var startBrightness = activity?.window?.attributes?.screenBrightness
                                    ?.takeIf { it >= 0f } ?: 0.5f
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                var accumDrag = 0f

                                detectVerticalDragGestures(
                                    onDragStart = {
                                        accumDrag = 0f
                                        startBrightness = activity?.window?.attributes?.screenBrightness
                                            ?.takeIf { it >= 0f } ?: 0.5f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumDrag -= dragAmount
                                        val deltaFraction = accumDrag / (totalHeightPx * 0.65f)
                                        if (change.position.x < totalWidthPx * 0.5f && settings.gestureBrightnessEnabled) {
                                            val target = (startBrightness + deltaFraction).coerceIn(0.05f, 1.0f)
                                            activity?.window?.let { win ->
                                                val lp = win.attributes
                                                lp.screenBrightness = target
                                                win.attributes = lp
                                            }
                                            gestureHudType = "BRIGHTNESS"
                                            gestureHudText = "Brightness ${(target * 100).toInt()}%"
                                        } else if (change.position.x >= totalWidthPx * 0.5f && settings.gestureVolumeEnabled) {
                                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val step = (deltaFraction * maxVol).toInt()
                                            if (step != 0) {
                                                val nextVol = (curVol + step).coerceIn(0, maxVol)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                                accumDrag = 0f
                                                gestureHudType = "VOLUME"
                                                gestureHudText = "Volume ${(nextVol * 100) / maxVol}%"
                                            }
                                        }
                                    }
                                )
                            }
                            .pointerInput(settings.gestureSeekEnabled) {
                                if (!settings.gestureSeekEnabled) return@pointerInput
                                var startPos = 0L
                                var dragTotalPx = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        startPos = playerState.currentPositionMs
                                        dragTotalPx = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragTotalPx += dragAmount
                                        val offsetMs = ((dragTotalPx / totalWidthPx) * 90_000f).toLong()
                                        val target = (startPos + offsetMs).coerceIn(0L, playerState.durationMs.coerceAtLeast(1L))
                                        gestureHudType = "SEEK"
                                        gestureHudText = "${FormatUtils.formatDuration(target)} (${if (offsetMs >= 0) "+" else ""}${offsetMs / 1000}s)"
                                    },
                                    onDragEnd = {
                                        val offsetMs = ((dragTotalPx / totalWidthPx) * 90_000f).toLong()
                                        playerController.seekTo(startPos + offsetMs)
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1f) {
                                        zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                                    }
                                }
                            }
                    } else Modifier
                )
        )

        // External Subtitle Cue Overlay
        if (activeExternalCue != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = settings.subtitlePrefs.verticalBottomPaddingDp.dp
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = settings.subtitlePrefs.backgroundOpacity))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeExternalCue.text,
                    color = Color(settings.subtitlePrefs.textColorHex),
                    fontSize = settings.subtitlePrefs.fontSizeSp.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center Gesture HUD Pill
        if (gestureHudText != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (gestureHudType) {
                            "BRIGHTNESS" -> Icons.Rounded.BrightnessHigh
                            "VOLUME" -> Icons.AutoMirrored.Rounded.VolumeUp
                            else -> Icons.Rounded.Forward10
                        },
                        contentDescription = null,
                        tint = ElectricGreen
                    )
                    Text(
                        text = gestureHudText.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Codec / File Playback Error Card
        if (playerState.playbackError != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = CoralError,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Unsupported Format or Playback Error",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = playerState.playbackError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text("Back to Library")
                        }
                        if (playerState.queue.size > 1) {
                            Button(
                                onClick = { playerController.skipNext() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricGreen,
                                    contentColor = Color(0xFF00210E)
                                )
                            ) {
                                Text("Next Video")
                            }
                        }
                    }
                }
            }
        }

        // Locked Screen Unlock Button
        if (isLocked && controlsVisible) {
            IconButton(
                onClick = { isLocked = false },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .testTag("unlock_player_button")
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = "Unlock Controls", tint = ElectricGreen)
            }
        }

        // Full Controls HUD
        AnimatedVisibility(
            visible = controlsVisible && !isLocked && playerState.playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.80f)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (!settings.backgroundAudioEnabled) playerController.pause()
                            onBack()
                        },
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playerState.currentEntry?.title ?: "LT PLAYER",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playerState.playbackSpeed}x • ${playerState.aspectRatioMode.label}${if (zoomScale > 1.05f) " • ${String.format("%.1fx", zoomScale)}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricGreen
                        )
                    }
                    IconButton(
                        onClick = { onToggleBackgroundAudio(!settings.backgroundAudioEnabled) },
                        modifier = Modifier.testTag("player_bg_audio_button")
                    ) {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = "Background Audio",
                            tint = if (settings.backgroundAudioEnabled) ElectricGreen else Color.White
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(
                            onClick = {
                                runCatching { activity?.enterPictureInPictureMode() }
                            },
                            modifier = Modifier.testTag("player_pip_button")
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = Color.White)
                        }
                    }
                    IconButton(
                        onClick = {
                            isLandscape = !isLandscape
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        },
                        modifier = Modifier.testTag("player_rotate_button")
                    ) {
                        Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate Screen", tint = Color.White)
                    }
                    IconButton(
                        onClick = { isLocked = true },
                        modifier = Modifier.testTag("player_lock_button")
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = "Lock Controls", tint = Color.White)
                    }
                }

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    IconButton(onClick = { playerController.skipPrevious() }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { playerController.seekBackward10s() }) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = ElectricGreen,
                        modifier = Modifier
                            .size(64.dp)
                            .clickable { playerController.togglePlayPause() }
                            .testTag("player_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF00210E),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    IconButton(onClick = { playerController.seekForward10s() }) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { playerController.skipNext() }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // Bottom Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val dur = playerState.durationMs.coerceAtLeast(1L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(playerState.currentPositionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = playerState.currentPositionMs.toFloat().coerceIn(0f, dur.toFloat()),
                            onValueChange = { playerController.seekTo(it.toLong()) },
                            valueRange = 0f..dur.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricGreen,
                                activeTrackColor = ElectricGreen,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = FormatUtils.formatDuration(playerState.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerActionChip(
                            icon = Icons.Rounded.Speed,
                            label = "${playerState.playbackSpeed}x",
                            onClick = { showSpeedSheet = true }
                        )
                        PlayerActionChip(
                            icon = Icons.Rounded.AspectRatio,
                            label = playerState.aspectRatioMode.label,
                            onClick = {
                                zoomScale = 1f
                                playerController.cycleAspectRatio()
                            }
                        )
                        PlayerActionChip(
                            icon = Icons.Rounded.Subtitles,
                            label = "Subtitles",
                            onClick = { showSubtitleSheet = true }
                        )
                        PlayerActionChip(
                            icon = Icons.Rounded.Audiotrack,
                            label = "Audio",
                            onClick = { showAudioTrackSheet = true }
                        )
                        PlayerActionChip(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            label = "Queue",
                            onClick = { showQueueSheet = true }
                        )
                    }
                }
            }
        }
    }

    if (showSpeedSheet) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Playback Speed", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                speeds.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { spd ->
                            val selected = playerState.playbackSpeed == spd
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ElectricGreen else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        playerController.setPlaybackSpeed(spd)
                                        showSpeedSheet = false
                                    }
                            ) {
                                Text(
                                    text = "${spd}x",
                                    color = if (selected) Color(0xFF00210E) else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSubtitleSheet) {
        SubtitleSettingsSheet(
            tracks = playerState.subtitleTracks,
            selectedTrackId = playerState.selectedSubtitleId,
            prefs = settings.subtitlePrefs,
            onDismiss = { showSubtitleSheet = false },
            onSelectTrack = { track ->
                playerController.selectSubtitleTrack(track, settings.subtitlePrefs.encoding)
            },
            onPickExternalSubtitle = onPickExternalSubtitle,
            onUpdatePrefs = onUpdateSubtitlePrefs
        )
    }

    if (showAudioTrackSheet) {
        ModalBottomSheet(onDismissRequest = { showAudioTrackSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Audio Tracks", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                if (playerState.audioTracks.isEmpty()) {
                    Text(
                        "Default audio track active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    playerState.audioTracks.forEach { track ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    playerController.selectAudioTrack(track)
                                    showAudioTrackSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(track.label, style = MaterialTheme.typography.bodyMedium)
                                if (track.isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = ElectricGreen)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Playback Queue (${playerState.queue.size})", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    itemsIndexed(playerState.queue) { index, item ->
                        val active = index == playerState.currentIndex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    playerController.playQueueIndex(index)
                                    showQueueSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    color = if (active) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(FormatUtils.formatDuration(item.durationMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlayerActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
