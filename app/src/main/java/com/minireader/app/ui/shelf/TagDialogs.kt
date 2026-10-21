package com.minireader.app.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.minireader.app.data.db.TagEntity

private val TagPalette = listOf(
    0xFF8B5E34,
    0xFFB23A48,
    0xFF2F6B3B,
    0xFF2C5F8A,
    0xFF7A4E9E,
    0xFFC27C0E,
    0xFF6B6257,
    0xFFD06B54,
    0xFF00796B,
    0xFFC2185B,
    0xFF827717,
    0xFF3949AB,
    0xFF546E7A,
    0xFFE65100,
)

@Composable
fun TagAssignDialog(
    tags: List<TagEntity>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定標籤") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (tags.isEmpty()) {
                    Text(
                        "尚未建立標籤",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(tag.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = tag.id in selected,
                            onCheckedChange = { onToggle(tag.id) },
                        )
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(tag.color.toInt())),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(tag.name)
                    }
                }
                TextButton(onClick = onManage) {
                    Text("管理標籤")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagerDialog(
    tags: List<TagEntity>,
    onCreate: (String, Long) -> Unit,
    onUpdate: (TagEntity, String, Long) -> Unit,
    onDelete: (TagEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(TagPalette.first()) }
    var editing by remember { mutableStateOf<TagEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<TagEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("標籤管理") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (tags.isEmpty()) {
                    Text(
                        "尚未建立標籤",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(tag.color.toInt())),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(tag.name, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                editing = tag
                                name = tag.name
                                color = tag.color
                            },
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "編輯標籤")
                        }
                        IconButton(onClick = { deleteTarget = tag }) {
                            Icon(Icons.Default.Delete, contentDescription = "刪除標籤")
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                Text(
                    if (editing == null) "新增標籤" else "編輯標籤",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("名稱") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagPalette.forEach { paletteColor ->
                        val selected = paletteColor == color
                        val swatch = Color(paletteColor.toInt())
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { color = paletteColor },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (swatch.luminance() > 0.6f) {
                                        Color(0xFF1C1C1C)
                                    } else {
                                        Color.White
                                    },
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editing != null) {
                        TextButton(
                            onClick = {
                                editing = null
                                name = ""
                                color = TagPalette.first()
                            },
                        ) {
                            Text("取消編輯")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val current = editing
                            if (current == null) {
                                onCreate(name, color)
                            } else {
                                onUpdate(current, name, color)
                            }
                            editing = null
                            name = ""
                            color = TagPalette.first()
                        },
                    ) {
                        Text(if (editing == null) "新增" else "儲存")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        },
    )

    deleteTarget?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("刪除標籤") },
            text = { Text("確定要刪除「${tag.name}」嗎？書籍不會被刪除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(tag)
                        if (editing?.id == tag.id) {
                            editing = null
                            name = ""
                            color = TagPalette.first()
                        }
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
}
