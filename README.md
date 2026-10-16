# Dashboard Chambre

App Android native (Jetpack Compose, Material 3) qui remplace Fully Kiosk + Lovelace sur la
Galaxy Tab S2 (LineageOS, Android 11) de la chambre : contrôle lumière / volet / scènes,
heure et météo, veille de l'écran pilotée par la caméra frontale.

Spec : `docs/superpowers/specs/2026-10-16-dashboard-chambre-design.md`

## Compiler

Prérequis : JDK 21 (chemin dans `gradle.properties`), Android SDK (`local.properties` → `sdk.dir`).

```bash
./gradlew testDebugUnitTest   # tests unitaires
./gradlew assembleRelease     # APK signé : app/build/outputs/apk/release/app-release.apk
```

La signature release lit `keystore.properties` (non versionné) :

```properties
storeFile=C:/Users/Alex/.android-keys/chambre-release.jks
storePassword=…
keyAlias=chambre
keyPassword=…
```

Garde le keystore : les mises à jour de l'app sur la tablette doivent être signées avec la même clé.

## Installer sur la tablette

1. Sur la tablette : **Options pour les développeurs** → activer **Débogage USB** (et **Débogage ADB
   sur le réseau** pour le Wi-Fi).
2. Depuis le PC :
   ```bash
   adb connect <ip-tablette>:5555        # ou brancher en USB
   adb install -r -g app/build/outputs/apk/release/app-release.apk
   ```
   `-g` accorde directement la permission caméra.
3. Appuyer sur le bouton **Accueil** → choisir **Chambre** → **Toujours**.
4. Désactiver Fully Kiosk (ou le désinstaller) pour qu'il ne reprenne pas la main au démarrage.
5. Dans Home Assistant : profil → **Sécurité** → **Jetons d'accès longue durée** → créer un jeton.
6. Dans l'app : saisir l'URL (`http://homeassistant.local:8123` ou l'IP) et le jeton →
   **Enregistrer et connecter**.

## Utilisation

- **Réglages** : appui long de 3 s sur l'heure.
- **Veille** : écran noir après le délai réglé (60 s par défaut) sans mouvement devant la caméra ni
  toucher. Un mouvement ou un toucher réveille l'écran ; le premier toucher ne déclenche rien d'autre.
- **Sensibilité caméra** : à ajuster dans les réglages si l'écran se réveille trop (augmenter) ou
  pas assez (baisser).
- **Par défaut** : remet la lumière à 70 % · 2700 K, comme l'appui long des télécommandes.

Les entités sont codées en dur dans `app/src/main/java/fr/alex/chambre/ha/Entities.kt`.
