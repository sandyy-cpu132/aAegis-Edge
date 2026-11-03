package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.EdgeAction
import com.example.core.model.EdgeGestureConfig
import com.example.core.model.EdgeSide
import com.example.core.model.EdgeSideConfig
import com.example.core.model.GestureType
import com.example.core.security.SystemSecurityAuditor
import com.example.feature.gesture.EdgeGestureClassifier
import com.example.ui.components.CategoryTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingsGroupCard
import com.example.ui.components.SettingsSliderRow
import com.example.ui.components.SettingsToggleRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgeGestureScreen(
    config: EdgeGestureConfig,
    installedApps: List<SystemSecurityAuditor.InstalledAppItem>,
    onLoadApps: () -> Unit,
    onUpdateMaster: ((EdgeGestureConfig) -> EdgeGestureConfig) -> Unit,
    onUpdateSide: (EdgeSide, (EdgeSideConfig) -> EdgeSideConfig) -> Unit,
    onToggleExcludedApp: (String) -> Unit,
    onTestHaptic: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedSide by remember { mutableStateOf(EdgeSide.LEFT) }
    var editingGestureType by remember { mutableStateOf<GestureType?>(null) }
    var showExclusionsDialog by remember { mutableStateOf(false) }

    val activeSideConfig = if (selectedSide == EdgeSide.LEFT) config.leftEdge else config.rightEdge

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTopBar(
            title = "Edge Gestures",
            subtitle = "Vertical-only left & right edge strokes (preserves horizontal back)",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("edge_gesture_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Enable Edge Gesture Engine",
                        subtitle = "Activate vertical edge gesture recognition via Accessibility overlay",
                        checked = config.masterEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateMaster { it.copy(masterEnabled = enabled) }
                        },
                        testTag = "toggle_edge_master"
                    )
                    SettingsToggleRow(
                        title = "Disable in Landscape Orientation",
                        subtitle = "Automatically suspend edge zones during full-screen video or gaming",
                        checked = config.disableInLandscape,
                        onCheckedChange = { disable ->
                            onUpdateMaster { it.copy(disableInLandscape = disable) }
                        },
                        testTag = "toggle_disable_landscape"
                    )
                    SettingsToggleRow(
                        title = "Temporary Override Mode",
                        subtitle = "Priority #2 context: override all edge gestures with ${config.temporaryModeAction.title}",
                        checked = config.temporaryModeActive,
                        onCheckedChange = { temp ->
                            onUpdateMaster { it.copy(temporaryModeActive = temp) }
                        },
                        testTag = "toggle_temp_mode"
                    )
                }
            }

            // Side Selector Tabs (Left vs Right Independent Configuration)
            item {
                SectionHeader("Side Selection (Independent Configuration)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = selectedSide == EdgeSide.LEFT,
                        onClick = { selectedSide = EdgeSide.LEFT },
                        label = { Text("Left Screen Edge") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_left_edge")
                    )
                    FilterChip(
                        selected = selectedSide == EdgeSide.RIGHT,
                        onClick = { selectedSide = EdgeSide.RIGHT },
                        label = { Text("Right Screen Edge") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_right_edge")
                    )
                }
            }

            // Live Animated & Interactive Gesture Preview Sandbox
            item {
                SectionHeader("Live Interactive Gesture Preview (${selectedSide.name})")
                LiveEdgeGesturePreviewSandbox(
                    side = selectedSide,
                    sideConfig = activeSideConfig,
                    onRecognized = { onTestHaptic() }
                )
            }

            // Zone Geometry & Sensitivity Sliders for Selected Side
            item {
                SectionHeader("${selectedSide.name} Edge Zone & Thresholds")
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Enable ${selectedSide.name.lowercase().replaceFirstChar { it.uppercase() }} Edge",
                        subtitle = "Independently toggle vertical recognition on the ${selectedSide.name.lowercase()} side",
                        checked = activeSideConfig.enabled,
                        onCheckedChange = { en ->
                            onUpdateSide(selectedSide) { it.copy(enabled = en) }
                        },
                        testTag = "toggle_side_enabled"
                    )
                    SettingsSliderRow(
                        title = "Trigger Zone Width",
                        valueLabel = "${activeSideConfig.triggerWidthDp} dp",
                        value = activeSideConfig.triggerWidthDp.toFloat(),
                        valueRange = 10f..40f,
                        onValueChange = { v ->
                            onUpdateSide(selectedSide) { it.copy(triggerWidthDp = v.toInt()) }
                        },
                        testTag = "slider_trigger_width"
                    )
                    SettingsSliderRow(
                        title = "Vertical Start Position",
                        valueLabel = "${activeSideConfig.verticalStartPercent}% from top",
                        value = activeSideConfig.verticalStartPercent.toFloat(),
                        valueRange = 5f..75f,
                        onValueChange = { v ->
                            onUpdateSide(selectedSide) { it.copy(verticalStartPercent = v.toInt()) }
                        },
                        testTag = "slider_vertical_start"
                    )
                    SettingsSliderRow(
                        title = "Trigger Zone Height",
                        valueLabel = "${activeSideConfig.verticalHeightPercent}% of height",
                        value = activeSideConfig.verticalHeightPercent.toFloat(),
                        valueRange = 15f..80f,
                        onValueChange = { v ->
                            onUpdateSide(selectedSide) { it.copy(verticalHeightPercent = v.toInt()) }
                        },
                        testTag = "slider_vertical_height"
                    )
                    SettingsSliderRow(
                        title = "Short Swipe Distance",
                        valueLabel = "${activeSideConfig.shortSwipeDistanceDp} dp",
                        value = activeSideConfig.shortSwipeDistanceDp.toFloat(),
                        valueRange = 20f..75f,
                        onValueChange = { v ->
                            onUpdateSide(selectedSide) { it.copy(shortSwipeDistanceDp = v.toInt()) }
                        },
                        testTag = "slider_short_distance"
                    )
                    SettingsSliderRow(
                        title = "Long Swipe Distance",
                        valueLabel = "${activeSideConfig.longSwipeDistanceDp} dp",
                        value = activeSideConfig.longSwipeDistanceDp.toFloat(),
                        valueRange = 65f..170f,
                        onValueChange = { v ->
                            onUpdateSide(selectedSide) { it.copy(longSwipeDistanceDp = v.toInt()) }
                        },
                        testTag = "slider_long_distance"
                    )
                    SettingsSliderRow(
                        title = "Swipe-and-Hold Duration",
                        valueLabel = "${activeSideConfig.holdDurationMs} ms",
                        value = activeSideConfig.holdDurationMs.toFloat(),
                        valueRange = 180f..850f,
                        onValueChange = { v ->
                            onUpdateSide(selectedSide) { it.copy(holdDurationMs = v.toInt()) }
                        },
                        testTag = "slider_hold_duration"
                    )
                    SettingsSliderRow(
                        title = "Vertical Sensitivity",
                        valueLabel = "${activeSideConfig.sensitivityPercent}%",
                        value = activeSideConfig.sensitivityPercent.toFloat(),
                        valueRange = 30f..100f,
                        onValueChange = { v ->
                            onUpdateSide(selectedSide) { it.copy(sensitivityPercent = v.toInt()) }
                        },
                        testTag = "slider_sensitivity"
                    )
                }
            }

            // Action Mapping for the 6 Vertical Gestures
            item {
                SectionHeader("${selectedSide.name} Action Mapping")
                SettingsGroupCard {
                    GestureType.entries.forEach { gestureType ->
                        val assigned = activeSideConfig.actionFor(gestureType)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingGestureType = gestureType }
                                .testTag("action_row_${gestureType.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = gestureType.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = assigned.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Change",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Application Exclusions (Priority #1 in Context Hierarchy)
            item {
                SectionHeader("Per-App Exclusions (Context Priority #1)")
                SettingsGroupCard {
                    Text(
                        text = "When an excluded app is in the foreground, all edge gesture overlays are automatically suspended.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (config.excludedPackages.isEmpty()) {
                            Text(
                                text = "No apps excluded",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            config.excludedPackages.forEach { pkg ->
                                FilterChip(
                                    selected = true,
                                    onClick = { onToggleExcludedApp(pkg) },
                                    label = { Text(pkg.substringAfterLast('.')) }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            onLoadApps()
                            showExclusionsDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_manage_exclusions")
                    ) {
                        Text("Select Installed Apps to Exclude (${config.excludedPackages.size})")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Action Picker Dialog
    val currentEditingType = editingGestureType
    if (currentEditingType != null) {
        AlertDialog(
            onDismissRequest = { editingGestureType = null },
            title = { Text("Assign Action: ${currentEditingType.displayName}") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(EdgeAction.entries.size) { idx ->
                        val act = EdgeAction.entries[idx]
                        val isSelected = activeSideConfig.actionFor(currentEditingType) == act
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSide(selectedSide) { it.withAction(currentEditingType, act) }
                                    editingGestureType = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(act.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    act.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingGestureType = null }) {
                    Text("Close")
                }
            }
        )
    }

    // App Exclusions Picker Dialog
    if (showExclusionsDialog) {
        AlertDialog(
            onDismissRequest = { showExclusionsDialog = false },
            title = { Text("Exclude Applications") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (installedApps.isEmpty()) {
                        item {
                            Text(
                                "No third-party launcher apps found in this environment.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        items(installedApps.size) { index ->
                            val item = installedApps[index]
                            val checked = config.excludedPackages.contains(item.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleExcludedApp(item.packageName) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(item.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        item.packageName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExclusionsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun LiveEdgeGesturePreviewSandbox(
    side: EdgeSide,
    sideConfig: EdgeSideConfig,
    onRecognized: () -> Unit
) {
    val density = LocalDensity.current.density
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var currentX by remember { mutableFloatStateOf(0f) }
    var currentY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var previewFeedbackText by remember {
        mutableStateOf("Swipe vertically inside the highlighted ${side.name.lowercase()} edge zone to test Short, Long, or Hold gestures.")
    }

    LaunchedEffect(side) {
        previewFeedbackText = "Swipe vertically inside the highlighted ${side.name.lowercase()} edge zone to test Short, Long, or Hold gestures."
    }

    SettingsGroupCard {
        Text(
            text = previewFeedbackText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .testTag("edge_gesture_preview_canvas")
                .pointerInput(side, sideConfig) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            dragStartY = offset.y
                            currentX = offset.x
                            currentY = offset.y
                            dragStartTime = System.currentTimeMillis()
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentX += dragAmount.x
                            currentY += dragAmount.y
                        },
                        onDragEnd = {
                            isDragging = false
                            val elapsed = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(1L)
                            val dx = currentX - dragStartX
                            val dy = currentY - dragStartY
                            val result = EdgeGestureClassifier.classifyStroke(
                                dxPx = dx,
                                dyPx = dy,
                                durationMs = elapsed,
                                isHoldTriggered = elapsed >= sideConfig.holdDurationMs,
                                shortDistancePx = sideConfig.shortSwipeDistanceDp * density,
                                longDistancePx = sideConfig.longSwipeDistanceDp * density,
                                holdDurationMs = sideConfig.holdDurationMs,
                                sensitivityPercent = sideConfig.sensitivityPercent
                            )
                            previewFeedbackText = when (result) {
                                is EdgeGestureClassifier.ClassificationResult.Recognized -> {
                                    onRecognized()
                                    val action = sideConfig.actionFor(result.gestureType)
                                    "Detected: ${result.gestureType.displayName} → ${action.title}"
                                }
                                EdgeGestureClassifier.ClassificationResult.IgnoredHorizontalOrDiagonal ->
                                    "Ignored: Horizontal / Diagonal swipe (Android Back navigation preserved)"
                                EdgeGestureClassifier.ClassificationResult.BelowThreshold ->
                                    "Below activation distance (${sideConfig.shortSwipeDistanceDp} dp required)"
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        ) {
            val cyan = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.secondary
            val outlineColor = MaterialTheme.colorScheme.outline

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw phone screen frame
                drawRoundRect(
                    color = outlineColor.copy(alpha = 0.35f),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw active vertical edge trigger zone
                val zoneW = (sideConfig.triggerWidthDp * 1.4f).dp.toPx().coerceAtMost(w * 0.28f)
                val zoneTop = (sideConfig.verticalStartPercent / 100f) * h
                val zoneH = ((sideConfig.verticalHeightPercent / 100f) * h).coerceAtMost(h - zoneTop)
                val zoneLeft = if (side == EdgeSide.LEFT) 0f else w - zoneW

                drawRoundRect(
                    color = cyan.copy(alpha = 0.24f),
                    topLeft = Offset(zoneLeft, zoneTop),
                    size = Size(zoneW, zoneH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                drawRoundRect(
                    color = cyan,
                    topLeft = Offset(zoneLeft, zoneTop),
                    size = Size(zoneW, zoneH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw Short & Long threshold reference lines from center of zone
                val refCenterY = zoneTop + zoneH / 2f
                val shortPx = (sideConfig.shortSwipeDistanceDp.dp.toPx() * 0.45f)
                val longPx = (sideConfig.longSwipeDistanceDp.dp.toPx() * 0.45f)

                drawLine(
                    color = secondary.copy(alpha = 0.5f),
                    start = Offset(w * 0.2f, (refCenterY - shortPx).coerceAtLeast(0f)),
                    end = Offset(w * 0.8f, (refCenterY - shortPx).coerceAtLeast(0f)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                drawLine(
                    color = cyan.copy(alpha = 0.65f),
                    start = Offset(w * 0.15f, (refCenterY - longPx).coerceAtLeast(0f)),
                    end = Offset(w * 0.85f, (refCenterY - longPx).coerceAtLeast(0f)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f))
                )

                // Draw live user stroke
                if (isDragging) {
                    drawCircle(
                        color = cyan,
                        radius = 7.dp.toPx(),
                        center = Offset(dragStartX, dragStartY)
                    )
                    drawLine(
                        color = cyan,
                        start = Offset(dragStartX, dragStartY),
                        end = Offset(currentX, currentY),
                        strokeWidth = 4.dp.toPx()
                    )
                    drawCircle(
                        color = secondary,
                        radius = 10.dp.toPx(),
                        center = Offset(currentX, currentY)
                    )
                }
            }
        }
    }
}
