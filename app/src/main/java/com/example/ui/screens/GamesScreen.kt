package com.example.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.InstalledGameInfo
import com.example.ui.components.EmptyMediaStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.PremiumBlue

@Composable
fun GamesScreen(
    games: List<InstalledGameInfo>,
    onLaunchGame: (InstalledGameInfo) -> Unit,
    onToggleFavorite: (InstalledGameInfo) -> Unit,
    onRefreshGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var infoTarget by remember { mutableStateOf<InstalledGameInfo?>(null) }
    val recentGames = remember(games) { games.filter { it.lastOpenedAt > 0L } }
    val favGames = remember(games) { games.filter { it.isFavorite } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Game Hub", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Installed Android games detected on your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefreshGames) { Text("Refresh") }
            }
        }

        if (recentGames.isNotEmpty()) {
            item { SectionHeader("Recently Opened Games", recentGames.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentGames, key = { it.packageName }) { game ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(150.dp).clickable { onLaunchGame(game) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                GameIconView(game.packageName, 48.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(game.appName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Play", style = MaterialTheme.typography.labelSmall, color = ElectricGreen)
                            }
                        }
                    }
                }
            }
        }

        if (favGames.isNotEmpty()) {
            item { SectionHeader("Favorite Games", favGames.size) }
            items(favGames, key = { "fav_${it.packageName}" }) { game ->
                GameListCard(
                    game = game,
                    onLaunch = { onLaunchGame(game) },
                    onToggleFav = { onToggleFavorite(game) },
                    onShowInfo = { infoTarget = game }
                )
            }
        }

        item { SectionHeader("Installed Game Library", games.size) }

        if (games.isEmpty()) {
            item {
                EmptyMediaStateCard(
                    title = "No Installed Games Detected",
                    subtitle = "Install any Android game tagged with CATEGORY_GAME on your device and tap Refresh to launch it directly from LT PLAYER.",
                    primaryButtonText = "Scan Again",
                    onPrimaryClick = onRefreshGames
                )
            }
        } else {
            items(games, key = { it.packageName }) { game ->
                GameListCard(
                    game = game,
                    onLaunch = { onLaunchGame(game) },
                    onToggleFav = { onToggleFavorite(game) },
                    onShowInfo = { infoTarget = game }
                )
            }
        }
    }

    infoTarget?.let { g ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(g.appName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Package: ${g.packageName}")
                    Text("Version: ${g.versionName}")
                    Text("Source: Device PackageManager")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    infoTarget = null
                    onLaunchGame(g)
                }) { Text("Launch") }
            },
            dismissButton = { TextButton(onClick = { infoTarget = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun GameListCard(
    game: InstalledGameInfo,
    onLaunch: () -> Unit,
    onToggleFav: () -> Unit,
    onShowInfo: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onLaunch)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIconView(game.packageName, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("v${game.versionName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleFav) {
                Icon(
                    if (game.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite Game",
                    tint = if (game.isFavorite) ElectricGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShowInfo) {
                Icon(Icons.Rounded.Info, contentDescription = "Game Info")
            }
            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = Color(0xFF00210E)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
        }
    }
}

@Composable
private fun GameIconView(packageName: String, sizeDp: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = packageName,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = PremiumBlue)
        }
    }
}
