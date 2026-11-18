package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DuplicateMediaGroup
import com.example.data.model.StorageBreakdown
import com.example.data.model.VideoItem
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagementScreen(
    storage: StorageBreakdown,
    videos: List<VideoItem>,
    duplicateGroups: List<DuplicateMediaGroup>,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onClearThumbnailCache: () -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    onOpenDownloads: () -> Unit,
    onBack: () -> Unit
) {
    val largeVideos = remember(videos) {
        videos.sortedByDescending { it.sizeBytes }.take(15)
    }
    var deleteCandidate by remember { mutableStateOf<VideoItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val fraction = if (storage.totalDeviceBytes > 0L) {
                    (storage.usedDeviceBytes.toFloat() / storage.totalDeviceBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Device Storage Overview", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = ElectricGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        StorageStatRow("Total Storage", FormatUtils.formatFileSize(storage.totalDeviceBytes), MaterialTheme.colorScheme.onSurface)
                        StorageStatRow("Used Storage", FormatUtils.formatFileSize(storage.usedDeviceBytes), AmberGold)
                        StorageStatRow("Available Storage", FormatUtils.formatFileSize(storage.availableDeviceBytes), ElectricGreen)
                        StorageStatRow("Video Library", FormatUtils.formatFileSize(storage.videoBytes), PremiumBlue)
                        StorageStatRow("Music Library", FormatUtils.formatFileSize(storage.audioBytes), PremiumBlue)
                        StorageStatRow("Downloads", FormatUtils.formatFileSize(storage.downloadBytes), ElectricGreen)
                        StorageStatRow("App Thumbnail Cache", FormatUtils.formatFileSize(storage.appCacheBytes), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onClearThumbnailCache,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = Color(0xFF00210E)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Cache")
                    }
                    OutlinedButton(onClick = onOpenDownloads, modifier = Modifier.weight(1f)) {
                        Text("Downloads")
                    }
                }
            }

            if (!hasMediaPermission) {
                item {
                    OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Security, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Media Storage Permission")
                    }
                }
            }

            if (duplicateGroups.isNotEmpty()) {
                item {
                    Text("Potential Duplicate Media (${duplicateGroups.size} groups)", style = MaterialTheme.typography.titleMedium)
                }
                items(duplicateGroups, key = { it.signatureKey }) { group ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${group.items.size} identical-size files • ${FormatUtils.formatFileSize(group.fileSizeBytes)} each",
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberGold
                            )
                            Spacer(Modifier.height(6.dp))
                            group.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${item.displayName} (${item.folderName})",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = { deleteCandidate = item }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Duplicate", tint = CoralError)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Largest Video Files", style = MaterialTheme.typography.titleMedium)
            }
            items(largeVideos, key = { it.uriString }) { video ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${FormatUtils.formatFileSize(video.sizeBytes)} • ${video.resolutionLabel} • ${video.folderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deleteCandidate = video }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete File", tint = CoralError)
                        }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { video ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete '${video.displayName}'?") },
            text = { Text("This action permanently removes the file (${FormatUtils.formatFileSize(video.sizeBytes)}) from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteVideo(video)
                    deleteCandidate = null
                }) { Text("Delete", color = CoralError) }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StorageStatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
