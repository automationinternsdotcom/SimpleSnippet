package com.simplesnippet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplesnippet.app.BuildConfig
import com.simplesnippet.app.data.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    // Drafts are re-keyed on the persisted value so an external config change resyncs them.
    var prefixDraft by remember(config.snippetTriggerPrefix) { mutableStateOf(config.snippetTriggerPrefix) }
    var delayDraft by remember(config.triggerDebounceMs) { mutableStateOf(config.triggerDebounceMs.toFloat()) }

    val isPrefixValid = prefixDraft.isNotBlank() && !prefixDraft.any { it.isWhitespace() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader("EXPANSION")

            // 1. Trigger prefix. Applied on confirm, never per keystroke: the accessibility
            // service reloads its config on every prefs change, so persisting a half-typed
            // prefix ("." on the way to "..") would break live expansion until the user finished.
            OutlinedTextField(
                value = prefixDraft,
                onValueChange = { prefixDraft = it },
                label = { Text("Trigger prefix") },
                singleLine = true,
                isError = !isPrefixValid,
                supportingText = {
                    Text(
                        if (isPrefixValid) "${prefixDraft}email → user@example.com"
                        else "Prefix can't be blank or contain spaces",
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onSave(config.copy(snippetTriggerPrefix = prefixDraft.trim())) },
                    enabled = isPrefixValid && prefixDraft != config.snippetTriggerPrefix
                ) { Text("Apply") }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Quick-save pattern is read-only on purpose: a malformed pattern (wrong number
            // of '%' placeholders) makes SnippetMatcher.findSaveCommand return null, silently
            // disabling quick-save with no visible error.
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Quick save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        config.saveSnippetPattern.replaceFirst("%", "name").replaceFirst("%", "content"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Type this in any text field to save a snippet on the spot.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 3. Match triggers anywhere vs. only at the caret's end position.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Expand anywhere in text", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Match triggers mid-text instead of only at the end",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = config.allowTriggerAnywhere,
                    onCheckedChange = { onSave(config.copy(allowTriggerAnywhere = it)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 4. Debounce. Persisted on gesture end only, so dragging doesn't spam the service.
            Text(
                "Expansion delay: ${delayDraft.toLong()} ms",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = delayDraft,
                valueRange = 100f..1000f,
                steps = 8,
                onValueChange = { delayDraft = it },
                onValueChangeFinished = { onSave(config.copy(triggerDebounceMs = delayDraft.toLong())) }
            )
            Text(
                "How long to wait after typing before a single-variation snippet auto-expands.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("APP")

            SettingsNavRow(
                icon = Icons.Default.Security,
                title = "Permissions & troubleshooting",
                subtitle = null,
                onClick = { onNavigate("permissions") }
            )
            Spacer(Modifier.height(8.dp))
            SettingsNavRow(
                icon = Icons.Default.Science,
                title = "Test Lab",
                subtitle = "Try snippet expansion inside the app",
                onClick = { onNavigate("test") }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "SimpleSnippet v${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
