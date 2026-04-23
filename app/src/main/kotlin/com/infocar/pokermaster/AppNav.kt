package com.infocar.pokermaster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.infocar.pokermaster.feature.lobby.LobbyScreen
import kotlinx.coroutines.delay

private object Routes {
    const val SPLASH = "splash"
    const val LOBBY = "lobby"
    // M3+: const val TABLE = "table/{mode}"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onReady = {
                nav.navigate(Routes.LOBBY) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.LOBBY) {
            LobbyScreen(onSelectMode = { /* M3 에서 테이블로 이동 */ })
        }
    }
}

@Composable
private fun SplashScreen(onReady: () -> Unit) {
    // M0: 단순 800ms 지연. 추후 M4 에서 모델 워밍업 / RNG 자가검증 / DB 점검 추가.
    LaunchedEffect(Unit) {
        delay(800L)
        onReady()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.splash_title),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
