package com.minireader.app.ui.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * Applies the current screen background to the system status/navigation bars
 * and keeps their icon contrast readable.
 *
 * On Android 15+ edge-to-edge enforcement may ignore the color setters; the
 * screen content already draws behind the bars, so the same background shows
 * through. Disabling the navigation bar contrast scrim keeps 3-button
 * navigation consistent with the selected reader theme where supported.
 */
@Suppress("DEPRECATION")
@Composable
fun SystemBarAppearance(
    backgroundColor: Color,
    darkIcons: Boolean,
) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return

    DisposableEffect(activity, backgroundColor, darkIcons) {
        val window = activity.window
        window.statusBarColor = backgroundColor.toArgb()
        window.navigationBarColor = backgroundColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = darkIcons
        insetsController.isAppearanceLightNavigationBars = darkIcons
        onDispose { }
    }
}

tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
