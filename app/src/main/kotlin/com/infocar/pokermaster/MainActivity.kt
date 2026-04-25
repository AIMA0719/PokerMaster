package com.infocar.pokermaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.core.ui.theme.ThemeMode
import com.infocar.pokermaster.feature.table.a11y.A11ySettings
import com.infocar.pokermaster.feature.table.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.DEFAULT)
            val a11y by settingsRepository.a11ySettings.collectAsState(initial = A11ySettings.Default)
            // M7: largerText 시 fontScale 1.20x — 글로벌 적용 (모든 Compose Text/Material typography).
            val baseDensity = LocalDensity.current
            val density = remember(baseDensity, a11y.largerText) {
                Density(
                    density = baseDensity.density,
                    fontScale = baseDensity.fontScale * if (a11y.largerText) 1.20f else 1f,
                )
            }
            CompositionLocalProvider(LocalDensity provides density) {
                PokerMasterTheme(themeMode = themeMode) {
                    AppNav()
                }
            }
        }
    }
}
