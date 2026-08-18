package com.simplesnippet.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.simplesnippet.app.data.AppConfig
import com.simplesnippet.app.data.Snippet
import com.simplesnippet.app.data.SnippetMatcher
import com.simplesnippet.app.service.SnippetAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsScreen(config: AppConfig, onSave: (AppConfig) -> Unit, onNavigate: (String) -> Unit) {
    var showEditDialog by remember { mutableStateOf(false) }
    var tTrigger by remember { mutableStateOf("") }
    var tContents by remember { mutableStateOf(mutableStateListOf<String>()) }

    var originalTrigger by remember { mutableStateOf<String?>(null) }
    var snippetToDelete by remember { mutableStateOf<Snippet?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSortAlphabetical by remember { mutableStateOf(false) }

    val snippets = config.snippets
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        hasPermission = SnippetAccessibilityService.isEnabled(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = SnippetAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredSnippets = remember(snippets, searchQuery, isSortAlphabetical) {
        val filtered = snippets.filter { s ->
            s.trigger.contains(searchQuery, ignoreCase = true) ||
            s.contents.any { it.contains(searchQuery, ignoreCase = true) }
        }
        if (isSortAlphabetical) {
            filtered.sortedBy { it.trigger.lowercase() }
        } else {
            filtered
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SimpleSnippet") },
                actions = {
                    IconButton(onClick = { isSortAlphabetical = !isSortAlphabetical }) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = "Toggle Sort",
                            tint = if (isSortAlphabetical) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.primary, navigationIconContentColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    tTrigger = ""
                    tContents.clear()
                    tContents.add("")
                    originalTrigger = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add New Snippet")
            }
        }
    ) { p ->
        Column(modifier = Modifier.padding(p)) {
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .clickable {
                            context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accessibility service is off", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Tap to enable SimpleSnippet in Accessibility settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Snippet expansion", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (config.isAppEnabled && hasPermission) "Expanding as you type" else "Paused",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.isAppEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !hasPermission) {
                                Toast.makeText(context, "Enable the Accessibility Service first", Toast.LENGTH_SHORT).show()
                                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } else {
                                onSave(config.copy(isAppEnabled = checked))
                            }
                        }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Usage:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Type a snippet's shortcut (e.g. ..email) to expand it.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("Quick Save:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    val saveExample = config.saveSnippetPattern.replaceFirst("%", "shortcut").replaceFirst("%", "content")
                    Text("Type '$saveExample' to save instantly.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search snippets...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )

            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(filteredSnippets) { s ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                tTrigger = s.trigger
                                tContents.clear()
                                tContents.addAll(s.contents)
                                if (tContents.isEmpty()) tContents.add("")
                                originalTrigger = s.trigger
                                showEditDialog = true
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.trigger, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                val subText = if (s.contents.size > 1) "${s.contents.size} variations" else if (s.contents.isNotEmpty()) s.contents[0] else ""
                                Text(subText, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { snippetToDelete = s }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (showEditDialog) {
            // Legacy tolerance: a shortcut migrated from v1 may violate today's
            // rules (whitespace inside a v1 trigger, say). Re-saving it untouched
            // has to stay possible, so validation only applies once the text is
            // actually modified — and an unchanged shortcut is saved verbatim,
            // because trimming it would itself count as a modification.
            val shortcutUnchanged = originalTrigger != null && tTrigger == originalTrigger
            val shortcutToSave = if (shortcutUnchanged) tTrigger else tTrigger.trim()
            val isShortcutValid = shortcutUnchanged || SnippetMatcher.isValidShortcut(shortcutToSave)
            // Display-only gate: a brand-new snippet starts empty, and scolding the
            // user before they have typed anything is noise. Save still keys off
            // isShortcutValid, so the pristine field cannot be saved either way.
            val showShortcutError = !isShortcutValid && tTrigger.isNotEmpty()
            val hasContent = tContents.any { it.isNotBlank() }
            // Runaway risk: expanding into text that still contains the shortcut
            // re-matches on the next keystroke, especially with "expand anywhere"
            // on. Warn, but never block — self-reference is occasionally wanted.
            val selfExpansionRisk = shortcutToSave.isNotEmpty() &&
                tContents.any { it.contains(shortcutToSave) }

            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text(if (originalTrigger == null) "New Snippet" else "Edit Snippet") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tTrigger,
                            onValueChange = { tTrigger = it },
                            label = { Text("Shortcut (e.g. ..email)") },
                            singleLine = true,
                            isError = showShortcutError,
                            supportingText = {
                                Text(
                                    if (showShortcutError) "Shortcut can't be blank or contain spaces."
                                    else "Type this anywhere to expand the snippet.",
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Variations:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
                            LazyColumn {
                                itemsIndexed(tContents.toList()) { index, content ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = content,
                                            onValueChange = { tContents[index] = it },
                                            label = { Text("Variation ${index + 1}") },
                                            modifier = Modifier.weight(1f),
                                            minLines = 1
                                        )
                                        if (tContents.size > 1) {
                                            IconButton(onClick = { tContents.removeAt(index) }) {
                                                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                item {
                                    TextButton(
                                        onClick = { tContents.add("") }
                                    ) {
                                        Icon(Icons.Default.Add, "Add")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add Variation")
                                    }
                                }
                            }
                        }

                        if (selfExpansionRisk) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "A variation contains '$shortcutToSave' — the inserted text may expand again.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        // Disabled rather than silently doing nothing, which is
                        // what the old `if (...)` guard around the save did.
                        enabled = isShortcutValid && hasContent,
                        onClick = {
                            val validContents = tContents.filter { it.isNotBlank() }.toMutableList()
                            val n = snippets.toMutableList()

                            // Duplicate check: creating a new snippet, or renaming
                            // an existing one onto a shortcut already in use.
                            if (shortcutToSave != originalTrigger && n.any { it.trigger == shortcutToSave }) {
                                Toast.makeText(context, "Snippet '$shortcutToSave' already exists!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (originalTrigger != null) n.removeIf { it.trigger == originalTrigger }
                            n.removeIf { it.trigger == shortcutToSave }
                            n.add(Snippet(shortcutToSave, contents = validContents))
                            onSave(config.copy(snippets = n))
                            showEditDialog = false
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (snippetToDelete != null) {
            AlertDialog(
                onDismissRequest = { snippetToDelete = null },
                title = { Text("Delete Snippet?") },
                text = { Text("Are you sure you want to delete '${snippetToDelete?.trigger}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val n = snippets.toMutableList()
                            n.remove(snippetToDelete)
                            onSave(config.copy(snippets = n))
                            snippetToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { snippetToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}
