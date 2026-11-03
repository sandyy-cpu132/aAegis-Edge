package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.AppConfiguration
import com.example.core.model.ShieldMode
import com.example.core.security.SystemSecurityAuditor
import com.example.ui.AppCategoryDestination
import com.example.ui.components.CategoryEntryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill

@Composable
fun DashboardScreen(
    config: AppConfiguration,
    audit: SystemSecurityAuditor.AuditSnapshot,
    profileCount: Int,
    onNavigate: (AppCategoryDestination) -> Unit,
    onTriggerShield: (ShieldMode) -> Unit,
    onRefreshAudit: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Aegis Edge",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "iQOO Neo10R • OriginOS 6 • Offline Vault",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StatusPill(
                    label = if (audit.internetPermissionAbsent) "0% Network • Offline" else "Check Permissions",
                    isPositive = audit.internetPermissionAbsent
                )
            }
        }

        // Security & System Readiness Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_status_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Security Foundation",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Security & Service Status",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${audit.keyReport.algorithm} • ${audit.keyReport.backingLevel.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Essential Service Setup Checklist
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ServiceRequirementRow(
                                label = "Accessibility Engine (Gestures & Shield)",
                                enabled = audit.accessibilityServiceEnabled,
                                actionLabel = "Enable",
                                testTag = "btn_enable_accessibility",
                                onAction = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                }
                            )
                            ServiceRequirementRow(
                                label = "Native Autofill Service",
                                enabled = audit.autofillServiceEnabled,
                                actionLabel = "Configure",
                                testTag = "btn_enable_autofill",
                                onAction = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Quick Action Triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onTriggerShield(ShieldMode.FROZEN_SNAPSHOT) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_freeze_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Freeze Screen")
                        }
                        FilledTonalButton(
                            onClick = { onTriggerShield(ShieldMode.NATIVE_LOCK) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_lock_button")
                        ) {
                            Text("Native Lock")
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Feature Categories")
        }

        item {
            CategoryEntryCard(
                icon = Icons.Filled.SwipeVertical,
                title = "Edge Gestures",
                subtitle = "Left & right vertical swipes, zones, exclusions & live preview",
                statusBadge = if (config.edgeGesture.masterEnabled) "Enabled" else "Off",
                statusPositive = config.edgeGesture.masterEnabled,
                testTag = "cat_edge_gestures",
                onClick = { onNavigate(AppCategoryDestination.EDGE_GESTURES) }
            )
        }

        item {
            CategoryEntryCard(
                icon = Icons.Filled.Lock,
                title = "Privacy Shield",
                subtitle = "Mode B Frozen Snapshot, Mode A Native Lock & corner exit gesture",
                statusBadge = if (config.privacyShield.defaultMode == ShieldMode.FROZEN_SNAPSHOT) {
                    "Snapshot + Fallback"
                } else {
                    "Native Lock"
                },
                statusPositive = config.privacyShield.enabled,
                testTag = "cat_privacy_shield",
                onClick = { onNavigate(AppCategoryDestination.PRIVACY_SHIELD) }
            )
        }

        item {
            CategoryEntryCard(
                icon = Icons.Filled.Vibration,
                title = "Feedback Engine",
                subtitle = "Haptics, temporary notch glow, edge lighting & per-action rules",
                statusBadge = config.feedback.defaultStyle.title.substringBefore(" "),
                statusPositive = config.feedback.enabled,
                testTag = "cat_feedback",
                onClick = { onNavigate(AppCategoryDestination.FEEDBACK) }
            )
        }

        item {
            CategoryEntryCard(
                icon = Icons.Filled.CleaningServices,
                title = "Clipboard & URL Cleaner",
                subtitle = "Strip tracking parameters locally, custom rules & share target",
                statusBadge = "${config.clipboard.customQueryParameters.size + 35} Trackers",
                statusPositive = config.clipboard.enabled,
                testTag = "cat_clipboard",
                onClick = { onNavigate(AppCategoryDestination.CLIPBOARD) }
            )
        }

        item {
            CategoryEntryCard(
                icon = Icons.Filled.Password,
                title = "Encrypted Autofill",
                subtitle = "Keystore AES-GCM personal profiles, custom fields & biometric gate",
                statusBadge = "$profileCount Profile(s)",
                statusPositive = config.autofillEnabled,
                testTag = "cat_autofill",
                onClick = { onNavigate(AppCategoryDestination.AUTOFILL) }
            )
        }

        item {
            CategoryEntryCard(
                icon = Icons.Filled.Security,
                title = "Security & System",
                subtitle = "Keystore hardware audit, OriginOS 6 setup, permissions & vault wipe",
                statusBadge = if (audit.keyReport.backingLevel.isHardwareBacked) "Hardware Key" else "Audited",
                statusPositive = true,
                testTag = "cat_security",
                onClick = { onNavigate(AppCategoryDestination.SECURITY) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ServiceRequirementRow(
    label: String,
    enabled: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (enabled) Color(0xFF10B981) else Color(0xFFF59E0B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (enabled) {
            StatusPill(label = "Active", isPositive = true)
        } else {
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier
                    .height(36.dp)
                    .testTag(testTag)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
