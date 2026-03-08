# StreamLocal — Application Android

Application Android native permettant de diffuser et visualiser les médias (vidéos et photos) hébergés sur un serveur local. Conçue pour fonctionner en réseau local (Wi-Fi domestique) avec support des certificats auto-signés.

---

## Fonctionnalités

| Catégorie | Détail |
|-----------|--------|
| **Connexion** | Configuration de l'URL du serveur, test de connexion automatique, support HTTPS auto-signé |
| **Authentification** | Code PIN avec protection anti-brute-force (rate limiting côté serveur) |
| **Vidéos** | Lecture en streaming via ExoPlayer, navigation Précédent/Suivant, avance automatique |
| **Photos** | Visionneuse avec zoom pinch-to-zoom, swipe horizontal, navigation par boutons |
| **Diaporama** | Lecture automatique, boucle, intervalles configurables (2s / 4s / 8s / 15s) |
| **Recherche** | Filtrage en temps réel sur les vidéos et photos |
| **UI** | Design dark ultra-moderne, Material 3, glassmorphism, animations fluides |

---

## Architecture

```
StreamLocalApp/
├── data/
│   ├── api/
│   │   ├── NetworkClient.kt        # Singleton OkHttp + Retrofit, gestion SSL
│   │   └── StreamApi.kt            # Interface Retrofit (endpoints REST)
│   ├── model/
│   │   └── MediaFile.kt            # Modèles de données (MediaFile, VideoInfo, PhotoInfo, Auth)
│   ├── preferences/
│   │   └── AppPreferences.kt       # Persistance DataStore (URL serveur, SSL)
│   └── repository/
│       └── MediaRepository.kt      # Couche d'accès aux données, construction des URLs
│
├── ui/
│   ├── home/
│   │   ├── HomeScreen.kt           # Écran principal — liste vidéos/photos, recherche
│   │   └── HomeViewModel.kt        # État de la liste, filtrage, tabs
│   ├── login/
│   │   ├── LoginScreen.kt          # Saisie du code PIN, animation d'erreur
│   │   └── LoginViewModel.kt       # Authentification, rate limiting, countdown
│   ├── navigation/
│   │   └── AppNavigation.kt        # NavHost, routes, navigation entre écrans
│   ├── photo/
│   │   ├── PhotoViewerScreen.kt    # Visionneuse photo — zoom, swipe, diaporama UI
│   │   └── PhotoViewerViewModel.kt # Logique diaporama, navigation, état
│   ├── player/
│   │   ├── VideoPlayerScreen.kt    # Lecteur vidéo — contrôles personnalisés
│   │   └── VideoPlayerViewModel.kt # ExoPlayer, navigation vidéo, auto-avance
│   ├── setup/
│   │   ├── ServerSetupScreen.kt    # Configuration de l'URL du serveur
│   │   └── ServerSetupViewModel.kt # Test de connexion, validation, sauvegarde
│   └── theme/
│       ├── Color.kt                # Palette de couleurs (dark amber)
│       ├── Theme.kt                # Material 3 ColorScheme
│       └── Type.kt                 # Typographie
│
├── MainActivity.kt                 # Point d'entrée, routage initial
└── StreamLocalApp.kt               # Application class, init réseau + Coil
```

### Pattern MVVM

```
Screen (Composable)
    ↕ collectAsState()
ViewModel (StateFlow<UiState>)
    ↕ suspend functions
Repository
    ↕ Retrofit / OkHttp
Serveur local (API REST Flask)
```

---

## Stack technique

| Composant | Bibliothèque | Version |
|-----------|-------------|---------|
| UI | Jetpack Compose + Material 3 | BOM 2024.x |
| Navigation | Navigation Compose | 2.8.5 |
| Réseau | Retrofit + OkHttp | — |
| Sérialisation | Gson (+ TypeAdapter tolérant) | — |
| Vidéo | Media3 / ExoPlayer | 1.5.0 |
| Images | Coil | 2.7.0 |
| Persistance | DataStore Preferences | 1.1.1 |
| Coroutines | Kotlin Coroutines | — |
| Min SDK | API 26 (Android 8.0) | — |
| Target SDK | API 35 (Android 15) | — |

---

## API serveur attendue

L'application s'attend à un serveur HTTP/HTTPS exposant les endpoints suivants :

```
POST /api/v1/auth                   → { "code": "PIN" }
                                    ← { "ok": true/false, "msg": "..." }

GET  /api/v1/logout                 → déconnexion session

GET  /api/v1/files/videos           ← [ { "name", "path", "ext", "size" }, ... ]
GET  /api/v1/files/photos           ← [ { "name", "path", "ext", "size" }, ... ]

GET  /api/v1/stream/video/{path}    → flux vidéo (streaming)
GET  /api/v1/stream/photo/{path}    → image

GET  /api/v1/info/video/{path}      ← { "name", "path", "size", "duration", "width", "height", "codec", "bitrate" }
GET  /api/v1/info/photo/{path}      ← { "name", "path", "size", "width", "height" }
```

> **Note :** Le champ `size` peut être une string formatée (`"3.9 Mo"`) ou un nombre. Le client gère les deux formats.

---

## Configuration réseau

### Certificats auto-signés

L'option **"Faire confiance aux certificats auto-signés"** sur l'écran de configuration désactive la vérification SSL pour le serveur local. Elle est activée par défaut.

La gestion SSL est faite dans `NetworkClient` via un `X509TrustManager` personnalisé injecté dans OkHttp. Cette approche contourne la vérification **uniquement** pour les connexions internes — elle ne doit pas être utilisée en production.

### Trafic HTTP (non chiffré)

Le fichier `network_security_config.xml` autorise le trafic HTTP en clair (`cleartextTrafficPermitted="true"`) pour permettre les connexions à un serveur local sans HTTPS.

---

## Écrans

### 1. Configuration du serveur (`ServerSetupScreen`)
- Saisie de l'URL du serveur (ex: `http://192.168.1.10:5000`)
- Test de connexion automatique avant de continuer
- Option pour accepter les certificats auto-signés

### 2. Connexion (`LoginScreen`)
- Saisie du code PIN
- Animation de secousse en cas d'erreur
- Affichage du countdown en cas de rate limiting (HTTP 429)
- Lien pour changer de serveur

### 3. Accueil (`HomeScreen`)
- Tabs Vidéos / Photos
- Barre de recherche filtrée en temps réel
- Pull-to-refresh
- Bouton de déconnexion

### 4. Lecteur vidéo (`VideoPlayerScreen`)
- Mode paysage forcé
- Contrôles personnalisés (précédent, lecture/pause, suivant)
- Avance automatique en fin de vidéo
- Comportement "précédent" intelligent : retour au début si > 3s écoulées

### 5. Visionneuse photo (`PhotoViewerScreen`)
- Zoom pinch-to-zoom (jusqu'à 5×)
- Swipe horizontal pour naviguer
- **Diaporama** avec :
  - Barre de progression animée (en haut de l'écran + dans la barre de contrôle)
  - Bouton Play/Pause
  - Lecture en boucle (toggle)
  - Intervalle réglable : 2s / 4s / 8s / 15s
  - Navigation Précédent / Suivant avec loop optionnel
  - Dots cliquables (jusqu'à 9 photos)

---

## Build & Installation

### Prérequis
- Android Studio Hedgehog (2023.1.1) ou plus récent
- JDK 11
- Un appareil ou émulateur Android 8.0+ (API 26)

### Build debug
```bash
./gradlew assembleDebug
```

### Installation directe
```bash
./gradlew installDebug
```

### Release
```bash
./gradlew assembleRelease
```

---

## Sécurité

- Le code PIN n'est **jamais** stocké sur l'appareil
- Les sessions sont gérées via cookie de session Flask (stocké en mémoire uniquement)
- Les cookies sont effacés à la déconnexion
- La vérification SSL peut être désactivée **uniquement** pour les serveurs locaux de confiance
