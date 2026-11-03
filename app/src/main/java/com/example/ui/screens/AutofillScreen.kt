package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.core.model.AutofillProfile
import com.example.core.security.BiometricAuthManager
import com.example.ui.components.CategoryTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SensitiveScreenProtectionEffect
import com.example.ui.components.SettingsGroupCard
import com.example.ui.components.SettingsToggleRow
import com.example.ui.components.StatusPill

@Composable
fun AutofillScreen(
    autofillEnabled: Boolean,
    systemServiceEnabled: Boolean,
    profiles: List<AutofillProfile>,
    sessionUnlocked: Boolean,
    strictBiometricOnly: Boolean,
    onToggleMaster: (Boolean) -> Unit,
    onSetSessionUnlocked: (Boolean) -> Unit,
    onSaveProfile: (AutofillProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    // Enforce Android 14+ accessibilityDataSensitive protection on this screen (Part 3.4)
    SensitiveScreenProtectionEffect()
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var editingProfile by remember { mutableStateOf<AutofillProfile?>(null) }
    var authErrorBanner by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTopBar(
            title = "Encrypted Autofill",
            subtitle = "Native Android AutofillService • Keystore AES-256-GCM vault",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("autofill_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Enable Aegis Autofill Assistant",
                        subtitle = "Respond to genuine Android Autofill Framework requests with encrypted profiles",
                        checked = autofillEnabled,
                        onCheckedChange = onToggleMaster,
                        testTag = "toggle_autofill_master"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Android System Autofill Provider",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        StatusPill(
                            label = if (systemServiceEnabled) "Selected" else "Not Selected",
                            isPositive = systemServiceEnabled
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                } catch (_: Exception) {
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_autofill_settings")
                    ) {
                        Text("Open Android Autofill Settings")
                    }
                }
            }

            // Biometric Vault Gate for viewing or modifying personal profile values (Part 9)
            item {
                SectionHeader("Vault Access & Biometric Gate")
                SettingsGroupCard {
                    if (!sessionUnlocked) {
                        Text(
                            text = "Personal profile fields are encrypted with AES-256-GCM in private no-backup storage. Authenticate to view or edit profile contents.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        authErrorBanner?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = {
                                val act = context as? FragmentActivity
                                val cap = BiometricAuthManager.queryCapability(context)
                                if (act != null && cap.strongBiometricReady) {
                                    BiometricAuthManager.authenticate(
                                        activity = act,
                                        title = "Unlock Encrypted Autofill Vault",
                                        subtitle = "Verify fingerprint to view or edit personal profiles",
                                        strictBiometricOnly = strictBiometricOnly,
                                        onSuccess = {
                                            authErrorBanner = null
                                            onSetSessionUnlocked(true)
                                        },
                                        onFailureOrCancel = { reason ->
                                            authErrorBanner = reason
                                        }
                                    )
                                } else {
                                    // If running on an emulator/device with no enrolled biometrics yet,
                                    // allow session unlock while surfacing clear notice.
                                    authErrorBanner = "Note: ${cap.statusMessage}. Unlocked for initial setup."
                                    onSetSessionUnlocked(true)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_unlock_autofill_vault")
                        ) {
                            Text("Authenticate to Reveal / Edit Profiles")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(label = "Vault Session Unlocked", isPositive = true)
                            FilledTonalButton(
                                onClick = { onSetSessionUnlocked(false) },
                                modifier = Modifier.testTag("btn_lock_autofill_vault")
                            ) {
                                Text("Lock Vault Now")
                            }
                        }
                    }
                }
            }

            // Encrypted Profiles List (Revealed only when unlocked)
            if (sessionUnlocked) {
                item {
                    SectionHeader("Encrypted Personal Profiles (${profiles.size})")
                }

                items(profiles.size) { index ->
                    val profile = profiles[index]
                    SettingsGroupCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = profile.profileName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { editingProfile = profile },
                                    modifier = Modifier.testTag("btn_edit_profile_${profile.id}")
                                ) {
                                    Text("Edit")
                                }
                                if (profiles.size > 1) {
                                    TextButton(onClick = { onDeleteProfile(profile.id) }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        ProfileFieldPreview("Full Name", "${profile.firstName} ${profile.lastName}".trim())
                        ProfileFieldPreview("Email", profile.email)
                        ProfileFieldPreview("Phone", profile.phone)
                        ProfileFieldPreview("Street Address", profile.streetAddress)
                        ProfileFieldPreview("PIN / Postal Code", profile.postalCode)

                        if (profile.customFields.isNotEmpty()) {
                            Text(
                                text = "Custom Fields: " + profile.customFields.entries.joinToString(", ") {
                                    "${it.key}=${it.value.ifEmpty { "—" }}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            editingProfile = AutofillProfile(
                                id = "prof_${System.currentTimeMillis()}",
                                profileName = "New Profile",
                                firstName = "",
                                lastName = "",
                                email = "",
                                phone = "",
                                streetAddress = "",
                                postalCode = "",
                                customFields = emptyMap(),
                                requireBiometricForFill = true
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_create_profile")
                    ) {
                        Text("Create Encrypted Profile")
                    }
                }
            }

            item {
                SectionHeader("Platform Scope & Limitations")
                SettingsGroupCard {
                    Text(
                        text = "• Native Android Autofill Framework only: Aegis never monitors typing via AccessibilityService and never collects form inputs from other apps.\n" +
                            "• Some third-party apps or custom WebViews do not expose standard Android Autofill hints; Aegis matches both standard autofillHints and field IDs/custom field keys where provided.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    val currentEdit = editingProfile
    if (currentEdit != null) {
        AutofillProfileEditorDialog(
            initial = currentEdit,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                onSaveProfile(updated)
                editingProfile = null
            }
        )
    }
}

@Composable
private fun ProfileFieldPreview(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "Not set" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AutofillProfileEditorDialog(
    initial: AutofillProfile,
    onDismiss: () -> Unit,
    onSave: (AutofillProfile) -> Unit
) {
    var profileName by remember { mutableStateOf(initial.profileName) }
    var firstName by remember { mutableStateOf(initial.firstName) }
    var lastName by remember { mutableStateOf(initial.lastName) }
    var email by remember { mutableStateOf(initial.email) }
    var phone by remember { mutableStateOf(initial.phone) }
    var street by remember { mutableStateOf(initial.streetAddress) }
    var postalCode by remember { mutableStateOf(initial.postalCode) }
    var customKey by remember { mutableStateOf("") }
    var customVal by remember { mutableStateOf("") }
    var customMap by remember { mutableStateOf(initial.customFields) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Encrypted Profile") },
        text = {
            LazyColumn(
                modifier = Modifier.height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_first_name")
                    )
                }
                item {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Last Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_email")
                    )
                }
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = street,
                        onValueChange = { street = it },
                        label = { Text("Street Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("PIN / Postal Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        text = "Custom Fields (match by field hint/id token)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    customMap.forEach { (k, v) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$k: $v", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { customMap = customMap - k }) {
                                    Text("✕")
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = customKey,
                            onValueChange = { customKey = it },
                            label = { Text("Key") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customVal,
                            onValueChange = { customVal = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(
                        onClick = {
                            if (customKey.isNotBlank()) {
                                customMap = customMap + (customKey.trim() to customVal.trim())
                                customKey = ""
                                customVal = ""
                            }
                        }
                    ) {
                        Text("+ Add Custom Field")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            profileName = profileName.ifBlank { "Personal" },
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            email = email.trim(),
                            phone = phone.trim(),
                            streetAddress = street.trim(),
                            postalCode = postalCode.trim(),
                            customFields = customMap
                        )
                    )
                },
                modifier = Modifier.testTag("btn_save_profile_dialog")
            ) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
