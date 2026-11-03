package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AppConfiguration
import com.example.core.model.AutofillProfile
import com.example.core.model.ClipboardConfig
import com.example.core.model.EdgeAction
import com.example.core.model.EdgeGestureConfig
import com.example.core.model.EdgeSide
import com.example.core.model.EdgeSideConfig
import com.example.core.model.FeedbackConfig
import com.example.core.model.PrivacyShieldConfig
import com.example.core.model.RegexCleanRule
import com.example.core.model.ShieldMode
import com.example.core.security.SecureLogger
import com.example.core.security.SystemSecurityAuditor
import com.example.core.storage.EncryptedStorageRepository
import com.example.feature.clipboard.UrlCleanerEngine
import com.example.feature.feedback.FeedbackEngine
import com.example.service.AegisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppCategoryDestination(
    val title: String,
    val subtitle: String
) {
    DASHBOARD("Dashboard", "System overview & category navigation"),
    EDGE_GESTURES("Edge Gestures", "Independent left & right vertical edge strokes, zones & preview"),
    PRIVACY_SHIELD("Privacy Shield", "Frozen full-screen snapshot & native Android lock fallback"),
    FEEDBACK("Feedback Engine", "Per-action haptics, temporary notch glow & edge lighting"),
    CLIPBOARD("Clipboard & URLs", "Local tracking parameter stripper & custom regex rules"),
    AUTOFILL("Encrypted Autofill", "Hardware-encrypted personal profiles & custom fields"),
    SECURITY("Security & System", "Keystore audit, permissions, OriginOS guide & diagnostics")
}

class AegisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = EncryptedStorageRepository.getInstance(appContext)
    private val feedbackEngine = FeedbackEngine(appContext)

    val configFlow: StateFlow<AppConfiguration> = repository.configFlow
    val profilesFlow: StateFlow<List<AutofillProfile>> = repository.profilesFlow
    val logsFlow = SecureLogger.entries
    val accessibilityConnectedFlow = AegisAccessibilityService.serviceConnected

    private val _currentDestination = MutableStateFlow(AppCategoryDestination.DASHBOARD)
    val currentDestination: StateFlow<AppCategoryDestination> = _currentDestination.asStateFlow()

    private val _auditSnapshot = MutableStateFlow(
        SystemSecurityAuditor.performFullAudit(appContext, repository.cryptoManager)
    )
    val auditSnapshot: StateFlow<SystemSecurityAuditor.AuditSnapshot> = _auditSnapshot.asStateFlow()

    private val _installedApps = MutableStateFlow<List<SystemSecurityAuditor.InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<SystemSecurityAuditor.InstalledAppItem>> = _installedApps.asStateFlow()

    private val _urlCleanerInput = MutableStateFlow(
        "https://example.com/product/neo10r?id=42&utm_source=newsletter&utm_medium=email&fbclid=IwAR123456&ref=promo"
    )
    val urlCleanerInput: StateFlow<String> = _urlCleanerInput.asStateFlow()

    private val _lastUrlCleanResult = MutableStateFlow<UrlCleanerEngine.UrlCleanResult?>(null)
    val lastUrlCleanResult: StateFlow<UrlCleanerEngine.UrlCleanResult?> = _lastUrlCleanResult.asStateFlow()

    private val _autofillSessionUnlocked = MutableStateFlow(false)
    val autofillSessionUnlocked: StateFlow<Boolean> = _autofillSessionUnlocked.asStateFlow()

    init {
        refreshSecurityAudit()
        runUrlCleanPreview()
    }

    fun navigateTo(destination: AppCategoryDestination) {
        _currentDestination.value = destination
        if (destination == AppCategoryDestination.SECURITY || destination == AppCategoryDestination.DASHBOARD) {
            refreshSecurityAudit()
        }
    }

    fun navigateBackToDashboard(): Boolean {
        return if (_currentDestination.value != AppCategoryDestination.DASHBOARD) {
            _currentDestination.value = AppCategoryDestination.DASHBOARD
            true
        } else {
            false
        }
    }

    fun refreshSecurityAudit() {
        _auditSnapshot.value = SystemSecurityAuditor.performFullAudit(appContext, repository.cryptoManager)
    }

    fun loadLaunchableAppsOnDemand() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = SystemSecurityAuditor.queryLaunchableAppsOnDemand(appContext)
            _installedApps.value = apps
        }
    }

    // --- Edge Gestures mutations ---

    fun updateEdgeGestureConfig(transform: (EdgeGestureConfig) -> EdgeGestureConfig) {
        repository.updateConfiguration { current ->
            current.copy(edgeGesture = transform(current.edgeGesture))
        }
    }

    fun updateSideConfig(side: EdgeSide, transform: (EdgeSideConfig) -> EdgeSideConfig) {
        updateEdgeGestureConfig { edge ->
            if (side == EdgeSide.LEFT) {
                edge.copy(leftEdge = transform(edge.leftEdge))
            } else {
                edge.copy(rightEdge = transform(edge.rightEdge))
            }
        }
    }

    fun toggleExcludedPackage(packageName: String) {
        updateEdgeGestureConfig { edge ->
            val next = edge.excludedPackages.toMutableSet()
            if (!next.add(packageName)) {
                next.remove(packageName)
            }
            edge.copy(excludedPackages = next)
        }
    }

    // --- Privacy Shield mutations & actions ---

    fun updatePrivacyShieldConfig(transform: (PrivacyShieldConfig) -> PrivacyShieldConfig) {
        repository.updateConfiguration { current ->
            current.copy(privacyShield = transform(current.privacyShield))
        }
    }

    fun triggerImmediatePrivacyShield(modeOverride: ShieldMode? = null): Boolean {
        return AegisAccessibilityService.requestImmediatePrivacyShield(modeOverride)
    }

    // --- Feedback mutations ---

    fun updateFeedbackConfig(transform: (FeedbackConfig) -> FeedbackConfig) {
        repository.updateConfiguration { current ->
            current.copy(feedback = transform(current.feedback))
        }
    }

    fun testHapticPulse() {
        feedbackEngine.performHapticPulse(configFlow.value.feedback.hapticStrengthPercent)
    }

    // --- Clipboard & URL Cleaner ---

    fun updateClipboardConfig(transform: (ClipboardConfig) -> ClipboardConfig) {
        repository.updateConfiguration { current ->
            current.copy(clipboard = transform(current.clipboard))
        }
        runUrlCleanPreview()
    }

    fun setUrlCleanerInput(input: String) {
        _urlCleanerInput.value = input
        runUrlCleanPreview()
    }

    fun handleIncomingSharedText(sharedText: String) {
        val safeText = sharedText.take(4096)
        _urlCleanerInput.value = safeText
        _currentDestination.value = AppCategoryDestination.CLIPBOARD
        runUrlCleanPreview()
        SecureLogger.info("UrlCleaner", "Received shared text for local URL cleaning")
    }

    fun runUrlCleanPreview() {
        val cfg = configFlow.value.clipboard
        _lastUrlCleanResult.value = UrlCleanerEngine.cleanTextOrUrl(_urlCleanerInput.value, cfg)
    }

    fun pasteAndCleanFromSystemClipboard(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(appContext)?.toString()
            } else {
                null
            }
            if (!text.isNullOrBlank()) {
                setUrlCleanerInput(text)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            SecureLogger.warn("UrlCleaner", "Clipboard read blocked or empty")
            false
        }
    }

    fun copyCleanedUrlToClipboard(): Boolean {
        val cleaned = _lastUrlCleanResult.value?.cleanedOutput
        if (cleaned.isNullOrBlank()) return false
        return try {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Cleaned URL", cleaned))
            feedbackEngine.performHapticPulse(60)
            SecureLogger.info("UrlCleaner", "Copied sanitized URL to clipboard")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun addCustomTrackingParam(param: String) {
        val clean = param.trim().lowercase().replace(Regex("[^a-z0-9_\\-]"), "")
        if (clean.isEmpty()) return
        updateClipboardConfig { cfg ->
            if (cfg.customQueryParameters.contains(clean)) cfg
            else cfg.copy(customQueryParameters = cfg.customQueryParameters + clean)
        }
    }

    fun removeCustomTrackingParam(param: String) {
        updateClipboardConfig { cfg ->
            cfg.copy(customQueryParameters = cfg.customQueryParameters - param)
        }
    }

    fun addCustomRegexRule(name: String, pattern: String, replacement: String) {
        if (name.isBlank() || pattern.isBlank()) return
        // Verify regex compiles before saving
        try {
            Regex(pattern)
        } catch (_: Exception) {
            return
        }
        val rule = RegexCleanRule(
            id = "rule_${System.currentTimeMillis()}",
            name = name.trim().take(40),
            pattern = pattern.trim().take(200),
            replacement = replacement.take(200),
            enabled = true
        )
        updateClipboardConfig { cfg ->
            cfg.copy(customRegexRules = cfg.customRegexRules + rule)
        }
    }

    fun removeCustomRegexRule(ruleId: String) {
        updateClipboardConfig { cfg ->
            cfg.copy(customRegexRules = cfg.customRegexRules.filterNot { it.id == ruleId })
        }
    }

    // --- Autofill Vault ---

    fun setAutofillMasterEnabled(enabled: Boolean) {
        repository.updateConfiguration { it.copy(autofillEnabled = enabled) }
    }

    fun markAutofillUnlocked(unlocked: Boolean) {
        _autofillSessionUnlocked.value = unlocked
    }

    fun saveAutofillProfile(profile: AutofillProfile) {
        repository.saveProfile(profile)
    }

    fun deleteAutofillProfile(profileId: String) {
        repository.deleteProfile(profileId)
    }

    // --- Security & Local Data Wipe ---

    fun wipeAllVaultData() {
        _autofillSessionUnlocked.value = false
        repository.wipeAllLocalData()
        refreshSecurityAudit()
    }
}
