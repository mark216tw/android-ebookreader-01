package com.minireader.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.minireader.app.data.prefs.DisplayMode
import com.minireader.app.data.prefs.FontChoice
import com.minireader.app.data.prefs.ReaderPrefs
import com.minireader.app.data.prefs.ReaderTheme
import com.minireader.app.data.prefs.TextAlignment
import com.minireader.app.data.prefs.resolveDark
import com.minireader.app.ui.reader.readerColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsContent(
    prefs: ReaderPrefs,
    onPrefsChange: (ReaderPrefs) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    var draft by remember { mutableStateOf(prefs) }

    LaunchedEffect(prefs) {
        if (prefs != draft) draft = prefs
    }
    LaunchedEffect(draft) {
        delay(140)
        if (draft != prefs) onPrefsChange(draft)
    }

    val darkMode = draft.displayMode.resolveDark(isSystemInDarkTheme())
    val availableThemes = ReaderTheme.entries.filter { it.isDark == darkMode }
    val selectedTheme = if (darkMode) draft.darkTheme else draft.theme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
    ) {
        if (showPreview) {
            PreviewCard(draft, selectedTheme)
            Spacer(Modifier.height(18.dp))
        }

        SectionTitle("顯示模式")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisplayMode.entries.forEach { mode ->
                FilterChip(
                    selected = draft.displayMode == mode,
                    onClick = { draft = draft.copy(displayMode = mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "系統模式會跟隨手機的淺色／深色設定。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        SectionTitle(if (darkMode) "護眼模式（深色）" else "護眼模式（淺色）")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableThemes.forEach { theme ->
                val colors = readerColors(theme)
                val selected = selectedTheme == theme
                Column(
                    modifier = Modifier
                        .width(62.dp)
                        .clickable {
                            draft = if (darkMode) {
                                draft.copy(darkTheme = theme)
                            } else {
                                draft.copy(theme = theme)
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.background)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0x33000000)
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aa",
                            color = colors.foreground,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        theme.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SwitchRow(
            title = "閱讀時保持螢幕亮起",
            subtitle = "閱讀器開啟時不會自動關閉螢幕",
            checked = draft.keepScreenOn,
            onCheckedChange = { draft = draft.copy(keepScreenOn = it) },
        )

        Spacer(Modifier.height(12.dp))
        SectionTitle("字體")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontChoice.entries.forEach { choice ->
                FilterChip(
                    selected = draft.fontFamily == choice,
                    onClick = { draft = draft.copy(fontFamily = choice) },
                    label = { Text(choice.label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SliderRow(
            label = "文字大小",
            value = draft.fontSizeSp,
            valueText = "${draft.fontSizeSp.roundToInt()} sp",
            valueRange = 20f..36f,
            steps = 15,
            onValueChange = { draft = draft.copy(fontSizeSp = it) },
        )
        SliderRow(
            label = "行距",
            value = draft.lineHeight,
            valueText = "%.1f".format(draft.lineHeight),
            valueRange = 1.2f..2.2f,
            steps = 9,
            onValueChange = { draft = draft.copy(lineHeight = it) },
        )
        SliderRow(
            label = "字距",
            value = draft.letterSpacing,
            valueText = "%.2f em".format(draft.letterSpacing),
            valueRange = 0f..0.2f,
            steps = 19,
            onValueChange = { draft = draft.copy(letterSpacing = it) },
        )
        SliderRow(
            label = "段落間距",
            value = draft.paragraphSpacing,
            valueText = "%.1f em".format(draft.paragraphSpacing),
            valueRange = 0f..2f,
            steps = 19,
            onValueChange = { draft = draft.copy(paragraphSpacing = it) },
        )
        SliderRow(
            label = "左右邊距",
            value = draft.horizontalMarginDp.toFloat(),
            valueText = "${draft.horizontalMarginDp} dp",
            valueRange = 8f..40f,
            steps = 15,
            onValueChange = { draft = draft.copy(horizontalMarginDp = it.roundToInt()) },
        )

        Spacer(Modifier.height(4.dp))
        SectionTitle("對齊方式")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextAlignment.entries.forEach { alignment ->
                FilterChip(
                    selected = draft.textAlignment == alignment,
                    onClick = { draft = draft.copy(textAlignment = alignment) },
                    label = { Text(alignment.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "首行縮排",
            subtitle = "每個段落第一行縮排兩個字",
            checked = draft.firstLineIndent,
            onCheckedChange = { draft = draft.copy(firstLineIndent = it) },
        )
    }
}

@Composable
private fun PreviewCard(prefs: ReaderPrefs, theme: ReaderTheme) {
    val colors = readerColors(theme)
    Surface(
        color = colors.background,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "閱讀範例",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "一本好書，像一扇安靜的窗。調整字體與間距，找到最舒服的閱讀節奏。",
                color = colors.foreground,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = when (prefs.fontFamily) {
                        FontChoice.SERIF -> FontFamily.Serif
                        FontChoice.SANS -> FontFamily.SansSerif
                        FontChoice.BOLD -> FontFamily.SansSerif
                    },
                    fontWeight = if (prefs.fontFamily == FontChoice.BOLD) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    fontSize = prefs.fontSizeSp.sp,
                    lineHeight = (prefs.fontSizeSp * prefs.lineHeight).sp,
                    letterSpacing = prefs.letterSpacing.em,
                    textAlign = when (prefs.textAlignment) {
                        TextAlignment.LEFT -> TextAlign.Left
                        TextAlignment.RIGHT -> TextAlign.Right
                        TextAlignment.CENTER -> TextAlign.Center
                        TextAlignment.JUSTIFY -> TextAlign.Justify
                    },
                ),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
