# TeeVClean 📺✨

[![Android CI](https://github.com/daygle/TeeVClean/actions/workflows/android-ci.yml/badge.svg)](https://github.com/daygle/TeeVClean/actions/workflows/android-ci.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV-green.svg)](https://developer.android.com/tv)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A modern, privacy-first cleaning assistant for Android TV. TeeVClean is optimized for the 10-foot experience, providing a calm and intuitive way to manage your TV's storage and health.

---

## 🌟 Features

- **Storage Health Dashboard**: Used, free and total space at a glance, refreshed whenever the app returns to the foreground and after every clean-up.
- **Cleanup**: Clear TeeVClean's own cache (internal, external, and code cache) in one pass, with a clear report of how much space was freed.
- **Junk Sweep**: Remove leftover temp, log, and thumbnail files plus empty folders from the folders you grant.
- **Large File Review**: Review oversized, stale and installer files (including APKs of apps already installed) from the folders you grant and the top of Downloads, Movies and Pictures, then delete the ones you select after a single confirmation.
- **Duplicates**: Find files in the folders you grant that share an exact size and a matching SHA-256 fingerprint sampled from the start and end of each file; each group keeps its newest copy, and the extras go only after you confirm.
- **Storage Breakdown**: See where space goes - apps and their caches, the system and everything else, and free space - with app attribution shown once usage access is granted.
- **Guided App Cache**: Open an app's system info screen, where its storage can be cleared - the only store-legal way to clear another app's cache on modern Android.
- **Applications**: Sort apps by size and jump straight to an app's system info or uninstall prompt.
- **Device Health**: Monitor storage pressure, network connectivity, system uptime, and hardware details.
- **Clean-up History**: Every manual and scheduled run logged newest first (the last 50), with the categories cleaned, item count, space freed and a marker for runs that were stopped early, plus running totals on the dashboard and a clear button.
- **D-Pad Optimized**: Fully navigable via remote control with clear focus indicators.
- **Privacy First**: Everything stays on the device, and manual deletions are confirmed first. A cleanup schedule you enable also runs unattended, but only for the categories you tick (its own cache is always included).

---

## 🛠 Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3, plus custom D-pad focus routing for the 10-foot experience (plain Compose — there is no Compose for TV dependency)
- **Build System**: Gradle 9.9.2 with **AGP 9.5.2** (Built-in Kotlin Support)
- **Language**: Kotlin 2.4+
- **Architecture**: MVVM — a single `AndroidViewModel` state holder over a repository, exposing Compose `mutableStateOf` state to the screens
- **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for scheduled cleanups

---

## 📐 Architecture & Flow

```mermaid
graph TD
    A[Leanback Launcher] --> B[Overview Dashboard]
    B --> C[Sidebar Navigation]
    C --> D[Cleanup]
    C --> E[Large Files]
    C --> F[Applications]
    C --> G[Device Health]
    C --> K[Settings]

    B --> L[Storage Breakdown]
    B --> M[Clean-up History]
    D --> N[Duplicates]

    D --> H{User Approval}
    H -- Confirm --> I[Clear Cache]
    H -- Cancel --> D
    
    F --> J[System App Info]
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio 2026.2.2 "Rabbit 2" (or newer) — the oldest release that supports AGP 9.5
- Android SDK 37.2
- JDK 21

### Build Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/daygle/TeeVClean.git
   ```
2. Open the project in Android Studio.
3. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

### Installation
TeeVClean runs on **Android TV 7.0 (API 24)** and above, which is the `minSdk = 24` in
`app/build.gradle.kts`. Two features need Android 11 (API 30) or newer: the all-files access
settings page and Android's system-wide "clear all app caches" prompt. On older TVs those options
are hidden and TeeVClean falls back to the folders you grant and each app's own storage settings.
For the best experience, deploy to a physical TV or a TV emulator with landscape orientation.

---

## 📄 License

Copyright 2026 daygle. Licensed under the Apache License, Version 2.0.
