package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewCompact
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.data.model.FolderViewMode
import com.example.data.model.MediaFolder
import com.example.data.model.PlaylistSummary
import com.example.data.model.VideoItem
import com.example.ui.components.EmptyMediaStateCard
import com.example.ui.components.VideoThumbnailBox
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    folders: List<MediaFolder>,
    videos: List<VideoItem>,
    playlists: List<PlaylistSummary>,
    hideThumbnails: Boolean,
    onAddSafFolder: () -> Unit,
    onPlayVideos: (List<VideoItem>, Int) -> Unit,
    onAddVideoToPlaylist: (Long, VideoItem) -> Unit,
    onRenameVideo: (VideoItem, String) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    var activeFolder by remember { mutableStateOf<MediaFolder?>(null) }
    var viewMode by remember { mutableStateOf(FolderViewMode.LIST) }
    var searchQuery by remember { mutableStateOf("") }
    var sortDescending by remember { mutableStateOf(true) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteConfirmList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var playlistPickerItems by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    val filteredFolders = remember(folders, searchQuery, sortDescending) {
        val base = if (searchQuery.isBlank()) folders else folders.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
        }
        if (sortDescending) base.sortedByDescending { it.videoCount } else base.sortedBy { it.name.lowercase() }
    }

    val folderVideos = remember(activeFolder, videos, searchQuery) {
        val folder = activeFolder ?: return@remember emptyList()
        videos.filter { it.folderPath == folder.path }.filter {
            searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedUris.isNotEmpty()) "${selectedUris.size} selected"
                        else activeFolder?.name ?: "Video Folders"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedUris.isNotEmpty() -> selectedUris = emptySet()
                            activeFolder != null -> {
                                activeFolder = null
                                searchQuery = ""
                            }
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedUris.isNotEmpty()) {
                        val chosen = folderVideos.filter { it.uriString in selectedUris }
                        IconButton(onClick = {
                            if (chosen.isNotEmpty()) onPlayVideos(chosen, 0)
                            selectedUris = emptySet()
                        }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Selected", tint = ElectricGreen)
                        }
                        IconButton(onClick = { playlistPickerItems = chosen }) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add Selected to Playlist")
                        }
                        IconButton(onClick = { deleteConfirmList = chosen }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected", tint = CoralError)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewMode = FolderViewMode.entries[(viewMode.ordinal + 1) % FolderViewMode.entries.size]
                            },
                            modifier = Modifier.testTag("folder_view_mode_button")
                        ) {
                            Icon(
                                when (viewMode) {
                                    FolderViewMode.LIST -> Icons.Rounded.ViewList
                                    FolderViewMode.GRID -> Icons.Rounded.GridView
                                    FolderViewMode.COMPACT -> Icons.Rounded.ViewCompact
                                },
                                contentDescription = "Switch View Mode"
                            )
                        }
                        IconButton(
                            onClick = onAddSafFolder,
                            modifier = Modifier.testTag("link_saf_folder_button")
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Link External / SD Folder", tint = ElectricGreen)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (activeFolder == null) "Filter folders..." else "Search files in folder...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                if (activeFolder == null) {
                    TextButton(onClick = { sortDescending = !sortDescending }) {
                        Text(if (sortDescending) "Most" else "A-Z")
                    }
                }
            }

            if (activeFolder == null) {
                if (filteredFolders.isEmpty()) {
                    EmptyMediaStateCard(
                        title = "No Video Folders",
                        subtitle = "Link an internal or SD card folder via Storage Access Framework to browse its contents.",
                        primaryButtonText = "Link Folder",
                        onPrimaryClick = onAddSafFolder
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredFolders, key = { it.path }) { folder ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeFolder = folder
                                        searchQuery = ""
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = PremiumBlue, modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${folder.videoCount} videos • ${FormatUtils.formatFileSize(folder.totalSizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(folder.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${folderVideos.size} items", style = MaterialTheme.typography.labelLarge)
                    Button(
                        onClick = { if (folderVideos.isNotEmpty()) onPlayVideos(folderVideos, 0) },
                        enabled = folderVideos.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = Color(0xFF00210E))
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play Folder")
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewMode == FolderViewMode.GRID) {
                        items(folderVideos.chunked(2)) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                pair.forEach { video ->
                                    val selected = video.uriString in selectedUris
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .combinedClickable(
                                                onClick = {
                                                    if (selectedUris.isNotEmpty()) {
                                                        selectedUris = if (selected) selectedUris - video.uriString else selectedUris + video.uriString
                                                    } else {
                                                        onPlayVideos(folderVideos, folderVideos.indexOf(video))
                                                    }
                                                },
                                                onLongClick = { selectedUris = selectedUris + video.uriString }
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            VideoThumbnailBox(video.uriString, video.title, hideThumbnails, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                            Spacer(Modifier.height(6.dp))
                                            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(FormatUtils.formatFileSize(video.sizeBytes), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(folderVideos, key = { it.uriString }) { video ->
                            val selected = video.uriString in selectedUris
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedUris.isNotEmpty()) {
                                                selectedUris = if (selected) selectedUris - video.uriString else selectedUris + video.uriString
                                            } else {
                                                onPlayVideos(folderVideos, folderVideos.indexOf(video))
                                            }
                                        },
                                        onLongClick = { selectedUris = selectedUris + video.uriString }
                                    )
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (selectedUris.isNotEmpty()) {
                                        Icon(
                                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = "Select",
                                            tint = ElectricGreen
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else if (viewMode == FolderViewMode.LIST) {
                                        VideoThumbnailBox(video.uriString, video.title, hideThumbnails, Modifier.width(96.dp).aspectRatio(16f / 9f))
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${video.resolutionLabel} • ${FormatUtils.formatDuration(video.durationMs)} • ${FormatUtils.formatFileSize(video.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { renameTarget = video }) {
                                        Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename")
                                    }
                                    IconButton(onClick = { deleteConfirmList = listOf(video) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = CoralError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirmList.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteConfirmList = emptyList() },
            title = { Text("Delete ${deleteConfirmList.size} file(s)?") },
            text = { Text("This permanently deletes the selected local media file(s) from device storage.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmList.forEach(onDeleteVideo)
                    deleteConfirmList = emptyList()
                    selectedUris = emptySet()
                }) { Text("Delete", color = CoralError) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmList = emptyList() }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { target ->
        var newName by remember { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("File name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameVideo(target, newName)
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    if (playlistPickerItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { playlistPickerItems = emptyList() },
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("Create a playlist first in the Playlists screen.")
                    } else {
                        playlists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playlistPickerItems.forEach { v -> onAddVideoToPlaylist(pl.id, v) }
                                        playlistPickerItems = emptyList()
                                        selectedUris = emptySet()
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(pl.name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { playlistPickerItems = emptyList() }) { Text("Close") } }
        )
    }
}
