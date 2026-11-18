package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.FavoriteMediaEntity
import com.example.data.database.LTPlayerDatabase
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistItemEntity
import com.example.download.DownloadRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: LTPlayerDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LTPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun appBranding_matchesLTPlayer() {
        assertEquals("LT PLAYER", context.getString(R.string.app_name))
    }

    @Test
    fun roomDatabase_playlistsAndFavoritesPersistProperly() = runTest {
        val playlistId = db.playlistDao().insertPlaylist(
            PlaylistEntity(name = "Cinema Favorites", createdAt = 100L, updatedAt = 100L)
        )
        db.playlistDao().insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                uriString = "content://media/external/video/media/42",
                title = "Sample 4K Clip",
                durationMs = 120_000L,
                isVideo = true,
                sortOrder = 0,
                addedAt = 100L
            )
        )
        val items = db.playlistDao().observeItems(playlistId).first()
        assertEquals(1, items.size)
        assertEquals("Sample 4K Clip", items[0].title)

        db.favoriteMediaDao().insert(
            FavoriteMediaEntity("content://media/external/video/media/42", "Sample 4K Clip", true, 100L)
        )
        assertTrue(db.favoriteMediaDao().isFavorite("content://media/external/video/media/42"))
    }

    @Test
    fun downloadRepository_blocksInsecureAndDrmManifestUrls() {
        val repo = DownloadRepository(context, db.downloadDao())
        assertTrue(repo.validateAuthorizedUrl("https://cdn.example.com/movie.mp4").isSuccess)
        assertFalse(repo.validateAuthorizedUrl("http://cdn.example.com/movie.mp4").isSuccess)
        assertFalse(repo.validateAuthorizedUrl("https://cdn.example.com/stream.m3u8").isSuccess)
        assertFalse(repo.validateAuthorizedUrl("https://cdn.example.com/manifest.mpd").isSuccess)
    }
}
