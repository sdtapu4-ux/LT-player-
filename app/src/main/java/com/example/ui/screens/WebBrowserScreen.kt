package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.browser.BrowserSuggestion
import com.example.browser.BrowserTab
import com.example.browser.DetectedWebMedia
import com.example.browser.SmartVideoDetector
import com.example.data.database.BrowserBookmarkEntity
import com.example.data.database.BrowserHistoryEntity
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils
import kotlinx.coroutines.launch
import java.util.UUID

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebBrowserScreen(
    bookmarks: List<BrowserBookmarkEntity>,
    history: List<BrowserHistoryEntity>,
    buildSuggestions: (String) -> List<BrowserSuggestion>,
    onRecordVisit: (url: String, title: String, isIncognito: Boolean) -> Unit,
    onToggleBookmark: (url: String, title: String, isBookmarked: Boolean) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onQueueDownload: (media: DetectedWebMedia) -> Unit,
    onOpenDownloads: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val tabs = remember {
        mutableStateListOf(BrowserTab(id = UUID.randomUUID().toString()))
    }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val webViews = remember { mutableStateMapOf<String, WebView>() }
    val detectedMediaByTab = remember { mutableStateMapOf<String, List<DetectedWebMedia>>() }
    val probedUrlsByTab = remember { mutableStateMapOf<String, MutableSet<String>>() }

    var addressInput by remember(activeTab.id, activeTab.url) {
        mutableStateOf(if (activeTab.url == BrowserTab.HOME_URL) "" else activeTab.url)
    }
    var addressFocused by remember { mutableStateOf(false) }
    var isFullscreenBrowsing by remember { mutableStateOf(false) }
    var html5FullscreenView by remember { mutableStateOf<View?>(null) }
    var html5FullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showDetectedMediaSheet by remember { mutableStateOf(false) }

    fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx >= 0) {
            tabs[idx] = transform(tabs[idx])
        }
    }

    fun inspectAndAddMediaUrl(tabId: String, rawUrl: String, pageTitle: String?) {
        if (!SmartVideoDetector.isDirectDownloadableMediaUrl(rawUrl)) return
        val seen = probedUrlsByTab.getOrPut(tabId) { mutableSetOf() }
        if (!seen.add(rawUrl)) return
        scope.launch {
            val probed = SmartVideoDetector.probeMediaUrl(rawUrl, pageTitle) ?: return@launch
            val existing = detectedMediaByTab[tabId].orEmpty()
            if (existing.none { it.url == probed.url }) {
                detectedMediaByTab[tabId] = listOf(probed) + existing
            }
        }
    }

    fun destroyTabWebView(tabId: String, isIncognito: Boolean) {
        webViews.remove(tabId)?.let { wv ->
            if (isIncognito) {
                wv.clearHistory()
                wv.clearFormData()
                wv.clearCache(true)
            }
            wv.stopLoading()
            wv.destroy()
        }
        detectedMediaByTab.remove(tabId)
        probedUrlsByTab.remove(tabId)
    }

    DisposableEffect(Unit) {
        onDispose {
            html5FullscreenCallback?.onCustomViewHidden()
            webViews.keys.toList().forEach { id ->
                val isIncog = tabs.firstOrNull { it.id == id }?.isIncognito ?: false
                destroyTabWebView(id, isIncog)
            }
        }
    }

    fun navigateActiveTab(rawTarget: String) {
        focusManager.clearFocus()
        addressFocused = false
        val resolved = SmartVideoDetector.resolveNavigationTarget(rawTarget)
        val tabId = activeTab.id
        detectedMediaByTab[tabId] = emptyList()
        probedUrlsByTab[tabId] = mutableSetOf()
        updateTab(tabId) {
            it.copy(
                url = resolved,
                title = if (resolved == BrowserTab.HOME_URL) "LT Browser Home" else resolved
            )
        }
        addressInput = if (resolved == BrowserTab.HOME_URL) "" else resolved
        if (resolved != BrowserTab.HOME_URL) {
            webViews[tabId]?.loadUrl(resolved)
        }
    }

    fun openNewTab(incognito: Boolean, initialUrl: String = BrowserTab.HOME_URL) {
        val id = UUID.randomUUID().toString()
        tabs.add(
            BrowserTab(
                id = id,
                title = if (incognito) "Private Tab" else "LT Browser Home",
                url = initialUrl,
                isIncognito = incognito
            )
        )
        activeTabId = id
        showTabsSheet = false
    }

    fun closeTab(tab: BrowserTab) {
        if (tabs.size == 1) {
            destroyTabWebView(tab.id, tab.isIncognito)
            val fresh = BrowserTab(id = tab.id, isIncognito = false)
            tabs[0] = fresh
            activeTabId = fresh.id
            return
        }
        val idx = tabs.indexOfFirst { it.id == tab.id }
        destroyTabWebView(tab.id, tab.isIncognito)
        tabs.removeAt(idx)
        if (activeTabId == tab.id) {
            activeTabId = tabs[idx.coerceAtMost(tabs.lastIndex)].id
        }
    }

    BackHandler {
        when {
            html5FullscreenView != null -> {
                html5FullscreenCallback?.onCustomViewHidden()
                html5FullscreenView = null
                html5FullscreenCallback = null
            }
            isFullscreenBrowsing -> isFullscreenBrowsing = false
            activeTab.url != BrowserTab.HOME_URL && webViews[activeTab.id]?.canGoBack() == true -> {
                webViews[activeTab.id]?.goBack()
            }
            activeTab.url != BrowserTab.HOME_URL -> {
                navigateActiveTab(BrowserTab.HOME_URL)
            }
            else -> onBack()
        }
    }

    val isBookmarked = remember(bookmarks, activeTab.url) {
        bookmarks.any { it.url == activeTab.url }
    }
    val detectedForTab = detectedMediaByTab[activeTab.id].orEmpty()
    val suggestions = remember(addressInput, addressFocused, bookmarks, history) {
        if (addressFocused && addressInput.isNotBlank()) {
            buildSuggestions(addressInput)
        } else emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullscreenBrowsing) {
                // Top Browser Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("browser_back_exit_button")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit Browser")
                            }

                            OutlinedTextField(
                                value = addressInput,
                                onValueChange = {
                                    addressInput = it
                                    addressFocused = true
                                },
                                placeholder = {
                                    Text(
                                        if (activeTab.isIncognito) "Private search or HTTPS URL" else "Search Google or enter URL",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when {
                                            activeTab.isIncognito -> Icons.Rounded.VisibilityOff
                                            activeTab.url == BrowserTab.HOME_URL -> Icons.Rounded.Search
                                            else -> Icons.Rounded.Lock
                                        },
                                        contentDescription = null,
                                        tint = if (activeTab.isIncognito) AmberGold else ElectricGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (addressInput.isNotEmpty()) {
                                        IconButton(onClick = { addressInput = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { navigateActiveTab(addressInput) }),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    focusedBorderColor = ElectricGreen,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("browser_url_input")
                            )

                            if (activeTab.url.startsWith("https://")) {
                                IconButton(
                                    onClick = {
                                        onToggleBookmark(activeTab.url, activeTab.title, isBookmarked)
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) AmberGold else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (activeTab.isLoading) {
                                        webViews[activeTab.id]?.stopLoading()
                                    } else if (activeTab.url != BrowserTab.HOME_URL) {
                                        webViews[activeTab.id]?.reload()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (activeTab.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                    contentDescription = if (activeTab.isLoading) "Stop" else "Reload"
                                )
                            }

                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Browser Menu")
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New Tab") },
                                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNewTab(incognito = false)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("New Private Incognito Tab") },
                                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = AmberGold) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNewTab(incognito = true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (activeTab.desktopMode) "Mobile Site Mode" else "Desktop Site Mode") },
                                        leadingIcon = { Icon(Icons.Rounded.DesktopWindows, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            val nextDesktop = !activeTab.desktopMode
                                            updateTab(activeTab.id) { it.copy(desktopMode = nextDesktop) }
                                            webViews[activeTab.id]?.apply {
                                                settings.userAgentString = if (nextDesktop) DESKTOP_UA else MOBILE_UA
                                                reload()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fullscreen Browsing") },
                                        leadingIcon = { Icon(Icons.Rounded.Fullscreen, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            isFullscreenBrowsing = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Bookmarks (${bookmarks.size})") },
                                        leadingIcon = { Icon(Icons.Rounded.Bookmarks, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showBookmarksSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Browsing History") },
                                        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showHistorySheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Download Manager") },
                                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = ElectricGreen) },
                                        onClick = {
                                            showMoreMenu = false
                                            onOpenDownloads()
                                        }
                                    )
                                }
                            }
                        }

                        if (activeTab.isLoading && activeTab.url != BrowserTab.HOME_URL) {
                            LinearProgressIndicator(
                                progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = ElectricGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        if (suggestions.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                suggestions.forEach { sug ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navigateActiveTab(sug.url) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (sug.isBookmark) Icons.Rounded.Bookmark else Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = if (sug.isBookmark) AmberGold else PremiumBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sug.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                sug.url,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Content Area: Quick Dial Home OR Active Secure WebView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activeTab.url == BrowserTab.HOME_URL) {
                    BrowserHomePage(
                        isIncognito = activeTab.isIncognito,
                        bookmarks = bookmarks,
                        onOpenUrl = { navigateActiveTab(it) },
                        onOpenDownloads = onOpenDownloads
                    )
                } else {
                    val tabId = activeTab.id
                    AndroidView(
                        factory = { ctx ->
                            webViews.getOrPut(tabId) {
                                createConfiguredWebView(
                                    context = ctx,
                                    tab = activeTab,
                                    onUrlChanged = { newUrl, canBack, canFwd ->
                                        updateTab(tabId) {
                                            it.copy(
                                                url = newUrl,
                                                canGoBack = canBack,
                                                canGoForward = canFwd
                                            )
                                        }
                                        if (tabId == activeTabId && !addressFocused) {
                                            addressInput = newUrl
                                        }
                                    },
                                    onTitleChanged = { title ->
                                        updateTab(tabId) { it.copy(title = title.ifBlank { it.url }) }
                                    },
                                    onLoadingChanged = { loading, progress ->
                                        updateTab(tabId) { it.copy(isLoading = loading, progress = progress) }
                                    },
                                    onPageFinished = { view, finishedUrl ->
                                        val isIncog = tabs.firstOrNull { it.id == tabId }?.isIncognito ?: false
                                        val pageTitle = view.title ?: finishedUrl
                                        onRecordVisit(finishedUrl, pageTitle, isIncog)
                                        view.evaluateJavascript(SmartVideoDetector.DOM_MEDIA_SCAN_JS) { raw ->
                                            val domUrls = SmartVideoDetector.parseDomScanResult(raw)
                                            domUrls.forEach { mediaUrl ->
                                                inspectAndAddMediaUrl(tabId, mediaUrl, pageTitle)
                                            }
                                        }
                                    },
                                    onInterceptMediaUrl = { reqUrl ->
                                        val curTitle = tabs.firstOrNull { it.id == tabId }?.title
                                        inspectAndAddMediaUrl(tabId, reqUrl, curTitle)
                                    },
                                    onDirectDownloadLink = { dlUrl, contentDisposition, mimeType ->
                                        if (SmartVideoDetector.isBlockedOrDrmUrl(dlUrl)) return@createConfiguredWebView
                                        scope.launch {
                                            val probed = SmartVideoDetector.probeMediaUrl(dlUrl, activeTab.title)
                                                ?: DetectedWebMedia(
                                                    url = dlUrl,
                                                    title = android.webkit.URLUtil.guessFileName(dlUrl, contentDisposition, mimeType),
                                                    mimeType = SmartVideoDetector.inferMimeType(dlUrl, mimeType),
                                                    qualityLabel = SmartVideoDetector.inferQualityLabel(dlUrl, mimeType ?: "")
                                                )
                                            val existing = detectedMediaByTab[tabId].orEmpty()
                                            if (existing.none { it.url == probed.url }) {
                                                detectedMediaByTab[tabId] = listOf(probed) + existing
                                            }
                                            showDetectedMediaSheet = true
                                        }
                                    },
                                    onShowCustomView = { view, cb ->
                                        html5FullscreenView = view
                                        html5FullscreenCallback = cb
                                    },
                                    onHideCustomView = {
                                        html5FullscreenView = null
                                        html5FullscreenCallback = null
                                    }
                                )
                            }
                        },
                        update = { wv ->
                            val targetUa = if (activeTab.desktopMode) DESKTOP_UA else MOBILE_UA
                            if (wv.settings.userAgentString != targetUa) {
                                wv.settings.userAgentString = targetUa
                            }
                            if (wv.url == null && activeTab.url.startsWith("https://")) {
                                wv.loadUrl(activeTab.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isFullscreenBrowsing) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clickable { isFullscreenBrowsing = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Fullscreen", tint = ElectricGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Exit Fullscreen", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }

            if (!isFullscreenBrowsing) {
                // Bottom Browser Controls
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { webViews[activeTab.id]?.goBack() },
                            enabled = activeTab.url != BrowserTab.HOME_URL && activeTab.canGoBack
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Page Back")
                        }
                        IconButton(
                            onClick = { webViews[activeTab.id]?.goForward() },
                            enabled = activeTab.url != BrowserTab.HOME_URL && activeTab.canGoForward
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Page Forward")
                        }
                        IconButton(onClick = { navigateActiveTab(BrowserTab.HOME_URL) }) {
                            Icon(Icons.Rounded.Home, contentDescription = "Browser Home")
                        }
                        IconButton(
                            onClick = {
                                // Trigger an immediate DOM rescan on active page as well
                                webViews[activeTab.id]?.let { wv ->
                                    val title = wv.title ?: activeTab.title
                                    wv.evaluateJavascript(SmartVideoDetector.DOM_MEDIA_SCAN_JS) { raw ->
                                        SmartVideoDetector.parseDomScanResult(raw).forEach { mediaUrl ->
                                            inspectAndAddMediaUrl(activeTab.id, mediaUrl, title)
                                        }
                                    }
                                }
                                showDetectedMediaSheet = true
                            },
                            modifier = Modifier.testTag("browser_detected_download_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (detectedForTab.isNotEmpty()) {
                                        Badge(containerColor = ElectricGreen, contentColor = Color(0xFF00210E)) {
                                            Text(detectedForTab.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = "Detected Media Downloads",
                                    tint = if (detectedForTab.isNotEmpty()) ElectricGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = { showTabsSheet = true },
                            modifier = Modifier.testTag("browser_tabs_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = PremiumBlue, contentColor = Color.White) {
                                        Text(tabs.size.toString())
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Tab, contentDescription = "Open Tabs")
                            }
                        }
                    }
                }
            }
        }

        // HTML5 Fullscreen Video Overlay
        html5FullscreenView?.let { customView ->
            AndroidView(
                factory = {
                    (customView.parent as? ViewGroup)?.removeView(customView)
                    FrameLayout(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        addView(
                            customView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Detected Media Sheet
    if (showDetectedMediaSheet) {
        ModalBottomSheet(onDismissRequest = { showDetectedMediaSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Smart Media Detector", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (detectedForTab.isEmpty()) "No direct non-DRM video or audio files detected on this page yet."
                            else "${detectedForTab.size} direct media file(s) ready to download",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        showDetectedMediaSheet = false
                        onOpenDownloads()
                    }) {
                        Text("Queue")
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (detectedForTab.isEmpty()) {
                    Text(
                        "Play a direct HTML5 video or tap a downloadable HTTPS media link. Adaptive DRM streams (HLS/DASH/Widevine) are filtered out per platform policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detectedForTab, key = { it.url }) { media ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (media.mimeType.startsWith("audio/")) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
                                        contentDescription = null,
                                        tint = ElectricGreen
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(media.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val sizeStr = if (media.sizeBytes > 0L) FormatUtils.formatFileSize(media.sizeBytes) else "Stream size pending"
                                        val qStr = media.qualityLabel?.let { " • $it" } ?: ""
                                        Text(
                                            "$sizeStr$qStr • ${media.mimeType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onQueueDownload(media)
                                            showDetectedMediaSheet = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricGreen,
                                            contentColor = Color(0xFF00210E)
                                        )
                                    ) {
                                        Text("Download")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Tabs Sheet
    if (showTabsSheet) {
        ModalBottomSheet(onDismissRequest = { showTabsSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Open Tabs (${tabs.size})", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openNewTab(incognito = true) }) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Private")
                        }
                        Button(
                            onClick = { openNewTab(incognito = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = Color(0xFF00210E))
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Tab")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTab.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeTabId = tab.id
                                    showTabsSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (tab.isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = if (tab.isIncognito) AmberGold else ElectricGreen
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tab.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (tab.url == BrowserTab.HOME_URL) "Home Quick Dial" else tab.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { closeTab(tab) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close Tab")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Bookmarks Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                if (bookmarks.isEmpty()) {
                    Text(
                        "Tap the bookmark icon on any HTTPS site to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bookmarks, key = { it.url }) { bm ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showBookmarksSheet = false
                                        navigateActiveTab(bm.url)
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = AmberGold)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bm.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { onToggleBookmark(bm.url, bm.title, true) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove Bookmark", tint = CoralError)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // History Sheet
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Browsing History", style = MaterialTheme.typography.titleLarge)
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClearHistory) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear All", color = CoralError)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (history.isEmpty()) {
                    Text(
                        "Visited HTTPS pages appear here (Private Incognito tabs are never saved).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it.id }) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHistorySheet = false
                                        navigateActiveTab(item.url)
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.History, contentDescription = null, tint = PremiumBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { onDeleteHistoryItem(item.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Entry", tint = CoralError)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BrowserHomePage(
    isIncognito: Boolean,
    bookmarks: List<BrowserBookmarkEntity>,
    onOpenUrl: (String) -> Unit,
    onOpenDownloads: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = (if (isIncognito) AmberGold else ElectricGreen).copy(alpha = 0.16f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Public,
                            contentDescription = null,
                            tint = if (isIncognito) AmberGold else ElectricGreen
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isIncognito) "Private Incognito Session" else "LT Smart Web Browser",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isIncognito) "History is not recorded. HTTPS enforced with smart media detection."
                        else "Search Google or visit sites to detect & download non-DRM videos and audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Dial", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenDownloads) {
                Text("Download Queue")
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(SmartVideoDetector.quickDials, key = { it.url }) { dial ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable { onOpenUrl(dial.url) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(dial.title, style = MaterialTheme.typography.titleSmall, color = ElectricGreen)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dial.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (bookmarks.isNotEmpty()) {
                items(bookmarks.take(4), key = { "bm_${it.url}" }) { bm ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onOpenUrl(bm.url) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(bm.title, style = MaterialTheme.typography.titleSmall, color = AmberGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: android.content.Context,
    tab: BrowserTab,
    onUrlChanged: (String, Boolean, Boolean) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLoadingChanged: (Boolean, Int) -> Unit,
    onPageFinished: (WebView, String) -> Unit,
    onInterceptMediaUrl: (String) -> Unit,
    onDirectDownloadLink: (String, String?, String?) -> Unit,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit
): WebView {
    return WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !tab.isIncognito
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = if (tab.desktopMode) DESKTOP_UA else MOBILE_UA
            if (tab.isIncognito) {
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://", ignoreCase = true)) {
                    val upgraded = "https://" + url.substring(7)
                    view.loadUrl(upgraded)
                    return true
                }
                if (!url.startsWith("https://", ignoreCase = true)) {
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val reqUrl = request?.url?.toString()
                if (!reqUrl.isNullOrBlank()) {
                    onInterceptMediaUrl(reqUrl)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                onUrlChanged(url, view.canGoBack(), view.canGoForward())
                onLoadingChanged(true, 15)
            }

            override fun onPageFinished(view: WebView, url: String) {
                onUrlChanged(url, view.canGoBack(), view.canGoForward())
                onLoadingChanged(false, 100)
                onPageFinished(view, url)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                onLoadingChanged(newProgress in 1..99, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) onTitleChanged(title)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                onHideCustomView()
            }
        }

        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            onDirectDownloadLink(url, contentDisposition, mimeType)
        }
    }
}
