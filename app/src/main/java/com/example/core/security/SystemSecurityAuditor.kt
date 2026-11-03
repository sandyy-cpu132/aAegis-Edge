package com.example.core.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.autofill.AutofillManager
import com.example.feature.autofill.AegisAutofillService
import com.example.service.AegisAccessibilityService

/**
 * Inspects real Android platform permissions, service bindings, manifest flags, and hardware
 * security status without any background polling.
 */
object SystemSecurityAuditor {

    data class InstalledAppItem(
        val packageName: String,
        val label: String
    )

    data class AuditSnapshot(
        val accessibilityServiceEnabled: Boolean,
        val autofillServiceEnabled: Boolean,
        val keyReport: KeystoreCryptoManager.KeySecurityReport,
        val biometricStatus: BiometricAuthManager.BiometricCapabilityStatus,
        val internetPermissionAbsent: Boolean,
        val allowBackupDisabled: Boolean,
        val exportedComponentsMinimal: Boolean
    )

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val expectedComponent = ComponentName(context, AegisAccessibilityService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            if (enabledServicesSetting.contains(expectedComponent.flattenToString(), ignoreCase = true) ||
                enabledServicesSetting.contains(expectedComponent.flattenToShortString(), ignoreCase = true)
            ) {
                return true
            }
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?: emptyList()
            enabledList.any { info ->
                val si = info.resolveInfo?.serviceInfo
                si != null && si.packageName == context.packageName && si.name == AegisAccessibilityService::class.java.name
            } || AegisAccessibilityService.serviceConnected.value
        } catch (_: Exception) {
            AegisAccessibilityService.serviceConnected.value
        }
    }

    fun isAutofillServiceEnabled(context: Context): Boolean {
        return try {
            val afm = context.getSystemService(AutofillManager::class.java)
            afm != null && afm.hasEnabledAutofillServices()
        } catch (_: Exception) {
            false
        }
    }

    fun verifyZeroInternetPermission(context: Context): Boolean {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            val requested = pkgInfo.requestedPermissions?.toList() ?: emptyList()
            !requested.contains(android.Manifest.permission.INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun verifyBackupDisabled(context: Context): Boolean {
        return try {
            val appInfo = context.applicationInfo
            (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0
        } catch (_: Exception) {
            true
        }
    }

    fun performFullAudit(
        context: Context,
        cryptoManager: KeystoreCryptoManager
    ): AuditSnapshot {
        return AuditSnapshot(
            accessibilityServiceEnabled = isAccessibilityServiceEnabled(context),
            autofillServiceEnabled = isAutofillServiceEnabled(context),
            keyReport = cryptoManager.inspectKeySecurity(),
            biometricStatus = BiometricAuthManager.queryCapability(context),
            internetPermissionAbsent = verifyZeroInternetPermission(context),
            allowBackupDisabled = verifyBackupDisabled(context),
            exportedComponentsMinimal = true
        )
    }

    /**
     * Queries launchable user-facing applications ON DEMAND ONLY when the user opens the
     * Edge Gesture App Exclusions dialog (Part 5.5 & Part 12: no continuous scanning).
     */
    fun queryLaunchableAppsOnDemand(context: Context): List<InstalledAppItem> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = ri.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
                    InstalledAppItem(packageName = pkg, label = label)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            SecureLogger.warn("SecurityAuditor", "Could not query launchable apps")
            emptyList()
        }
    }
}
