package com.aura.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.agent.data.ChatMessage
import com.aura.agent.data.ToolCallTrace

@Composable
fun MessageItem(message: ChatMessage) {
    if (message.role == "user") {
        UserMessageBubble(message)
    } else {
        AssistantMessageBlock(message)
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageBlock(message: ChatMessage) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            SparkleIcon(
                size = 22.dp,
                animate = message.isStreaming,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (message.toolTraces.isNotEmpty()) {
                    message.toolTraces.forEach { trace ->
                        ToolTraceCard(trace)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (message.content.isBlank() && message.isStreaming) {
                    Text(
                        text = "Thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FormattedResponseText(
                        text = message.content,
                        isError = message.isError,
                        onCopyCode = { code -> clipboard.setText(AnnotatedString(code)) }
                    )
                }

                if (!message.isStreaming && message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(message.content)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTraceCard(trace: ToolCallTrace) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = if (trace.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Used skill: ${trace.toolName}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Args: ${trace.arguments}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = trace.result.take(1000),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = if (trace.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun FormattedResponseText(
    text: String,
    isError: Boolean,
    onCopyCode: (String) -> Unit
) {
    val segments = remember(text) { splitMarkdownCodeBlocks(text) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { seg ->
                if (seg.isCode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = seg.language.ifBlank { "code" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onCopyCode(seg.content) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = seg.content,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else if (seg.content.isNotBlank()) {
                    Text(
                        text = seg.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private data class TextSegment(val content: String, val isCode: Boolean, val language: String = "")

private fun splitMarkdownCodeBlocks(raw: String): List<TextSegment> {
    val result = mutableListOf<TextSegment>()
    val regex = Regex("```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```")
    var lastIdx = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIdx) {
            result.add(TextSegment(raw.substring(lastIdx, match.range.first).trim(), false))
        }
        result.add(TextSegment(match.groupValues[2].trimEnd(), true, match.groupValues[1]))
        lastIdx = match.range.last + 1
    }
    if (lastIdx < raw.length) {
        result.add(TextSegment(raw.substring(lastIdx).trim(), false))
    }
    return result.ifEmpty { listOf(TextSegment(raw, false)) }
}
