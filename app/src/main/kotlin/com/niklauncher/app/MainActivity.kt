package com.niklauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niklauncher.app.ui.LauncherViewModel
import com.niklauncher.app.ui.NikLauncherApp
import com.niklauncher.app.ui.theme.NikLauncherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as NikLauncherApplication).container

        setContent {
            NikLauncherTheme {
                val viewModel: LauncherViewModel = viewModel(
                    factory = LauncherViewModel.Factory(container),
                )
                val settings by viewModel.settings.collectAsState()
                keepScreenOn(settings.keepScreenOn)
                NikLauncherApp(viewModel)
            }
        }
    }

    private fun keepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
