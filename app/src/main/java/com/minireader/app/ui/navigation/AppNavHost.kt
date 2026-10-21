package com.minireader.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.minireader.app.ui.reader.ReaderScreen
import com.minireader.app.ui.settings.SettingsScreen
import com.minireader.app.ui.shelf.ShelfScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "shelf",
        modifier = Modifier.fillMaxSize(),
    ) {
        composable("shelf") {
            ShelfScreen(
                onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            route = "reader/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            ReaderScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
