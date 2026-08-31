package com.niklauncher.app.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.niklauncher.app.NikLauncherApplication
import com.niklauncher.app.runtime.GlfwBridge
import com.niklauncher.app.runtime.JvmBridge
import kotlinx.coroutines.launch

/**
 * Runs one Minecraft session.
 *
 * In its own process, for two reasons the Invocation API forces on us: a VM
 * cannot be torn down and rebuilt, so a second session needs a fresh process,
 * and a crash inside the game would otherwise take the launcher with it.
 *
 * The order here is not incidental. The GLFW bridge has to know its Surface
 * and its EGL library *before* the VM starts, because Minecraft creates its
 * window early in startup and the bridge binds its EGL on the first call.
 */
class GameActivity : ComponentActivity() {

    private val session by lazy {
        val application = application as NikLauncherApplication
        GameSession(
            container = application.container,
            instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID).orEmpty(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The game owns the whole screen, and a session that dims out halfway
        // through a cave is worse than a flat battery.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            GameScreen(
                state = session.state,
                onSurface = ::attachSurface,
                onExit = { finish() },
            )
        }

        lifecycleScope.launch { session.prepare() }
    }

    /**
     * Handed to the bridge as soon as Android has one, and taken away again on
     * destruction: an ANativeWindow that outlives its Surface is a crash inside
     * eglSwapBuffers, not an error we would get to report.
     */
    private fun attachSurface(view: SurfaceView) {
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                GlfwBridge.attachSurface(holder.surface)
                lifecycleScope.launch { session.start() }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                GlfwBridge.setWindowSize(width, height)
                session.onSurfaceSize(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                GlfwBridge.attachSurface(null)
            }
        })
    }

    override fun onPause() {
        super.onPause()
        // Releasing what is held stops the game from walking into a wall while
        // the player is in another app.
        GlfwBridge.setFocused(false)
    }

    override fun onResume() {
        super.onResume()
        if (JvmBridge.isRunning) GlfwBridge.setFocused(true)
    }

    companion object {
        private const val EXTRA_INSTANCE_ID = "com.niklauncher.app.INSTANCE_ID"

        fun intent(context: Context, instanceId: String): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_INSTANCE_ID, instanceId)
    }
}
