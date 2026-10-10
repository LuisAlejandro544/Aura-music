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

    /**
     * Procesa de forma segura el Intent entrante verificando su acción y limpiando extras tras consumirlos.
     */
    private fun handleSafeIncomingIntent(incomingIntent: android.content.Intent?, vm: MusicViewModel) {
        if (incomingIntent == null) return
        try {
            val incomingAuthToken = incomingIntent.getStringExtra(
                com.example.widget.AuraMusicWidgetProvider.EXTRA_INTERNAL_AUTH_TOKEN
            )
            val isInternalAuthenticated = incomingAuthToken == com.example.widget.AuraMusicWidgetProvider.INTERNAL_IPC_AUTH_TOKEN

            val widgetCommand = incomingIntent.getStringExtra(
                com.example.widget.AuraMusicWidgetProvider.EXTRA_WIDGET_COMMAND
            )
            if (!widgetCommand.isNullOrBlank()) {
                incomingIntent.removeExtra(com.example.widget.AuraMusicWidgetProvider.EXTRA_WIDGET_COMMAND)
                incomingIntent.removeExtra(com.example.widget.AuraMusicWidgetProvider.EXTRA_INTERNAL_AUTH_TOKEN)
                if (isInternalAuthenticated) {
                    when (widgetCommand) {
                        com.example.widget.AuraMusicWidgetProvider.ACTION_WIDGET_PLAY_PAUSE -> vm.togglePlayPause()
                        com.example.widget.AuraMusicWidgetProvider.ACTION_WIDGET_NEXT -> vm.playNext()
                        com.example.widget.AuraMusicWidgetProvider.ACTION_WIDGET_PREV -> vm.playPrevious()
                    }
                }
                return
            }

            val downloadedTrackId = incomingIntent.getLongExtra(
                com.example.playback.AuraDownloadService.EXTRA_PLAY_DOWNLOADED_TRACK_ID,
                -1L
            )
            if (downloadedTrackId > 0L) {
                incomingIntent.removeExtra(com.example.playback.AuraDownloadService.EXTRA_PLAY_DOWNLOADED_TRACK_ID)
                incomingIntent.removeExtra(com.example.widget.AuraMusicWidgetProvider.EXTRA_INTERNAL_AUTH_TOKEN)
                if (isInternalAuthenticated) {
                    vm.playDownloadedTrackFromNotification(downloadedTrackId)
                }
                return
            }

            val allowedActions = setOf(
                android.content.Intent.ACTION_VIEW,
                android.content.Intent.ACTION_SEND,
                android.content.Intent.ACTION_SEND_MULTIPLE
            )
            if (incomingIntent.action in allowedActions) {
                vm.onIncomingIntent(incomingIntent)
            }
        } catch (_: Exception) {
            // Ignorar parcelas malformadas o excepciones de deserialización de Intents externos
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MusicViewModel = viewModel()
            musicViewModel = viewModel

            // Procesar Intent de inicio ("Abrir con...", "Compartir con..." o toque en notificación de descarga)
            LaunchedEffect(intent) {
                handleSafeIncomingIntent(intent, viewModel)
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
        val pkgState = com.example.data.importer.YtDlpAutoUpdater.packageUpdateState.value
        if (pkgState is com.example.model.PackageUpdateState.Checking ||
            pkgState is com.example.model.PackageUpdateState.UpToDate ||
            pkgState is com.example.model.PackageUpdateState.Idle
        ) {
            com.example.playback.AuraDownloadService.dismissPackageNotification(applicationContext)
        }
        super.onStop()
    }

    override fun onDestroy() {
        musicViewModel = null
        super.onDestroy()
        try {
            val resourcesImplClass = Class.forName("android.content.res.ResourcesImpl")
            for (field in resourcesImplClass.declaredFields) {
                if (field.name == "mAppContext") {
                    field.isAccessible = true
                    val currentVal = field.get(null)
                    if (currentVal === baseContext || currentVal === this) {
                        field.set(null, applicationContext)
                    }
                    break
                }
            }
        } catch (_: Throwable) {}
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        musicViewModel?.let { vm ->
            handleSafeIncomingIntent(intent, vm)
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
