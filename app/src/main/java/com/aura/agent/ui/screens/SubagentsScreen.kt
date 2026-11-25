package com.aura.agent.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.ReasoningEffort
import com.aura.agent.data.SubagentConfig
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubagentsScreen(
    subagents: List<SubagentConfig>,
    providers: List<ProviderConfig>,
    onBack: () -> Unit,
    onSaveSubagent: (SubagentConfig) -> Unit,
    onDeleteSubagent: (String) -> Unit
) {
    var editing by remember { mutableStateOf<SubagentConfig?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Subagents Studio") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Subagent")
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = "Build specialized subagents with their own provider, manual model override, thinking effort, and approval gate. Invoke via spawn_subagent or /subagent <name> <task>.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(subagents, key = { it.id }) { sub ->
                val pName = providers.firstOrNull { it.id == sub.providerId }?.name ?: "Active Provider"
                val mLabel = sub.modelOverride.ifBlank { "Default model" }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = sub.role,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$pName · $mLabel · Effort: ${sub.reasoningEffort.label}" +
                                    if (sub.askBeforeRunning) " · Ask" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { editing = sub }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Subagent")
                        }
                        if (!sub.isBuiltIn) {
                            IconButton(onClick = { onDeleteSubagent(sub.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Subagent")
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        SubagentEditorDialog(
            initial = editing,
            providers = providers,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = {
                onSaveSubagent(it)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun SubagentEditorDialog(
    initial: SubagentConfig?,
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onSave: (SubagentConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var role by remember { mutableStateOf(initial?.role.orEmpty()) }
    var systemPrompt by remember { mutableStateOf(initial?.systemPrompt.orEmpty()) }
    var selectedProviderId by remember { mutableStateOf(initial?.providerId) }
    var modelOverride by remember { mutableStateOf(initial?.modelOverride.orEmpty()) }
    var effort by remember { mutableStateOf(initial?.reasoningEffort ?: ReasoningEffort.MEDIUM) }
    var temperature by remember { mutableFloatStateOf(initial?.temperature ?: 0.4f) }
    var askBefore by remember { mutableStateOf(initial?.askBeforeRunning ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Subagent" else "Edit ${initial.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subagent Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role / Specialty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Instructions") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Assigned Provider", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedProviderId == null,
                            onClick = { selectedProviderId = null },
                            label = { Text("Auto / Active") }
                        )
                    }
                    items(providers, key = { it.id }) { p ->
                        FilterChip(
                            selected = selectedProviderId == p.id,
                            onClick = {
                                selectedProviderId = p.id
                                if (modelOverride.isBlank()) modelOverride = p.model
                            },
                            label = { Text(p.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = modelOverride,
                    onValueChange = { modelOverride = it },
                    label = { Text("Manual Model Override (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Thinking / Reasoning Effort", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReasoningEffort.entries.forEach { e ->
                        FilterChip(
                            selected = effort == e,
                            onClick = { effort = e },
                            label = { Text(e.label) }
                        )
                    }
                }
                Text(
                    text = "Temperature: ${String.format(Locale.US, "%.2f", temperature)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..1.2f
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ask approval before running", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = askBefore, onCheckedChange = { askBefore = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            (initial ?: SubagentConfig(name = "", role = "", systemPrompt = "")).copy(
                                name = name.trim(),
                                role = role.trim().ifBlank { name.trim() },
                                systemPrompt = systemPrompt.trim(),
                                providerId = selectedProviderId,
                                modelOverride = modelOverride.trim(),
                                reasoningEffort = effort,
                                temperature = temperature,
                                askBeforeRunning = askBefore
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
