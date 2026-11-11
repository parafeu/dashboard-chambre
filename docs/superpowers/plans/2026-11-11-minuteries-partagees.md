# Minuteries partagées Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kitchen timers live in the app, shared by every wall tablet through a Home Assistant Local To-do list, and ring loudly on every tablet until one of them presses « Arrêter ».

**Architecture:** `HaRepository` subscribes to `todo.minuteries_foyer` items over the existing WebSocket and exposes them. A pure model (`timers/SharedTimer.kt`) turns items into timers; `TimerController` adds the clock, the offline cache, queued stops and the HA writes. Compose shows a « Minuteries » panel in `CuisinePanels`, a « Nouvelle minuterie » dialog and a full-screen ringing overlay in `App()`; `AlarmPlayer` loops the alarm ringtone while the overlay shows.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.coroutines/serialization, DataStore Preferences, OkHttp WebSocket, JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-11-minuteries-partagees-design.md` · mockup https://claude.ai/artifact/HaEvnWL72DWxx9dGBjQGxQ (screens A, Nouvelle minuterie, Sonnerie).

## Global Constraints

- HA list entity: `todo.minuteries_foyer`. Item: `summary` = name, `uid` = timer id, `description` = `{"v":1,"end":"<ISO-8601 UTC>","duration":<seconds>,"origin":"<text>","room":"cuisine|salon"}`.
- Writes: `todo.add_item` (`item`, `description`), `todo.update_item` (`item` = uid, `description`), `todo.remove_item` (`item` = uid) through `HaApi.call(domain, service, entityId, data)`.
- Live read: WebSocket `{"type":"todo/item/subscribe","entity_id":"todo.minuteries_foyer"}`; events carry `event.items` = `[{uid, summary, status, description?}]` (the full list each time).
- A timer rings from `end` until deleted; no automatic stop. « +1 min » only on running timers. Nouvelle minuterie: whole minutes 1–180, starts at 5, presets 1 / 3 / 5 / 10 / 15 / 30, blank name → « Minuterie ».
- Copy (exact): « Minuteries », « + Nouvelle minuterie », « Aucune minuterie », « Terminée », « Arrêter », « +1 min », « Hors ligne », « Nouvelle minuterie », « Nom (facultatif) », « Annuler », « Lancer », « Minuterie terminée », « sonne depuis m:ss », « Arrêter ici l'arrête sur toutes les tablettes. », « Sonner pour les minuteries », « Lancée en cuisine », « Lancée au salon », origin of a recipe timer « <recette> · étape N », snackbar « Minuterie de <timerLabel> lancée ».
- Remaining time format: `m:ss` under an hour, `h:mm:ss` from one hour.
- Alarm: `RingtoneManager.getDefaultUri(TYPE_ALARM)`, fallback `TYPE_RINGTONE`, `AudioAttributes.USAGE_ALARM`, looping (`Ringtone.isLooping`, API 28 = minSdk).
- Tablets only. The phone keeps today's recipe chips → Android Clock (`startSystemTimer`, kept for the phone only) and never subscribes to the timers. *(Builder's call: the spec's non-goal says the phone ignores timers; its chips keep working as today.)*
- Look: Theme.kt Amber palette; panel = `Surface(shape = RoundedCornerShape(32.dp), color = surfaceContainer)` like `RecipesPanel`; ringing card = `colors.primary` / `onPrimary`.
- Never act on the live Home Assistant without announcing it to the user first (Task 9).

## Review Focus

1. Two tablets press « Arrêter » on the same timer within a second: the second `todo.remove_item` fails (« item not found ») — no error snackbar may show for a stop. → Task 3 test `stop_of_an_already_removed_timer_is_silent`.
2. HA restarts or the tablet reconnects while a timer is ringing and was stopped locally offline: the reloaded list must not ring it again, and the queued remove is sent. → Task 3 tests `stopped_offline_is_removed_on_reconnect`, `locally_stopped_timer_does_not_ring_again`.
3. Tablet clock a few seconds off or the app restarted after the end time: a timer whose end is already past on load rings at once (spec: a tablet coming online rings for an ended, undeleted timer). → Task 3 test `timer_already_ended_on_load_rings`.
4. A malformed or foreign item in the list (typed by hand in HA's to-do panel): ignored, never crashes, never deleted. → Task 1 test `foreign_items_are_ignored`.
5. Ringing setting off, or the settings screen / an open recipe in front: the overlay and sound follow only the setting, never the screen shown. → Task 6 (overlay drawn last in `App()`, above `SleepOverlay`), Task 5 test `ringing_disabled_shows_nothing`.

---

### Task 1: Timer model

**Files:**
- Create: `app/src/main/java/fr/alex/chambre/timers/SharedTimer.kt`
- Test: `app/src/test/java/fr/alex/chambre/timers/SharedTimerTest.kt`

**Interfaces:**
- Produces:
  - `data class TodoItem(val uid: String, val summary: String, val description: String?)` (in `fr.alex.chambre.ha`, file `ha/HaMessages.kt` — created here so the model compiles; Task 2 parses it)
  - `data class SharedTimer(val id: String, val name: String, val end: Instant, val durationSec: Int, val origin: String)`
  - `fun parseTimer(item: TodoItem): SharedTimer?`
  - `fun timerDescription(end: Instant, durationSec: Int, origin: String, room: String): String`
  - `fun SharedTimer.isRinging(now: Instant): Boolean` (`!now.isBefore(end)`)
  - `fun SharedTimer.remainingSec(now: Instant): Long` (ceil, ≥ 0)
  - `fun sortTimers(timers: List<SharedTimer>, now: Instant): List<SharedTimer>` — ringing first (earliest end first), then running by soonest end
  - `fun formatClock(seconds: Long): String` — `4:12`, `27:05`, `1:02:03`
  - `fun freeOrigin(room: Room): String` — CUISINE → « Lancée en cuisine », SALON → « Lancée au salon », CHAMBRE → « Lancée dans la chambre » (unused in UI, total function)

- [ ] **Step 1: Write the failing tests** — `round_trip_description_parses_back` (timerDescription then parseTimer gives the same end/duration/origin; summary → name); `blank_summary_becomes_Minuterie`; `foreign_items_are_ignored` (description null, `"acheter du pain"`, JSON without `end`, `"v":2`, unparsable date → all null); `ringing_at_and_after_end` (end-1 s false, end true, end+5 min true); `remaining_rounds_up` (end − now = 4.2 s → 5, past → 0); `ringing_first_then_soonest` (order of [running 10 min, ringing since 1 min, running 2 min, ringing since 3 min] = [ringing 3 min, ringing 1 min, running 2, running 10]); `clock_format` (0 → `0:00`, 252 → `4:12`, 1625 → `27:05`, 3723 → `1:02:03`).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "fr.alex.chambre.timers.SharedTimerTest"` — Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** the interfaces above with kotlinx.serialization `Json.parseToJsonElement` (no `@Serializable` class needed); `end` written with `Instant.toString()`.
- [ ] **Step 4: Run** the same command — Expected: PASS.
- [ ] **Step 5: Commit** `feat(timers): shared timer model stored as to-do items`.

### Task 2: Home Assistant to-do subscription

**Files:**
- Modify: `app/src/main/java/fr/alex/chambre/ha/HaMessages.kt` (new `Incoming.TodoItemsEvent`, `HaProtocol.subscribeTodoItems`, parse branch)
- Modify: `app/src/main/java/fr/alex/chambre/ha/HaRepository.kt` (`HaConfig.timers`, `HaApi.timerItems`, subscribe on `AuthOk`)
- Modify: `app/src/main/java/fr/alex/chambre/ha/Entities.kt` (`const val TIMERS_LIST = "todo.minuteries_foyer"`)
- Test: `app/src/test/java/fr/alex/chambre/ha/HaMessagesTest.kt`, `app/src/test/java/fr/alex/chambre/ha/HaRepositoryTest.kt`

**Interfaces:**
- Consumes: `TodoItem` (Task 1).
- Produces:
  - `Incoming.TodoItemsEvent(val id: Int, val items: List<TodoItem>)`
  - `HaProtocol.subscribeTodoItems(id: Int, entityId: String): String`
  - `HaConfig(..., val timers: Boolean = false)`
  - `HaApi.timerItems: StateFlow<List<TodoItem>?>` — declared with a default getter returning a shared `MutableStateFlow(null)` so existing fakes compile; null = not loaded on this connection yet. `HaRepository` sets it to null when a session ends (so consumers know the list is stale) and to the event's items on each event.

- [ ] **Step 1: Write the failing tests** — HaMessagesTest `subscribe_todo_items_message` (JSON has `id`, `type` `todo/item/subscribe`, `entity_id`); `parses_todo_items_event` (`{"id":7,"type":"event","event":{"items":[{"uid":"a","summary":"Pâtes","status":"needs_action","description":"{…}"},{"uid":"b","summary":"x","status":"needs_action"}]}}` → `TodoItemsEvent(7, [TodoItem("a","Pâtes","{…}"), TodoItem("b","x",null)])`); `empty_items_list_is_an_event_not_an_entities_diff`. HaRepositoryTest (follow the file's existing fake-server pattern): `subscribes_to_timer_list_when_enabled` and `timer_items_follow_events_and_reset_offline`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "fr.alex.chambre.ha.*"` — Expected: FAIL.
- [ ] **Step 3: Implement** — in `parseEvent`, check `event["items"] is JsonArray` before the entities-diff fallback; subscribe only when `cfg.timers`.
- [ ] **Step 4: Run** the same command — Expected: PASS.
- [ ] **Step 5: Commit** `feat(ha): subscribe to the shared timers to-do list`.

### Task 3: TimerController (clock, writes, offline, queued stops)

**Files:**
- Create: `app/src/main/java/fr/alex/chambre/timers/TimerController.kt`
- Create: `app/src/main/java/fr/alex/chambre/timers/TimerStore.kt` (DataStore-backed, like `AnnouncementStore`)
- Test: `app/src/test/java/fr/alex/chambre/timers/TimerControllerTest.kt`

**Interfaces:**
- Consumes: Task 1 model; `HaApi.timerItems`, `HaApi.conn`, `HaApi.call` (Task 2); `Entities.TIMERS_LIST`.
- Produces:
  - `interface TimerCache { suspend fun lastKnown(): List<TodoItem>; suspend fun saveLastKnown(items: List<TodoItem>); suspend fun stopped(): Set<String>; suspend fun saveStopped(ids: Set<String>) }`; `class TimerStore(dataStore: DataStore<Preferences>) : TimerCache` (keys `timers_last_known` as a JSON string, `timers_stopped` string set)
  - `class TimerController(scope: CoroutineScope, ha: HaApi, cache: TimerCache, room: () -> Room, now: () -> Instant = Instant::now, tickMs: Long = 1_000)`
  - `val timers: StateFlow<List<SharedTimer>>` — sorted (`sortTimers`), locally stopped ones excluded, re-sorted every tick
  - `val ringing: StateFlow<List<SharedTimer>>` — the ringing subset of `timers`
  - `val online: StateFlow<Boolean>` — `conn == CONNECTED && timerItems != null`
  - `val now: StateFlow<Instant>` — ticks every `tickMs` (UI countdowns read it)
  - `suspend fun start(seconds: Int, name: String, origin: String): Boolean`
  - `suspend fun startFree(minutes: Int, name: String): Boolean` — origin = `freeOrigin(room())`, name blank → « Minuterie »
  - `suspend fun addMinute(id: String): Boolean` — `update_item` with `end + 60 s`, `duration + 60`
  - `suspend fun delete(id: String): Boolean` — `remove_item`
  - `fun stop(id: String)` — hides it locally at once, persists it in `stopped`, and sends `remove_item` now if online
- Behaviour: when `timerItems` is non-null, it is the truth (saved as last known; `stopped` ids no longer in it are dropped from the set; `stopped` ids still in it get `remove_item` sent once per connection). When null, the last known list is used. `start`/`addMinute`/`delete` return false without calling HA when offline; the « Hors ligne » snackbar comes from `HaRepository.errors` only for these, never for `stop` (call `stop`'s remove through a path that does not report errors — add `HaApi.callQuietly(domain, service, entityId, data): Boolean` with a default implementation delegating to `call`, overridden in `HaRepository` with `reportErrors = false`).

- [ ] **Step 1: Write the failing tests** (fake `HaApi` with settable `conn`/`timerItems` and a recorded `calls` list; fake `TimerCache` in memory; `now` from a variable; `runTest` + `advanceTimeBy`) — `start_adds_item_with_end_and_origin` (now 12:00:00, `start(600,"Pâtes","Lasagnes · étape 4")` → one `todo.add_item` with `item`=Pâtes and description end 12:10:00Z, duration 600); `free_timer_from_kitchen` (`startFree(5,"")` → name Minuterie, origin « Lancée en cuisine », duration 300); `timer_rings_at_end` (ringing empty at 12:09:59, one at 12:10:00 after a tick); `timer_already_ended_on_load_rings`; `add_minute_pushes_end`; `stop_hides_at_once_and_removes` (`stop` → absent from `timers`/`ringing` before HA answers, `todo.remove_item` with `item`=uid); `stop_of_an_already_removed_timer_is_silent` (fake `callQuietly` returns false → no error emitted, timer stays hidden); `stopped_offline_is_removed_on_reconnect` (offline: stop → no call, id persisted; back online with the item still listed → `remove_item` sent); `locally_stopped_timer_does_not_ring_again` (list reloads with the item → still hidden); `offline_uses_last_known_list` (timerItems → null: `timers` unchanged, `online` false, `start` returns false and sends nothing); `restart_offline_reads_cache` (new controller, HA never connects → cached timers shown, ending one rings).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "fr.alex.chambre.timers.*"` — Expected: FAIL.
- [ ] **Step 3: Implement** `TimerStore`, `HaApi.callQuietly` and `TimerController` per the interfaces.
- [ ] **Step 4: Run** the same command plus `--tests "fr.alex.chambre.ha.*"` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(timers): controller with offline cache and stops that always work`.

### Task 4: Settings switch and device features

**Files:**
- Modify: `app/src/main/java/fr/alex/chambre/settings/SettingsStore.kt` (`Settings.timerRing: Boolean = true`, key `timer_ring`)
- Modify: `app/src/main/java/fr/alex/chambre/settings/DeviceFeatures.kt` (remove `timerEvents`; add `timers: Boolean` = tablet; `timerPanel: Boolean` = tablet && room in CUISINE, SALON)
- Modify: `app/src/main/java/fr/alex/chambre/settings/SettingsScreen.kt` (switch « Sonner pour les minuteries » in the « Pièce de cette tablette » card for every room, tablets only; settings text drops « fin des minuteurs de cuisine »)
- Test: `app/src/test/java/fr/alex/chambre/settings/SettingsStoreTest.kt`, `DeviceFeaturesTest.kt`

**Interfaces:**
- Produces: `Settings.timerRing`, `DeviceFeatures.timers`, `DeviceFeatures.timerPanel`.

- [ ] **Step 1: Write the failing tests** — `timer_ring_defaults_on_and_round_trips`; DeviceFeaturesTest: phone → `timers` false, `timerPanel` false; tablet CHAMBRE → `timers` true, `timerPanel` false; SALON and CUISINE → both true; remove the `timerEvents` assertions.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "fr.alex.chambre.settings.*"` — Expected: FAIL.
- [ ] **Step 3: Implement**; the switch mirrors the « Démarrage automatique » row (title + `Switch`).
- [ ] **Step 4: Run** the same command — Expected: PASS (other modules may not compile until Task 5 wires `timerEvents` away; if so run Step 4 after Task 5's Step 3 and commit both together).
- [ ] **Step 5: Commit** `feat(settings): « Sonner pour les minuteries » on every tablet`.

### Task 5: Wiring, alarm sound and removal of the old timers

**Files:**
- Create: `app/src/main/java/fr/alex/chambre/timers/AlarmPlayer.kt`
- Create: `app/src/main/java/fr/alex/chambre/timers/RingState.kt`
- Modify: `app/src/main/java/fr/alex/chambre/ui/DashboardViewModel.kt` (HaConfig `timers = f.timers`; `val timers = TimerController(...)`; `val ringingShown`; alarm + presence wiring; `onCleared` stops the player)
- Modify: `app/src/main/java/fr/alex/chambre/presence/PresenceController.kt` (`var alarm: Boolean`: wakes when set true, blocks sleep while true, independent of `hold`)
- Remove: `Entities.KITCHEN_TIMERS`, `timerStartScript`, `HaConfig.timerEvents`, `HaApi.events` and the `timer.finished` subscription in `HaRepository`, the timer branch and `api` parameter of `AnnouncementController`, `Announcement.TimerDone` / `timerFinished` in `AnnouncementEngine`, and their tests (`timer_finished_uses_friendly_name`, `only_kitchen_timers_are_announced`, engine timer tests).
- Modify: `app/src/main/java/fr/alex/chambre/ui/salon/RoomSummary.kt` (`kitchenSummary(cover: EntityState?, running: Int)`), `SalonApp.kt` call site (`vm.timers.timers.value.size` via `collectAsStateWithLifecycle`)
- Test: `app/src/test/java/fr/alex/chambre/timers/RingStateTest.kt`, `presence/PresenceControllerTest.kt`, `ui/salon/RoomSummaryTest.kt` (existing file in `ui/salon`; adapt)

**Interfaces:**
- Consumes: `TimerController.ringing` (Task 3), `Settings.timerRing`, `DeviceFeatures` (Task 4).
- Produces:
  - `fun ringingShown(ringing: List<SharedTimer>, enabled: Boolean, tablet: Boolean): List<SharedTimer>` (RingState.kt, pure)
  - `DashboardViewModel.timers: TimerController`, `DashboardViewModel.ringingShown: StateFlow<List<SharedTimer>>`
  - `class AlarmPlayer(context: Context) { fun start(); fun stop() }` — idempotent; `start` while playing does nothing
  - `PresenceController.alarm: Boolean`

- [ ] **Step 1: Write the failing tests** — `ringing_disabled_shows_nothing`, `phone_shows_nothing`, `enabled_tablet_shows_ringing`; PresenceControllerTest `alarm_wakes_and_blocks_sleep` (asleep → `alarm = true` → AWAKE; past the delay still AWAKE; `alarm = false` → sleeps after the delay); RoomSummary `kitchen_summary_counts_shared_timers` (0 → « Volet ouvert · minuteurs prêts », 2 → « … · 2 minuteurs en cours »).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest` — Expected: FAIL (new tests) .
- [ ] **Step 3: Implement** — in the ViewModel, collect `ringingShown`: non-empty → `presence.alarm = true; player.start()`, empty → `presence.alarm = false; player.stop()`. `room` for the controller = `settings.value?.room ?: Room.CHAMBRE`; cache = `TimerStore(app.dataStore)`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` — Expected: all PASS (whole suite: the removals touch several test files).
- [ ] **Step 5: Commit** `feat(timers): ring on every tablet; drop the HA timer helpers from the app`.

### Task 6: UI — Minuteries panel, Nouvelle minuterie, ringing overlay, recipe chips

**Files:**
- Create: `app/src/main/java/fr/alex/chambre/ui/timers/TimersPanel.kt`
- Create: `app/src/main/java/fr/alex/chambre/ui/timers/NewTimerDialog.kt`
- Create: `app/src/main/java/fr/alex/chambre/ui/timers/RingingOverlay.kt`
- Create: `app/src/main/java/fr/alex/chambre/ui/timers/NewTimerState.kt` (pure stepper/preset logic)
- Modify: `app/src/main/java/fr/alex/chambre/ui/cuisine/CuisineApp.kt` (`CuisinePanels`: Cover 200 dp, `TimersPanel` 330 dp, `RecipesPanel(columns = 2)`; `CuisineRecipeOverlay` chips → `vm.timers.start` on tablets, `startSystemTimer` on the phone)
- Modify: `app/src/main/java/fr/alex/chambre/ui/cuisine/RecipeScreen.kt` (`onTimer: (seconds: Int, name: String, origin: String) -> Unit`, origin « ${recipe.name} · étape ${i + 1} »)
- Modify: `app/src/main/java/fr/alex/chambre/MainActivity.kt` (`App()`: `RingingOverlay` after `SleepOverlay`)
- Modify: `app/src/main/java/fr/alex/chambre/ui/phone/PhoneApp.kt` (new `onTimer` signature only)
- Test: `app/src/test/java/fr/alex/chambre/ui/timers/NewTimerStateTest.kt`

**Interfaces:**
- Consumes: `vm.timers` (`timers`, `now`, `online`, `startFree`, `addMinute`, `delete`, `stop`, `start`), `vm.ringingShown`, `formatClock`, `isRinging`, `remainingSec`.
- Produces: `@Composable fun TimersPanel(vm: DashboardViewModel, modifier: Modifier)`, `@Composable fun RingingOverlay(vm: DashboardViewModel)`, `@Composable fun NewTimerDialog(onDismiss: () -> Unit, onStart: (minutes: Int, name: String) -> Unit)`; `data class NewTimerState(val minutes: Int = 5) { fun plus(): NewTimerState; fun minus(): NewTimerState; fun preset(m: Int): NewTimerState }` clamped 1–180.

- [ ] **Step 1: Write the failing test** — `stepper_starts_at_5_and_clamps` (minus ×10 → 1; plus from 180 → 180; preset 30 → 30; presets list = 1, 3, 5, 10, 15, 30).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "fr.alex.chambre.ui.timers.*"` — Expected: FAIL.
- [ ] **Step 3: Implement** the composables following mockup A / Nouvelle minuterie / Sonnerie: cards `surfaceContainerHigh`, countdown 38 sp primary tabular digits, progress = elapsed / duration, ringing card primary with « Terminée » + « Arrêter »; offline → panel content at 50 % alpha with « Hors ligne », buttons call the controller and show a snackbar on `false`; empty → « Aucune minuterie »; list scrolls (`LazyColumn`), button pinned at the bottom. Overlay: scrim `Color(0xDB0D0906)` full screen consuming taps, header « Minuterie terminée · <origin> », first ringing timer as the large primary card (name 52 sp, « <timerLabel(duration)> · sonne depuis m:ss », « Arrêter » 96 dp tall), others as secondary cards, footnote.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` then `./gradlew assembleDebug` — Expected: PASS, BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(timers): Minuteries panel, new timer dialog and ringing screen`.

### Task 7: Emulator check against an offline HA

**Files:** none (verification only).

- [ ] **Step 1:** Install the debug APK on the API 30 AVD `tabS2a11` with an offline HA URL (memory: no blind taps on the real HA). Room Cuisine: the panel shows « Hors ligne », « Aucune minuterie »; the dialog opens, clamps, and « Lancer » shows the offline snackbar. Screenshot each.
- [ ] **Step 2:** Room Chambre: no panel; settings show the switch. Screenshot.

### Task 8: Release

- [ ] **Step 1:** `versionCode = 14`, `versionName = "1.6.0"` in `app/build.gradle.kts`; README line about timers (shared via `todo.minuteries_foyer`, created once in HA, « Sonner pour les minuteries » per tablet).
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleRelease` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `chore: v1.6.0`.

### Task 9: Home Assistant setup and first live test (with the user)

- [ ] **Step 1:** Tell the user, then (Orca browser on HA, user logged in) add the « Local To-do » integration named « Minuteries foyer »; check the entity id is `todo.minuteries_foyer`.
- [ ] **Step 2:** In HA, search automations, scripts and dashboards for `minuteur_1`, `minuteur_2`, `minuteur_3`, `demarrer_minuteur`; report findings; with the user's go-ahead delete helpers `timer.minuteur_1..3` and the six `script.demarrer_minuteur{1,2,3}_{5,10}min`.
- [ ] **Step 3:** Announce, then install the release on the Lenovo and both Tab S2 (`adb -s <serial> install -r -g`). Start a 1-min free timer on the Lenovo: it appears on the salon tablet's Cuisine tab within a second; at the end all three ring; « Arrêter » on the bedroom tablet silences all three. Report with logcat evidence.
