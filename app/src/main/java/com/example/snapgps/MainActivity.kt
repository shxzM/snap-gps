package com.example.snapgps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.snapgps.domain.model.ThemeMode
import com.example.snapgps.domain.repository.SettingsRepository
import com.example.snapgps.presentation.navigation.AppNavHost
import com.example.snapgps.presentation.theme.SnapGpsTheme
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeFlow = settingsRepository.settings.map { it.themeMode }
        setContent {
            val themeMode by themeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SnapGpsTheme(themeMode = themeMode) {
                AppNavHost()
            }
        }
    }
}
