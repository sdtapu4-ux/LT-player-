package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.AppSettings
import com.example.data.model.AspectRatioMode
import com.example.data.model.StorageBreakdown
import com.example.data.model.SubtitlePreferences
import com.example.data.model.ThemeMode
import com.example.data.scanner.CodecInspector
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@Composable
fun MeAndSettingsScreen(
    settings: AppSettings,
    storage: StorageBreakdown,
    isPremiumActive: Boolean,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateProfile: (String, Int) -> Unit,
    onUpdatePlayerDefaults: (
        speed: Float?,
        aspectRatio: AspectRatioMode?,
        gestureBrightness: Boolean?,
        gestureVolume: Boolean?,
        gestureSeek: Boolean?,
        autoPlayNext: Boolean?,
        keepScreenAwake: Boolean?,
        backgroundAudio: Boolean?,
        notificationsEnabled: Boolean?,
        privacyHideThumbs: Boolean?
    ) -> Unit,
    onUpdateSubtitlePrefs: (SubtitlePreferences) -> Unit,
    onOpenPremium: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenFolders: () -> Unit,
    onClearHistory: () -> Unit,
    onResetSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCodecDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showSubtitleDefaults by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val avatarColors = listOf(ElectricGreen, PremiumBlue, AmberGold, Color(0xFFAB47BC))
    val languages = listOf("en" to "English", "bn" to "বাংলা (Bengali)", "es" to "Español", "hi" to "हिन्दी", "ar" to "العربية")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Profile Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        avatarColors[settings.avatarIndex % avatarColors.size],
                                        PremiumBlue
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(settings.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (isPremiumActive) "LT PLAYER Ultimate Active" else "Standard Edition • Upgrade Available",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPremiumActive) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showEditProfileDialog = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile")
                    }
                }
            }
        }

        // Premium Banner
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPremium)
                    .testTag("me_premium_card")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = AmberGold, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LT PLAYER Ultimate Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (isPremiumActive) "Subscribed via Google Play" else "Ad-free studio, pro themes & advanced playback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        }

        // Quick Tools
        item {
            SettingsCardGroup(title = "Library & Storage") {
                SettingsActionRow(
                    icon = Icons.Rounded.Storage,
                    title = "Storage Management",
                    subtitle = "${FormatUtils.formatFileSize(storage.availableDeviceBytes)} available",
                    onClick = onOpenStorage
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Folder,
                    title = "Folder Browser & SAF",
                    subtitle = "Browse internal & SD card folders",
                    onClick = onOpenFolders
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Download,
                    title = "Download Manager",
                    subtitle = "Manage active and completed downloads",
                    onClick = onOpenDownloads
                )
            }
        }

        // Appearance & Language
        item {
            SettingsCardGroup(title = "Appearance & Language") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = ElectricGreen)
                    Spacer(Modifier.width(12.dp))
                    Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateTheme(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricGreen,
                                selectedLabelColor = Color(0xFF00210E)
                            )
                        )
                    }
                }
                SettingsActionRow(
                    icon = Icons.Rounded.Language,
                    title = "Language",
                    subtitle = languages.firstOrNull { it.first == settings.languageCode }?.second ?: "English",
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        // Player & Gestures
        item {
            SettingsCardGroup(title = "Playback, Gestures & Subtitles") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = PremiumBlue)
                        Spacer(Modifier.width(12.dp))
                        Text("Default Speed: ${settings.defaultSpeed}x", style = MaterialTheme.typography.titleSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            FilterChip(
                                selected = settings.defaultSpeed == spd,
                                onClick = { onUpdatePlayerDefaults(spd, null, null, null, null, null, null, null, null, null) },
                                label = { Text("${spd}x") }
                            )
                        }
                    }
                }
                SettingsActionRow(
                    icon = Icons.Rounded.Subtitles,
                    title = "Subtitle Preferences",
                    subtitle = "Size ${settings.subtitlePrefs.fontSizeSp.toInt()}sp • ${settings.subtitlePrefs.encoding}",
                    onClick = { showSubtitleDefaults = true }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.TouchApp,
                    title = "Left-Edge Brightness Gesture",
                    checked = settings.gestureBrightnessEnabled,
                    onCheckedChange = { onUpdatePlayerDefaults(null, null, it, null, null, null, null, null, null, null) }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.TouchApp,
                    title = "Right-Edge Volume Gesture",
                    checked = settings.gestureVolumeEnabled,
                    onCheckedChange = { onUpdatePlayerDefaults(null, null, null, it, null, null, null, null, null, null) }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.TouchApp,
                    title = "Horizontal Swipe Seek",
                    checked = settings.gestureSeekEnabled,
                    onCheckedChange = { onUpdatePlayerDefaults(null, null, null, null, it, null, null, null, null, null) }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Speed,
                    title = "Keep Screen Awake During Video",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = { onUpdatePlayerDefaults(null, null, null, null, null, null, it, null, null, null) }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Playback & Download Notifications",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { onUpdatePlayerDefaults(null, null, null, null, null, null, null, null, it, null) }
                )
                SettingsToggleRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = "Privacy Mode (Hide Video Thumbnails)",
                    checked = settings.privacyHideRecentThumbs,
                    onCheckedChange = { onUpdatePlayerDefaults(null, null, null, null, null, null, null, null, null, it) }
                )
            }
        }

        // Data & System
        item {
            SettingsCardGroup(title = "System & Diagnostics") {
                SettingsActionRow(
                    icon = Icons.Rounded.Code,
                    title = "Hardware Codec Diagnostics",
                    subtitle = "Inspect H.264, HEVC, AV1, VP9, AC3, FLAC decoders",
                    onClick = { showCodecDialog = true }
                )
                SettingsActionRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Clear Playback & Search History",
                    subtitle = "Removes local resume positions and search tags",
                    onClick = onClearHistory
                )
                SettingsActionRow(
                    icon = Icons.Rounded.RestartAlt,
                    title = "Reset Settings",
                    subtitle = "Restore default playback and theme preferences",
                    onClick = { showResetConfirm = true }
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Info,
                    title = "Open-Source Licenses & Version",
                    subtitle = "LT PLAYER v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                    onClick = { showLicensesDialog = true }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        var name by remember { mutableStateOf(settings.displayName) }
        var avatar by remember { mutableIntStateOf(settings.avatarIndex) }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        avatarColors.forEachIndexed { idx, color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { avatar = idx }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateProfile(name, avatar)
                    showEditProfileDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = {
                Column {
                    languages.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label)
                            if (settings.languageCode == code) Text("Active", color = ElectricGreen)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Close") } }
        )
    }

    if (showCodecDialog) {
        val inventory = remember { CodecInspector.getDeviceDecoderInventory() }
        AlertDialog(
            onDismissRequest = { showCodecDialog = false },
            title = { Text("Device Decoder Support") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(inventory.size) { idx ->
                        val (label, ok) = inventory[idx]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                if (ok) "Supported" else "Unavailable",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ok) ElectricGreen else CoralError
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCodecDialog = false }) { Text("Close") } }
        )
    }

    if (showSubtitleDefaults) {
        SubtitleSettingsSheet(
            tracks = emptyList(),
            selectedTrackId = null,
            prefs = settings.subtitlePrefs,
            onDismiss = { showSubtitleDefaults = false },
            onSelectTrack = {},
            onPickExternalSubtitle = {},
            onUpdatePrefs = onUpdateSubtitlePrefs
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset All Settings?") },
            text = { Text("This restores theme, playback speed, gesture, and subtitle preferences to defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetSettings()
                    showResetConfirm = false
                }) { Text("Reset", color = CoralError) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("About & Open-Source Licenses") },
            text = {
                Text(
                    "LT PLAYER Ultimate Edition v${BuildConfig.VERSION_NAME}\n\n" +
                        "Powered by:\n" +
                        "• AndroidX Media3 ExoPlayer & MediaSession (Apache 2.0)\n" +
                        "• Jetpack Compose & Material 3 (Apache 2.0)\n" +
                        "• AndroidX Room & DataStore (Apache 2.0)\n" +
                        "• Coil Image & Video Frame Loader (Apache 2.0)\n" +
                        "• Firebase AI Logic SDK (Apache 2.0)\n" +
                        "• Google Play Billing Library (Android SDK)"
                )
            },
            confirmButton = { TextButton(onClick = { showLicensesDialog = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun SettingsCardGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ElectricGreen)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PremiumBlue)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
