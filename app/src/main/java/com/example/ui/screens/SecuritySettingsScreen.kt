package com.example.ui.screens

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.security.SecureLogger
import com.example.core.security.SystemSecurityAuditor
import com.example.ui.components.CategoryTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SensitiveScreenProtectionEffect
import com.example.ui.components.SettingsGroupCard
import com.example.ui.components.StatusPill

@Composable
fun SecuritySettingsScreen(
    audit: SystemSecurityAuditor.AuditSnapshot,
    logs: List<SecureLogger.LogEntry>,
    onRefreshAudit: () -> Unit,
    onClearLogs: () -> Unit,
    onWipeVault: () -> Unit,
    onBack: () -> Unit
) {
    // Enforce Android 14+ accessibilityDataSensitive protection on security diagnostics (Part 3.4)
    SensitiveScreenProtectionEffect()
    BackHandler(onBack = onBack)

    var showWipeConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTopBar(
            title = "Security & System",
            subtitle = "Hardware Keystore audit, OriginOS 6 setup & local data management",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("security_settings_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Cryptographic & Sandbox Audit
            item {
                SectionHeader("Cryptographic & Sandbox Verification")
                SettingsGroupCard {
                    AuditRow(
                        label = "Encryption Algorithm",
                        value = audit.keyReport.algorithm,
                        positive = audit.keyReport.keyPresent
                    )
                    AuditRow(
                        label = "Keystore Hardware Level",
                        value = audit.keyReport.backingLevel.label,
                        positive = audit.keyReport.backingLevel.isHardwareBacked
                    )
                    AuditRow(
                        label = "Device StrongBox Feature",
                        value = if (audit.keyReport.strongBoxSupportedByDevice) "Present" else "TEE Fallback",
                        positive = audit.keyReport.strongBoxSupportedByDevice
                    )
                    AuditRow(
                        label = "Biometric Class 3 Status",
                        value = if (audit.biometricStatus.strongBiometricReady) "Ready" else "Enroll Required",
                        positive = audit.biometricStatus.strongBiometricReady
                    )
                    AuditRow(
                        label = "INTERNET Permission Absent",
                        value = if (audit.internetPermissionAbsent) "Verified (0 Network)" else "Present",
                        positive = audit.internetPermissionAbsent
                    )
                    AuditRow(
                        label = "Cloud & ADB Backup Disabled",
                        value = if (audit.allowBackupDisabled) "allowBackup=false" else "Enabled",
                        positive = audit.allowBackupDisabled
                    )

                    OutlinedButton(
                        onClick = onRefreshAudit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_refresh_security_audit")
                    ) {
                        Text("Re-Verify Hardware & Sandbox Audit")
                    }
                }
            }

            // OriginOS 6 (iQOO Neo10R) Setup & Persistence Checklist
            item {
                SectionHeader("iQOO Neo10R • OriginOS 6 Configuration Guide")
                SettingsGroupCard {
                    Text(
                        text = "To prevent OriginOS 6 from suspending the Accessibility Gesture Engine when idle:\n" +
                            "1. Settings → Accessibility → Downloaded Services → Aegis Edge → Enable (If Android 16 shows 'Restricted Setting', open App Info → top-right ⋮ menu → 'Allow restricted settings').\n" +
                            "2. Settings → Battery → Background Power Consumption Management → Aegis Edge → select 'High Background Power Usage' (Allow).\n" +
                            "3. Settings → Apps → App Manager → Aegis Edge → Permissions → Autostart → Enable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Honest Feature & Security Boundary Disclosure (Part 3, Part 10, Part 17)
            item {
                SectionHeader("Security Boundary & Feature Classification")
                SettingsGroupCard {
                    Text(
                        text = "• Security Boundary: Aegis protects local settings and autofill profiles against ordinary third-party apps using Android sandboxing, non-exported components, and Keystore AES-256-GCM. No normal user-space app can defend against a compromised OS kernel, root attacker, or privileged system malware.\n" +
                            "• Screenshot Trash Utility (Part 10): Omitted by design. OriginOS 6 Gallery's proprietary Recently Deleted sync with MediaStore IS_TRASHED cannot be guaranteed without broad media permissions and background ContentObserver polling. Per project rules, no substitute trash bin or permanent deletion is used.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sensitive-Data-Safe Diagnostic Ring Buffer
            item {
                SectionHeader("PII-Redacted Diagnostic Events (${logs.size})")
                SettingsGroupCard {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No diagnostic events recorded in this session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.takeLast(8).reversed().forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${entry.timestamp} • [${entry.module}]",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (entry.isWarning) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    Text(
                                        text = entry.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        TextButton(onClick = onClearLogs) {
                            Text("Clear In-Memory Logs")
                        }
                    }
                }
            }

            // Cryptographic Wipe & Key Rotation
            item {
                SectionHeader("Emergency Local Data Destruction")
                SettingsGroupCard {
                    Text(
                        text = "Overwrites encrypted vault files with zeroes, deletes internal storage files, and destroys the master AES-256-GCM key in Android Keystore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showWipeConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_cryptographic_wipe")
                    ) {
                        Text("Cryptographically Wipe All Local Data")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Confirm Cryptographic Wipe") },
            text = {
                Text(
                    "This will permanently destroy your Android Keystore master key, zero out all encrypted autofill profiles, and reset all gesture configurations to factory defaults."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onWipeVault()
                        showWipeConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("btn_confirm_wipe_dialog")
                ) {
                    Text("Destroy Keys & Wipe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AuditRow(
    label: String,
    value: String,
    positive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        StatusPill(label = value, isPositive = positive)
    }
}
