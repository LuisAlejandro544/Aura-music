package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AuraMusicAppContent
import com.example.ui.theme.AuraMusicTheme
import com.example.viewmodel.MusicViewModel

/**
 * Actividad Principal de Aura Music.
 * Arquitectura Modular MVVM:
 * - Configura Edge-to-Edge y fijación de escala tipográfica (fontScale = 1.0f).
 * - Despacha eventos físicos de botones de auriculares a [MusicViewModel.headphoneController].
 * - Procesa intents del sistema ("Abrir con...", "Compartir con...", notificaciones de descargas).
 * - Delega la composición y navegación de la interfaz de usuario en [AuraMusicAppContent].
 */
class MainActivity : ComponentActivity() {

    private var musicViewModel: MusicViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MusicViewModel = viewModel()
            musicViewModel = viewModel

            // Procesar Intent de inicio ("Abrir con...", "Compartir con..." o toque en notificación de descarga)
            LaunchedEffect(intent) {
                val downloadedTrackId = intent?.getLongExtra(
                    com.example.playback.AuraDownloadService.EXTRA_PLAY_DOWNLOADED_TRACK_ID,
                    -1L
                ) ?: -1L
                if (downloadedTrackId > 0L) {
                    intent?.removeExtra(com.example.playback.AuraDownloadService.EXTRA_PLAY_DOWNLOADED_TRACK_ID)
                    viewModel.playDownloadedTrackFromNotification(downloadedTrackId)
                } else {
                    viewModel.onIncomingIntent(intent)
                }
            }

            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            val currentDensity = LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 1.0f
                )
            ) {
                AuraMusicTheme(auraTheme = currentTheme) {
                    AuraMusicApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AuraApplication.isAppInForeground = true
    }

    override fun onStop() {
        AuraApplication.isAppInForeground = false
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val downloadedTrackId = intent.getLongExtra(
            com.example.playback.AuraDownloadService.EXTRA_PLAY_DOWNLOADED_TRACK_ID,
            -1L
        )
        if (downloadedTrackId > 0L) {
            intent.removeExtra(com.example.playback.AuraDownloadService.EXTRA_PLAY_DOWNLOADED_TRACK_ID)
            musicViewModel?.playDownloadedTrackFromNotification(downloadedTrackId)
        } else {
            musicViewModel?.onIncomingIntent(intent)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        musicViewModel?.let { vm ->
            if (vm.headphoneController.onKeyEvent(event.keyCode, event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun AuraMusicApp(viewModel: MusicViewModel) {
    val context = LocalContext.current

    // Solicitud del permiso de notificaciones para Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Notificaciones concedidas o denegadas */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    AuraMusicAppContent(viewModel = viewModel)
}
