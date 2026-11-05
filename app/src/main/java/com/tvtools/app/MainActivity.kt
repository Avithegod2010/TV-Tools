package com.tvtools.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tvtools.app.ui.common.LocalHapticsEnabled
import com.tvtools.app.ui.device.DeviceScreen
import com.tvtools.app.ui.files.FileManagerScreen
import com.tvtools.app.ui.apps.BackupsScreen
import com.tvtools.app.ui.home.HomeScreen
import com.tvtools.app.ui.labs.AutomationsScreen
import com.tvtools.app.ui.labs.CastScreen
import com.tvtools.app.ui.labs.HealthScreen
import com.tvtools.app.ui.labs.IrScreen
import com.tvtools.app.ui.labs.KidsScreen
import com.tvtools.app.ui.labs.LaunchpadScreen
import com.tvtools.app.ui.labs.LiveTvScreen
import com.tvtools.app.ui.labs.MouseKeyboardScreen
import com.tvtools.app.ui.labs.SftpScreen
import com.tvtools.app.ui.labs.SpeedTestScreen
import com.tvtools.app.ui.labs.UsersScreen
import com.tvtools.app.ui.mirror.MirrorScreen
import com.tvtools.app.ui.music.MusicScreen
import com.tvtools.app.ui.onboarding.OnboardingScreen
import com.tvtools.app.ui.share.ShareToTvDialog
import com.tvtools.app.ui.storage.StorageScreen
import com.tvtools.app.ui.theme.TvToolsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppServices.init(applicationContext)
        handleShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings by AppServices.settings.state.collectAsState()
            TvToolsTheme(settings = settings) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (settings.onboardingDone) {
                            AppNav()
                        } else {
                            OnboardingScreen(onDone = { AppServices.settings.setOnboardingDone() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val extra: Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        // Some apps put the file in the intent data instead of EXTRA_STREAM.
        val uri = extra ?: intent.data
        if (uri != null) {
            AppServices.pendingShare.value = uri to intent.type
        }
    }

}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val pendingShare by AppServices.pendingShare.collectAsState()

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }
        },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = {
            fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 10 }
        },
    ) {
        composable("home") {
            HomeScreen(
                onConnected = { device ->
                    nav.navigate("device/${Uri.encode(device.name)}")
                },
            )
        }
        composable(
            route = "device/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "Device"
            DeviceScreen(
                deviceName = name,
                onBack = { nav.popBackStack("home", inclusive = false) },
                onOpenFiles = { nav.navigate("files") },
                onOpenMirror = { nav.navigate("mirror/${Uri.encode(name)}") },
                onOpenBackups = { nav.navigate("backups") },
                onOpenStorage = { nav.navigate("storage") },
                onOpenMusic = { nav.navigate("music") },
                onOpenLab = { route -> nav.navigate(route) },
            )
        }
        composable("files") {
            FileManagerScreen(onBack = { nav.popBackStack() })
        }
        composable("backups") {
            BackupsScreen(onBack = { nav.popBackStack() })
        }
        composable("storage") {
            StorageScreen(onBack = { nav.popBackStack() })
        }
        composable("music") {
            MusicScreen(onBack = { nav.popBackStack() })
        }
        composable("cast") { CastScreen(onBack = { nav.popBackStack() }) }
        composable("speed") { SpeedTestScreen(onBack = { nav.popBackStack() }) }
        composable("livetv") { LiveTvScreen(onBack = { nav.popBackStack() }) }
        composable("mouse") { MouseKeyboardScreen(onBack = { nav.popBackStack() }) }
        composable("health") { HealthScreen(onBack = { nav.popBackStack() }) }
        composable("users") { UsersScreen(onBack = { nav.popBackStack() }) }
        composable("sftp") { SftpScreen(onBack = { nav.popBackStack() }) }
        composable("ir") { IrScreen(onBack = { nav.popBackStack() }) }
        composable("kids") { KidsScreen(onBack = { nav.popBackStack() }) }
        composable("launchpad") { LaunchpadScreen(onBack = { nav.popBackStack() }) }
        composable("automations") { AutomationsScreen(onBack = { nav.popBackStack() }) }
        composable(
            route = "mirror/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { entry ->
            MirrorScreen(
                deviceName = entry.arguments?.getString("name") ?: "TV",
                onBack = { nav.popBackStack() },
            )
        }
    }

    pendingShare?.let { (uri, mime) ->
        ShareToTvDialog(
            uri = uri,
            mime = mime,
            onDismiss = { AppServices.pendingShare.value = null },
            onOpenDevices = {
                AppServices.pendingShare.value = null
                nav.navigate("home") { popUpTo("home") { inclusive = true } }
            },
        )
    }
}
