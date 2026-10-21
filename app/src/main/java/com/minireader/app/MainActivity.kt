package com.minireader.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minireader.app.data.prefs.ReaderPrefs
import com.minireader.app.data.prefs.resolveDark
import com.minireader.app.ui.navigation.AppNavHost
import com.minireader.app.ui.theme.MiniReaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val container = (application as MiniReaderApp).container
            val prefs by container.preferences.data.collectAsStateWithLifecycle(
                initialValue = ReaderPrefs(),
            )
            val darkTheme = prefs.displayMode.resolveDark(isSystemInDarkTheme())
            MiniReaderTheme(darkTheme = darkTheme) {
                AppNavHost()
            }
        }
    }
}
