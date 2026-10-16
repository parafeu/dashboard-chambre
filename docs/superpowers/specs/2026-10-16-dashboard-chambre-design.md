# Dashboard Chambre — app Android native (Material 3 Expressive)

Date : 2026-10-16
Statut : validé en brainstorming (disposition B2, palette Ambre chaud, sélecteur de couleur inclus)

## 1. Contexte et objectif

Une Galaxy Tab S2 sous **LineageOS (Android 12, API 31)**, fixée dans la chambre en paysage,
affiche aujourd'hui le dashboard Lovelace `dashboard-chambre/chambre` via **Fully Kiosk**.

Problèmes :
- Lovelace (Bubble Card + card-mod + light-entity-card) est peu fluide sur ce SoC de 2015.
- La détection de présence par caméra de Fully Kiosk est payante.
- L'utilisateur veut une vraie interface **Material 3 Expressive** (look « Android 17 »).

Objectif : remplacer Fully Kiosk + Lovelace par **une seule app Android native** qui :
1. affiche un panneau de contrôle de la chambre fluide en Compose Material 3 ;
2. parle directement à Home Assistant via l'API WebSocket ;
3. réveille / met en veille l'écran selon le mouvement détecté par la caméra frontale ;
4. sert de lanceur (kiosque) sur la tablette.

### Critères de réussite
- Sliders qui suivent le doigt sans latence perceptible ; commandes HA envoyées en continu.
- Écran qui se réveille à l'approche, se met en veille après inactivité, sans rien payer.
- Toutes les fonctions retenues de l'ancien dashboard sont présentes (sauf la minuterie, retirée volontairement).

## 2. Périmètre

### Inclus
- Bandeau : heure, date, météo actuelle + 4 prochaines heures.
- Ambiance : 4 scènes (scripts).
- Lumière : luminosité, température de blanc, 9 pastilles couleur, sélecteur de couleur complet.
- Volet : position, ↑ ■ ↓, mi-hauteur.
- Veille pilotée par caméra + toucher.
- Mode kiosque (app HOME, plein écran immersif, écran maintenu allumé).
- Écran de réglages caché.

### Exclu (YAGNI)
- Minuterie (retirée à la demande de l'utilisateur).
- Exposition d'un capteur de présence vers HA.
- Plusieurs vues, dashboard configurable, découverte auto de HA, OAuth.
- Extinction réelle de l'écran (device admin / root) — on utilise un voile noir + luminosité minimale.
- Thème clair.

## 3. Entités Home Assistant (codées en dur)

| Rôle | Entité | Détails relevés |
|---|---|---|
| Lumière (groupe 2 ampoules IKEA Kajplats) | `light.chambre_lumieres` | modes `color_temp`, `hs`, `xy` ; kelvin 1801–6535 |
| Volet | `cover.volet_chambre` | features 15 (open/close/stop/set_position) ; `current_position` 0–100, 100 = ouvert |
| Scène Coucher | `script.chambre_scene_coucher` | |
| Scène Lecture | `script.chambre_scene_lecture` | |
| Scène Réveil | `script.chambre_scene_reveil` | |
| Scène Nuit | `script.chambre_scene_nuit` | |
| Mi-hauteur volet | `script.chambre_volet_mi_hauteur` | |
| Météo | `weather.forecast_maison` | met.no, prévisions horaires + journalières |

Pastilles couleur (reprises de la config Lovelace actuelle, transition 1 s) :
`2200 K`, `2700 K`, `4000 K`, `6500 K`, `rgb(255,60,45)`, `rgb(255,140,40)`,
`rgb(70,220,120)`, `rgb(55,140,255)`, `rgb(170,90,255)`.

## 4. Architecture

App Kotlin unique, `minSdk 31`, `targetSdk 35`, package `fr.alex.chambre`.

```
app/src/main/java/fr/alex/chambre/
  MainActivity.kt          plein écran immersif, keep-screen-on, HOME launcher
  ha/
    HaConnection.kt        WebSocket OkHttp : auth, ids de messages, reconnexion backoff
    HaMessages.kt          modèles kotlinx.serialization (auth, subscribe_entities, call_service, forecast)
    HaRepository.kt        StateFlow par entité, météo, callService(), état de connexion
    Entities.kt            constantes des entités + pastilles
  ui/
    theme/Theme.kt         ColorScheme sombre généré depuis la graine ambre (#FFB86B)
    DashboardScreen.kt     grille B2 : bandeau + 3 colonnes
    HeaderBar.kt           heure, date, météo, pastille hors-ligne
    ScenesPanel.kt         4 tuiles de scène
    LightPanel.kt          2 sliders verticaux + pastilles
    ColorSheet.kt          bottom sheet roue de teintes + saturation
    CoverPanel.kt          slider vertical + 4 boutons
    components/VerticalSlider.kt
    SleepOverlay.kt        voile noir + absorption du premier toucher
  presence/
    MotionDetector.kt      fonction pure : (frame précédente, frame courante) -> score
    CameraMotionSource.kt  CameraX ImageAnalysis frontale, 160×120, ~3 fps, luminance Y
    PresenceController.kt  machine d'états AWAKE / ASLEEP, horloge injectable
  settings/
    SettingsStore.kt       DataStore : URL HA, jeton, délai veille, sensibilité
    SettingsScreen.kt
  util/Throttle.kt         limiteur de débit pour les sliders
```

### Flux de données
1. `HaConnection` ouvre `ws://<host>/api/websocket`, envoie `auth` avec le jeton longue durée.
2. Après `auth_ok` : `subscribe_entities` avec la liste des entités ci-dessus,
   et `weather/subscribe_forecast` (`forecast_type: hourly`) pour la météo.
3. Les messages `event` (format compressé `a`/`c`/`r` de `subscribe_entities`) mettent à jour
   une `Map<String, EntityState>` exposée en `StateFlow` par `HaRepository`.
4. L'UI collecte ces flows ; les actions appellent `HaRepository.callService(domain, service, target, data)`
   qui envoie un message `call_service` et attend le `result`.
5. Pendant un geste de slider, l'UI affiche la valeur locale (état optimiste) ; les commandes partent
   au maximum toutes les 150 ms, la valeur finale est envoyée au relâchement. L'état HA reprend la main
   1 s après le relâchement.

## 5. Interface (disposition B2, palette Ambre chaud)

Écran unique paysage 4:3 (2048×1536). Thème sombre uniquement.
Palette générée depuis la graine `#FFB86B` (primary `#FFB86B`, onPrimary `#4A2800`,
background `#19120C`, surfaceContainer `#261E17`, surfaceContainerHigh `#3A3028`, tertiary `#C5CA8F`).

### Bandeau (pleine largeur, en haut)
- Gauche : heure `HH:mm` en grand (graisse fine), date « Jeudi 16 octobre » (locale fr-FR).
- Droite : icône condition + température actuelle ; 4 prochaines heures (heure, icône, °C).
- Pastille « Hors ligne » quand la WebSocket est déconnectée.
- Appui long 3 s sur l'heure → écran de réglages.

### Colonne 1 — Ambiance
- 4 grandes tuiles empilées : Coucher, Lecture, Réveil, Nuit (icônes Material Symbols).
- Appui → `script.turn_on`. Tuile animée (forme + couleur) tant que le script est `on`.
- La dernière scène lancée reste en surbrillance (primary) ; la surbrillance est effacée si la lumière
  s'éteint ou est modifiée depuis le panneau Lumière.

### Colonne 2 — Lumière
- Deux sliders verticaux épais côte à côte :
  - Luminosité 0–100 % (0 = `light.turn_off`) ; pourcentage affiché ; appui sur l'icône = toggle.
  - Température : dégradé chaud→froid, plage `min_color_temp_kelvin`–`max_color_temp_kelvin`.
- Rangée de 9 pastilles + 1 pastille arc-en-ciel.
- Pastille arc-en-ciel → `ColorSheet` (bottom sheet) : roue de teintes + slider de saturation,
  appliqués en direct (`hs_color`, débit limité à 150 ms).

### Colonne 3 — Volet
- Slider vertical qui se remplit **depuis le haut** : hauteur remplie = `100 - current_position`
  (le volet « descend »). Relâchement → `cover.set_cover_position`.
- Boutons ↑ (`open_cover`), ■ (`stop_cover`), ↓ (`close_cover`), ½ (`script.chambre_volet_mi_hauteur`).

### Contrôles indisponibles
Entité absente ou `unavailable` → contrôle grisé et désactivé. Déconnexion → tout grisé.

## 6. Présence et veille

- `CameraMotionSource` : CameraX `ImageAnalysis`, caméra frontale, résolution cible 160×120,
  `STRATEGY_KEEP_ONLY_LATEST`, analyse ~3 fps (frames intermédiaires ignorées). Seul le plan Y est lu,
  sous-échantillonné à 40×30.
- `MotionDetector.score(prev, cur)` = proportion de pixels dont |Δluminance| > 25.
  Mouvement si score > seuil (sensibilité réglable, défaut 0,02).
- `PresenceController` :
  - AWAKE → ASLEEP après `sleepDelay` (défaut 60 s) sans mouvement ni toucher.
  - ASLEEP → AWAKE sur mouvement ou toucher.
- ASLEEP : `SleepOverlay` noir opaque + `window.attributes.screenBrightness = 0.01`.
  AWAKE : luminosité système (`BRIGHTNESS_OVERRIDE_NONE`).
- En ASLEEP, le premier toucher ne fait que réveiller (il est consommé par l'overlay).
- Caméra refusée/absente : veille au toucher uniquement.
- La caméra tourne tant que l'activité est au premier plan (pas de service : l'app est le lanceur
  et reste au premier plan en permanence).

## 7. Kiosque

- `MainActivity` déclarée avec `CATEGORY_HOME` + `CATEGORY_DEFAULT` → choisie comme lanceur.
- Plein écran immersif (`WindowInsetsControllerCompat`, barres masquées, réapparition par balayage).
- `FLAG_KEEP_SCREEN_ON` ; orientation `sensorLandscape`.
- Retour (Back) ignoré sur l'écran principal.

## 8. Réglages

Écran caché (appui long 3 s sur l'heure) : URL HA (défaut `http://homeassistant.local:8123`),
jeton longue durée (champ masqué), délai de veille (s), sensibilité caméra, bouton « Tester la connexion ».
Stockage DataStore Preferences. Au premier lancement sans jeton → écran de réglages directement.

## 9. Gestion des erreurs

| Cas | Comportement |
|---|---|
| `auth_invalid` | ouvre les réglages avec message « Jeton refusé » |
| HA injoignable / coupure | pastille Hors ligne, reconnexion backoff 1, 2, 4, 8, 16, 30 s (plafond) ; réabonnement complet après reconnexion |
| Entité absente / `unavailable` | contrôle grisé |
| `result` avec `success: false` | snackbar avec le message d'erreur HA |
| Caméra indisponible | veille au toucher uniquement, pas d'erreur bloquante |

## 10. Tests

Tests unitaires JVM (JUnit + kotlinx-coroutines-test + Turbine) :
- `MotionDetector` : images identiques → 0 ; zone modifiée → score attendu ; bruit sous le seuil ignoré.
- `PresenceController` : transitions avec horloge virtuelle (veille à 60 s, réveil mouvement/toucher).
- `HaMessages` / `HaRepository` : décodage `auth_*`, `result`, événements `subscribe_entities`
  (ajout `a`, changement `c`, suppression `r`), forecast ; sérialisation `call_service`.
- `Throttle` : débit max respecté, dernière valeur toujours émise.
- `HaConnection` contre un `MockWebServer` OkHttp : auth, ids croissants, reconnexion.

Validation de bout en bout sur émulateur :
- AVD Android 12 (API 31), 2048×1536, densité 320 dpi, paysage, caméra frontale = webcam.
- Connexion au vrai HA de l'utilisateur ; vérification par captures d'écran.
- Les tests qui actionnent la lumière / le volet réels sont annoncés avant exécution.

## 11. Outillage et installation

- Android SDK (cmdline-tools, platform-tools, build-tools, platform 35, emulator,
  system image `android-31;google_apis;x86_64`) installés dans `%LOCALAPPDATA%\Android\Sdk`.
- JDK 21 (le JDK 25 présent n'est pas supporté par AGP) ; Gradle wrapper.
- Build release signée avec une clé locale, R8 activé (pas de baseline profile pour la v1).
- Déploiement tablette : `adb install`, choix du lanceur par défaut, désactivation de Fully Kiosk,
  saisie de l'URL et du jeton dans les réglages.
