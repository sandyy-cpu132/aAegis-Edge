package com.example

import com.example.core.model.ClipboardConfig
import com.example.core.model.EdgeAction
import com.example.core.model.EdgeGestureConfig
import com.example.core.model.EdgeSide
import com.example.core.model.EdgeSideConfig
import com.example.core.model.GestureType
import com.example.core.model.PrivacyShieldConfig
import com.example.core.model.RegexCleanRule
import com.example.core.security.SecureLogger
import com.example.feature.clipboard.UrlCleanerEngine
import com.example.feature.gesture.EdgeGestureClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM unit tests verifying:
 * 1. Edge Gesture vertical stroke classification & horizontal/diagonal rejection.
 * 2. Edge Gesture 4-tier context priority hierarchy (Part 5.6).
 * 3. Privacy Shield bidirectional upper-corner horizontal exit gesture recognition (Part 6.5).
 * 4. Local URL Cleaner tracking parameter removal, preservation of unrelated parameters,
 *    malformed URL resilience, and scheme-validated custom regex rules (Part 8).
 * 5. SecureLogger automatic redaction of URLs, emails, and numeric sequences (Part 3.2).
 */
class ExampleUnitTest {

    @Test
    fun `edge gesture rejects horizontal and diagonal swipes to preserve back gesture`() {
        val horizontalSwipe = EdgeGestureClassifier.classifyStroke(
            dxPx = 120f,
            dyPx = 15f,
            durationMs = 120L,
            isHoldTriggered = false,
            shortDistancePx = 50f,
            longDistancePx = 140f,
            holdDurationMs = 350,
            sensitivityPercent = 80
        )
        assertEquals(
            EdgeGestureClassifier.ClassificationResult.IgnoredHorizontalOrDiagonal,
            horizontalSwipe
        )

        val diagonalSwipe = EdgeGestureClassifier.classifyStroke(
            dxPx = 90f,
            dyPx = 110f,
            durationMs = 140L,
            isHoldTriggered = false,
            shortDistancePx = 50f,
            longDistancePx = 140f,
            holdDurationMs = 350,
            sensitivityPercent = 80
        )
        assertEquals(
            EdgeGestureClassifier.ClassificationResult.IgnoredHorizontalOrDiagonal,
            diagonalSwipe
        )
    }

    @Test
    fun `edge gesture accurately distinguishes short, long, and hold vertical strokes`() {
        val shortUp = EdgeGestureClassifier.classifyStroke(
            dxPx = 4f,
            dyPx = -75f,
            durationMs = 110L,
            isHoldTriggered = false,
            shortDistancePx = 50f,
            longDistancePx = 140f,
            holdDurationMs = 350,
            sensitivityPercent = 80
        )
        assertEquals(
            EdgeGestureClassifier.ClassificationResult.Recognized(GestureType.SHORT_SWIPE_UP),
            shortUp
        )

        val longDown = EdgeGestureClassifier.classifyStroke(
            dxPx = 6f,
            dyPx = 185f,
            durationMs = 160L,
            isHoldTriggered = false,
            shortDistancePx = 50f,
            longDistancePx = 140f,
            holdDurationMs = 350,
            sensitivityPercent = 80
        )
        assertEquals(
            EdgeGestureClassifier.ClassificationResult.Recognized(GestureType.LONG_SWIPE_DOWN),
            longDown
        )

        val holdUp = EdgeGestureClassifier.classifyStroke(
            dxPx = 5f,
            dyPx = -80f,
            durationMs = 400L,
            isHoldTriggered = true,
            shortDistancePx = 50f,
            longDistancePx = 140f,
            holdDurationMs = 350,
            sensitivityPercent = 80
        )
        assertEquals(
            EdgeGestureClassifier.ClassificationResult.Recognized(GestureType.HOLD_SWIPE_UP),
            holdUp
        )
    }

    @Test
    fun `edge gesture enforces exact 4-tier context priority order`() {
        val baseConfig = EdgeGestureConfig(
            masterEnabled = true,
            leftEdge = EdgeSideConfig(shortUpAction = EdgeAction.NOTIFICATION_SHADE),
            excludedPackages = setOf("com.game.excluded"),
            appSpecificOverrides = mapOf("com.gallery.app" to EdgeAction.PRIVACY_SHIELD_SNAPSHOT),
            temporaryModeActive = true,
            temporaryModeAction = EdgeAction.PRIVACY_SHIELD_LOCK
        )

        // Priority 1: Excluded app -> null (gestures disabled even when temporary mode is active)
        val excludedResult = EdgeGestureClassifier.resolveActionWithPriority(
            config = baseConfig,
            side = EdgeSide.LEFT,
            gestureType = GestureType.SHORT_SWIPE_UP,
            foregroundPackage = "com.game.excluded"
        )
        assertNull(excludedResult)

        // Priority 2: Temporary mode overrides app-specific and normal mode
        val tempResult = EdgeGestureClassifier.resolveActionWithPriority(
            config = baseConfig,
            side = EdgeSide.LEFT,
            gestureType = GestureType.SHORT_SWIPE_UP,
            foregroundPackage = "com.gallery.app"
        )
        assertEquals(EdgeAction.PRIVACY_SHIELD_LOCK, tempResult)

        // Priority 3: App-specific rule overrides normal mode when temporary mode is off
        val appOverrideConfig = baseConfig.copy(temporaryModeActive = false)
        val appResult = EdgeGestureClassifier.resolveActionWithPriority(
            config = appOverrideConfig,
            side = EdgeSide.LEFT,
            gestureType = GestureType.SHORT_SWIPE_UP,
            foregroundPackage = "com.gallery.app"
        )
        assertEquals(EdgeAction.PRIVACY_SHIELD_SNAPSHOT, appResult)

        // Priority 4: Normal mode executes default action for side & gesture
        val normalResult = EdgeGestureClassifier.resolveActionWithPriority(
            config = appOverrideConfig,
            side = EdgeSide.LEFT,
            gestureType = GestureType.SHORT_SWIPE_UP,
            foregroundPackage = "com.other.browser"
        )
        assertEquals(EdgeAction.NOTIFICATION_SHADE, normalResult)
    }

    @Test
    fun `privacy shield exit gesture recognizes bidirectional top-corner horizontal swipes only`() {
        val shieldConfig = PrivacyShieldConfig(
            exitGestureEnabled = true,
            exitCornerHeightDp = 68,
            exitMinSwipePercent = 55
        )

        // Valid Left-to-Right top-corner swipe
        assertTrue(
            EdgeGestureClassifier.isPrivacyShieldExitGesture(
                startX = 80f,
                startY = 40f,
                endX = 820f,
                endY = 55f,
                maxYDuringStroke = 65f,
                screenWidthPx = 1080,
                cornerHeightPx = 140f,
                shieldConfig = shieldConfig
            )
        )

        // Valid Right-to-Left top-corner swipe
        assertTrue(
            EdgeGestureClassifier.isPrivacyShieldExitGesture(
                startX = 1000f,
                startY = 45f,
                endX = 180f,
                endY = 50f,
                maxYDuringStroke = 60f,
                screenWidthPx = 1080,
                cornerHeightPx = 140f,
                shieldConfig = shieldConfig
            )
        )

        // Invalid: swipe in middle of screen (not upper corner)
        assertFalse(
            EdgeGestureClassifier.isPrivacyShieldExitGesture(
                startX = 80f,
                startY = 600f,
                endX = 900f,
                endY = 610f,
                maxYDuringStroke = 610f,
                screenWidthPx = 1080,
                cornerHeightPx = 140f,
                shieldConfig = shieldConfig
            )
        )
    }

    @Test
    fun `url cleaner removes tracking parameters while preserving unrelated parameters and fragments`() {
        val config = ClipboardConfig(
            enabled = true,
            removeBuiltInTracking = true,
            customQueryParameters = listOf("ref", "aff_id"),
            customRegexRules = listOf(
                RegexCleanRule(
                    id = "r1",
                    name = "Strip /ref= segment",
                    pattern = "/ref=[^/?#]+",
                    replacement = "",
                    enabled = true
                )
            )
        )

        val dirty = "https://shop.example.com/item/ref=sr_1_2?id=9001&utm_source=ig&fbclid=IwAR99&page=2&ref=banner#specs"
        val res = UrlCleanerEngine.cleanTextOrUrl(dirty, config)

        assertTrue(res.wasModified)
        assertEquals("https://shop.example.com/item?id=9001&page=2#specs", res.cleanedOutput)
        assertTrue(res.removedParameters.containsAll(listOf("utm_source", "fbclid", "ref")))
    }

    @Test
    fun `url cleaner rejects unsafe scheme injection in custom regex`() {
        val unsafeRuleConfig = ClipboardConfig(
            customRegexRules = listOf(
                RegexCleanRule(
                    id = "bad",
                    name = "Unsafe scheme rewrite",
                    pattern = "^https://",
                    replacement = "javascript://",
                    enabled = true
                )
            )
        )
        val input = "https://example.com/article?utm_medium=social"
        val res = UrlCleanerEngine.cleanTextOrUrl(input, unsafeRuleConfig)
        // Must strip utm_medium but reject the unsafe javascript:// regex rewrite
        assertEquals("https://example.com/article", res.cleanedOutput)
    }

    @Test
    fun `secure logger redacts URLs, emails, and numeric sequences`() {
        val raw = "User sandy@example.com visited https://secret.site/token?k=1 with PIN 987654"
        val sanitized = SecureLogger.sanitizeMessage(raw)
        assertFalse(sanitized.contains("sandy@example.com"))
        assertFalse(sanitized.contains("https://secret.site"))
        assertFalse(sanitized.contains("987654"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
        assertTrue(sanitized.contains("[REDACTED_URL]"))
        assertTrue(sanitized.contains("[REDACTED_NUM]"))
    }
}
