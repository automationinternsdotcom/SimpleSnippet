package com.simplesnippet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplesnippet.app.data.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    config: AppConfig,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    onBack: () -> Unit
) {
    var t by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onStartTest(); onDispose { onStopTest() } }

    // Derived from the live config, not hardcoded: shortcuts are free-form now,
    // so a fixed "..email" preset would simply fail to expand for most users.
    // Each preset puts the shortcut at end-of-text after a space, which the
    // default (end-anchored, boundary-checked) matching rules accept.
    val presets = remember(config.snippets, config.saveSnippetPattern) {
        val expandable = config.snippets
            .filter { s -> s.contents.any { c -> c.isNotEmpty() } }
            .take(3)
            .map { s -> "Preview: " + s.trigger }
        val saveExample = config.saveSnippetPattern
            .replaceFirst("%", "ph")
            .replaceFirst("%", "+1 555 0199")
        expandable + saveExample
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Test Lab") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.primary, navigationIconContentColor = MaterialTheme.colorScheme.primary)) }) { p ->
        Column(modifier = Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("The Accessibility Service is active here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom=8.dp))
            OutlinedTextField(value = t, onValueChange = { t=it }, label = { Text("Type or tap a preset...") }, modifier = Modifier.fillMaxWidth().height(150.dp))
            Spacer(Modifier.height(24.dp))
            Text("Quick Test Shortcuts:", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            presets.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { t = item }, elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(item, fontSize = 14.sp) }
                }
            }
        }
    }
}