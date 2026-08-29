# TeeVClean

A modern, privacy-first Android TV 11 cleaning assistant optimized for 10-foot viewing and D-pad navigation.

## MVP experience

- Storage health dashboard with available-space summary
- Quick clean for safe temporary files
- Large-file and media review
- Unused-app and cache guidance
- Device health checks
- Confirmation-first cleanup flow
- Android TV launcher support and landscape layout

The app intentionally guides users to system settings for operations Android does not allow ordinary apps to perform silently, such as clearing another app's cache or uninstalling an app.

## Build

Open this repository in Android Studio with Android SDK 35 installed. Use Gradle 8.11.1 (the included wrapper) with Android Gradle Plugin 8.9.3, then run:

```bash
./gradlew assembleDebug
```

Install the debug APK on an Android TV 11 device or emulator. The app targets API 30 for Android TV 11 compatibility while compiling against the current SDK.
