package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.core.model.AnimationPreset
import com.example.core.model.EdgeAction
import com.example.core.model.FeedbackConfig
import com.example.core.model.FeedbackStyle
import com.example.ui.components.CategoryTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingsGroupCard
import com.example.ui.components.SettingsSliderRow
import com.example.ui.components.SettingsToggleRow

private val ColorSwatches = listOf(
    "#00E5FF" to Color(0xFF00E5FF),
    "#38BDF8" to Color(0xFF38BDF8),
    "#10B981" to Color(0xFF10B981),
    "#A855F7" to Color(0xFFA855F7),
    "#F59E0B" to Color(0xFFF59E0B),
    "#F43F5E" to Color(0xFFF43F5E)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    config: FeedbackConfig,
    onUpdate: ((FeedbackConfig) -> FeedbackConfig) -> Unit,
    onTestHaptic: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var overrideEditingAction by remember { mutableStateOf<EdgeAction?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTopBar(
            title = "Feedback Engine",
            subtitle = "Haptics, temporary notch glow, edge lighting & per-action rules",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("feedback_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Enable Action Feedback",
                        subtitle = "Provide tactile or temporary visual confirmation when gestures execute",
                        checked = config.enabled,
                        onCheckedChange = { en -> onUpdate { it.copy(enabled = en) } },
                        testTag = "toggle_feedback_enabled"
                    )
                    Button(
                        onClick = onTestHaptic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_test_haptic")
                    ) {
                        Text("Test Haptic Pulse (${config.hapticStrengthPercent}%)")
                    }
                }
            }

            // Default Feedback Style
            item {
                SectionHeader("Default Feedback Mode")
                SettingsGroupCard {
                    FeedbackStyle.entries.forEach { style ->
                        val selected = config.defaultStyle == style
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdate { it.copy(defaultStyle = style) } }
                                .testTag("feedback_style_${style.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(modifier = Modifier.padding(start = 10.dp)) {
                                    Text(style.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        style.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Haptic & Visual Parameters
            item {
                SectionHeader("Haptic & Temporary Visual Geometry")
                SettingsGroupCard {
                    SettingsSliderRow(
                        title = "Haptic Pulse Intensity",
                        valueLabel = "${config.hapticStrengthPercent}%",
                        value = config.hapticStrengthPercent.toFloat(),
                        valueRange = 10f..100f,
                        onValueChange = { v ->
                            onUpdate { it.copy(hapticStrengthPercent = v.toInt()) }
                        },
                        testTag = "slider_haptic_strength"
                    )
                    SettingsSliderRow(
                        title = "Visual Glow Duration",
                        valueLabel = "${config.durationMs} ms",
                        value = config.durationMs.toFloat(),
                        valueRange = 180f..1000f,
                        onValueChange = { v ->
                            onUpdate { it.copy(durationMs = v.toInt()) }
                        },
                        testTag = "slider_glow_duration"
                    )
                    SettingsSliderRow(
                        title = "Punch-Hole / Notch Ring Radius",
                        valueLabel = "${config.notchRadiusDp} dp",
                        value = config.notchRadiusDp.toFloat(),
                        valueRange = 10f..36f,
                        onValueChange = { v ->
                            onUpdate { it.copy(notchRadiusDp = v.toInt()) }
                        },
                        testTag = "slider_notch_radius"
                    )
                    SettingsSliderRow(
                        title = "Punch-Hole Vertical Offset",
                        valueLabel = "${config.notchVerticalOffsetDp} dp",
                        value = config.notchVerticalOffsetDp.toFloat(),
                        valueRange = 0f..48f,
                        onValueChange = { v ->
                            onUpdate { it.copy(notchVerticalOffsetDp = v.toInt()) }
                        },
                        testTag = "slider_notch_offset"
                    )

                    Text(
                        text = "Animation Envelope",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimationPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = config.animationPreset == preset,
                                onClick = { onUpdate { it.copy(animationPreset = preset) } },
                                label = { Text(preset.title) }
                            )
                        }
                    }

                    Text(
                        text = "Glow Accent Colour",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ColorSwatches.forEach { (hex, color) ->
                            val isChosen = config.accentColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isChosen) 3.dp else 1.dp,
                                        color = if (isChosen) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onUpdate { it.copy(accentColorHex = hex) } }
                            )
                        }
                    }
                }
            }

            // Per-Action Feedback Configuration
            item {
                SectionHeader("Per-Action Feedback Overrides")
                SettingsGroupCard {
                    Text(
                        text = "Assign a custom feedback mode to individual actions (for example, Silent for Privacy Shield Snapshot).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EdgeAction.entries.filter { it != EdgeAction.NONE }.forEach { action ->
                        val activeStyle = config.styleFor(action)
                        val isOverridden = config.perActionStyles.containsKey(action)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { overrideEditingAction = action }
                                .testTag("per_action_feedback_${action.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(action.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (isOverridden) "Custom: ${activeStyle.title}" else "Default: ${activeStyle.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Configure",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    val editingAct = overrideEditingAction
    if (editingAct != null) {
        AlertDialog(
            onDismissRequest = { overrideEditingAction = null },
            title = { Text("Feedback: ${editingAct.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            onUpdate { cfg ->
                                cfg.copy(perActionStyles = cfg.perActionStyles - editingAct)
                            }
                            overrideEditingAction = null
                        }
                    ) {
                        Text("Use Global Default (${config.defaultStyle.title})")
                    }
                    FeedbackStyle.entries.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdate { cfg ->
                                        cfg.copy(perActionStyles = cfg.perActionStyles + (editingAct to st))
                                    }
                                    overrideEditingAction = null
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.perActionStyles[editingAct] == st,
                                onClick = null
                            )
                            Text(
                                text = st.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overrideEditingAction = null }) {
                    Text("Close")
                }
            }
        )
    }
}
