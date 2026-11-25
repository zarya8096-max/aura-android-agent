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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.agent.data.TelegramBridgeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(
    config: TelegramBridgeConfig,
    isRunning: Boolean,
    logs: List<String>,
    onBack: () -> Unit,
    onSaveConfig: (TelegramBridgeConfig) -> Unit,
    onTestToken: (TelegramBridgeConfig) -> Unit,
    onToggleBot: (Boolean) -> Unit
) {
    var token by remember(config.botToken) { mutableStateOf(config.botToken) }
    var allowList by remember(config.allowedChatIds) { mutableStateOf(config.allowedChatIds) }
    var baseUrl by remember(config.apiBaseUrl) { mutableStateOf(config.apiBaseUrl.ifBlank { "https://api.telegram.org" }) }
    var enableTools by remember(config.enableTools) { mutableStateOf(config.enableTools) }

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
                title = { Text("Telegram Bot Bridge") }
            )
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
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRunning) "Bot Online ${config.botUsername}" else "Bot Stopped",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Chat with Aura directly from Telegram using Long Polling (no server needed)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { wantRun ->
                                onSaveConfig(
                                    config.copy(
                                        botToken = token.trim(),
                                        allowedChatIds = allowList.trim(),
                                        apiBaseUrl = baseUrl.trim().ifBlank { "https://api.telegram.org" },
                                        enableTools = enableTools
                                    )
                                )
                                onToggleBot(wantRun)
                            }
                        )
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
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Telegram Bot Token (@BotFather)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = allowList,
                            onValueChange = { allowList = it },
                            label = { Text("Allowed Chat IDs / @usernames (optional)") },
                            supportingText = { Text("Leave blank to allow any chat, or comma-separate IDs/usernames") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Telegram API Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Agent Tools in Telegram", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = enableTools, onCheckedChange = { enableTools = it })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onSaveConfig(
                                        config.copy(
                                            botToken = token.trim(),
                                            allowedChatIds = allowList.trim(),
                                            apiBaseUrl = baseUrl.trim().ifBlank { "https://api.telegram.org" },
                                            enableTools = enableTools
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Save") }
                            OutlinedButton(
                                onClick = {
                                    val updated = config.copy(
                                        botToken = token.trim(),
                                        allowedChatIds = allowList.trim(),
                                        apiBaseUrl = baseUrl.trim().ifBlank { "https://api.telegram.org" },
                                        enableTools = enableTools
                                    )
                                    onSaveConfig(updated)
                                    onTestToken(updated)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Verify Token") }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Live Telegram Activity (${logs.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "No activity yet. Paste your bot token, tap Verify Token, and turn the bot on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(logs.take(30)) { line ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
