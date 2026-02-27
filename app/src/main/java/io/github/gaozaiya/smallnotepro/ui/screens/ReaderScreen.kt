package io.github.gaozaiya.smallnotepro.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gaozaiya.smallnotepro.markdown.MarkdownRenderer
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.model.TextSpanOverride
import io.github.gaozaiya.smallnotepro.ui.components.ColorPicker
import io.github.gaozaiya.smallnotepro.ui.viewmodel.ReaderViewModel
import io.github.gaozaiya.smallnotepro.util.ColorUtils
import io.github.gaozaiya.smallnotepro.util.TextIndexUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.FlowPreview
import kotlin.math.roundToInt

/**
 * 阅读器主页面。
 *
 * 核心功能包括：
 * - 文本显示（支持普通文本和 Markdown 渲染）
 * - 大文件分页加载
 * - 阅读进度保存与恢复
 * - 长按菜单（文件选择、收藏、样式设置等）
 * - 局部样式覆盖
 */
@Composable
@OptIn(FlowPreview::class)
fun ReaderScreen(
    readerViewModel: ReaderViewModel,
    onOpenFavorites: () -> Unit,
    onOpenStyleSettings: () -> Unit,
) {
    val uiState by readerViewModel.uiState.collectAsStateWithLifecycle()

    val view = LocalView.current

    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    appContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (_: SecurityException) {
                }
                readerViewModel.openUri(uri)
            }
        },
    )

    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showSpanDialog by rememberSaveable { mutableStateOf(false) }
    var showEnableMarkdownDialog by rememberSaveable { mutableStateOf(false) }

    val background = uiState.style.backgroundColor
    val baseTextColor = ColorUtils.applyBrightness(uiState.style.textColor, uiState.style.textBrightness)

    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var hasRestoredProgress by remember(uiState.currentUri, uiState.isMarkdown) { mutableStateOf(false) }

    val bigListState = rememberLazyListState()
    val bigPageLayouts = remember { mutableStateMapOf<Int, TextLayoutResult>() }
    var hasRestoredBigProgress by remember(uiState.currentUri) { mutableStateOf(false) }

    val firstVisibleBigPageIndex by remember {
        derivedStateOf { bigListState.firstVisibleItemIndex }
    }

    LaunchedEffect(uiState.currentUri, uiState.isBigFileMode) {
        hasRestoredBigProgress = false
        if (!uiState.isBigFileMode) return@LaunchedEffect
        if (uiState.bigPageCount <= 0) return@LaunchedEffect

        // 大文件模式下用 LazyColumn 分页渲染；这里先把列表滚到目标页附近，再在下一段根据页内 offset 精确定位。
        val targetPage = uiState.bigProgressPageIndex.coerceIn(0, uiState.bigPageCount - 1)
        bigListState.scrollToItem(targetPage)
    }

    LaunchedEffect(
        uiState.isBigFileMode,
        uiState.bigProgressPageIndex,
        uiState.bigProgressOffsetCharInPage,
        bigPageLayouts[uiState.bigProgressPageIndex],
    ) {
        if (!uiState.isBigFileMode) return@LaunchedEffect
        if (hasRestoredBigProgress) return@LaunchedEffect
        if (uiState.bigPageCount <= 0) return@LaunchedEffect

        val pageIndex = uiState.bigProgressPageIndex.coerceIn(0, uiState.bigPageCount - 1)
        val layout = bigPageLayouts[pageIndex] ?: return@LaunchedEffect
        if (layout.layoutInput.text.isEmpty()) return@LaunchedEffect

        val targetOffset = uiState.bigProgressOffsetCharInPage.coerceIn(0, layout.layoutInput.text.length)
        val line = layout.getLineForOffset(targetOffset)
        val top = layout.getLineTop(line)
        bigListState.scrollToItem(pageIndex, top.roundToInt().coerceAtLeast(0))
        hasRestoredBigProgress = true
    }

    LaunchedEffect(uiState.currentUri, uiState.isBigFileMode, firstVisibleBigPageIndex) {
        if (!uiState.isBigFileMode) return@LaunchedEffect
        if (uiState.bigPageCount <= 0) return@LaunchedEffect

        // 预加载当前页附近的页，避免滚动时频繁看到“加载中...”。
        for (i in (firstVisibleBigPageIndex - 2)..(firstVisibleBigPageIndex + 2)) {
            readerViewModel.loadBigFilePage(i)
        }
    }

    LaunchedEffect(uiState.currentUri, uiState.isBigFileMode) {
        if (!uiState.isBigFileMode) return@LaunchedEffect
        if (uiState.currentUri == null) return@LaunchedEffect
        if (uiState.bigPageCount <= 0) return@LaunchedEffect

        // 大文件模式下不保存“全局字符偏移”（无法一次性得到全文 length），改为保存“页索引 + 页内字符偏移”。
        snapshotFlow {
            bigListState.firstVisibleItemIndex to bigListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .debounce(300)
            .collectLatest { (pageIndex, scrollOffset) ->
                val layout = bigPageLayouts[pageIndex] ?: return@collectLatest
                val offset = layout.getOffsetForPosition(Offset(0f, scrollOffset.toFloat()))
                val line = layout.getLineForOffset(offset)
                val lineStart = layout.getLineStart(line)
                readerViewModel.saveBigFileProgress(pageIndex, lineStart)
            }
    }

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()

        val isLight = background.luminance() > 0.5f
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

    LaunchedEffect(uiState.currentUri, uiState.isMarkdown, uiState.progressOffsetChar, textLayoutResult) {
        if (uiState.isBigFileMode) return@LaunchedEffect
        if (hasRestoredProgress) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        if (uiState.content.isBlank()) return@LaunchedEffect

        val targetOffset = uiState.progressOffsetChar.coerceIn(0, layout.layoutInput.text.length)
        val line = layout.getLineForOffset(targetOffset)
        val top = layout.getLineTop(line)
        scrollState.scrollTo(top.roundToInt().coerceAtLeast(0))
        hasRestoredProgress = true
    }

    LaunchedEffect(uiState.currentUri, uiState.isMarkdown, uiState.content, textLayoutResult) {
        if (uiState.isBigFileMode) return@LaunchedEffect
        if (uiState.currentUri == null) return@LaunchedEffect
        if (uiState.content.isBlank()) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect

        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .debounce(300)
            .map { scrollY ->
                val offset = layout.getOffsetForPosition(Offset(0f, scrollY.toFloat()))
                val line = layout.getLineForOffset(offset)
                layout.getLineStart(line)
            }
            .distinctUntilChanged()
            .collectLatest { offset ->
                readerViewModel.saveProgressOffsetChar(offset)
            }
    }

    val renderedText: AnnotatedString = remember(
        uiState.content,
        uiState.style.fontSizeSp,
        baseTextColor,
        uiState.spanOverrides,
        uiState.isMarkdown,
        uiState.isBigFileMode,
    ) {
        if (uiState.isBigFileMode) {
            AnnotatedString("")
        } else
        if (uiState.isMarkdown) {
            MarkdownRenderer.render(
                markdown = uiState.content,
                baseFontSizeSp = uiState.style.fontSizeSp,
                baseColor = baseTextColor,
            )
        } else {
            val base = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = uiState.style.fontSizeSp.sp, color = baseTextColor)) {
                    append(uiState.content)
                }
            }

            buildAnnotatedString {
                append(base)
                uiState.spanOverrides.forEach { override ->
                    val spanColor = override.color?.let { c ->
                        ColorUtils.applyBrightness(c, override.brightness ?: 1f)
                    }

                    addStyle(
                        SpanStyle(
                            fontSize = (override.fontSizeSp ?: uiState.style.fontSizeSp).sp,
                            color = spanColor ?: Color.Unspecified,
                        ),
                        start = override.start,
                        end = override.end,
                    )
                }
            }
        }
    }

    val onTapState = rememberUpdatedState {
        if (uiState.tapToToggleHidden && !uiState.isTextHidden) {
            readerViewModel.setTextHidden(true)
        }
    }

    val onLongPressState = rememberUpdatedState {
        if (uiState.isTextHidden) {
            readerViewModel.setTextHidden(false)
        } else {
            showMenu = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapState.value() },
                    onLongPress = { onLongPressState.value() },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(8.dp),
        ) {
            if (uiState.showFileName) {
                uiState.displayName?.let { name ->
                    Text(
                        text = name,
                        color = baseTextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            if (uiState.showCharset) {
                uiState.detectedCharsetName?.let { charsetName ->
                    Text(
                        text = charsetName,
                        color = baseTextColor,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            if (uiState.errorMessage != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = baseTextColor,
                        fontSize = uiState.style.fontSizeSp.sp,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else if (uiState.isTextHidden) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    if (!uiState.hideHintWhenHidden) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "长按显示文本",
                                color = baseTextColor,
                                fontSize = uiState.style.fontSizeSp.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else if (uiState.isBigFileMode) {
                LazyColumn(
                    state = bigListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(uiState.bigPageCount) { pageIndex ->
                        val pageText = uiState.bigPages[pageIndex]
                        if (pageText == null) {
                            LaunchedEffect(uiState.currentUri, pageIndex) {
                                readerViewModel.loadBigFilePage(pageIndex)
                            }
                            Text(
                                text = "加载中...",
                                color = baseTextColor,
                                fontSize = uiState.style.fontSizeSp.sp,
                            )
                        } else {
                            Text(
                                text = pageText,
                                color = baseTextColor,
                                fontSize = uiState.style.fontSizeSp.sp,
                                onTextLayout = { layout ->
                                    bigPageLayouts[pageIndex] = layout
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else if (uiState.content.isBlank()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "长按打开菜单",
                            color = baseTextColor,
                            fontSize = uiState.style.fontSizeSp.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = renderedText,
                        color = baseTextColor,
                        onTextLayout = { textLayoutResult = it },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showMenu) {
            ReaderMenuSheet(
                uiState = uiState,
                onDismiss = { showMenu = false },
                onPickFile = {
                    showMenu = false
                    openLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                },
                onToggleFavorite = {
                    readerViewModel.toggleFavorite(!uiState.isFavorite)
                },
                onOpenFavorites = {
                    showMenu = false
                    onOpenFavorites()
                },
                onOpenStyleSettings = {
                    showMenu = false
                    onOpenStyleSettings()
                },
                onOpenSpanDialog = {
                    showMenu = false
                    showSpanDialog = true
                },
                onToggleMarkdown = {
                    if (uiState.isMarkdown) {
                        readerViewModel.setMarkdownEnabledForCurrentFile(false)
                    } else {
                        if (!uiState.isBigFileMode) {
                            showEnableMarkdownDialog = true
                        }
                    }
                },
            )
        }

        if (showSpanDialog && !uiState.isBigFileMode) {
            SpanOverrideDialog(
                content = uiState.content,
                uiStyle = uiState.style,
                onDismiss = { showSpanDialog = false },
                onAdd = { readerViewModel.addSpanOverride(it) },
            )
        }

        if (showEnableMarkdownDialog) {
            AlertDialog(
                onDismissRequest = { showEnableMarkdownDialog = false },
                containerColor = uiState.style.uiSurfaceColor,
                titleContentColor = uiState.style.uiOnSurfaceColor,
                textContentColor = uiState.style.uiOnSurfaceColor,
                title = { Text(text = "开启 Markdown 渲染") },
                text = { Text(text = "开启 Markdown 渲染会使局部样式失效") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEnableMarkdownDialog = false
                            readerViewModel.setMarkdownEnabledForCurrentFile(true)
                        },
                    ) {
                        Text(text = "开启")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEnableMarkdownDialog = false }) {
                        Text(text = "取消")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderMenuSheet(
    uiState: io.github.gaozaiya.smallnotepro.ui.viewmodel.ReaderUiState,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenStyleSettings: () -> Unit,
    onOpenSpanDialog: () -> Unit,
    onToggleMarkdown: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.PartiallyExpanded },
    )

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = uiState.style.uiAccentColor,
        contentColor = uiState.style.uiOnSurfaceColor,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = uiState.style.uiSurfaceColor,
        contentColor = uiState.style.uiOnSurfaceColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickFile,
                colors = buttonColors,
            ) {
                Text(text = "选择文件")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleFavorite,
                enabled = uiState.currentUri != null,
                colors = buttonColors,
            ) {
                Text(text = if (uiState.isFavorite) "取消收藏" else "收藏")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenFavorites,
                colors = buttonColors,
            ) {
                Text(text = "收藏列表")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSpanDialog,
                enabled = uiState.content.isNotBlank() && !uiState.isMarkdown && !uiState.isBigFileMode,
                colors = buttonColors,
            ) {
                Text(text = "局部样式")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleMarkdown,
                enabled = uiState.currentUri != null && !uiState.isBigFileMode,
                colors = buttonColors,
            ) {
                Text(text = if (uiState.isMarkdown) "关闭 Markdown 渲染" else "开启 Markdown 渲染")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenStyleSettings,
                colors = buttonColors,
            ) {
                Text(text = "样式设置")
            }
        }
    }
}

@Composable
private fun SpanOverrideDialog(
    content: String,
    uiStyle: ReaderStyle,
    onDismiss: () -> Unit,
    onAdd: (TextSpanOverride) -> Unit,
) {
    var lineNumberText by rememberSaveable { mutableStateOf("") }
    var startText by rememberSaveable { mutableStateOf("") }
    var endText by rememberSaveable { mutableStateOf("") }

    var fontSizeSp by rememberSaveable { mutableFloatStateOf(16f) }
    var brightness by rememberSaveable { mutableFloatStateOf(1f) }

    var spanColor by rememberSaveable { mutableStateOf(Color(1f, 1f, 1f, 1f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = uiStyle.uiSurfaceColor,
        titleContentColor = uiStyle.uiOnSurfaceColor,
        textContentColor = uiStyle.uiOnSurfaceColor,
        title = { Text(text = "添加局部样式") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = lineNumberText,
                    onValueChange = { lineNumberText = it },
                    label = { Text(text = "行号(1开始，可选)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = uiStyle.uiAccentColor,
                        focusedLabelColor = uiStyle.uiAccentColor,
                        cursorColor = uiStyle.uiAccentColor,
                    ),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text(text = "start") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = uiStyle.uiAccentColor,
                            focusedLabelColor = uiStyle.uiAccentColor,
                            cursorColor = uiStyle.uiAccentColor,
                        ),
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text(text = "end") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = uiStyle.uiAccentColor,
                            focusedLabelColor = uiStyle.uiAccentColor,
                            cursorColor = uiStyle.uiAccentColor,
                        ),
                    )
                }

                LabeledSlider(
                    title = "字号",
                    value = fontSizeSp,
                    valueRange = 8f..40f,
                    labelColor = uiStyle.uiOnSurfaceColor,
                    accentColor = uiStyle.uiAccentColor,
                    onValueChange = { fontSizeSp = it },
                )

                LabeledSlider(
                    title = "亮度",
                    value = brightness,
                    valueRange = 0f..2f,
                    labelColor = uiStyle.uiOnSurfaceColor,
                    accentColor = uiStyle.uiAccentColor,
                    onValueChange = { brightness = it },
                )

                ColorPicker(
                    title = "颜色",
                    color = spanColor,
                    labelColor = uiStyle.uiOnSurfaceColor,
                    accentColor = uiStyle.uiAccentColor,
                    onColorChange = { spanColor = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val range = lineNumberText.toIntOrNull()?.let { lineNumber ->
                        TextIndexUtils.findLineRange(content, lineNumber)
                    }

                    val start = range?.first ?: startText.toIntOrNull() ?: 0
                    val end = (range?.last?.plus(1)) ?: endText.toIntOrNull() ?: start

                    onAdd(
                        TextSpanOverride(
                            start = start,
                            end = end,
                            fontSizeSp = fontSizeSp,
                            color = spanColor,
                            brightness = brightness,
                        ),
                    )
                    onDismiss()
                },
            ) {
                Text(text = "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    labelColor: Color,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "$title: ${String.format("%.2f", value)}", color = labelColor)
        Slider(
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
