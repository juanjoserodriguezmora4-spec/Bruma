package com.bruma.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bruma.app.container
import com.bruma.app.data.db.DownloadState
import com.bruma.app.data.db.TrackEntity
import com.bruma.app.data.db.st
import com.bruma.app.ui.common.BottomNav
import com.bruma.app.ui.common.LocalSnackbar
import com.bruma.app.ui.common.MiniPlayerBar
import com.bruma.app.ui.downloads.DownloadsScreen
import com.bruma.app.ui.home.HomeScreen
import com.bruma.app.ui.library.LibraryScreen
import com.bruma.app.ui.library.PlaylistScreen
import com.bruma.app.ui.player.PlayerScreen
import com.bruma.app.ui.player.VideoScreen

private val TAB_ROUTES = setOf("home", "downloads", "library", "settings")

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BrumaRoot(
    incoming: String?,
    onIncomingConsumed: () -> Unit,
    startRoute: String?,
    onRouteConsumed: () -> Unit,
    inPip: Boolean,
    onVideoActive: (Boolean) -> Unit,
) {
    val app = LocalContext.current.container
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val player by app.player.state.collectAsStateWithLifecycle()
    val downloads by app.downloads.all.collectAsState(initial = emptyList())
    val activeCount =
        downloads.count {
            it.st == DownloadState.DOWNLOADING || it.st == DownloadState.QUEUED || it.st == DownloadState.PROCESSING
        }

    LaunchedEffect(Unit) { app.player.connect() }

    LaunchedEffect(incoming) {
        if (incoming != null) nav.goTab("home")
    }
    LaunchedEffect(startRoute) {
        if (startRoute != null) {
            if (startRoute == "player") {
                nav.navigate("player") { launchSingleTop = true }
            } else if (TAB_ROUTES.contains(startRoute)) {
                nav.goTab(startRoute)
            }
            onRouteConsumed()
        }
    }

    if (inPip) {
        VideoScreen(onBack = {}, onVideoActive = onVideoActive, pip = true)
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBars = (route != null && TAB_ROUTES.contains(route)) || route?.startsWith("playlist") == true

    val openTrack: (List<TrackEntity>, Int, Boolean) -> Unit = { list, index, shuffle ->
        val t = list.getOrNull(index)
        if (t != null) {
            if (t.isVideo) {
                app.player.play(listOf(t), 0)
                nav.navigate("video") { launchSingleTop = true }
            } else {
                app.player.play(list.filter { !it.isVideo }, list.filter { !it.isVideo }.indexOf(t).coerceAtLeast(0), shuffle)
            }
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                composable("home") {
                    HomeScreen(
                        incoming = incoming,
                        onIncomingConsumed = onIncomingConsumed,
                        onOpenDownloads = { nav.goTab("downloads") },
                        onOpenTrack = { l, i -> openTrack(l, i, false) },
                    )
                }
                composable("downloads") { DownloadsScreen(onOpenTrack = { l, i -> openTrack(l, i, false) }) }
                composable("library") {
                    LibraryScreen(
                        onOpenPlaylist = { id -> nav.navigate("playlist/$id") },
                        onOpenTrack = { l, i -> openTrack(l, i, false) },
                    )
                }
                composable("settings") {
                    com.bruma.app.ui.settings
                        .SettingsScreen()
                }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    PlaylistScreen(
                        id = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                        onOpenTrack = openTrack,
                    )
                }
                composable("player") {
                    PlayerScreen(onBack = { nav.popBackStack() }, onOpenVideo = { nav.navigate("video") { launchSingleTop = true } })
                }
                composable("video") {
                    VideoScreen(onBack = { nav.popBackStack() }, onVideoActive = onVideoActive)
                }
            }

            if (showBars) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                ) {
                    if (player.currentId != null) {
                        MiniPlayerBar(player, app.player) {
                            if (player.isVideo) {
                                nav.navigate("video") { launchSingleTop = true }
                            } else {
                                nav.navigate("player") { launchSingleTop = true }
                            }
                        }
                    }
                    BottomNav(route, activeCount) { nav.goTab(it) }
                }
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (player.currentId != null) 150.dp else 90.dp),
            )
        }
    }
}
