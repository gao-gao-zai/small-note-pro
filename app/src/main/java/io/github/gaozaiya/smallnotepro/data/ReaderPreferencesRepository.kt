package io.github.gaozaiya.smallnotepro.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

/**
 * 阅读器偏好设置仓库。
 *
 * 管理阅读器的全局设置和文件级设置（如阅读进度、Markdown 开关）。
 * 使用 DataStore 持久化数据，文件级设置通过 URI 的 SHA-256 哈希作为键。
 */

/**
 * 阅读器全局偏好设置。
 *
 * 包含适用于所有文件的通用设置，如界面显示选项和系统栏隐藏选项。
 */
data class ReaderGlobalPreferences(
    val showFileName: Boolean,
    val showCharset: Boolean,
    val tapToToggleHidden: Boolean,
    val autoHideTextOnEnter: Boolean,
    val hideHintWhenHidden: Boolean,
    val hideStatusBar: Boolean,
    val hideNavigationBar: Boolean,
)

class ReaderPreferencesRepository(
    private val appContext: Context,
) {
    private val showFileNameKey = booleanPreferencesKey("show_file_name")
    private val showCharsetKey = booleanPreferencesKey("show_charset")
    private val tapToToggleHiddenKey = booleanPreferencesKey("tap_to_toggle_hidden")
    private val autoHideTextOnEnterKey = booleanPreferencesKey("auto_hide_text_on_enter")
    private val hideHintWhenHiddenKey = booleanPreferencesKey("hide_hint_when_hidden")
    private val hideStatusBarKey = booleanPreferencesKey("hide_status_bar")
    private val hideNavigationBarKey = booleanPreferencesKey("hide_navigation_bar")

    private val lastOpenedUriKey = stringPreferencesKey("last_opened_uri")

    val globalPreferences: Flow<ReaderGlobalPreferences> = appContext.appDataStore.data.map { preferences ->
        ReaderGlobalPreferences(
            showFileName = preferences[showFileNameKey] ?: true,
            showCharset = preferences[showCharsetKey] ?: true,
            tapToToggleHidden = preferences[tapToToggleHiddenKey] ?: false,
            autoHideTextOnEnter = preferences[autoHideTextOnEnterKey] ?: false,
            hideHintWhenHidden = preferences[hideHintWhenHiddenKey] ?: false,
            hideStatusBar = preferences[hideStatusBarKey] ?: false,
            hideNavigationBar = preferences[hideNavigationBarKey] ?: false,
        )
    }

    val lastOpenedUriString: Flow<String?> = appContext.appDataStore.data.map { preferences ->
        preferences[lastOpenedUriKey]
    }

    fun markdownEnabled(uriString: String): Flow<Boolean> {
        val key = booleanPreferencesKey("markdown_enabled_${fileId(uriString)}")
        return appContext.appDataStore.data.map { preferences ->
            preferences[key] ?: false
        }
    }

    fun progressOffsetChar(uriString: String, isMarkdown: Boolean): Flow<Int> {
        val key = progressOffsetKey(uriString, isMarkdown)
        return appContext.appDataStore.data.map { preferences ->
            preferences[key] ?: 0
        }
    }

    fun bigProgressPageIndex(uriString: String): Flow<Int> {
        val key = bigProgressPageIndexKey(uriString)
        return appContext.appDataStore.data.map { preferences ->
            preferences[key] ?: 0
        }
    }

    fun bigProgressOffsetCharInPage(uriString: String): Flow<Int> {
        val key = bigProgressOffsetCharInPageKey(uriString)
        return appContext.appDataStore.data.map { preferences ->
            preferences[key] ?: 0
        }
    }

    suspend fun setShowFileName(enabled: Boolean) {
        appContext.appDataStore.edit { preferences ->
            preferences[showFileNameKey] = enabled
        }
    }

    suspend fun setShowCharset(enabled: Boolean) {
        appContext.appDataStore.edit { preferences ->
            preferences[showCharsetKey] = enabled
        }
    }

    suspend fun setTapToToggleHidden(enabled: Boolean) {
        appContext.appDataStore.edit { preferences ->
            preferences[tapToToggleHiddenKey] = enabled
        }
    }

    suspend fun setAutoHideTextOnEnter(enabled: Boolean) {
        appContext.appDataStore.edit { preferences ->
            preferences[autoHideTextOnEnterKey] = enabled
        }
    }

    suspend fun setHideHintWhenHidden(enabled: Boolean) {
        appContext.appDataStore.edit { preferences ->
            preferences[hideHintWhenHiddenKey] = enabled
        }
    }

    suspend fun setHideStatusBar(enabled: Boolean) {
        appContext.appDataStore.edit { preferences ->
            preferences[hideStatusBarKey] = enabled
        }
    }

    suspend fun setHideNavigationBar(enabled: Boolean) {
        appContext.appDataStore.edit { preferences ->
            preferences[hideNavigationBarKey] = enabled
        }
    }

    suspend fun setLastOpenedUriString(uriString: String) {
        appContext.appDataStore.edit { preferences ->
            preferences[lastOpenedUriKey] = uriString
        }
    }

    suspend fun setMarkdownEnabled(uriString: String, enabled: Boolean) {
        val key = booleanPreferencesKey("markdown_enabled_${fileId(uriString)}")
        appContext.appDataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }

    suspend fun setProgressOffsetChar(uriString: String, isMarkdown: Boolean, offset: Int) {
        val key = progressOffsetKey(uriString, isMarkdown)
        appContext.appDataStore.edit { preferences ->
            preferences[key] = offset.coerceAtLeast(0)
        }
    }

    suspend fun setBigProgressPageIndex(uriString: String, pageIndex: Int) {
        val key = bigProgressPageIndexKey(uriString)
        appContext.appDataStore.edit { preferences ->
            preferences[key] = pageIndex.coerceAtLeast(0)
        }
    }

    suspend fun setBigProgressOffsetCharInPage(uriString: String, offset: Int) {
        val key = bigProgressOffsetCharInPageKey(uriString)
        appContext.appDataStore.edit { preferences ->
            preferences[key] = offset.coerceAtLeast(0)
        }
    }

    private fun progressOffsetKey(uriString: String, isMarkdown: Boolean): Preferences.Key<Int> {
        val suffix = if (isMarkdown) "md" else "plain"
        return intPreferencesKey("progress_offset_char_${suffix}_${fileId(uriString)}")
    }

    private fun bigProgressPageIndexKey(uriString: String): Preferences.Key<Int> {
        return intPreferencesKey("big_progress_page_index_${fileId(uriString)}")
    }

    private fun bigProgressOffsetCharInPageKey(uriString: String): Preferences.Key<Int> {
        return intPreferencesKey("big_progress_offset_char_in_page_${fileId(uriString)}")
    }

    /**
     * 生成文件标识符。
     *
     * 使用 URI 的 SHA-256 哈希前 8 字节作为标识，用于构建文件级偏好设置的键。
     * 这样可以避免 URI 字符串过长或包含特殊字符导致的键名问题。
     */
    private fun fileId(uriString: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uriString.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { b -> "%02x".format(b) }
    }
}
