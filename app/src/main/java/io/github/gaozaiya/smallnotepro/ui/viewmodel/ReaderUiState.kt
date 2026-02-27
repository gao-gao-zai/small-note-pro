package io.github.gaozaiya.smallnotepro.ui.viewmodel

import android.net.Uri
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.model.TextSpanOverride

/**
 * 阅读器 UI 状态。
 *
 * 包含阅读器所需的所有状态数据，由 [ReaderViewModel] 管理并通过 StateFlow 暴露。
 */
data class ReaderUiState(
    val currentUri: Uri? = null,
    val displayName: String? = null,
    val content: String = "",
    val isMarkdown: Boolean = false,
    val isBigFileMode: Boolean = false,
    val detectedCharsetName: String? = null,
    val errorMessage: String? = null,
    val showFileName: Boolean = true,
    val showCharset: Boolean = true,
    val tapToToggleHidden: Boolean = false,
    val autoHideTextOnEnter: Boolean = false,
    val hideHintWhenHidden: Boolean = false,
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val isTextHidden: Boolean = false,
    val progressOffsetChar: Int = 0,
    val bigProgressPageIndex: Int = 0,
    val bigProgressOffsetCharInPage: Int = 0,
    val bigPageCount: Int = 0,
    val bigPages: Map<Int, String> = emptyMap(),
    val style: ReaderStyle = ReaderStyle(),
    val spanOverrides: List<TextSpanOverride> = emptyList(),
    val favorites: Set<String> = emptySet(),
) {
    val isFavorite: Boolean = currentUri?.toString()?.let { favorites.contains(it) } ?: false
}
