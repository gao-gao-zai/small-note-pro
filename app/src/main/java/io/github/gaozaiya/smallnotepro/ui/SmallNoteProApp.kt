package io.github.gaozaiya.smallnotepro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.gaozaiya.smallnotepro.ui.screens.FavoritesScreen
import io.github.gaozaiya.smallnotepro.ui.screens.ReaderScreen
import io.github.gaozaiya.smallnotepro.ui.screens.StyleSettingsScreen
import io.github.gaozaiya.smallnotepro.ui.viewmodel.ReaderViewModel

/**
 * 路由常量定义。
 */
private object Routes {
    const val Reader = "reader"
    const val Favorites = "favorites"
    const val StyleSettings = "style_settings"
}

/**
 * 应用入口 Composable。
 *
 * 配置导航控制器和各页面路由，共享 ReaderViewModel 实例。
 */
@Composable
fun SmallNoteProApp() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    val readerViewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory(appContext))

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.Reader) {
        composable(Routes.Reader) {
            ReaderScreen(
                readerViewModel = readerViewModel,
                onOpenFavorites = { navController.navigate(Routes.Favorites) },
                onOpenStyleSettings = { navController.navigate(Routes.StyleSettings) },
            )
        }
        composable(Routes.Favorites) {
            FavoritesScreen(
                readerViewModel = readerViewModel,
                onBack = { navController.popBackStack() },
                onOpenReader = { navController.popBackStack() },
            )
        }
        composable(Routes.StyleSettings) {
            StyleSettingsScreen(
                readerViewModel = readerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
