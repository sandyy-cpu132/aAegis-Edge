package com.example.core.model

import org.json.JSONArray
import org.json.JSONObject

enum class EdgeSide {
    LEFT, RIGHT
}

enum class GestureType(val displayName: String, val description: String) {
    SHORT_SWIPE_UP("Short Swipe Up", "Quick upward vertical stroke along the edge"),
    SHORT_SWIPE_DOWN("Short Swipe Down", "Quick downward vertical stroke along the edge"),
    LONG_SWIPE_UP("Long Swipe Up", "Extended upward vertical stroke along the edge"),
    LONG_SWIPE_DOWN("Long Swipe Down", "Extended downward vertical stroke along the edge"),
    HOLD_SWIPE_UP("Swipe Up & Hold", "Move upward along edge and hold stationary"),
    HOLD_SWIPE_DOWN("Swipe Down & Hold", "Move downward along edge and hold stationary")
}

enum class EdgeAction(
    val title: String,
    val subtitle: String,
    val category: String
) {
    NONE("No Action", "Do nothing for this gesture", "System"),
    PRIVACY_SHIELD_SNAPSHOT(
        "Privacy Shield: Frozen Snapshot",
        "Freeze current screen immediately; falls back to native lock on failure",
        "Privacy Shield"
    ),
    PRIVACY_SHIELD_LOCK(
        "Privacy Shield: Native Device Lock",
        "Invoke actual Android lock screen immediately",
        "Privacy Shield"
    ),
    BACK_NAVIGATION("System Back", "Perform standard Android back action", "Navigation"),
    HOME_SCREEN("Home Screen", "Return to Android home launcher", "Navigation"),
    RECENTS_SCREEN("Recent Apps", "Open Android overview / recent apps", "Navigation"),
    NOTIFICATION_SHADE("Notification Shade", "Expand system notification panel", "System"),
    QUICK_SETTINGS("Quick Settings", "Expand system Quick Settings panel", "System"),
    TAKE_SYSTEM_SCREENSHOT("System Screenshot", "Trigger native Accessibility screenshot", "Utility"),
    OPEN_CLIPBOARD_CLEANER("URL Cleaner Sheet", "Open local URL tracking sanitizer", "Utility"),
    TOGGLE_TEMPORARY_MODE(
        "Toggle Temporary Gesture Mode",
        "Switch edge gestures into or out of temporary override mode",
        "Utility"
    )
}

data class EdgeSideConfig(
    val enabled: Boolean = true,
    val triggerWidthDp: Int = 18,
    val verticalStartPercent: Int = 28,
    val verticalHeightPercent: Int = 44,
    val shortSwipeDistanceDp: Int = 36,
    val longSwipeDistanceDp: Int = 92,
    val holdDurationMs: Int = 340,
    val sensitivityPercent: Int = 80,
    val shortUpAction: EdgeAction = EdgeAction.PRIVACY_SHIELD_SNAPSHOT,
    val shortDownAction: EdgeAction = EdgeAction.NOTIFICATION_SHADE,
    val longUpAction: EdgeAction = EdgeAction.RECENTS_SCREEN,
    val longDownAction: EdgeAction = EdgeAction.PRIVACY_SHIELD_LOCK,
    val holdUpAction: EdgeAction = EdgeAction.OPEN_CLIPBOARD_CLEANER,
    val holdDownAction: EdgeAction = EdgeAction.QUICK_SETTINGS
) {
    fun actionFor(type: GestureType): EdgeAction = when (type) {
        GestureType.SHORT_SWIPE_UP -> shortUpAction
        GestureType.SHORT_SWIPE_DOWN -> shortDownAction
        GestureType.LONG_SWIPE_UP -> longUpAction
        GestureType.LONG_SWIPE_DOWN -> longDownAction
        GestureType.HOLD_SWIPE_UP -> holdUpAction
        GestureType.HOLD_SWIPE_DOWN -> holdDownAction
    }

    fun withAction(type: GestureType, action: EdgeAction): EdgeSideConfig = when (type) {
        GestureType.SHORT_SWIPE_UP -> copy(shortUpAction = action)
        GestureType.SHORT_SWIPE_DOWN -> copy(shortDownAction = action)
        GestureType.LONG_SWIPE_UP -> copy(longUpAction = action)
        GestureType.LONG_SWIPE_DOWN -> copy(longDownAction = action)
        GestureType.HOLD_SWIPE_UP -> copy(holdUpAction = action)
        GestureType.HOLD_SWIPE_DOWN -> copy(holdDownAction = action)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("triggerWidthDp", triggerWidthDp.coerceIn(10, 40))
        put("verticalStartPercent", verticalStartPercent.coerceIn(5, 80))
        put("verticalHeightPercent", verticalHeightPercent.coerceIn(15, 85))
        put("shortSwipeDistanceDp", shortSwipeDistanceDp.coerceIn(20, 80))
        put("longSwipeDistanceDp", longSwipeDistanceDp.coerceIn(60, 180))
        put("holdDurationMs", holdDurationMs.coerceIn(180, 900))
        put("sensitivityPercent", sensitivityPercent.coerceIn(30, 100))
        put("shortUpAction", shortUpAction.name)
        put("shortDownAction", shortDownAction.name)
        put("longUpAction", longUpAction.name)
        put("longDownAction", longDownAction.name)
        put("holdUpAction", holdUpAction.name)
        put("holdDownAction", holdDownAction.name)
    }

    companion object {
        fun fromJson(obj: JSONObject?): EdgeSideConfig {
            if (obj == null) return EdgeSideConfig()
            fun parseAction(key: String, default: EdgeAction): EdgeAction {
                val raw = obj.optString(key, default.name)
                return EdgeAction.entries.firstOrNull { it.name == raw } ?: default
            }
            return EdgeSideConfig(
                enabled = obj.optBoolean("enabled", true),
                triggerWidthDp = obj.optInt("triggerWidthDp", 18).coerceIn(10, 40),
                verticalStartPercent = obj.optInt("verticalStartPercent", 28).coerceIn(5, 80),
                verticalHeightPercent = obj.optInt("verticalHeightPercent", 44).coerceIn(15, 85),
                shortSwipeDistanceDp = obj.optInt("shortSwipeDistanceDp", 36).coerceIn(20, 80),
                longSwipeDistanceDp = obj.optInt("longSwipeDistanceDp", 92).coerceIn(60, 180),
                holdDurationMs = obj.optInt("holdDurationMs", 340).coerceIn(180, 900),
                sensitivityPercent = obj.optInt("sensitivityPercent", 80).coerceIn(30, 100),
                shortUpAction = parseAction("shortUpAction", EdgeAction.PRIVACY_SHIELD_SNAPSHOT),
                shortDownAction = parseAction("shortDownAction", EdgeAction.NOTIFICATION_SHADE),
                longUpAction = parseAction("longUpAction", EdgeAction.RECENTS_SCREEN),
                longDownAction = parseAction("longDownAction", EdgeAction.PRIVACY_SHIELD_LOCK),
                holdUpAction = parseAction("holdUpAction", EdgeAction.OPEN_CLIPBOARD_CLEANER),
                holdDownAction = parseAction("holdDownAction", EdgeAction.QUICK_SETTINGS)
            )
        }
    }
}

data class EdgeGestureConfig(
    val masterEnabled: Boolean = true,
    val leftEdge: EdgeSideConfig = EdgeSideConfig(),
    val rightEdge: EdgeSideConfig = EdgeSideConfig(
        shortUpAction = EdgeAction.PRIVACY_SHIELD_SNAPSHOT,
        shortDownAction = EdgeAction.NOTIFICATION_SHADE,
        longUpAction = EdgeAction.HOME_SCREEN,
        longDownAction = EdgeAction.PRIVACY_SHIELD_LOCK,
        holdUpAction = EdgeAction.OPEN_CLIPBOARD_CLEANER,
        holdDownAction = EdgeAction.QUICK_SETTINGS
    ),
    val disableInLandscape: Boolean = true,
    val excludedPackages: Set<String> = emptySet(),
    val appSpecificOverrides: Map<String, EdgeAction> = emptyMap(),
    val temporaryModeActive: Boolean = false,
    val temporaryModeAction: EdgeAction = EdgeAction.PRIVACY_SHIELD_SNAPSHOT
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("masterEnabled", masterEnabled)
        put("leftEdge", leftEdge.toJson())
        put("rightEdge", rightEdge.toJson())
        put("disableInLandscape", disableInLandscape)
        put("excludedPackages", JSONArray(excludedPackages.toList()))
        val overridesObj = JSONObject()
        appSpecificOverrides.forEach { (pkg, act) -> overridesObj.put(pkg, act.name) }
        put("appSpecificOverrides", overridesObj)
        put("temporaryModeActive", temporaryModeActive)
        put("temporaryModeAction", temporaryModeAction.name)
    }

    companion object {
        fun fromJson(obj: JSONObject?): EdgeGestureConfig {
            if (obj == null) return EdgeGestureConfig()
            val excluded = mutableSetOf<String>()
            val exArr = obj.optJSONArray("excludedPackages")
            if (exArr != null) {
                for (i in 0 until exArr.length()) {
                    val pkg = exArr.optString(i).trim()
                    if (pkg.isNotEmpty()) excluded.add(pkg)
                }
            }
            val overrides = mutableMapOf<String, EdgeAction>()
            val ovObj = obj.optJSONObject("appSpecificOverrides")
            if (ovObj != null) {
                val keys = ovObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val actName = ovObj.optString(k)
                    val act = EdgeAction.entries.firstOrNull { it.name == actName }
                    if (act != null) overrides[k] = act
                }
            }
            val tempActName = obj.optString("temporaryModeAction", EdgeAction.PRIVACY_SHIELD_SNAPSHOT.name)
            val tempAct = EdgeAction.entries.firstOrNull { it.name == tempActName }
                ?: EdgeAction.PRIVACY_SHIELD_SNAPSHOT

            return EdgeGestureConfig(
                masterEnabled = obj.optBoolean("masterEnabled", true),
                leftEdge = EdgeSideConfig.fromJson(obj.optJSONObject("leftEdge")),
                rightEdge = EdgeSideConfig.fromJson(obj.optJSONObject("rightEdge")),
                disableInLandscape = obj.optBoolean("disableInLandscape", true),
                excludedPackages = excluded,
                appSpecificOverrides = overrides,
                temporaryModeActive = obj.optBoolean("temporaryModeActive", false),
                temporaryModeAction = tempAct
            )
        }
    }
}

enum class ShieldMode(val title: String, val summary: String) {
    FROZEN_SNAPSHOT(
        "Frozen Full-Screen Snapshot (Mode B)",
        "Captures exact current screen into memory, blocks app touches, and immediately triggers native Android lock if capture/overlay fails."
    ),
    NATIVE_LOCK(
        "Real Android Lock Screen (Mode A)",
        "Immediately locks the device using the native Android Accessibility lock-screen action."
    )
}

data class PrivacyShieldConfig(
    val enabled: Boolean = true,
    val defaultMode: ShieldMode = ShieldMode.FROZEN_SNAPSHOT,
    val exitGestureEnabled: Boolean = true,
    val exitCornerHeightDp: Int = 68,
    val exitMinSwipePercent: Int = 55,
    val strictBiometricOnly: Boolean = true,
    val lockOnScreenOff: Boolean = true,
    val persistAcrossWake: Boolean = false,
    val fallbackTimeoutMs: Long = 1200L,
    val showExitHintBanner: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("defaultMode", defaultMode.name)
        put("exitGestureEnabled", exitGestureEnabled)
        put("exitCornerHeightDp", exitCornerHeightDp.coerceIn(44, 120))
        put("exitMinSwipePercent", exitMinSwipePercent.coerceIn(35, 85))
        put("strictBiometricOnly", strictBiometricOnly)
        put("lockOnScreenOff", lockOnScreenOff)
        put("persistAcrossWake", persistAcrossWake)
        put("fallbackTimeoutMs", fallbackTimeoutMs.coerceIn(500L, 2500L))
        put("showExitHintBanner", showExitHintBanner)
    }

    companion object {
        fun fromJson(obj: JSONObject?): PrivacyShieldConfig {
            if (obj == null) return PrivacyShieldConfig()
            val modeName = obj.optString("defaultMode", ShieldMode.FROZEN_SNAPSHOT.name)
            val mode = ShieldMode.entries.firstOrNull { it.name == modeName } ?: ShieldMode.FROZEN_SNAPSHOT
            return PrivacyShieldConfig(
                enabled = obj.optBoolean("enabled", true),
                defaultMode = mode,
                exitGestureEnabled = obj.optBoolean("exitGestureEnabled", true),
                exitCornerHeightDp = obj.optInt("exitCornerHeightDp", 68).coerceIn(44, 120),
                exitMinSwipePercent = obj.optInt("exitMinSwipePercent", 55).coerceIn(35, 85),
                strictBiometricOnly = obj.optBoolean("strictBiometricOnly", true),
                lockOnScreenOff = obj.optBoolean("lockOnScreenOff", true),
                persistAcrossWake = obj.optBoolean("persistAcrossWake", false),
                fallbackTimeoutMs = obj.optLong("fallbackTimeoutMs", 1200L).coerceIn(500L, 2500L),
                showExitHintBanner = obj.optBoolean("showExitHintBanner", false)
            )
        }
    }
}

enum class FeedbackStyle(val title: String, val description: String) {
    HAPTIC_AND_EDGE("Haptic + Edge Lighting", "Crisp vibration with temporary edge light pulse"),
    HAPTIC_AND_NOTCH("Haptic + Notch Glow", "Crisp vibration with temporary punch-hole ring glow"),
    HAPTIC_ONLY("Haptic Only", "Tactile vibration with zero visual indicator"),
    NOTCH_GLOW("Temporary Notch Glow Only", "Brief ring glow around front camera cutout"),
    EDGE_LIGHTING("Temporary Edge Lighting Only", "Brief luminous bar along the active screen edge"),
    SILENT("Silent", "No vibration and no visual indicator")
}

enum class AnimationPreset(val title: String) {
    PULSE("Crisp Pulse"),
    BREATHE("Smooth Fade"),
    RIPPLE_ARC("Vertical Sweep")
}

data class FeedbackConfig(
    val enabled: Boolean = true,
    val defaultStyle: FeedbackStyle = FeedbackStyle.HAPTIC_AND_EDGE,
    val hapticStrengthPercent: Int = 75,
    val accentColorHex: String = "#00E5FF",
    val durationMs: Int = 420,
    val animationPreset: AnimationPreset = AnimationPreset.PULSE,
    val notchRadiusDp: Int = 18,
    val notchVerticalOffsetDp: Int = 14,
    val perActionStyles: Map<EdgeAction, FeedbackStyle> = emptyMap()
) {
    fun styleFor(action: EdgeAction): FeedbackStyle {
        if (!enabled) return FeedbackStyle.SILENT
        return perActionStyles[action] ?: defaultStyle
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("defaultStyle", defaultStyle.name)
        put("hapticStrengthPercent", hapticStrengthPercent.coerceIn(10, 100))
        put("accentColorHex", accentColorHex)
        put("durationMs", durationMs.coerceIn(180, 1000))
        put("animationPreset", animationPreset.name)
        put("notchRadiusDp", notchRadiusDp.coerceIn(10, 36))
        put("notchVerticalOffsetDp", notchVerticalOffsetDp.coerceIn(0, 48))
        val mapObj = JSONObject()
        perActionStyles.forEach { (act, style) -> mapObj.put(act.name, style.name) }
        put("perActionStyles", mapObj)
    }

    companion object {
        fun fromJson(obj: JSONObject?): FeedbackConfig {
            if (obj == null) return FeedbackConfig()
            val defName = obj.optString("defaultStyle", FeedbackStyle.HAPTIC_AND_EDGE.name)
            val defStyle = FeedbackStyle.entries.firstOrNull { it.name == defName }
                ?: FeedbackStyle.HAPTIC_AND_EDGE
            val animName = obj.optString("animationPreset", AnimationPreset.PULSE.name)
            val anim = AnimationPreset.entries.firstOrNull { it.name == animName }
                ?: AnimationPreset.PULSE
            val map = mutableMapOf<EdgeAction, FeedbackStyle>()
            val mapObj = obj.optJSONObject("perActionStyles")
            if (mapObj != null) {
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val act = EdgeAction.entries.firstOrNull { it.name == k }
                    val st = FeedbackStyle.entries.firstOrNull { it.name == mapObj.optString(k) }
                    if (act != null && st != null) map[act] = st
                }
            }
            return FeedbackConfig(
                enabled = obj.optBoolean("enabled", true),
                defaultStyle = defStyle,
                hapticStrengthPercent = obj.optInt("hapticStrengthPercent", 75).coerceIn(10, 100),
                accentColorHex = obj.optString("accentColorHex", "#00E5FF"),
                durationMs = obj.optInt("durationMs", 420).coerceIn(180, 1000),
                animationPreset = anim,
                notchRadiusDp = obj.optInt("notchRadiusDp", 18).coerceIn(10, 36),
                notchVerticalOffsetDp = obj.optInt("notchVerticalOffsetDp", 14).coerceIn(0, 48),
                perActionStyles = map
            )
        }
    }
}

data class RegexCleanRule(
    val id: String,
    val name: String,
    val pattern: String,
    val replacement: String,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("pattern", pattern)
        put("replacement", replacement)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(obj: JSONObject): RegexCleanRule = RegexCleanRule(
            id = obj.optString("id", "rule_${System.currentTimeMillis()}"),
            name = obj.optString("name", "Custom Rule"),
            pattern = obj.optString("pattern", ""),
            replacement = obj.optString("replacement", ""),
            enabled = obj.optBoolean("enabled", true)
        )
    }
}

data class ClipboardConfig(
    val enabled: Boolean = true,
    val removeBuiltInTracking: Boolean = true,
    val customQueryParameters: List<String> = listOf("ref", "source", "campaign_id"),
    val customRegexRules: List<RegexCleanRule> = emptyList(),
    val autoCleanOnShareReceive: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("removeBuiltInTracking", removeBuiltInTracking)
        put("customQueryParameters", JSONArray(customQueryParameters))
        val rulesArr = JSONArray()
        customRegexRules.forEach { rulesArr.put(it.toJson()) }
        put("customRegexRules", rulesArr)
        put("autoCleanOnShareReceive", autoCleanOnShareReceive)
    }

    companion object {
        fun fromJson(obj: JSONObject?): ClipboardConfig {
            if (obj == null) return ClipboardConfig()
            val params = mutableListOf<String>()
            val paramArr = obj.optJSONArray("customQueryParameters")
            if (paramArr != null) {
                for (i in 0 until paramArr.length()) {
                    val p = paramArr.optString(i).trim()
                    if (p.isNotEmpty()) params.add(p)
                }
            } else {
                params.addAll(listOf("ref", "source", "campaign_id"))
            }
            val regexes = mutableListOf<RegexCleanRule>()
            val regArr = obj.optJSONArray("customRegexRules")
            if (regArr != null) {
                for (i in 0 until regArr.length()) {
                    val rObj = regArr.optJSONObject(i)
                    if (rObj != null) regexes.add(RegexCleanRule.fromJson(rObj))
                }
            }
            return ClipboardConfig(
                enabled = obj.optBoolean("enabled", true),
                removeBuiltInTracking = obj.optBoolean("removeBuiltInTracking", true),
                customQueryParameters = params,
                customRegexRules = regexes,
                autoCleanOnShareReceive = obj.optBoolean("autoCleanOnShareReceive", true)
            )
        }
    }
}

data class AutofillProfile(
    val id: String,
    val profileName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val streetAddress: String,
    val postalCode: String,
    val customFields: Map<String, String> = emptyMap(),
    val requireBiometricForFill: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("profileName", profileName)
        put("firstName", firstName)
        put("lastName", lastName)
        put("email", email)
        put("phone", phone)
        put("streetAddress", streetAddress)
        put("postalCode", postalCode)
        val customObj = JSONObject()
        customFields.forEach { (k, v) -> customObj.put(k, v) }
        put("customFields", customObj)
        put("requireBiometricForFill", requireBiometricForFill)
    }

    companion object {
        fun fromJson(obj: JSONObject): AutofillProfile {
            val customMap = mutableMapOf<String, String>()
            val customObj = obj.optJSONObject("customFields")
            if (customObj != null) {
                val keys = customObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    customMap[k] = customObj.optString(k, "")
                }
            }
            return AutofillProfile(
                id = obj.optString("id", "prof_${System.currentTimeMillis()}"),
                profileName = obj.optString("profileName", "Personal"),
                firstName = obj.optString("firstName", ""),
                lastName = obj.optString("lastName", ""),
                email = obj.optString("email", ""),
                phone = obj.optString("phone", ""),
                streetAddress = obj.optString("streetAddress", ""),
                postalCode = obj.optString("postalCode", ""),
                customFields = customMap,
                requireBiometricForFill = obj.optBoolean("requireBiometricForFill", true)
            )
        }
    }
}

data class AppConfiguration(
    val edgeGesture: EdgeGestureConfig = EdgeGestureConfig(),
    val privacyShield: PrivacyShieldConfig = PrivacyShieldConfig(),
    val feedback: FeedbackConfig = FeedbackConfig(),
    val clipboard: ClipboardConfig = ClipboardConfig(),
    val autofillEnabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("edgeGesture", edgeGesture.toJson())
        put("privacyShield", privacyShield.toJson())
        put("feedback", feedback.toJson())
        put("clipboard", clipboard.toJson())
        put("autofillEnabled", autofillEnabled)
    }

    companion object {
        fun fromJson(obj: JSONObject?): AppConfiguration {
            if (obj == null) return AppConfiguration()
            return AppConfiguration(
                edgeGesture = EdgeGestureConfig.fromJson(obj.optJSONObject("edgeGesture")),
                privacyShield = PrivacyShieldConfig.fromJson(obj.optJSONObject("privacyShield")),
                feedback = FeedbackConfig.fromJson(obj.optJSONObject("feedback")),
                clipboard = ClipboardConfig.fromJson(obj.optJSONObject("clipboard")),
                autofillEnabled = obj.optBoolean("autofillEnabled", true)
            )
        }
    }
}
