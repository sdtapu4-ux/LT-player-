package com.example

import android.content.Context
import com.example.data.database.LTPlayerDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.AiVideoRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.BrowserRepository
import com.example.data.repository.MediaRepository
import com.example.data.scanner.MediaScanner
import com.example.download.DownloadRepository
import com.example.player.PlayerController
import com.example.share.LtShareManager

class LTPlayerContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: LTPlayerDatabase = LTPlayerDatabase.getInstance(appContext)
    val settingsDataStore = SettingsDataStore(appContext)
    val mediaScanner = MediaScanner(appContext)
    val mediaRepository = MediaRepository(appContext, database, mediaScanner)
    val playerController = PlayerController(appContext, mediaRepository)
    val downloadRepository = DownloadRepository(appContext, database.downloadDao())
    val browserRepository = BrowserRepository(database.browserBookmarkDao(), database.browserHistoryDao())
    val editorProjectDao = database.editorProjectDao()
    val billingRepository = BillingRepository(appContext)
    val aiVideoRepository = AiVideoRepository()
    val ltShareManager = LtShareManager(appContext, database.shareHistoryDao())
}
