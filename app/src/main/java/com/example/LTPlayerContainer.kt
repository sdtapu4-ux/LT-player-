package com.example

import android.content.Context
import com.example.data.database.LTPlayerDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.AiVideoRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MediaRepository
import com.example.data.scanner.MediaScanner
import com.example.download.DownloadRepository
import com.example.player.PlayerController

class LTPlayerContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: LTPlayerDatabase = LTPlayerDatabase.getInstance(appContext)
    val settingsDataStore = SettingsDataStore(appContext)
    val mediaScanner = MediaScanner(appContext)
    val mediaRepository = MediaRepository(appContext, database, mediaScanner)
    val playerController = PlayerController(appContext, mediaRepository)
    val downloadRepository = DownloadRepository(appContext, database.downloadDao())
    val billingRepository = BillingRepository(appContext)
    val aiVideoRepository = AiVideoRepository()
}
