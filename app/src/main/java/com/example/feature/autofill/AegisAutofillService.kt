package com.example.feature.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.example.R
import com.example.core.model.AutofillProfile
import com.example.core.security.SecureLogger
import com.example.core.storage.EncryptedStorageRepository
import java.util.Locale

/**
 * Native Android AutofillService backed by AES-256-GCM encrypted local profiles (Part 9).
 *
 * Security & Privacy Rules:
 * - Bound with `android.permission.BIND_AUTOFILL_SERVICE`.
 * - Processes ONLY explicit system `onFillRequest` invocations.
 * - Never collects, stores, or logs unrelated form data in `onSaveRequest`.
 * - Never monitors typing via AccessibilityService.
 */
class AegisAutofillService : AutofillService() {

    enum class FieldCategory {
        FIRST_NAME,
        LAST_NAME,
        FULL_NAME,
        EMAIL,
        PHONE,
        STREET_ADDRESS,
        POSTAL_CODE,
        CUSTOM
    }

    data class DetectedField(
        val autofillId: AutofillId,
        val category: FieldCategory,
        val customKeyMatch: String? = null
    )

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val repository = EncryptedStorageRepository.getInstance(applicationContext)
        val appConfig = repository.configFlow.value
        if (!appConfig.autofillEnabled) {
            callback.onSuccess(null)
            return
        }

        val profiles = repository.profilesFlow.value
        if (profiles.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val contexts = request.fillContexts
        val latestStructure: AssistStructure = contexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val customKeys = profiles
            .flatMap { it.customFields.keys }
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .toSet()

        val detectedFields = mutableListOf<DetectedField>()
        val windowCount = latestStructure.windowNodeCount
        for (i in 0 until windowCount) {
            val rootNode = latestStructure.getWindowNodeAt(i).rootViewNode
            traverseNode(rootNode, customKeys, detectedFields)
        }

        if (detectedFields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()
        var datasetsAdded = 0

        for (profile in profiles) {
            val datasetBuilder = Dataset.Builder()
            var fieldsPopulatedForProfile = 0

            for (field in detectedFields) {
                val valueToFill = resolveValueForField(profile, field)
                if (!valueToFill.isNullOrBlank()) {
                    val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(
                            android.R.id.text1,
                            "Aegis: ${profile.profileName} (${labelForCategory(field)})"
                        )
                    }
                    @Suppress("DEPRECATION")
                    datasetBuilder.setValue(
                        field.autofillId,
                        AutofillValue.forText(valueToFill),
                        presentation
                    )
                    fieldsPopulatedForProfile++
                }
            }

            if (fieldsPopulatedForProfile > 0) {
                responseBuilder.addDataset(datasetBuilder.build())
                datasetsAdded++
            }
        }

        if (datasetsAdded == 0) {
            callback.onSuccess(null)
        } else {
            SecureLogger.info("AutofillService", "Prepared autofill response ($datasetsAdded profile datasets)")
            callback.onSuccess(responseBuilder.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Security Rule (Part 9): Never silently harvest or collect form values from external apps.
        callback.onSuccess()
    }

    private fun traverseNode(
        node: AssistStructure.ViewNode,
        customKeys: Set<String>,
        outFields: MutableList<DetectedField>
    ) {
        val autofillId = node.autofillId
        if (autofillId != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
            val classified = classifyViewNode(node, customKeys)
            if (classified != null) {
                outFields.add(classified)
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            traverseNode(node.getChildAt(i), customKeys, outFields)
        }
    }

    private fun classifyViewNode(
        node: AssistStructure.ViewNode,
        customKeys: Set<String>
    ): DetectedField? {
        val id = node.autofillId ?: return null
        val hints = node.autofillHints?.map { it.lowercase(Locale.US) } ?: emptyList()
        val idEntry = node.idEntry?.lowercase(Locale.US) ?: ""
        val hintText = node.hint?.toString()?.lowercase(Locale.US) ?: ""
        val combinedTokens = (hints + listOf(idEntry, hintText)).joinToString(" ")

        // 1. Check custom field keys first if exact token match
        for (customKey in customKeys) {
            if (combinedTokens.contains(customKey)) {
                return DetectedField(id, FieldCategory.CUSTOM, customKey)
            }
        }

        // 2. Match standard autofill categories
        return when {
            hints.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS) ||
                combinedTokens.contains("email") ||
                combinedTokens.contains("e-mail") -> DetectedField(id, FieldCategory.EMAIL)

            hints.contains(View.AUTOFILL_HINT_PHONE) ||
                combinedTokens.contains("phone") ||
                combinedTokens.contains("mobile") ||
                combinedTokens.contains("tel") -> DetectedField(id, FieldCategory.PHONE)

            hints.contains(View.AUTOFILL_HINT_POSTAL_CODE) ||
                combinedTokens.contains("postal") ||
                combinedTokens.contains("pincode") ||
                combinedTokens.contains("zip") -> DetectedField(id, FieldCategory.POSTAL_CODE)

            hints.contains(View.AUTOFILL_HINT_POSTAL_ADDRESS) ||
                combinedTokens.contains("address") ||
                combinedTokens.contains("street") -> DetectedField(id, FieldCategory.STREET_ADDRESS)

            combinedTokens.contains("givenname") ||
                combinedTokens.contains("first_name") ||
                combinedTokens.contains("firstname") ||
                combinedTokens.contains("first name") -> DetectedField(id, FieldCategory.FIRST_NAME)

            combinedTokens.contains("familyname") ||
                combinedTokens.contains("last_name") ||
                combinedTokens.contains("lastname") ||
                combinedTokens.contains("surname") ||
                combinedTokens.contains("last name") -> DetectedField(id, FieldCategory.LAST_NAME)

            hints.contains(View.AUTOFILL_HINT_NAME) ||
                combinedTokens.contains("full_name") ||
                combinedTokens.contains("fullname") -> DetectedField(id, FieldCategory.FULL_NAME)

            else -> null
        }
    }

    private fun resolveValueForField(profile: AutofillProfile, field: DetectedField): String? {
        return when (field.category) {
            FieldCategory.FIRST_NAME -> profile.firstName
            FieldCategory.LAST_NAME -> profile.lastName
            FieldCategory.FULL_NAME -> "${profile.firstName} ${profile.lastName}".trim()
            FieldCategory.EMAIL -> profile.email
            FieldCategory.PHONE -> profile.phone
            FieldCategory.STREET_ADDRESS -> profile.streetAddress
            FieldCategory.POSTAL_CODE -> profile.postalCode
            FieldCategory.CUSTOM -> {
                val key = field.customKeyMatch ?: return null
                profile.customFields.entries.firstOrNull {
                    it.key.trim().lowercase(Locale.US) == key
                }?.value
            }
        }
    }

    private fun labelForCategory(field: DetectedField): String = when (field.category) {
        FieldCategory.FIRST_NAME -> "First Name"
        FieldCategory.LAST_NAME -> "Last Name"
        FieldCategory.FULL_NAME -> "Full Name"
        FieldCategory.EMAIL -> "Email"
        FieldCategory.PHONE -> "Phone"
        FieldCategory.STREET_ADDRESS -> "Address"
        FieldCategory.POSTAL_CODE -> "Postal Code"
        FieldCategory.CUSTOM -> field.customKeyMatch ?: "Custom"
    }
}
