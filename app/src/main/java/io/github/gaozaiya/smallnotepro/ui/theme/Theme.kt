package io.github.gaozaiya.smallnotepro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun SmallNoteProTheme(content: @Composable () -> Unit) {
    val isNight = (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    MaterialTheme(
        colorScheme = if (isNight) DarkColors else LightColors,
        content = content,
    )
}
