package io.github.gaozaiya.smallnotepro.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.gaozaiya.smallnotepro.data.FavoritesRepository
import io.github.gaozaiya.smallnotepro.data.ReaderGlobalPreferences
import io.github.gaozaiya.smallnotepro.data.ReaderPreferencesRepository
import io.github.gaozaiya.smallnotepro.model.ReaderStyle
import io.github.gaozaiya.smallnotepro.model.TextSpanOverride
import io.github.gaozaiya.smallnotepro.util.PagedTextFileReader
import io.github.gaozaiya.smallnotepro.util.TextContentLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.Charset

/**
 * 阅读器 ViewModel。
 *
 * 管理阅读器的核心状态和业务逻辑，包括：
 * - 文件加载（小文件全文加载 / 大文件分页加载）
 * - 阅读进度保存与恢复
 * - 收藏管理
 * - 样式和偏好设置
 * - Markdown 渲染开关
 *
 * 大文件模式（>5MB）采用分页策略，通过 [PagedTextFileReader] 按需加载页面，
 * 避免一次性将全文载入内存。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    private val appContext: Context,
    private val favoritesRepository: FavoritesRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
) : ViewModel() {
    private val currentUri = MutableStateFlow<Uri?>(null)
    private val displayName = MutableStateFlow<String?>(null)
    private val content = MutableStateFlow("")
    private val detectedCharsetName = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val style = MutableStateFlow(ReaderStyle())
    private val spanOverrides = MutableStateFlow<List<TextSpanOverride>>(emptyList())
    private val isTextHidden = MutableStateFlow(false)

    private val isBigFileMode = MutableStateFlow(false)
    private val bigPageCount = MutableStateFlow(0)
    private val bigPages = MutableStateFlow<Map<Int, String>>(emptyMap())

    // 大文件分页读取需要在内存中保存“编码 + 分页索引”，否则无法按页 seek 读取。
    // 这些是运行期状态，不需要持久化。

    private var bigFileCharset: Charset? = null
    private var bigFilePageIndex: PagedTextFileReader.PageIndex? = null

    private val globalPreferences: StateFlow<ReaderGlobalPreferences> =
        preferencesRepository.globalPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ReaderGlobalPreferences(
                showFileName = true,
                showCharset = true,
                tapToToggleHidden = false,
                autoHideTextOnEnter = false,
                hideHintWhenHidden = false,
                hideStatusBar = false,
                hideNavigationBar = false,
            ),
        )

    private val currentUriString: StateFlow<String?> = currentUri
        .map { it?.toString() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val markdownEnabled: StateFlow<Boolean> = currentUriString
        .flatMapLatest { uriString ->
            if (uriString == null) {
                flowOf(false)
            } else {
                preferencesRepository.markdownEnabled(uriString)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val bigProgressPageIndex: StateFlow<Int> = currentUriString
        .flatMapLatest { uriString ->
            if (uriString == null) {
                flowOf(0)
            } else {
                preferencesRepository.bigProgressPageIndex(uriString)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val bigProgressOffsetCharInPage: StateFlow<Int> = currentUriString
        .flatMapLatest { uriString ->
            if (uriString == null) {
                flowOf(0)
            } else {
                preferencesRepository.bigProgressOffsetCharInPage(uriString)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val progressOffsetChar: StateFlow<Int> = combine(
        currentUriString,
        markdownEnabled,
    ) { uriString, isMarkdown ->
        uriString to isMarkdown
    }.flatMapLatest { (uriString, isMarkdown) ->
        if (uriString == null) {
            flowOf(0)
        } else {
            preferencesRepository.progressOffsetChar(uriString, isMarkdown)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.globalPreferences.first()
            if (prefs.autoHideTextOnEnter) {
                isTextHidden.value = true
            }
        }

        viewModelScope.launch {
            val lastOpened = preferencesRepository.lastOpenedUriString.first() ?: return@launch
            openUri(Uri.parse(lastOpened))
        }
    }

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

    private data class CoreWithFavorites(
        val core: CoreState,
        val favorites: Set<String>,
    )

    private data class BigFileState(
        val isBigFileMode: Boolean,
        val pageCount: Int,
        val pages: Map<Int, String>,
        val progressPageIndex: Int,
        val progressOffsetCharInPage: Int,
    )

    private data class DisplayState(
        val prefs: ReaderGlobalPreferences,
        val rawMarkdownEnabled: Boolean,
        val progressOffsetChar: Int,
        val isTextHidden: Boolean,
    )

    private val coreWithFavorites = combine(
        coreState,
        favoritesRepository.favorites,
    ) { core, favorites ->
        CoreWithFavorites(core = core, favorites = favorites)
    }

    // Flow 的 combine 在当前依赖版本下对参数个数有限制；这里拆成若干中间 State，保证类型推导稳定。

    private val bigFileState: StateFlow<BigFileState> = combine(
        isBigFileMode,
        bigPageCount,
        bigPages,
        bigProgressPageIndex,
        bigProgressOffsetCharInPage,
    ) { bigMode, pageCount, pages, pageIndex, offsetInPage ->
        BigFileState(
            isBigFileMode = bigMode,
            pageCount = pageCount,
            pages = pages,
            progressPageIndex = pageIndex,
            progressOffsetCharInPage = offsetInPage,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BigFileState(
            isBigFileMode = false,
            pageCount = 0,
            pages = emptyMap(),
            progressPageIndex = 0,
            progressOffsetCharInPage = 0,
        ),
    )

    private val displayState: StateFlow<DisplayState> = combine(
        globalPreferences,
        markdownEnabled,
        progressOffsetChar,
        isTextHidden,
    ) { prefs, rawMarkdown, progressOffset, hidden ->
        DisplayState(
            prefs = prefs,
            rawMarkdownEnabled = rawMarkdown,
            progressOffsetChar = progressOffset,
            isTextHidden = hidden,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DisplayState(
            prefs = ReaderGlobalPreferences(
                showFileName = true,
                showCharset = true,
                tapToToggleHidden = false,
                autoHideTextOnEnter = false,
                hideHintWhenHidden = false,
                hideStatusBar = false,
                hideNavigationBar = false,
            ),
            rawMarkdownEnabled = false,
            progressOffsetChar = 0,
            isTextHidden = false,
        ),
    )

    val uiState: StateFlow<ReaderUiState> = combine(
        coreWithFavorites,
        displayState,
        bigFileState,
    ) { coreFav, display, big ->
        val effectiveMarkdown = display.rawMarkdownEnabled && !big.isBigFileMode
        ReaderUiState(
            currentUri = coreFav.core.uri,
            displayName = coreFav.core.displayName,
            content = coreFav.core.content,
            isMarkdown = effectiveMarkdown,
            isBigFileMode = big.isBigFileMode,
            detectedCharsetName = coreFav.core.detectedCharsetName,
            errorMessage = coreFav.core.errorMessage,
            showFileName = display.prefs.showFileName,
            showCharset = display.prefs.showCharset,
            tapToToggleHidden = display.prefs.tapToToggleHidden,
            autoHideTextOnEnter = display.prefs.autoHideTextOnEnter,
            hideHintWhenHidden = display.prefs.hideHintWhenHidden,
            hideStatusBar = display.prefs.hideStatusBar,
            hideNavigationBar = display.prefs.hideNavigationBar,
            isTextHidden = display.isTextHidden,
            progressOffsetChar = display.progressOffsetChar,
            bigProgressPageIndex = big.progressPageIndex,
            bigProgressOffsetCharInPage = big.progressOffsetCharInPage,
            bigPageCount = big.pageCount,
            bigPages = big.pages,
            style = coreFav.core.style,
            spanOverrides = if (big.isBigFileMode) emptyList() else coreFav.core.spanOverrides,
            favorites = coreFav.favorites,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    fun openUri(uri: Uri) {
        viewModelScope.launch {
            currentUri.value = uri
            displayName.value = DocumentFile.fromSingleUri(appContext, uri)?.name

            preferencesRepository.setLastOpenedUriString(uri.toString())

            isBigFileMode.value = false
            bigPageCount.value = 0
            bigPages.value = emptyMap()
            bigFileCharset = null
            bigFilePageIndex = null

            val uriString = uri.toString()
            // 小文件（<= 5MB）直接全文加载，保证 Markdown/局部样式/精确字符进度等能力。
            // 更大的文件进入“大文件模式”，分页懒加载降低内存压力。
            val maxSmallFileBytes = 5_000_000
            val fileLength = DocumentFile.fromSingleUri(appContext, uri)?.length() ?: -1L
            val shouldUseBigFileMode = fileLength > maxSmallFileBytes || fileLength < 0

            if (shouldUseBigFileMode) {
                openBigFile(uri, uriString)
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                TextContentLoader.load(appContext, uri, maxBytes = maxSmallFileBytes)
            }

            if (result.errorMessage == "文件过大") {
                openBigFile(uri, uriString)
                return@launch
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

    fun loadBigFilePage(pageIndex: Int) {
        val uri = currentUri.value ?: return
        val charset = bigFileCharset ?: return
        val index = bigFilePageIndex ?: return
        if (pageIndex < 0 || pageIndex >= index.pageCount) return
        if (bigPages.value.containsKey(pageIndex)) return

        viewModelScope.launch {
            val range = index.pageRange(pageIndex)
            val text = withContext(Dispatchers.IO) {
                PagedTextFileReader.readPage(appContext, uri, charset, range)
            }

            // 简单做一个页缓存上限（保留最近 N 页），避免页数很大时 Map 膨胀。
            val newMap = (bigPages.value + (pageIndex to text))
                .entries
                .sortedBy { it.key }
                .takeLast(10)
                .associate { it.toPair() }
            bigPages.value = newMap
        }
    }

    fun saveBigFileProgress(pageIndex: Int, offsetCharInPage: Int) {
        val uriString = currentUri.value?.toString() ?: return
        viewModelScope.launch {
            // 大文件模式下不再保存“全局字符偏移”，而是保存“页索引 + 页内字符偏移”。
            preferencesRepository.setBigProgressPageIndex(uriString, pageIndex)
            preferencesRepository.setBigProgressOffsetCharInPage(uriString, offsetCharInPage)
        }
    }

    private suspend fun openBigFile(uri: Uri, uriString: String) {
        val detect = withContext(Dispatchers.IO) {
            TextContentLoader.detectCharset(appContext, uri)
        }

        if (!detect.isSuccess || detect.charset == null) {
            content.value = ""
            detectedCharsetName.value = null
            errorMessage.value = detect.errorMessage ?: "无法读取文本"
            spanOverrides.value = emptyList()
            return
        }

        val charset = detect.charset
        val charsetName = detect.charsetName

        detectedCharsetName.value = charsetName
        errorMessage.value = null
        content.value = ""
        spanOverrides.value = emptyList()

        val index = withContext(Dispatchers.IO) {
            PagedTextFileReader.buildPageIndex(appContext, uri, charset)
        }

        bigFileCharset = charset
        bigFilePageIndex = index
        isBigFileMode.value = true
        bigPageCount.value = index.pageCount

        val savedPage = preferencesRepository.bigProgressPageIndex(uriString).first().coerceIn(0, (index.pageCount - 1).coerceAtLeast(0))
        loadBigFilePage(savedPage)
        loadBigFilePage(savedPage - 1)
        loadBigFilePage(savedPage + 1)
    }

    fun setTextHidden(hidden: Boolean) {
        isTextHidden.value = hidden
    }

    fun toggleTextHidden() {
        isTextHidden.value = !isTextHidden.value
    }

    fun setMarkdownEnabledForCurrentFile(enabled: Boolean) {
        val uriString = currentUri.value?.toString() ?: return
        viewModelScope.launch {
            preferencesRepository.setMarkdownEnabled(uriString, enabled)
        }
    }

    fun saveProgressOffsetChar(offset: Int) {
        val uriString = currentUri.value?.toString() ?: return
        val isMarkdown = uiState.value.isMarkdown
        viewModelScope.launch {
            preferencesRepository.setProgressOffsetChar(uriString, isMarkdown, offset)
        }
    }

    fun setShowFileName(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowFileName(enabled)
        }
    }

    fun setShowCharset(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowCharset(enabled)
        }
    }

    fun setTapToToggleHidden(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTapToToggleHidden(enabled)
        }
    }

    fun setAutoHideTextOnEnter(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoHideTextOnEnter(enabled)
        }
    }

    fun setHideHintWhenHidden(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHideHintWhenHidden(enabled)
        }
    }

    fun setHideStatusBar(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHideStatusBar(enabled)
        }
    }

    fun setHideNavigationBar(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHideNavigationBar(enabled)
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
            val preferencesRepository = ReaderPreferencesRepository(appContext)
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(appContext, favoritesRepository, preferencesRepository) as T
        }
    }
}
