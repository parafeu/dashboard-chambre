# Foyer — phone layout, widgets, name and icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the app « Foyer » with its new icon, give it a phone layout that stacks the tablet panels, make every room tab show that room's own dashboard, and add three home-screen widgets.

**Architecture:** One APK. A pure `deviceFeatures(settings, formFactor)` decides what runs (tablet vs phone); the ViewModel and MainActivity obey it. The phone UI (`ui/phone/`) only arranges existing panels (`LightPanel`, `CoverPanel`, `ScenesPanel`, the kitchen panels). Widgets use Jetpack Glance and talk to Home Assistant over its REST API with the saved URL + token, independent of the app's WebSocket.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (BOM 2025.01), Material 3, Glance AppWidget 1.1.1, OkHttp 4.12, kotlinx.serialization, JUnit 4 + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-21-foyer-mobile-widgets-design.md` (mockups in `docs/superpowers/mockups/2026-10-21-foyer/`).

## Global Constraints

- Copy, never reinvent: phone screens use the tablet's composables unchanged except for size/arrangement parameters. No new sliders, arrows or buttons.
- Displayed name « Foyer »; `applicationId` stays `fr.alex.chambre`; HA device names and entity ids unchanged.
- `minSdk = 28` stays; everything builds and installs on API 28. Release ABIs: `armeabi-v7a` + `arm64-v8a`.
- Phone = `smallestScreenWidthDp < 600`. On a phone: no agenda, camera, announcements, auto-return, battery reporting, boot start, immersive mode, FLAG_KEEP_SCREEN_ON (except while a recipe is open), blocked back button.
- Icons: Material `Icons.Filled.*` only. UI strings in French.
- Network: one HA URL, one Mealie URL, home Wi-Fi only.
- Never tap blindly on an emulator connected to the real HA; UI checks use an unreachable HA URL (e.g. `http://10.0.2.2:1`) unless a real-device test is announced.

## Review Focus

- HA unreachable / token wrong when a widget is tapped → the widget keeps its last state, dimmed, no crash, no toast loop (Task 6 test `failed refresh keeps last states and marks stale`).
- Widget tapped before the app was ever configured (blank URL/token) → widget shows « Ouvrir Foyer pour configurer » and the tap opens the app (Task 6 test `blank settings give the unconfigured model`).
- Light off and « − » pressed on a widget → light turns on at 10 %, not off and not an error (Task 5 test `minus on an off light turns it on at the minimum`).
- Shutter reporting no `current_position` (state only) → tile text and tap action still correct from `state` (Task 5 tests `cover without position uses its state`).
- Phone rotated or split-screen to ≥ 600 dp → it must not suddenly start the camera/announcements: the form factor is read once per process from the device's smallest width, not the window (Task 2 test `phone stays phone whatever the room`).

---

### Task 1: Name « Foyer », adaptive icon, 64-bit release

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (`app_name` → `Foyer`)
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/drawable/ic_launcher_monochrome.xml`, `app/src/main/res/values/ic_launcher_background.xml`
- Create (pre-API-26 fallback, the tablets run API 28 so v26 applies; still provide one): `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` rendered from the foreground on the background
- Modify: `app/src/main/AndroidManifest.xml` (`android:icon="@mipmap/ic_launcher"`, `android:roundIcon="@mipmap/ic_launcher"` if absent)
- Modify: `app/build.gradle.kts` release `ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }`
- Modify: any user-visible « Chambre » that names the app (settings title, README title) — not room names.

- [ ] **Step 1:** Vector foreground on the 108 dp canvas, glyph inside the central 66 dp: solid house `#FFB86B` (path from `icone.html` I2: `M54 26 L82 50 V82 H26 V50 Z`, rounded joins) with the flame cut out filled `#4A2800`; background colour `#261E17`. Monochrome = the same house with the flame as a hole (single path, `fillType="evenOdd"`), any opaque colour.
- [ ] **Step 2:** `adaptive-icon` with `<background>`, `<foreground>`, `<monochrome>`.
- [ ] **Step 3:** Build: `./gradlew assembleDebug assembleRelease` → BUILD SUCCESSFUL; `unzip -l app/build/outputs/apk/release/*.apk | grep lib/` lists `armeabi-v7a` and `arm64-v8a`.
- [ ] **Step 4:** Install debug on the tablet emulator, screenshot the launcher: label « Foyer », house-and-flame icon.
- [ ] **Step 5:** Commit `feat: rename the app Foyer with its house-and-flame icon; ship arm64 too`.

### Task 2: Device features (tablet vs phone)

**Files:**
- Create: `app/src/main/java/fr/alex/chambre/settings/DeviceFeatures.kt`
- Test: `app/src/test/java/fr/alex/chambre/settings/DeviceFeaturesTest.kt`
- Modify: `ui/DashboardViewModel.kt`, `mealie/CuisineController.kt:58`, `announce/AnnouncementController.kt:79-86`, `boot/BootReceiver.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class FormFactor { TABLET, PHONE }
  fun formFactorFor(smallestWidthDp: Int): FormFactor          // < 600 → PHONE
  data class DeviceFeatures(
      val entities: List<String>,   // HA subscription
      val timerEvents: Boolean,
      val announcements: Boolean,
      val batteryReport: Boolean,
      val camera: Boolean,
      val presence: Boolean,        // auto sleep overlay
      val mealie: Boolean,
      val bootStart: Boolean,
      val kiosk: Boolean,           // immersive, keep screen on, blocked back
  )
  fun deviceFeatures(settings: Settings, formFactor: FormFactor): DeviceFeatures
  ```
  `DashboardViewModel.formFactor: FormFactor` (from `app.resources.configuration.smallestScreenWidthDp`, read once) and `val features: StateFlow<DeviceFeatures?>`.

- [ ] **Step 1: Write the failing tests**
  - `formFactorFor(599) == PHONE`, `formFactorFor(600) == TABLET`.
  - `tablet keeps today's behaviour per room`: CHAMBRE → entities `entitiesFor(CHAMBRE)`, announcements false, camera true, presence true, batteryReport true, mealie false, kiosk true, bootStart == settings.autoStart; SALON → entities `entitiesFor(SALON)`, timerEvents true, announcements true, **mealie true**; CUISINE → mealie true, announcements false.
  - `phone stays phone whatever the room`: for each `Room`, PHONE → entities `entitiesFor(SALON)`, timerEvents false, announcements false, batteryReport false, camera false, presence false, mealie true, bootStart false (even with `autoStart = true`), kiosk false.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*DeviceFeaturesTest*'` → FAIL (unresolved).
- [ ] **Step 3:** Implement `DeviceFeatures.kt`.
- [ ] **Step 4:** Wire it: VM builds `HaConfig` from `features.entities/timerEvents`; `presence.start()` only if `features.presence`; `BatteryReporter` config flow filtered to `batteryReport`; `AnnouncementController` and `CuisineController` gate on a `Boolean` flow (`announcements` / `mealie`) instead of `room == SALON` / `room == CUISINE` (change their constructor to take `enabled: Flow<Boolean>` or keep `settings` and add the flow — keep their existing tests green, update them for the new parameter); `BootReceiver` uses `deviceFeatures(settings, formFactorFor(res.configuration.smallestScreenWidthDp)).bootStart`.
- [ ] **Step 5:** `./gradlew testDebugUnitTest` → all PASS.
- [ ] **Step 6:** Commit `feat: decide tablet and phone features in one place`.

### Task 3: Salon tablet « Cuisine » tab = kitchen dashboard

**Files:**
- Modify: `ui/cuisine/CuisineApp.kt` — extract `@Composable fun CuisinePanels(vm: DashboardViewModel, modifier: Modifier = Modifier)` (the `Row { CoverPanel; RecipesPanel }` body) and `@Composable fun CuisineRecipeOverlay(vm: DashboardViewModel, snackbar: SnackbarHostState): Boolean` (shows `RecipeScreen` full screen with `BackHandler` when a recipe is open, returns whether it did). `CuisineApp` uses both; behaviour unchanged on the kitchen tablet.
- Modify: `ui/salon/SalonApp.kt` — `SalonTab.CUISINE -> CuisinePanels(vm, Modifier.weight(1f))`; when `CuisineRecipeOverlay` is showing it covers the whole salon screen; the 2-min return to Salon does not fire while a recipe is open; the screen stays awake while a recipe is open (same `presence.hold` as `CuisineApp`, without breaking the announcement hold — hold = recipe open || announcement shown).
- Delete: `ui/salon/CuisineScreen.kt`, `app/src/test/java/fr/alex/chambre/ui/salon/TimerFormatTest.kt` (only tested `timerRemaining`, now unused). Keep `kitchenSummary` and `entitiesFor(SALON)` unchanged (rail summary and announcements still use the timers).
- Modify: `settings/SettingsScreen.kt:178` — Mealie fields shown when `room == CUISINE || room == SALON`.

- [ ] **Step 1:** Add to `CuisineControllerTest` (or `DeviceFeaturesTest` if gating moved there): salon settings with a Mealie URL + key load recipes (fake `MealieApi` called once).
- [ ] **Step 2:** Run → FAIL before Task 2's gating is used by the controller, PASS after; run full unit tests → PASS.
- [ ] **Step 3:** Refactor composables as above; `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Emulator (tablet AVD `tabS2a11`, offline HA URL, a reachable Mealie if available else check the « Chargement / erreur » message): salon mode → Cuisine tab shows volet + recettes exactly like cuisine mode; open a recipe → full screen, back returns to the Cuisine tab; wait 2 min with the recipe open → stays.
- [ ] **Step 5:** Commit `feat(salon): the Cuisine tab is the kitchen dashboard`.

### Task 4: Phone layout

**Files:**
- Create: `ui/phone/PhoneApp.kt` — `@Composable fun PhoneApp(vm: DashboardViewModel, startTab: SalonTab, onOpenSettings: () -> Unit)`
- Create: `ui/phone/FloatingRoomBar.kt` — `@Composable fun FloatingRoomBar(selected: SalonTab, onSelect: (SalonTab) -> Unit, modifier: Modifier = Modifier)`
- Modify: `ui/HeaderBar.kt` — make `WeatherBlock` `internal` (no other change).
- Modify: `ui/cuisine/CuisineApp.kt` — `RecipesPanel(..., columns: Int = 3, lazy: Boolean = true)`; phone passes `columns = 2, lazy = false` (plain rows of cards inside the outer scroll; search box full width when `columns == 2`).
- Modify: `ui/cuisine/RecipeScreen.kt` — `RecipeScreen(..., stacked: Boolean = false)`: when true, ingredients panel above steps panel in one scrolling column (non-lazy lists), same components.
- Modify: `settings/SettingsScreen.kt` — `phone: Boolean = false`: single scrolling column with only « Connexion » (HA URL + token) and the Mealie fields; title « Réglages ».
- Modify: `MainActivity.kt` — read `vm.formFactor`; if PHONE: no keep-screen-on (set it only while `vm.cuisine.opened != null`), no `hideSystemBars`, no back-blocking callback, no camera permission/start, `TouchGate` not consuming; `App` shows `PhoneApp(vm, startTab = tabFromIntent(intent))`; `onNewIntent` updates the tab. Extra name: `const val EXTRA_TAB = "fr.alex.chambre.TAB"` with values `"salon" | "cuisine" | "chambre"` (in `PhoneApp.kt`, with `fun tabFromExtra(value: String?): SalonTab` defaulting to SALON).
- Test: `app/src/test/java/fr/alex/chambre/ui/phone/TabExtraTest.kt`

**Interfaces:**
- Consumes: `DashboardViewModel.formFactor` (Task 2); `CuisinePanels`/`CuisineRecipeOverlay` pieces (Task 3) — the phone uses `CoverPanel(kitchen)` + `RecipesPanel(columns = 2, lazy = false)` directly.
- Produces: `EXTRA_TAB`, `tabFromExtra()` used by Task 7.

Layout (`PhoneApp`): `Box { Column(verticalScroll, padding 12.dp, bottom padding 96.dp) { header; room heading; panels }; FloatingRoomBar(align BottomCenter, 22.dp above the gesture bar) }`.
- Header: date (« Mardi 21 octobre »), online dot + « En ligne » / « Hors ligne », settings `IconButton(Icons.Filled.Settings)`, then `WeatherBlock` (same toggle as the tablet).
- Room heading: room name; for Salon also « 21,4 °C · 52 % » from the two sensors.
- Salon: `LightPanel(salon light, colors = false, title = "Lumières", modifier = height(LIGHT_PLAIN))`, `CoverPanel(salon cover, height(COVER))`.
- Cuisine: `CoverPanel(kitchen cover, height(COVER))`, `RecipesPanel(columns = 2, lazy = false)`.
- Chambre: `LightPanel(bedroom light, colors = true, height(LIGHT_COLORS))`, `CoverPanel(height(COVER))`, `ScenesPanel(height(SCENES))`.
- Start values (tune on the Pixel-size emulator so the sliders are ≈ 300 dp / ≈ 336 dp tall): `LIGHT_COLORS = 480.dp`, `LIGHT_PLAIN = 440.dp`, `COVER = 450.dp`, `SCENES = 400.dp`.
- `FloatingRoomBar`: `Surface(shape = RoundedCornerShape(32.dp), color = surfaceContainerHigh, shadowElevation = 6.dp)`, `Row(padding 6.dp, spacedBy 4.dp)`; per room a 52 dp-high pill: icon (`Chair`, `Kitchen`, `Bed`); selected pill `primary`/`onPrimary` with the room name after the icon; unselected icon only.
- Recipe open: `RecipeScreen(stacked = true)` full screen, bar hidden, `BackHandler` closes it.

- [ ] **Step 1:** Test `tabFromExtra("cuisine") == SalonTab.CUISINE`, `tabFromExtra("chambre") == SalonTab.CHAMBRE`, `tabFromExtra(null) == SalonTab.SALON`, `tabFromExtra("x") == SalonTab.SALON`. Run → FAIL.
- [ ] **Step 2:** Implement; run → PASS; `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Create/start a phone AVD (Pixel 9 profile, API 35, `x86_64`), HA URL `http://10.0.2.2:1` with any token: screenshots of the three tabs and of the settings screen; compare with `app-mobile.html` (F2 bar): panels identical to the tablet's, bar floating, no clock/battery, status and navigation bars visible, back leaves the app.
- [ ] **Step 4:** Tablet AVD still shows the tablet UI (screenshot).
- [ ] **Step 5:** Commit `feat(phone): stacked tablet panels with a floating room bar`.

### Task 5: Widget logic (pure)

**Files:**
- Create: `app/src/main/java/fr/alex/chambre/widget/WidgetModel.kt`
- Test: `app/src/test/java/fr/alex/chambre/widget/WidgetModelTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class WidgetRoom(val tab: String, val title: String, val light: String?, val cover: String) {
      CHAMBRE("chambre", "Chambre", Entities.CHAMBRE_ROOM.light, Entities.CHAMBRE_ROOM.cover),
      SALON("salon", "Salon", Entities.SALON_ROOM.light, Entities.SALON_ROOM.cover),
      CUISINE("cuisine", "Cuisine", null, Entities.KITCHEN_COVER),
  }
  fun lightText(light: EntityState?): String            // "40 %", "Éteinte", "–" when null/unavailable
  fun lightIsOn(light: EntityState?): Boolean
  fun stepBrightness(light: EntityState?, deltaPct: Int): Int   // target brightness_pct, clamped 10..100
  fun coverText(cover: EntityState?): String            // "Ouvert", "Fermé", "Ouvert à 35 %", "Ouverture…", "Fermeture…", "–"
  fun coverIsOpen(cover: EntityState?): Boolean          // position > 0, or state "open"/"opening"
  enum class CoverTap { OPEN, CLOSE, STOP }
  fun coverTap(cover: EntityState?): CoverTap           // opening/closing → STOP; open → CLOSE; else OPEN
  fun climateText(temp: EntityState?, humidity: EntityState?): String  // "21,4 °C · 52 %", "" when both missing
  ```

- [ ] **Step 1: Failing tests** (build `EntityState` with `buildJsonObject`):
  - `lightText`: on with brightness 102 → `"40 %"`; off → `"Éteinte"`; null → `"–"`.
  - `minus on an off light turns it on at the minimum`: `stepBrightness(off, -10) == 10`; `stepBrightness(off, +10) == 10`; on 40 % `+10 → 50`, `-10 → 30`; on 15 % `-10 → 10`; on 95 % `+10 → 100`.
  - `coverText`: position 100 → `"Ouvert"`, 0 → `"Fermé"`, 35 → `"Ouvert à 35 %"`, state `opening` → `"Ouverture…"`, `closing` → `"Fermeture…"`.
  - `cover without position uses its state`: no `current_position`, state `open` → text `"Ouvert"`, tap CLOSE; state `closed` → `"Fermé"`, tap OPEN.
  - `coverTap`: opening → STOP, closing → STOP, position 50 → CLOSE, position 0 → OPEN, null → OPEN.
  - `climateText(21.4, 52)` → `"21,4 °C · 52 %"`.
- [ ] **Step 2:** Run `--tests '*WidgetModelTest*'` → FAIL.
- [ ] **Step 3:** Implement; reuse `brightnessPct`, `coverPosition`, `isUnavailable`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(widget): widget texts and actions`.

### Task 6: Home Assistant REST client and widget state

**Files:**
- Create: `app/src/main/java/fr/alex/chambre/ha/HaRestClient.kt`
- Create: `app/src/main/java/fr/alex/chambre/widget/WidgetRepository.kt`
- Test: `app/src/test/java/fr/alex/chambre/ha/HaRestClientTest.kt`, `app/src/test/java/fr/alex/chambre/widget/WidgetRepositoryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class HaRestClient(baseUrl: String, token: String, client: OkHttpClient) {
      suspend fun state(entityId: String): EntityState?          // GET /api/states/{id}; null on 404
      suspend fun call(domain: String, service: String, data: JsonObject): Boolean  // POST /api/services/{domain}/{service}
  }   // throws IOException on network failure / non-2xx other than 404
  data class WidgetSnapshot(val states: Map<String, EntityState>, val stale: Boolean, val configured: Boolean)
  class WidgetRepository(store: WidgetStore, settings: suspend () -> Settings, api: (Settings) -> HaRestClient) {
      suspend fun refresh(room: WidgetRoom): WidgetSnapshot
      suspend fun act(room: WidgetRoom, action: WidgetAction): WidgetSnapshot   // calls HA then refresh
  }
  interface WidgetStore { suspend fun load(room: WidgetRoom): Map<String, EntityState>; suspend fun save(room: WidgetRoom, states: Map<String, EntityState>) }
  sealed interface WidgetAction { data object ToggleLight; data class StepLight(val delta: Int); data object CoverTapAction; data object CoverOpen; data object CoverStop; data object CoverClose }
  ```
  Service mapping: ToggleLight → `light.toggle`; StepLight → `light.turn_on {brightness_pct: stepBrightness(...)}`; CoverTapAction → `coverTap()` → `cover.open_cover|close_cover|stop_cover`; the others direct. Entities per room: light (if any), cover, plus for SALON `Entities.SALON_TEMPERATURE`, `Entities.SALON_HUMIDITY`. URL normalisation: reuse the settings' normalised `url` (strip a trailing `/`).
  `WidgetStore` real implementation: one DataStore Preferences file `widget_states`, JSON per room (kotlinx.serialization of `{entityId: {state, attributes}}`).

- [ ] **Step 1: Failing tests**
  - HaRestClient with MockWebServer: `state()` sends `GET /api/states/light.x` with `Authorization: Bearer t` and parses `{"entity_id":"light.x","state":"on","attributes":{"brightness":102}}`; 404 → null; `call("cover","open_cover", {entity_id})` sends `POST /api/services/cover/open_cover` with that JSON body, 200 → true; 500 → throws.
  - WidgetRepository (fake store + MockWebServer): `refresh` stores and returns fresh states (`stale = false`); `failed refresh keeps last states and marks stale` (server returns 500 / connection refused → last saved states, `stale = true`); `blank settings give the unconfigured model` (`configured = false`, no request made); `act(CHAMBRE, StepLight(-10))` with the light off sends `light.turn_on` with `brightness_pct = 10`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement (OkHttp calls on `Dispatchers.IO`, 5 s timeouts). **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(widget): Home Assistant REST client and cached widget states`.

### Task 7: Glance widgets

**Files:**
- Modify: `gradle/libs.versions.toml` (`glance = "1.1.1"`, `androidx-glance-appwidget`, `androidx-glance-material3`), `app/build.gradle.kts` dependencies.
- Create: `widget/FoyerWidget.kt` — `class FoyerWidget(val room: WidgetRoom) : GlanceAppWidget()` (UI below) and three receivers `ChambreWidgetReceiver`, `SalonWidgetReceiver`, `CuisineWidgetReceiver : GlanceAppWidgetReceiver`.
- Create: `widget/WidgetActions.kt` — `ActionCallback`s for each `WidgetAction` (room passed via `ActionParameters`), each calling `WidgetRepository.act` then `FoyerWidget(room).update(...)`; after a cover action, refresh again at +3 s and +20 s.
- Create: `widget/WidgetRefreshWorker.kt` — `CoroutineWorker` refreshing all placed widgets; enqueued as unique periodic work (15 min) when a widget is first enabled (`onEnabled`), cancelled in `onDisabled` of the last one.
- Create: `res/xml/widget_chambre.xml`, `widget_salon.xml` (`targetCellWidth=4 targetCellHeight=2`, `minWidth=250dp minHeight=110dp`), `widget_cuisine.xml` (`4×1`, `minHeight=50dp`), `resizeMode="horizontal|vertical"`, `widgetCategory="home_screen"`, `previewLayout`/`description` « Lumière et volet de la chambre » etc.
- Modify: `AndroidManifest.xml` — the three receivers with `APPWIDGET_UPDATE` and their `meta-data`.
- Modify: `ui/DashboardViewModel.kt` — when the app's HA entities change, push the widget-relevant states into `WidgetStore` and call `updateAll` (throttled to once per 2 s with `util/Throttle.kt`), so widgets are live while the app runs.

UI (Ambre colours from `ui/theme/Theme.kt`, `GlanceTheme` not used): widget background `#19120C` radius 28 dp, padding 10 dp; title row: room name (13 sp, semibold, `onSurface`), for Salon `climateText` in `onSurfaceVariant`, then a 28 dp round button `Icons.Filled.OpenInNew` → `actionStartActivity<MainActivity>(EXTRA_TAB to room.tab)` (title text has the same action). Tiles: radius 22 dp, padding 12/14, `#2F2620` off, `#FFB86B` on (text `#3F2200`), 40 dp round icon (`LightMode` for light, a shutter icon — use `Icons.Filled.Blinds` if present in material-icons-extended, else `Icons.Filled.Window`), name + `lightText`/`coverText`, round 36 dp buttons at the end: light `Remove`/`Add`, cover `ArrowUpward`/`ArrowDownward` (Cuisine also `Stop` between them). Tap on tile = `ToggleLight` / `CoverTapAction`. Stale → whole content alpha 0.5 (Glance: use the dimmed colour variants). Unconfigured → single text « Ouvrir Foyer pour configurer » opening the app. Icons are rendered to bitmaps (Glance `ImageProvider` from a vector drawable copied into `res/drawable/` — `ic_w_light`, `ic_w_blinds`, `ic_w_up`, `ic_w_down`, `ic_w_stop`, `ic_w_minus`, `ic_w_plus`, `ic_w_open` — exported from the Material icons).

- [ ] **Step 1:** `./gradlew assembleDebug` → BUILD SUCCESSFUL with Glance on minSdk 28.
- [ ] **Step 2:** Phone AVD, offline HA URL: add the three widgets to one home page (via the launcher's widget picker; `adb shell` cannot place widgets, do it with `input` gestures and verify with screenshots). Expect: Chambre 4×2 and Salon 4×2 render H1/SA tiles dimmed (stale/no data) with « – »; Cuisine 4×1 fits; tapping ↗ opens Foyer on the right tab.
- [ ] **Step 3:** Tap a widget button with the offline URL → no crash (`adb logcat -d | grep -i foyer`/`AndroidRuntime` clean), widget stays dimmed.
- [ ] **Step 4:** Commit `feat(widget): Chambre, Salon and Cuisine home-screen widgets`.

### Task 8: Docs, version, final verification

**Files:** `README.md`, `app/build.gradle.kts` (`versionCode = 9`, `versionName = "1.5.0"`).

- [ ] **Step 1:** README: title « Foyer »; new « Téléphone » section (automatic phone layout, tabs, floating bar, what is not on the phone, use the HA IP address if `homeassistant.local` doesn't resolve through WireGuard); « Widgets » section (three widgets, sizes, actions, refresh, dimmed when HA unreachable); salon section: Cuisine tab = kitchen dashboard, Mealie settings now also in Salon mode; drop the old kitchen-timers/shopping-list/plug description from the Salon tab.
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug assembleRelease` → all PASS / BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit `docs: Foyer phone and widgets; version 1.5.0`.
