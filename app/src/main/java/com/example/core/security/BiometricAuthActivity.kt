package com.example.core.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Non-exported internal transparent activity that hosts [BiometricPrompt] when triggered
 * from [com.example.service.AegisAccessibilityService] (e.g., exiting Privacy Shield Frozen Snapshot).
 *
 * Security design:
 * - `android:exported="false"` in AndroidManifest.xml prevents any external application from
 *   invoking this activity.
 * - Uses an unguessable in-memory one-time session token (`EXTRA_SESSION_TOKEN`) registered
 *   within the same process so even internal intent replay is rejected.
 */
class BiometricAuthActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_SESSION_TOKEN = "aegis_internal_session_token"
        private const val EXTRA_TITLE = "aegis_prompt_title"
        private const val EXTRA_SUBTITLE = "aegis_prompt_subtitle"
        private const val EXTRA_STRICT_BIOMETRIC = "aegis_strict_biometric"

        private data class PendingSession(
            val onVerified: () -> Unit,
            val onRejected: (String) -> Unit
        )

        private val activeSessions = ConcurrentHashMap<String, PendingSession>()

        fun launchFromInternalService(
            context: Context,
            title: String,
            subtitle: String,
            strictBiometricOnly: Boolean,
            onVerified: () -> Unit,
            onRejected: (String) -> Unit
        ) {
            val token = UUID.randomUUID().toString()
            activeSessions[token] = PendingSession(onVerified, onRejected)
            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_SESSION_TOKEN, token)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_STRICT_BIOMETRIC, strictBiometricOnly)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                activeSessions.remove(token)
                onRejected("Could not launch biometric prompt activity")
            }
        }
    }

    private var currentToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val token = intent?.getStringExtra(EXTRA_SESSION_TOKEN)
        val session = if (token != null) activeSessions[token] else null
        if (token == null || session == null) {
            SecureLogger.warn("BiometricAuthActivity", "Rejected unauthenticated or expired intent")
            finish()
            return
        }
        currentToken = token

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Aegis Security Verification"
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Verify fingerprint to continue"
        val strictBiometric = intent.getBooleanExtra(EXTRA_STRICT_BIOMETRIC, true)

        BiometricAuthManager.authenticate(
            activity = this,
            title = title,
            subtitle = subtitle,
            strictBiometricOnly = strictBiometric,
            onSuccess = {
                activeSessions.remove(token)?.onVerified?.invoke()
                finish()
                overridePendingTransition(0, 0)
            },
            onFailureOrCancel = { reason ->
                activeSessions.remove(token)?.onRejected?.invoke(reason)
                finish()
                overridePendingTransition(0, 0)
            }
        )
    }

    override fun onDestroy() {
        val token = currentToken
        if (token != null) {
            activeSessions.remove(token)?.onRejected?.invoke("Authentication cancelled")
        }
        super.onDestroy()
    }
}
