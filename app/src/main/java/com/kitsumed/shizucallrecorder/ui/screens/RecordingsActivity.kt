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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DeleteSweep
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
    var actionError by remember { mutableStateOf<String?>(null) }
    var isPlaybackPaused by remember { mutableStateOf(false) }
    var isPlayerPrepared by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecordingEntry?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val pageSize = 20

    fun stopPlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingUri = null
        isPlaybackPaused = false
        isPlayerPrepared = false
    }

    fun loadRecordings() {
        scope.launch {
            loading = true
            error = null
            try {
                val folderUri = withContext(Dispatchers.IO) {
                    AppPreferences(context.applicationContext).getRecordingFolderUri()
                }
                val result = withContext(Dispatchers.IO) {
                    if (folderUri == null) {
                        emptyList()
                    } else {
                        val root = runCatching { DocumentFile.fromTreeUri(context, folderUri) }.getOrNull()
                        if (root == null || !root.canRead()) {
                            emptyList()
                        } else {
                            val found = mutableListOf<RecordingEntry>()
                            val stack = ArrayDeque<DocumentFile>()
                            stack.add(root)
                            while (stack.isNotEmpty()) {
                                val directory = stack.removeLast()
                                val children = runCatching { directory.listFiles() }.getOrDefault(emptyArray())
                                for (child in children) {
                                    if (child.isDirectory) {
                                        stack.add(child)
                                    } else if (
                                        child.isFile &&
                                        (child.type?.startsWith("audio/") == true ||
                                            child.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS)
                                    ) {
                                        found.add(
                                            RecordingEntry(
                                                uri = child.uri,
                                                name = child.name ?: "Audio recording",
                                                modified = child.lastModified(),
                                                size = child.length()
                                            )
                                        )
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
                    val rootExists = runCatching {
                        DocumentFile.fromTreeUri(context, folderUri)?.canRead() == true
                    }.getOrDefault(false)
                    if (!rootExists) {
                        error = "The recording folder is unavailable. Re-select it in Settings."
                    }
                }
                recordings = result
                page = 0
            } catch (exception: Exception) {
                error = "Could not read the recording library. Check folder access and try again."
            } finally {
                loading = false
            }
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
                    IconButton(onClick = { confirmDeleteAll = true }, enabled = !loading && recordings.isNotEmpty()) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Delete all recordings")
                    }
                    IconButton(onClick = { loadRecordings() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh recordings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            actionError?.let { message ->
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { actionError = null }) { Text("Dismiss") }
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
                error != null -> Card(shape = MaterialTheme.shapes.large) {
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
                                            if (!isPlayerPrepared) {
                                                // Keep the user's intent while prepareAsync() is still running.
                                                isPlaybackPaused = !isPlaybackPaused
                                            } else {
                                                runCatching {
                                                    if (isPlaybackPaused) activePlayer.start() else activePlayer.pause()
                                                    isPlaybackPaused = !isPlaybackPaused
                                                }.onFailure {
                                                    actionError = "Playback could not be resumed. Try opening the recording again."
                                                    stopPlayer()
                                                }
                                            }
                                        }
                                    } else {
                                        stopPlayer()
                                        actionError = null
                                        runCatching {
                                            MediaPlayer().also { nextPlayer ->
                                                player = nextPlayer
                                                playingUri = item.uri
                                                nextPlayer.setOnPreparedListener { prepared ->
                                                    isPlayerPrepared = true
                                                    if (!isPlaybackPaused) prepared.start()
                                                }
                                                nextPlayer.setOnCompletionListener { completed ->
                                                    runCatching { completed.release() }
                                                    if (playingUri == item.uri) {
                                                        player = null
                                                        playingUri = null
                                                        isPlaybackPaused = false
                                                        isPlayerPrepared = false
                                                    }
                                                }
                                                nextPlayer.setOnErrorListener { failedPlayer, _, _ ->
                                                    runCatching { failedPlayer.release() }
                                                    if (playingUri == item.uri) {
                                                        player = null
                                                        playingUri = null
                                                        isPlaybackPaused = false
                                                        isPlayerPrepared = false
                                                    }
                                                    actionError = "Unable to play this recording. The file may be unavailable or use an unsupported format."
                                                    true
                                                }
                                                nextPlayer.setDataSource(context, item.uri)
                                                nextPlayer.prepareAsync()
                                            }
                                        }.onFailure {
                                            stopPlayer()
                                            actionError = "Unable to play this recording. The file may be unavailable or use an unsupported format."
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

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all recordings?") },
            text = { Text("This will permanently delete all recordings in your selected recording folder. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    scope.launch {
                        val snapshot = recordings
                        val results = withContext(Dispatchers.IO) {
                            snapshot.map { item ->
                                item.uri to runCatching { DocumentFile.fromSingleUri(context, item.uri)?.delete() ?: false }.getOrDefault(false)
                            }
                        }
                        val deletedUris = results.filter { it.second }.map { it.first }.toSet()
                        if (playingUri in deletedUris) stopPlayer()
                        recordings = recordings.filterNot { it.uri in deletedUris }
                        val failed = results.size - deletedUris.size
                        if (failed > 0) actionError = "Some recordings could not be deleted. Check folder permissions."
                        else actionError = "All recordings deleted."
                    }
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
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
                            actionError = "Could not delete this recording. Check folder permissions."
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
        shape = MaterialTheme.shapes.large,
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
                val details = remember(item.name) { parseRecordingName(item.name) }
                Text(details.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(details.caller, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(details.number, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details.direction.isNotBlank()) Text(details.direction, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatSize(item.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share recording") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete recording") }
        }
    }
}

private data class RecordingNameDetails(val date: String, val caller: String, val number: String, val direction: String)

private fun parseRecordingName(fileName: String): RecordingNameDetails {
    val base = fileName.substringBeforeLast('.', fileName)
    val dateMatch = Regex("^(\\d{4})-(\\d{2})-(\\d{2})_(\\d{2})-(\\d{2})-(\\d{2})_(.*)$").find(base)
    if (dateMatch != null) {
        val (year, month, day, hour, minute, second, remainder) = dateMatch.destructured
        val directionMatch = Regex("_(Incoming|Outgoing)_", RegexOption.IGNORE_CASE).find(remainder)
        if (directionMatch != null) {
            val caller = remainder.substring(0, directionMatch.range.first).replace('_', ' ').ifBlank { "Unknown caller" }
            val direction = directionMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            val number = remainder.substring(directionMatch.range.last + 1).ifBlank { "Unknown number" }
            return RecordingNameDetails("$day/$month/$year  $hour:$minute", caller, number, direction)
        }
    }
    return RecordingNameDetails("Legacy recording", "Legacy recording", base, "")
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
