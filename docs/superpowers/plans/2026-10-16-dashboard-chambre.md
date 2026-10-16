# Dashboard Chambre Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android native (Compose Material 3) qui remplace Fully Kiosk + Lovelace sur la Tab S2 : panneau chambre fluide, connexion WebSocket HA, veille par caméra.

**Architecture:** Une seule app Kotlin. `ha/` (WebSocket + repository de StateFlow), `presence/` (détection de mouvement pure + machine d'états de veille), `ui/` (un fichier par panneau), `settings/` (DataStore). L'activité est le lanceur HOME et reste au premier plan.

**Tech Stack:** Kotlin 2.x, AGP 8.x, Compose BOM récent + Material 3, OkHttp 4 (+ MockWebServer), kotlinx.serialization-json, kotlinx-coroutines, CameraX 1.4, DataStore Preferences, JUnit 4, Turbine, coroutines-test.

**Spec:** `docs/superpowers/specs/2026-10-16-dashboard-chambre-design.md`

## Global Constraints

- `minSdk 31`, `targetSdk 35`, `compileSdk 35`, package `fr.alex.chambre`.
- JDK 21 pour Gradle (pas le JDK 25 système).
- Thème sombre uniquement, graine `#FFB86B` ; primary `#FFB86B`, onPrimary `#4A2800`, background `#19120C`, surfaceContainer `#261E17`, surfaceContainerHigh `#3A3028`, tertiary `#C5CA8F`.
- Entités codées en dur (spec §3). URL HA par défaut `http://homeassistant.local:8123`.
- Débit des sliders : max 1 commande / 150 ms, valeur finale toujours envoyée ; état HA reprend la main 1 s après relâchement.
- Veille : défaut 60 s ; seuil pixel |Δ| > 25 ; sensibilité défaut 0,02 ; analyse 40×30, ~3 fps.
- Reconnexion : 1, 2, 4, 8, 16, 30 s (plafond).
- Textes UI en français ; date au format « Jeudi 16 octobre » (fr-FR, majuscule initiale).
- Les actions sur le vrai HA (lumière/volet) pendant les tests émulateur sont annoncées à l'utilisateur avant.

## Review Focus

1. Lumière éteinte : `brightness` et `color_temp_kelvin` absents → slider luminosité à 0, slider température affiché sans valeur, pas de crash.
2. Lumière en mode `hs` : `color_temp_kelvin` null → slider température sans curseur, pastilles OK.
3. HA redémarre en cours de session → reconnexion puis **réabonnement complet** (entités + forecast) et état rafraîchi.
4. Volet en mouvement / `current_position` absent → slider affiché sans valeur, boutons actifs.
5. Jeton collé avec espaces / retour ligne → `trim()` avant envoi.

Chaque point a son test dans la tâche propriétaire (marqué *RF*).

---

### Task 0: Outillage (JDK 21, SDK Android, AVD)

**Files:** aucun dans le repo.

- [ ] **Step 1:** Installer Temurin JDK 21 (winget `EclipseAdoptium.Temurin.21.JDK`) ; noter son chemin pour `org.gradle.java.home`.
- [ ] **Step 2:** Télécharger les cmdline-tools dans `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`, puis `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "emulator" "system-images;android-31;google_apis;x86_64"` et accepter les licences.
- [ ] **Step 3:** `avdmanager create avd -n tabS2 -k "system-images;android-31;google_apis;x86_64"` puis dans `config.ini` : `hw.lcd.width=2048`, `hw.lcd.height=1536`, `hw.lcd.density=320`, `hw.camera.front=webcam0`, `hw.keyboard=yes`, `hw.ramSize=3072`.
- [ ] **Step 4:** Démarrer l'émulateur ; vérifier `adb shell getprop ro.build.version.sdk` → `31`.

### Task 1: Squelette Gradle + thème + activité kiosque

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, wrapper Gradle, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`, `MainActivity.kt`, `ui/theme/Theme.kt`, `res/values/strings.xml`, `res/values/themes.xml`

**Interfaces:**
- Produces: `@Composable fun ChambreTheme(content: @Composable () -> Unit)` ; `object Amber` (constantes de couleur ci-dessus).

- [ ] **Step 1:** Projet Gradle Kotlin DSL avec version catalog ; plugins android-application, kotlin-android, kotlin-compose, kotlin-serialization ; `gradle.properties` avec `org.gradle.java.home` = JDK 21.
- [ ] **Step 2:** Manifest : permissions `INTERNET`, `CAMERA` ; `usesCleartextTraffic=true` ; activité `exported`, `screenOrientation="sensorLandscape"`, intent-filters `MAIN/LAUNCHER` et `MAIN/HOME/DEFAULT`.
- [ ] **Step 3:** `MainActivity` : edge-to-edge, barres système masquées en immersif (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), `FLAG_KEEP_SCREEN_ON`, back ignoré via `OnBackPressedCallback`, affiche `ChambreTheme { Text("Chambre") }`.
- [ ] **Step 4:** `ChambreTheme` = `MaterialTheme(darkColorScheme(...))` avec les couleurs des Global Constraints, les autres rôles dérivés (onSurface `#F0DFD3`, onSurfaceVariant `#D6C3B5`, outline `#52443A`, primaryContainer `#6A3B00`, onPrimaryContainer `#FFDCBE`).
- [ ] **Step 5:** `gradlew assembleDebug` → BUILD SUCCESSFUL ; `adb install` + lancement sur l'AVD → texte visible plein écran (capture).
- [ ] **Step 6:** Commit `feat: squelette app kiosque + thème ambre`.

### Task 2: Throttle

**Files:** Create `util/Throttle.kt` ; Test `test/.../util/ThrottleTest.kt`

**Interfaces:**
- Produces: `class Throttle<T>(scope: CoroutineScope, intervalMs: Long = 150, send: suspend (T) -> Unit) { fun submit(value: T); fun flush(value: T) }` — `submit` émet immédiatement si l'intervalle est écoulé, sinon garde la dernière valeur et l'émet à la fin de l'intervalle ; `flush` émet la valeur tout de suite et annule l'attente.

- [ ] **Step 1:** Tests (runTest, temps virtuel) : `first_submit_sends_immediately` ; `submits_within_interval_send_only_last_at_interval_end` (submit 1,2,3 à t=0,10,20 → envois `[1,3]`, 3 à t=150) ; `flush_sends_now_and_cancels_pending` (submit 1, submit 2, flush 9 → `[1,9]`, rien après).
- [ ] **Step 2:** Lancer `gradlew testDebugUnitTest --tests *ThrottleTest*` → FAIL.
- [ ] **Step 3:** Implémenter.
- [ ] **Step 4:** Tests → PASS. Commit `feat: throttle des commandes`.

### Task 3: MotionDetector

**Files:** Create `presence/MotionDetector.kt` ; Test `MotionDetectorTest.kt`

**Interfaces:**
- Produces: `object MotionDetector { const val W = 40; const val H = 30; fun downsample(y: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray /* W*H moyenne par bloc */ ; fun score(prev: ByteArray, cur: ByteArray, pixelThreshold: Int = 25): Float /* fraction de pixels |Δ|>seuil, octets lus non signés */ }`

- [ ] **Step 1:** Tests : `identical_frames_score_zero` ; `changed_block_score_is_fraction` (120 pixels sur 1200 changés de 0→200 → 0.1f) ; `small_noise_ignored` (Δ=20 partout → 0f) ; `unsigned_bytes_handled` (0x10 vs 0xF0 compte comme changement) ; `downsample_averages_blocks` (image 160×120 moitié gauche 0, droite 255, rowStride 176 → colonnes 0..19 = 0, 20..39 = 255).
- [ ] **Step 2:** FAIL. **Step 3:** Implémenter. **Step 4:** PASS. Commit `feat: détecteur de mouvement`.

### Task 4: PresenceController

**Files:** Create `presence/PresenceController.kt` ; Test `PresenceControllerTest.kt`

**Interfaces:**
- Produces: `enum class Presence { AWAKE, ASLEEP }` ; `class PresenceController(scope: CoroutineScope, sleepDelayMs: () -> Long, now: () -> Long = SystemClock::elapsedRealtime) { val state: StateFlow<Presence>; fun onMotion(); fun onTouch(); fun start() }` — boucle qui vérifie toutes les secondes `now() - lastActivity >= sleepDelayMs()`.

- [ ] **Step 1:** Tests (temps virtuel, `now = { testScheduler.currentTime }`) : `starts_awake` ; `sleeps_after_delay_without_activity` (59 s AWAKE, 60 s ASLEEP) ; `motion_resets_timer` ; `motion_wakes_when_asleep` ; `touch_wakes_when_asleep` ; `delay_change_applies_live` (passer à 10 s → ASLEEP à 10 s).
- [ ] **Step 2–4:** FAIL → implémenter → PASS. Commit `feat: contrôleur de veille`.

### Task 5: Messages HA + modèle d'état

**Files:** Create `ha/HaMessages.kt`, `ha/EntityState.kt`, `ha/Entities.kt` ; Test `HaMessagesTest.kt`

**Interfaces:**
- Produces:
  - `data class EntityState(val entityId: String, val state: String, val attributes: JsonObject)` + extensions `brightnessPct: Int?` (brightness/255*100 arrondi), `colorTempK: Int?`, `minK: Int`(défaut 2000), `maxK: Int`(défaut 6500), `coverPosition: Int?`, `isUnavailable: Boolean` (`unavailable`/`unknown`).
  - `data class ForecastHour(val time: Instant, val condition: String, val temperature: Double)`.
  - `sealed interface Incoming { AuthRequired; AuthOk; AuthInvalid(message); Result(id, success, errorMessage?); EntitiesEvent(id, added: Map<String,EntityState>, changed: Map<String,JsonObject /*diff brut*/>, removed: List<String>); ForecastEvent(id, hours: List<ForecastHour>); Unknown }`
  - `object HaProtocol { fun parse(text: String): Incoming; fun auth(token: String): String; fun subscribeEntities(id: Int, ids: List<String>): String; fun subscribeForecast(id: Int, entityId: String): String; fun callService(id: Int, domain: String, service: String, entityId: String, data: JsonObject = JsonObject(emptyMap())): String }`
  - `fun EntityState.applyDiff(diff: JsonObject): EntityState` — format compressé : `a` = ajout complet `{s, a}`, `c` = `{"+": {s?, a?}, "-": {a: [clés]}}`.
  - `object Entities` : `LIGHT`, `COVER`, `SCENES: List<Scene(entityId, label, icon)>`, `HALF`, `WEATHER`, `ALL`, `SWATCHES: List<Swatch>` (spec §3, `sealed Swatch { Kelvin(k); Rgb(r,g,b) }`, avec `displayColor`).

- [ ] **Step 1:** Tests : parse `auth_required`/`auth_ok`/`auth_invalid` ; `result` succès et échec (`error.message`) ; événement `a` (lumière avec brightness 133 → `brightnessPct` 52) ; diff `c` qui change `s` et ajoute un attribut ; diff `-` qui retire `brightness` → `brightnessPct` null (*RF1*) ; `r` ; forecast (2 entrées) ; `auth("  tok\n")` contient `"access_token":"tok"` (*RF5*) ; `callService` produit `{"id":7,"type":"call_service","domain":"light","service":"turn_on","target":{"entity_id":"light.chambre_lumieres"},"service_data":{...}}` ; `colorTempK` null quand absent (*RF2*) ; `coverPosition` null quand absent (*RF4*).
- [ ] **Step 2–4:** FAIL → implémenter → PASS. Commit `feat: protocole HA`.

### Task 6: HaConnection + HaRepository

**Files:** Create `ha/HaConnection.kt`, `ha/HaRepository.kt` ; Test `HaRepositoryTest.kt` (MockWebServer)

**Interfaces:**
- Consumes: Task 5.
- Produces:
  - `enum class ConnState { CONNECTING, CONNECTED, OFFLINE, AUTH_FAILED }`
  - `class HaRepository(scope: CoroutineScope, client: OkHttpClient, config: Flow<HaConfig /*(url, token)*/>, backoffMs: List<Long> = listOf(1000,2000,4000,8000,16000,30000)) { val conn: StateFlow<ConnState>; val entities: StateFlow<Map<String, EntityState>>; val forecast: StateFlow<List<ForecastHour>>; val errors: SharedFlow<String>; suspend fun call(domain: String, service: String, entityId: String, data: JsonObject = …): Boolean }`
  - URL ws dérivée : `http→ws`, `https→wss`, + `/api/websocket`. Ids de message croissants, remis à 1 à chaque connexion. Après `auth_ok` : `subscribeEntities(Entities.ALL)` puis `subscribeForecast(WEATHER)`. `call` attend le `Result` correspondant (timeout 10 s) ; si échec → émet dans `errors`.
  - Changement de config → fermeture et reconnexion.

- [ ] **Step 1:** Tests avec MockWebServer (`withWebSocketUpgrade`) : `auth_then_subscribes` (le serveur reçoit auth avec token, puis subscribe_entities id 1 avec les entités, puis forecast id 2) ; `entities_flow_updates_from_events` ; `call_returns_true_on_success` / `false_and_emits_error_on_failure` ; `auth_invalid_sets_AUTH_FAILED_without_retry` ; `reconnects_and_resubscribes_after_server_close` (*RF3* : backoff court injecté `listOf(10,10)`, 2ᵉ connexion reçoit à nouveau auth + subscribe, état remis à jour).
- [ ] **Step 2–4:** FAIL → implémenter → PASS. Commit `feat: connexion et repository HA`.

### Task 7: Réglages

**Files:** Create `settings/SettingsStore.kt`, `settings/SettingsScreen.kt` ; Test `SettingsStoreTest.kt`

**Interfaces:**
- Produces: `data class Settings(val url: String = "http://homeassistant.local:8123", val token: String = "", val sleepDelaySec: Int = 60, val sensitivity: Float = 0.02f)` ; `class SettingsStore(dataStore: DataStore<Preferences>) { val settings: Flow<Settings>; suspend fun save(s: Settings) }` (token et url `trim()` à la sauvegarde) ; `@Composable fun SettingsScreen(initial: Settings, conn: ConnState, onSave: (Settings) -> Unit, onClose: () -> Unit)` — champs URL, jeton (masqué), délai (s), sensibilité (slider 0.005–0.1), boutons Enregistrer/Fermer ; message « Jeton refusé » si `AUTH_FAILED`.

- [ ] **Step 1:** Test (DataStore sur fichier temp) : défauts ; aller-retour ; trim de token/url.
- [ ] **Step 2–4:** FAIL → implémenter → PASS. Commit `feat: réglages`.

### Task 8: ViewModel + bandeau + scènes

**Files:** Create `ui/DashboardViewModel.kt`, `ui/DashboardScreen.kt`, `ui/HeaderBar.kt`, `ui/ScenesPanel.kt`, `ui/WeatherIcons.kt` ; Modify `MainActivity.kt` ; Test `DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 4–7.
- Produces: `class DashboardViewModel(app) : AndroidViewModel` exposant `entities`, `forecast`, `conn`, `errors`, `settings`, `presence: PresenceController`, `lastScene: StateFlow<String?>`, et actions `runScene(id)`, `toggleLight()`, `setBrightness(pct, final: Boolean)`, `setKelvin(k, final)`, `setHs(h, s, final)`, `applySwatch(sw)`, `coverOpen/Stop/Close/Half()`, `setCoverPosition(pos)`, `saveSettings(s)`. Les actions `final=false` passent par un `Throttle` par type ; `final=true` → `flush`. Les actions lumière autres que `runScene` effacent `lastScene` ; une transition de la lumière vers `off` aussi.
- `fun formatDate(date: LocalDate): String` (« Jeudi 16 octobre »).
- `@Composable fun DashboardScreen(vm)` : `Column { HeaderBar; Row(weights 1.1/1.4/1) { ScenesPanel; LightPanel; CoverPanel } }`, padding 24 dp, espacement 16 dp, sections `RoundedCornerShape(28.dp)` sur `surfaceContainer`. `SettingsScreen` affiché si token vide, AUTH_FAILED, ou appui long 3 s sur l'heure. Snackbar sur `errors`.
- Météo : mapping condition HA (`clear-night`, `sunny`, `partlycloudy`, `cloudy`, `rainy`, `pouring`, `snowy`, `fog`, `windy`, `lightning`, `lightning-rainy`, `snowy-rainy`, `hail`, `exceptional`) → icônes Material Symbols ; température actuelle = attribut `temperature` de `weather.forecast_maison` ; 4 premières heures futures du forecast.
- Scènes : tuiles pleine hauteur (weight 1 chacune), forme 28 dp → 40 dp + couleur primary si `lastScene`, animation de pulsation (`infiniteTransition`) tant que l'état du script est `on`.

- [ ] **Step 1:** Tests VM (repository factice via interface `HaApi { conn; entities; forecast; errors; suspend fun call(...) }` — extraire cette interface de `HaRepository`) : `formatDate` → « Jeudi 16 octobre » pour 2026-10-16 ; `runScene` appelle `script.turn_on` et fixe `lastScene` ; `setBrightness(0, final=true)` appelle `light.turn_off` ; `setBrightness(40,true)` → `turn_on {brightness_pct:40}` ; lumière passant à `off` efface `lastScene`.
- [ ] **Step 2–4:** FAIL → implémenter → PASS.
- [ ] **Step 5:** Brancher dans `MainActivity` ; build + install sur l'AVD ; saisir URL/jeton (demander le jeton à l'utilisateur) ; capture : bandeau avec heure/météo réelles, scènes. Commit `feat: bandeau, scènes, viewmodel`.

### Task 9: Lumière + sélecteur de couleur

**Files:** Create `ui/components/VerticalSlider.kt`, `ui/LightPanel.kt`, `ui/ColorSheet.kt`

**Interfaces:**
- Produces: `@Composable fun VerticalSlider(value: Float? /*0..1, null = pas de curseur*/, onChange: (Float) -> Unit, onRelease: (Float) -> Unit, modifier, fillFromTop: Boolean = false, track: Brush? = null, fillColor: Color, enabled: Boolean, label: String, icon: ImageVector, onIconClick: (() -> Unit)? = null)` — valeur locale pendant le geste (pointerInput drag + tap), largeur pleine, coins 32 dp ; reprend `value` externe 1 s après relâchement.
- `LightPanel` : sliders luminosité (fill primary, label « 55 % ») et température (track dégradé `#FFB46B`→`#FFF3E0`→`#DCEBFF` de bas en haut, minK..maxK) ; rangée de 9 pastilles 40 dp + pastille arc-en-ciel (sweepGradient) ouvrant `ColorSheet`. Grisé si lumière indisponible/déconnecté.
- `ColorSheet` : `ModalBottomSheet` ; roue de teintes (Canvas, sweepGradient, anneau) avec curseur draggable → hue ; slider horizontal saturation ; centre = couleur courante ; `vm.setHs(h, s, final)`.

- [ ] **Step 1:** Implémenter ; build + install.
- [ ] **Step 2:** Prévenir l'utilisateur, puis sur l'AVD : glisser luminosité, toucher une pastille, ouvrir la roue → captures + vérifier l'état de `light.chambre_lumieres` dans HA. Commit `feat: panneau lumière et roue de couleurs`.

### Task 10: Volet

**Files:** Create `ui/CoverPanel.kt`

- [ ] **Step 1:** `CoverPanel` : `VerticalSlider(fillFromTop = true, value = (100 - pos)/100f, fillColor = tertiary)` ; relâchement → `setCoverPosition(100 - round(v*100))` ; colonne de 4 `FilledTonalIconButton` ↑ ■ ↓ ½ (icônes `arrow_upward`, `stop`, `arrow_downward`, `vertical_align_center`) ; libellé « 43 % ouvert ».
- [ ] **Step 2:** Prévenir l'utilisateur ; tester ■ et un petit déplacement sur l'AVD ; capture. Commit `feat: panneau volet`.

### Task 11: Caméra + veille

**Files:** Create `presence/CameraMotionSource.kt`, `ui/SleepOverlay.kt` ; Modify `MainActivity.kt`

**Interfaces:**
- Consumes: Tasks 3–4.
- Produces: `class CameraMotionSource(context, lifecycleOwner, sensitivity: () -> Float, onMotion: () -> Unit) { fun start(); fun stop() }` — CameraX `ImageAnalysis` front, `ResolutionSelector` cible 160×120, `STRATEGY_KEEP_ONLY_LATEST`, ignore les frames < 333 ms après la précédente, `MotionDetector.downsample` puis `score > sensitivity()` → `onMotion()`.
- `SleepOverlay(asleep: Boolean, onWakeTouch)` : `Box` noir plein écran (fondu 600 ms) qui consomme tous les pointeurs quand ASLEEP et appelle `presence.onTouch()`.
- `MainActivity` : demande `CAMERA` au démarrage ; si refusé, pas de caméra ; tous les touchers (via `dispatchTouchEvent`) appellent `presence.onTouch()` ; collecte `presence.state` → `window.attributes.screenBrightness = 0.01f` / `BRIGHTNESS_OVERRIDE_NONE`.

- [ ] **Step 1:** Implémenter ; build + install ; délai de veille mis à 10 s dans les réglages.
- [ ] **Step 2:** Vérifier sur l'AVD : écran noir après 10 s sans mouvement devant la webcam ; mouvement → réveil ; toucher sur écran en veille ne déclenche aucun contrôle. Captures. Commit `feat: veille par caméra`.

### Task 12: Build release + livraison

**Files:** Modify `app/build.gradle.kts` (signingConfig depuis `keystore.properties` non versionné, `isMinifyEnabled = true`, `isShrinkResources = true`), `app/proguard-rules.pro` (règles kotlinx.serialization), `.gitignore` (`*.jks`, `keystore.properties`, `build/`, `.gradle/`, `local.properties`) ; Create `README.md` (installation tablette).

- [ ] **Step 1:** Générer une clé locale (`keytool`), `gradlew assembleRelease` → APK signé.
- [ ] **Step 2:** Installer l'APK release sur l'AVD, refaire un parcours rapide (connexion, un slider, veille) → captures.
- [ ] **Step 3:** README : `adb connect <ip>:5555` / `adb install -r app-release.apk`, choisir « Chambre » comme lanceur, désactiver Fully Kiosk, créer le jeton HA, appui long 3 s sur l'heure pour les réglages.
- [ ] **Step 4:** `gradlew testDebugUnitTest` → tout vert. Commit `chore: build release + README`.
