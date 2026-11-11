package fr.alex.chambre.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import fr.alex.chambre.announce.AnnouncementController
import fr.alex.chambre.announce.AndroidCalendarSource
import fr.alex.chambre.announce.AnnouncementStore
import fr.alex.chambre.announce.DeviceCalendar
import fr.alex.chambre.announce.TtsSpeaker
import fr.alex.chambre.battery.BatteryMonitor
import fr.alex.chambre.battery.BatteryReporter
import fr.alex.chambre.ha.DeviceInfo
import fr.alex.chambre.ha.MobileAppClient
import fr.alex.chambre.settings.dataStore
import fr.alex.chambre.settings.FormFactor
import fr.alex.chambre.settings.deviceFeatures
import fr.alex.chambre.settings.formFactorFor
import fr.alex.chambre.util.activeWithGrace
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.HaConfig
import fr.alex.chambre.ha.TodoList
import fr.alex.chambre.settings.Room
import fr.alex.chambre.ha.HaRepository
import fr.alex.chambre.presence.PresenceController
import fr.alex.chambre.settings.Settings
import fr.alex.chambre.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import fr.alex.chambre.mealie.CuisineController
import fr.alex.chambre.mealie.MealieClient
import fr.alex.chambre.timers.AlarmPlayer
import fr.alex.chambre.timers.SharedTimer
import fr.alex.chambre.timers.TimerController
import fr.alex.chambre.timers.TimerStore
import java.util.concurrent.TimeUnit


/** Wires settings, the Home Assistant connection, the controller and presence together. */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app.dataStore)

    /** null until the stored settings are loaded. */
    val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Read once: a phone stays a phone (split screen or rotation never turns tablet features on). */
    val formFactor: FormFactor = formFactorFor(app.resources.configuration.smallestScreenWidthDp)

    private val haConfig = settings.filterNotNull()
        .map { s -> deviceFeatures(s, formFactor).let { f -> HaConfig(s.url, s.token, f.entities, timers = f.timers, timersList = s.timersList) } }
        .distinctUntilChanged()

    private val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()

    /** Phone only: set by MainActivity when the app comes on / leaves the screen. Tablets stay true. */
    val foreground = MutableStateFlow(true)

    /** Phone in the background for 30 s: no HA connection, no Mealie polling (the widgets have their own access). */
    private val active = activeWithGrace(foreground, graceMs = 30_000).stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val ha = HaRepository(viewModelScope, client, combine(haConfig, active) { cfg, on -> cfg.takeIf { on } })

    val controller = DashboardController(viewModelScope, ha)

    /** Living-room mode: the salon and kitchen panels drive their own entities. */
    val salonController = DashboardController(viewModelScope, ha, Entities.SALON_ROOM)
    val kitchenController = DashboardController(viewModelScope, ha, Entities.KITCHEN_ROOM)

    /** Kitchen-tablet mode: Mealie recipes (read only). */
    val cuisine = CuisineController(
        viewModelScope,
        settings.filterNotNull(),
        apiFor = { s -> if (s.mealieUrl.isBlank() || s.mealieToken.isBlank()) null else MealieClient(s.mealieUrl, s.mealieToken, client) },
        enabled = { deviceFeatures(it, formFactor).mealie },
        active = active,
    )

    val presence = PresenceController(
        viewModelScope,
        sleepDelayMs = { (settings.value?.sleepDelaySec ?: Settings().sleepDelaySec) * 1_000L },
    ).also { if (formFactor == FormFactor.TABLET) it.start() }

    /** Shared kitchen timers (Home Assistant to-do list), on every wall tablet. */
    val timers = TimerController(
        viewModelScope,
        ha,
        TimerStore(app.dataStore),
        room = { settings.value?.room ?: Room.CHAMBRE },
        list = { settings.value?.timersList ?: Entities.TIMERS_LIST },
    )

    /** The timers ringing on this tablet: drives the ringing overlay, the alarm sound and the wake-up. */
    val ringingShown: StateFlow<List<SharedTimer>> = combine(timers.ringing, settings) { ringing, s ->
        fr.alex.chambre.timers.ringingShown(ringing, enabled = s?.timerRing, tablet = formFactor == FormFactor.TABLET)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val alarmPlayer = AlarmPlayer(app)

    init {
        viewModelScope.launch {
            ringingShown.map { it.isNotEmpty() }.distinctUntilChanged().collect { ringing ->
                presence.alarm = ringing
                if (ringing) alarmPlayer.start() else alarmPlayer.stop()
            }
        }
    }

    val battery = BatteryMonitor(app).also { it.start() }

    val batteryReporter = BatteryReporter(
        viewModelScope,
        battery.state,
        // The phone never registers with the Mobile App integration.
        haConfig.filter { formFactor == FormFactor.TABLET },
        MobileAppClient(client),
        store,
        deviceInfo = { id ->
            DeviceInfo(
                deviceId = id,
                appVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                osVersion = Build.VERSION.RELEASE,
                deviceName = when (settings.value?.room) {
                    Room.SALON -> "Tablette salon"
                    Room.CUISINE -> "Tablette cuisine"
                    else -> MobileAppClient.DEVICE_NAME
                },
            )
        },
    )

    private val speaker = TtsSpeaker(app)

    private val calendarSource = AndroidCalendarSource(app)

    /** Living-room announcements (does nothing in bedroom mode). */
    val announcements = AnnouncementController(
        viewModelScope,
        calendarSource,
        settings.filterNotNull(),
        AnnouncementStore(app.dataStore),
        speaker,
        wake = { presence.onMotion() },
        enabled = { deviceFeatures(it, formFactor).announcements },
    )

    /** Test button in the settings. */
    private val _deviceCalendars = MutableStateFlow<List<DeviceCalendar>?>(emptyList())
    /** The tablet's calendars for the settings pickers; null without the calendar permission. */
    val deviceCalendars: StateFlow<List<DeviceCalendar>?> = _deviceCalendars.asStateFlow()

    fun refreshCalendars() {
        viewModelScope.launch { _deviceCalendars.value = withContext(Dispatchers.IO) { calendarSource.calendars() } }
        announcements.refreshNow()
    }

    private val _todoLists = MutableStateFlow<List<TodoList>?>(null)
    /** Home Assistant's to-do lists for the « Liste des minuteries » picker; null when HA cannot be reached. */
    val todoLists: StateFlow<List<TodoList>?> = _todoLists.asStateFlow()

    fun refreshTodoLists() {
        viewModelScope.launch { _todoLists.value = ha.todoLists() }
    }

    fun testVoice() = speaker.say("Ceci est un test de la tablette du salon.")

    override fun onCleared() {
        speaker.shutdown()
        alarmPlayer.stop()
        battery.stop()
    }

    /** True while the settings screen is shown (enables the camera preview). */
    val settingsOpen = MutableStateFlow(false)

    /** Annotated camera frames, published only while the settings screen is open. */
    val cameraPreview = MutableStateFlow<Bitmap?>(null)

    fun saveSettings(s: Settings) {
        viewModelScope.launch {
            store.save(s)
            ha.reconnect()
        }
    }
}
