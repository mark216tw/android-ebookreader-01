package com.minireader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minireader.app.data.db.BookmarkEntity
import com.minireader.app.data.epub.TocEntry
import com.minireader.app.data.prefs.ReaderPrefs
import com.minireader.app.ui.icons.AppIcons
import com.minireader.app.ui.settings.ReaderSettingsContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal val ReaderTopBarHeight = 58.dp

private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun ReaderTopBar(
    title: String,
    progress: Float,
    colors: ReaderColors,
    onBack: () -> Unit,
    onAddBookmark: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(color = colors.background.copy(alpha = 0.96f)) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.foreground,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                IconButton(onClick = onAddBookmark) {
                    Icon(
                        AppIcons.Bookmark,
                        contentDescription = "書籤",
                        tint = colors.foreground,
                    )
                }
                IconButton(onClick = onOpenToc) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = "目錄",
                        tint = colors.foreground,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "排版設定",
                        tint = colors.foreground,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = colors.accent,
                trackColor = colors.foreground.copy(alpha = 0.08f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocSheet(
    toc: List<TocEntry>,
    currentChapterIndex: Int,
    colors: ReaderColors,
    onSelect: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        contentColor = colors.foreground,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "目錄",
                style = MaterialTheme.typography.titleMedium,
                color = colors.foreground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            if (toc.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("此書沒有目錄", color = colors.secondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    items(toc) { entry ->
                        val enabled = entry.chapterIndex != null
                        val isCurrent = entry.chapterIndex == currentChapterIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) {
                                        colors.accent.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable(enabled = enabled) {
                                    if (entry.chapterIndex != null) onSelect(entry)
                                }
                                .padding(
                                    start = (20 + entry.depth.coerceAtMost(6) * 16).dp,
                                    end = 20.dp,
                                    top = 12.dp,
                                    bottom = 12.dp,
                                ),
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) {
                                    colors.foreground
                                } else {
                                    colors.secondary.copy(alpha = 0.5f)
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    colors: ReaderColors,
    onAddCurrent: () -> Unit,
    onSelect: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        contentColor = colors.foreground,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "書籤管理",
                style = MaterialTheme.typography.titleMedium,
                color = colors.foreground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddCurrent)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    AppIcons.Bookmark,
                    contentDescription = null,
                    tint = colors.accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "加入目前位置書籤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accent,
                )
            }
            HorizontalDivider(color = colors.foreground.copy(alpha = 0.08f))
            if (bookmarks.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("還沒有書籤，點上方按鈕加入目前位置。", color = colors.secondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelect(bookmark) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = bookmark.chapterTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accent,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = bookmark.excerpt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.foreground,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                bookmark.note?.let { note ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "備註：$note",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = formatBookmarkTime(bookmark.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondary,
                                )
                            }
                            IconButton(onClick = { onDelete(bookmark) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "刪除書籤",
                                    tint = colors.secondary,
                                )
                            }
                        }
                        HorizontalDivider(color = colors.foreground.copy(alpha = 0.08f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AddBookmarkDialog(
    initialExcerpt: String,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var excerpt by remember(initialExcerpt) { mutableStateOf(initialExcerpt) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入書籤") },
        text = {
            Column {
                OutlinedTextField(
                    value = excerpt,
                    onValueChange = { excerpt = it },
                    label = { Text("書籤內容") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("備註（選填）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(excerpt, note.ifBlank { null }) }) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    prefs: ReaderPrefs,
    onPrefsChange: (ReaderPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "排版設定",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        ReaderSettingsContent(
            prefs = prefs,
            onPrefsChange = onPrefsChange,
            showPreview = false,
        )
    }
}

private fun formatBookmarkTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DATE_TIME_FORMATTER)
}
