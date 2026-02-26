package io.github.gaozaiya.smallnotepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.gaozaiya.smallnotepro.ui.SmallNoteProApp
import io.github.gaozaiya.smallnotepro.ui.theme.SmallNoteProTheme

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
