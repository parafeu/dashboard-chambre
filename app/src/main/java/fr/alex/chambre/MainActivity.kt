package fr.alex.chambre

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.settings.SettingsScreen
import fr.alex.chambre.ui.DashboardScreen
import fr.alex.chambre.ui.DashboardViewModel
import fr.alex.chambre.ui.theme.ChambreTheme

class MainActivity : ComponentActivity() {
    private val vm: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent { ChambreTheme { App(vm) } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun App(vm: DashboardViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val conn by vm.ha.conn.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    val s = settings ?: return

    if (settingsOpen || s.token.isBlank() || conn == ConnState.AUTH_FAILED) {
        SettingsScreen(
            initial = s,
            conn = conn,
            onSave = {
                vm.saveSettings(it)
                settingsOpen = false
            },
            onClose = { settingsOpen = false },
        )
    } else {
        DashboardScreen(vm, onOpenSettings = { settingsOpen = true })
    }
}
