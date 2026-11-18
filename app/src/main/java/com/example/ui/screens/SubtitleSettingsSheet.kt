package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SubtitlePreferences
import com.example.data.model.SubtitleTrackOption
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSheet(
    tracks: List<SubtitleTrackOption>,
    selectedTrackId: String?,
    prefs: SubtitlePreferences,
    onDismiss: () -> Unit,
    onSelectTrack: (SubtitleTrackOption?) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onUpdatePrefs: (SubtitlePreferences) -> Unit
) {
    val encodings = listOf("UTF-8", "ISO-8859-1", "UTF-16", "Windows-1252")
    val colorOptions = listOf(
        0xFFFFFFFFL to Color.White,
        0xFF00E676L to ElectricGreen,
        0xFFFFD54FL to AmberGold,
        0xFF40C4FFL to PremiumBlue
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = ElectricGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Subtitle Studio", style = MaterialTheme.typography.titleLarge)
                }
                Switch(
                    checked = prefs.enabled,
                    onCheckedChange = { onUpdatePrefs(prefs.copy(enabled = it)) },
                    modifier = Modifier.testTag("subtitle_enable_switch")
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Available Tracks", style = MaterialTheme.typography.titleSmall)
                Button(
                    onClick = onPickExternalSubtitle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("load_external_subtitle_button")
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Load SRT/VTT/ASS")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Track List
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTrack(null) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Off / None", style = MaterialTheme.typography.bodyMedium)
                    if (selectedTrackId == null) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = ElectricGreen)
                    }
                }
            }

            tracks.forEach { track ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTrack(track) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (track.isEmbedded) "Embedded Stream" else "External File",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selectedTrackId == track.id) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = ElectricGreen)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sync Delay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Subtitle Sync Delay", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onUpdatePrefs(prefs.copy(delayMs = (prefs.delayMs - 250L).coerceAtLeast(-10_000L))) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Delay -0.25s")
                    }
                    Text(
                        text = String.format("%+.2fs", prefs.delayMs / 1000f),
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricGreen
                    )
                    IconButton(onClick = { onUpdatePrefs(prefs.copy(delayMs = (prefs.delayMs + 250L).coerceAtMost(10_000L))) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Delay +0.25s")
                    }
                    TextButton(onClick = { onUpdatePrefs(prefs.copy(delayMs = 0L)) }) {
                        Text("Reset")
                    }
                }
            }

            // Font Size
            Text("Font Size: ${prefs.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = prefs.fontSizeSp,
                onValueChange = { onUpdatePrefs(prefs.copy(fontSizeSp = it)) },
                valueRange = 12f..32f,
                colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen)
            )

            // Position
            Text("Vertical Position: ${prefs.verticalBottomPaddingDp.toInt()}dp", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = prefs.verticalBottomPaddingDp,
                onValueChange = { onUpdatePrefs(prefs.copy(verticalBottomPaddingDp = it)) },
                valueRange = 16f..180f,
                colors = SliderDefaults.colors(thumbColor = PremiumBlue, activeTrackColor = PremiumBlue)
            )

            // Background Opacity
            Text("Background Opacity: ${(prefs.backgroundOpacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = prefs.backgroundOpacity,
                onValueChange = { onUpdatePrefs(prefs.copy(backgroundOpacity = it)) },
                valueRange = 0f..0.9f,
                colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen)
            )

            // Text Color
            Text("Text Color", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                colorOptions.forEach { (hex, color) ->
                    val selected = prefs.textColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) ElectricGreen else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onUpdatePrefs(prefs.copy(textColorHex = hex)) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Encoding
            Text("Text Encoding", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(encodings) { enc ->
                    FilterChip(
                        selected = prefs.encoding == enc,
                        onClick = { onUpdatePrefs(prefs.copy(encoding = enc)) },
                        label = { Text(enc) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
