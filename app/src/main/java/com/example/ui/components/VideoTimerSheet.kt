package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.SleepTimerEndAction
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

private val SLEEP_TIMER_PRESETS_MIN = listOf(5, 10, 15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoTimerBottomSheet(
    remainingSec: Int?,
    totalSec: Int?,
    endAction: SleepTimerEndAction = SleepTimerEndAction.AUTO_PAUSE,
    onSelectEndAction: (SleepTimerEndAction) -> Unit = {},
    onStartMinutes: (Int) -> Unit,
    onStartHoursMinutes: (Int, Int) -> Unit = { h, m -> onStartMinutes(h * 60 + m) },
    onResetTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var customHoursInput by remember { mutableStateOf("0") }
    var customMinutesInput by remember { mutableStateOf("") }
    val parsedHours = (customHoursInput.trim().toIntOrNull() ?: 0).coerceIn(0, 12)
    val parsedMinutes = customMinutesInput.trim().toIntOrNull() ?: 0
    val customTotalMinutes = (parsedHours * 60) + parsedMinutes
    val isCustomValid = (customHoursInput.isNotBlank() || customMinutesInput.isNotBlank()) &&
        parsedMinutes in 0..59 &&
        customTotalMinutes in 1..720

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = ElectricGreen,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = PremiumBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Runs in background with live notification • ${endAction.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Active Countdown & Remaining Time Display Card
            if (remainingSec != null) {
                val safeTotal = (totalSec ?: remainingSec).coerceAtLeast(1)
                val progress = (remainingSec.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timer_countdown_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = ElectricGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Remaining (${endAction.label})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = FormatUtils.formatTimerSeconds(remainingSec),
                                style = MaterialTheme.typography.headlineSmall,
                                color = ElectricGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("timer_remaining_text")
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ElectricGreen,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onResetTimer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("timer_reset_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reset")
                            }
                            Button(
                                onClick = onCancelTimer,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoralError.copy(alpha = 0.20f),
                                    contentColor = CoralError
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("timer_cancel_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel Timer")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // When Timer Ends Action Selector
            Text(
                text = "When Timer Ends",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepTimerEndAction.entries.forEach { action ->
                    val selected = endAction == action
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectEndAction(action) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (action == SleepTimerEndAction.AUTO_PAUSE) {
                                    Icons.Rounded.PauseCircle
                                } else {
                                    Icons.Rounded.StopCircle
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(action.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricGreen,
                            selectedLabelColor = Color(0xFF00210E),
                            selectedLeadingIconColor = Color(0xFF00210E)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("timer_action_${action.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Preset Sleep Timer Chips: 5, 10, 15, 30, 45, 60 min
            Text(
                text = "Preset Durations",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SLEEP_TIMER_PRESETS_MIN.forEach { mins ->
                    val selected = totalSec == mins * 60 && remainingSec != null
                    FilterChip(
                        selected = selected,
                        onClick = { onStartMinutes(mins) },
                        label = {
                            Text(
                                text = "$mins min",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricGreen,
                            selectedLabelColor = Color(0xFF00210E)
                        ),
                        modifier = Modifier.testTag("timer_preset_$mins")
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Custom Timer (Hours + Minutes)
            Text(
                text = "Custom Timer (Hours & Minutes • up to 12h)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customHoursInput,
                    onValueChange = { raw ->
                        customHoursInput = raw.filter { it.isDigit() }.take(2)
                    },
                    label = { Text("Hours") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("timer_custom_hours_input")
                )
                OutlinedTextField(
                    value = customMinutesInput,
                    onValueChange = { raw ->
                        customMinutesInput = raw.filter { it.isDigit() }.take(2)
                    },
                    label = { Text("Minutes") },
                    placeholder = { Text("25") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("timer_custom_input")
                )
                Button(
                    onClick = {
                        if (isCustomValid) {
                            onStartHoursMinutes(parsedHours, parsedMinutes)
                            customHoursInput = "0"
                            customMinutesInput = ""
                        }
                    },
                    enabled = isCustomValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("timer_custom_start_button")
                ) {
                    Text("Start")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
