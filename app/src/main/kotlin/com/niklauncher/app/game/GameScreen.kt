package com.niklauncher.app.game

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.niklauncher.app.input.ControlOverlay
import com.niklauncher.core.control.ControlButton
import com.niklauncher.core.control.ControlJoystick
import com.niklauncher.core.control.ControlLayout
import kotlinx.coroutines.flow.StateFlow

/**
 * What the player sees during a session: the game's own surface, and - until it
 * is drawing - whatever the launcher knows about why it is not.
 *
 * The surface is created in every state rather than only once the session is
 * ready, because the session cannot start without it: waiting for "ready"
 * before offering a surface would deadlock the two against each other.
 */
@Composable
fun GameScreen(
    state: StateFlow<GameState>,
    controls: StateFlow<ControlLayout>,
    inMenu: StateFlow<Boolean>,
    onSurface: (SurfaceView) -> Unit,
    onButtonPressed: (ControlButton) -> Unit,
    onButtonReleased: (ControlButton) -> Unit,
    onJoystickMoved: (ControlJoystick, Float, Float) -> Unit,
    onJoystickReleased: (ControlJoystick) -> Unit,
    onExit: () -> Unit,
) {
    val current by state.collectAsState()
    val layout by controls.collectAsState()
    val menu by inMenu.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context -> SurfaceView(context).also(onSurface) },
            modifier = Modifier.fillMaxSize(),
        )

        when (val value = current) {
            is GameState.Preparing -> Message("Preparando la sesión…")

            is GameState.Ready -> Message(value.summary + "\n\nEsperando a la superficie…")

            // Drawn only while the game is running: a control that reaches the
            // bridge before the window exists would be queued against nothing.
            is GameState.Running -> ControlOverlay(
                layout = layout,
                inMenu = menu,
                onButtonPressed = onButtonPressed,
                onButtonReleased = onButtonReleased,
                onJoystickMoved = onJoystickMoved,
                onJoystickReleased = onJoystickReleased,
            )

            is GameState.Failed -> Failure(value.reason, onExit)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun Failure(reason: String, onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "No se pudo iniciar",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = reason,
                color = Color(0xFFFFB4AB),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onExit) { Text("Volver") }
        }
    }
}
