package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.PlayerRoute
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.theme.AuraTvTheme
import com.example.ui.theme.BgPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0F1117")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0F1117"))
        )
        setContent {
            AuraTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgPrimary
                ) {
                    AuraNavHost()
                }
            }
        }
    }
}

@Composable
private fun AuraNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onChannelSelected = { channelId, playlistId, categoryKey ->
                    navController.navigate(
                        PlayerRoute(
                            channelId = channelId,
                            playlistId = playlistId,
                            categoryKey = categoryKey
                        )
                    )
                }
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlayerRoute>()
            PlayerScreen(
                channelId = route.channelId,
                playlistId = route.playlistId,
                categoryKey = route.categoryKey,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
