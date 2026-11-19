package com.example.data.repository

import com.example.browser.BrowserSuggestion
import com.example.data.database.BrowserBookmarkDao
import com.example.data.database.BrowserBookmarkEntity
import com.example.data.database.BrowserHistoryDao
import com.example.data.database.BrowserHistoryEntity
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val bookmarkDao: BrowserBookmarkDao,
    private val historyDao: BrowserHistoryDao
) {
    val bookmarks: Flow<List<BrowserBookmarkEntity>> = bookmarkDao.observeAll()
    val history: Flow<List<BrowserHistoryEntity>> = historyDao.observeRecent()

    suspend fun recordVisit(url: String, title: String, isIncognito: Boolean) {
        if (isIncognito) return
        val normalized = url.trim()
        if (!normalized.startsWith("https://")) return
        historyDao.insert(
            BrowserHistoryEntity(
                url = normalized,
                title = title.ifBlank { normalized },
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleBookmark(url: String, title: String, isCurrentlyBookmarked: Boolean) {
        val normalized = url.trim()
        if (!normalized.startsWith("https://")) return
        if (isCurrentlyBookmarked) {
            bookmarkDao.deleteByUrl(normalized)
        } else {
            bookmarkDao.upsert(
                BrowserBookmarkEntity(
                    url = normalized,
                    title = title.ifBlank { normalized },
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteHistoryItem(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    fun buildSuggestions(
        query: String,
        bookmarks: List<BrowserBookmarkEntity>,
        history: List<BrowserHistoryEntity>
    ): List<BrowserSuggestion> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val bookmarkSuggestions = bookmarks
            .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .map { BrowserSuggestion(it.title, it.url, isBookmark = true) }
        val seen = bookmarkSuggestions.map { it.url }.toMutableSet()
        val historySuggestions = history
            .filter {
                it.url !in seen &&
                    (it.title.lowercase().contains(q) || it.url.lowercase().contains(q))
            }
            .distinctBy { it.url }
            .map {
                seen.add(it.url)
                BrowserSuggestion(it.title, it.url, isBookmark = false)
            }
        return (bookmarkSuggestions + historySuggestions).take(6)
    }
}
