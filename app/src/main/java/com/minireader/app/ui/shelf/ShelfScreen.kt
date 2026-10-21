package com.minireader.app.ui.shelf

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.minireader.app.data.db.BookEntity
import com.minireader.app.data.prefs.ShelfSort
import com.minireader.app.data.prefs.resolveDark
import com.minireader.app.data.repository.ShelfBook
import com.minireader.app.ui.icons.AppIcons
import com.minireader.app.ui.system.SystemBarAppearance
import com.minireader.app.ui.theme.readerColorScheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ShelfViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> -> viewModel.importBooks(uris) }

    var sortMenuOpen by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<ShelfBook?>(null) }
    var detailTarget by remember { mutableStateOf<ShelfBook?>(null) }
    var detailInfo by remember { mutableStateOf<BookDetails?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ShelfBook?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ShelfBook?>(null) }
    var tagTarget by remember { mutableStateOf<ShelfBook?>(null) }
    var selectedTagIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTagManager by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHost.showSnackbar(message)
        }
    }

    LaunchedEffect(tagTarget) {
        tagTarget?.let { shelfBook ->
            viewModel.loadTagIds(shelfBook.book.id) { ids -> selectedTagIds = ids }
        }
    }

    LaunchedEffect(importState.finished, importState.errors) {
        if (importState.finished && importState.errors.isEmpty()) {
            viewModel.dismissImportResult()
        }
    }

    val effectiveTheme = if (state.displayMode.resolveDark(isSystemInDarkTheme())) {
        state.darkTheme
    } else {
        state.lightTheme
    }
    val homeScheme = remember(effectiveTheme) {
        readerColorScheme(effectiveTheme)
    }

    MaterialTheme(colorScheme = homeScheme) {
        SystemBarAppearance(
            backgroundColor = homeScheme.background,
            darkIcons = homeScheme.background.luminance() > 0.5f,
        )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("mini閱讀器", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${state.books.size} 本書",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) viewModel.onQueryChange("")
                        },
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = if (searchVisible) "關閉搜尋" else "搜尋",
                        )
                    }
                    IconButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/epub+zip", "*/*"))
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "匯入 EPUB")
                    }
                    Box {
                        IconButton(onClick = { moreMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = moreMenuOpen,
                            onDismissRequest = { moreMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("標籤管理") },
                                leadingIcon = {
                                    Icon(Icons.Default.List, contentDescription = null)
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    showTagManager = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("閱讀設定") },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    onOpenSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (searchVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShelfSearchField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        onClear = { viewModel.onQueryChange("") },
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                AppIcons.Sort,
                                contentDescription = "排序：${state.sortOrder.label}",
                                tint = if (state.sortOrder == ShelfSort.ADDED_DESC) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            ShelfSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.sortOrder == sort) {
                                                "✓ ${sort.label}"
                                            } else {
                                                sort.label
                                            },
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(sort)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (state.tags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedTagId == null,
                            onClick = { viewModel.selectTag(null) },
                            label = { Text("全部") },
                        )
                    }
                    items(state.tags, key = { it.id }) { tag ->
                        FilterChip(
                            selected = state.selectedTagId == tag.id,
                            onClick = { viewModel.selectTag(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(tag.color.toInt())),
                                )
                            },
                        )
                    }
                }
            }

            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.books.isEmpty() -> {
                    EmptyShelf(
                        filtered = state.query.isNotBlank() || state.selectedTagId != null,
                        onImport = {
                            importLauncher.launch(arrayOf("application/epub+zip", "*/*"))
                        },
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 112.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.books, key = { it.book.id }) { shelfBook ->
                            BookCard(
                                shelfBook = shelfBook,
                                onClick = { onOpenBook(shelfBook.book.id) },
                                onLongClick = { actionBook = shelfBook },
                            )
                        }
                    }
                }
            }
        }
    }

    if (importState.running) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("匯入中") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = {
                            if (importState.total == 0) 0f
                            else importState.processed.toFloat() / importState.total
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("${importState.processed} / ${importState.total} 個檔案")
                }
            },
            confirmButton = {},
        )
    } else if (importState.finished && importState.errors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissImportResult,
            title = { Text("匯入結果") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "成功 ${importState.success} 本、重複 ${importState.duplicate} 本、" +
                            "失敗 ${importState.failure} 本",
                    )
                    Spacer(Modifier.height(8.dp))
                    importState.errors.forEach { error ->
                        Text(
                            "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportResult) {
                    Text("關閉")
                }
            },
        )
    }

    actionBook?.let { shelfBook ->
        ModalBottomSheet(onDismissRequest = { actionBook = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    shelfBook.book.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                SheetAction("詳細資訊", Icons.Default.Info) {
                    detailTarget = shelfBook
                    detailInfo = null
                    detailLoading = true
                    viewModel.loadDetails(shelfBook.book) { details ->
                        if (detailTarget?.book?.id == shelfBook.book.id) {
                            detailInfo = details
                            detailLoading = false
                        }
                    }
                    actionBook = null
                }
                SheetAction("重新命名", Icons.Default.Edit) {
                    renameTarget = shelfBook
                    renameText = shelfBook.book.displayTitle
                    actionBook = null
                }
                SheetAction("設定標籤", Icons.Default.List) {
                    tagTarget = shelfBook
                    actionBook = null
                }
                SheetAction("刪除書籍", Icons.Default.Delete, destructive = true) {
                    deleteTarget = shelfBook
                    actionBook = null
                }
            }
        }
    }

    detailTarget?.let { target ->
        BookDetailsDialog(
            shelfBook = target,
            details = detailInfo,
            loading = detailLoading,
            onDismiss = {
                detailTarget = null
                detailInfo = null
                detailLoading = false
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重新命名") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("書名") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { renameText = target.book.originalTitle },
                    ) {
                        Text("使用原始書名")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        viewModel.renameBook(target.book.id, renameText)
                        renameTarget = null
                    },
                ) {
                    Text("儲存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("刪除書籍") },
            text = {
                Text("確定要刪除「${target.book.displayTitle}」嗎？檔案、書籤與標籤關聯都會一併移除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(target)
                        deleteTarget = null
                    },
                ) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    tagTarget?.let { target ->
        TagAssignDialog(
            tags = state.tags,
            selected = selectedTagIds,
            onToggle = { tagId ->
                selectedTagIds = if (tagId in selectedTagIds) {
                    selectedTagIds - tagId
                } else {
                    selectedTagIds + tagId
                }
            },
            onSave = {
                viewModel.setTags(target.book.id, selectedTagIds)
                tagTarget = null
            },
            onManage = {
                tagTarget = null
                showTagManager = true
            },
            onDismiss = { tagTarget = null },
        )
    }

    if (showTagManager) {
        TagManagerDialog(
            tags = state.tags,
            onCreate = viewModel::createTag,
            onUpdate = viewModel::updateTag,
            onDelete = viewModel::deleteTag,
            onDismiss = { showTagManager = false },
        )
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    shelfBook: ShelfBook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val book = shelfBook.book
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val coverPath = book.coverPath
            if (!coverPath.isNullOrBlank() && File(coverPath).exists()) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = book.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (book.progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.2f),
                )
            }
            bookStatusLabel(book)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author?.takeIf { it.isNotBlank() } ?: "未知作者",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        book.lastOpenedAt?.let { lastOpened ->
            Text(
                text = relativeTime(lastOpened),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ShelfSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    "搜尋書名、作者或標籤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "清除搜尋",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BookDetailsDialog(
    shelfBook: ShelfBook,
    details: BookDetails?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val book = shelfBook.book
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("詳細資訊") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailSection("書籍資訊")
                DetailRow("書名", book.displayTitle)
                if (book.originalTitle.isNotBlank() && book.originalTitle != book.displayTitle) {
                    DetailRow("原書名", book.originalTitle)
                }
                DetailRow("作者", book.author?.takeIf { it.isNotBlank() } ?: "未知作者")
                DetailRow(
                    "語言",
                    details?.language?.let(::languageLabel)
                        ?: if (loading) "讀取中…" else "未知",
                )
                DetailRow(
                    "字數",
                    details?.let { String.format(Locale.getDefault(), "%,d 字", it.wordCount) }
                        ?: if (loading) "計算中…" else "未知",
                )
                DetailRow(
                    "標籤",
                    shelfBook.tags.joinToString("、") { it.name }.ifBlank { "無" },
                )

                Spacer(Modifier.height(10.dp))
                DetailSection("閱讀狀態")
                DetailRow("狀態", bookStatusLabel(book) ?: "閱讀中")
                DetailRow("閱讀進度", progressSummary(book, details))
                DetailRow(
                    "目前章節",
                    details?.takeIf { it.chapterIndex >= 0 }?.chapterTitle
                        ?: if (loading) "讀取中…" else "尚未開始",
                )
                DetailRow("最近閱讀", book.lastOpenedAt?.let(::formatDateTime) ?: "尚未閱讀")
                DetailRow("加入時間", formatDateTime(book.addedAt))

                Spacer(Modifier.height(10.dp))
                DetailSection("檔案資訊")
                DetailRow("檔案大小", formatFileSize(book.fileSize))
                DetailRow(
                    "章節數",
                    details?.let { "${it.chapterCount} 章" }
                        ?: if (loading) "讀取中…" else "未知",
                )
                DetailRow(
                    "書籤數",
                    details?.let { "${it.bookmarkCount} 個" }
                        ?: if (loading) "讀取中…" else "未知",
                )

                if (loading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        },
    )
}

@Composable
private fun DetailSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun bookStatusLabel(book: BookEntity): String? = when {
    book.lastOpenedAt == null -> "未讀"
    book.progressFraction >= 0.99f -> "已讀完"
    else -> null
}

private fun progressSummary(book: BookEntity, details: BookDetails?): String {
    if (book.lastOpenedAt == null) return "尚未開始"
    val percent = (book.progressFraction * 100).roundToInt()
    val chapter = details
        ?.takeIf { it.chapterIndex >= 0 }
        ?.let { "第 ${it.chapterIndex + 1} / ${it.chapterCount} 章" }
    return if (chapter == null) "$percent%" else "$percent%（$chapter）"
}

private fun relativeTime(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L ->
        String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L ->
        String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun languageLabel(raw: String?): String {
    val key = raw?.trim()?.lowercase()?.replace('_', '-') ?: return "未知"
    return when {
        key == "zh" || key.startsWith("zh-tw") || key.startsWith("zh-hk") ||
            key.startsWith("zh-hant") -> "繁體中文"
        key.startsWith("zh-cn") || key.startsWith("zh-sg") ||
            key.startsWith("zh-hans") -> "簡體中文"
        key.startsWith("en") -> "英文"
        key.startsWith("ja") -> "日文"
        key.startsWith("ko") -> "韓文"
        key.startsWith("fr") -> "法文"
        key.startsWith("de") -> "德文"
        key.startsWith("es") -> "西班牙文"
        else -> raw
    }
}

@Composable
private fun EmptyShelf(filtered: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.List,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (filtered) "找不到符合的書" else "書架還是空的",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (filtered) "換個關鍵字或標籤試試看" else "匯入 EPUB 檔案，開始你的閱讀",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!filtered) {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("匯入 EPUB")
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
