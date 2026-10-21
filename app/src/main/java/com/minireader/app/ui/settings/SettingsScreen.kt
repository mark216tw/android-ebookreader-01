package com.minireader.app.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minireader.app.ui.system.SystemBarAppearance
import com.minireader.app.ui.theme.readerColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val scheme = readerColorScheme(prefs.effectiveTheme(isSystemInDarkTheme()))

    MaterialTheme(colorScheme = scheme) {
        SystemBarAppearance(
            backgroundColor = scheme.background,
            darkIcons = scheme.background.luminance() > 0.5f,
        )

        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("閱讀設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        ReaderSettingsContent(
            prefs = prefs,
            onPrefsChange = viewModel::updatePrefs,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            showPreview = true,
        )
    }
    }
}
