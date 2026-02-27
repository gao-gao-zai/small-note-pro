package io.github.gaozaiya.smallnotepro.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * HSV 颜色选择器组件。
 *
 * 提供完整的颜色选择功能，包括：
 * - SV 面板（饱和度/明度）
 * - 色相条
 * - 预设颜色
 * - Hex 输入框
 * - RGBA 滑块
 *
 * @param title 标题文本。
 * @param color 当前颜色。
 * @param labelColor 标签文字颜色。
 * @param accentColor 强调色（用于控件高亮）。
 * @param onColorChange 颜色变更回调。
 * @param modifier Modifier。
 */
@Composable
fun ColorPicker(
    title: String,
    color: Color,
    labelColor: Color,
    accentColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(color.toArgb()) { colorToHsv(color) }
    val hue = hsv[0]
    val saturation = hsv[1]
    val value = hsv[2]

    var hexField by remember { mutableStateOf(TextFieldValue(colorToHex(color))) }
    var isHexEditing by remember { mutableStateOf(false) }

    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(color.toArgb()) {
        if (!isHexEditing) {
            hexField = TextFieldValue(colorToHex(color))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, color = labelColor, modifier = Modifier.weight(1f))
            Text(text = colorToHex(color), color = labelColor)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
            )
        }

        if (expanded) {
            PresetColorsRow(
                onPick = onColorChange,
                contentPadding = PaddingValues(2.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SvPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v -> onColorChange(Color.hsv(hue, s, v, color.alpha)) },
                )

                HueBar(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight(),
                    hue = hue,
                    onChange = { h -> onColorChange(Color.hsv(h, saturation, value, color.alpha)) },
                )
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isHexEditing = it.isFocused },
                value = hexField,
                onValueChange = {
                    hexField = it
                    parseHexColor(it.text, fallbackAlpha = color.alpha)?.let(onColorChange)
                },
                label = { Text(text = "Hex", color = labelColor) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    focusedLabelColor = accentColor,
                    cursorColor = accentColor,
                ),
            )

            RgbaSliders(
                color = color,
                labelColor = labelColor,
                accentColor = accentColor,
                onColorChange = onColorChange,
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * SV 面板（饱和度/明度选择器）。
 */
@Composable
private fun SvPanel(
    modifier: Modifier,
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val pureHue = remember(hue) { Color.hsv(hue, 1f, 1f, 1f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val onChangeState = rememberUpdatedState(onChange)

    fun update(pos: Offset) {
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val s = (pos.x / w).coerceIn(0f, 1f)
        val v = (1f - pos.y / h).coerceIn(0f, 1f)
        onChangeState.value(s, v)
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { update(it) },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { update(it) },
                    onDrag = { change, _ -> update(change.position) },
                )
            }
            .then(
                Modifier
                    .background(Color.Transparent)
                    .padding(0.dp),
            ),
        onDraw = {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, pureHue)),
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            )

            val cx = saturation * size.width.toFloat()
            val cy = (1f - value) * size.height.toFloat()
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = Color.Black,
                radius = 10.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
        },
    )
}

/**
 * 色相条选择器。
 */
@Composable
private fun HueBar(
    modifier: Modifier,
    hue: Float,
    onChange: (Float) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val onChangeState = rememberUpdatedState(onChange)

    fun update(pos: Offset) {
        val h = size.height.toFloat().coerceAtLeast(1f)
        val nh = ((pos.y / h) * 360f).coerceIn(0f, 360f)
        onChangeState.value(nh)
    }

    val colors = remember {
        listOf(
            Color.hsv(0f, 1f, 1f),
            Color.hsv(60f, 1f, 1f),
            Color.hsv(120f, 1f, 1f),
            Color.hsv(180f, 1f, 1f),
            Color.hsv(240f, 1f, 1f),
            Color.hsv(300f, 1f, 1f),
            Color.hsv(360f, 1f, 1f),
        )
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { update(it) })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { update(it) },
                    onDrag = { change, _ -> update(change.position) },
                )
            },
        onDraw = {
            drawRect(
                brush = Brush.verticalGradient(colors),
            )

            val cy = (hue / 360f) * size.height.toFloat()
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = Offset(size.width.toFloat() / 2f, cy),
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = Color.Black,
                radius = 10.dp.toPx(),
                center = Offset(size.width.toFloat() / 2f, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
        },
    )
}

/**
 * RGBA 滑块组。
 */
@Composable
private fun RgbaSliders(
    color: Color,
    labelColor: Color,
    accentColor: Color,
    onColorChange: (Color) -> Unit,
) {
    val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    val a = color.alpha.coerceIn(0f, 1f)

    LabeledSlider(
        title = "R",
        value = r.toFloat(),
        valueRange = 0f..255f,
        labelColor = labelColor,
        accentColor = accentColor,
        onValueChange = {
            val nr = it.roundToInt().coerceIn(0, 255)
            onColorChange(Color(nr / 255f, color.green, color.blue, color.alpha))
        },
    )

    LabeledSlider(
        title = "G",
        value = g.toFloat(),
        valueRange = 0f..255f,
        labelColor = labelColor,
        accentColor = accentColor,
        onValueChange = {
            val ng = it.roundToInt().coerceIn(0, 255)
            onColorChange(Color(color.red, ng / 255f, color.blue, color.alpha))
        },
    )

    LabeledSlider(
        title = "B",
        value = b.toFloat(),
        valueRange = 0f..255f,
        labelColor = labelColor,
        accentColor = accentColor,
        onValueChange = {
            val nb = it.roundToInt().coerceIn(0, 255)
            onColorChange(Color(color.red, color.green, nb / 255f, color.alpha))
        },
    )

    LabeledSlider(
        title = "A",
        value = a,
        valueRange = 0f..1f,
        labelColor = labelColor,
        accentColor = accentColor,
        onValueChange = {
            onColorChange(Color(color.red, color.green, color.blue, it.coerceIn(0f, 1f)))
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

/**
 * 预设颜色行。
 */
@Composable
private fun PresetColorsRow(
    onPick: (Color) -> Unit,
    contentPadding: PaddingValues,
) {
    val presets = remember {
        listOf(
            Color(0xFF000000),
            Color(0xFFFFFFFF),
            Color(0xFF1B1B1B),
            Color(0xFF2E3440),
            Color(0xFF0D47A1),
            Color(0xFF1976D2),
            Color(0xFF009688),
            Color(0xFF4CAF50),
            Color(0xFFFFC107),
            Color(0xFFFF5722),
            Color(0xFFE91E63),
            Color(0xFF9C27B0),
        )
    }

    val onPickState = rememberUpdatedState(onPick)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(preset)
                    .pointerInput(preset) {
                        detectTapGestures(onTap = { onPickState.value(preset) })
                    },
            )
        }
    }
}

private fun colorToHsv(color: Color): FloatArray {
    val out = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), out)
    return out
}

private fun colorToHex(color: Color): String {
    val a = (color.alpha * 255f).roundToInt().coerceIn(0, 255)
    val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}

private fun parseHexColor(text: String, fallbackAlpha: Float): Color? {
    val raw = text.trim()
    if (raw.isEmpty()) return null

    val hex = if (raw.startsWith("#")) raw.substring(1) else raw
    if (hex.length != 6 && hex.length != 8) return null

    val value = hex.toLongOrNull(16) ?: return null

    return when (hex.length) {
        6 -> {
            val r = ((value shr 16) and 0xFF).toInt()
            val g = ((value shr 8) and 0xFF).toInt()
            val b = (value and 0xFF).toInt()
            Color(r / 255f, g / 255f, b / 255f, fallbackAlpha.coerceIn(0f, 1f))
        }
        8 -> {
            val a = ((value shr 24) and 0xFF).toInt()
            val r = ((value shr 16) and 0xFF).toInt()
            val g = ((value shr 8) and 0xFF).toInt()
            val b = (value and 0xFF).toInt()
            Color(r / 255f, g / 255f, b / 255f, a / 255f)
        }
        else -> null
    }
}
