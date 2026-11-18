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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.data.database.PlaylistItemEntity
import com.example.data.model.AudioItem
import com.example.data.model.PlaylistSummary
import com.example.data.model.VideoItem
import com.example.ui.components.EmptyMediaStateCard
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    allVideos: List<VideoItem>,
    allAudio: List<AudioItem>,
    observePlaylistItems: (Long) -> Flow<List<PlaylistItemEntity>>,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddVideoToPlaylist: (Long, VideoItem) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onMoveItem: (List<PlaylistItemEntity>, Int, Int) -> Unit,
    onPlayVideos: (List<VideoItem>, Int) -> Unit,
    onPlayAudio: (List<AudioItem>, Int) -> Unit,
    onBack: () -> Unit
) {
    var selectedPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var showAddMediaDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedPlaylist?.name ?: "My Playlists") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPlaylist != null) selectedPlaylist = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedPlaylist == null) showCreateDialog = true else showAddMediaDialog = true
                },
                containerColor = ElectricGreen,
                contentColor = Color(0xFF00210E),
                modifier = Modifier.testTag("playlist_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        val activePlaylist = selectedPlaylist
        if (activePlaylist == null) {
            if (playlists.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    EmptyMediaStateCard(
                        title = "No Playlists Yet",
                        subtitle = "Organize your favorite videos and music tracks into custom playlists.",
                        primaryButtonText = "Create Playlist",
                        onPrimaryClick = { showCreateDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlaylist = pl }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = ElectricGreen)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${pl.itemCount} items • ${FormatUtils.formatDuration(pl.totalDurationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { renameTarget = pl }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val items by observePlaylistItems(activePlaylist.id).collectAsState(initial = emptyList())
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val matchedVideos = items.mapNotNull { item -> allVideos.firstOrNull { it.uriString == item.uriString } }
                            if (matchedVideos.isNotEmpty()) {
                                onPlayVideos(matchedVideos, 0)
                            } else {
                                val matchedAudio = items.mapNotNull { item -> allAudio.firstOrNull { it.uriString == item.uriString } }
                                if (matchedAudio.isNotEmpty()) onPlayAudio(matchedAudio, 0)
                            }
                        },
                        enabled = items.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = Color(0xFF00210E)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play All")
                    }
                    OutlinedButton(
                        onClick = {
                            val shuffled = items.shuffled()
                            val matchedVideos = shuffled.mapNotNull { item -> allVideos.firstOrNull { it.uriString == item.uriString } }
                            if (matchedVideos.isNotEmpty()) onPlayVideos(matchedVideos, 0)
                        },
                        enabled = items.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }

                if (items.isEmpty()) {
                    EmptyMediaStateCard(
                        title = "Playlist is Empty",
                        subtitle = "Tap + to add local videos to '${activePlaylist.name}'.",
                        primaryButtonText = "Add Media",
                        onPrimaryClick = { showAddMediaDialog = true }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { idx, item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}.", style = MaterialTheme.typography.labelLarge, color = PremiumBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(FormatUtils.formatDuration(item.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (idx > 0) {
                                        IconButton(onClick = { onMoveItem(items, idx, idx - 1) }) {
                                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up")
                                        }
                                    }
                                    if (idx < items.lastIndex) {
                                        IconButton(onClick = { onMoveItem(items, idx, idx + 1) }) {
                                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down")
                                        }
                                    }
                                    IconButton(onClick = { onRemoveItem(item.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreatePlaylist(nameInput)
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { target ->
        var nameInput by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenamePlaylist(target.id, nameInput)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    if (showAddMediaDialog && selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showAddMediaDialog = false },
            title = { Text("Add Video to Playlist") },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(allVideos, key = { it.uriString }) { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddVideoToPlaylist(selectedPlaylist!!.id, video)
                                    showAddMediaDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = ElectricGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddMediaDialog = false }) { Text("Close") } }
        )
    }
}
