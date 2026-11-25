package com.aura.agent.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.agent.data.AgentSkill
import com.aura.agent.data.Gem
import com.aura.agent.data.GemKnowledgeFile
import com.aura.agent.data.ReasoningEffort
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemsScreen(
    gems: List<Gem>,
    skills: List<AgentSkill>,
    onBack: () -> Unit,
    onLaunchGem: (String) -> Unit,
    onSaveGem: (Gem) -> Unit,
    onDeleteGem: (String) -> Unit,
    onRewriteInstructions: (String, (String) -> Unit) -> Unit
) {
    var editorTarget by remember { mutableStateOf<Gem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (isCreatingNew || editorTarget != null) {
        GemStudioFullScreen(
            initial = editorTarget,
            allSkills = skills,
            onCancel = {
                isCreatingNew = false
                editorTarget = null
            },
            onSave = {
                onSaveGem(it)
                isCreatingNew = false
                editorTarget = null
            },
            onRewriteInstructions = onRewriteInstructions
        )
        return
    }

    val myGems = gems.filter { !it.isBuiltIn }
    val premadeGems = gems.filter { it.isBuiltIn }

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
            ExtendedFloatingActionButton(
                onClick = { isCreatingNew = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New Gem") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = "Create custom AI experts with dedicated instructions, knowledge files, and default tools.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (myGems.isNotEmpty()) {
                item {
                    Text(
                        text = "My Gems",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(myGems, key = { it.id }) { gem ->
                    GemCardRow(
                        gem = gem,
                        onLaunch = { onLaunchGem(gem.id) },
                        onTogglePin = { onSaveGem(gem.copy(pinned = !gem.pinned)) },
                        onCopy = {
                            onSaveGem(gem.copy(id = UUID.randomUUID().toString(), name = "${gem.name} Copy", isBuiltIn = false))
                        },
                        onEdit = { editorTarget = gem },
                        onDelete = { onDeleteGem(gem.id) }
                    )
                }
            }

            item {
                Text(
                    text = "Premade by Aura",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(premadeGems, key = { it.id }) { gem ->
                GemCardRow(
                    gem = gem,
                    onLaunch = { onLaunchGem(gem.id) },
                    onTogglePin = { onSaveGem(gem.copy(pinned = !gem.pinned)) },
                    onCopy = {
                        // Just like Google Gemini: "Make a copy" forks a premade Gem into My Gems and opens editor!
                        val forked = gem.copy(
                            id = UUID.randomUUID().toString(),
                            name = "My ${gem.name}",
                            isBuiltIn = false,
                            pinned = true
                        )
                        editorTarget = forked
                    },
                    onEdit = { editorTarget = gem },
                    onDelete = null
                )
            }
        }
    }
}

@Composable
private fun GemCardRow(
    gem: Gem,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        onClick = onLaunch,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(gem.accentHex).copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = gemIconFor(gem.iconName),
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
                if (gem.knowledgeFiles.isNotEmpty() || gem.defaultSkillIds.isNotEmpty()) {
                    Text(
                        text = buildList {
                            if (gem.knowledgeFiles.isNotEmpty()) add("${gem.knowledgeFiles.size} knowledge file(s)")
                            if (gem.defaultSkillIds.isNotEmpty()) add("${gem.defaultSkillIds.size} tools")
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (gem.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    tint = if (gem.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Make a copy", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GemStudioFullScreen(
    initial: Gem?,
    allSkills: List<AgentSkill>,
    onCancel: () -> Unit,
    onSave: (Gem) -> Unit,
    onRewriteInstructions: (String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val palette = listOf(0xFF8AB4F8, 0xFFC58AF9, 0xFF81C995, 0xFFFDD663, 0xFFF28B82)
    val iconChoices = listOf("sparkle", "code", "lightbulb", "school", "edit", "work", "hub")

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var accentHex by remember { mutableStateOf(initial?.accentHex ?: palette.first()) }
    var iconName by remember { mutableStateOf(initial?.iconName ?: "sparkle") }
    var useSoulMd by remember { mutableStateOf(initial?.useSoulMd ?: true) }
    var useMemoryMd by remember { mutableStateOf(initial?.useMemoryMd ?: true) }
    var pinned by remember { mutableStateOf(initial?.pinned ?: true) }
    var effort by remember { mutableStateOf(initial?.reasoningEffort ?: ReasoningEffort.MEDIUM) }
    var selectedSkills by remember { mutableStateOf(initial?.defaultSkillIds.orEmpty().toSet()) }
    var knowledgeFiles by remember { mutableStateOf(initial?.knowledgeFiles.orEmpty()) }
    var rewriting by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && knowledgeFiles.size < 10) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val fileName = uri.lastPathSegment?.substringAfterLast("/")?.ifBlank { "knowledge.txt" } ?: "knowledge.txt"
                if (text.isNotBlank()) {
                    knowledgeFiles = knowledgeFiles + GemKnowledgeFile(name = fileName, content = text.take(12000))
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Cancel")
                    }
                },
                title = { Text(if (initial == null) "New Gem" else "Edit Gem") },
                actions = {
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onSave(
                                (initial ?: Gem(name = "", description = "", instructions = "")).copy(
                                    name = name.trim(),
                                    description = description.trim().ifBlank { "Custom Gem" },
                                    instructions = instructions.trim(),
                                    iconName = iconName,
                                    accentHex = accentHex,
                                    useSoulMd = useSoulMd,
                                    useMemoryMd = useMemoryMd,
                                    pinned = pinned,
                                    reasoningEffort = effort,
                                    defaultSkillIds = selectedSkills.toList(),
                                    knowledgeFiles = knowledgeFiles
                                )
                            )
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Instructions + Google Gemini Magic Wand Rewrite Button!
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Instructions", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        enabled = instructions.isNotBlank() && !rewriting,
                        onClick = {
                            rewriting = true
                            onRewriteInstructions(instructions) { expanded ->
                                instructions = expanded
                                rewriting = false
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (rewriting) "Rewriting..." else "Rewrite with Aura")
                    }
                }
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    placeholder = { Text("Describe the Gem's persona, goals, tone, and constraints...") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Knowledge Files Section (up to 10 reference files, just like Google Gemini Gems!)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Knowledge (${knowledgeFiles.size}/10)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Attach text, markdown, JSON, or code files for this Gem to reference",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            enabled = knowledgeFiles.size < 10,
                            onClick = { filePicker.launch(arrayOf("text/*", "application/json", "*/*")) }
                        ) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Add")
                        }
                    }
                    knowledgeFiles.forEach { kf ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${kf.name} (${kf.content.length} chars)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { knowledgeFiles = knowledgeFiles.filterNot { it.id == kf.id } }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove file", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Default Tools Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default Tools", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allSkills, key = { it.id }) { sk ->
                        val active = sk.id in selectedSkills
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedSkills = if (active) selectedSkills - sk.id else selectedSkills + sk.id
                            },
                            label = { Text(sk.name) }
                        )
                    }
                }
            }

            // Context & Appearance Toggles
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Use SOUL.md", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = useSoulMd, onCheckedChange = { useSoulMd = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Use MEMORY.md", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = useMemoryMd, onCheckedChange = { useMemoryMd = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Pin to Sidebar", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = pinned, onCheckedChange = { pinned = it })
                    }
                    Text("Default Thinking Effort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReasoningEffort.entries.forEach { e ->
                            FilterChip(selected = effort == e, onClick = { effort = e }, label = { Text(e.label) })
                        }
                    }
                    Text("Icon & Accent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(iconChoices) { ic ->
                            FilterChip(
                                selected = iconName == ic,
                                onClick = { iconName = ic },
                                label = { Icon(gemIconFor(ic), contentDescription = ic, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun gemIconFor(name: String): ImageVector = when (name) {
    "code" -> Icons.Rounded.Code
    "lightbulb" -> Icons.Rounded.Lightbulb
    "school" -> Icons.Rounded.School
    "edit" -> Icons.Outlined.Edit
    "work" -> Icons.Rounded.Work
    "hub" -> Icons.Rounded.Hub
    else -> Icons.Rounded.AutoAwesome
}
