package io.github.gaozaiya.smallnotepro.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.model.TextSpanOverride
import io.github.gaozaiya.smallnotepro.ui.viewmodel.ReaderViewModel
import io.github.gaozaiya.smallnotepro.util.ColorUtils
import io.github.gaozaiya.smallnotepro.util.TextIndexUtils

@Composable
fun ReaderScreen(
    readerViewModel: ReaderViewModel,
    onOpenFavorites: () -> Unit,
) {
    val uiState by readerViewModel.uiState.collectAsStateWithLifecycle()

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

    val background = uiState.style.backgroundColor
    val baseTextColor = ColorUtils.applyBrightness(uiState.style.textColor, uiState.style.textBrightness)

    val renderedText: AnnotatedString = remember(
        uiState.content,
        uiState.style.fontSizeSp,
        baseTextColor,
        uiState.spanOverrides,
    ) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(8.dp),
        ) {
            uiState.displayName?.let { name ->
                Text(
                    text = name,
                    color = baseTextColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            uiState.detectedCharsetName?.let { charsetName ->
                Text(
                    text = charsetName,
                    color = baseTextColor,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = baseTextColor,
                        fontSize = uiState.style.fontSizeSp.sp,
                    )
                } else if (uiState.content.isBlank()) {
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
                } else {
                    Text(
                        text = renderedText,
                        color = baseTextColor,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
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
                onStyleChange = readerViewModel::setStyle,
                onOpenSpanDialog = {
                    showMenu = false
                    showSpanDialog = true
                },
            )
        }

        if (showSpanDialog) {
            SpanOverrideDialog(
                content = uiState.content,
                onDismiss = { showSpanDialog = false },
                onAdd = { readerViewModel.addSpanOverride(it) },
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
    onStyleChange: (ReaderStyle) -> Unit,
    onOpenSpanDialog: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.PartiallyExpanded },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
            ) {
                Text(text = "选择文件")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleFavorite,
                enabled = uiState.currentUri != null,
            ) {
                Text(text = if (uiState.isFavorite) "取消收藏" else "收藏")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenFavorites,
            ) {
                Text(text = "收藏列表")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSpanDialog,
                enabled = uiState.content.isNotBlank(),
            ) {
                Text(text = "局部样式")
            }

            ReaderStylePanel(
                style = uiState.style,
                onStyleChange = onStyleChange,
                onOpenSpanDialog = onOpenSpanDialog,
            )
        }
    }
}

@Composable
private fun ReaderStylePanel(
    style: ReaderStyle,
    onStyleChange: (ReaderStyle) -> Unit,
    onOpenSpanDialog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "全局样式", color = MaterialTheme.colorScheme.onSurface)

        ColorSliders(
            title = "背景色",
            color = style.backgroundColor,
            onColorChange = { onStyleChange(style.copy(backgroundColor = it)) },
        )

        ColorSliders(
            title = "文字颜色",
            color = style.textColor,
            onColorChange = { onStyleChange(style.copy(textColor = it)) },
        )

        LabeledSlider(
            title = "文字亮度",
            value = style.textBrightness,
            valueRange = 0f..2f,
            onValueChange = { onStyleChange(style.copy(textBrightness = it)) },
        )

        LabeledSlider(
            title = "字号",
            value = style.fontSizeSp,
            valueRange = 10f..28f,
            onValueChange = { onStyleChange(style.copy(fontSizeSp = it)) },
        )

        Button(onClick = onOpenSpanDialog) {
            Text(text = "局部样式")
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(text = "$title: ${String.format("%.2f", value)}", color = MaterialTheme.colorScheme.onSurface)
        Slider(value = value, valueRange = valueRange, onValueChange = onValueChange)
    }
}

@Composable
private fun ColorSliders(
    title: String,
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    Column {
        Text(text = title, color = MaterialTheme.colorScheme.onSurface)

        LabeledSlider(
            title = "R",
            value = color.red,
            valueRange = 0f..1f,
            onValueChange = { onColorChange(Color(it, color.green, color.blue, color.alpha)) },
        )
        LabeledSlider(
            title = "G",
            value = color.green,
            valueRange = 0f..1f,
            onValueChange = { onColorChange(Color(color.red, it, color.blue, color.alpha)) },
        )
        LabeledSlider(
            title = "B",
            value = color.blue,
            valueRange = 0f..1f,
            onValueChange = { onColorChange(Color(color.red, color.green, it, color.alpha)) },
        )
    }
}

@Composable
private fun SpanOverrideDialog(
    content: String,
    onDismiss: () -> Unit,
    onAdd: (TextSpanOverride) -> Unit,
) {
    var lineNumberText by rememberSaveable { mutableStateOf("") }
    var startText by rememberSaveable { mutableStateOf("") }
    var endText by rememberSaveable { mutableStateOf("") }

    var fontSizeSp by rememberSaveable { mutableFloatStateOf(16f) }
    var brightness by rememberSaveable { mutableFloatStateOf(1f) }

    var colorR by rememberSaveable { mutableFloatStateOf(1f) }
    var colorG by rememberSaveable { mutableFloatStateOf(1f) }
    var colorB by rememberSaveable { mutableFloatStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加局部样式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lineNumberText,
                    onValueChange = { lineNumberText = it },
                    label = { Text(text = "行号(1开始，可选)") },
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text(text = "start") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text(text = "end") },
                        singleLine = true,
                    )
                }

                LabeledSlider(
                    title = "字号",
                    value = fontSizeSp,
                    valueRange = 8f..40f,
                    onValueChange = { fontSizeSp = it },
                )

                LabeledSlider(
                    title = "亮度",
                    value = brightness,
                    valueRange = 0f..2f,
                    onValueChange = { brightness = it },
                )

                LabeledSlider(
                    title = "颜色R",
                    value = colorR,
                    valueRange = 0f..1f,
                    onValueChange = { colorR = it },
                )
                LabeledSlider(
                    title = "颜色G",
                    value = colorG,
                    valueRange = 0f..1f,
                    onValueChange = { colorG = it },
                )
                LabeledSlider(
                    title = "颜色B",
                    value = colorB,
                    valueRange = 0f..1f,
                    onValueChange = { colorB = it },
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
                            color = Color(colorR, colorG, colorB, 1f),
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
