/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * Licensed under GPL-3.0-or-later; see LICENSE.
 */
package com.kitsumed.shizucallrecorder.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val AUDIO_EXTENSIONS = setOf("aac", "amr", "3gp", "m4a", "mp3", "ogg", "opus", "wav", "flac", "webm")

private data class RecordingEntry(
    val uri: Uri,
    val name: String,
    val modified: Long,
    val size: Long
)

class RecordingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = AppPreferences(applicationContext)
        setContent {
            val darkTheme = when (preferences.getThemeMode()) {
                AppPreferences.ThemeMode.LIGHT -> false
                AppPreferences.ThemeMode.DARK -> true
                AppPreferences.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            ShizuCallRecorderTheme(darkTheme = darkTheme, dynamicColor = preferences.isDynamicColorEnabled()) {
                RecordingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isPlaybackPaused by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecordingEntry?>(null) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val pageSize = 20

    fun stopPlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingUri = null
        isPlaybackPaused = false
    }

    fun loadRecordings() {
        scope.launch {
            loading = true
            error = null
            val folderUri = withContext(Dispatchers.IO) {
                AppPreferences(context.applicationContext).getRecordingFolderUri()
            }
            val result = withContext(Dispatchers.IO) {
                if (folderUri == null) {
                    emptyList()
                } else {
                    val root = runCatching { DocumentFile.fromTreeUri(context, folderUri) }.getOrNull()
                    if (root == null || !root.canRead()) emptyList()
                    else {
                        val found = mutableListOf<RecordingEntry>()
                        val stack = ArrayDeque<DocumentFile>()
                        stack.add(root)
                        while (stack.isNotEmpty()) {
                            val dir = stack.removeLast()
                            val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
                            for (child in children) {
                                if (child.isDirectory) stack.add(child)
                                else if (child.isFile && (child.type?.startsWith("audio/") == true || child.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS)) {
                                    found.add(RecordingEntry(child.uri, child.name ?: "Audio recording", child.lastModified(), child.length()))
                                }
                            }
                        }
                        found.sortedByDescending { it.modified }
                    }
                }
            }
            if (folderUri == null) {
                error = "Choose a recording folder in Settings first."
            } else if (result.isEmpty()) {
                val rootExists = runCatching { DocumentFile.fromTreeUri(context, folderUri)?.canRead() == true }.getOrDefault(false)
                if (!rootExists) error = "The recording folder is unavailable. Re-select it in Settings."
            }
            recordings = result
            page = 0
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadRecordings() }
    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() }; player = null }
    }

    val filtered = remember(recordings, query) {
        if (query.isBlank()) recordings else recordings.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val pageCount = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = page.coerceIn(0, pageCount - 1)
    val visible = filtered.drop(safePage * pageSize).take(pageSize)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recordings")
                        Text(
                            if (loading) "Loading your library…" else "${recordings.size} saved recordings",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadRecordings() }, enabled = !loading) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Refresh recordings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            playbackError?.let { message ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { playbackError = null }) { Text("Dismiss") }
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; page = 0 },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search recordings") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error!!, style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { loadRecordings() }) { Text("Try again") }
                    }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(if (query.isBlank()) "No recordings found" else "No matching recordings", style = MaterialTheme.typography.titleMedium)
                        Text("New recordings saved to your selected folder will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visible, key = { it.uri.toString() }) { item ->
                            RecordingCard(
                                item = item,
                                isPlaying = playingUri == item.uri && !isPlaybackPaused,
                                onPlay = {
                                    if (playingUri == item.uri) {
                                        val activePlayer = player
                                        if (activePlayer != null) {
                                            runCatching {
                                                if (isPlaybackPaused) activePlayer.start() else activePlayer.pause()
                                                isPlaybackPaused = !isPlaybackPaused
                                            }.onFailure {
                                                playbackError = "Playback could not be resumed. Try opening the recording again."
                                                stopPlayer()
                                            }
                                        }
                                    } else {
                                        stopPlayer()
                                        playbackError = null
                                        runCatching {
                                            MediaPlayer().also { nextPlayer ->
                                                player = nextPlayer
                                                playingUri = item.uri
                                                nextPlayer.setOnPreparedListener { prepared ->
                                                    if (!isPlaybackPaused) prepared.start()
                                                }
                                                nextPlayer.setOnCompletionListener { completed ->
                                                    runCatching { completed.release() }
                                                    if (playingUri == item.uri) {
                                                        player = null
                                                        playingUri = null
                                                        isPlaybackPaused = false
                                                    }
                                                }
                                                nextPlayer.setOnErrorListener { failedPlayer, _, _ ->
                                                    runCatching { failedPlayer.release() }
                                                    if (playingUri == item.uri) {
                                                        player = null
                                                        playingUri = null
                                                    }
                                                    playbackError = "Unable to play this recording. The file may be unavailable or use an unsupported format."
                                                    true
                                                }
                                                nextPlayer.setDataSource(context, item.uri)
                                                nextPlayer.prepareAsync()
                                            }
                                        }.onFailure {
                                            stopPlayer()
                                            playbackError = "Unable to play this recording. The file may be unavailable or use an unsupported format."
                                        }
                                    }
                                },
                                onShare = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = context.contentResolver.getType(item.uri) ?: "audio/*"
                                        putExtra(Intent.EXTRA_STREAM, item.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share recording"))
                                },
                                onDelete = { pendingDelete = item }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${safePage * pageSize + 1}–${(safePage * pageSize + visible.size)} of ${filtered.size}", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { page = (safePage - 1).coerceAtLeast(0) }, enabled = safePage > 0) { Text("Previous") }
                            Text("${safePage + 1} / $pageCount", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { page = (safePage + 1).coerceAtMost(pageCount - 1) }, enabled = safePage < pageCount - 1) { Text("Next") }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete recording?") },
            text = { Text("${item.name} will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            runCatching { DocumentFile.fromSingleUri(context, item.uri)?.delete() ?: false }.getOrDefault(false)
                        }
                        if (deleted) {
                            if (playingUri == item.uri) stopPlayer()
                            recordings = recordings.filterNot { it.uri == item.uri }
                        } else {
                            error = "Could not delete this recording. Check folder permissions."
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecordingCard(
    item: RecordingEntry,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onPlay) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause recording" else "Play recording")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(item.modified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(formatSize(item.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share recording") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete recording") }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
