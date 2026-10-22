package com.minireader.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ReaderTheme(val label: String, val isDark: Boolean) {
    LIGHT("白天", false),
    SEPIA("米黃", false),
    GREEN("護眼綠", false),
    BLUE_FILTER("藍光濾鏡", false),
    BLACK("純黑", true),
    DARK("暗色", true),
    GRAY("灰階", false),
}

enum class DisplayMode(val label: String) {
    SYSTEM("系統"),
    LIGHT("淺色"),
    DARK("深色"),
}

fun DisplayMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    DisplayMode.SYSTEM -> systemDark
    DisplayMode.LIGHT -> false
    DisplayMode.DARK -> true
}

enum class FontChoice(val label: String) {
    SERIF("襯線"),
    SANS("無襯線"),
    BOLD("粗體"),
}

enum class TextAlignment(val label: String) {
    LEFT("靠左"),
    RIGHT("靠右"),
    CENTER("置中"),
    JUSTIFY("左右對齊"),
}

enum class ShelfLayout(val label: String) {
    GRID("封面"),
    COMPACT("緊湊"),
}

enum class ShelfSort(val label: String) {
    ADDED_DESC("加入時間"),
    LAST_READ_DESC("最近閱讀"),
    TITLE_ASC("書名"),
    AUTHOR_ASC("作者"),
    PROGRESS_DESC("閱讀進度"),
}

data class ReaderPrefs(
    val fontSizeSp: Float = 20f,
    val fontFamily: FontChoice = FontChoice.SERIF,
    val lineHeight: Float = 1.7f,
    val letterSpacing: Float = 0f,
    val paragraphSpacing: Float = 0.8f,
    val horizontalMarginDp: Int = 20,
    val textAlignment: TextAlignment = TextAlignment.JUSTIFY,
    val firstLineIndent: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    val darkTheme: ReaderTheme = ReaderTheme.DARK,
    val displayMode: DisplayMode = DisplayMode.SYSTEM,
    val keepScreenOn: Boolean = false,
    val sortOrder: ShelfSort = ShelfSort.ADDED_DESC,
    val shelfLayout: ShelfLayout = ShelfLayout.GRID,
    val ttsSpeed: Float = 1f,
) {
    fun effectiveTheme(systemDark: Boolean): ReaderTheme {
        return if (displayMode.resolveDark(systemDark)) darkTheme else theme
    }
}

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_prefs",
)

class ReaderPreferences(private val context: Context) {

    val data: Flow<ReaderPrefs> = context.readerDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toReaderPrefs() }

    suspend fun update(transform: (ReaderPrefs) -> ReaderPrefs) {
        context.readerDataStore.edit { preferences ->
            preferences.write(transform(preferences.toReaderPrefs()))
        }
    }

    suspend fun reset() {
        context.readerDataStore.edit { preferences ->
            preferences.write(ReaderPrefs())
        }
    }
}

private object Keys {
    val fontSize = floatPreferencesKey("font_size")
    val fontFamily = stringPreferencesKey("font_family")
    val lineHeight = floatPreferencesKey("line_height")
    val letterSpacing = floatPreferencesKey("letter_spacing")
    val paragraphSpacing = floatPreferencesKey("paragraph_spacing")
    val horizontalMargin = intPreferencesKey("horizontal_margin")
    val textAlignment = stringPreferencesKey("text_alignment")
    val firstLineIndent = booleanPreferencesKey("first_line_indent")
    val theme = stringPreferencesKey("reader_theme")
    val darkTheme = stringPreferencesKey("reader_dark_theme")
    val displayMode = stringPreferencesKey("display_mode")
    val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    val sortOrder = stringPreferencesKey("shelf_sort")
    val shelfLayout = stringPreferencesKey("shelf_layout")
    val ttsSpeed = floatPreferencesKey("tts_speed")
}

private fun Preferences.toReaderPrefs(): ReaderPrefs {
    val defaults = ReaderPrefs()
    val storedTheme = enumOrDefault(this[Keys.theme], defaults.theme)
    val lightTheme = storedTheme.takeIf { !it.isDark } ?: defaults.theme
    val storedDarkTheme = enumOrDefault(
        this[Keys.darkTheme],
        if (storedTheme.isDark) storedTheme else defaults.darkTheme,
    )
    val darkTheme = storedDarkTheme.takeIf { it.isDark } ?: defaults.darkTheme
    return ReaderPrefs(
        fontSizeSp = (this[Keys.fontSize] ?: defaults.fontSizeSp).coerceIn(20f, 36f),
        fontFamily = enumOrDefault(this[Keys.fontFamily], defaults.fontFamily),
        lineHeight = this[Keys.lineHeight] ?: defaults.lineHeight,
        letterSpacing = this[Keys.letterSpacing] ?: defaults.letterSpacing,
        paragraphSpacing = this[Keys.paragraphSpacing] ?: defaults.paragraphSpacing,
        horizontalMarginDp = this[Keys.horizontalMargin] ?: defaults.horizontalMarginDp,
        textAlignment = enumOrDefault(this[Keys.textAlignment], defaults.textAlignment),
        firstLineIndent = this[Keys.firstLineIndent] ?: defaults.firstLineIndent,
        theme = lightTheme,
        darkTheme = darkTheme,
        displayMode = enumOrDefault(this[Keys.displayMode], defaults.displayMode),
        keepScreenOn = this[Keys.keepScreenOn] ?: defaults.keepScreenOn,
        sortOrder = enumOrDefault(this[Keys.sortOrder], defaults.sortOrder),
        shelfLayout = enumOrDefault(this[Keys.shelfLayout], defaults.shelfLayout),
        ttsSpeed = (this[Keys.ttsSpeed] ?: defaults.ttsSpeed).coerceIn(0.5f, 2f),
    )
}

private fun androidx.datastore.preferences.core.MutablePreferences.write(prefs: ReaderPrefs) {
    this[Keys.fontSize] = prefs.fontSizeSp
    this[Keys.fontFamily] = prefs.fontFamily.name
    this[Keys.lineHeight] = prefs.lineHeight
    this[Keys.letterSpacing] = prefs.letterSpacing
    this[Keys.paragraphSpacing] = prefs.paragraphSpacing
    this[Keys.horizontalMargin] = prefs.horizontalMarginDp
    this[Keys.textAlignment] = prefs.textAlignment.name
    this[Keys.firstLineIndent] = prefs.firstLineIndent
    this[Keys.theme] = prefs.theme.name
    this[Keys.darkTheme] = prefs.darkTheme.name
    this[Keys.displayMode] = prefs.displayMode.name
    this[Keys.keepScreenOn] = prefs.keepScreenOn
    this[Keys.sortOrder] = prefs.sortOrder.name
    this[Keys.shelfLayout] = prefs.shelfLayout.name
    this[Keys.ttsSpeed] = prefs.ttsSpeed
}

private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
    if (raw == null) return fallback
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}
