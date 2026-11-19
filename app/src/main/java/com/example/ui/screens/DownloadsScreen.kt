package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DownloadMediaCategory
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import com.example.ui.components.EmptyMediaStateCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

private enum class DownloadFilterTab(val label: String) {
    ALL("All"),
    QUEUE("Queue"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    FILES("Files"),
    HISTORY("History")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadRecord>,
    onStartDownload: (url: String, customTitle: String?, usePublicFolder: Boolean, safTreeUri: String?) -> Unit,
    onPauseDownload: (Long) -> Unit = {},
    onResumeDownload: (Long) -> Unit = {},
    onCancelDownload: (DownloadRecord) -> Unit,
    onRetryDownload: (DownloadRecord) -> Unit,
    onPlayDownloadedMedia: (DownloadRecord) -> Unit = {},
    onOpenDownloadedFile: (DownloadRecord) -> Unit = {},
    onShareDownloadedFile: (DownloadRecord) -> Unit = {},
    onDeleteDownloadedFile: (DownloadRecord) -> Unit = onCancelDownload,
    onClearHistory: () -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var saveToPublicDownloads by remember { mutableStateOf(true) }
    var selectedSafTreeUri by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(DownloadFilterTab.ALL) }

    val safFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            selectedSafTreeUri = uri.toString()
        }
    }

    val filteredDownloads = remember(downloads, selectedFilter) {
        when (selectedFilter) {
            DownloadFilterTab.ALL -> downloads
            DownloadFilterTab.QUEUE -> downloads.filter {
                it.status == DownloadStatus.PENDING ||
                    it.status == DownloadStatus.RUNNING ||
                    it.status == DownloadStatus.PAUSED
            }
            DownloadFilterTab.VIDEOS -> downloads.filter { it.mediaCategory == DownloadMediaCategory.VIDEO }
            DownloadFilterTab.MUSIC -> downloads.filter { it.mediaCategory == DownloadMediaCategory.MUSIC }
            DownloadFilterTab.FILES -> downloads.filter { it.mediaCategory == DownloadMediaCategory.FILE }
            DownloadFilterTab.HISTORY -> downloads.filter {
                it.status == DownloadStatus.SUCCESSFUL || it.status == DownloadStatus.FAILED
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenBrowser,
                        modifier = Modifier.testTag("downloads_open_browser_button")
                    ) {
                        Icon(Icons.Rounded.Public, contentDescription = "Open Web Browser", tint = ElectricGreen)
                    }
                    if (downloads.any { it.status == DownloadStatus.SUCCESSFUL || it.status == DownloadStatus.FAILED }) {
                        IconButton(
                            onClick = onClearHistory,
                            modifier = Modifier.testTag("downloads_clear_history_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Download History")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Resumable Media Downloader", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = onOpenBrowser) {
                                Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Browse Web")
                            }
                        }
                        Text(
                            "Supports multi-queue HTTP Range pause/resume, live speed & ETA, SAF folders, and SHA-verified saving. Protected DRM manifests are blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("HTTPS Media or File URL") },
                            placeholder = { Text("https://example.com/sample.mp4") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_url_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("File Name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        selectedSafTreeUri != null -> "Saving to custom SAF folder"
                                        saveToPublicDownloads -> "Save to Public Downloads/LTPlayer"
                                        else -> "Save to App Private Storage"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (selectedSafTreeUri != null) {
                                    Text(
                                        text = selectedSafTreeUri!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (selectedSafTreeUri == null) {
                                Switch(
                                    checked = saveToPublicDownloads,
                                    onCheckedChange = { saveToPublicDownloads = it }
                                )
                            } else {
                                TextButton(onClick = { selectedSafTreeUri = null }) {
                                    Text("Reset")
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { safFolderPicker.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("SAF Folder")
                            }
                            Button(
                                onClick = {
                                    onStartDownload(
                                        urlInput,
                                        titleInput.takeIf { it.isNotBlank() },
                                        saveToPublicDownloads,
                                        selectedSafTreeUri
                                    )
                                    urlInput = ""
                                    titleInput = ""
                                },
                                enabled = urlInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricGreen,
                                    contentColor = Color(0xFF00210E)
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("start_download_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Queue Download")
                            }
                        }
                    }
                }
            }

            // Category Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DownloadFilterTab.entries.forEach { tab ->
                        val count = when (tab) {
                            DownloadFilterTab.ALL -> downloads.size
                            DownloadFilterTab.QUEUE -> downloads.count {
                                it.status == DownloadStatus.PENDING ||
                                    it.status == DownloadStatus.RUNNING ||
                                    it.status == DownloadStatus.PAUSED
                            }
                            DownloadFilterTab.VIDEOS -> downloads.count { it.mediaCategory == DownloadMediaCategory.VIDEO }
                            DownloadFilterTab.MUSIC -> downloads.count { it.mediaCategory == DownloadMediaCategory.MUSIC }
                            DownloadFilterTab.FILES -> downloads.count { it.mediaCategory == DownloadMediaCategory.FILE }
                            DownloadFilterTab.HISTORY -> downloads.count {
                                it.status == DownloadStatus.SUCCESSFUL || it.status == DownloadStatus.FAILED
                            }
                        }
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = { Text("${tab.label} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricGreen,
                                selectedLabelColor = Color(0xFF00210E)
                            )
                        )
                    }
                }
            }

            if (filteredDownloads.isEmpty()) {
                item {
                    EmptyMediaStateCard(
                        title = if (downloads.isEmpty()) "No Downloads Yet" else "No Items in ${selectedFilter.label}",
                        subtitle = "Paste a direct HTTPS link above or open the built-in Web Browser to detect downloadable videos & audio."
                    )
                }
            } else {
                items(filteredDownloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = { onPauseDownload(item.id) },
                        onResume = { onResumeDownload(item.id) },
                        onCancel = { onCancelDownload(item) },
                        onRetry = { onRetryDownload(item) },
                        onPlay = { onPlayDownloadedMedia(item) },
                        onOpen = { onOpenDownloadedFile(item) },
                        onShare = { onShareDownloadedFile(item) },
                        onDelete = { onDeleteDownloadedFile(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadRecord,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryIcon = when (item.mediaCategory) {
        DownloadMediaCategory.VIDEO -> Icons.Rounded.Movie
        DownloadMediaCategory.MUSIC -> Icons.Rounded.MusicNote
        DownloadMediaCategory.FILE -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
    val statusColor = when (item.status) {
        DownloadStatus.SUCCESSFUL -> ElectricGreen
        DownloadStatus.FAILED -> CoralError
        DownloadStatus.PAUSED -> AmberGold
        else -> PremiumBlue
    }
    val percent = if (item.status == DownloadStatus.SUCCESSFUL) 100 else (item.progress * 100f).toInt().coerceIn(0, 100)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = item.mediaCategory.label,
                    tint = statusColor
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!item.qualityLabel.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PremiumBlue.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = item.qualityLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PremiumBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${item.status.label} • ${item.mediaCategory.label} • ${item.localPath ?: "Downloads"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (item.status) {
                    DownloadStatus.RUNNING, DownloadStatus.PENDING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Rounded.Pause, contentDescription = "Pause", tint = AmberGold)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "Cancel", tint = CoralError)
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume", tint = ElectricGreen)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "Cancel", tint = CoralError)
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = ElectricGreen)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = CoralError)
                        }
                    }
                    DownloadStatus.SUCCESSFUL -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete File", tint = CoralError)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (item.status == DownloadStatus.SUCCESSFUL) 1f else item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sizeLabel = if (item.totalBytes > 0L) {
                    "${FormatUtils.formatFileSize(item.bytesDownloaded)} / ${FormatUtils.formatFileSize(item.totalBytes)} ($percent%)"
                } else {
                    FormatUtils.formatFileSize(item.bytesDownloaded)
                }
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    item.errorReason != null -> Text(
                        text = item.errorReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralError,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.status == DownloadStatus.RUNNING -> {
                        val rem = if (item.remainingBytes > 0L) " • ${FormatUtils.formatFileSize(item.remainingBytes)} left" else ""
                        val eta = if (item.etaSeconds > 0L) " • ETA ${FormatUtils.formatEta(item.etaSeconds)}" else ""
                        Text(
                            text = "${FormatUtils.formatSpeed(item.speedBytesPerSec)}$rem$eta",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumBlue
                        )
                    }
                    item.status == DownloadStatus.PAUSED -> Text(
                        text = if (item.supportsRange) "Paused (Resumable)" else "Paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold
                    )
                }
            }

            if (item.status == DownloadStatus.SUCCESSFUL) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.mediaCategory == DownloadMediaCategory.VIDEO || item.mediaCategory == DownloadMediaCategory.MUSIC) {
                        Button(
                            onClick = onPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricGreen,
                                contentColor = Color(0xFF00210E)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open")
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}
