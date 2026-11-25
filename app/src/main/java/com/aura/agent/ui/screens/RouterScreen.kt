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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.RouterConfig
import com.aura.agent.data.RouterStats
import com.aura.agent.data.RoutingMode
import com.aura.agent.router.ProviderHealth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterScreen(
    config: RouterConfig,
    stats: RouterStats,
    cacheCount: Int,
    providers: List<ProviderConfig>,
    healthMap: Map<String, ProviderHealth>,
    onBack: () -> Unit,
    onUpdateConfig: (RouterConfig) -> Unit,
    gatewayRunning: Boolean,
    gatewayStatusLog: String,
    onToggleGateway: (Boolean) -> Unit,
    onClearCache: () -> Unit
) {
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
                title = { Text("AuraRouter & Smart Cache") }
            )
        }
    ) { padding ->
        val clipboard = LocalClipboardManager.current
        val openCodeSnippet = "{\n  \"provider\": {\n    \"aura\": {\n      \"npm\": \"@ai-sdk/openai-compatible\",\n      \"name\": \"AuraRouter Local\",\n      \"options\": { \"baseURL\": \"http://127.0.0.1:8910/v1\", \"apiKey\": \"aura-local\" },\n      \"models\": { \"aura/auto\": { \"name\": \"Aura Smart Auto\" } }\n    }\n  }\n}"
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
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ToggleRow(
                            title = "Local API Aggregator (OpenCode / CLI)",
                            subtitle = "OpenAI-compatible endpoint: http://127.0.0.1:8910/v1 (model: aura/auto)",
                            checked = gatewayRunning,
                            onCheckedChange = onToggleGateway
                        )
                        Text(
                            text = gatewayStatusLog,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (gatewayRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(openCodeSnippet)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Text(" Copy OpenCode Config (opencode.json)")
                        }
                    }
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Cache Hit Rate: ${stats.hitRatePercent}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatPill("Exact Hits", "${stats.exactCacheHits}")
                            StatPill("Semantic Hits", "${stats.semanticCacheHits}")
                            StatPill("Fallbacks", "${stats.fallbackRecoveries}")
                            StatPill("Saved ~Tok", "${stats.estimatedTokensSaved}")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Stored entries: $cacheCount / ${config.maxCacheEntries}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onClearCache) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                Text(" Clear")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Routing Strategy",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(RoutingMode.entries) { mode ->
                Surface(
                    onClick = { onUpdateConfig(config.copy(mode = mode)) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        RadioButton(
                            selected = config.mode == mode,
                            onClick = { onUpdateConfig(config.copy(mode = mode)) }
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
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ToggleRow(
                            title = "Auto-Fallback & Circuit Breaker",
                            subtitle = "Automatically switch provider on 429/5xx or timeout",
                            checked = config.enableFallback,
                            onCheckedChange = { onUpdateConfig(config.copy(enableFallback = it)) }
                        )
                        ToggleRow(
                            title = "Exact Normalized Cache",
                            subtitle = "Instant 0ms replies for identical Persian/English prompts",
                            checked = config.exactCacheEnabled,
                            onCheckedChange = { onUpdateConfig(config.copy(exactCacheEnabled = it)) }
                        )
                        ToggleRow(
                            title = "Semantic Similarity Cache",
                            subtitle = "Matches rephrased prompts locally via trigram + token similarity",
                            checked = config.semanticCacheEnabled,
                            onCheckedChange = { onUpdateConfig(config.copy(semanticCacheEnabled = it)) }
                        )
                        if (config.semanticCacheEnabled) {
                            Column {
                                Text(
                                    text = "Semantic Match Threshold: ${(config.semanticThreshold * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = config.semanticThreshold,
                                    onValueChange = { onUpdateConfig(config.copy(semanticThreshold = it)) },
                                    valueRange = 0.72f..0.96f
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Provider Health & Latency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(providers, key = { it.id }) { p ->
                val h = healthMap[p.id]
                val hasKey = p.apiKey.isNotBlank() || p.baseUrl.contains("11434") || p.baseUrl.contains("localhost")
                val statusText = when {
                    !hasKey -> "No API Key"
                    h?.isInCooldown() == true -> "Cooldown (${h.consecutiveFailures} fails)"
                    h != null && h.avgLatencyMs > 0 -> "Healthy · ${h.avgLatencyMs}ms avg"
                    else -> "Ready"
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                p.model,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                !hasKey || h?.isInCooldown() == true -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
