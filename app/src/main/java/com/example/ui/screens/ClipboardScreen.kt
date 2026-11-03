package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ClipboardConfig
import com.example.feature.clipboard.UrlCleanerEngine
import com.example.ui.components.CategoryTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingsGroupCard
import com.example.ui.components.SettingsToggleRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipboardScreen(
    config: ClipboardConfig,
    inputUrl: String,
    cleanResult: UrlCleanerEngine.UrlCleanResult?,
    onInputUrlChange: (String) -> Unit,
    onPasteAndClean: () -> Boolean,
    onCopyCleaned: () -> Boolean,
    onUpdateConfig: ((ClipboardConfig) -> ClipboardConfig) -> Unit,
    onAddCustomParam: (String) -> Unit,
    onRemoveCustomParam: (String) -> Unit,
    onAddRegexRule: (String, String, String) -> Unit,
    onRemoveRegexRule: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var newParamText by remember { mutableStateOf("") }
    var regexName by remember { mutableStateOf("") }
    var regexPattern by remember { mutableStateOf("") }
    var regexReplacement by remember { mutableStateOf("") }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTopBar(
            title = "Clipboard & URL Cleaner",
            subtitle = "100% local URL sanitizer • Zero network • Zero persistent history",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("clipboard_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Interactive Local URL Sanitizer Workspace
            item {
                SectionHeader("Interactive URL Sanitizer")
                SettingsGroupCard {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = onInputUrlChange,
                        label = { Text("URL or Shared Text to Clean") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("url_input_field"),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val ok = onPasteAndClean()
                                statusBanner = if (ok) "Pasted & cleaned from clipboard" else "Clipboard is empty or restricted"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_paste_and_clean")
                        ) {
                            Text("Paste & Clean")
                        }
                        Button(
                            onClick = {
                                val ok = onCopyCleaned()
                                statusBanner = if (ok) "Copied cleaned URL to clipboard!" else "No valid cleaned URL to copy"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_copy_cleaned")
                        ) {
                            Text("Copy Cleaned")
                        }
                    }

                    statusBanner?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (cleanResult != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cleaned_url_output_box")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "CLEANED OUTPUT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = cleanResult.cleanedOutput.ifEmpty { "—" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (cleanResult.errorMessage != null) {
                                    Text(
                                        text = cleanResult.errorMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = if (cleanResult.removedParameters.isEmpty() && cleanResult.appliedRegexNames.isEmpty()) {
                                            "URL is already clean (0 tracking parameters found)"
                                        } else {
                                            "Stripped ${cleanResult.removedParameters.size} tracking parameter(s): ${cleanResult.removedParameters.joinToString(", ")}"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Built-in & Custom Query Parameter Rules
            item {
                SectionHeader("Tracking Parameter Rules")
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Remove Built-In Tracking Parameters (35+ Rules)",
                        subtitle = "Automatically strips utm_*, fbclid, gclid, dclid, msclkid, igshid, si, mc_eid, yclid, mkt_tok, spm, etc.",
                        checked = config.removeBuiltInTracking,
                        onCheckedChange = { rem ->
                            onUpdateConfig { it.copy(removeBuiltInTracking = rem) }
                        },
                        testTag = "toggle_builtin_trackers"
                    )

                    Text(
                        text = "Custom Query Parameters to Strip",
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        config.customQueryParameters.forEach { param ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveCustomParam(param) },
                                label = { Text("?$param=…  ✕") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newParamText,
                            onValueChange = { newParamText = it },
                            label = { Text("Add query key (e.g. affiliate_id)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_custom_param")
                        )
                        OutlinedButton(
                            onClick = {
                                if (newParamText.isNotBlank()) {
                                    onAddCustomParam(newParamText)
                                    newParamText = ""
                                }
                            },
                            modifier = Modifier.testTag("btn_add_custom_param")
                        ) {
                            Text("Add")
                        }
                    }
                }
            }

            // Optional Safe Custom Regex Rules
            item {
                SectionHeader("Custom Regex Rules (Scheme-Validated)")
                SettingsGroupCard {
                    Text(
                        text = "Every regex transformation is validated to preserve a valid http/https hostname and never corrupt non-URL schemes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    config.customRegexRules.forEach { rule ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "/${rule.pattern}/ → '${rule.replacement}'",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(onClick = { onRemoveRegexRule(rule.id) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = regexName,
                        onValueChange = { regexName = it },
                        label = { Text("Rule Name (e.g. Strip Amazon ref path)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regexPattern,
                        onValueChange = { regexPattern = it },
                        label = { Text("Regex Pattern (e.g. /ref=[^/?]+)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regexReplacement,
                        onValueChange = { regexReplacement = it },
                        label = { Text("Replacement (leave empty to remove)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            if (regexName.isNotBlank() && regexPattern.isNotBlank()) {
                                onAddRegexRule(regexName, regexPattern, regexReplacement)
                                regexName = ""
                                regexPattern = ""
                                regexReplacement = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_add_regex_rule")
                    ) {
                        Text("Save Custom Regex Rule")
                    }
                }
            }

            // Android Platform Clipboard Behaviour Explanation
            item {
                SectionHeader("Android 16 Clipboard Architecture")
                SettingsGroupCard {
                    Text(
                        text = "Why Aegis Does Not Poll Your Clipboard in the Background:\n" +
                            "1. Android 10–16 strictly blocks background apps from reading the system clipboard unless the app is the active input method (keyboard) or currently focused.\n" +
                            "2. Continuous background polling wastes CPU and battery and triggers repeated Android clipboard toast warnings.\n" +
                            "3. Instead, use Android's native Share Sheet ('Clean URL (Aegis)'), trigger the URL Cleaner via an Edge Gesture, or tap 'Paste & Clean' above.",
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
}
