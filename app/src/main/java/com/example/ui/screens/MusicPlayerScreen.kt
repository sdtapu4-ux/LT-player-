package com.example.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AudioItem
import com.example.data.model.PlaylistSummary
import com.example.data.model.RepeatModeOption
import com.example.player.PlayerController
import com.example.player.PlayerUiState
import com.example.ui.components.EmptyMediaStateCard
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    tracks: List<AudioItem>,
    playlists: List<PlaylistSummary>,
    playerController: PlayerController,
    playerState: PlayerUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlayAudio: (AudioItem, List<AudioItem>) -> Unit,
    onToggleFavorite: (AudioItem) -> Unit,
    onOpenPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Songs", "Albums", "Artists", "Genres", "Playlists", "Favorites", "Recent", "Folders")
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilterKey by remember { mutableStateOf<String?>(null) }
    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf<AudioItem?>(null) }

    val context = LocalContext.current
    var extractedLyrics by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playerState.currentEntry?.uriString, showLyricsDialog) {
        if (showLyricsDialog && playerState.currentEntry != null) {
            extractedLyrics = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(playerState.currentEntry.uriString))
                    val lyrics = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                    retriever.release()
                    lyrics
                }.getOrNull()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Music Studio", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "${tracks.size} local tracks • Hi-Fi Media3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(
                    onClick = { showSleepTimerDialog = true },
                    modifier = Modifier.testTag("music_sleep_timer_button")
                ) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (playerState.sleepTimerRemainingSec != null) ElectricGreen else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { showEqualizerSheet = true },
                    modifier = Modifier.testTag("music_equalizer_button")
                ) {
                    Icon(
                        Icons.Rounded.Equalizer,
                        contentDescription = "Equalizer",
                        tint = if (playerState.equalizerEnabled) ElectricGreen else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Category Tabs
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs.indices.toList()) { idx ->
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = {
                        selectedTab = idx
                        selectedFilterKey = null
                    },
                    label = { Text(tabs[idx]) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricGreen,
                        selectedLabelColor = Color(0xFF00210E)
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (tracks.isEmpty()) {
                EmptyMediaStateCard(
                    title = "No Local Music Found",
                    subtitle = if (hasPermission) {
                        "Add MP3, FLAC, WAV, AAC, or Opus audio files to your device to enjoy LT PLAYER Music Studio."
                    } else {
                        "Grant audio permission so LT PLAYER can organize your songs, albums, artists, and folders."
                    },
                    primaryButtonText = if (!hasPermission) "Grant Audio Access" else null,
                    onPrimaryClick = if (!hasPermission) onRequestPermission else null
                )
            } else {
                when (selectedTab) {
                    0 -> AudioTrackList(
                        tracks = tracks,
                        currentUri = playerState.currentEntry?.uriString,
                        onPlay = { onPlayAudio(it, tracks) },
                        onToggleFavorite = onToggleFavorite,
                        onShowInfo = { showSongInfoDialog = it }
                    )
                    1 -> GroupedAudioSection(
                        groups = tracks.groupBy { it.album },
                        icon = Icons.Rounded.Album,
                        selectedGroup = selectedFilterKey,
                        onSelectGroup = { selectedFilterKey = it },
                        currentUri = playerState.currentEntry?.uriString,
                        onPlay = onPlayAudio,
                        onToggleFavorite = onToggleFavorite,
                        onShowInfo = { showSongInfoDialog = it }
                    )
                    2 -> GroupedAudioSection(
                        groups = tracks.groupBy { it.artist },
                        icon = Icons.Rounded.Person,
                        selectedGroup = selectedFilterKey,
                        onSelectGroup = { selectedFilterKey = it },
                        currentUri = playerState.currentEntry?.uriString,
                        onPlay = onPlayAudio,
                        onToggleFavorite = onToggleFavorite,
                        onShowInfo = { showSongInfoDialog = it }
                    )
                    3 -> GroupedAudioSection(
                        groups = tracks.groupBy { it.genre },
                        icon = Icons.Rounded.GraphicEq,
                        selectedGroup = selectedFilterKey,
                        onSelectGroup = { selectedFilterKey = it },
                        currentUri = playerState.currentEntry?.uriString,
                        onPlay = onPlayAudio,
                        onToggleFavorite = onToggleFavorite,
                        onShowInfo = { showSongInfoDialog = it }
                    )
                    4 -> {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Button(
                                    onClick = onOpenPlaylists,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricGreen,
                                        contentColor = Color(0xFF00210E)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Playlist Manager (${playlists.size})")
                                }
                            }
                            items(playlists, key = { it.id }) { pl ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onOpenPlaylists)
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = ElectricGreen)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(pl.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${pl.itemCount} items • ${FormatUtils.formatDuration(pl.totalDurationMs)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    5 -> {
                        val favs = tracks.filter { it.isFavorite }
                        if (favs.isEmpty()) {
                            EmptyMediaStateCard("No Favorite Songs", "Tap the heart icon on any track to save it here.")
                        } else {
                            AudioTrackList(favs, playerState.currentEntry?.uriString, { onPlayAudio(it, favs) }, onToggleFavorite, { showSongInfoDialog = it })
                        }
                    }
                    6 -> {
                        val recent = tracks.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
                        if (recent.isEmpty()) {
                            EmptyMediaStateCard("No Recently Played Tracks", "Play any track to track your listening history.")
                        } else {
                            AudioTrackList(recent, playerState.currentEntry?.uriString, { onPlayAudio(it, recent) }, onToggleFavorite, { showSongInfoDialog = it })
                        }
                    }
                    7 -> GroupedAudioSection(
                        groups = tracks.groupBy { it.folderName },
                        icon = Icons.Rounded.Folder,
                        selectedGroup = selectedFilterKey,
                        onSelectGroup = { selectedFilterKey = it },
                        currentUri = playerState.currentEntry?.uriString,
                        onPlay = onPlayAudio,
                        onToggleFavorite = onToggleFavorite,
                        onShowInfo = { showSongInfoDialog = it }
                    )
                }
            }
        }

        // Persistent Mini Audio Player
        val current = playerState.currentEntry
        if (current != null && !current.isVideo) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showNowPlayingSheet = true }
                    .testTag("music_mini_player")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = ElectricGreen)
                        if (current.artworkUri != null) {
                            AsyncImage(
                                model = current.artworkUri,
                                contentDescription = current.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(current.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { playerController.skipPrevious() }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                    }
                    IconButton(onClick = { playerController.togglePlayPause() }) {
                        Icon(
                            if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = ElectricGreen
                        )
                    }
                    IconButton(onClick = { playerController.skipNext() }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                    }
                }
            }
        }
    }

    if (showNowPlayingSheet && playerState.currentEntry != null) {
        val entry = playerState.currentEntry
        ModalBottomSheet(onDismissRequest = { showNowPlayingSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(listOf(ElectricGreen.copy(alpha = 0.25f), PremiumBlue.copy(alpha = 0.25f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(72.dp))
                    if (entry.artworkUri != null) {
                        AsyncImage(
                            model = entry.artworkUri,
                            contentDescription = entry.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(entry.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                val dur = playerState.durationMs.coerceAtLeast(1L)
                Slider(
                    value = playerState.currentPositionMs.toFloat().coerceIn(0f, dur.toFloat()),
                    onValueChange = { playerController.seekTo(it.toLong()) },
                    valueRange = 0f..dur.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(FormatUtils.formatDuration(playerState.currentPositionMs), style = MaterialTheme.typography.labelSmall)
                    Text(FormatUtils.formatDuration(playerState.durationMs), style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { playerController.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.shuffleEnabled) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { playerController.skipPrevious() }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = ElectricGreen,
                        modifier = Modifier
                            .size(64.dp)
                            .clickable { playerController.togglePlayPause() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF00210E),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    IconButton(onClick = { playerController.skipNext() }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { playerController.cycleRepeatMode() }) {
                        Icon(
                            imageVector = if (playerState.repeatMode == RepeatModeOption.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (playerState.repeatMode != RepeatModeOption.OFF) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { showLyricsDialog = true }) {
                        Icon(Icons.Rounded.Lyrics, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Lyrics")
                    }
                    TextButton(onClick = { showEqualizerSheet = true }) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Equalizer")
                    }
                    TextButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            playerState.sleepTimerRemainingSec?.let { "${it / 60}m" } ?: "Timer"
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showEqualizerSheet) {
        ModalBottomSheet(onDismissRequest = { showEqualizerSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("5-Band Audio Equalizer", style = MaterialTheme.typography.titleLarge)
                    Switch(
                        checked = playerState.equalizerEnabled,
                        onCheckedChange = { playerController.setEqualizerEnabled(it) },
                        enabled = playerState.equalizerSupported
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (!playerState.equalizerSupported) {
                    Text(
                        "Start playing an audio or video track first to attach the Android AudioFX Equalizer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(playerState.equalizerPresets) { idx, name ->
                            FilterChip(
                                selected = playerState.selectedEqPreset == idx,
                                onClick = { playerController.selectEqPreset(idx) },
                                label = { Text(name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    playerState.equalizerBands.forEach { band ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${band.centerFreqHz} Hz",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = band.currentLevelMb.toFloat(),
                                onValueChange = { playerController.setEqBandLevel(band.index, it.toInt().toShort()) },
                                valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                                enabled = playerState.equalizerEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSleepTimerDialog) {
        val options = listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "60 minutes")
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    options.forEach { (mins, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerController.startSleepTimer(mins)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label)
                            if (mins > 0 && playerState.sleepTimerRemainingSec != null &&
                                (playerState.sleepTimerRemainingSec / 60) in (mins - 1)..mins
                            ) {
                                Text("Active", color = ElectricGreen)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close") } }
        )
    }

    if (showLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = { Text("Track Lyrics") },
            text = {
                Text(
                    text = extractedLyrics?.takeIf { it.isNotBlank() }
                        ?: "No embedded lyrics metadata found in this local audio file.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = { showLyricsDialog = false }) { Text("Close") } }
        )
    }

    showSongInfoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { showSongInfoDialog = null },
            title = { Text(info.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Artist: ${info.artist}")
                    Text("Album: ${info.album}")
                    Text("Duration: ${FormatUtils.formatDuration(info.durationMs)}")
                    Text("Size: ${FormatUtils.formatFileSize(info.sizeBytes)}")
                    Text("Format: ${info.mimeType}")
                    Text("Folder: ${info.folderPath}")
                    Text("Decoder: ${if (info.isCompatible) "Supported" else info.compatibilityNote ?: "Check device codec"}")
                }
            },
            confirmButton = { TextButton(onClick = { showSongInfoDialog = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun AudioTrackList(
    tracks: List<AudioItem>,
    currentUri: String?,
    onPlay: (AudioItem) -> Unit,
    onToggleFavorite: (AudioItem) -> Unit,
    onShowInfo: (AudioItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks, key = { it.uriString }) { track ->
            val active = track.uriString == currentUri
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(track) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = if (active) ElectricGreen else PremiumBlue
                        )
                        if (track.albumArtUri != null) {
                            AsyncImage(
                                model = track.albumArtUri,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) ElectricGreen else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${track.artist} • ${FormatUtils.formatDuration(track.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(track) }) {
                        Icon(
                            if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (track.isFavorite) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onShowInfo(track) }) {
                        Icon(Icons.Rounded.Info, contentDescription = "Song Info", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedAudioSection(
    groups: Map<String, List<AudioItem>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedGroup: String?,
    onSelectGroup: (String?) -> Unit,
    currentUri: String?,
    onPlay: (AudioItem, List<AudioItem>) -> Unit,
    onToggleFavorite: (AudioItem) -> Unit,
    onShowInfo: (AudioItem) -> Unit
) {
    if (selectedGroup != null && groups.containsKey(selectedGroup)) {
        val list = groups[selectedGroup].orEmpty()
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$selectedGroup (${list.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onSelectGroup(null) }) { Text("All") }
            }
            AudioTrackList(list, currentUri, { onPlay(it, list) }, onToggleFavorite, onShowInfo)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups.keys.toList()) { key ->
                val list = groups[key].orEmpty()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectGroup(key) }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = PremiumBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(key, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
