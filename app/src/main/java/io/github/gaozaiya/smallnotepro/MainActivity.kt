package io.github.gaozaiya.smallnotepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.gaozaiya.smallnotepro.ui.SmallNoteProApp
import io.github.gaozaiya.smallnotepro.ui.theme.SmallNoteProTheme

/**
 * 应用主 Activity。
 *
 * 作为应用入口，设置 Compose 内容并应用主题。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmallNoteProTheme {
                SmallNoteProApp()
            }
        }
    }
}
