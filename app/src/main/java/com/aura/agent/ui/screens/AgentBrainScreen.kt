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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.agent.data.Defaults
import com.aura.agent.data.AuditLogEntry
import com.aura.agent.data.GlobalApprovalMode
import com.aura.agent.data.MemoryEntry
import com.aura.agent.data.ToolPermissionMode
import androidx.compose.material3.RadioButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentBrainScreen(
    soulMarkdown: String,
    userProfileMarkdown: String,
    memories: List<MemoryEntry>,
    globalApprovalMode: GlobalApprovalMode,
    toolPolicies: Map<String, ToolPermissionMode>,
    auditLogs: List<AuditLogEntry>,
    onBack: () -> Unit,
    onSetGlobalApproval: (GlobalApprovalMode) -> Unit,
    onSetToolPolicy: (String, ToolPermissionMode) -> Unit,
    onClearAuditLogs: () -> Unit,
    onSaveSoul: (String) -> Unit,
    onSaveUserProfile: (String) -> Unit,
    onAddMemory: (key: String, content: String, category: String, pinned: Boolean) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var soulDraft by remember(soulMarkdown) { mutableStateOf(soulMarkdown) }
    var userDraft by remember(userProfileMarkdown) { mutableStateOf(userProfileMarkdown) }
    var memoryQuery by remember { mutableStateOf("") }
    var showAddMemory by remember { mutableStateOf(false) }

    val tabs = listOf("SOUL.md", "USER.md", "Memory (${memories.size})", "Policies")

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
                title = { Text("Aura Brain") }
            )
        },
        floatingActionButton = {
            if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = { showAddMemory = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Memory")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Edit SOUL.md to shape the agent's core identity, reasoning discipline, and autonomous tool behavior.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { soulDraft = Defaults.DEFAULT_SOUL_MD },
                                    label = { Text("Reset Aura Default") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        soulDraft = "# SOUL.md — Ruthless Minimalist\n- Zero fluff, zero sycophancy.\n- Verify facts with tools before claiming.\n- Write clean, terse, high-signal answers."
                                    },
                                    label = { Text("Minimalist Preset") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        soulDraft = "# SOUL.md — Deep Research Agent\n- Always plan multi-step tool calls (web_search -> http_fetch -> memory_save).\n- Cite concrete facts and save verified findings to long-term memory."
                                    },
                                    label = { Text("Researcher Preset") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = soulDraft,
                            onValueChange = { soulDraft = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        Button(
                            onClick = { onSaveSoul(soulDraft) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save SOUL.md")
                        }
                    }
                }
                1 -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "USER.md holds your enduring personal context, hardware constraints, and preferences injected into every agent run.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = userDraft,
                            onValueChange = { userDraft = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        Button(
                            onClick = { onSaveUserProfile(userDraft) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save USER.md")
                        }
                    }
                }
                2 -> {
                    val filtered = remember(memories, memoryQuery) {
                        val q = memoryQuery.trim().lowercase()
                        if (q.isEmpty()) memories else memories.filter {
                            it.key.lowercase().contains(q) ||
                                it.content.lowercase().contains(q) ||
                                it.category.lowercase().contains(q)
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            OutlinedTextField(
                                value = memoryQuery,
                                onValueChange = { memoryQuery = it },
                                label = { Text("Search long-term memory...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    text = "No memories stored yet. The agent can save them automatically via memory_save, or tap + to add one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                        items(filtered, key = { it.id }) { mem ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = mem.key,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "[${mem.category}]",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = mem.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onTogglePin(mem.id) }) {
                                        Icon(
                                            imageVector = if (mem.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin memory",
                                            tint = if (mem.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete memory")
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    val toolNames = listOf(
                        "http_fetch", "web_search", "file_workspace",
                        "memory_save", "memory_delete", "spawn_subagent",
                        "calculator", "json_query", "text_transform", "device_time"
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "Approval Behavior (Ask Before Actions)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(GlobalApprovalMode.entries) { mode ->
                            Surface(
                                onClick = { onSetGlobalApproval(mode) },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    RadioButton(
                                        selected = globalApprovalMode == mode,
                                        onClick = { onSetGlobalApproval(mode) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mode.label, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            mode.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Per-Tool Permissions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(toolNames) { tool ->
                            val curr = toolPolicies[tool] ?: ToolPermissionMode.ALLOW
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(tool, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ToolPermissionMode.entries.forEach { pm ->
                                            FilterChip(
                                                selected = curr == pm,
                                                onClick = { onSetToolPolicy(tool, pm) },
                                                label = { Text(pm.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                Text(
                                    text = "Audit Log (${auditLogs.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (auditLogs.isNotEmpty()) {
                                    TextButton(onClick = onClearAuditLogs) { Text("Clear") }
                                }
                            }
                        }
                        items(auditLogs.take(25), key = { it.id }) { log ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${log.toolName} · ${log.decision} (${log.sourceGem})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (log.decision == "DENIED" || log.decision == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = log.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMemory) {
        var key by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("project") }
        val cats = listOf("profile", "project", "preference", "finding", "general")

        AlertDialog(
            onDismissRequest = { showAddMemory = false },
            title = { Text("Add Long-Term Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("Key (e.g. server_stack)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(cats) { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Memory Content") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (key.isNotBlank() && content.isNotBlank()) {
                            onAddMemory(key, content, category, true)
                            showAddMemory = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemory = false }) { Text("Cancel") }
            }
        )
    }
}
