package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.database.PlaybackHistoryEntity
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaFolder
import com.example.data.model.PlaylistSummary
import com.example.data.model.StorageBreakdown
import com.example.data.model.VideoItem
import com.example.ui.components.EmptyMediaStateCard
import com.example.ui.components.LTWordmarkBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.SkeletonVideoCardRow
import com.example.ui.components.VideoThumbnailBox
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeVideoDashboardScreen(
    videos: List<VideoItem>,
    folders: List<MediaFolder>,
    playlists: List<PlaylistSummary>,
    downloads: List<DownloadRecord>,
    history: List<PlaybackHistoryEntity>,
    storage: StorageBreakdown,
    isScanning: Boolean,
    hasMediaPermission: Boolean,
    hideThumbnails: Boolean,
    isPremiumActive: Boolean,
    onRequestMediaPermission: () -> Unit,
    onOpenSafFilePicker: () -> Unit,
    onRefresh: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onAddToPlaylist: (VideoItem) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAllFolders: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLtShare: () -> Unit,
    onInspectCodec: (VideoItem) -> Unit,
    onEditVideo: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isGridLayout by remember { mutableStateOf(false) }

    val recentlyPlayed = remember(videos) {
        videos.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }.take(10)
    }
    val continueWatching = remember(videos) {
        videos.filter { it.lastPositionMs > 2_000L && it.progressFraction < 0.96f }
            .sortedByDescending { it.lastPlayedAt }
            .take(10)
    }
    val recentlyAdded = remember(videos) {
        videos.sortedByDescending { it.dateAddedSec }.take(10)
    }
    val favoriteVideos = remember(videos) {
        videos.filter { it.isFavorite }
    }
    val downloadedCompleted = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.SUCCESSFUL }
    }
    val recommendedVideos = remember(videos) {
        videos.filter { it.lastPlayedAt == 0L }.sortedByDescending { it.width * it.height }.take(8)
    }
    val recentlyOpenedHistory = remember(history) {
        history.filter { it.isVideo }.take(8)
    }

    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Top Header
            item {
                DashboardTopHeader(
                    storage = storage,
                    isPremiumActive = isPremiumActive,
                    onOpenPremium = onOpenPremium,
                    onOpenStorage = onOpenStorage,
                    onOpenSearch = onOpenSearch,
                    onOpenDownloads = onOpenDownloads,
                    onOpenLtShare = onOpenLtShare,
                    onOpenSettings = onOpenSettings
                )
            }

            // Permission Banner if not granted
            if (!hasMediaPermission) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Grant Media Access to Scan Device Videos",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "LT PLAYER only scans local media with your permission, or you can open individual files safely using Android Picker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onRequestMediaPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricGreen,
                                        contentColor = Color(0xFF00210E)
                                    ),
                                    modifier = Modifier.testTag("grant_media_permission_button")
                                ) {
                                    Text("Allow Media")
                                }
                                OutlinedButton(
                                    onClick = onOpenSafFilePicker,
                                    modifier = Modifier.testTag("open_saf_picker_button")
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pick File")
                                }
                            }
                        }
                    }
                }
            }

            if (isScanning && videos.isEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    SkeletonVideoCardRow()
                }
            }

            // 1. Recently Played
            if (recentlyPlayed.isNotEmpty()) {
                item { SectionHeader(title = "Recently Played", count = recentlyPlayed.size) }
                item {
                    HorizontalVideoCarousel(
                        items = recentlyPlayed,
                        hideThumbnails = hideThumbnails,
                        onPlay = { onPlayVideo(it, recentlyPlayed) },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }

            // 2. Continue Watching
            if (continueWatching.isNotEmpty()) {
                item { SectionHeader(title = "Continue Watching", count = continueWatching.size) }
                item {
                    HorizontalVideoCarousel(
                        items = continueWatching,
                        hideThumbnails = hideThumbnails,
                        onPlay = { onPlayVideo(it, continueWatching) },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }

            // 3. Recently Added
            if (recentlyAdded.isNotEmpty()) {
                item { SectionHeader(title = "Recently Added", count = recentlyAdded.size) }
                item {
                    HorizontalVideoCarousel(
                        items = recentlyAdded,
                        hideThumbnails = hideThumbnails,
                        onPlay = { onPlayVideo(it, recentlyAdded) },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }

            // 5. Video Folders Quick Strip
            if (folders.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Video Folders",
                        count = folders.size,
                        actionLabel = "Browse All",
                        onActionClick = onOpenAllFolders
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders.take(10), key = { it.path }) { folder ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .width(156.dp)
                                    .clickable(onClick = onOpenAllFolders)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = PremiumBlue,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${folder.videoCount} videos • ${FormatUtils.formatFileSize(folder.totalSizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Favorite Videos
            if (favoriteVideos.isNotEmpty()) {
                item { SectionHeader(title = "Favorite Videos", count = favoriteVideos.size) }
                item {
                    HorizontalVideoCarousel(
                        items = favoriteVideos,
                        hideThumbnails = hideThumbnails,
                        onPlay = { onPlayVideo(it, favoriteVideos) },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }

            // 7. My Playlists
            item {
                SectionHeader(
                    title = "My Playlists",
                    count = playlists.size,
                    actionLabel = "Manage",
                    onActionClick = onOpenPlaylists
                )
            }
            if (playlists.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable(onClick = onOpenPlaylists),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = ElectricGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Create custom video & music playlists",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .width(170.dp)
                                    .clickable(onClick = onOpenPlaylists)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = ElectricGreen)
                                    Spacer(Modifier.height(6.dp))
                                    Text(pl.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${pl.itemCount} items • ${FormatUtils.formatDuration(pl.totalDurationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 8. Downloaded Videos
            if (downloadedCompleted.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Downloaded Videos",
                        count = downloadedCompleted.size,
                        actionLabel = "Manager",
                        onActionClick = onOpenDownloads
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(downloadedCompleted, key = { it.id }) { dl ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .width(200.dp)
                                    .clickable(onClick = onOpenDownloads)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, tint = ElectricGreen)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(dl.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            FormatUtils.formatFileSize(dl.totalBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 9. Recommended Local Media
            if (recommendedVideos.isNotEmpty()) {
                item { SectionHeader(title = "Recommended Local Media", count = recommendedVideos.size) }
                item {
                    HorizontalVideoCarousel(
                        items = recommendedVideos,
                        hideThumbnails = hideThumbnails,
                        onPlay = { onPlayVideo(it, recommendedVideos) },
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }

            // 10. Recently Opened Files
            if (recentlyOpenedHistory.isNotEmpty()) {
                item { SectionHeader(title = "Recently Opened Files", count = recentlyOpenedHistory.size) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentlyOpenedHistory, key = { it.uriString }) { hist ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .width(190.dp)
                                    .clickable {
                                        val match = videos.firstOrNull { it.uriString == hist.uriString }
                                        if (match != null) onPlayVideo(match, videos)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(hist.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${hist.resolution} • ${FormatUtils.formatDuration(hist.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. All Videos Header with Grid/List Toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "All Videos (${videos.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row {
                        IconButton(
                            onClick = onOpenSafFilePicker,
                            modifier = Modifier.testTag("pick_video_file_button")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Open File via Picker")
                        }
                        IconButton(
                            onClick = { isGridLayout = !isGridLayout },
                            modifier = Modifier.testTag("toggle_grid_list_button")
                        ) {
                            Icon(
                                imageVector = if (isGridLayout) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = "Toggle Grid or List"
                            )
                        }
                    }
                }
            }

            if (videos.isEmpty() && !isScanning) {
                item {
                    EmptyMediaStateCard(
                        title = "No Local Videos Found",
                        subtitle = "Add videos to your device or pick any video/folder directly using Android's secure file picker.",
                        primaryButtonText = "Pick Video File",
                        onPrimaryClick = onOpenSafFilePicker,
                        secondaryButtonText = if (!hasMediaPermission) "Grant Access" else "Refresh",
                        onSecondaryClick = if (!hasMediaPermission) onRequestMediaPermission else onRefresh
                    )
                }
            } else if (isGridLayout) {
                val rows = videos.chunked(2)
                items(rows) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { video ->
                            VideoGridCard(
                                video = video,
                                hideThumbnail = hideThumbnails,
                                onClick = { onPlayVideo(video, videos) },
                                onToggleFavorite = { onToggleFavorite(video) },
                                onAddToPlaylist = { onAddToPlaylist(video) },
                                onInspectCodec = { onInspectCodec(video) },
                                onEditVideo = { onEditVideo(video) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(videos, key = { it.uriString }) { video ->
                    VideoListRowCard(
                        video = video,
                        hideThumbnail = hideThumbnails,
                        onClick = { onPlayVideo(video, videos) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onAddToPlaylist = { onAddToPlaylist(video) },
                        onInspectCodec = { onInspectCodec(video) },
                        onEditVideo = { onEditVideo(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardTopHeader(
    storage: StorageBreakdown,
    isPremiumActive: Boolean,
    onOpenPremium: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLtShare: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LTWordmarkBadge()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isPremiumActive) AmberGold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenPremium)
                        .testTag("premium_header_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = "Premium",
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isPremiumActive) "PRO" else "ULTIMATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.testTag("header_search_button")
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Global Search")
                }
                IconButton(
                    onClick = onOpenLtShare,
                    modifier = Modifier.testTag("header_ltshare_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "LT SHARE", tint = ElectricGreen)
                }
                IconButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier.testTag("header_downloads_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download Manager")
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("header_settings_button")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Compact Storage Usage Strip
        val usedFraction = if (storage.totalDeviceBytes > 0L) {
            (storage.usedDeviceBytes.toFloat() / storage.totalDeviceBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenStorage)
                .testTag("storage_strip_button")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = ElectricGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Storage • ${FormatUtils.formatFileSize(storage.availableDeviceBytes)} Free",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Videos ${FormatUtils.formatFileSize(storage.videoBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumBlue
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ElectricGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HorizontalVideoCarousel(
    items: List<VideoItem>,
    hideThumbnails: Boolean,
    onPlay: (VideoItem) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.uriString }) { video ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .width(204.dp)
                    .clickable { onPlay(video) }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Box {
                        VideoThumbnailBox(
                            uriString = video.uriString,
                            contentDescription = video.title,
                            hideThumbnail = hideThumbnails,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.72f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = FormatUtils.formatDuration(video.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PremiumBlue.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = video.resolutionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (video.progressFraction > 0f) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { video.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = ElectricGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${FormatUtils.formatFileSize(video.sizeBytes)} • ${video.folderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onToggleFavorite(video) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (video.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (video.isFavorite) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoGridCard(
    video: VideoItem,
    hideThumbnail: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onInspectCodec: () -> Unit,
    onEditVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box {
                VideoThumbnailBox(
                    uriString = video.uriString,
                    contentDescription = video.title,
                    hideThumbnail = hideThumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${video.resolutionLabel} • ${FormatUtils.formatFileSize(video.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", modifier = Modifier.size(18.dp))
                    }
                    VideoOverflowMenu(
                        expanded = menuOpen,
                        isFavorite = video.isFavorite,
                        onDismiss = { menuOpen = false },
                        onToggleFavorite = onToggleFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                        onInspectCodec = onInspectCodec,
                        onEditVideo = onEditVideo
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListRowCard(
    video: VideoItem,
    hideThumbnail: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onInspectCodec: () -> Unit,
    onEditVideo: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(126.dp)) {
                VideoThumbnailBox(
                    uriString = video.uriString,
                    contentDescription = video.title,
                    hideThumbnail = hideThumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = video.resolutionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${FormatUtils.formatFileSize(video.sizeBytes)} • ${video.folderName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (video.progressFraction > 0f) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { video.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ElectricGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                if (video.compatibilityNote != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = video.compatibilityNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Video options")
                }
                VideoOverflowMenu(
                    expanded = menuOpen,
                    isFavorite = video.isFavorite,
                    onDismiss = { menuOpen = false },
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onInspectCodec = onInspectCodec,
                    onEditVideo = onEditVideo
                )
            }
        }
    }
}

@Composable
private fun VideoOverflowMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onInspectCodec: () -> Unit,
    onEditVideo: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Edit Video") },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = ElectricGreen) },
            onClick = {
                onDismiss()
                onEditVideo()
            }
        )
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
            leadingIcon = {
                Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onToggleFavorite()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist") },
            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
            onClick = {
                onDismiss()
                onAddToPlaylist()
            }
        )
        DropdownMenuItem(
            text = { Text("Inspect Codec Compatibility") },
            leadingIcon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
            onClick = {
                onDismiss()
                onInspectCodec()
            }
        )
    }
}
