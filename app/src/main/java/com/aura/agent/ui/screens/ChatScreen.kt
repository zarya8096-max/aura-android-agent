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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.agent.data.Gem
import com.aura.agent.ui.components.ChatInputBar
import com.aura.agent.ui.components.MessageItem
import com.aura.agent.ui.components.SparkleIcon
import com.aura.agent.viewmodel.AuraUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AuraUiState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onToggleDarkLight: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onStop: () -> Unit,
    onLaunchGem: (String) -> Unit,
    onToggleSoulMd: () -> Unit,
    onToggleMemoryMd: () -> Unit,
    onCycleEffort: () -> Unit,
    onRespondApproval: (Boolean) -> Unit,
    onOpenGems: () -> Unit,
    onOpenSkills: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val messages = state.activeSession?.messages.orEmpty()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Box {
                        Surface(
                            onClick = { showModelMenu = true },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = state.activeGem?.name ?: (state.activeProvider?.model?.substringAfterLast("/") ?: "Aura"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Switch model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            state.providers.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(p.name, fontWeight = FontWeight.Medium)
                                            Text(
                                                p.model,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectProvider(p.id)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleDarkLight) {
                        Icon(
                            imageVector = if (state.themePreset.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle Dark/Light Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNewChat) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Chat",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                input = input,
                onInputChange = { input = it },
                onSend = {
                    val toSend = input
                    input = ""
                    onSendMessage(toSend)
                },
                onStop = onStop,
                isGenerating = state.isGenerating,
                activeSkillsCount = state.skills.count { it.enabled },
                useSoulMd = state.effectiveUseSoulMd,
                useMemoryMd = state.effectiveUseMemoryMd,
                reasoningEffort = state.effectiveReasoningEffort,
                onToggleSoulMd = onToggleSoulMd,
                onToggleMemoryMd = onToggleMemoryMd,
                onCycleEffort = onCycleEffort,
                onOpenSkills = onOpenSkills,
                onOpenGems = onOpenGems
            )
        }
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyGeminiHome(
                activeGem = state.activeGem,
                gems = state.gems,
                onLaunchGem = onLaunchGem,
                onSuggestionClick = { onSendMessage(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageItem(message = msg)
                }
            }
        }
    }

    val pending = state.pendingApproval
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { onRespondApproval(false) },
            title = { Text("Tool Approval") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Run tool: ${pending.toolName}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = pending.arguments.take(500),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onRespondApproval(true) }) { Text("Approve") }
            },
            dismissButton = {
                TextButton(onClick = { onRespondApproval(false) }) { Text("Deny") }
            }
        )
    }
}

@Composable
private fun EmptyGeminiHome(
    activeGem: Gem?,
    gems: List<Gem>,
    onLaunchGem: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickPrompts = listOf(
        "Explain a complex topic simply",
        "Write & review clean Kotlin code",
        "Search live info & summarize",
        "یه ایده خلاقانه برام طراحی کن"
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SparkleIcon(size = 28.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = activeGem?.name ?: "Hello",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = activeGem?.description ?: "Where should we start?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Sleek horizontal suggestion pills (like real Gemini Android)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(quickPrompts) { prompt ->
                Surface(
                    onClick = { onSuggestionClick(prompt) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.width(196.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Gems",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(gems.take(6), key = { it.id }) { gem ->
                    Surface(
                        onClick = { onLaunchGem(gem.id) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.width(164.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(gem.accentHex).copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(gem.accentHex),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = gem.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = gem.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
