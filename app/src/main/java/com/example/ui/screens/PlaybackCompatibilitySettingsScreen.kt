package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.CodecSupportReport
import com.example.data.model.DecoderPreference
import com.example.data.model.PlaybackEnginePreference
import com.example.data.model.SubtitlePreferences
import com.example.data.scanner.CodecInspector
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackCompatibilitySettingsScreen(
    settings: AppSettings,
    inspectedFileReport: CodecSupportReport?,
    onSelectEngine: (PlaybackEnginePreference) -> Unit,
    onSelectDecoder: (DecoderPreference) -> Unit,
    onSelectPreferredAudioLanguage: (String) -> Unit,
    onUpdateAudioDelayMs: (Long) -> Unit,
    onUpdateAudioBoostPercent: (Int) -> Unit,
    onUpdateSubtitlePrefs: (SubtitlePreferences) -> Unit,
    onPickFileToDiagnose: () -> Unit,
    onBack: () -> Unit
) {
    var showSubtitleSheet by remember { mutableStateOf(false) }
    val detailedInventory = remember { CodecInspector.getDetailedDecoderInventory() }
    val audioLangOptions = remember {
        listOf(
            "und" to "Default",
            "en" to "English",
            "bn" to "Bengali",
            "hi" to "Hindi",
            "es" to "Spanish",
            "ja" to "Japanese",
            "ar" to "Arabic"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback Compatibility") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("compat_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Engine Mode Selection
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoMode, contentDescription = null, tint = ElectricGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Playback Engine Strategy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Auto Engine plays via Media3 ExoPlayer and seamlessly falls back to LibVLC if any container, codec, or decoder fails.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        PlaybackEnginePreference.entries.forEach { option ->
                            val selected = settings.enginePreference == option
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) ElectricGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSelectEngine(option) }
                                    .testTag("engine_option_${option.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selected) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (option == PlaybackEnginePreference.AUTO) "${option.label} (Recommended)" else option.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) ElectricGreen else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Hardware & Software Decoding
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Memory, contentDescription = null, tint = PremiumBlue)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Decoder Mode (HW / SW)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        DecoderPreference.entries.forEach { mode ->
                            val selected = settings.decoderPreference == mode
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) PremiumBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSelectDecoder(mode) }
                                    .testTag("decoder_option_${mode.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selected) PremiumBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) PremiumBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (settings.decoderPreference == DecoderPreference.SOFTWARE) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AmberGold.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Software decoding uses CPU instead of dedicated GPU/VPU silicon and may increase battery drain or temperature on 4K/HEVC streams.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Audio Tracks, Sync Delay & Safe Boost
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = ElectricGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Audio Compatibility & Boost",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Preferred Audio Language", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            audioLangOptions.forEach { (code, label) ->
                                FilterChip(
                                    selected = settings.preferredAudioLanguage == code,
                                    onClick = { onSelectPreferredAudioLanguage(code) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricGreen,
                                        selectedLabelColor = Color(0xFF00210E)
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Audio Boost: ${settings.audioBoostPercent}%", style = MaterialTheme.typography.titleSmall)
                            if (settings.audioBoostPercent > 100) {
                                TextButton(onClick = { onUpdateAudioBoostPercent(100) }) { Text("Reset") }
                            }
                        }
                        Slider(
                            value = settings.audioBoostPercent.toFloat(),
                            onValueChange = { onUpdateAudioBoostPercent(it.toInt()) },
                            valueRange = 100f..200f,
                            colors = SliderDefaults.colors(thumbColor = ElectricGreen, activeTrackColor = ElectricGreen)
                        )
                        Text(
                            "Uses LibVLC pre-amp or Android LoudnessEnhancer for quiet stereo/AC3 tracks without clipping.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                String.format("Audio Sync Delay: %+.2fs", settings.audioDelayMs / 1000f),
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (settings.audioDelayMs != 0L) {
                                TextButton(onClick = { onUpdateAudioDelayMs(0L) }) { Text("Reset") }
                            }
                        }
                        Slider(
                            value = settings.audioDelayMs.toFloat(),
                            onValueChange = { onUpdateAudioDelayMs((it / 50f).toInt() * 50L) },
                            valueRange = -5000f..5000f,
                            colors = SliderDefaults.colors(thumbColor = PremiumBlue, activeTrackColor = PremiumBlue)
                        )

                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSubtitleSheet = true }
                                .testTag("compat_open_subtitle_studio")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = ElectricGreen)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Subtitle Studio & Bengali UTF-8", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "SRT / ASS / SSA / VTT • ${settings.subtitlePrefs.fontSizeSp.toInt()}sp • ${settings.subtitlePrefs.encoding}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Supported Containers + Live File Inspector
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.SettingsInputComponent, contentDescription = null, tint = PremiumBlue)
                                Spacer(Modifier.width(10.dp))
                                Text("Playback Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onPickFileToDiagnose,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricGreen,
                                    contentColor = Color(0xFF00210E)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("compat_inspect_file_button")
                            ) {
                                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Inspect File")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Supported Containers (${CodecInspector.supportedVideoContainers.size}): " +
                                CodecInspector.supportedVideoContainers.joinToString(", ") { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (inspectedFileReport != null) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(
                                        "Header & Stream Report • ${inspectedFileReport.container}" +
                                            if (inspectedFileReport.detectedByHeader) " (Verified via magic bytes)" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ElectricGreen
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Video: ${inspectedFileReport.videoCodec ?: "None"} | Audio: ${inspectedFileReport.audioCodec ?: "None"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "HW Decoder: ${if (inspectedFileReport.hasHardwareDecoder) "Yes" else "No"} • " +
                                            "SW Decoder: ${if (inspectedFileReport.hasSoftwareDecoder) "Yes" else "No"} • " +
                                            "LibVLC: ${if (inspectedFileReport.vlcCanHandle) "Ready" else "Fallback"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        inspectedFileReport.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (inspectedFileReport.isVideoSupported || inspectedFileReport.vlcCanHandle) ElectricGreen else CoralError
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Device Decoder Matrix Header
            item {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Codec Matrix (Device MediaCodec + LibVLC)", style = MaterialTheme.typography.titleSmall)
                }
            }

            items(detailedInventory) { cap ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cap.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(cap.mimeType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DecoderPill("HW", cap.hardwareSupported, ElectricGreen)
                            DecoderPill("SW", cap.softwareSupported, PremiumBlue)
                            DecoderPill("VLC", cap.vlcSupported, AmberGold)
                        }
                    }
                }
            }
        }
    }

    if (showSubtitleSheet) {
        SubtitleSettingsSheet(
            tracks = emptyList(),
            selectedTrackId = null,
            prefs = settings.subtitlePrefs,
            onDismiss = { showSubtitleSheet = false },
            onSelectTrack = {},
            onPickExternalSubtitle = {},
            onUpdatePrefs = onUpdateSubtitlePrefs
        )
    }
}

@Composable
private fun DecoderPill(label: String, active: Boolean, activeColor: Color) {
    Box(
        modifier = Modifier
            .background(
                color = if (active) activeColor.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}
