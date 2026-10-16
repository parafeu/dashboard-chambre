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

- **Réglages** : toucher la ligne « batterie + engrenage » sous la date. L'écran Réglages montre l'aperçu
  caméra en direct (zones en mouvement en vert) ; il empêche la mise en veille tant qu'il est ouvert.
- **Veille** : écran noir après le délai réglé (1 min par défaut) sans mouvement devant la caméra ni toucher.
  Un mouvement ou un toucher réveille l'écran ; le premier toucher ne déclenche rien d'autre.
- **Détection de mouvement** (OpenCV, soustraction de fond MOG2) : une zone qui bouge sur au moins 2 images
  sur 3 réveille l'écran ; les changements de lumière (plus de 60 % de l'image d'un coup) et la propre
  lumière de l'écran sont ignorés. Réglage « Taille minimale d'un mouvement » (3 % par défaut).
- **Météo** : toucher le bloc météo pour passer de « Heures » (7 prochaines heures) à « Semaine » (6 jours) ;
  retour automatique aux heures après 30 s.
- **Par défaut** : remet la lumière à 70 % · 2700 K, comme l'appui long des télécommandes.

## Batterie dans Home Assistant

L'app s'enregistre via l'intégration **Mobile App** (appareil « Tablette chambre ») et publie :
`sensor.tablette_chambre_batterie`, `binary_sensor.tablette_chambre_en_charge`,
`sensor.tablette_chambre_temperature_batterie` (immédiatement au (dé)branchement, sinon au plus toutes les
5 min, et toutes les 30 min dans tous les cas).

L'automatisation HA « Tablette chambre – protection batterie » garde la batterie entre 40 et 80 % en pilotant
la prise `switch.grillplats_plug_2` (coupure aussi au-delà de 40 °C, rallumage de sécurité sans nouvelles depuis 2 h).

Les entités sont codées en dur dans `app/src/main/java/fr/alex/chambre/ha/Entities.kt`.
