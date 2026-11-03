package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AutofillProfile
import com.example.core.model.EdgeAction
import com.example.core.security.BiometricAuthActivity
import com.example.core.security.KeystoreCryptoManager
import com.example.core.security.SystemSecurityAuditor
import com.example.core.storage.EncryptedStorageRepository
import com.example.feature.autofill.AegisAutofillService
import com.example.service.AegisAccessibilityService
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `manifest enforces zero INTERNET permission, allowBackup false, and protected services`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        // 1. Verify zero INTERNET permission
        assertTrue(
            "Application must not declare android.permission.INTERNET",
            SystemSecurityAuditor.verifyZeroInternetPermission(context)
        )

        // 2. Verify allowBackup is disabled
        assertTrue(
            "Application must have android:allowBackup=false",
            SystemSecurityAuditor.verifyBackupDisabled(context)
        )

        // 3. Verify BiometricAuthActivity is non-exported
        val bioActivityInfo = pm.getActivityInfo(
            ComponentName(context, BiometricAuthActivity::class.java),
            0
        )
        assertFalse("BiometricAuthActivity must not be exported", bioActivityInfo.exported)

        // 4. Verify AccessibilityService requires BIND_ACCESSIBILITY_SERVICE
        val accServiceInfo = pm.getServiceInfo(
            ComponentName(context, AegisAccessibilityService::class.java),
            0
        )
        assertEquals(
            android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            accServiceInfo.permission
        )

        // 5. Verify AutofillService requires BIND_AUTOFILL_SERVICE
        val autofillServiceInfo = pm.getServiceInfo(
            ComponentName(context, AegisAutofillService::class.java),
            0
        )
        assertEquals(
            android.Manifest.permission.BIND_AUTOFILL_SERVICE,
            autofillServiceInfo.permission
        )
    }

    @Test
    fun `aes-256-gcm encryption roundtrip and AAD tamper detection succeed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = KeystoreCryptoManager(context)
        val plaintext = "Sensitive Autofill Profile Payload".toByteArray(StandardCharsets.UTF_8)
        val aad1 = "AEGIS_AUTOFILL_V1_AAD".toByteArray(StandardCharsets.UTF_8)
        val aad2 = "AEGIS_CONFIG_V1_AAD".toByteArray(StandardCharsets.UTF_8)

        val encrypted = crypto.encrypt(plaintext, aad1)
        val decrypted = crypto.decrypt(encrypted, aad1)
        assertArrayEquals(plaintext, decrypted)

        // Tampering with AAD must throw AEADBadTagException
        try {
            crypto.decrypt(encrypted, aad2)
            fail("Decrypting with mismatched AAD must fail authentication")
        } catch (_: Exception) {
            // Expected GCM authentication failure
        }
    }

    @Test
    fun `encrypted storage persists profiles and cryptographic wipe zeroes vault`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EncryptedStorageRepository.getInstance(context)

        repo.updateConfiguration { cfg ->
            cfg.copy(
                edgeGesture = cfg.edgeGesture.copy(
                    leftEdge = cfg.edgeGesture.leftEdge.copy(shortUpAction = EdgeAction.QUICK_SETTINGS)
                )
            )
        }
        assertEquals(
            EdgeAction.QUICK_SETTINGS,
            repo.configFlow.value.edgeGesture.leftEdge.shortUpAction
        )

        val testProfile = AutofillProfile(
            id = "test_prof_1",
            profileName = "Work Vault",
            firstName = "Aarav",
            lastName = "Sharma",
            email = "aarav@example.org",
            phone = "+919000000000",
            streetAddress = "42 Cyber Park",
            postalCode = "560001",
            customFields = mapOf("employee_id" to "EMP-42")
        )
        repo.saveProfile(testProfile)
        assertTrue(repo.profilesFlow.value.any { it.id == "test_prof_1" && it.firstName == "Aarav" })

        // Cryptographic wipe resets configuration and profiles
        repo.wipeAllLocalData()
        assertFalse(repo.profilesFlow.value.any { it.id == "test_prof_1" })
        assertEquals(
            EdgeAction.PRIVACY_SHIELD_SNAPSHOT,
            repo.configFlow.value.edgeGesture.leftEdge.shortUpAction
        )
    }

    @Test
    fun `compose UI navigates cleanly across all categories following restaurant blueprint`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_status_card").assertIsDisplayed()

        // Navigate to Edge Gestures
        composeTestRule.onNodeWithTag("nav_item_gestures").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_edge_master").assertIsDisplayed()

        // Navigate to Privacy Shield
        composeTestRule.onNodeWithTag("nav_item_shield").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_shield_enabled").assertIsDisplayed()

        // Navigate to URL Cleaner
        composeTestRule.onNodeWithTag("nav_item_clipboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("url_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cleaned_url_output_box").assertIsDisplayed()

        // Navigate to Encrypted Autofill
        composeTestRule.onNodeWithTag("nav_item_autofill").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_autofill_master").assertIsDisplayed()

        // Navigate to Security & System Audit
        composeTestRule.onNodeWithTag("nav_item_security").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("security_settings_list").assertIsDisplayed()
    }
}
