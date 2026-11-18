package com.example.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.CodecSupportReport
import com.example.data.model.VideoItem
import com.example.data.scanner.CodecInspector
import com.example.ui.MainViewModel
import com.example.ui.screens.AiVideoWorkspaceScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.FullScreenPlayerScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.HomeVideoDashboardScreen
import com.example.ui.screens.MeAndSettingsScreen
import com.example.ui.screens.MusicPlayerScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.PremiumSubscriptionScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StorageManagementScreen
import com.example.ui.theme.ElectricGreen
import kotlinx.serialization.Serializable

@Serializable data object RouteSplash
@Serializable data object RouteOnboarding
@Serializable data object RouteVideoHome
@Serializable data object RouteMusic
@Serializable data object RouteAiVideo
@Serializable data object RouteGame
@Serializable data object RouteMe
@Serializable data object RouteFullPlayer
@Serializable data object RouteFolders
@Serializable data object RoutePlaylists
@Serializable data object RouteSearch
@Serializable data object RouteDownloads
@Serializable data object RouteStorage
@Serializable data object RoutePremium

private data class BottomTabItem(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun LTAppNavigation(
    viewModel: MainViewModel,
    incomingViewUri: Uri?
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistSummaries.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val playbackHistory by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val storage by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val games by viewModel.installedGames.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val billingState by viewModel.billingState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var codecInspectReport by remember { mutableStateOf<CodecSupportReport?>(null) }
    var playlistAddTarget by remember { mutableStateOf<VideoItem?>(null) }

    fun checkMediaPermissionsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasMediaPermission by remember { mutableStateOf(checkMediaPermissionsGranted()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasMediaPermission = checkMediaPermissionsGranted()
        viewModel.refreshAll()
    }

    fun requestMediaPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    val safFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openPickedVideoUri(uri) {
                navController.navigate(RouteFullPlayer)
            }
        }
    }

    val safFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.addSafFolder(uri)
        }
    }

    val safSubtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.playerController.loadExternalSubtitleUri(uri, settings.subtitlePrefs.encoding)
            viewModel.showMessage("External subtitle loaded.")
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    LaunchedEffect(incomingViewUri) {
        if (incomingViewUri != null) {
            viewModel.openPickedVideoUri(incomingViewUri) {
                navController.navigate(RouteFullPlayer)
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val topLevelTabs = remember {
        listOf(
            BottomTabItem("VIDEO", RouteVideoHome, Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle, "tab_video"),
            BottomTabItem("MUSIC", RouteMusic, Icons.Rounded.MusicNote, Icons.Outlined.MusicNote, "tab_music"),
            BottomTabItem("AI VIDEO", RouteAiVideo, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_ai_video"),
            BottomTabItem("GAME", RouteGame, Icons.Rounded.SportsEsports, Icons.Outlined.SportsEsports, "tab_game"),
            BottomTabItem("ME", RouteMe, Icons.Rounded.Person, Icons.Outlined.Person, "tab_me")
        )
    }

    val showMainNav = currentDestination?.let { dest ->
        dest.hasRoute(RouteVideoHome::class) ||
            dest.hasRoute(RouteMusic::class) ||
            dest.hasRoute(RouteAiVideo::class) ||
            dest.hasRoute(RouteGame::class) ||
            dest.hasRoute(RouteMe::class)
    } ?: false

    fun navigateTopTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showMainNav && !isExpandedScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricGreen,
                                    selectedTextColor = ElectricGreen,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showMainNav && isExpandedScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        topLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricGreen,
                                    selectedTextColor = ElectricGreen,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (showMainNav) Modifier.statusBarsPadding() else Modifier),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = RouteSplash,
                        enterTransition = { fadeIn(animationSpec = tween(220)) },
                        exitTransition = { fadeOut(animationSpec = tween(180)) },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (currentDestination?.hasRoute(RouteFullPlayer::class) == true) Modifier
                                else Modifier.widthIn(max = 900.dp)
                            )
                    ) {
                        composable<RouteSplash> {
                            SplashScreen(
                                onFinished = {
                                    val dest: Any = if (settings.onboardingCompleted) RouteVideoHome else RouteOnboarding
                                    navController.navigate(dest) {
                                        popUpTo(RouteSplash) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<RouteOnboarding> {
                            OnboardingScreen(
                                onComplete = {
                                    viewModel.completeOnboarding()
                                    navController.navigate(RouteVideoHome) {
                                        popUpTo(RouteOnboarding) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<RouteVideoHome> {
                            HomeVideoDashboardScreen(
                                videos = videos,
                                folders = folders,
                                playlists = playlists,
                                downloads = downloads,
                                history = playbackHistory,
                                storage = storage,
                                isScanning = isScanning,
                                hasMediaPermission = hasMediaPermission,
                                hideThumbnails = settings.privacyHideRecentThumbs,
                                isPremiumActive = billingState.isPremiumActive,
                                onRequestMediaPermission = { requestMediaPermissions() },
                                onOpenSafFilePicker = { safFilePicker.launch(arrayOf("video/*", "audio/*")) },
                                onRefresh = { viewModel.refreshAll() },
                                onPlayVideo = { video, queue ->
                                    viewModel.playVideo(video, queue)
                                    navController.navigate(RouteFullPlayer)
                                },
                                onToggleFavorite = { viewModel.toggleFavoriteVideo(it) },
                                onAddToPlaylist = { playlistAddTarget = it },
                                onOpenSearch = { navController.navigate(RouteSearch) },
                                onOpenDownloads = { navController.navigate(RouteDownloads) },
                                onOpenSettings = { navigateTopTab(RouteMe) },
                                onOpenPremium = { navController.navigate(RoutePremium) },
                                onOpenStorage = { navController.navigate(RouteStorage) },
                                onOpenAllFolders = { navController.navigate(RouteFolders) },
                                onOpenPlaylists = { navController.navigate(RoutePlaylists) },
                                onInspectCodec = { video ->
                                    codecInspectReport = CodecInspector.inspectUri(
                                        context,
                                        Uri.parse(video.uriString),
                                        video.containerExtension
                                    )
                                }
                            )
                        }

                        composable<RouteMusic> {
                            MusicPlayerScreen(
                                tracks = audioTracks,
                                playlists = playlists,
                                playerController = viewModel.playerController,
                                playerState = playerState,
                                hasPermission = hasMediaPermission,
                                onRequestPermission = { requestMediaPermissions() },
                                onPlayAudio = { track, queue -> viewModel.playAudio(track, queue) },
                                onToggleFavorite = { viewModel.toggleFavoriteAudio(it) },
                                onOpenPlaylists = { navController.navigate(RoutePlaylists) }
                            )
                        }

                        composable<RouteAiVideo> {
                            AiVideoWorkspaceScreen(
                                videos = videos,
                                aiState = aiState,
                                onSummarize = { v, notes -> viewModel.runAiSummary(v, notes) },
                                onDescribeScene = { v -> viewModel.runAiSceneDescription(v) },
                                onGenerateSubtitles = { v, script -> viewModel.runAiSubtitleGeneration(v, script) },
                                onTranslateSubtitles = { src, lang -> viewModel.runAiSubtitleTranslation(src, lang) },
                                onExplainMetadata = { v -> viewModel.runAiMetadataExplanation(v) },
                                onAssistEditing = { v, goal -> viewModel.runAiEditingAssistance(v, goal) },
                                onGenerateThumbnail = { prompt -> viewModel.runAiThumbnailGeneration(prompt) },
                                onClearResult = { viewModel.clearAiState() }
                            )
                        }

                        composable<RouteGame> {
                            GamesScreen(
                                games = games,
                                onLaunchGame = { viewModel.launchGame(it) },
                                onToggleFavorite = { viewModel.toggleFavoriteGame(it) },
                                onRefreshGames = { viewModel.scanInstalledGames() }
                            )
                        }

                        composable<RouteMe> {
                            MeAndSettingsScreen(
                                settings = settings,
                                storage = storage,
                                isPremiumActive = billingState.isPremiumActive,
                                onUpdateTheme = { viewModel.updateThemeMode(it) },
                                onUpdateLanguage = { viewModel.updateLanguage(it) },
                                onUpdateProfile = { n, idx -> viewModel.updateProfile(n, idx) },
                                onUpdatePlayerDefaults = { sp, ar, gb, gv, gs, ap, ksa, bg, notif, priv ->
                                    viewModel.updatePlayerDefaults(sp, ar, gb, gv, gs, ap, ksa, bg, notif, priv)
                                },
                                onUpdateSubtitlePrefs = { viewModel.updateSubtitlePreferences(it) },
                                onOpenPremium = { navController.navigate(RoutePremium) },
                                onOpenStorage = { navController.navigate(RouteStorage) },
                                onOpenDownloads = { navController.navigate(RouteDownloads) },
                                onOpenFolders = { navController.navigate(RouteFolders) },
                                onClearHistory = { viewModel.clearHistory() },
                                onResetSettings = { viewModel.resetSettings() }
                            )
                        }

                        composable<RouteFullPlayer> {
                            FullScreenPlayerScreen(
                                playerController = viewModel.playerController,
                                playerState = playerState,
                                settings = settings,
                                onBack = { navController.popBackStack() },
                                onPickExternalSubtitle = {
                                    safSubtitlePicker.launch(arrayOf("*/*"))
                                },
                                onUpdateSubtitlePrefs = { viewModel.updateSubtitlePreferences(it) },
                                onToggleBackgroundAudio = { enabled ->
                                    viewModel.updatePlayerDefaults(backgroundAudio = enabled)
                                    viewModel.showMessage(if (enabled) "Background audio playback enabled" else "Background audio disabled")
                                }
                            )
                        }

                        composable<RouteFolders> {
                            FoldersScreen(
                                folders = folders,
                                videos = videos,
                                playlists = playlists,
                                hideThumbnails = settings.privacyHideRecentThumbs,
                                onAddSafFolder = { safFolderPicker.launch(null) },
                                onPlayVideos = { list, idx ->
                                    viewModel.playVideo(list[idx], list)
                                    navController.navigate(RouteFullPlayer)
                                },
                                onAddVideoToPlaylist = { plId, video -> viewModel.addVideoToPlaylist(plId, video) },
                                onRenameVideo = { v, name -> viewModel.renameVideoFile(v, name) },
                                onDeleteVideo = { v -> viewModel.deleteVideoFile(v) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<RoutePlaylists> {
                            PlaylistsScreen(
                                playlists = playlists,
                                allVideos = videos,
                                allAudio = audioTracks,
                                observePlaylistItems = { viewModel.observePlaylistItems(it) },
                                onCreatePlaylist = { viewModel.createPlaylist(it) },
                                onRenamePlaylist = { id, n -> viewModel.renamePlaylist(id, n) },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onAddVideoToPlaylist = { plId, v -> viewModel.addVideoToPlaylist(plId, v) },
                                onRemoveItem = { viewModel.removePlaylistItem(it) },
                                onMoveItem = { list, f, t -> viewModel.movePlaylistItem(list, f, t) },
                                onPlayVideos = { list, idx ->
                                    viewModel.playVideo(list[idx], list)
                                    navController.navigate(RouteFullPlayer)
                                },
                                onPlayAudio = { list, idx ->
                                    viewModel.playAudio(list[idx], list)
                                    navigateTopTab(RouteMusic)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<RouteSearch> {
                            SearchScreen(
                                videos = videos,
                                audio = audioTracks,
                                folders = folders,
                                playlists = playlists,
                                downloads = downloads,
                                searchHistory = searchHistory,
                                onRecordSearch = { viewModel.recordSearch(it) },
                                onClearSearchHistory = { viewModel.clearSearchHistory() },
                                onPlayVideo = { video, queue ->
                                    viewModel.playVideo(video, queue)
                                    navController.navigate(RouteFullPlayer)
                                },
                                onPlayAudio = { track, queue ->
                                    viewModel.playAudio(track, queue)
                                    navigateTopTab(RouteMusic)
                                },
                                onOpenFolders = { navController.navigate(RouteFolders) },
                                onOpenPlaylists = { navController.navigate(RoutePlaylists) },
                                onOpenDownloads = { navController.navigate(RouteDownloads) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<RouteDownloads> {
                            DownloadsScreen(
                                downloads = downloads,
                                onStartDownload = { url, title, pub -> viewModel.startDownload(url, title, pub) },
                                onCancelDownload = { viewModel.cancelDownload(it) },
                                onRetryDownload = { viewModel.retryDownload(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<RouteStorage> {
                            StorageManagementScreen(
                                storage = storage,
                                videos = videos,
                                duplicateGroups = duplicates,
                                hasMediaPermission = hasMediaPermission,
                                onRequestPermission = { requestMediaPermissions() },
                                onClearThumbnailCache = { viewModel.clearThumbnailCache() },
                                onDeleteVideo = { viewModel.deleteVideoFile(it) },
                                onOpenDownloads = { navController.navigate(RouteDownloads) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<RoutePremium> {
                            PremiumSubscriptionScreen(
                                billingState = billingState,
                                onLaunchPurchase = { act, prod -> viewModel.launchPremiumPurchase(act, prod) },
                                onRestorePurchases = { viewModel.restorePurchases() },
                                onRetryBilling = { viewModel.refreshBilling() },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    codecInspectReport?.let { report ->
        AlertDialog(
            onDismissRequest = { codecInspectReport = null },
            title = { Text("Codec & Container Inspection") },
            text = {
                Column {
                    Text("Container: ${report.container}")
                    Text("Video Track: ${report.videoCodec ?: "None"}")
                    Text("Audio Track: ${report.audioCodec ?: "None"}")
                    Spacer(Modifier.height(6.dp))
                    Text(report.message, color = if (report.isVideoSupported && report.isAudioSupported) ElectricGreen else MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { codecInspectReport = null }) { Text("OK") } }
        )
    }

    playlistAddTarget?.let { targetVideo ->
        AlertDialog(
            onDismissRequest = { playlistAddTarget = null },
            title = { Text("Add '${targetVideo.title}' to Playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("No playlists yet. Create one in My Playlists.")
                    } else {
                        playlists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addVideoToPlaylist(pl.id, targetVideo)
                                        playlistAddTarget = null
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(pl.name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    playlistAddTarget = null
                    navController.navigate(RoutePlaylists)
                }) { Text("Manage Playlists") }
            },
            dismissButton = { TextButton(onClick = { playlistAddTarget = null }) { Text("Cancel") } }
        )
    }
}
