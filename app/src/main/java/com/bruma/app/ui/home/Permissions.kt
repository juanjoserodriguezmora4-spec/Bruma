package com.bruma.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bruma.app.ui.theme.T

private fun granted(
    context: Context,
    permission: String,
) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Pide los permisos justo antes de la primera descarga, con una explicación previa.
 * Devuelve una función: gate { acción } ejecuta la acción cuando ya se respondió.
 */
@Composable
fun rememberPermissionGate(
    notifAsked: Boolean,
    onNotifAsked: () -> Unit,
): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRationale by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            pending?.invoke()
            pending = null
        }

    fun needed(includeNotif: Boolean): Array<String> {
        val list = mutableListOf<String>()
        if (includeNotif &&
            Build.VERSION.SDK_INT >= 33 &&
            !notifAsked &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT <= 28 && !granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            list += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return list.toTypedArray()
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("¿Te avisamos cuando termine?", style = T.h2) },
            text = {
                Text(
                    "Bruma quiere mostrarte el progreso y avisarte cuando tus descargas terminen. " +
                        "Si dices que no, igual se descargan, solo que no verás el progreso en la barra de notificaciones.",
                    style = T.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    onNotifAsked()
                    launcher.launch(needed(true))
                }) { Text("Permitir avisos") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    onNotifAsked()
                    val rest = needed(false)
                    if (rest.isNotEmpty()) {
                        launcher.launch(rest)
                    } else {
                        pending?.invoke()
                        pending = null
                    }
                }) { Text("Ahora no") }
            },
        )
    }

    return { action ->
        val all = needed(true)
        when {
            all.isEmpty() -> action()
            Manifest.permission.POST_NOTIFICATIONS in all -> {
                pending = action
                showRationale = true
            }
            else -> {
                pending = action
                launcher.launch(all)
            }
        }
    }
}
