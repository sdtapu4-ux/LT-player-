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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import com.example.ui.components.EmptyMediaStateCard
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadRecord>,
    onStartDownload: (url: String, customTitle: String?, usePublicFolder: Boolean) -> Unit,
    onCancelDownload: (DownloadRecord) -> Unit,
    onRetryDownload: (DownloadRecord) -> Unit,
    onBack: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var saveToPublicDownloads by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Manager") },
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
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Add Authorized Media URL", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Supports direct HTTPS media links via Android DownloadManager. Protected DRM or paywalled streams are blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("HTTPS Media URL") },
                            placeholder = { Text("https://example.com/sample.mp4") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("download_url_input")
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
                            Text(
                                if (saveToPublicDownloads) "Save to Public Downloads/LTPlayer" else "Save to App Storage",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Switch(checked = saveToPublicDownloads, onCheckedChange = { saveToPublicDownloads = it })
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onStartDownload(urlInput, titleInput.takeIf { it.isNotBlank() }, saveToPublicDownloads)
                                urlInput = ""
                                titleInput = ""
                            },
                            enabled = urlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricGreen,
                                contentColor = Color(0xFF00210E)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("start_download_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Start Download")
                        }
                    }
                }
            }

            if (downloads.isEmpty()) {
                item {
                    EmptyMediaStateCard(
                        title = "No Downloads Yet",
                        subtitle = "Paste a direct HTTPS video or audio link above to queue a background download with notification progress."
                    )
                }
            } else {
                items(downloads, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = when (item.status) {
                                        DownloadStatus.SUCCESSFUL -> ElectricGreen
                                        DownloadStatus.FAILED -> CoralError
                                        else -> PremiumBlue
                                    }
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${item.status.label} • ${item.localPath ?: "Downloads"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.status == DownloadStatus.FAILED) {
                                    IconButton(onClick = { onRetryDownload(item) }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = ElectricGreen)
                                    }
                                }
                                IconButton(onClick = { onCancelDownload(item) }) {
                                    Icon(Icons.Rounded.Cancel, contentDescription = "Remove", tint = CoralError)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (item.status == DownloadStatus.SUCCESSFUL) 1f else item.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (item.status == DownloadStatus.FAILED) CoralError else ElectricGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${FormatUtils.formatFileSize(item.bytesDownloaded)} / ${FormatUtils.formatFileSize(item.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.errorReason != null) {
                                    Text(item.errorReason, style = MaterialTheme.typography.labelSmall, color = CoralError)
                                } else if (item.status == DownloadStatus.RUNNING) {
                                    Text("Active", style = MaterialTheme.typography.labelSmall, color = PremiumBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
