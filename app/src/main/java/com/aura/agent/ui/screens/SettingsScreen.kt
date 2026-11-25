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
import androidx.compose.material.icons.rounded.NetworkCheck
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
import androidx.compose.material3.Switch
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
import com.aura.agent.data.AppThemePreset
import com.aura.agent.data.ProviderConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    providers: List<ProviderConfig>,
    activeProviderId: String,
    themePreset: AppThemePreset,
    testStatus: String?,
    onBack: () -> Unit,
    onToggleDarkLight: () -> Unit,
    onSelectTheme: (AppThemePreset) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSaveProvider: (ProviderConfig) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onTestProvider: (ProviderConfig) -> Unit
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
                title = { Text("Providers & Appearance") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addingNew = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Custom Provider")
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
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
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
                                Text("Dark Mode", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (themePreset.isDark) "Dark theme active" else "Light theme active",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = themePreset.isDark, onCheckedChange = { onToggleDarkLight() })
                        }
                        Text("Palette Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AppThemePreset.entries) { preset ->
                                FilterChip(
                                    selected = themePreset == preset,
                                    onClick = { onSelectTheme(preset) },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                    }
                }
            }

            if (!testStatus.isNullOrBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = testStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "API Providers (Tap + to add any custom endpoint)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(providers, key = { it.id }) { provider ->
                Surface(
                    onClick = { onSelectProvider(provider.id) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (provider.id == activeProviderId) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
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
                                text = if (provider.apiKey.isBlank()) "API key not set" else "Configured",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (provider.apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onTestProvider(provider) }) {
                            Icon(Icons.Rounded.NetworkCheck, contentDescription = "Test & Fetch Models")
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
    var headersRaw by remember {
        mutableStateOf(
            initial?.customHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()
        )
    }
    val presets = initial?.availableModels.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Manual Provider" else "Edit ${initial.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
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
                    label = { Text("Base URL (e.g. https://.../v1)") },
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
                OutlinedTextField(
                    value = headersRaw,
                    onValueChange = { headersRaw = it },
                    label = { Text("Custom Headers (Key: Value per line)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()) {
                        val parsedHeaders = headersRaw.lines()
                            .mapNotNull { line ->
                                val idx = line.indexOf(':')
                                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                            }
                            .toMap()
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
                                model = model.trim(),
                                customHeaders = parsedHeaders
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
