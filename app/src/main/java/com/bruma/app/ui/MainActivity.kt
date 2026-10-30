package com.bruma.app.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bruma.app.container
import com.bruma.app.data.AppSettings
import com.bruma.app.data.SettingsRepository
import com.bruma.app.download.Notifications
import com.bruma.app.ui.home.extractUrls
import com.bruma.app.ui.onboarding.Onboarding
import com.bruma.app.ui.theme.BrumaTheme
import com.bruma.app.ui.theme.LocalBruma
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<String?>(null)
    private val route = mutableStateOf<String?>(null)
    private val inPip = mutableStateOf(false)
    private var videoActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val settingsState = container.settings.flow.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            val settings = settingsState.value
            val scope = rememberCoroutineScope()
            BrumaTheme(settings?.theme ?: "SYSTEM", settings?.reduceTransparency ?: false, settings?.palette ?: "BRUMA") {
                val c = LocalBruma.current
                when {
                    settings == null -> Box(Modifier.fillMaxSize().background(c.bg))
                    !settings.onboardingDone ->
                        Onboarding(onDone = {
                            scope.launch { container.settings.set(SettingsRepository.ONBOARDING_DONE, true) }
                        })
                    else -> {
                        val sharedUrl by incoming
                        val startRoute by route
                        val pip by inPip
                        BrumaRoot(
                            incoming = sharedUrl,
                            onIncomingConsumed = { incoming.value = null },
                            startRoute = startRoute,
                            onRouteConsumed = { route.value = null },
                            inPip = pip,
                            onVideoActive = { videoActive = it },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val url = extractUrls(text).firstOrNull()
            if (url != null) incoming.value = url
        }
        intent.getStringExtra(Notifications.EXTRA_ROUTE)?.let { route.value = it }
    }

    /** Al salir de la app con un vídeo sonando, pasar a imagen en imagen. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val playing = container.player.state.value.isPlaying
        if (videoActive && playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }
}
