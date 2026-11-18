package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.VideoItem
import com.example.ui.AiTaskUiState
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@Composable
fun AiVideoWorkspaceScreen(
    videos: List<VideoItem>,
    aiState: AiTaskUiState,
    onSummarize: (VideoItem, String) -> Unit,
    onDescribeScene: (VideoItem) -> Unit,
    onGenerateSubtitles: (VideoItem, String) -> Unit,
    onTranslateSubtitles: (String, String) -> Unit,
    onExplainMetadata: (VideoItem) -> Unit,
    onAssistEditing: (VideoItem, String) -> Unit,
    onGenerateThumbnail: (String) -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        "Summarize" to Icons.Rounded.Summarize,
        "Scene AI" to Icons.Rounded.MovieFilter,
        "Gen Subtitles" to Icons.Rounded.ClosedCaption,
        "Translate Sub" to Icons.Rounded.Translate,
        "Metadata" to Icons.Rounded.Info,
        "Edit Coach" to Icons.Rounded.ContentCut,
        "Thumbnail" to Icons.Rounded.Image
    )
    var selectedTool by remember { mutableIntStateOf(0) }
    var selectedVideoUri by remember(videos) { mutableStateOf(videos.firstOrNull()?.uriString) }
    var promptInput by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("Spanish") }

    val selectedVideo = remember(videos, selectedVideoUri) {
        videos.firstOrNull { it.uriString == selectedVideoUri } ?: videos.firstOrNull()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Banner
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0A2A1E), Color(0xFF0B1F3A), MaterialTheme.colorScheme.surface)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ElectricGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("AI Video Workspace", style = MaterialTheme.typography.headlineSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Powered by Firebase AI Logic (Gemini 3.8 Flash & Nano Banana 2.1). No external API key required — frames and metadata are sent only when you tap Run.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Tool Selector
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tools.indices.toList()) { idx ->
                    val (label, icon) = tools[idx]
                    FilterChip(
                        selected = selectedTool == idx,
                        onClick = {
                            selectedTool = idx
                            onClearResult()
                        },
                        label = { Text(label) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricGreen,
                            selectedLabelColor = Color(0xFF00210E),
                            selectedLeadingIconColor = Color(0xFF00210E)
                        )
                    )
                }
            }
        }

        // Video Selector (for video-based tools)
        if (selectedTool != 3 && selectedTool != 6) {
            item {
                Text("Select Local Video", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                if (videos.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            "No local videos scanned yet. Grant media access or pick a file in the VIDEO tab first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(videos.take(15), key = { it.uriString }) { video ->
                            val isSel = video.uriString == selectedVideo?.uriString
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .width(175.dp)
                                    .clickable { selectedVideoUri = video.uriString }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${video.resolutionLabel} • ${FormatUtils.formatDuration(video.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSel) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Context Input Field
        item {
            when (selectedTool) {
                0 -> OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("Optional context or transcript") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                2 -> OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("Paste dialogue/script for SRT timing") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                3 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetLanguage,
                        onValueChange = { targetLanguage = it },
                        label = { Text("Target Language (e.g., Bengali, Spanish, Japanese)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        label = { Text("Paste SRT/VTT subtitle text to translate") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                5 -> OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("Editing goal (e.g., 60s Reel, chapter cuts, color mood)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                6 -> OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("Describe thumbnail artwork prompt") },
                    placeholder = { Text("Cyberpunk cinema poster with neon green rain and glowing skyline") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Action Button
        item {
            val canRun = when (selectedTool) {
                3 -> promptInput.isNotBlank() && targetLanguage.isNotBlank()
                6 -> promptInput.isNotBlank()
                else -> selectedVideo != null
            }
            Button(
                onClick = {
                    when (selectedTool) {
                        0 -> selectedVideo?.let { onSummarize(it, promptInput) }
                        1 -> selectedVideo?.let { onDescribeScene(it) }
                        2 -> selectedVideo?.let { onGenerateSubtitles(it, promptInput) }
                        3 -> onTranslateSubtitles(promptInput, targetLanguage)
                        4 -> selectedVideo?.let { onExplainMetadata(it) }
                        5 -> selectedVideo?.let { onAssistEditing(it, promptInput) }
                        6 -> onGenerateThumbnail(promptInput)
                    }
                },
                enabled = canRun && aiState !is AiTaskUiState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricGreen,
                    contentColor = Color(0xFF00210E)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("ai_run_button")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Run ${tools[selectedTool].first}", fontWeight = FontWeight.Bold)
            }
        }

        // AI Result Surface
        item {
            when (aiState) {
                is AiTaskUiState.Idle -> Unit
                is AiTaskUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(color = ElectricGreen, modifier = Modifier.size(28.dp))
                            Text(aiState.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is AiTaskUiState.Streaming -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(aiState.title, style = MaterialTheme.typography.titleMedium, color = ElectricGreen)
                            Spacer(Modifier.height(8.dp))
                            Text(aiState.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is AiTaskUiState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(aiState.title, style = MaterialTheme.typography.titleMedium, color = ElectricGreen)
                            Spacer(Modifier.height(8.dp))
                            aiState.generatedBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "AI Generated Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(aiState.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is AiTaskUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("AI Request Failed", style = MaterialTheme.typography.titleMedium, color = CoralError)
                            Spacer(Modifier.height(4.dp))
                            Text(aiState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}
