package com.aura.agent.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.unit.dp
import com.aura.agent.data.AgentSkill
import com.aura.agent.data.SkillType
import com.aura.agent.skills.SkillExecutor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    skills: List<AgentSkill>,
    onBack: () -> Unit,
    onToggleSkill: (String) -> Unit,
    onSaveSkill: (AgentSkill) -> Unit,
    onDeleteSkill: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

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
                title = { Text("Agent Skills & Tools") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Skill")
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
                    text = "Toggle built-in agent tools, paste SKILL.md instruction packs, or connect custom HTTP API webhooks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(skills, key = { it.id }) { skill ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = skill.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = skill.type.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = skill.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!skill.isBuiltIn) {
                            IconButton(onClick = { onDeleteSkill(skill.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Skill")
                            }
                        }
                        Switch(
                            checked = skill.enabled,
                            onCheckedChange = { onToggleSkill(skill.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSkillDialog(
            onDismiss = { showAddDialog = false },
            onSave = {
                onSaveSkill(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onSave: (AgentSkill) -> Unit
) {
    var selectedType by remember { mutableStateOf(SkillType.PROMPT_SKILL) }
    var rawSkillMd by remember { mutableStateOf("---
name: my-skill
description: Short description
---
Instructions here...") }
    var webhookName by remember { mutableStateOf("") }
    var webhookDesc by remember { mutableStateOf("") }
    var webhookUrl by remember { mutableStateOf("https://") }
    var webhookMethod by remember { mutableStateOf("GET") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Agent Skill") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == SkillType.PROMPT_SKILL,
                        onClick = { selectedType = SkillType.PROMPT_SKILL },
                        label = { Text("SKILL.md") }
                    )
                    FilterChip(
                        selected = selectedType == SkillType.HTTP_WEBHOOK,
                        onClick = { selectedType = SkillType.HTTP_WEBHOOK },
                        label = { Text("HTTP API Tool") }
                    )
                }

                if (selectedType == SkillType.PROMPT_SKILL) {
                    OutlinedTextField(
                        value = rawSkillMd,
                        onValueChange = { rawSkillMd = it },
                        label = { Text("Paste SKILL.md Content") },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = webhookName,
                        onValueChange = { webhookName = it },
                        label = { Text("Tool Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = webhookDesc,
                        onValueChange = { webhookDesc = it },
                        label = { Text("What does this API do?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("Endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("GET", "POST").forEach { m ->
                            FilterChip(
                                selected = webhookMethod == m,
                                onClick = { webhookMethod = m },
                                label = { Text(m) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedType == SkillType.PROMPT_SKILL) {
                        val (name, desc, body) = SkillExecutor.parseSkillMarkdown(rawSkillMd)
                        onSave(
                            AgentSkill(
                                name = name,
                                slug = SkillExecutor.sanitizeToolName(name),
                                description = desc,
                                type = SkillType.PROMPT_SKILL,
                                enabled = true,
                                instructions = body
                            )
                        )
                    } else if (webhookName.isNotBlank() && webhookUrl.startsWith("http")) {
                        onSave(
                            AgentSkill(
                                name = webhookName.trim(),
                                slug = SkillExecutor.sanitizeToolName(webhookName.trim()),
                                description = webhookDesc.trim().ifBlank { "Custom HTTP webhook tool" },
                                type = SkillType.HTTP_WEBHOOK,
                                enabled = true,
                                webhookUrl = webhookUrl.trim(),
                                webhookMethod = webhookMethod
                            )
                        )
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
