package io.github.gaozaiya.smallnotepro.ui.screens

import android.app.Activity
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.ui.components.ColorPicker
import io.github.gaozaiya.smallnotepro.ui.viewmodel.ReaderViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

private enum class FocusMode {
    None,
    TextBrightness,
    TextAlpha,
}

private enum class SettingsPage {
    Home,
    ReadingUi,
    Interaction,
    PasswordAndDecoy,
    ReadingStyle,
    UiColors,
}

private val SettingsContentMaxWidth = 420.dp
private const val SliderWidthFraction = 1f

/**
 * 设置页面。
 *
 * 提供阅读界面和 UI 的样式配置选项，包括：
 * - 界面显示选项（文件名、编码、隐藏模式等）
 * - 阅读样式（背景色、文字颜色/字号/亮度）
 * - UI 配色（菜单背景、文字颜色、强调色）
 */
@Composable
fun StyleSettingsScreen(
    readerViewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val uiState by readerViewModel.uiState.collectAsStateWithLifecycle()
    val style = uiState.style
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val view = LocalView.current

    var focusMode by rememberSaveable { mutableStateOf(FocusMode.None) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    val scrollState = rememberScrollState()
    val isFocusing = focusMode != FocusMode.None
    val otherAlpha = if (isFocusing) 0f else 1f
    val otherModifier = Modifier.alpha(otherAlpha)
    val pageBackgroundColor = if (isFocusing) style.backgroundColor else style.uiSurfaceColor

    var showFakePasswordNeedRealDialog by rememberSaveable { mutableStateOf(false) }

    var showFavoritesManagerDialog by rememberSaveable { mutableStateOf(false) }
    var showDecoyFavoritesManagerDialog by rememberSaveable { mutableStateOf(false) }
    var pickForDecoyFavorites by rememberSaveable { mutableStateOf(false) }

    val pickFavoriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                appContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
            }

            val uriString = uri.toString()
            if (pickForDecoyFavorites) {
                readerViewModel.addDecoyFakeFavorite(uriString)
            } else {
                readerViewModel.toggleFavorite(true, uriString)
            }
        },
    )

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = pageBackgroundColor.toArgb()
        window.navigationBarColor = pageBackgroundColor.toArgb()

        val isLight = pageBackgroundColor.luminance() > 0.5f
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (isFocusing) {
            ReaderScreen(
                readerViewModel = readerViewModel,
                onOpenFavorites = {},
                onOpenStyleSettings = {},
                interactionEnabled = false,
                applySystemBars = false,
                enableProgressTracking = false,
                applySafeDrawingPadding = true,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(pageBackgroundColor))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SettingsContentMaxWidth)
                    .align(Alignment.TopCenter)
                    .verticalScroll(scrollState, enabled = !isFocusing),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(otherAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (page != SettingsPage.Home) {
                        page = SettingsPage.Home
                    } else {
                        onBack()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = style.uiOnSurfaceColor,
                )
            }
            Text(
                text = "设置",
                color = style.uiOnSurfaceColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        when (page) {
            SettingsPage.Home -> {
                SettingsCategoryCard(
                    title = "目录",
                    style = style,
                    modifier = otherModifier,
                ) {
                    TextButton(onClick = { page = SettingsPage.ReadingUi }) {
                        Text(text = "阅读界面", color = style.uiOnSurfaceColor)
                    }
                    TextButton(onClick = { page = SettingsPage.Interaction }) {
                        Text(text = "交互", color = style.uiOnSurfaceColor)
                    }
                    TextButton(onClick = { page = SettingsPage.PasswordAndDecoy }) {
                        Text(text = "密码与诱饵", color = style.uiOnSurfaceColor)
                    }
                    TextButton(onClick = { page = SettingsPage.ReadingStyle }) {
                        Text(text = "阅读样式", color = style.uiOnSurfaceColor)
                    }
                    TextButton(onClick = { page = SettingsPage.UiColors }) {
                        Text(text = "UI 配色", color = style.uiOnSurfaceColor)
                    }
                }

                SettingsCategoryCard(
                    title = "文字透明度(顶层)",
                    style = style,
                ) {
                    LabeledSwitch(
                        title = "顶层显示透明度滑条",
                        checked = uiState.showTextAlphaOnSettingsHome,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setShowTextAlphaOnSettingsHome(it) },
                    )

                    if (uiState.showTextAlphaOnSettingsHome) {
                        FocusSlider(
                            title = "文字透明度",
                            value = style.textAlpha.coerceIn(0f, 1f),
                            valueRange = 0f..1f,
                            labelColor = style.uiOnSurfaceColor,
                            accentColor = style.uiAccentColor,
                            onValueChange = {
                                if (focusMode == FocusMode.None) focusMode = FocusMode.TextAlpha
                                readerViewModel.setStyle(style.copy(textAlpha = it.coerceIn(0f, 1f)))
                            },
                            onValueChangeFinished = { focusMode = FocusMode.None },
                        )
                    }
                }
            }

            SettingsPage.ReadingUi -> {
                SettingsCategoryCard(
                    title = "阅读界面",
                    style = style,
                    modifier = otherModifier,
                ) {
                    LabeledSwitch(
                        title = "显示文件名",
                        checked = uiState.showFileName,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setShowFileName(it) },
                    )

                    LabeledSwitch(
                        title = "显示文件编码",
                        checked = uiState.showCharset,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setShowCharset(it) },
                    )

                    LabeledSwitch(
                        title = "单击切换隐藏文本",
                        checked = uiState.tapToToggleHidden,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setTapToToggleHidden(it) },
                    )

                    LabeledSwitch(
                        title = "进入应用自动隐藏文本",
                        checked = uiState.autoHideTextOnEnter,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setAutoHideTextOnEnter(it) },
                    )

                    LabeledSwitch(
                        title = "隐藏文本时隐藏提示",
                        checked = uiState.hideHintWhenHidden,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setHideHintWhenHidden(it) },
                    )

                    LabeledSwitch(
                        title = "隐藏状态栏",
                        checked = uiState.hideStatusBar,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setHideStatusBar(it) },
                    )

                    LabeledSwitch(
                        title = "隐藏导航栏",
                        checked = uiState.hideNavigationBar,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setHideNavigationBar(it) },
                    )
                }
            }

            SettingsPage.Interaction -> {
                SettingsCategoryCard(
                    title = "交互",
                    style = style,
                    modifier = otherModifier,
                ) {
                    LabeledIntSlider(
                        title = "长按判定时间(ms)",
                        value = uiState.longPressTimeoutMs,
                        valueRange = 50..5_000,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onValueChange = { readerViewModel.setLongPressTimeoutMs(it) },
                    )
                }
            }

            SettingsPage.PasswordAndDecoy -> {
                var decoyText by rememberSaveable(uiState.decoyText) { mutableStateOf(uiState.decoyText) }
                var decoyName by rememberSaveable(uiState.decoyDisplayName) { mutableStateOf(uiState.decoyDisplayName ?: "") }
                var decoyCharset by rememberSaveable(uiState.decoyCharsetName) { mutableStateOf(uiState.decoyCharsetName ?: "") }

                SettingsCategoryCard(
                    title = "密码与诱饵",
                    style = style,
                    modifier = otherModifier,
                ) {
                    TextButton(
                        onClick = { showFavoritesManagerDialog = true },
                    ) {
                        Text(text = "管理收藏列表", color = style.uiOnSurfaceColor)
                    }

                    TextButton(
                        onClick = { showDecoyFavoritesManagerDialog = true },
                    ) {
                        Text(text = "管理假收藏列表", color = style.uiOnSurfaceColor)
                    }

                    PasswordSettingRow(
                        title = "显示文本密码",
                        currentIsSet = !uiState.revealPasswordHash.isNullOrBlank(),
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onSetPassword = { readerViewModel.setRevealPassword(it) },
                        onClearPassword = { readerViewModel.clearRevealPassword() },
                    )

                    PasswordSettingRow(
                        title = "假密码",
                        currentIsSet = !uiState.fakePasswordHash.isNullOrBlank(),
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onSetPassword = {
                            if (uiState.revealPasswordHash.isNullOrBlank()) {
                                showFakePasswordNeedRealDialog = true
                            } else {
                                readerViewModel.setFakePassword(it)
                            }
                        },
                        onClearPassword = { readerViewModel.clearFakePassword() },
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = decoyText,
                        onValueChange = { decoyText = it },
                        label = { Text(text = "诱饵文本") },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = style.uiAccentColor,
                            focusedLabelColor = style.uiAccentColor,
                            cursorColor = style.uiAccentColor,
                            focusedTextColor = style.uiOnSurfaceColor,
                            unfocusedTextColor = style.uiOnSurfaceColor,
                        ),
                    )

                    TextButton(
                        onClick = { readerViewModel.setDecoyText(decoyText) },
                    ) {
                        Text(text = "保存诱饵文本", color = style.uiOnSurfaceColor)
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = decoyName,
                        onValueChange = { decoyName = it },
                        label = { Text(text = "诱饵文件名(留空则隐藏)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = style.uiAccentColor,
                            focusedLabelColor = style.uiAccentColor,
                            cursorColor = style.uiAccentColor,
                            focusedTextColor = style.uiOnSurfaceColor,
                            unfocusedTextColor = style.uiOnSurfaceColor,
                        ),
                    )

                    TextButton(
                        onClick = { readerViewModel.setDecoyDisplayName(decoyName.takeIf { it.isNotBlank() }) },
                    ) {
                        Text(text = "保存诱饵文件名", color = style.uiOnSurfaceColor)
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = decoyCharset,
                        onValueChange = { decoyCharset = it },
                        label = { Text(text = "诱饵编码(留空则隐藏)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = style.uiAccentColor,
                            focusedLabelColor = style.uiAccentColor,
                            cursorColor = style.uiAccentColor,
                            focusedTextColor = style.uiOnSurfaceColor,
                            unfocusedTextColor = style.uiOnSurfaceColor,
                        ),
                    )

                    TextButton(
                        onClick = { readerViewModel.setDecoyCharsetName(decoyCharset.takeIf { it.isNotBlank() }) },
                    ) {
                        Text(text = "保存诱饵编码", color = style.uiOnSurfaceColor)
                    }

                    LabeledSwitch(
                        title = "诱饵模式隐藏设置入口",
                        checked = uiState.decoyHideSettings,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setDecoyHideSettings(it) },
                    )

                    LabeledSwitch(
                        title = "诱饵模式隐藏选择文件",
                        checked = uiState.decoyHidePickFile,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setDecoyHidePickFile(it) },
                    )

                    LabeledSwitch(
                        title = "诱饵模式隐藏收藏列表",
                        checked = uiState.decoyHideFavoritesList,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onCheckedChange = { readerViewModel.setDecoyHideFavoritesList(it) },
                    )
                }
            }

            SettingsPage.ReadingStyle -> {
                SettingsCategoryCard(
                    title = "阅读样式",
                    style = style,
                    titleModifier = Modifier.alpha(otherAlpha),
                    containerColor = if (isFocusing) Color.Transparent else style.uiSurfaceColor,
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isFocusing) 0.dp else 1.dp),
                ) {
                    Column(modifier = otherModifier) {
                        ColorPicker(
                            title = "文字颜色",
                            color = style.textColor,
                            labelColor = style.uiOnSurfaceColor,
                            accentColor = style.uiAccentColor,
                            onColorChange = { readerViewModel.setStyle(style.copy(textColor = it)) },
                        )
                    }

                    FocusSlider(
                        modifier = Modifier.alpha(if (focusMode == FocusMode.TextAlpha) 0f else 1f),
                        title = "文字亮度",
                        value = style.textBrightness,
                        valueRange = 0f..2f,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onValueChange = {
                            if (focusMode == FocusMode.None) focusMode = FocusMode.TextBrightness
                            readerViewModel.setStyle(style.copy(textBrightness = it))
                        },
                        onValueChangeFinished = { focusMode = FocusMode.None },
                    )

                    FocusSlider(
                        modifier = Modifier.alpha(if (focusMode == FocusMode.TextBrightness) 0f else 1f),
                        title = "文字透明度",
                        value = style.textAlpha.coerceIn(0f, 1f),
                        valueRange = 0f..1f,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onValueChange = {
                            if (focusMode == FocusMode.None) focusMode = FocusMode.TextAlpha
                            readerViewModel.setStyle(style.copy(textAlpha = it.coerceIn(0f, 1f)))
                        },
                        onValueChangeFinished = { focusMode = FocusMode.None },
                    )

                    LabeledSlider(
                        title = "字号",
                        value = style.fontSizeSp,
                        valueRange = 10f..28f,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onValueChange = { readerViewModel.setStyle(style.copy(fontSizeSp = it)) },
                        modifier = otherModifier,
                    )
                }
            }

            SettingsPage.UiColors -> {
                SettingsCategoryCard(
                    title = "UI 配色",
                    style = style,
                    modifier = otherModifier,
                ) {
                    ColorPicker(
                        title = "菜单/对话框背景",
                        color = style.uiSurfaceColor,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onColorChange = { readerViewModel.setStyle(style.copy(uiSurfaceColor = it)) },
                    )

                    ColorPicker(
                        title = "UI 文字颜色",
                        color = style.uiOnSurfaceColor,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onColorChange = { readerViewModel.setStyle(style.copy(uiOnSurfaceColor = it)) },
                    )

                    ColorPicker(
                        title = "强调色(按钮/滑杆)",
                        color = style.uiAccentColor,
                        labelColor = style.uiOnSurfaceColor,
                        accentColor = style.uiAccentColor,
                        onColorChange = { readerViewModel.setStyle(style.copy(uiAccentColor = it)) },
                    )
                }
            }
        }

        if (showFavoritesManagerDialog) {
            AlertDialog(
                onDismissRequest = { showFavoritesManagerDialog = false },
                containerColor = style.uiSurfaceColor,
                titleContentColor = style.uiOnSurfaceColor,
                textContentColor = style.uiOnSurfaceColor,
                title = { Text(text = "收藏列表管理") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                pickForDecoyFavorites = false
                                pickFavoriteLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                            },
                        ) {
                            Text(text = "添加文件", color = style.uiOnSurfaceColor)
                        }

                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            val favorites = uiState.favorites.toList().sorted()
                            items(favorites) { uriString ->
                                val uri = remember(uriString) { Uri.parse(uriString) }
                                val doc = remember(uriString) { DocumentFile.fromSingleUri(appContext, uri) }
                                val name = remember(uriString) { doc?.name ?: uri.lastPathSegment ?: uriString }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(text = name, color = style.uiOnSurfaceColor, modifier = Modifier.weight(1f), maxLines = 2)
                                    IconButton(onClick = { readerViewModel.toggleFavorite(false, uriString) }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = style.uiOnSurfaceColor)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFavoritesManagerDialog = false }) {
                        Text(text = "关闭", color = style.uiOnSurfaceColor)
                    }
                },
            )
        }

        if (showDecoyFavoritesManagerDialog) {
            AlertDialog(
                onDismissRequest = { showDecoyFavoritesManagerDialog = false },
                containerColor = style.uiSurfaceColor,
                titleContentColor = style.uiOnSurfaceColor,
                textContentColor = style.uiOnSurfaceColor,
                title = { Text(text = "假收藏列表管理") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                pickForDecoyFavorites = true
                                pickFavoriteLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                            },
                        ) {
                            Text(text = "添加文件", color = style.uiOnSurfaceColor)
                        }

                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            val favorites = uiState.decoyFakeFavorites
                            items(favorites) { uriString ->
                                val uri = remember(uriString) { Uri.parse(uriString) }
                                val doc = remember(uriString) { DocumentFile.fromSingleUri(appContext, uri) }
                                val name = remember(uriString) { doc?.name ?: uri.lastPathSegment ?: uriString }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(text = name, color = style.uiOnSurfaceColor, modifier = Modifier.weight(1f), maxLines = 2)
                                    IconButton(onClick = { readerViewModel.removeDecoyFakeFavorite(uriString) }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = style.uiOnSurfaceColor)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDecoyFavoritesManagerDialog = false }) {
                        Text(text = "关闭", color = style.uiOnSurfaceColor)
                    }
                },
            )
        }
        if (showFakePasswordNeedRealDialog) {
            AlertDialog(
                onDismissRequest = { showFakePasswordNeedRealDialog = false },
                title = { Text(text = "无法设置假密码", color = style.uiOnSurfaceColor) },
                text = { Text(text = "请先设置“显示文本密码”。", color = style.uiOnSurfaceColor) },
                confirmButton = {
                    TextButton(onClick = { showFakePasswordNeedRealDialog = false }) {
                        Text(text = "知道了", color = style.uiOnSurfaceColor)
                    }
                },
                containerColor = style.uiSurfaceColor,
            )
        }
    }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    style: ReaderStyle,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    containerColor: Color = style.uiSurfaceColor,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = elevation,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, color = style.uiOnSurfaceColor, fontSize = 13.sp, modifier = titleModifier)
            content()
        }
    }
}

@Composable
private fun FocusSlider(
    modifier: Modifier = Modifier,
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    labelColor: Color,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "$title: ${String.format("%.2f", value)}", color = labelColor)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                modifier = Modifier.fillMaxWidth(SliderWidthFraction),
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                ),
            )
        }
    }
}

@Composable
private fun LabeledIntSlider(
    title: String,
    value: Int,
    valueRange: IntRange,
    labelColor: Color,
    accentColor: Color,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "$title: $value", color = labelColor)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                modifier = Modifier.fillMaxWidth(SliderWidthFraction),
                value = value.toFloat(),
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                ),
            )
        }
    }
}

@Composable
private fun PasswordSettingRow(
    title: String,
    currentIsSet: Boolean,
    labelColor: Color,
    accentColor: Color,
    onSetPassword: (String) -> Unit,
    onClearPassword: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "$title: ${if (currentIsSet) "已设置" else "未设置"}", color = labelColor)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = input,
            onValueChange = { input = it },
            label = { Text(text = "输入新密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                focusedLabelColor = accentColor,
                cursorColor = accentColor,
                focusedTextColor = labelColor,
                unfocusedTextColor = labelColor,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    onSetPassword(input)
                    input = ""
                },
            ) {
                Text(text = "保存", color = labelColor)
            }
            TextButton(onClick = onClearPassword) {
                Text(text = "清除", color = labelColor)
            }
        }
    }
}

@Composable
private fun LabeledSwitch(
    title: String,
    checked: Boolean,
    labelColor: Color,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, color = labelColor)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.5f),
                uncheckedThumbColor = labelColor.copy(alpha = 0.7f),
                uncheckedTrackColor = labelColor.copy(alpha = 0.25f),
            ),
        )
    }
}

@Composable
private fun SectionTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, color = color, fontSize = 13.sp, modifier = modifier)
}

@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    labelColor: Color,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "$title: ${String.format("%.2f", value)}", color = labelColor)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                modifier = Modifier.fillMaxWidth(SliderWidthFraction),
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                ),
            )
        }
    }
}
