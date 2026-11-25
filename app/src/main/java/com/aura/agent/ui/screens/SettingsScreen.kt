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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aura.agent.data.ApiProtocol
import com.aura.agent.data.ProviderConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    providers: List<ProviderConfig>,
    activeProviderId: String,
    darkMode: Boolean,
    onBack: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSaveProvider: (ProviderConfig) -> Unit,
    onDeleteProvider: (String) -> Unit
) {
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var addingNew by remember { mutableStateOf(false) }

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
                title = { Text("API Providers & Settings") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addingNew = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Custom API")
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
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Theme", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Gemini charcoal dark surface",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = darkMode, onCheckedChange = { onToggleDarkMode() })
                    }
                }
            }

            item {
                Text(
                    text = "Configured API Providers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(providers, key = { it.id }) { provider ->
                Surface(
                    onClick = { onSelectProvider(provider.id) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        RadioButton(
                            selected = provider.id == activeProviderId,
                            onClick = { onSelectProvider(provider.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${provider.protocol.label} · ${provider.model}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (provider.apiKey.isBlank()) "API key not set" else "API key configured",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (provider.apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { editingProvider = provider }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Provider")
                        }
                        if (providers.size > 1) {
                            IconButton(onClick = { onDeleteProvider(provider.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Provider")
                            }
                        }
                    }
                }
            }
        }
    }

    if (addingNew || editingProvider != null) {
        ProviderEditorDialog(
            initial = editingProvider,
            onDismiss = {
                addingNew = false
                editingProvider = null
            },
            onSave = {
                onSaveProvider(it)
                addingNew = false
                editingProvider = null
            }
        )
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: ProviderConfig?,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "Custom Provider") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: ApiProtocol.OPENAI_COMPATIBLE) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var model by remember { mutableStateOf(initial?.model ?: "gpt-4o-mini") }
    val presets = initial?.availableModels.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add API Provider" else "Edit ${initial.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ApiProtocol.entries) { proto ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = { protocol = proto },
                            label = { Text(proto.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (presets.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presets) { m ->
                            FilterChip(
                                selected = model == m,
                                onClick = { model = m },
                                label = { Text(m) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()) {
                        onSave(
                            (initial ?: ProviderConfig(
                                name = name,
                                protocol = protocol,
                                baseUrl = baseUrl,
                                model = model
                            )).copy(
                                name = name.trim(),
                                protocol = protocol,
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                model = model.trim()
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
