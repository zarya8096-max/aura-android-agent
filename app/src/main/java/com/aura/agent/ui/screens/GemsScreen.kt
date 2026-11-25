package com.aura.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import com.aura.agent.data.Gem
import com.aura.agent.data.ReasoningEffort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemsScreen(
    gems: List<Gem>,
    onBack: () -> Unit,
    onLaunchGem: (String) -> Unit,
    onSaveGem: (Gem) -> Unit,
    onDeleteGem: (String) -> Unit
) {
    var editingGem by remember { mutableStateOf<Gem?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

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
                title = { Text("Gem Manager") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creatingNew = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Gem")
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
                    text = "Create custom versions of Aura with dedicated instructions, personas, and expertise.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(gems, key = { it.id }) { gem ->
                Surface(
                    onClick = { onLaunchGem(gem.id) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(gem.accentHex).copy(alpha = 0.22f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color(gem.accentHex),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = gem.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = gem.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            onSaveGem(
                                gem.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = "${gem.name} Copy",
                                    isBuiltIn = false
                                )
                            )
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate Gem")
                        }
                        IconButton(onClick = { editingGem = gem }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Gem")
                        }
                        if (!gem.isBuiltIn) {
                            IconButton(onClick = { onDeleteGem(gem.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Gem")
                            }
                        }
                    }
                }
            }
        }
    }

    if (creatingNew || editingGem != null) {
        GemEditorDialog(
            initial = editingGem,
            onDismiss = {
                creatingNew = false
                editingGem = null
            },
            onSave = {
                onSaveGem(it)
                creatingNew = false
                editingGem = null
            }
        )
    }
}

@Composable
private fun GemEditorDialog(
    initial: Gem?,
    onDismiss: () -> Unit,
    onSave: (Gem) -> Unit
) {
    val palette = listOf(0xFF8AB4F8, 0xFFC58AF9, 0xFF81C995, 0xFFFDD663, 0xFFF28B82)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var accentHex by remember { mutableStateOf(initial?.accentHex ?: palette.first()) }
    var useSoulMd by remember { mutableStateOf(initial?.useSoulMd ?: true) }
    var useMemoryMd by remember { mutableStateOf(initial?.useMemoryMd ?: true) }
    var effort by remember { mutableStateOf(initial?.reasoningEffort ?: ReasoningEffort.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Gem" else "Edit Gem") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Gem Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("System Instructions") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use SOUL.md", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useSoulMd, onCheckedChange = { useSoulMd = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use MEMORY.md", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useMemoryMd, onCheckedChange = { useMemoryMd = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReasoningEffort.entries.forEach { e ->
                        FilterChip(
                            selected = effort == e,
                            onClick = { effort = e },
                            label = { Text(e.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    palette.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(if (accentHex == hex) 32.dp else 26.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { accentHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            (initial ?: Gem(name = "", description = "", instructions = "")).copy(
                                name = name.trim(),
                                description = description.trim().ifBlank { "Custom Gem" },
                                instructions = instructions.trim(),
                                accentHex = accentHex,
                                useSoulMd = useSoulMd,
                                useMemoryMd = useMemoryMd,
                                reasoningEffort = effort
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
