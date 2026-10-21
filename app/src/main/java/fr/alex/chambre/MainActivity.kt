package fr.alex.chambre

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.presence.CameraMotionSource
import fr.alex.chambre.ui.cuisine.CuisineApp
import fr.alex.chambre.presence.Presence
import fr.alex.chambre.presence.TouchGate
import fr.alex.chambre.settings.Settings
import fr.alex.chambre.settings.FormFactor
import fr.alex.chambre.ui.phone.EXTRA_TAB
import fr.alex.chambre.ui.phone.PhoneApp
import fr.alex.chambre.ui.phone.tabFromExtra
import fr.alex.chambre.ui.salon.SalonTab
import fr.alex.chambre.settings.SettingsScreen
import fr.alex.chambre.ui.DashboardScreen
import fr.alex.chambre.ui.salon.SalonApp
import fr.alex.chambre.settings.Room
import fr.alex.chambre.ui.DashboardViewModel
import fr.alex.chambre.ui.SleepOverlay
import fr.alex.chambre.ui.theme.ChambreTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: DashboardViewModel by viewModels()
    private val phone get() = vm.formFactor == FormFactor.PHONE
    /** Phone: the room a widget asked for (see EXTRA_TAB). */
    private val startTab = mutableStateOf(SalonTab.SALON)
    private var camera: CameraMotionSource? = null
    private val touchGate by lazy {
        TouchGate(isAsleep = { vm.presence.state.value == Presence.ASLEEP }, onTouch = { vm.presence.onTouch() })
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark: light status / navigation bar icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        // Wall tablets in landscape; the phone layout is portrait.
        requestedOrientation = if (phone) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        startTab.value = tabFromExtra(intent.getStringExtra(EXTRA_TAB))
        if (phone) {
            // A normal phone app; the screen only stays on while a recipe is open, as on the tablet.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    vm.cuisine.opened.map { it != null }.distinctUntilChanged().collect { cooking ->
                        if (cooking) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemBars()
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            })

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previous: Presence? = null
                vm.presence.state.collect {
                    applyBrightness(it)
                    if (previous != null && previous != it) camera?.onScreenTransition()
                    previous = it
                }
            }
        }

        setContent { ChambreTheme { App(vm, phone, startTab.value) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startTab.value = tabFromExtra(intent.getStringExtra(EXTRA_TAB))
    }

    private fun startCamera() {
        if (camera != null) return
        camera = CameraMotionSource(
            context = this,
            lifecycleOwner = this,
            minArea = { vm.settings.value?.sensitivity ?: Settings().sensitivity },
            previewEnabled = { vm.settingsOpen.value },
            onMotion = { vm.presence.onMotion() },
        ).also { cam ->
            cam.start()
            lifecycleScope.launch { cam.preview.collect { vm.cameraPreview.value = it } }
            // Nothing is opened before the saved choice is known (CameraX can jam some old HALs).
            lifecycleScope.launch { vm.settings.filterNotNull().map { it.cameraApi }.distinctUntilChanged().collect(cam::setApi) }
        }
    }

    private fun applyBrightness(p: Presence) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (p == Presence.ASLEEP) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchGate.Action.DOWN
            MotionEvent.ACTION_UP -> TouchGate.Action.UP
            MotionEvent.ACTION_CANCEL -> TouchGate.Action.CANCEL
            else -> TouchGate.Action.MOVE
        }
        if (touchGate.shouldConsume(action)) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        camera?.stop()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !phone) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun App(vm: DashboardViewModel, phone: Boolean, startTab: SalonTab) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val conn by vm.ha.conn.collectAsStateWithLifecycle()
    val presence by vm.presence.state.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    val s = settings ?: return

    val showSettings = settingsOpen || s.token.isBlank() || conn == ConnState.AUTH_FAILED
    DisposableEffect(showSettings) {
        vm.settingsOpen.value = showSettings
        vm.presence.hold = showSettings
        onDispose { }
    }

    Box {
        if (showSettings) {
            val battery by vm.battery.state.collectAsStateWithLifecycle()
            val report by vm.batteryReporter.status.collectAsStateWithLifecycle()
            val preview by vm.cameraPreview.collectAsStateWithLifecycle()
            val calendarError by vm.announcements.error.collectAsStateWithLifecycle()
            val calendars by vm.deviceCalendars.collectAsStateWithLifecycle()
            val mealieError by vm.cuisine.error.collectAsStateWithLifecycle()
            val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCalendars() }
            LaunchedEffect(Unit) { vm.refreshCalendars() }
            SettingsScreen(
                initial = s,
                phone = phone,
                conn = conn,
                battery = battery,
                report = report,
                preview = preview,
                onSave = {
                    vm.saveSettings(it)
                    settingsOpen = false
                },
                onClose = { settingsOpen = false },
                calendarError = calendarError,
                onTestVoice = vm::testVoice,
                mealieError = mealieError,
                calendars = calendars,
                onAllowCalendars = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
            )
        } else {
            if (phone) {
                PhoneApp(vm, startTab, onOpenSettings = { settingsOpen = true })
            } else when (s.room) {
                Room.SALON -> SalonApp(vm, s, onOpenSettings = { settingsOpen = true })
                Room.CUISINE -> CuisineApp(vm, onOpenSettings = { settingsOpen = true })
                Room.CHAMBRE -> DashboardScreen(vm, onOpenSettings = { settingsOpen = true })
            }
        }
        SleepOverlay(asleep = presence == Presence.ASLEEP)
    }
}
