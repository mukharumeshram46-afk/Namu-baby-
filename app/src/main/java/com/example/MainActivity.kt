package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.HealthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoriesScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.theme.MyApplicationTheme

enum class ScreenDestination {
    HOME,
    BROWSER,
    MEMORIES,
    PERMISSIONS,
    HEALTH
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyraaApplication

        setContent {
            MyApplicationTheme(darkTheme = true) {
                var currentScreen by remember { mutableStateOf(ScreenDestination.HOME) }

                // Runtime Permissions Requester
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    // Handled gracefully in UI flows
                }

                LaunchedEffect(Unit) {
                    val permissionsNeeded = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }

                    if (permissionsNeeded.isNotEmpty()) {
                        permissionsLauncher.launch(permissionsNeeded.toTypedArray())
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        ScreenDestination.HOME -> {
                            HomeScreen(
                                app = app,
                                onNavigateToBrowser = { currentScreen = ScreenDestination.BROWSER },
                                onNavigateToMemories = { currentScreen = ScreenDestination.MEMORIES },
                                onNavigateToPermissions = { currentScreen = ScreenDestination.PERMISSIONS },
                                onNavigateToHealth = { currentScreen = ScreenDestination.HEALTH },
                                onRequestMicrophonePermission = {
                                    permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                }
                            )
                        }
                        ScreenDestination.BROWSER -> {
                            BrowserScreen(
                                app = app,
                                onBack = { currentScreen = ScreenDestination.HOME }
                            )
                        }
                        ScreenDestination.MEMORIES -> {
                            MemoriesScreen(
                                app = app,
                                onBack = { currentScreen = ScreenDestination.HOME }
                            )
                        }
                        ScreenDestination.PERMISSIONS -> {
                            PermissionsScreen(
                                onBack = { currentScreen = ScreenDestination.HOME }
                            )
                        }
                        ScreenDestination.HEALTH -> {
                            HealthScreen(
                                app = app,
                                onBack = { currentScreen = ScreenDestination.HOME }
                            )
                        }
                    }
                }
            }
        }
    }
}
