package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.database.SearchHistoryEntity
import com.example.data.model.AudioItem
import com.example.data.model.DownloadRecord
import com.example.data.model.MediaFolder
import com.example.data.model.MediaSortOption
import com.example.data.model.PlaylistSummary
import com.example.data.model.VideoItem
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    videos: List<VideoItem>,
    audio: List<AudioItem>,
    folders: List<MediaFolder>,
    playlists: List<PlaylistSummary>,
    downloads: List<DownloadRecord>,
    searchHistory: List<SearchHistoryEntity>,
    onRecordSearch: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onPlayAudio: (AudioItem, List<AudioItem>) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenDownloads: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var minDurationMin by remember { mutableStateOf(0) }
    var sortBy by remember { mutableStateOf(MediaSortOption.DATE_DESC) }

    val categories = listOf("All", "Videos", "Music", "Albums", "Artists", "Folders", "Playlists", "Downloads")

    val matchedVideos = remember(videos, query, minDurationMin, sortBy) {
        val q = query.trim()
        val filtered = videos.filter { v ->
            (q.isEmpty() || v.title.contains(q, true) || v.folderName.contains(q, true)) &&
                v.durationMs >= minDurationMin * 60_000L
        }
        when (sortBy) {
            MediaSortOption.DATE_DESC -> filtered.sortedByDescending { it.dateAddedSec }
            MediaSortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            MediaSortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            MediaSortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
        }
    }

    val matchedAudio = remember(audio, query, category) {
        val q = query.trim()
        audio.filter { a ->
            when (category) {
                "Albums" -> q.isEmpty() || a.album.contains(q, true)
                "Artists" -> q.isEmpty() || a.artist.contains(q, true)
                else -> q.isEmpty() || a.title.contains(q, true) || a.artist.contains(q, true) || a.album.contains(q, true)
            }
        }
    }

    val matchedFolders = remember(folders, query) {
        val q = query.trim()
        folders.filter { q.isEmpty() || it.name.contains(q, true) }
    }

    val matchedPlaylists = remember(playlists, query) {
        val q = query.trim()
        playlists.filter { q.isEmpty() || it.name.contains(q, true) }
    }

    val matchedDownloads = remember(downloads, query) {
        val q = query.trim()
        downloads.filter { q.isEmpty() || it.title.contains(q, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search videos, songs, albums, artists, folders...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("global_search_input")
            )

            Spacer(Modifier.height(8.dp))

            // Category Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricGreen,
                            selectedLabelColor = Color(0xFF00210E)
                        )
                    )
                }
            }

            // Duration & Sort Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = minDurationMin == 5,
                        onClick = { minDurationMin = if (minDurationMin == 5) 0 else 5 },
                        label = { Text("5+ min") }
                    )
                }
                items(MediaSortOption.entries) { option ->
                    FilterChip(
                        selected = sortBy == option,
                        onClick = { sortBy = option },
                        label = { Text(option.label) }
                    )
                }
            }

            // Search History
            if (query.isEmpty() && searchHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Searches", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onClearSearchHistory) { Text("Clear") }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchHistory, key = { it.query }) { hist ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { query = hist.query }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text(hist.query, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (category in listOf("All", "Videos")) {
                    items(matchedVideos, key = { "v_${it.uriString}" }) { v ->
                        SearchResultRow(
                            icon = Icons.Rounded.Movie,
                            title = v.title,
                            subtitle = "Video • ${v.resolutionLabel} • ${FormatUtils.formatDuration(v.durationMs)} • ${FormatUtils.formatFileSize(v.sizeBytes)}",
                            onClick = {
                                if (query.isNotBlank()) onRecordSearch(query)
                                onPlayVideo(v, matchedVideos)
                            }
                        )
                    }
                }
                if (category in listOf("All", "Music", "Albums", "Artists")) {
                    items(matchedAudio, key = { "a_${it.uriString}" }) { a ->
                        SearchResultRow(
                            icon = Icons.Rounded.MusicNote,
                            title = a.title,
                            subtitle = "${a.artist} • ${a.album} • ${FormatUtils.formatDuration(a.durationMs)}",
                            onClick = {
                                if (query.isNotBlank()) onRecordSearch(query)
                                onPlayAudio(a, matchedAudio)
                            }
                        )
                    }
                }
                if (category in listOf("All", "Folders")) {
                    items(matchedFolders, key = { "f_${it.path}" }) { f ->
                        SearchResultRow(
                            icon = Icons.Rounded.Folder,
                            title = f.name,
                            subtitle = "Folder • ${f.videoCount} videos • ${f.audioCount} audio",
                            onClick = onOpenFolders
                        )
                    }
                }
                if (category in listOf("All", "Playlists")) {
                    items(matchedPlaylists, key = { "p_${it.id}" }) { p ->
                        SearchResultRow(
                            icon = Icons.Rounded.PlaylistPlay,
                            title = p.name,
                            subtitle = "Playlist • ${p.itemCount} items",
                            onClick = onOpenPlaylists
                        )
                    }
                }
                if (category in listOf("All", "Downloads")) {
                    items(matchedDownloads, key = { "d_${it.id}" }) { d ->
                        SearchResultRow(
                            icon = Icons.Rounded.Download,
                            title = d.title,
                            subtitle = "Download • ${d.status.label}",
                            onClick = onOpenDownloads
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = PremiumBlue)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
