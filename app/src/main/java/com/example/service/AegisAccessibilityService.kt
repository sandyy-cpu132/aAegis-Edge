package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.core.model.AppConfiguration
import com.example.core.model.EdgeAction
import com.example.core.model.EdgeSide
import com.example.core.model.EdgeSideConfig
import com.example.core.model.ShieldMode
import com.example.core.security.BiometricAuthActivity
import com.example.core.security.SecureLogger
import com.example.core.storage.EncryptedStorageRepository
import com.example.feature.feedback.FeedbackEngine
import com.example.feature.gesture.EdgeGestureClassifier
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * System-bound AccessibilityService powering:
 * 1. Vertical Edge Gesture Engine (Left & Right independent trigger overlays).
 * 2. Privacy Shield (Mode A: Native Android Lock, Mode B: Full-screen Frozen Snapshot with
 *    mandatory immediate fallback to Native Android Lock on any capture/overlay failure).
 * 3. Temporary Action Feedback Engine (Haptics, Notch Glow, Edge Lighting).
 *
 * Security & Performance guarantees:
 * - Bound strictly with `android.permission.BIND_ACCESSIBILITY_SERVICE`.
 * - Event-driven only: zero polling loops, zero wake locks, zero text/keystroke inspection.
 * - Preserves normal Android horizontal back gestures by filtering strictly for vertical edge strokes.
 */
class AegisAccessibilityService : AccessibilityService() {

    companion object {
        private val _serviceConnected = MutableStateFlow(false)
        val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

        private val _shieldSnapshotActive = MutableStateFlow(false)
        val shieldSnapshotActive: StateFlow<Boolean> = _shieldSnapshotActive.asStateFlow()

        private val _lastForegroundPackage = MutableStateFlow<String?>(null)
        val lastForegroundPackage: StateFlow<String?> = _lastForegroundPackage.asStateFlow()

        @Volatile
        private var activeInstance: AegisAccessibilityService? = null

        fun requestImmediatePrivacyShield(modeOverride: ShieldMode? = null): Boolean {
            val instance = activeInstance ?: return false
            instance.activatePrivacyShield(modeOverride)
            return true
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: EncryptedStorageRepository
    private lateinit var feedbackEngine: FeedbackEngine
    private var windowManager: WindowManager? = null

    private var leftOverlayView: View? = null
    private var rightOverlayView: View? = null

    // Privacy Shield Mode B (Frozen Snapshot) state — kept strictly in memory and zeroed on exit
    private var snapshotOverlayView: View? = null
    private var frozenBitmap: Bitmap? = null
    private var fallbackWatchdogRunnable: Runnable? = null
    private var biometricPromptInFlight: Boolean = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val cfg = repository.configFlow.value.privacyShield
                if (_shieldSnapshotActive.value) {
                    if (cfg.lockOnScreenOff || !cfg.persistAcrossWake) {
                        SecureLogger.info("PrivacyShield", "Screen turned off during snapshot; clearing memory and locking")
                        teardownSnapshotOverlay()
                        invokeNativeAndroidLock()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        _serviceConnected.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        repository = EncryptedStorageRepository.getInstance(applicationContext)
        feedbackEngine = FeedbackEngine(applicationContext)

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        serviceScope.launch {
            repository.configFlow.collectLatest { config ->
                syncEdgeOverlays(config)
            }
        }
        SecureLogger.info("AccessibilityService", "Aegis AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() &&
                pkg != packageName &&
                !pkg.startsWith("com.android.systemui")
            ) {
                if (_lastForegroundPackage.value != pkg) {
                    _lastForegroundPackage.value = pkg
                    // Dynamically hide/show edge overlays if entering or leaving an excluded app
                    syncEdgeOverlays(repository.configFlow.value)
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // When Privacy Shield Frozen Snapshot is active, block physical/system Back key events
        if (_shieldSnapshotActive.value && event != null) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::repository.isInitialized) {
            syncEdgeOverlays(repository.configFlow.value)
        }
    }

    override fun onInterrupt() {
        SecureLogger.warn("AccessibilityService", "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        activeInstance = null
        _serviceConnected.value = false
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        removeEdgeOverlays()
        teardownSnapshotOverlay()
        if (::feedbackEngine.isInitialized) {
            feedbackEngine.clearVisualOverlay(windowManager)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // =========================================================================================
    // PART 5 — EDGE GESTURE OVERLAY ENGINE
    // =========================================================================================

    private fun syncEdgeOverlays(config: AppConfiguration) {
        val wm = windowManager ?: return
        removeEdgeOverlays()

        // Do not mount edge gesture strips while Privacy Shield snapshot is actively freezing the screen
        if (_shieldSnapshotActive.value) return

        val gestureCfg = config.edgeGesture
        if (!gestureCfg.masterEnabled) return

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape && gestureCfg.disableInLandscape) {
            return
        }

        val currentPkg = _lastForegroundPackage.value
        if (!currentPkg.isNullOrBlank() && gestureCfg.excludedPackages.contains(currentPkg)) {
            return
        }

        if (gestureCfg.leftEdge.enabled) {
            leftOverlayView = attachEdgeStrip(wm, EdgeSide.LEFT, gestureCfg.leftEdge)
        }
        if (gestureCfg.rightEdge.enabled) {
            rightOverlayView = attachEdgeStrip(wm, EdgeSide.RIGHT, gestureCfg.rightEdge)
        }
    }

    private fun removeEdgeOverlays() {
        val wm = windowManager ?: return
        leftOverlayView?.let {
            try {
                wm.removeViewImmediate(it)
            } catch (_: Exception) {
            }
        }
        leftOverlayView = null

        rightOverlayView?.let {
            try {
                wm.removeViewImmediate(it)
            } catch (_: Exception) {
            }
        }
        rightOverlayView = null
    }

    private fun attachEdgeStrip(
        wm: WindowManager,
        side: EdgeSide,
        sideConfig: EdgeSideConfig
    ): View? {
        val metrics = resources.displayMetrics
        val screenHeight = metrics.heightPixels
        val density = metrics.density

        val stripWidthPx = (sideConfig.triggerWidthDp.coerceIn(10, 40) * density).toInt()
        val stripHeightPx = ((sideConfig.verticalHeightPercent.coerceIn(15, 85) / 100f) * screenHeight).toInt()
        val topOffsetPx = ((sideConfig.verticalStartPercent.coerceIn(5, 80) / 100f) * screenHeight).toInt()

        val view = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener(
                EdgeStripTouchListener(
                    side = side,
                    density = density
                )
            )
        }

        val params = WindowManager.LayoutParams(
            stripWidthPx,
            stripHeightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or if (side == EdgeSide.LEFT) Gravity.START else Gravity.END
            x = 0
            y = topOffsetPx
        }

        return try {
            wm.addView(view, params)
            view
        } catch (e: Exception) {
            SecureLogger.warn("EdgeGesture", "Could not attach ${side.name} edge strip overlay")
            null
        }
    }

    private inner class EdgeStripTouchListener(
        private val side: EdgeSide,
        private val density: Float
    ) : View.OnTouchListener {

        private var startX = 0f
        private var startY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var downTimestamp = 0L
        private var holdFired = false
        private var gestureAbortedAsHorizontal = false

        private val holdRunnable = Runnable {
            if (gestureAbortedAsHorizontal || holdFired) return@Runnable
            val cfg = repository.configFlow.value
            val sideCfg = if (side == EdgeSide.LEFT) cfg.edgeGesture.leftEdge else cfg.edgeGesture.rightEdge
            val dx = lastX - startX
            val dy = lastY - startY

            val classification = EdgeGestureClassifier.classifyStroke(
                dxPx = dx,
                dyPx = dy,
                durationMs = sideCfg.holdDurationMs.toLong(),
                isHoldTriggered = true,
                shortDistancePx = sideCfg.shortSwipeDistanceDp * density,
                longDistancePx = sideCfg.longSwipeDistanceDp * density,
                holdDurationMs = sideCfg.holdDurationMs,
                sensitivityPercent = sideCfg.sensitivityPercent
            )
            if (classification is EdgeGestureClassifier.ClassificationResult.Recognized) {
                holdFired = true
                dispatchRecognizedGesture(side, classification.gestureType)
            }
        }

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null) return false
            val cfg = repository.configFlow.value
            val sideCfg = if (side == EdgeSide.LEFT) cfg.edgeGesture.leftEdge else cfg.edgeGesture.rightEdge

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    lastX = startX
                    lastY = startY
                    downTimestamp = event.eventTime
                    holdFired = false
                    gestureAbortedAsHorizontal = false
                    mainHandler.removeCallbacks(holdRunnable)
                    mainHandler.postDelayed(holdRunnable, sideCfg.holdDurationMs.toLong())
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (holdFired || gestureAbortedAsHorizontal) return true
                    lastX = event.rawX
                    lastY = event.rawY
                    val dx = lastX - startX
                    val dy = lastY - startY
                    if (abs(dx) > 18f * density && abs(dy) < abs(dx) * 1.85f) {
                        // User is swiping horizontally or diagonally — cancel vertical gesture tracking
                        gestureAbortedAsHorizontal = true
                        mainHandler.removeCallbacks(holdRunnable)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(holdRunnable)
                    if (holdFired || gestureAbortedAsHorizontal) return true

                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    val elapsed = (event.eventTime - downTimestamp).coerceAtLeast(1L)

                    val classification = EdgeGestureClassifier.classifyStroke(
                        dxPx = dx,
                        dyPx = dy,
                        durationMs = elapsed,
                        isHoldTriggered = false,
                        shortDistancePx = sideCfg.shortSwipeDistanceDp * density,
                        longDistancePx = sideCfg.longSwipeDistanceDp * density,
                        holdDurationMs = sideCfg.holdDurationMs,
                        sensitivityPercent = sideCfg.sensitivityPercent
                    )
                    if (classification is EdgeGestureClassifier.ClassificationResult.Recognized) {
                        dispatchRecognizedGesture(side, classification.gestureType)
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(holdRunnable)
                    holdFired = false
                    gestureAbortedAsHorizontal = false
                    return true
                }
            }
            return false
        }
    }

    private fun dispatchRecognizedGesture(
        side: EdgeSide,
        gestureType: com.example.core.model.GestureType
    ) {
        val cfg = repository.configFlow.value
        val action = EdgeGestureClassifier.resolveActionWithPriority(
            config = cfg.edgeGesture,
            side = side,
            gestureType = gestureType,
            foregroundPackage = _lastForegroundPackage.value
        ) ?: return

        feedbackEngine.triggerFeedback(
            action = action,
            side = side,
            config = cfg.feedback,
            windowManager = windowManager
        )
        executeEdgeAction(action)
    }

    private fun executeEdgeAction(action: EdgeAction) {
        when (action) {
            EdgeAction.NONE -> Unit
            EdgeAction.PRIVACY_SHIELD_SNAPSHOT -> activatePrivacyShield(ShieldMode.FROZEN_SNAPSHOT)
            EdgeAction.PRIVACY_SHIELD_LOCK -> activatePrivacyShield(ShieldMode.NATIVE_LOCK)
            EdgeAction.BACK_NAVIGATION -> performGlobalAction(GLOBAL_ACTION_BACK)
            EdgeAction.HOME_SCREEN -> performGlobalAction(GLOBAL_ACTION_HOME)
            EdgeAction.RECENTS_SCREEN -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            EdgeAction.NOTIFICATION_SHADE -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            EdgeAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            EdgeAction.TAKE_SYSTEM_SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                }
            }
            EdgeAction.OPEN_CLIPBOARD_CLEANER -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_CATEGORY, "CLIPBOARD")
                }
                startActivity(intent)
            }
            EdgeAction.TOGGLE_TEMPORARY_MODE -> {
                repository.updateConfiguration { current ->
                    val nextState = !current.edgeGesture.temporaryModeActive
                    SecureLogger.info("EdgeGesture", "Toggled temporary gesture mode: $nextState")
                    current.copy(
                        edgeGesture = current.edgeGesture.copy(temporaryModeActive = nextState)
                    )
                }
            }
        }
    }

    // =========================================================================================
    // PART 6 — PRIVACY SHIELD (MODE A: NATIVE LOCK, MODE B: FROZEN SNAPSHOT + MANDATORY FALLBACK)
    // =========================================================================================

    fun activatePrivacyShield(modeOverride: ShieldMode? = null) {
        val shieldCfg = repository.configFlow.value.privacyShield
        if (!shieldCfg.enabled) {
            SecureLogger.warn("PrivacyShield", "Privacy Shield is disabled in settings")
            return
        }
        val targetMode = modeOverride ?: shieldCfg.defaultMode
        when (targetMode) {
            ShieldMode.NATIVE_LOCK -> invokeNativeAndroidLock()
            ShieldMode.FROZEN_SNAPSHOT -> startFrozenSnapshotWithMandatoryFallback(shieldCfg)
        }
    }

    private fun invokeNativeAndroidLock() {
        teardownSnapshotOverlay()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locked = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            if (!locked) {
                SecureLogger.warn("PrivacyShield", "GLOBAL_ACTION_LOCK_SCREEN returned false")
            } else {
                SecureLogger.info("PrivacyShield", "Invoked native Android lock screen (Mode A)")
            }
        }
    }

    private fun startFrozenSnapshotWithMandatoryFallback(
        shieldCfg: com.example.core.model.PrivacyShieldConfig
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Mandatory fallback (Part 6.2): API < 30 lacks Accessibility takeScreenshot()
            SecureLogger.warn("PrivacyShield", "API < 30; executing mandatory fallback to native lock")
            invokeNativeAndroidLock()
            return
        }

        // Remove edge strips first so they are neither captured nor obstructing the full-screen shield
        removeEdgeOverlays()

        // Start strict watchdog timer: if capture + overlay attachment does not succeed within
        // fallbackTimeoutMs, immediately lock the device with native Android lock screen!
        cancelFallbackWatchdog()
        val watchdog = Runnable {
            if (!_shieldSnapshotActive.value) {
                SecureLogger.warn("PrivacyShield", "Snapshot watchdog timeout; invoking mandatory native lock fallback")
                invokeNativeAndroidLock()
            }
        }
        fallbackWatchdogRunnable = watchdog
        mainHandler.postDelayed(watchdog, shieldCfg.fallbackTimeoutMs.coerceIn(500L, 2500L))

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hwBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        var softwareCopy: Bitmap? = null
                        try {
                            val wrapped = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            if (wrapped != null && wrapped.width > 0 && wrapped.height > 0) {
                                softwareCopy = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                                wrapped.recycle()
                            }
                        } catch (e: Exception) {
                            SecureLogger.warn("PrivacyShield", "HardwareBuffer bitmap copy failed")
                        } finally {
                            try {
                                hwBuffer.close()
                            } catch (_: Exception) {
                            }
                        }

                        if (softwareCopy == null || softwareCopy.width <= 0 || softwareCopy.height <= 0) {
                            // Mandatory fallback (Part 6.2)
                            cancelFallbackWatchdog()
                            SecureLogger.warn("PrivacyShield", "Invalid screenshot bitmap; invoking native lock fallback")
                            invokeNativeAndroidLock()
                            return
                        }

                        val mounted = mountFrozenSnapshotOverlay(softwareCopy, shieldCfg)
                        cancelFallbackWatchdog()
                        if (!mounted) {
                            softwareCopy.recycle()
                            SecureLogger.warn("PrivacyShield", "Snapshot overlay failed to mount; invoking native lock fallback")
                            invokeNativeAndroidLock()
                        } else {
                            SecureLogger.info("PrivacyShield", "Frozen Snapshot active (interaction blocked)")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        // Mandatory fallback (Part 6.2): e.g. FLAG_SECURE window or rate limit
                        cancelFallbackWatchdog()
                        SecureLogger.warn(
                            "PrivacyShield",
                            "Screenshot rejected (code $errorCode); immediately invoking native Android lock"
                        )
                        invokeNativeAndroidLock()
                    }
                }
            )
        } catch (e: Exception) {
            cancelFallbackWatchdog()
            SecureLogger.warn("PrivacyShield", "takeScreenshot threw exception; invoking native lock fallback")
            invokeNativeAndroidLock()
        }
    }

    private fun mountFrozenSnapshotOverlay(
        bitmap: Bitmap,
        shieldCfg: com.example.core.model.PrivacyShieldConfig
    ): Boolean {
        val wm = windowManager ?: return false
        teardownSnapshotOverlay()

        frozenBitmap = bitmap
        val density = resources.displayMetrics.density
        val cornerHeightPx = shieldCfg.exitCornerHeightDp * density

        val view = object : View(this) {
            private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 11, 19, 43)
                style = Paint.Style.FILL
            }
            private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E0FBFC")
                textSize = 12f * density
                textAlign = Paint.Align.CENTER
            }

            private var touchStartX = 0f
            private var touchStartY = 0f
            private var maxTouchY = 0f

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val bmp = frozenBitmap
                if (bmp == null || bmp.isRecycled) {
                    // Mandatory failure rule: if snapshot bitmap becomes unavailable while active,
                    // never show a blank/black screen — immediately invoke native Android lock!
                    post { invokeNativeAndroidLock() }
                    return
                }
                canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)

                if (shieldCfg.showExitHintBanner) {
                    val pillW = 240f * density
                    val pillH = 26f * density
                    val left = (width - pillW) / 2f
                    val top = 12f * density
                    canvas.drawRoundRect(left, top, left + pillW, top + pillH, 13f * density, 13f * density, badgeBgPaint)
                    canvas.drawText(
                        "Shield Frozen • Swipe top corners to unlock",
                        width / 2f,
                        top + 17f * density,
                        badgeTextPaint
                    )
                }
            }

            override fun onTouchEvent(event: MotionEvent?): Boolean {
                if (event == null) return true
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        maxTouchY = touchStartY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        maxTouchY = max(maxTouchY, event.rawY)
                    }
                    MotionEvent.ACTION_UP -> {
                        maxTouchY = max(maxTouchY, event.rawY)
                        val isExit = EdgeGestureClassifier.isPrivacyShieldExitGesture(
                            startX = touchStartX,
                            startY = touchStartY,
                            endX = event.rawX,
                            endY = event.rawY,
                            maxYDuringStroke = maxTouchY,
                            screenWidthPx = width,
                            cornerHeightPx = cornerHeightPx,
                            shieldConfig = shieldCfg
                        )
                        if (isExit && !biometricPromptInFlight) {
                            promptBiometricToExitSnapshot(shieldCfg.strictBiometricOnly)
                        }
                    }
                }
                // Always return true to consume 100% of touches and block the underlying app
                return true
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                // If focus is lost unexpectedly and we are not showing our own BiometricAuthActivity,
                // enforce safety by re-asserting or locking if overlay integrity is compromised.
                if (!hasWindowFocus && _shieldSnapshotActive.value && !biometricPromptInFlight) {
                    SecureLogger.info("PrivacyShield", "Snapshot window focus changed while active")
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        return try {
            wm.addView(view, params)
            snapshotOverlayView = view
            _shieldSnapshotActive.value = true
            true
        } catch (e: Exception) {
            snapshotOverlayView = null
            _shieldSnapshotActive.value = false
            false
        }
    }

    private fun promptBiometricToExitSnapshot(strictBiometricOnly: Boolean) {
        biometricPromptInFlight = true
        feedbackEngine.performHapticPulse(70)
        BiometricAuthActivity.launchFromInternalService(
            context = applicationContext,
            title = "Unlock Privacy Shield",
            subtitle = "Authenticate with fingerprint to unfreeze screen",
            strictBiometricOnly = strictBiometricOnly,
            onVerified = {
                biometricPromptInFlight = false
                SecureLogger.info("PrivacyShield", "Biometric exit verified; releasing frozen snapshot")
                teardownSnapshotOverlay()
                syncEdgeOverlays(repository.configFlow.value)
            },
            onRejected = { reason ->
                biometricPromptInFlight = false
                SecureLogger.warn("PrivacyShield", "Snapshot exit rejected ($reason); keeping screen frozen")
            }
        )
    }

    private fun cancelFallbackWatchdog() {
        fallbackWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackWatchdogRunnable = null
    }

    private fun teardownSnapshotOverlay() {
        cancelFallbackWatchdog()
        biometricPromptInFlight = false
        _shieldSnapshotActive.value = false

        val wm = windowManager
        val view = snapshotOverlayView
        snapshotOverlayView = null
        if (view != null && wm != null) {
            try {
                wm.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }

        frozenBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
        frozenBitmap = null
    }
}
