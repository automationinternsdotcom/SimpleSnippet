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
import com.simplesnippet.app.data.SnippetMatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    // Drafts are re-keyed on the persisted value so an external config change resyncs them.
    var savePatternDraft by remember(config.saveSnippetPattern) { mutableStateOf(config.saveSnippetPattern) }
    var delayDraft by remember(config.triggerDebounceMs) { mutableStateOf(config.triggerDebounceMs.toFloat()) }

    // Validate and apply the trimmed value, so surrounding whitespace can never
    // become part of a literal segment of the pattern.
    val savePatternCandidate = savePatternDraft.trim()
    val isSavePatternValid = SnippetMatcher.isValidSavePattern(savePatternCandidate)
    val savePatternPreview = savePatternCandidate
        .replaceFirst("%", "shortcut")
        .replaceFirst("%", "content")

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

            // 1. Quick-save pattern. Applied on confirm, never per keystroke: the accessibility
            // service reloads its config on every prefs change, so persisting a half-typed
            // pattern would silently disable quick-save until the user finished typing.
            // Invalid input can't be applied at all — an unusable pattern makes
            // SnippetMatcher.findSaveCommand return null with no visible error.
            OutlinedTextField(
                value = savePatternDraft,
                onValueChange = { savePatternDraft = it },
                label = { Text("Quick-save pattern") },
                singleLine = true,
                isError = !isSavePatternValid,
                supportingText = {
                    Text(
                        if (isSavePatternValid) "Type '$savePatternPreview' in any field to save a snippet on the spot."
                        else "Needs exactly two % placeholders with text before, between, and after them.",
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onSave(config.copy(saveSnippetPattern = savePatternCandidate)) },
                    enabled = isSavePatternValid && savePatternCandidate != config.saveSnippetPattern
                ) { Text("Apply") }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Match shortcuts anywhere vs. only at the caret's end position.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Expand anywhere in text", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Match shortcuts mid-text instead of only at the end",
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

            // 3. Debounce. Persisted on gesture end only, so dragging doesn't spam the service.
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
                "How long to wait after typing a shortcut before a single-variation snippet auto-expands.",
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
