package io.github.gaozaiya.smallnotepro.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.ui.components.ColorPicker
import io.github.gaozaiya.smallnotepro.ui.viewmodel.ReaderViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

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
    val view = LocalView.current

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
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
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

        SectionTitle(text = "阅读界面", color = style.uiOnSurfaceColor)

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

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(text = "交互", color = style.uiOnSurfaceColor)

        LabeledIntSlider(
            title = "长按判定时间(ms)",
            value = uiState.longPressTimeoutMs,
            valueRange = 50..5_000,
            labelColor = style.uiOnSurfaceColor,
            accentColor = style.uiAccentColor,
            onValueChange = { readerViewModel.setLongPressTimeoutMs(it) },
        )

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(text = "密码与诱饵", color = style.uiOnSurfaceColor)

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
            onSetPassword = { readerViewModel.setFakePassword(it) },
            onClearPassword = { readerViewModel.clearFakePassword() },
        )

        var decoyText by rememberSaveable(uiState.decoyText) { mutableStateOf(uiState.decoyText) }
        var decoyName by rememberSaveable(uiState.decoyDisplayName) { mutableStateOf(uiState.decoyDisplayName ?: "") }
        var decoyCharset by rememberSaveable(uiState.decoyCharsetName) { mutableStateOf(uiState.decoyCharsetName ?: "") }
        var decoyFakeFavoritesRaw by rememberSaveable { mutableStateOf("") }

        LaunchedEffect(uiState.decoyFakeFavorites) {
            val raw = uiState.decoyFakeFavorites.joinToString("\n")
            if (decoyFakeFavoritesRaw.isBlank()) {
                decoyFakeFavoritesRaw = raw
            }
        }

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

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = decoyFakeFavoritesRaw,
            onValueChange = { decoyFakeFavoritesRaw = it },
            label = { Text(text = "假收藏列表(每行一个)") },
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
            onClick = { readerViewModel.setDecoyFakeFavorites(decoyFakeFavoritesRaw) },
        ) {
            Text(text = "保存假收藏列表", color = style.uiOnSurfaceColor)
        }

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(text = "阅读样式", color = style.uiOnSurfaceColor)


        ColorPicker(
            title = "背景色",
            color = style.backgroundColor,
            labelColor = style.uiOnSurfaceColor,
            accentColor = style.uiAccentColor,
            onColorChange = { readerViewModel.setStyle(style.copy(backgroundColor = it)) },
        )

        ColorPicker(
            title = "文字颜色",
            color = style.textColor,
            labelColor = style.uiOnSurfaceColor,
            accentColor = style.uiAccentColor,
            onColorChange = { readerViewModel.setStyle(style.copy(textColor = it)) },
        )

        LabeledSlider(
            title = "文字亮度",
            value = style.textBrightness,
            valueRange = 0f..2f,
            labelColor = style.uiOnSurfaceColor,
            accentColor = style.uiAccentColor,
            onValueChange = { readerViewModel.setStyle(style.copy(textBrightness = it)) },
        )

        LabeledSlider(
            title = "字号",
            value = style.fontSizeSp,
            valueRange = 10f..28f,
            labelColor = style.uiOnSurfaceColor,
            accentColor = style.uiAccentColor,
            onValueChange = { readerViewModel.setStyle(style.copy(fontSizeSp = it)) },
        )

        Spacer(modifier = Modifier.height(4.dp))

        SectionTitle(text = "UI 配色", color = style.uiOnSurfaceColor)

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
        Slider(
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
private fun SectionTitle(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 13.sp)
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
