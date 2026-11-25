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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.agent.data.Gem
import com.aura.agent.ui.components.ChatInputBar
import com.aura.agent.ui.components.MessageItem
import com.aura.agent.ui.components.SparkleIcon
import com.aura.agent.ui.theme.GeminiGradientColors
import com.aura.agent.viewmodel.AuraUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AuraUiState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onStop: () -> Unit,
    onLaunchGem: (String) -> Unit,
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showModelMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = state.activeGem?.name ?: "Aura",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Switch model",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "${state.activeProvider?.name ?: "API"} · ${state.activeProvider?.model ?: ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                contentPadding = PaddingValues(vertical = 8.dp),
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
}

@Composable
private fun EmptyGeminiHome(
    activeGem: Gem?,
    gems: List<Gem>,
    onLaunchGem: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "What time is it on my device right now?",
        "Calculate (1250 * 0.18) + 49.99 using your calculator skill",
        "Fetch https://api.github.com/zen and explain it",
        "یه ایده خلاقانه برای اپ اندروید بده"
    )

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        SparkleIcon(size = 36.dp)
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = activeGem?.name ?: "Hello, Explorer",
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                brush = Brush.linearGradient(GeminiGradientColors)
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = activeGem?.description ?: "How can I help you today?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Gems",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(gems.take(6), key = { it.id }) { gem ->
                Surface(
                    onClick = { onLaunchGem(gem.id) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.width(172.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(gem.accentHex).copy(alpha = 0.2f))
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
                        Spacer(modifier = Modifier.height(4.dp))
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

        Spacer(modifier = Modifier.height(22.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { text ->
                Surface(
                    onClick = { onSuggestionClick(text) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}
