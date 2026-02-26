package io.github.gaozaiya.smallnotepro.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.gaozaiya.smallnotepro.data.FavoritesRepository
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.model.TextSpanOverride
import io.github.gaozaiya.smallnotepro.util.TextContentLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    private val appContext: Context,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    private val currentUri = MutableStateFlow<Uri?>(null)
    private val displayName = MutableStateFlow<String?>(null)
    private val content = MutableStateFlow("")
    private val detectedCharsetName = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val style = MutableStateFlow(ReaderStyle())
    private val spanOverrides = MutableStateFlow<List<TextSpanOverride>>(emptyList())

    private data class CoreState(
        val uri: Uri?,
        val displayName: String?,
        val content: String,
        val detectedCharsetName: String?,
        val errorMessage: String?,
        val style: ReaderStyle,
        val spanOverrides: List<TextSpanOverride>,
    )

    private val coreState: StateFlow<CoreState> = combine(
        currentUri,
        displayName,
        content,
        detectedCharsetName,
        style,
    ) { uri, name, text, charset, st ->
        CoreState(
            uri = uri,
            displayName = name,
            content = text,
            detectedCharsetName = charset,
            errorMessage = null,
            style = st,
            spanOverrides = emptyList(),
        )
    }.combine(errorMessage) { core, err ->
        core.copy(errorMessage = err)
    }.combine(spanOverrides) { core, overrides ->
        core.copy(spanOverrides = overrides)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CoreState(null, null, "", null, null, ReaderStyle(), emptyList()),
    )

    val uiState: StateFlow<ReaderUiState> = combine(
        coreState,
        favoritesRepository.favorites,
    ) { core, favorites ->
        ReaderUiState(
            currentUri = core.uri,
            displayName = core.displayName,
            content = core.content,
            isMarkdown = false,
            detectedCharsetName = core.detectedCharsetName,
            errorMessage = core.errorMessage,
            style = core.style,
            spanOverrides = core.spanOverrides,
            favorites = favorites,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    fun openUri(uri: Uri) {
        viewModelScope.launch {
            currentUri.value = uri
            displayName.value = DocumentFile.fromSingleUri(appContext, uri)?.name

            val result = withContext(Dispatchers.IO) {
                TextContentLoader.load(appContext, uri)
            }

            detectedCharsetName.value = result.charsetName

            if (result.text != null && result.errorMessage == null) {
                content.value = result.text
                errorMessage.value = null
            } else {
                content.value = ""
                errorMessage.value = result.errorMessage ?: "无法读取文本"
            }

            spanOverrides.value = emptyList()
        }
    }

    fun toggleFavorite(isFavorite: Boolean, uriString: String? = null) {
        val targetUriString = uriString ?: currentUri.value?.toString() ?: return
        viewModelScope.launch {
            favoritesRepository.toggle(targetUriString, isFavorite)
        }
    }

    fun setStyle(newStyle: ReaderStyle) {
        style.value = newStyle
    }

    fun addSpanOverride(override: TextSpanOverride) {
        val text = content.value
        val start = override.start.coerceIn(0, text.length)
        val end = override.end.coerceIn(0, text.length)
        if (start >= end) return

        spanOverrides.value = spanOverrides.value + override.copy(start = start, end = end)
    }

    class Factory(
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val favoritesRepository = FavoritesRepository(appContext)
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(appContext, favoritesRepository) as T
        }
    }
}
