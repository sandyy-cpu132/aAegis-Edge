package com.example.feature.gesture

import com.example.core.model.EdgeAction
import com.example.core.model.EdgeGestureConfig
import com.example.core.model.EdgeSide
import com.example.core.model.EdgeSideConfig
import com.example.core.model.GestureType
import com.example.core.model.PrivacyShieldConfig
import kotlin.math.abs

/**
 * Pure, deterministic gesture math & context-priority engine.
 *
 * Enforces:
 * 1. Vertical-only edge strokes (rejects horizontal/diagonal swipes so Android's
 *    normal horizontal back gesture is preserved).
 * 2. Short swipe, Long swipe, and Swipe-and-Hold classification.
 * 3. Exact 4-tier context priority (Excluded App -> Temporary Mode -> App Override -> Normal).
 * 4. Bidirectional Upper-Corner Horizontal Exit Gesture for Privacy Shield.
 */
object EdgeGestureClassifier {

    /**
     * Minimum ratio of |dy| / |dx| required to classify a movement as vertical along the edge.
     * 1.85 corresponds to an angle within ~28 degrees of pure vertical.
     */
    private const val MIN_VERTICAL_RATIO = 1.85f

    sealed class ClassificationResult {
        data object IgnoredHorizontalOrDiagonal : ClassificationResult()
        data object BelowThreshold : ClassificationResult()
        data class Recognized(val gestureType: GestureType) : ClassificationResult()
    }

    fun classifyStroke(
        dxPx: Float,
        dyPx: Float,
        durationMs: Long,
        isHoldTriggered: Boolean,
        shortDistancePx: Float,
        longDistancePx: Float,
        holdDurationMs: Int,
        sensitivityPercent: Int
    ): ClassificationResult {
        val absDx = abs(dxPx)
        val absDy = abs(dyPx)

        // Sensitivity scales required distance between 1.25x (at 30%) and 0.80x (at 100%)
        val sensitivityScale = 1.25f - ((sensitivityPercent.coerceIn(30, 100) - 30) / 70f) * 0.45f
        val effectiveShortPx = (shortDistancePx * sensitivityScale).coerceAtLeast(24f)
        val effectiveLongPx = (longDistancePx * sensitivityScale).coerceAtLeast(effectiveShortPx + 28f)

        // Reject horizontal or diagonal movements immediately
        if (absDx > 16f && absDy < absDx * MIN_VERTICAL_RATIO) {
            return ClassificationResult.IgnoredHorizontalOrDiagonal
        }

        if (absDy < effectiveShortPx) {
            return ClassificationResult.BelowThreshold
        }

        val isUpward = dyPx < 0f
        val isHold = isHoldTriggered || durationMs >= holdDurationMs

        val gestureType = when {
            isHold -> if (isUpward) GestureType.HOLD_SWIPE_UP else GestureType.HOLD_SWIPE_DOWN
            absDy >= effectiveLongPx -> if (isUpward) GestureType.LONG_SWIPE_UP else GestureType.LONG_SWIPE_DOWN
            else -> if (isUpward) GestureType.SHORT_SWIPE_UP else GestureType.SHORT_SWIPE_DOWN
        }
        return ClassificationResult.Recognized(gestureType)
    }

    /**
     * Resolves the action to execute according to Part 5.6 exact context priority order:
     * 1. Excluded app -> null (gestures disabled)
     * 2. Active temporary mode -> temporaryModeAction
     * 3. Matching application-specific rule -> assigned action
     * 4. Normal mode -> default action for side & gestureType
     */
    fun resolveActionWithPriority(
        config: EdgeGestureConfig,
        side: EdgeSide,
        gestureType: GestureType,
        foregroundPackage: String?
    ): EdgeAction? {
        if (!config.masterEnabled) return null

        val sideConfig: EdgeSideConfig = when (side) {
            EdgeSide.LEFT -> config.leftEdge
            EdgeSide.RIGHT -> config.rightEdge
        }
        if (!sideConfig.enabled) return null

        // Priority 1: Excluded application -> gestures disabled
        if (!foregroundPackage.isNullOrBlank() && config.excludedPackages.contains(foregroundPackage)) {
            return null
        }

        // Priority 2: Active temporary mode -> temporary mode's configured gesture action
        if (config.temporaryModeActive) {
            return config.temporaryModeAction
        }

        // Priority 3: Matching application-specific rule -> assigned action
        if (!foregroundPackage.isNullOrBlank()) {
            val appOverride = config.appSpecificOverrides[foregroundPackage]
            if (appOverride != null && appOverride != EdgeAction.NONE) {
                return appOverride
            }
        }

        // Priority 4: Normal mode -> side's configured action for this gesture type
        val action = sideConfig.actionFor(gestureType)
        return if (action == EdgeAction.NONE) null else action
    }

    /**
     * Evaluates the Privacy Shield Upper-Corner Horizontal Exit Gesture (Part 6.5):
     * - Must start near the top-left clock area (or top-right menu area) within [cornerHeightPx].
     * - Must swipe horizontally across toward the opposite top corner covering at least
     *   [minSwipePercent]% of [screenWidthPx] while remaining inside the upper strip.
     */
    fun isPrivacyShieldExitGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        maxYDuringStroke: Float,
        screenWidthPx: Int,
        cornerHeightPx: Float,
        shieldConfig: PrivacyShieldConfig
    ): Boolean {
        if (!shieldConfig.exitGestureEnabled || screenWidthPx <= 0) return false

        val maxVerticalDrift = cornerHeightPx * 1.45f
        if (startY < 0f || startY > cornerHeightPx || endY > maxVerticalDrift || maxYDuringStroke > maxVerticalDrift) {
            return false
        }

        val widthF = screenWidthPx.toFloat()
        val cornerZoneWidth = widthF * 0.28f
        val requiredSpan = widthF * (shieldConfig.exitMinSwipePercent.coerceIn(35, 85) / 100f)
        val dx = endX - startX

        val startedTopLeft = startX <= cornerZoneWidth
        val startedTopRight = startX >= (widthF - cornerZoneWidth)

        if (startedTopLeft && dx >= requiredSpan) {
            return true
        }
        if (startedTopRight && -dx >= requiredSpan) {
            return true
        }
        return false
    }
}
