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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.agent.data.DownloadedLocalModel
import com.aura.agent.hf.HfGgufFile
import com.aura.agent.hf.HfModelSummary
import com.aura.agent.viewmodel.DownloadProgress
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(
    localModels: List<DownloadedLocalModel>,
    hfResults: List<HfModelSummary>,
    selectedRepoId: String?,
    selectedRepoFiles: List<HfGgufFile>,
    loading: Boolean,
    error: String?,
    activeDownload: DownloadProgress?,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onInspectRepo: (String) -> Unit,
    onCloseInspect: () -> Unit,
    onDownloadFile: (String, HfGgufFile) -> Unit,
    onCancelDownload: () -> Unit,
    onActivateModel: (DownloadedLocalModel) -> Unit,
    onDeleteModel: (DownloadedLocalModel) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val quickTags = listOf("Qwen2.5", "Llama-3.2", "DeepSeek-R1", "Gemma-3", "Phi-4", "Mistral")

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
                title = { Text("Local Models · Hugging Face") }
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
            if (activeDownload != null) {
                item {
                    val pct = if (activeDownload.totalBytes > 0) {
                        (activeDownload.downloadedBytes.toFloat() / activeDownload.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Downloading ${activeDownload.fileName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onCancelDownload) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                                }
                            }
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${formatBytes(activeDownload.downloadedBytes)} / ${formatBytes(activeDownload.totalBytes)} (${(pct * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (localModels.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloaded on Device (${localModels.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(localModels, key = { it.id }) { m ->
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${m.repoId} · ${formatBytes(m.sizeBytes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteModel(m) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete model")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onActivateModel(m) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Text(" Use Model")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val cmd = "llama-server -m \"${m.localPath}\" --host 127.0.0.1 --port 8080 -c 4096"
                                        clipboard.setText(AnnotatedString(cmd))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                    Text(" Run Cmd")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Hugging Face GGUF Catalog (Live)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search any GGUF model on Hugging Face...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onSearch(query) }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickTags) { tag ->
                        FilterChip(
                            selected = query.equals(tag, ignoreCase = true),
                            onClick = {
                                query = tag
                                onSearch(tag)
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            if (loading) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            items(hfResults, key = { it.repoId }) { repo ->
                Surface(
                    onClick = { onInspectRepo(repo.repoId) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = repo.repoId,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${repo.downloads} downloads · ${repo.likes} likes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Browse GGUF files",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (selectedRepoId != null) {
        AlertDialog(
            onDismissRequest = onCloseInspect,
            title = { Text(selectedRepoId, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                if (loading) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                    }
                } else if (selectedRepoFiles.isEmpty()) {
                    Text("No .gguf files found in main branch.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        items(selectedRepoFiles, key = { it.path }) { f ->
                            Surface(
                                onClick = {
                                    onDownloadFile(selectedRepoId, f)
                                    onCloseInspect()
                                },
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = f.path.substringAfterLast("/"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = formatBytes(f.sizeBytes),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(Icons.Rounded.Download, contentDescription = "Download")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onCloseInspect) { Text("Close") }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return String.format(Locale.US, "%.2f GB", gb)
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
