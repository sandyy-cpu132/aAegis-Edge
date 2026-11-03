package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.security.SecureLogger
import com.example.ui.AegisViewModel
import com.example.ui.AppCategoryDestination
import com.example.ui.screens.AutofillScreen
import com.example.ui.screens.ClipboardScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EdgeGestureScreen
import com.example.ui.screens.FeedbackScreen
import com.example.ui.screens.PrivacyShieldScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.theme.MyApplicationTheme

/**
 * Main application entry point extending [FragmentActivity] so AndroidX [BiometricPrompt]
 * can attach cleanly.
 *
 * Security Rules Enforced (Part 3.1):
 * - Strictly validates incoming intents (`ACTION_SEND` `text/plain` for URL cleaning, or internal
 *   navigation extras) and rejects any unexpected data or malformed URIs.
 * - Never allows external intents to silently mutate configuration.
 */
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_CATEGORY = "aegis_open_category"
    }

    private val viewModel: AegisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        validateAndHandleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                AegisRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        validateAndHandleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSecurityAudit()
    }

    private fun validateAndHandleIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        when (incoming.action) {
            Intent.ACTION_SEND -> {
                if (incoming.type == "text/plain") {
                    val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank() && sharedText.length <= 8192) {
                        viewModel.handleIncomingSharedText(sharedText)
                    } else {
                        SecureLogger.warn("IntentValidator", "Rejected empty or oversized ACTION_SEND text")
                    }
                } else {
                    SecureLogger.warn("IntentValidator", "Rejected unexpected ACTION_SEND mimeType")
                }
            }
            Intent.ACTION_MAIN, null -> {
                val categoryExtra = incoming.getStringExtra(EXTRA_OPEN_CATEGORY)
                if (categoryExtra == "CLIPBOARD") {
                    viewModel.navigateTo(AppCategoryDestination.CLIPBOARD)
                }
            }
            else -> {
                // Reject any unexpected action without mutating state
                SecureLogger.warn("IntentValidator", "Ignored unexpected incoming intent action")
            }
        }
    }
}

@Composable
fun AegisRootApp(viewModel: AegisViewModel) {
    val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val config by viewModel.configFlow.collectAsStateWithLifecycle()
    val profiles by viewModel.profilesFlow.collectAsStateWithLifecycle()
    val audit by viewModel.auditSnapshot.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlCleanerInput.collectAsStateWithLifecycle()
    val urlResult by viewModel.lastUrlCleanResult.collectAsStateWithLifecycle()
    val autofillUnlocked by viewModel.autofillSessionUnlocked.collectAsStateWithLifecycle()
    val logs by viewModel.logsFlow.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                NavigationBarItem(
                    selected = destination == AppCategoryDestination.DASHBOARD,
                    onClick = { viewModel.navigateTo(AppCategoryDestination.DASHBOARD) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                    label = { Text("Home") },
                    modifier = Modifier.testTag("nav_item_dashboard")
                )
                NavigationBarItem(
                    selected = destination == AppCategoryDestination.EDGE_GESTURES,
                    onClick = { viewModel.navigateTo(AppCategoryDestination.EDGE_GESTURES) },
                    icon = { Icon(Icons.Filled.SwipeVertical, contentDescription = "Edge Gestures") },
                    label = { Text("Gestures") },
                    modifier = Modifier.testTag("nav_item_gestures")
                )
                NavigationBarItem(
                    selected = destination == AppCategoryDestination.PRIVACY_SHIELD,
                    onClick = { viewModel.navigateTo(AppCategoryDestination.PRIVACY_SHIELD) },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = "Privacy Shield") },
                    label = { Text("Shield") },
                    modifier = Modifier.testTag("nav_item_shield")
                )
                NavigationBarItem(
                    selected = destination == AppCategoryDestination.CLIPBOARD,
                    onClick = { viewModel.navigateTo(AppCategoryDestination.CLIPBOARD) },
                    icon = { Icon(Icons.Filled.CleaningServices, contentDescription = "URL Cleaner") },
                    label = { Text("URLs") },
                    modifier = Modifier.testTag("nav_item_clipboard")
                )
                NavigationBarItem(
                    selected = destination == AppCategoryDestination.AUTOFILL,
                    onClick = { viewModel.navigateTo(AppCategoryDestination.AUTOFILL) },
                    icon = { Icon(Icons.Filled.Password, contentDescription = "Autofill") },
                    label = { Text("Autofill") },
                    modifier = Modifier.testTag("nav_item_autofill")
                )
                NavigationBarItem(
                    selected = destination == AppCategoryDestination.SECURITY,
                    onClick = { viewModel.navigateTo(AppCategoryDestination.SECURITY) },
                    icon = { Icon(Icons.Filled.Security, contentDescription = "Security") },
                    label = { Text("Security") },
                    modifier = Modifier.testTag("nav_item_security")
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = destination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "category_transition"
        ) { target ->
            when (target) {
                AppCategoryDestination.DASHBOARD -> DashboardScreen(
                    config = config,
                    audit = audit,
                    profileCount = profiles.size,
                    onNavigate = { viewModel.navigateTo(it) },
                    onTriggerShield = { mode -> viewModel.triggerImmediatePrivacyShield(mode) },
                    onRefreshAudit = { viewModel.refreshSecurityAudit() }
                )

                AppCategoryDestination.EDGE_GESTURES -> EdgeGestureScreen(
                    config = config.edgeGesture,
                    installedApps = installedApps,
                    onLoadApps = { viewModel.loadLaunchableAppsOnDemand() },
                    onUpdateMaster = { viewModel.updateEdgeGestureConfig(it) },
                    onUpdateSide = { side, fn -> viewModel.updateSideConfig(side, fn) },
                    onToggleExcludedApp = { pkg -> viewModel.toggleExcludedPackage(pkg) },
                    onTestHaptic = { viewModel.testHapticPulse() },
                    onBack = { viewModel.navigateBackToDashboard() }
                )

                AppCategoryDestination.PRIVACY_SHIELD -> PrivacyShieldScreen(
                    config = config.privacyShield,
                    accessibilityActive = audit.accessibilityServiceEnabled,
                    onUpdate = { viewModel.updatePrivacyShieldConfig(it) },
                    onTriggerShield = { mode -> viewModel.triggerImmediatePrivacyShield(mode) },
                    onTestHaptic = { viewModel.testHapticPulse() },
                    onBack = { viewModel.navigateBackToDashboard() }
                )

                AppCategoryDestination.FEEDBACK -> FeedbackScreen(
                    config = config.feedback,
                    onUpdate = { viewModel.updateFeedbackConfig(it) },
                    onTestHaptic = { viewModel.testHapticPulse() },
                    onBack = { viewModel.navigateBackToDashboard() }
                )

                AppCategoryDestination.CLIPBOARD -> ClipboardScreen(
                    config = config.clipboard,
                    inputUrl = urlInput,
                    cleanResult = urlResult,
                    onInputUrlChange = { viewModel.setUrlCleanerInput(it) },
                    onPasteAndClean = { viewModel.pasteAndCleanFromSystemClipboard() },
                    onCopyCleaned = { viewModel.copyCleanedUrlToClipboard() },
                    onUpdateConfig = { viewModel.updateClipboardConfig(it) },
                    onAddCustomParam = { viewModel.addCustomTrackingParam(it) },
                    onRemoveCustomParam = { viewModel.removeCustomTrackingParam(it) },
                    onAddRegexRule = { n, p, r -> viewModel.addCustomRegexRule(n, p, r) },
                    onRemoveRegexRule = { id -> viewModel.removeCustomRegexRule(id) },
                    onBack = { viewModel.navigateBackToDashboard() }
                )

                AppCategoryDestination.AUTOFILL -> AutofillScreen(
                    autofillEnabled = config.autofillEnabled,
                    systemServiceEnabled = audit.autofillServiceEnabled,
                    profiles = profiles,
                    sessionUnlocked = autofillUnlocked,
                    strictBiometricOnly = config.privacyShield.strictBiometricOnly,
                    onToggleMaster = { viewModel.setAutofillMasterEnabled(it) },
                    onSetSessionUnlocked = { viewModel.markAutofillUnlocked(it) },
                    onSaveProfile = { viewModel.saveAutofillProfile(it) },
                    onDeleteProfile = { viewModel.deleteAutofillProfile(it) },
                    onBack = { viewModel.navigateBackToDashboard() }
                )

                AppCategoryDestination.SECURITY -> SecuritySettingsScreen(
                    audit = audit,
                    logs = logs,
                    onRefreshAudit = { viewModel.refreshSecurityAudit() },
                    onClearLogs = { SecureLogger.clear() },
                    onWipeVault = { viewModel.wipeAllVaultData() },
                    onBack = { viewModel.navigateBackToDashboard() }
                )
            }
        }
    }
}
