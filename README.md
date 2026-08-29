# TeeVClean 📺✨

[![Android CI](https://github.com/daygle/TeeVClean/actions/workflows/android-ci.yml/badge.svg)](https://github.com/daygle/TeeVClean/actions/workflows/android-ci.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV-green.svg)](https://developer.android.com/tv)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A modern, privacy-first cleaning assistant for Android TV. TeeVClean is optimized for the 10-foot experience, providing a calm and intuitive way to manage your TV's storage and health.

---

## 🌟 Features

- **Storage Health Dashboard**: Real-time summary of used and available space.
- **Safe Cleanup**: One-tap removal of TeeVClean's own cache (internal, external, and code cache), with a clear report of how much space was freed.
- **Junk Sweep**: Remove leftover temp, log, and thumbnail files plus empty folders from the folders you grant.
- **Large File Review**: Scan folders you grant for oversized or stale files and delete them, with explicit per-file confirmation.
- **Guided App Cache**: Deep-link into each app's storage page — the only store-legal way to clear another app's cache on modern Android.
- **App Review**: Sort apps by size and quickly access system info for management.
- **Device Health**: Monitor network connectivity, system uptime, and hardware details.
- **D-Pad Optimized**: Fully navigable via remote control with clear focus indicators.
- **Privacy First**: No background deletions. Every action requires your explicit approval.

---

## 🛠 Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Compose for TV](https://developer.android.com/tv/compose)
- **Build System**: Gradle 8.11+ with **AGP 9.3.2** (Built-in Kotlin Support)
- **Language**: Kotlin 2.4+
- **Architecture**: MVI-inspired UI state management
- **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for scheduled cleanups

---

## 📐 Architecture & Flow

```mermaid
graph TD
    A[Leanback Launcher] --> B[Overview Dashboard]
    B --> C[Sidebar Navigation]
    C --> D[Safe Cleanup]
    C --> E[Large Files]
    C --> F[App Review]
    C --> G[Device Health]
    
    D --> H{User Approval}
    H -- Confirm --> I[Clear Cache]
    H -- Cancel --> D
    
    F --> J[System App Info]
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 35
- Java 21

### Build Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/daygle/TeeVClean.git
   ```
2. Open the project in Android Studio.
3. Build and install the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

### Installation
TeeVClean is designed for **Android TV 11 (API 30)** and above. For the best experience, deploy to a physical TV or a TV emulator with landscape orientation.

---

## 📄 License

Copyright 2026 daygle. Licensed under the Apache License, Version 2.0.
