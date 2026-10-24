package com.bruma.app

import android.app.Application
import android.content.Context
import com.bruma.app.data.SettingsRepository
import com.bruma.app.data.db.BrumaDatabase
import com.bruma.app.download.DownloadRepository
import com.bruma.app.download.EngineUpdateWorker
import com.bruma.app.download.Notifications
import com.bruma.app.engine.Engine
import com.bruma.app.library.LibraryRepository
import com.bruma.app.player.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BrumaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Preparar el motor en segundo plano para que la primera descarga arranque rápido.
        container.appScope.launch(Dispatchers.IO) {
            runCatching { Engine.ensureInit(this@BrumaApp) }
        }
        EngineUpdateWorker.schedule(this)
    }
}

/** Contenedor sencillo de dependencias (en lugar de Hilt, para mantener el proyecto liviano). */
class AppContainer(
    app: Application,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = BrumaDatabase.build(app)
    val settings = SettingsRepository(app)
    val library = LibraryRepository(app, db)
    val downloads = DownloadRepository(app, db, settings)
    val player = PlayerConnection(app, db, appScope)
}

val Context.container: AppContainer
    get() = (applicationContext as BrumaApp).container
