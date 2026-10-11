package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import coil.imageLoader
import com.example.data.importer.FFmpegNativeEngine
import com.example.data.importer.YtDlpAutoUpdater
import com.example.data.importer.YtDlpNativeEngine
import com.example.data.updater.AppReleaseUpdater
import com.example.debug.AuraDebugManager
import com.example.ui.theme.ArtworkColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Clase Application principal de Aura Music.
 * Inicializa el sistema de telemetría y monitor de depuración global [AuraDebugManager],
 * prepara de forma asíncrona en segundo plano los motores nativos puros de FFmpeg y Python yt-dlp,
 * supervisa el ciclo de vida de primer/segundo plano ([isAppInForegroundFlow]) para activar el
 * ahorro inteligente de recursos mientras el usuario juega, y captura eventos de memoria del sistema.
 */
class AuraApplication : Application() {

    companion object {
        private val _isAppInForegroundFlow = MutableStateFlow(false)
        val isAppInForegroundFlow: StateFlow<Boolean> = _isAppInForegroundFlow.asStateFlow()

        @Volatile
        var isBackgroundGameModeEnabled: Boolean = true

        var isAppInForeground: Boolean
            get() = _isAppInForegroundFlow.value
            set(value) {
                _isAppInForegroundFlow.value = value
            }

        /**
         * Indica si la aplicación debe suspender procesos visuales/UI que el usuario no está viendo
         * porque se encuentra en segundo plano con el Modo Juego / Ahorro activo.
         */
        fun shouldSuspendBackgroundVisuals(): Boolean {
            return !_isAppInForegroundFlow.value && isBackgroundGameModeEnabled
        }

        /**
         * Mitiga de raíz la fuga de memoria reportada en Android 14 (ROMs TECNO HiOS, Xiaomi/MIUI/HyperOS, vivo, ColorOS):
         * - `ResourcesImpl.mAppContext -> ContextImpl.mOuterContext -> Service`
         * - `ResourcesImpl.mAppContext -> ContextImpl.mServiceCache[] -> VirtualDeviceManager.mContext -> AuraDownloadService`
         *
         * En Android 14 (API 34), cuando un Service invoca `startForeground()` o consulta servicios del sistema,
         * `SystemServiceRegistry` instancia `android.companion.virtual.VirtualDeviceManager` dentro del arreglo
         * `ContextImpl.mServiceCache` del `ContextImpl` asociado al `Service`, guardando una referencia fuerte
         * `VirtualDeviceManager.mContext = AuraDownloadService`. Si `ResourcesImpl.mAppContext` retiene ese
         * `ContextImpl`, el `Service` queda atrapado en memoria tras `onDestroy()`.
         *
         * Esta función:
         * 1. Redirige `ContextImpl.mOuterContext` hacia `applicationContext`.
         * 2. Recorre `ContextImpl.mServiceCache` (`Object[]`), busca cualquier servicio cacheado (como `VirtualDeviceManager`)
         *    cuyo campo `mContext` / `mOuterContext` apunte al `Service` destruido, lo reasigna a `applicationContext`
         *    y vacía por completo el arreglo `mServiceCache` del `ContextImpl` saliente.
         * 3. Limpia `ResourcesImpl.mAppContext` si la ROM permite el acceso reflexivo.
         */
        fun detachServiceFromContextImplAndOemResources(service: android.app.Service) {
            val appCtx = service.applicationContext ?: return
            // 1. Precalentar / anclar recursos globales sobre el ApplicationContext
            try {
                appCtx.resources
            } catch (_: Throwable) {}

            // Función auxiliar para limpiar referencias a `service` dentro de cualquier wrapper del sistema (ej. VirtualDeviceManager)
            fun scrubCachedSystemServiceObject(cachedObj: Any?) {
                if (cachedObj == null) return
                var clazz: Class<*>? = cachedObj.javaClass
                var depth = 0
                while (clazz != null && clazz != Any::class.java && depth < 4) {
                    for (f in clazz.declaredFields) {
                        if (f.name == "mContext" || f.name == "mOuterContext" || f.type == android.content.Context::class.java) {
                            try {
                                f.isAccessible = true
                                val currentRef = f.get(cachedObj)
                                if (currentRef === service || currentRef === service.baseContext) {
                                    f.set(cachedObj, appCtx)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                    clazz = clazz.superclass
                    depth++
                }
            }

            // 2. Desenvolver cualquier ContextWrapper hasta alcanzar android.app.ContextImpl
            //    redirigir mOuterContext -> applicationContext y vaciar mServiceCache[]
            try {
                var currentCtx: android.content.Context? = service.baseContext
                var depth = 0
                while (currentCtx != null && depth < 10) {
                    if (currentCtx.javaClass.name == "android.app.ContextImpl") {
                        // A. Intentar primero el método interno setOuterContext(Context)
                        var updated = false
                        try {
                            val setOuterMethod = currentCtx.javaClass.getDeclaredMethod(
                                "setOuterContext",
                                android.content.Context::class.java
                            )
                            setOuterMethod.isAccessible = true
                            setOuterMethod.invoke(currentCtx, appCtx)
                            updated = true
                        } catch (_: Throwable) {}

                        // Respaldo por reflexión directa sobre el campo mOuterContext
                        if (!updated) {
                            try {
                                val outerField = currentCtx.javaClass.getDeclaredField("mOuterContext")
                                outerField.isAccessible = true
                                val currentOuter = outerField.get(currentCtx)
                                if (currentOuter === service) {
                                    outerField.set(currentCtx, appCtx)
                                }
                            } catch (_: Throwable) {}
                        }

                        // B. Limpiar ContextImpl.mServiceCache (incluyendo VirtualDeviceManager.mContext -> AuraDownloadService)
                        try {
                            val cacheField = currentCtx.javaClass.getDeclaredField("mServiceCache")
                            cacheField.isAccessible = true
                            val serviceCache = cacheField.get(currentCtx) as? Array<*>
                            if (serviceCache != null) {
                                @Suppress("UNCHECKED_CAST")
                                val mutableCache = serviceCache as? Array<Any?>
                                for (i in serviceCache.indices) {
                                    val cachedService = serviceCache[i]
                                    if (cachedService != null) {
                                        scrubCachedSystemServiceObject(cachedService)
                                        mutableCache?.set(i, null)
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                        break
                    }
                    currentCtx = if (currentCtx is android.content.ContextWrapper) {
                        currentCtx.baseContext
                    } else {
                        null
                    }
                    depth++
                }
            } catch (_: Throwable) {}

            // 3. Limpiar también ResourcesImpl.mAppContext si la ROM permite el acceso reflexivo
            try {
                val resourcesImplClass = Class.forName("android.content.res.ResourcesImpl")
                for (field in resourcesImplClass.declaredFields) {
                    if (field.name == "mAppContext") {
                        field.isAccessible = true
                        val currentVal = field.get(null)
                        if (currentVal === service.baseContext || currentVal === service) {
                            val appBase = (appCtx as? android.content.ContextWrapper)?.baseContext ?: appCtx
                            field.set(null, appBase)
                        }
                        break
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Anclar desde el primer milisegundo ResourcesImpl.mAppContext al contexto de la aplicación
        // para evitar que el primer acceso a recursos ocurra dentro de un Service efímero.
        try {
            applicationContext.resources
            resources
        } catch (_: Throwable) {}

        val uiPrefs = getSharedPreferences("aura_music_ui_prefs", MODE_PRIVATE)
        isBackgroundGameModeEnabled = uiPrefs.getBoolean("pref_background_game_mode_enabled", true)

        // Inicializar el monitor y el capturador de excepciones no controladas (Crashes)
        AuraDebugManager.init(this)
        AuraDebugManager.logInfo("Application", "Aura Music iniciada correctamente.")

        // Inicialización asíncrona en segundo plano de los motores nativos (FFmpeg puro y entorno Python) y limpieza de residuos
        applicationScope.launch {
            try {
                com.example.data.storage.AppStorageManager.cleanupResidualFiles(applicationContext)
                com.example.data.importer.WebViewCookieBridge.syncWebViewCookiesToNetscapeFile(applicationContext)
                FFmpegNativeEngine.init(applicationContext)
                YtDlpNativeEngine.init(applicationContext)
                // Chequeo en caliente OTA de yt-dlp sin bloquear la interfaz
                YtDlpAutoUpdater.checkAndUpdate(applicationContext, forceDownload = false)
                // Búsqueda silenciosa de nueva versión APK en GitHub Releases (Pre-Releases -beta)
                AppReleaseUpdater.checkForUpdates(applicationContext, manualCheck = false)
            } catch (e: Exception) {
                AuraDebugManager.logWarning("Application", "Aviso en inicialización de motores nativos: ${e.message}")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ArtworkColorExtractor.clearCache()
        try {
            imageLoader.memoryCache?.clear()
        } catch (_: Throwable) {}
        AuraDebugManager.logWarning(
            "Memory",
            "Dispositivo en estado crítico de memoria baja (onLowMemory). Se liberaron cachés de color y metadatos."
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                ArtworkColorExtractor.clearCache()
                try {
                    imageLoader.memoryCache?.clear()
                } catch (_: Throwable) {}
                AuraDebugManager.logWarning(
                    "Memory",
                    "Memoria del sistema bajo presión crítica (TRIM_MEMORY level: $level). Cachés liberadas."
                )
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                ArtworkColorExtractor.clearCache()
                try {
                    imageLoader.memoryCache?.clear()
                } catch (_: Throwable) {}
                AuraDebugManager.logWarning(
                    "Memory",
                    "Memoria del sistema bajo presión alta (TRIM_MEMORY level: $level). Cachés liberadas."
                )
            }
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                if (isBackgroundGameModeEnabled) {
                    ArtworkColorExtractor.trimForBackgroundGaming()
                    try {
                        imageLoader.memoryCache?.clear()
                    } catch (_: Throwable) {}
                } else {
                    ArtworkColorExtractor.releaseRetriever()
                }
                AuraDebugManager.logInfo(
                    "Memory",
                    "Interfaz oculta en segundo plano (TRIM_MEMORY_UI_HIDDEN level: 20). Recursos gráficos no usados liberados para optimizar juegos y multitarea."
                )
            }
        }
    }
}
