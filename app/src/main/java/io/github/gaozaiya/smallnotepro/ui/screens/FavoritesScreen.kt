package io.github.gaozaiya.smallnotepro.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gaozaiya.smallnotepro.ui.viewmodel.ReaderViewModel

/**
 * 收藏列表页面。
 *
 * 展示已收藏的文件列表，支持点击打开和删除收藏。
 */
@Composable
fun FavoritesScreen(
    readerViewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenReader: () -> Unit,
) {
    val uiState by readerViewModel.uiState.collectAsStateWithLifecycle()
    val style = uiState.style
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val view = LocalView.current

    var openErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = style.uiSurfaceColor.toArgb()
        window.navigationBarColor = style.uiSurfaceColor.toArgb()

        val isLight = style.uiSurfaceColor.luminance() > 0.5f
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight

        val compatController = WindowInsetsControllerCompat(window, view)
        compatController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (uiState.hideStatusBar) {
            compatController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        } else {
            compatController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }

        if (uiState.hideNavigationBar) {
            compatController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        } else {
            compatController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(style.uiSurfaceColor)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = style.uiOnSurfaceColor)
            }
            Text(text = "收藏", color = style.uiOnSurfaceColor, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(0.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val favorites = if (uiState.decoyModeEnabled) {
                uiState.decoyFakeFavorites
            } else {
                uiState.favorites.toList().sorted()
            }
            items(favorites) { uriString ->
                val uri = remember(uriString) { Uri.parse(uriString) }
                val doc = remember(uriString) { DocumentFile.fromSingleUri(appContext, uri) }
                val name = remember(uriString) {
                    doc?.name ?: uri.lastPathSegment ?: uriString
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(style.uiSurfaceColor.copy(alpha = 0.65f))
                        .padding(8.dp)
                        .clickable {
                            val exists = doc?.exists() == true
                            val canRead = doc?.canRead() == true
                            if (!exists || !canRead) {
                                openErrorMessage = "文件不存在或无法打开"
                                return@clickable
                            }

                            readerViewModel.openUri(uri)
                            onOpenReader()
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        color = style.uiOnSurfaceColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    if (!uiState.decoyModeEnabled) {
                        IconButton(onClick = { readerViewModel.toggleFavorite(false, uriString) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = style.uiOnSurfaceColor)
                        }
                    }
                }
            }
        }

        if (openErrorMessage != null) {
            AlertDialog(
                onDismissRequest = { openErrorMessage = null },
                containerColor = style.uiSurfaceColor,
                titleContentColor = style.uiOnSurfaceColor,
                textContentColor = style.uiOnSurfaceColor,
                title = { Text(text = "无法打开") },
                text = { Text(text = openErrorMessage ?: "") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { openErrorMessage = null }) {
                        Text(text = "知道了")
                    }
                },
            )
        }
    }
}
