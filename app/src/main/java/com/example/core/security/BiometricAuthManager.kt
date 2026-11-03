package com.example.core.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric authentication manager using AndroidX BiometricPrompt.
 *
 * Architectural & Security Notes (Part 3.3 & Part 6.5):
 * - Android's modern BiometricPrompt routes through Class 3 (BIOMETRIC_STRONG) authenticators.
 * - On iQOO Neo10R (OriginOS 6), the optical/ultrasonic under-display fingerprint sensor is the
 *   Class 3 (Strong) biometric sensor, while standard 2D camera face unlock is Class 2 (Weak).
 * - Therefore, requesting `BIOMETRIC_STRONG` without `BIOMETRIC_WEAK` or `DEVICE_CREDENTIAL`
 *   specifically enforces the Class 3 fingerprint hardware on iQOO Neo10R without using deprecated
 *   `FingerprintManager` APIs.
 * - We also explicitly report whether `PackageManager.FEATURE_FINGERPRINT` is present and whether
 *   Class 3 biometrics are enrolled.
 */
object BiometricAuthManager {

    data class BiometricCapabilityStatus(
        val fingerprintHardwareDeclared: Boolean,
        val strongBiometricReady: Boolean,
        val statusMessage: String
    )

    fun queryCapability(context: Context): BiometricCapabilityStatus {
        val hasFingerprintFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus(
                fingerprintHardwareDeclared = hasFingerprintFeature,
                strongBiometricReady = true,
                statusMessage = if (hasFingerprintFeature) {
                    "Class 3 Strong Biometric Ready (Fingerprint hardware present)"
                } else {
                    "Class 3 Strong Biometric Ready"
                }
            )
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus(
                fingerprintHardwareDeclared = hasFingerprintFeature,
                strongBiometricReady = false,
                statusMessage = "No strong biometric enrolled in Android Settings"
            )
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapabilityStatus(
                fingerprintHardwareDeclared = hasFingerprintFeature,
                strongBiometricReady = false,
                statusMessage = "No Class 3 biometric hardware detected"
            )
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapabilityStatus(
                fingerprintHardwareDeclared = hasFingerprintFeature,
                strongBiometricReady = false,
                statusMessage = "Biometric sensor temporarily unavailable"
            )
            else -> BiometricCapabilityStatus(
                fingerprintHardwareDeclared = hasFingerprintFeature,
                strongBiometricReady = false,
                statusMessage = "Biometric status code: $result"
            )
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        strictBiometricOnly: Boolean = true,
        onSuccess: () -> Unit,
        onFailureOrCancel: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    SecureLogger.info("BiometricAuth", "Biometric verification succeeded")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    SecureLogger.warn("BiometricAuth", "Biometric error ($errorCode)")
                    onFailureOrCancel(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    SecureLogger.warn("BiometricAuth", "Biometric attempt did not match")
                }
            }
        )

        val capability = queryCapability(activity)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)

        if (strictBiometricOnly && capability.strongBiometricReady) {
            builder.setAllowedAuthenticators(BIOMETRIC_STRONG)
            builder.setNegativeButtonText("Cancel")
        } else {
            // Only if Class 3 biometric is not enrolled or user disabled strict-biometric-only
            val fallbackAuth = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            val canFallback = BiometricManager.from(activity).canAuthenticate(fallbackAuth) ==
                BiometricManager.BIOMETRIC_SUCCESS
            if (canFallback && !strictBiometricOnly) {
                builder.setAllowedAuthenticators(fallbackAuth)
            } else if (capability.strongBiometricReady) {
                builder.setAllowedAuthenticators(BIOMETRIC_STRONG)
                builder.setNegativeButtonText("Cancel")
            } else {
                onFailureOrCancel(capability.statusMessage)
                return
            }
        }

        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            SecureLogger.warn("BiometricAuth", "Unable to launch BiometricPrompt")
            onFailureOrCancel("Biometric prompt failed to launch")
        }
    }
}
