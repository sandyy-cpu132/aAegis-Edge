package com.example.feature.feedback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.example.core.model.AnimationPreset
import com.example.core.model.EdgeAction
import com.example.core.model.EdgeSide
import com.example.core.model.FeedbackConfig
import com.example.core.model.FeedbackStyle
import com.example.core.security.SecureLogger

/**
 * Lightweight, strictly temporary action feedback engine (Part 7).
 *
 * Rules enforced:
 * - Zero permanent overlay or island.
 * - Visual overlay is added to WindowManager only at the instant an action triggers,
 *   animates for `durationMs` (default 420ms), and immediately removes itself on completion.
 * - Respects display cutout geometry for temporary punch-hole/notch ring glow.
 */
class FeedbackEngine(private val context: Context) {

    private var activeVisualView: View? = null
    private var activeAnimator: ValueAnimator? = null

    fun triggerFeedback(
        action: EdgeAction,
        side: EdgeSide,
        config: FeedbackConfig,
        windowManager: WindowManager?
    ) {
        val style = config.styleFor(action)
        if (style == FeedbackStyle.SILENT) return

        val wantsHaptic = style == FeedbackStyle.HAPTIC_ONLY ||
            style == FeedbackStyle.HAPTIC_AND_EDGE ||
            style == FeedbackStyle.HAPTIC_AND_NOTCH

        val wantsNotch = style == FeedbackStyle.NOTCH_GLOW ||
            style == FeedbackStyle.HAPTIC_AND_NOTCH

        val wantsEdge = style == FeedbackStyle.EDGE_LIGHTING ||
            style == FeedbackStyle.HAPTIC_AND_EDGE

        if (wantsHaptic) {
            performHapticPulse(config.hapticStrengthPercent)
        }

        if ((wantsNotch || wantsEdge) && windowManager != null) {
            showTemporaryVisualOverlay(
                windowManager = windowManager,
                isNotchGlow = wantsNotch,
                side = side,
                config = config
            )
        }
    }

    fun performHapticPulse(strengthPercent: Int) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            val amplitude = ((strengthPercent.coerceIn(10, 100) / 100f) * 255f)
                .toInt()
                .coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(24L, amplitude)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            SecureLogger.warn("FeedbackEngine", "Haptic pulse skipped")
        }
    }

    private fun showTemporaryVisualOverlay(
        windowManager: WindowManager,
        isNotchGlow: Boolean,
        side: EdgeSide,
        config: FeedbackConfig
    ) {
        clearVisualOverlay(windowManager)

        val parsedColor = try {
            Color.parseColor(config.accentColorHex)
        } catch (_: Exception) {
            Color.parseColor("#00E5FF")
        }

        val density = context.resources.displayMetrics.density
        val overlayView = TemporaryFeedbackView(
            context = context,
            isNotchGlow = isNotchGlow,
            side = side,
            baseColor = parsedColor,
            preset = config.animationPreset,
            notchRadiusPx = config.notchRadiusDp * density,
            notchOffsetYPx = config.notchVerticalOffsetDp * density
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(overlayView, params)
            activeVisualView = overlayView

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = config.durationMs.coerceIn(180, 1000).toLong()
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    overlayView.progress = anim.animatedValue as Float
                    overlayView.invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        clearVisualOverlay(windowManager)
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        clearVisualOverlay(windowManager)
                    }
                })
            }
            activeAnimator = animator
            animator.start()
        } catch (e: Exception) {
            SecureLogger.warn("FeedbackEngine", "Temporary visual feedback overlay could not attach")
            clearVisualOverlay(windowManager)
        }
    }

    fun clearVisualOverlay(windowManager: WindowManager?) {
        activeAnimator?.let { anim ->
            anim.removeAllListeners()
            anim.cancel()
        }
        activeAnimator = null
        val view = activeVisualView
        activeVisualView = null
        if (view != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
    }

    private class TemporaryFeedbackView(
        context: Context,
        private val isNotchGlow: Boolean,
        private val side: EdgeSide,
        private val baseColor: Int,
        private val preset: AnimationPreset,
        private val notchRadiusPx: Float,
        private val notchOffsetYPx: Float
    ) : View(context) {

        var progress: Float = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            // Bell-curve alpha envelope that starts bright and cleanly fades to 0
            val alphaFactor = when (preset) {
                AnimationPreset.PULSE -> (1f - progress) * (1f - progress)
                AnimationPreset.BREATHE -> kotlin.math.sin(progress * Math.PI).toFloat()
                AnimationPreset.RIPPLE_ARC -> (1f - progress)
            }.coerceIn(0f, 1f)

            val alphaInt = (alphaFactor * 235).toInt().coerceIn(0, 255)
            if (alphaInt <= 2) return

            if (isNotchGlow) {
                // Detect actual display cutout center if reported by Android, else top-center punch-hole
                var centerX = w / 2f
                var centerY = notchOffsetYPx + notchRadiusPx
                var radius = notchRadiusPx

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val cutout = rootWindowInsets?.displayCutout
                    val rect = cutout?.boundingRects?.firstOrNull()
                    if (rect != null && !rect.isEmpty) {
                        centerX = rect.exactCenterX()
                        centerY = rect.exactCenterY()
                        radius = (kotlin.math.max(rect.width(), rect.height()) / 2f)
                            .coerceAtLeast(notchRadiusPx)
                    }
                }

                val expandedRadius = radius + (progress * radius * 0.65f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (6f * resources.displayMetrics.density) * (1f - progress * 0.4f)
                paint.shader = null
                paint.color = Color.argb(
                    alphaInt,
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
                canvas.drawCircle(centerX, centerY, expandedRadius, paint)
            } else {
                // Temporary luminous edge bar along the triggered vertical edge
                val density = resources.displayMetrics.density
                val barThickness = 8f * density * (1f - progress * 0.35f)
                val centerY = h * 0.5f
                val halfSpan = (h * 0.22f) * (0.65f + progress * 0.55f)
                val topY = (centerY - halfSpan).coerceAtLeast(0f)
                val bottomY = (centerY + halfSpan).coerceAtMost(h)

                val colorWithAlpha = Color.argb(
                    alphaInt,
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
                paint.style = Paint.Style.FILL
                paint.shader = LinearGradient(
                    0f,
                    topY,
                    0f,
                    bottomY,
                    intArrayOf(Color.TRANSPARENT, colorWithAlpha, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                if (side == EdgeSide.LEFT) {
                    canvas.drawRect(0f, topY, barThickness, bottomY, paint)
                } else {
                    canvas.drawRect(w - barThickness, topY, w, bottomY, paint)
                }
            }
        }
    }
}
