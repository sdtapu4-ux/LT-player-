package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.model.AudioItem
import com.example.data.model.VideoItem
import com.example.editor.MergeClipEntry
import com.example.editor.SourceAudioTrackInfo
import com.example.editor.TextOverlayPosition
import com.example.editor.VideoCropPreset
import com.example.editor.VideoEditorConfig
import com.example.editor.VideoExportResolution
import com.example.editor.VideoFilterPreset
import com.example.editor.VideoSplitExportChoice
import com.example.editor.VideoTimelineMode
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@Composable
fun TimelineToolPanel(
    config: VideoEditorConfig,
    onUpdateConfig: (VideoEditorConfig) -> Unit,
    onSeekPreviewMs: (Long) -> Unit,
    onOpenMergeLibraryPicker: () -> Unit,
    onOpenMergeSafPicker: () -> Unit
) {
    val dur = config.durationMs.coerceAtLeast(1_000L).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoTimelineMode.entries.forEach { mode ->
                val selected = config.timelineMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateConfig(config.copy(timelineMode = mode)) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricGreen,
                        selectedLabelColor = Color(0xFF00210E)
                    ),
                    modifier = Modifier.testTag("editor_mode_${mode.name.lowercase()}")
                )
            }
        }

        when (config.timelineMode) {
            VideoTimelineMode.TRIM -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Keep: ${FormatUtils.formatDuration(config.trimStartMs)} – ${FormatUtils.formatDuration(config.trimEndMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricGreen
                    )
                    Text(
                        "Out: ${FormatUtils.formatDuration((config.trimEndMs - config.trimStartMs).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PremiumBlue
                    )
                }
                RangeSlider(
                    value = config.trimStartMs.toFloat().coerceIn(0f, dur)..config.trimEndMs.toFloat().coerceIn(0f, dur),
                    onValueChange = { range ->
                        val start = range.start.toLong()
                        val end = range.endInclusive.toLong().coerceAtLeast((start + 400L).coerceAtMost(config.durationMs))
                        onUpdateConfig(config.copy(trimStartMs = start, trimEndMs = end))
                        onSeekPreviewMs(start)
                    },
                    valueRange = 0f..dur,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricGreen,
                        activeTrackColor = ElectricGreen
                    )
                )
            }

            VideoTimelineMode.CUT -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Remove Middle: ${FormatUtils.formatDuration(config.cutStartMs)} – ${FormatUtils.formatDuration(config.cutEndMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = CoralError
                    )
                    val remainingMs = (config.durationMs - (config.cutEndMs - config.cutStartMs)).coerceAtLeast(0L)
                    Text(
                        "Kept: ${FormatUtils.formatDuration(remainingMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricGreen
                    )
                }
                RangeSlider(
                    value = config.cutStartMs.toFloat().coerceIn(0f, dur)..config.cutEndMs.toFloat().coerceIn(0f, dur),
                    onValueChange = { range ->
                        val start = range.start.toLong()
                        val end = range.endInclusive.toLong().coerceAtLeast((start + 300L).coerceAtMost(config.durationMs))
                        onUpdateConfig(config.copy(cutStartMs = start, cutEndMs = end))
                        onSeekPreviewMs(start)
                    },
                    valueRange = 0f..dur,
                    colors = SliderDefaults.colors(
                        thumbColor = CoralError,
                        activeTrackColor = CoralError
                    )
                )
                Text(
                    "Exports a seamless video with the highlighted middle section removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VideoTimelineMode.SPLIT -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Split Point: ${FormatUtils.formatDuration(config.splitPositionMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = PremiumBlue
                    )
                    Text(
                        "Total: ${FormatUtils.formatDuration(config.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = config.splitPositionMs.toFloat().coerceIn(0f, dur),
                    onValueChange = { pos ->
                        val clamped = pos.toLong().coerceIn(300L, (config.durationMs - 300L).coerceAtLeast(300L))
                        onUpdateConfig(config.copy(splitPositionMs = clamped))
                        onSeekPreviewMs(clamped)
                    },
                    valueRange = 0f..dur,
                    colors = SliderDefaults.colors(thumbColor = PremiumBlue, activeTrackColor = PremiumBlue)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoSplitExportChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = config.splitExportChoice == choice,
                            onClick = { onUpdateConfig(config.copy(splitExportChoice = choice)) },
                            label = { Text(choice.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PremiumBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            VideoTimelineMode.MERGE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Merge Sequence (${config.mergeClips.size} clips)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onOpenMergeLibraryPicker) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Library")
                        }
                        OutlinedButton(onClick = onOpenMergeSafPicker) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Files")
                        }
                    }
                }
                config.mergeClips.forEachIndexed { idx, clip ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}.",
                                color = ElectricGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(clip.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(FormatUtils.formatDuration(clip.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (idx > 0) {
                                IconButton(onClick = {
                                    val mutable = config.mergeClips.toMutableList()
                                    val moved = mutable.removeAt(idx)
                                    mutable.add(idx - 1, moved)
                                    onUpdateConfig(config.copy(mergeClips = mutable))
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (idx < config.mergeClips.lastIndex) {
                                IconButton(onClick = {
                                    val mutable = config.mergeClips.toMutableList()
                                    val moved = mutable.removeAt(idx)
                                    mutable.add(idx + 1, moved)
                                    onUpdateConfig(config.copy(mergeClips = mutable))
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (config.mergeClips.size > 1) {
                                IconButton(onClick = {
                                    val updated = config.mergeClips.toMutableList().apply { removeAt(idx) }
                                    onUpdateConfig(config.copy(mergeClips = updated))
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = CoralError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransformToolPanel(
    config: VideoEditorConfig,
    onUpdateConfig: (VideoEditorConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Rotate & Flip", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onUpdateConfig(config.copy(rotationDegrees = (config.rotationDegrees + 90) % 360))
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("editor_rotate_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${config.rotationDegrees}°")
            }
            FilterChip(
                selected = config.flipHorizontal,
                onClick = { onUpdateConfig(config.copy(flipHorizontal = !config.flipHorizontal)) },
                leadingIcon = { Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Flip H") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricGreen,
                    selectedLabelColor = Color(0xFF00210E)
                ),
                modifier = Modifier.testTag("editor_flip_h_button")
            )
            FilterChip(
                selected = config.flipVertical,
                onClick = { onUpdateConfig(config.copy(flipVertical = !config.flipVertical)) },
                leadingIcon = { Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Flip V") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricGreen,
                    selectedLabelColor = Color(0xFF00210E)
                ),
                modifier = Modifier.testTag("editor_flip_v_button")
            )
        }

        Text("Crop Aspect Preset", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VideoCropPreset.entries.forEach { preset ->
                FilterChip(
                    selected = config.cropPreset == preset,
                    onClick = {
                        onUpdateConfig(
                            config.copy(
                                cropPreset = preset,
                                cropInsetLeft = 0f,
                                cropInsetRight = 0f,
                                cropInsetTop = 0f,
                                cropInsetBottom = 0f
                            )
                        )
                    },
                    label = { Text(preset.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PremiumBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        if (config.cropPreset == VideoCropPreset.ORIGINAL) {
            Text(
                "Custom Edge Crop (L/R: ${(config.cropInsetLeft * 100).toInt()}%/${(config.cropInsetRight * 100).toInt()}%, T/B: ${(config.cropInsetTop * 100).toInt()}%/${(config.cropInsetBottom * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Left", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.cropInsetLeft,
                        onValueChange = { onUpdateConfig(config.copy(cropInsetLeft = it)) },
                        valueRange = 0f..0.40f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Right", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.cropInsetRight,
                        onValueChange = { onUpdateConfig(config.copy(cropInsetRight = it)) },
                        valueRange = 0f..0.40f
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Top", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.cropInsetTop,
                        onValueChange = { onUpdateConfig(config.copy(cropInsetTop = it)) },
                        valueRange = 0f..0.40f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bottom", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.cropInsetBottom,
                        onValueChange = { onUpdateConfig(config.copy(cropInsetBottom = it)) },
                        valueRange = 0f..0.40f
                    )
                }
            }
        }

        // Video Resize (Custom Dimensions)
        Text("Video Resize (Custom Dimensions)", style = MaterialTheme.typography.titleSmall)
        val resizePresets = listOf(
            Triple("Auto", 0, 0),
            Triple("1920×1080", 1920, 1080),
            Triple("1280×720", 1280, 720),
            Triple("1080×1920", 1080, 1920),
            Triple("1080×1080", 1080, 1080),
            Triple("854×480", 854, 480)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            resizePresets.forEach { (label, w, h) ->
                val selected = config.resizeWidth == w && config.resizeHeight == h
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateConfig(config.copy(resizeWidth = w, resizeHeight = h)) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricGreen,
                        selectedLabelColor = Color(0xFF00210E)
                    )
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = if (config.resizeWidth > 0) config.resizeWidth.toString() else "",
                onValueChange = { raw ->
                    val v = raw.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                    val fallbackH = if (v > 0 && config.resizeHeight == 0) config.sourceHeight else config.resizeHeight
                    onUpdateConfig(config.copy(resizeWidth = v, resizeHeight = if (v == 0) 0 else fallbackH))
                },
                label = { Text("Width px") },
                placeholder = { Text("Auto") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("editor_resize_width_input")
            )
            OutlinedTextField(
                value = if (config.resizeHeight > 0) config.resizeHeight.toString() else "",
                onValueChange = { raw ->
                    val v = raw.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                    val fallbackW = if (v > 0 && config.resizeWidth == 0) config.sourceWidth else config.resizeWidth
                    onUpdateConfig(config.copy(resizeHeight = v, resizeWidth = if (v == 0) 0 else fallbackW))
                },
                label = { Text("Height px") },
                placeholder = { Text("Auto") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("editor_resize_height_input")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedToolPanel(
    config: VideoEditorConfig,
    onUpdateConfig: (VideoEditorConfig) -> Unit
) {
    val speedPresets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Playback & Export Speed", style = MaterialTheme.typography.titleSmall)
            Text(
                text = String.format("%.2fx", config.speedMultiplier) +
                    when {
                        config.speedMultiplier < 0.99f -> " (Slow-Mo)"
                        config.speedMultiplier > 1.01f -> " (Fast-Mo)"
                        else -> " (Normal)"
                    },
                style = MaterialTheme.typography.labelLarge,
                color = ElectricGreen,
                fontWeight = FontWeight.Bold
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            speedPresets.forEach { spd ->
                FilterChip(
                    selected = kotlin.math.abs(config.speedMultiplier - spd) < 0.02f,
                    onClick = { onUpdateConfig(config.copy(speedMultiplier = spd)) },
                    label = { Text("${spd}x") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricGreen,
                        selectedLabelColor = Color(0xFF00210E)
                    )
                )
            }
        }
        Slider(
            value = config.speedMultiplier,
            onValueChange = {
                val snapped = (it * 20f).toInt() / 20f
                onUpdateConfig(config.copy(speedMultiplier = snapped.coerceIn(0.25f, 3.0f)))
            },
            valueRange = 0.25f..3.0f,
            colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen)
        )
    }
}

@Composable
fun ColorFilterToolPanel(
    config: VideoEditorConfig,
    onUpdateConfig: (VideoEditorConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Video Filters", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = {
                onUpdateConfig(
                    config.copy(
                        brightness = 0f,
                        contrast = 0f,
                        saturation = 0f,
                        filterPreset = VideoFilterPreset.NONE
                    )
                )
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoFilterPreset.entries.forEach { preset ->
                FilterChip(
                    selected = config.filterPreset == preset,
                    onClick = { onUpdateConfig(config.copy(filterPreset = preset)) },
                    label = { Text(preset.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricGreen,
                        selectedLabelColor = Color(0xFF00210E)
                    ),
                    modifier = Modifier.testTag("editor_filter_${preset.name.lowercase()}")
                )
            }
        }

        ColorAdjustmentSlider(
            label = "Brightness",
            valueText = String.format("%+.0f%%", config.brightness * 100f),
            value = config.brightness,
            range = -0.5f..0.5f,
            onValueChange = { onUpdateConfig(config.copy(brightness = it)) }
        )
        ColorAdjustmentSlider(
            label = "Contrast",
            valueText = String.format("%+.0f%%", config.contrast * 100f),
            value = config.contrast,
            range = -0.5f..0.5f,
            onValueChange = { onUpdateConfig(config.copy(contrast = it)) }
        )
        ColorAdjustmentSlider(
            label = "Saturation",
            valueText = String.format("%+.0f", config.saturation),
            value = config.saturation,
            range = -100f..100f,
            onValueChange = { onUpdateConfig(config.copy(saturation = it)) }
        )
    }
}

@Composable
private fun ColorAdjustmentSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = ElectricGreen)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = PremiumBlue, activeTrackColor = PremiumBlue)
        )
    }
}

@Composable
fun TextSubtitleToolPanel(
    config: VideoEditorConfig,
    onUpdateConfig: (VideoEditorConfig) -> Unit,
    onPickSubtitleFile: () -> Unit
) {
    val textColors = listOf(0xFFFFFFFFL, 0xFF00E676L, 0xFFFFB300L, 0xFFFF5252L, 0xFF40C4FFL)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = config.overlayText,
            onValueChange = { onUpdateConfig(config.copy(overlayText = it)) },
            label = { Text("Text Overlay") },
            placeholder = { Text("Type title or watermark text...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_text_overlay_input")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                textColors.forEach { hex ->
                    val selected = config.overlayTextColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .clickable { onUpdateConfig(config.copy(overlayTextColorHex = hex)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextOverlayPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = config.overlayTextPosition == pos,
                        onClick = { onUpdateConfig(config.copy(overlayTextPosition = pos)) },
                        label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // Text Size Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Text Size", style = MaterialTheme.typography.labelMedium)
            Text(
                "${(config.overlayTextSizeScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = ElectricGreen
            )
        }
        Slider(
            value = config.overlayTextSizeScale,
            onValueChange = { onUpdateConfig(config.copy(overlayTextSizeScale = it)) },
            valueRange = 0.4f..1.5f,
            colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen),
            modifier = Modifier.testTag("editor_text_size_slider")
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = PremiumBlue)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Burn-In Subtitles (SRT / VTT / ASS)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (config.subtitleCues.isNotEmpty()) {
                                "${config.subtitleFileName ?: "Subtitle"} • ${config.subtitleCues.size} cues"
                            } else {
                                "No external subtitle file loaded"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (config.subtitleCues.isNotEmpty()) {
                        Switch(
                            checked = config.subtitleEnabled,
                            onCheckedChange = { onUpdateConfig(config.copy(subtitleEnabled = it)) }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    OutlinedButton(
                        onClick = onPickSubtitleFile,
                        modifier = Modifier.testTag("editor_import_subtitle_button")
                    ) {
                        Text("Import")
                    }
                }
                if (config.subtitleCues.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            String.format("Subtitle Timing Offset: %+.2fs", config.subtitleDelayMs / 1000f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onUpdateConfig(config.copy(subtitleDelayMs = config.subtitleDelayMs - 250L)) }) {
                                Text("-0.25s")
                            }
                            TextButton(onClick = { onUpdateConfig(config.copy(subtitleDelayMs = 0L)) }) {
                                Text("Reset")
                            }
                            TextButton(onClick = { onUpdateConfig(config.copy(subtitleDelayMs = config.subtitleDelayMs + 250L)) }) {
                                Text("+0.25s")
                            }
                        }
                    }
                    Slider(
                        value = config.subtitleDelayMs.toFloat(),
                        onValueChange = {
                            val snapped = (it / 50f).toInt() * 50L
                            onUpdateConfig(config.copy(subtitleDelayMs = snapped.coerceIn(-10_000L, 10_000L)))
                        },
                        valueRange = -5000f..5000f,
                        colors = SliderDefaults.colors(thumbColor = PremiumBlue, activeTrackColor = PremiumBlue),
                        modifier = Modifier.testTag("editor_subtitle_timing_slider")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioExportToolPanel(
    config: VideoEditorConfig,
    sourceAudioTracks: List<SourceAudioTrackInfo>,
    onUpdateConfig: (VideoEditorConfig) -> Unit,
    onOpenMusicLibraryPicker: () -> Unit,
    onPickMusicSaf: () -> Unit,
    onExportClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (config.muteOriginalAudio) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = if (config.muteOriginalAudio) CoralError else ElectricGreen
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remove Original Audio", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (config.muteOriginalAudio) "Original video audio track removed on export" else "Original audio track active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.muteOriginalAudio,
                        onCheckedChange = { onUpdateConfig(config.copy(muteOriginalAudio = it)) },
                        modifier = Modifier.testTag("editor_mute_switch")
                    )
                }

                if (!config.muteOriginalAudio) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Original Audio Volume", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(config.originalAudioVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = config.originalAudioVolume,
                        onValueChange = { onUpdateConfig(config.copy(originalAudioVolume = it)) },
                        valueRange = 0f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen),
                        modifier = Modifier.testTag("editor_original_volume_slider")
                    )

                    if (sourceAudioTracks.isNotEmpty()) {
                        Text("Audio Track Selection", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            sourceAudioTracks.forEach { track ->
                                FilterChip(
                                    selected = config.selectedAudioTrackIndex == track.audioOrderIndex,
                                    onClick = { onUpdateConfig(config.copy(selectedAudioTrackIndex = track.audioOrderIndex)) },
                                    label = { Text(track.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PremiumBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = AmberGold)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Music Track", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = config.backgroundMusicTitle ?: "None selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (config.backgroundMusicUriString != null) {
                        IconButton(onClick = {
                            onUpdateConfig(config.copy(backgroundMusicUriString = null, backgroundMusicTitle = null))
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear Music", tint = CoralError)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenMusicLibraryPicker, modifier = Modifier.weight(1f)) {
                        Text("Pick from Music")
                    }
                    OutlinedButton(onClick = onPickMusicSaf, modifier = Modifier.weight(1f)) {
                        Text("Pick Audio File")
                    }
                }
                if (config.backgroundMusicUriString != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "BGM Mix Volume: ${(config.backgroundMusicVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = config.backgroundMusicVolume,
                        onValueChange = { onUpdateConfig(config.copy(backgroundMusicVolume = it)) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                    )
                }
            }
        }

        Text("Custom Frame Rate (FPS)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Auto", 24 to "24 fps", 30 to "30 fps", 60 to "60 fps").forEach { (fps, label) ->
                FilterChip(
                    selected = config.customFrameRate == fps,
                    onClick = { onUpdateConfig(config.copy(customFrameRate = fps)) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PremiumBlue,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("editor_fps_$fps")
                )
            }
        }

        Text("Export Quality (Non-Destructive Copy)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VideoExportResolution.entries.forEach { res ->
                FilterChip(
                    selected = config.exportResolution == res,
                    onClick = { onUpdateConfig(config.copy(exportResolution = res)) },
                    label = { Text(res.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricGreen,
                        selectedLabelColor = Color(0xFF00210E)
                    ),
                    modifier = Modifier.testTag("editor_quality_${res.name.lowercase()}")
                )
            }
        }

        val exportSummary = if (config.resizeWidth > 0 && config.resizeHeight > 0) {
            "${config.resizeWidth}×${config.resizeHeight}"
        } else {
            config.exportResolution.label
        } + if (config.customFrameRate > 0) " • ${config.customFrameRate}fps" else ""

        Text(
            text = "Saves new file to Movies/LT PLAYER/Edited Videos (original stays untouched)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onExportClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricGreen,
                contentColor = Color(0xFF00210E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("editor_save_gallery_button")
        ) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Export & Save ($exportSummary)", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryVideoPickerSheet(
    title: String,
    videos: List<VideoItem>,
    onSelectVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            if (videos.isEmpty()) {
                Text(
                    "No local library videos available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(videos, key = { it.uriString }) { video ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelectVideo(video)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${video.resolutionLabel} • ${FormatUtils.formatDuration(video.durationMs)} • ${FormatUtils.formatFileSize(video.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Rounded.Add, contentDescription = "Select", tint = ElectricGreen)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMusicPickerSheet(
    tracks: List<AudioItem>,
    onSelectTrack: (AudioItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Choose Background Music", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            if (tracks.isEmpty()) {
                Text(
                    "No local audio tracks found. Use 'Pick Audio File' to select from storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(tracks, key = { it.uriString }) { track ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelectTrack(track)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = AmberGold)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${track.artist} • ${FormatUtils.formatDuration(track.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
