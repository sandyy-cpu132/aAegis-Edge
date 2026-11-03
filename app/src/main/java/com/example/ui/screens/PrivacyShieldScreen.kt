package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.PrivacyShieldConfig
import com.example.core.model.ShieldMode
import com.example.feature.gesture.EdgeGestureClassifier
import com.example.ui.components.CategoryTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingsGroupCard
import com.example.ui.components.SettingsSliderRow
import com.example.ui.components.SettingsToggleRow
import kotlin.math.max

@Composable
fun PrivacyShieldScreen(
    config: PrivacyShieldConfig,
    accessibilityActive: Boolean,
    onUpdate: ((PrivacyShieldConfig) -> PrivacyShieldConfig) -> Unit,
    onTriggerShield: (ShieldMode) -> Unit,
    onTestHaptic: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var exitTestStatus by remember {
        mutableStateOf("Swipe horizontally from top-left clock area to top-right (or reverse) to test the exit gesture.")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTopBar(
            title = "Privacy Shield",
            subtitle = "Frozen full-screen snapshot with mandatory native lock fallback",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("privacy_shield_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Enable Privacy Shield",
                        subtitle = "Allow edge gestures and dashboard controls to invoke Privacy Shield",
                        checked = config.enabled,
                        onCheckedChange = { en -> onUpdate { it.copy(enabled = en) } },
                        testTag = "toggle_shield_enabled"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onTriggerShield(ShieldMode.FROZEN_SNAPSHOT) },
                            enabled = accessibilityActive && config.enabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_activate_snapshot")
                        ) {
                            Text("Test Snapshot Freeze")
                        }
                        FilledTonalButton(
                            onClick = { onTriggerShield(ShieldMode.NATIVE_LOCK) },
                            enabled = accessibilityActive && config.enabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_activate_nativelock")
                        ) {
                            Text("Test Native Lock")
                        }
                    }
                    if (!accessibilityActive) {
                        Text(
                            text = "Note: Enable Aegis Accessibility Service on the Dashboard to activate live screen freeze or native lock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Protection Mode Selection
            item {
                SectionHeader("Principal Protection Mode")
                SettingsGroupCard {
                    ShieldMode.entries.forEach { mode ->
                        val selected = config.defaultMode == mode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdate { it.copy(defaultMode = mode) } }
                                .testTag("mode_option_${mode.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(modifier = Modifier.padding(start = 10.dp)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mandatory Failure Fallback Policy
            item {
                SectionHeader("Mandatory Failure Fallback Policy (Non-Disableable)")
                SettingsGroupCard {
                    Text(
                        text = "Safety Guarantee: If screenshot capture fails (e.g., FLAG_SECURE banking/private window), if the bitmap is invalid, or if the full-screen overlay cannot establish touch blocking within the timeout below, Aegis immediately invokes the real Android lock screen. It never substitutes a black screen or blur.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SettingsSliderRow(
                        title = "Watchdog Fallback Timeout",
                        valueLabel = "${config.fallbackTimeoutMs} ms",
                        value = config.fallbackTimeoutMs.toFloat(),
                        valueRange = 500f..2500f,
                        onValueChange = { v ->
                            onUpdate { it.copy(fallbackTimeoutMs = v.toLong()) }
                        },
                        testTag = "slider_fallback_timeout"
                    )
                }
            }

            // Upper-Corner Horizontal Exit Gesture & Tester
            item {
                SectionHeader("Upper-Corner Horizontal Exit Gesture")
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Enable Upper-Corner Exit Gesture",
                        subtitle = "Swipe horizontally across the top bar (left-to-right or right-to-left), then verify fingerprint to unfreeze",
                        checked = config.exitGestureEnabled,
                        onCheckedChange = { en -> onUpdate { it.copy(exitGestureEnabled = en) } },
                        testTag = "toggle_exit_gesture"
                    )
                    SettingsSliderRow(
                        title = "Top Corner Strip Height",
                        valueLabel = "${config.exitCornerHeightDp} dp",
                        value = config.exitCornerHeightDp.toFloat(),
                        valueRange = 44f..120f,
                        onValueChange = { v ->
                            onUpdate { it.copy(exitCornerHeightDp = v.toInt()) }
                        },
                        testTag = "slider_exit_corner_height"
                    )
                    SettingsSliderRow(
                        title = "Required Horizontal Span",
                        valueLabel = "${config.exitMinSwipePercent}% of width",
                        value = config.exitMinSwipePercent.toFloat(),
                        valueRange = 35f..85f,
                        onValueChange = { v ->
                            onUpdate { it.copy(exitMinSwipePercent = v.toInt()) }
                        },
                        testTag = "slider_exit_span"
                    )

                    Text(
                        text = exitTestStatus,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ExitGestureInteractiveBox(
                        config = config,
                        onResult = { matched ->
                            if (matched) {
                                onTestHaptic()
                                exitTestStatus = "Exit gesture recognized! BiometricPrompt would now verify your fingerprint."
                            } else {
                                exitTestStatus = "Not an exit gesture: start in top-left or top-right corner and swipe across the top bar."
                            }
                        }
                    )
                }
            }

            // Biometric & Session Behaviour
            item {
                SectionHeader("Authentication & Session Lifecycle")
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Strict Class 3 Biometric Only (Fingerprint Preferred)",
                        subtitle = "Require Class 3 BIOMETRIC_STRONG (fingerprint sensor on iQOO Neo10R) without weak face or PIN fallback when enrolled",
                        checked = config.strictBiometricOnly,
                        onCheckedChange = { strict ->
                            onUpdate { it.copy(strictBiometricOnly = strict) }
                        },
                        testTag = "toggle_strict_biometric"
                    )
                    SettingsToggleRow(
                        title = "Invoke Native Lock When Screen Turns Off",
                        subtitle = "Automatically recycle in-memory snapshot and lock device when display sleeps",
                        checked = config.lockOnScreenOff,
                        onCheckedChange = { lock ->
                            onUpdate { it.copy(lockOnScreenOff = lock) }
                        },
                        testTag = "toggle_lock_screen_off"
                    )
                    SettingsToggleRow(
                        title = "Show Subtle Frozen Hint Pill",
                        subtitle = "Optional subtle reminder pill at top of frozen snapshot (leave off for 100% identical visual freeze)",
                        checked = config.showExitHintBanner,
                        onCheckedChange = { show ->
                            onUpdate { it.copy(showExitHintBanner = show) }
                        },
                        testTag = "toggle_exit_hint_banner"
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ExitGestureInteractiveBox(
    config: PrivacyShieldConfig,
    onResult: (Boolean) -> Unit
) {
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    var curX by remember { mutableFloatStateOf(0f) }
    var curY by remember { mutableFloatStateOf(0f) }
    var maxY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .testTag("exit_gesture_tester_box")
            .pointerInput(config) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        startY = offset.y
                        curX = offset.x
                        curY = offset.y
                        maxY = offset.y
                        dragging = true
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        curX += amount.x
                        curY += amount.y
                        maxY = max(maxY, curY)
                    },
                    onDragEnd = {
                        dragging = false
                        val matched = EdgeGestureClassifier.isPrivacyShieldExitGesture(
                            startX = startX,
                            startY = startY,
                            endX = curX,
                            endY = curY,
                            maxYDuringStroke = maxY,
                            screenWidthPx = size.width,
                            cornerHeightPx = size.height * 0.75f,
                            shieldConfig = config
                        )
                        onResult(matched)
                    },
                    onDragCancel = {
                        dragging = false
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val zoneH = h * 0.72f
            drawRoundRect(
                color = outline.copy(alpha = 0.35f),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Highlight top corner start boxes
            val cornerW = w * 0.25f
            drawRoundRect(
                color = primary.copy(alpha = 0.22f),
                topLeft = Offset(0f, 0f),
                size = Size(cornerW, zoneH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )
            drawRoundRect(
                color = primary.copy(alpha = 0.22f),
                topLeft = Offset(w - cornerW, 0f),
                size = Size(cornerW, zoneH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )
            if (dragging) {
                drawLine(
                    color = primary,
                    start = Offset(startX, startY),
                    end = Offset(curX, curY),
                    strokeWidth = 4.dp.toPx()
                )
                drawCircle(color = primary, radius = 8.dp.toPx(), center = Offset(curX, curY))
            }
        }
    }
}
