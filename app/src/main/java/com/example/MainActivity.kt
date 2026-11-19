package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.navigation.LTAppNavigation
import com.example.ui.MainViewModel
import com.example.ui.theme.LTPlayerTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: LTPlayerContainer
    private var incomingMediaUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Initialize Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        container = LTPlayerContainer(applicationContext)
        incomingMediaUri = extractViewUri(intent)

        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.provideFactory(container)
            )
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()

            LTPlayerTheme(themeMode = settings.themeMode) {
                LTAppNavigation(
                    viewModel = mainViewModel,
                    incomingViewUri = incomingMediaUri
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractViewUri(intent)?.let { incomingMediaUri = it }
    }

    override fun onDestroy() {
        if (::container.isInitialized) {
            container.playerController.release()
            container.ltShareManager.release()
        }
        super.onDestroy()
    }

    private fun extractViewUri(intent: Intent?): Uri? {
        return if (intent?.action == Intent.ACTION_VIEW) intent.data else null
    }
}
