# ☀️ DessMonitor SmartESS

A modern, high-performance Android application designed for real-time monitoring and control of solar inverters. Built with Jetpack Compose, this app provides a premium user experience with advanced features like automated rules, detailed history logging, and instant settings management.

---

## ✨ Features

- **🔄 Live Synchronization**: Real-time energy flow dashboard showing PV production, Grid status, Load usage, and Battery state.
- **⚡ Advanced Telemetry**: Instant access to detailed device data points including voltages, currents, frequencies, and SOC.
- **📱 Modern Glass UI**: A premium interface featuring a "Frosted Glass" navigation bar with real-time backdrop blur (Haze).
- **🔋 Intelligent Connectivity**: Automatic online/offline status detection based on server telemetry and summary endpoints.
- **🔔 Smart Alarms**: Real-time alarm monitoring with persistent storage and historical warning logs.

---

## 📊 Monitoring & History

- **📉 Interactive Charts**: Visualize your energy trends with custom sensors and configurable date ranges.
- **📋 Excel-like History**: View detailed historical logs in a clean, banded-row table format.
- **⏳ Chronological Sorting**: Latest logs are always shown at the top for immediate access.
- **📁 CSV Export**: Export your inverter's historical data directly to CSV files for external analysis or record-keeping.

---

## ⚙️ Inverter Settings

- **🚀 Instant Load**: Settings are cached locally for near-instant access upon opening, even without an active network.
- **🗂 Smart Categorization**: Settings are logically grouped into **System**, **PV / Solar**, **Output**, and **Battery** for intuitive control.
- **✍️ Easy Controls**: Change inverter parameters (voltages, priorities, display modes) with pre-filled inputs and highlighted active selections.
- **📡 Background Sync**: Enter any settings category to automatically refresh its live values from the server.

---

## 🤖 Automations

Define custom rules to make your inverter smarter:
- **Condition-based Actions**: Automatically change inverter settings (e.g., switch to Grid mode if Battery SOC is low).
- **Mobile Notifications**: Receive instant alerts when specific conditions are met (e.g., PV power exceeds a threshold).
- **Persistent Logic**: Rules are evaluated in the background to ensure your system reacts even when the app is closed.

---

## 🎨 Personalization

- **🌓 Theme Support**: Seamlessly switches between Light and Dark modes, with an option to follow system settings.
- **🌈 Custom Palettes**: Personalize your charts with multiple predefined color palettes or create your own custom theme.
- **🏷 Device Aliasing**: Give your inverters custom names and display icons for easy identification.

---

## 🚀 Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) for a modern, declarative UI.
- **DI**: [Koin](https://insert-koin.io/) for lightweight dependency injection.
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/) with custom SHA-1 signature security.
- **Persistence**: [Room](https://developer.android.com/training/data-storage/room) for local database and [SharedPreferences](https://developer.android.com/reference/android/content/SharedPreferences) for caching.
- **Visuals**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) for analytics and [Haze](https://github.com/chrisbanes/haze) for glass blur effects.

---

## 🛠 Getting Started

1. **Clone the Repo**: `git clone https://github.com/AbShaheen184/Dessmonitor_Inverter_App.git`
2. **Build**: Run `./gradlew app:assembleDebug` or open in Android Studio.
3. **Login**: Use your existing EyBond/DessMonitor credentials to start monitoring.

---

*Note: This app is optimized for Axpert, EASUN, SRNE, Must, and other rebranding inverters using the PI18 protocol.*
