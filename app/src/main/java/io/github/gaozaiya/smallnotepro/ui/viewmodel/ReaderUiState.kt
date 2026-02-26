package io.github.gaozaiya.smallnotepro.ui.viewmodel

import android.net.Uri
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.model.TextSpanOverride

data class ReaderUiState(
    val currentUri: Uri? = null,
    val displayName: String? = null,
    val content: String = "",
    val isMarkdown: Boolean = false,
    val detectedCharsetName: String? = null,
    val errorMessage: String? = null,
    val style: ReaderStyle = ReaderStyle(),
    val spanOverrides: List<TextSpanOverride> = emptyList(),
    val favorites: Set<String> = emptySet(),
) {
    val isFavorite: Boolean = currentUri?.toString()?.let { favorites.contains(it) } ?: false
}
