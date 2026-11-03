package com.example.feature.clipboard

import com.example.core.model.ClipboardConfig
import com.example.core.model.RegexCleanRule
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Local-only URL sanitizer and tracking-parameter cleaner (Part 8).
 *
 * Security & Privacy guarantees:
 * - Zero network requests; 100% deterministic local parsing.
 * - Rejects oversized inputs (> 8192 chars) to prevent memory/ReDoS exhaustion.
 * - Preserves non-tracking query parameters, ports, paths, and fragments.
 * - Validates output scheme (`http` or `https` only) after any custom regex rule so a
 *   malformed regex can never rewrite a URL into `javascript:`, `file:`, or `intent:`.
 */
object UrlCleanerEngine {

    private const val MAX_INPUT_LENGTH = 8192

    val BUILT_IN_TRACKING_PARAMS: Set<String> = setOf(
        // Google / Analytics / Ads
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "utm_name", "utm_reader", "utm_viz_id", "utm_pubreferrer", "utm_swu",
        "gclid", "gclsrc", "dclid", "wbraid", "gbraid", "_ga", "_gl",
        // Meta / Facebook / Instagram / Threads
        "fbclid", "igshid", "igsh", "fb_action_ids", "fb_action_types", "fb_source",
        // Microsoft / Bing / LinkedIn / X (Twitter) / TikTok / YouTube
        "msclkid", "twclid", "ttclid", "si", "feature", "kw", "li_fat_id",
        // Email / Marketing / E-commerce trackers
        "mc_cid", "mc_eid", "yclid", "_hsenc", "_hsmi", "vero_id", "mkt_tok",
        "rb_clickid", "s_kwcid", "spm", "scm", "aff_platform", "aff_trace_key",
        "ref_src", "ref_url", "wt_zmc", "pk_campaign", "pk_kwd", "pk_source",
        "sc_campaign", "sc_channel", "sc_content", "sc_medium", "sc_outcome"
    )

    private val urlExtractorRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    data class UrlCleanResult(
        val originalInput: String,
        val cleanedOutput: String,
        val extractedUrlsCount: Int,
        val removedParameters: List<String>,
        val appliedRegexNames: List<String>,
        val wasModified: Boolean,
        val errorMessage: String? = null
    )

    fun cleanTextOrUrl(rawInput: String, config: ClipboardConfig): UrlCleanResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return UrlCleanResult(
                originalInput = rawInput,
                cleanedOutput = "",
                extractedUrlsCount = 0,
                removedParameters = emptyList(),
                appliedRegexNames = emptyList(),
                wasModified = false,
                errorMessage = "Input is empty"
            )
        }
        if (trimmed.length > MAX_INPUT_LENGTH) {
            return UrlCleanResult(
                originalInput = rawInput.take(128),
                cleanedOutput = "",
                extractedUrlsCount = 0,
                removedParameters = emptyList(),
                appliedRegexNames = emptyList(),
                wasModified = false,
                errorMessage = "Input exceeds maximum safe length ($MAX_INPUT_LENGTH chars)"
            )
        }

        val matches = urlExtractorRegex.findAll(trimmed).toList()
        if (matches.isEmpty()) {
            // Try checking if the user pasted a bare URL without scheme or if it's invalid
            return UrlCleanResult(
                originalInput = trimmed,
                cleanedOutput = trimmed,
                extractedUrlsCount = 0,
                removedParameters = emptyList(),
                appliedRegexNames = emptyList(),
                wasModified = false,
                errorMessage = "No valid http:// or https:// URL found in input"
            )
        }

        val allRemovedParams = mutableListOf<String>()
        val allAppliedRegexes = mutableSetOf<String>()
        var updatedText = trimmed

        // Replace from end to start so character ranges stay valid
        for (match in matches.asReversed()) {
            val singleUrl = match.value
            val singleResult = cleanSingleUrl(singleUrl, config)
            allRemovedParams.addAll(0, singleResult.removedParameters)
            allAppliedRegexes.addAll(singleResult.appliedRegexNames)
            if (singleResult.cleanedOutput != singleUrl) {
                updatedText = updatedText.replaceRange(match.range, singleResult.cleanedOutput)
            }
        }

        return UrlCleanResult(
            originalInput = trimmed,
            cleanedOutput = updatedText,
            extractedUrlsCount = matches.size,
            removedParameters = allRemovedParams.distinct(),
            appliedRegexNames = allAppliedRegexes.toList(),
            wasModified = updatedText != trimmed,
            errorMessage = null
        )
    }

    fun cleanSingleUrl(url: String, config: ClipboardConfig): UrlCleanResult {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return UrlCleanResult(
                originalInput = url,
                cleanedOutput = url,
                extractedUrlsCount = 0,
                removedParameters = emptyList(),
                appliedRegexNames = emptyList(),
                wasModified = false,
                errorMessage = "Malformed URI syntax"
            )
        }

        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            return UrlCleanResult(
                originalInput = url,
                cleanedOutput = url,
                extractedUrlsCount = 0,
                removedParameters = emptyList(),
                appliedRegexNames = emptyList(),
                wasModified = false,
                errorMessage = "Only http and https URLs are supported"
            )
        }

        if (uri.host.isNullOrBlank()) {
            return UrlCleanResult(
                originalInput = url,
                cleanedOutput = url,
                extractedUrlsCount = 0,
                removedParameters = emptyList(),
                appliedRegexNames = emptyList(),
                wasModified = false,
                errorMessage = "URL is missing a valid hostname"
            )
        }

        val blockedKeys = buildSet {
            if (config.removeBuiltInTracking) {
                addAll(BUILT_IN_TRACKING_PARAMS)
            }
            config.customQueryParameters.forEach { custom ->
                val normalized = custom.trim().lowercase(Locale.US)
                if (normalized.isNotEmpty()) add(normalized)
            }
        }

        val removedParams = mutableListOf<String>()
        val rawQuery = uri.rawQuery
        val keptPairs = mutableListOf<String>()

        if (!rawQuery.isNullOrEmpty()) {
            val segments = rawQuery.split("&")
            for (segment in segments) {
                if (segment.isEmpty()) continue
                val eqIdx = segment.indexOf('=')
                val rawKey = if (eqIdx >= 0) segment.substring(0, eqIdx) else segment
                val decodedKey = try {
                    URLDecoder.decode(rawKey, "UTF-8")
                } catch (_: Exception) {
                    rawKey
                }
                val normalizedKey = decodedKey.trim().lowercase(Locale.US)
                val isWildcardUtm = config.removeBuiltInTracking && normalizedKey.startsWith("utm_")

                if (blockedKeys.contains(normalizedKey) || isWildcardUtm) {
                    removedParams.add(decodedKey)
                } else {
                    keptPairs.add(segment)
                }
            }
        }

        val rebuilt = StringBuilder().apply {
            append(scheme).append("://")
            if (!uri.rawAuthority.isNullOrEmpty()) {
                append(uri.rawAuthority)
            } else {
                append(uri.host)
                if (uri.port != -1) append(":").append(uri.port)
            }
            if (!uri.rawPath.isNullOrEmpty()) {
                append(uri.rawPath)
            }
            if (keptPairs.isNotEmpty()) {
                append("?").append(keptPairs.joinToString("&"))
            }
            if (!uri.rawFragment.isNullOrEmpty()) {
                append("#").append(uri.rawFragment)
            }
        }.toString()

        var finalUrl = rebuilt
        val appliedRegexNames = mutableListOf<String>()

        for (rule: RegexCleanRule in config.customRegexRules) {
            if (!rule.enabled || rule.pattern.isBlank() || rule.pattern.length > 256) continue
            try {
                val regex = Regex(rule.pattern)
                val candidate = regex.replace(finalUrl, rule.replacement)
                // Validate candidate is still a safe http/https URI with non-empty host
                val candidateUri = URI(candidate)
                val candScheme = candidateUri.scheme?.lowercase(Locale.US)
                if ((candScheme == "http" || candScheme == "https") && !candidateUri.host.isNullOrBlank()) {
                    if (candidate != finalUrl) {
                        finalUrl = candidate
                        appliedRegexNames.add(rule.name)
                    }
                }
            } catch (_: Exception) {
                // Ignore invalid user regex without corrupting URL
            }
        }

        // Encode spaces if any slipped in
        val safeUrl = finalUrl.replace(" ", URLEncoder.encode(" ", "UTF-8"))

        return UrlCleanResult(
            originalInput = url,
            cleanedOutput = safeUrl,
            extractedUrlsCount = 1,
            removedParameters = removedParams,
            appliedRegexNames = appliedRegexNames,
            wasModified = safeUrl != url,
            errorMessage = null
        )
    }
}
