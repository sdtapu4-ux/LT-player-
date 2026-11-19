package com.example.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.database.EditorProjectEntity
import com.example.data.model.AudioItem
import com.example.data.model.VideoItem
import com.example.data.scanner.SubtitleParser
import com.example.editor.MergeClipEntry
import com.example.editor.SourceAudioTrackInfo
import com.example.editor.TextOverlayPosition
import com.example.editor.VideoEditorConfig
import com.example.editor.VideoEditorEngine
import com.example.editor.VideoEditorSpecBuilder
import com.example.editor.VideoExportState
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private enum class EditorToolTab(val label: String, val icon: ImageVector) {
    TIMELINE("Trim/Cut", Icons.Rounded.ContentCut),
    TRANSFORM("Transform", Icons.Rounded.CropRotate),
    SPEED("Speed", Icons.Rounded.Speed),
    COLOR("Filters", Icons.Rounded.AutoFixHigh),
    TEXT_SUBS("Text & Sub", Icons.Rounded.TextFields),
    AUDIO_EXPORT("Audio/Save", Icons.Rounded.MusicNote)
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    initialVideoUri: String?,
    videos: List<VideoItem>,
    audioTracks: List<AudioItem>,
    savedProjects: List<EditorProjectEntity> = emptyList(),
    subtitleEncoding: String,
    onSaveProjectDraft: (name: String, sourceUriString: String, configJson: String) -> Unit = { _, _, _ -> },
    onDeleteProjectDraft: (Long) -> Unit = {},
    onExportSavedToGallery: () -> Unit,
    onPlayExportedVideo: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editorEngine = remember { VideoEditorEngine(context.applicationContext) }
    val exportState by editorEngine.exportState.collectAsStateWithLifecycle()

    val resolvedInitialVideo = remember(initialVideoUri, videos) {
        videos.firstOrNull { it.uriString == initialVideoUri } ?: videos.firstOrNull()
    }

    var config by remember(resolvedInitialVideo?.uriString, initialVideoUri) {
        val baseUri = initialVideoUri ?: resolvedInitialVideo?.uriString
        val dur = (resolvedInitialVideo?.durationMs ?: 30_000L).coerceAtLeast(1_000L)
        val w = resolvedInitialVideo?.width?.takeIf { it > 0 } ?: 1280
        val h = resolvedInitialVideo?.height?.takeIf { it > 0 } ?: 720
        val title = resolvedInitialVideo?.title ?: "Selected Video"
        mutableStateOf(
            baseUri?.let { uriStr ->
                VideoEditorConfig(
                    sourceUriString = uriStr,
                    title = title,
                    durationMs = dur,
                    sourceWidth = w,
                    sourceHeight = h,
                    mergeClips = listOf(
                        MergeClipEntry(
                            uriString = uriStr,
                            title = title,
                            durationMs = dur,
                            resolutionLabel = resolvedInitialVideo?.resolutionLabel ?: ""
                        )
                    )
                )
            }
        )
    }

    var undoStack by remember { mutableStateOf<List<VideoEditorConfig>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<VideoEditorConfig>>(emptyList()) }

    fun updateConfigWithHistory(next: VideoEditorConfig) {
        val prev = config
        if (prev != null && prev != next) {
            undoStack = (undoStack + prev).takeLast(30)
            redoStack = emptyList()
        }
        config = next
    }

    fun performUndo() {
        val current = config ?: return
        val last = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + current).takeLast(30)
        config = last
    }

    fun performRedo() {
        val current = config ?: return
        val next = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + current).takeLast(30)
        config = next
    }

    var sourceAudioTracks by remember { mutableStateOf<List<SourceAudioTrackInfo>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(EditorToolTab.TIMELINE) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var previewDurationMs by remember { mutableLongStateOf(config?.durationMs ?: 0L) }

    var showChangeVideoPicker by remember { mutableStateOf(false) }
    var showMergeLibraryPicker by remember { mutableStateOf(false) }
    var showMusicLibraryPicker by remember { mutableStateOf(false) }
    var showSaveProjectDialog by remember { mutableStateOf(false) }
    var showLoadProjectsSheet by remember { mutableStateOf(false) }
    var projectNameInput by remember { mutableStateOf("") }

    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    fun selectVideoIntoEditor(uri: Uri, knownItem: VideoItem? = null) {
        scope.launch {
            val probe = withContext(Dispatchers.IO) {
                probeVideoMetadata(context, uri)
            }
            val metaName = knownItem?.title ?: queryDisplayName(context, uri)
            val dur = knownItem?.durationMs?.takeIf { it > 0L } ?: probe.durationMs.takeIf { it > 0L } ?: 30_000L
            val w = knownItem?.width?.takeIf { it > 0 } ?: probe.width.takeIf { it > 0 } ?: 1280
            val h = knownItem?.height?.takeIf { it > 0 } ?: probe.height.takeIf { it > 0 } ?: 720
            val uriStr = uri.toString()
            undoStack = emptyList()
            redoStack = emptyList()
            config = VideoEditorConfig(
                sourceUriString = uriStr,
                title = metaName,
                durationMs = dur,
                sourceWidth = w,
                sourceHeight = h,
                mergeClips = listOf(
                    MergeClipEntry(
                        uriString = uriStr,
                        title = metaName,
                        durationMs = dur,
                        resolutionLabel = knownItem?.resolutionLabel ?: "${w}x${h}"
                    )
                )
            )
            previewPositionMs = 0L
        }
    }

    LaunchedEffect(initialVideoUri) {
        if (initialVideoUri != null && resolvedInitialVideo == null) {
            selectVideoIntoEditor(Uri.parse(initialVideoUri))
        }
    }

    val safSourceVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectVideoIntoEditor(uri)
        }
    }

    val safMergeClipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val probe = withContext(Dispatchers.IO) {
                    probeVideoMetadata(context, uri)
                }
                val title = queryDisplayName(context, uri)
                val entry = MergeClipEntry(
                    uriString = uri.toString(),
                    title = title,
                    durationMs = probe.durationMs.coerceAtLeast(1_000L),
                    resolutionLabel = if (probe.width > 0 && probe.height > 0) "${probe.width}x${probe.height}" else ""
                )
                config?.let { current ->
                    updateConfigWithHistory(current.copy(mergeClips = current.mergeClips + entry))
                }
            }
        }
    }

    val safSubtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val cues = withContext(Dispatchers.IO) {
                    SubtitleParser.loadFromUri(context, uri, subtitleEncoding)
                }
                val name = queryDisplayName(context, uri)
                config?.let { current ->
                    updateConfigWithHistory(
                        current.copy(
                            subtitleCues = cues,
                            subtitleEnabled = cues.isNotEmpty(),
                            subtitleFileName = name
                        )
                    )
                }
            }
        }
    }

    val safMusicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri)
            config?.let { current ->
                updateConfigWithHistory(
                    current.copy(
                        backgroundMusicUriString = uri.toString(),
                        backgroundMusicTitle = name
                    )
                )
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPreviewPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = previewPlayer.duration
                if (d > 0L) {
                    previewDurationMs = d
                    config?.let { current ->
                        if (kotlin.math.abs(current.durationMs - d) > 1_000L && current.durationMs <= 30_000L) {
                            config = VideoEditorSpecBuilder.normalizeConfig(
                                current.copy(durationMs = d, trimEndMs = d, splitPositionMs = d / 2L)
                            )
                        }
                    }
                }
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
            editorEngine.release()
        }
    }

    val currentConfig = config

    LaunchedEffect(currentConfig?.sourceUriString) {
        val uriStr = currentConfig?.sourceUriString ?: return@LaunchedEffect
        val parsedUri = Uri.parse(uriStr)
        sourceAudioTracks = withContext(Dispatchers.IO) {
            VideoEditorSpecBuilder.inspectSourceAudioTracks(context, parsedUri)
        }
        previewPlayer.setMediaItem(MediaItem.fromUri(parsedUri))
        previewPlayer.prepare()
        previewPlayer.playWhenReady = false
    }

    val glEffectKey = currentConfig?.let {
        listOf(
            it.cropPreset, it.cropInsetLeft, it.cropInsetRight, it.cropInsetTop, it.cropInsetBottom,
            it.rotationDegrees, it.flipHorizontal, it.flipVertical,
            it.brightness, it.contrast, it.saturation, it.filterPreset
        )
    }
    LaunchedEffect(glEffectKey) {
        val cfg = config ?: return@LaunchedEffect
        val effects = VideoEditorSpecBuilder.buildVideoEffects(
            config = cfg,
            includeOverlays = false,
            targetShortSidePx = null
        )
        runCatching {
            val pos = previewPlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = previewPlayer.isPlaying
            previewPlayer.setVideoEffects(effects)
            previewPlayer.prepare()
            if (pos > 0L) previewPlayer.seekTo(pos)
            previewPlayer.playWhenReady = wasPlaying
        }
    }

    LaunchedEffect(
        currentConfig?.speedMultiplier,
        currentConfig?.muteOriginalAudio,
        currentConfig?.originalAudioVolume
    ) {
        val cfg = config ?: return@LaunchedEffect
        previewPlayer.setPlaybackSpeed(cfg.speedMultiplier.coerceIn(0.25f, 3.0f))
        previewPlayer.volume = if (VideoEditorSpecBuilder.shouldRemoveOriginalAudio(cfg)) {
            0f
        } else {
            cfg.originalAudioVolume.coerceIn(0f, 1.0f)
        }
    }

    LaunchedEffect(currentConfig?.sourceUriString) {
        while (isActive) {
            previewPositionMs = previewPlayer.currentPosition.coerceAtLeast(0L)
            val d = previewPlayer.duration
            if (d > 0L) previewDurationMs = d
            delay(180L)
        }
    }

    BackHandler {
        if (exportState is VideoExportState.Exporting) {
            editorEngine.cancelExport()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Editor Header with Undo / Redo / Projects / Switch Video / Export
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Video Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = currentConfig?.title ?: "No video selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (currentConfig != null) {
                IconButton(
                    onClick = { performUndo() },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo",
                        tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = { performRedo() },
                    enabled = redoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_redo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Redo",
                        tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = {
                        projectNameInput = "${currentConfig.title.substringBeforeLast('.')} Project"
                        showSaveProjectDialog = true
                    },
                    modifier = Modifier.testTag("editor_save_project_button")
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Save Project Draft", tint = PremiumBlue)
                }
            }
            if (savedProjects.isNotEmpty()) {
                IconButton(
                    onClick = { showLoadProjectsSheet = true },
                    modifier = Modifier.testTag("editor_load_projects_button")
                ) {
                    Icon(Icons.Rounded.FolderSpecial, contentDescription = "Saved Projects", tint = ElectricGreen)
                }
            }
            IconButton(onClick = { showChangeVideoPicker = true }) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = "Switch Video", tint = PremiumBlue)
            }
            if (currentConfig != null) {
                Button(
                    onClick = {
                        previewPlayer.pause()
                        editorEngine.startExport(currentConfig) {
                            onExportSavedToGallery()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricGreen,
                        contentColor = Color(0xFF00210E)
                    ),
                    modifier = Modifier.testTag("editor_top_export_button")
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }

        if (currentConfig == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(48.dp))
                        Text("Open a Video to Edit", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Trim, cut, split, merge, crop, resize, adjust FPS & audio, apply filters, add text or subtitles, and save drafts or export to Edited Videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { safSourceVideoPicker.launch(arrayOf("video/*")) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricGreen,
                                    contentColor = Color(0xFF00210E)
                                )
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pick File")
                            }
                            if (videos.isNotEmpty()) {
                                OutlinedButton(onClick = { showChangeVideoPicker = true }) {
                                    Text("From Library")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Live Preview Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = previewPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (currentConfig.overlayText.isNotBlank()) {
                    val align = when (currentConfig.overlayTextPosition) {
                        TextOverlayPosition.TOP -> Alignment.TopCenter
                        TextOverlayPosition.CENTER -> Alignment.Center
                        TextOverlayPosition.BOTTOM -> Alignment.BottomCenter
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                        modifier = Modifier
                            .align(align)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentConfig.overlayText.trim(),
                            color = Color(currentConfig.overlayTextColorHex),
                            fontSize = (18f * currentConfig.overlayTextSizeScale).coerceIn(10f, 30f).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (currentConfig.subtitleEnabled && currentConfig.subtitleCues.isNotEmpty()) {
                    val cue = SubtitleParser.findCueAt(
                        currentConfig.subtitleCues,
                        previewPositionMs,
                        delayMs = currentConfig.subtitleDelayMs
                    )
                    if (cue != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.78f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Text(
                                text = cue.text,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Transport Bar
            val safeDur = previewDurationMs.coerceAtLeast(currentConfig.durationMs).coerceAtLeast(1L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = ElectricGreen,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            if (previewPlayer.isPlaying) previewPlayer.pause() else previewPlayer.play()
                        }
                        .testTag("editor_preview_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPreviewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play Preview",
                            tint = Color(0xFF00210E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(FormatUtils.formatDuration(previewPositionMs), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = previewPositionMs.toFloat().coerceIn(0f, safeDur.toFloat()),
                    onValueChange = {
                        val ms = it.toLong()
                        previewPositionMs = ms
                        previewPlayer.seekTo(ms)
                    },
                    valueRange = 0f..safeDur.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
                Text(FormatUtils.formatDuration(safeDur), style = MaterialTheme.typography.labelSmall)
            }

            // Editor Tool Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorToolTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(tab.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = ElectricGreen,
                            selectedLeadingIconColor = ElectricGreen
                        ),
                        modifier = Modifier.testTag("editor_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Active Tool Deck
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        EditorToolTab.TIMELINE -> TimelineToolPanel(
                            config = currentConfig,
                            onUpdateConfig = { updateConfigWithHistory(it) },
                            onSeekPreviewMs = { ms -> previewPlayer.seekTo(ms) },
                            onOpenMergeLibraryPicker = { showMergeLibraryPicker = true },
                            onOpenMergeSafPicker = { safMergeClipPicker.launch(arrayOf("video/*")) }
                        )
                        EditorToolTab.TRANSFORM -> TransformToolPanel(
                            config = currentConfig,
                            onUpdateConfig = { updateConfigWithHistory(it) }
                        )
                        EditorToolTab.SPEED -> SpeedToolPanel(
                            config = currentConfig,
                            onUpdateConfig = { updateConfigWithHistory(it) }
                        )
                        EditorToolTab.COLOR -> ColorFilterToolPanel(
                            config = currentConfig,
                            onUpdateConfig = { updateConfigWithHistory(it) }
                        )
                        EditorToolTab.TEXT_SUBS -> TextSubtitleToolPanel(
                            config = currentConfig,
                            onUpdateConfig = { updateConfigWithHistory(it) },
                            onPickSubtitleFile = { safSubtitlePicker.launch(arrayOf("*/*")) }
                        )
                        EditorToolTab.AUDIO_EXPORT -> AudioExportToolPanel(
                            config = currentConfig,
                            sourceAudioTracks = sourceAudioTracks,
                            onUpdateConfig = { updateConfigWithHistory(it) },
                            onOpenMusicLibraryPicker = { showMusicLibraryPicker = true },
                            onPickMusicSaf = { safMusicPicker.launch(arrayOf("audio/*")) },
                            onExportClick = {
                                previewPlayer.pause()
                                editorEngine.startExport(currentConfig) {
                                    onExportSavedToGallery()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSaveProjectDialog && currentConfig != null) {
        AlertDialog(
            onDismissRequest = { showSaveProjectDialog = false },
            title = { Text("Save Editor Project") },
            text = {
                OutlinedTextField(
                    value = projectNameInput,
                    onValueChange = { projectNameInput = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = Json.encodeToString(currentConfig)
                        onSaveProjectDraft(
                            projectNameInput.ifBlank { currentConfig.title },
                            currentConfig.sourceUriString,
                            json
                        )
                        showSaveProjectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = Color(0xFF00210E))
                ) {
                    Text("Save Draft")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProjectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLoadProjectsSheet) {
        ModalBottomSheet(onDismissRequest = { showLoadProjectsSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Saved Editor Projects", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(savedProjects, key = { it.id }) { proj ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    runCatching {
                                        val loaded = Json.decodeFromString<VideoEditorConfig>(proj.configJson)
                                        undoStack = emptyList()
                                        redoStack = emptyList()
                                        config = loaded
                                    }
                                    showLoadProjectsSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        FormatUtils.formatRelativeTime(proj.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteProjectDraft(proj.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete Draft", tint = CoralError)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showChangeVideoPicker) {
        LibraryVideoPickerSheet(
            title = "Select Video to Edit",
            videos = videos,
            onSelectVideo = { v -> selectVideoIntoEditor(Uri.parse(v.uriString), v) },
            onDismiss = { showChangeVideoPicker = false }
        )
    }

    if (showMergeLibraryPicker) {
        LibraryVideoPickerSheet(
            title = "Add Clip to Merge",
            videos = videos,
            onSelectVideo = { v ->
                config?.let { cur ->
                    val entry = MergeClipEntry(v.uriString, v.title, v.durationMs, v.resolutionLabel)
                    updateConfigWithHistory(cur.copy(mergeClips = cur.mergeClips + entry))
                }
            },
            onDismiss = { showMergeLibraryPicker = false }
        )
    }

    if (showMusicLibraryPicker) {
        LibraryMusicPickerSheet(
            tracks = audioTracks,
            onSelectTrack = { track ->
                config?.let { cur ->
                    updateConfigWithHistory(
                        cur.copy(
                            backgroundMusicUriString = track.uriString,
                            backgroundMusicTitle = track.title
                        )
                    )
                }
            },
            onDismiss = { showMusicLibraryPicker = false }
        )
    }

    when (val st = exportState) {
        is VideoExportState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Exporting Video") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(st.stageLabel, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { (st.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ElectricGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "${st.progressPercent}% • Original file remains untouched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { editorEngine.cancelExport() }) {
                        Text("Cancel Export", color = CoralError)
                    }
                }
            )
        }

        is VideoExportState.Success -> {
            AlertDialog(
                onDismissRequest = { editorEngine.resetExportState() },
                icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(36.dp)) },
                title = { Text("Saved to Edited Videos") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (st.savedFileCount > 1) {
                                "${st.savedFileCount} split videos saved to ${st.outputFolderLabel}!"
                            } else {
                                "Saved new video copy to ${st.outputFolderLabel}:"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(st.displayFileName, style = MaterialTheme.typography.labelLarge, color = ElectricGreen)
                        Text(
                            "Total output size: ${FormatUtils.formatFileSize(st.outputSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetUri = st.primaryGalleryUri
                            editorEngine.resetExportState()
                            onPlayExportedVideo(targetUri)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricGreen,
                            contentColor = Color(0xFF00210E)
                        )
                    ) {
                        Text("Play Video")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editorEngine.resetExportState() }) {
                        Text("Done")
                    }
                }
            )
        }

        is VideoExportState.Error -> {
            AlertDialog(
                onDismissRequest = { editorEngine.resetExportState() },
                icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = CoralError, modifier = Modifier.size(36.dp)) },
                title = { Text("Export Failed") },
                text = { Text(st.message, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(onClick = { editorEngine.resetExportState() }) {
                        Text("OK")
                    }
                }
            )
        }

        VideoExportState.Idle -> Unit
    }
}

private data class VideoMetadataProbe(
    val durationMs: Long,
    val width: Int,
    val height: Int
)

private fun probeVideoMetadata(context: android.content.Context, uri: Uri): VideoMetadataProbe {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        VideoMetadataProbe(durationMs = dur, width = w, height = h)
    } catch (_: Throwable) {
        VideoMetadataProbe(0L, 0, 0)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Selected Media" } ?: "Selected Media"
}
