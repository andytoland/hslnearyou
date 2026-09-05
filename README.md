# HSL Lähellä (Android)

A native Android application (Google Pixel and Material You optimized) that provides real-time public transit routing, journey planning, and departures for the Helsinki region (HSL), taking you seamlessly from **where you are** to **where you go** (Home, Work, or any destination).

---

## 🚀 Key Features

- **Origin ➔ Destination Journey Planning**: Select a destination (e.g., *Koti / Home*, *Työ / Work*, or custom favorites) to instantly calculate the fastest public transport itineraries (buses, subways, trams, trains, and ferries) from your current starting position.
- **Walking Time & Distance Calculation**: Automatically calculates and displays estimated walking time and distance to the nearest bus terminal or subway station (e.g., `🚶‍♂️ Kävely asemälle / pysäkille: 3 min (250 m)`).
- **Catchable Lines Filter**: Intelligently filters out transit connections that depart too soon, ensuring you only see lines you can walk to and catch in time.
- **24h Clock & Countdown Departure Times**: Displays both relative countdowns and precise 24h clock departure times (e.g., `7 min (14:09)`).
- **Saved Routes & Trip History (Bookmarks)**: Bookmark your favorite journey options with the save button (`🔖`) and view your saved routes and departure timings anytime via the bookmark icon in the top toolbar—even after departure time has passed or when offline.
- **Destination Arrangement / Reordering**: Easily reorder and arrange your saved destinations (Up `▲` / Down `▼` arrows in Settings) to match your personal priority and convenience.
- **Customizable Starting Location**: Easily set your starting position via live GPS, quick Helsinki presets, address search, or manual coordinates.
- **Scrollable Route Legs**: Smooth horizontal scrolling for step-by-step route breakdowns ensuring long route descriptions never get cut off.
- **Material You Dynamic Theming**: Adapts dynamically to your device wallpaper colors and supports Edge-to-Edge display.
- **Home Screen App Widget**: View quick transit summaries and next departure times to your favorite destinations directly on your home screen.
- **Offline / Demo Mode**: Built-in Helsinki demonstration dataset allows the app to work instantly even without an internet connection or API key.

---

## 📱 Technical Requirements & Target Platform

- **Platform**: Android (Smartphones & Foldables)
- **Minimum SDK**: Android 8.0 (API level 26) or higher
- **Recommended SDK**: Android 14+ (API level 34+) / Android 15 / 16
- **Language**: Kotlin 2.1
- **UI Framework**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Kotlin Coroutines + StateFlow
- **Location Services**: Google Play Services (`FusedLocationProviderClient`)
- **Backend API**: HSL Digitransit API v2 (GraphQL / OpenTripPlanner)

---

## 📥 Installation Instructions

1. **Prerequisites**:
   - Install [Android Studio](https://developer.android.com/studio) (Koala, Ladybug, or newer).
   - Ensure you have the Android SDK (API 34/35) and a physical Android device (or emulator running Android 8.0+) configured with developer options enabled.
2. **Clone or Download Project**:
   - Clone this repository or extract the project folder to your local drive.
3. **Open Project in Android Studio**:
   - Open Android Studio, select **Open**, and choose the project root folder:
     `/Users/kodantti/Kehitys/oma/hometravelapp`
4. **Gradle Sync**:
   - Allow Android Studio to automatically sync the project and download required dependencies.
5. **Build and Run**:
   - Connect your Android device via USB (or start an Android Virtual Device).
   - Click the green **Run (▶)** button in Android Studio to build and install `debug` on your device.

---

## ⚙️ Configuration Guide

### 1. HSL Digitransit API Key (Free)
While the app includes a fully functional offline demo mode for central Helsinki, live data requires a free HSL Digitransit API key:
1. Go to the [Digitransit Developer Portal](https://portal-api.digitransit.fi/).
2. Sign up for a free account and subscribe to the free product (*Products ➔ Subscribe*).
3. Copy your **Primary key** from your profile (*Profile ➔ Primary key*).
4. In the app, tap the **Settings (⚙️)** icon in the top toolbar, paste your API key into the API key field, and tap **Tallenna (Save)**.

### 2. Setting Your Starting Position & Destinations
- Tap the **Starting Location (✏️ Aseta)** card on the home screen to choose whether to use live device GPS or a custom manual starting position (e.g. your home address or office).
- Use the **Settings (⚙️)** menu or the **+ Lisää** chip to add, edit, reorder (Up/Down arrows), or remove your favorite destinations.

---

## 📄 Open Source License

This project is open-source software licensed under the **MIT License**. 

You are free to use, modify, distribute, and build upon this software for personal or commercial purposes, provided that the copyright notice and permission notice are included in all copies. See the [LICENSE](LICENSE) file for full details.
