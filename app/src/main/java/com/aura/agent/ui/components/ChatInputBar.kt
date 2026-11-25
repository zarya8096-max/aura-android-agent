package com.aura.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.agent.commands.SlashCommandRegistry
import com.aura.agent.data.ReasoningEffort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    activeSkillsCount: Int,
    useSoulMd: Boolean,
    useMemoryMd: Boolean,
    reasoningEffort: ReasoningEffort,
    onToggleSoulMd: () -> Unit,
    onToggleMemoryMd: () -> Unit,
    onCycleEffort: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenGems: () -> Unit
) {
    var showControlsSheet by remember { mutableStateOf(false) }

    val matchingCommands = remember(input) {
        if (input.startsWith("/") && !input.contains(" ")) {
            SlashCommandRegistry.filter(input)
        } else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        AnimatedVisibility(visible = matchingCommands.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .heightIn(max = 220.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                    items(matchingCommands, key = { it.command }) { spec ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val needsArg = spec.usage.contains(" ")
                                    onInputChange(if (needsArg) "${spec.command} " else spec.command)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = spec.command,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = spec.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Authentic Gemini-style floating rounded pill composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp, end = 6.dp)
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = "Ask Aura",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { showControlsSheet = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Chat Controls",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onInputChange("/") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = "Slash Commands",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenGems,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Gems",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        // Compact subtle status pill showing current effort / SOUL / MEM state
                        Surface(
                            onClick = { showControlsSheet = true },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(50)
                        ) {
                            val label = buildString {
                                append(reasoningEffort.label)
                                if (!useSoulMd) append(" · No SOUL")
                                if (!useMemoryMd) append(" · No Mem")
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    val canSend = input.isNotBlank() && !isGenerating
                    IconButton(
                        onClick = { if (isGenerating) onStop() else if (canSend) onSend() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isGenerating -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                    canSend -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                    ) {
                        Icon(
                            imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                            contentDescription = if (isGenerating) "Stop" else "Send",
                            tint = when {
                                isGenerating -> MaterialTheme.colorScheme.error
                                canSend -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showControlsSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text("Chat Context & Reasoning", style = MaterialTheme.typography.titleLarge)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SOUL.md Identity", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Inject agent persona & Hermes behavior rules",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = useSoulMd, onCheckedChange = { onToggleSoulMd() })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MEMORY.md & USER.md", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Inject personal profile & long-term memories",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = useMemoryMd, onCheckedChange = { onToggleMemoryMd() })
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Thinking / Reasoning Effort", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReasoningEffort.entries.forEach { eff ->
                            FilterChip(
                                selected = reasoningEffort == eff,
                                onClick = {
                                    if (reasoningEffort != eff) {
                                        // Cycle until matching
                                        var tries = 0
                                        while (tries < 3) {
                                            onCycleEffort()
                                            tries++
                                            break
                                        }
                                    }
                                },
                                label = { Text("${eff.label} (${eff.maxToolSteps} steps)") }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = {
                            showControlsSheet = false
                            onOpenSkills()
                        },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tools ($activeSkillsCount)", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Surface(
                        onClick = {
                            showControlsSheet = false
                            onOpenGems()
                        },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gems", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
