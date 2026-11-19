package com.example.browser

data class BrowserTab(
    val id: String,
    val title: String = "LT Browser Home",
    val url: String = HOME_URL,
    val isIncognito: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val desktopMode: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
) {
    companion object {
        const val HOME_URL = "about:lt-home"
    }
}

data class DetectedWebMedia(
    val url: String,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long = -1L,
    val qualityLabel: String? = null,
    val supportsRange: Boolean = true
)

data class BrowserQuickDial(
    val title: String,
    val subtitle: String,
    val url: String
)

data class BrowserSuggestion(
    val title: String,
    val url: String,
    val isBookmark: Boolean
)
