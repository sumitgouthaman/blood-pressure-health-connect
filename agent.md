# Blood Pressure Tracker - Agent Context Guide

Welcome! This document provides the necessary context, architecture outline, rules, and workflows for AI agents (and human developers) working on the **Blood Pressure Tracker** Android application.

## 📖 Project Overview
The Blood Pressure Tracker is a fast, privacy-focused, offline-first Android application for logging and tracking blood pressure readings.
- **Privacy Policy / Core Rule**: The application does **not** make network requests or use cloud APIs. All blood pressure data is stored directly and exclusively via Android's **Health Connect API** on-device.
- **AI BP Scanning**: It contains a feature powered by **ML Kit Google GenAI Prompt SDK (Gemini Nano)** to scan photos of blood pressure monitors taken via the camera, extract systolic and diastolic numbers, and pre-fill the manual entry form.

---

## 🛠️ Technology Stack
- **Language**: Kotlin
- **Build System**: Gradle Kotlin DSL (`kts`)
- **Android SDK**: Min SDK 26, Target SDK 36
- **UI Framework**: Jetpack Compose (Material 3)
- **Navigation**: Jetpack Navigation 3 (`androidx.navigation3`)
- **Database/Storage**: Android Health Connect API (`androidx.health.connect`)
- **On-Device AI**: Google ML Kit GenAI Prompt SDK (`com.google.mlkit:genai-prompt`)
- **JSON Serialization**: Kotlinx Serialization

---

## 📂 Project Structure & Key Files

The project follows a standard modern Android project layout. Key files and packages include:

```
app/src/main/
├── AndroidManifest.xml                        # Permission configurations, activities, and providers
├── java/com/sumitgouthaman/bloodpressuretracker/
│   ├── MainActivity.kt                       # Entry activity, sets theme and navigation
│   ├── Navigation.kt                         # App routing using Navigation 3
│   ├── NavigationKeys.kt                     # Serializable NavKey definitions for routes
│   │
│   ├── data/
│   │   ├── HealthConnectManager.kt           # Encapsulates all Health Connect read/write logic
│   │   └── DebugLogManager.kt                # In-memory thread-safe logger for Gemini Nano diagnostics
│   │
│   ├── theme/
│   │   ├── Color.kt, Theme.kt, Type.kt       # Material 3 colors, shapes, and typography
│   │
│   └── ui/
│       ├── debug/
│       │   └── DebugMenuScreen.kt            # Debug logger screen for viewing model checks, requests, and errors
│       │
│       └── ui/main/
│           ├── MainScreen.kt                 # Dashboard, input form, and trend graph UI
│           └── MainScreenViewModel.kt        # Presentation logic, Health Connect state, and Gemini Nano scanning
```

---

## 🏗️ Architecture & Component Details

### 1. Navigation (Navigation 3)
We use the newer **Navigation 3** library (`androidx.navigation3`).
- Screen keys are defined as `@Serializable data object`s implementing `NavKey` in [NavigationKeys.kt](file:///Users/sumitgt/Projects/blood-pressure-health-connect/app/src/main/java/com/sumitgouthaman/bloodpressuretracker/NavigationKeys.kt).
- Router navigation layout is configured inside `MainNavigation()` in [Navigation.kt](file:///Users/sumitgt/Projects/blood-pressure-health-connect/app/src/main/java/com/sumitgouthaman/bloodpressuretracker/Navigation.kt) using `rememberNavBackStack`, `NavDisplay`, and `entryProvider`.

### 2. Storage & Permissions (Health Connect)
All blood pressure readings are read from and written to **Health Connect**.
- Managed by `HealthConnectManager` ([HealthConnectManager.kt](file:///Users/sumitgt/Projects/blood-pressure-health-connect/app/src/main/java/com/sumitgouthaman/bloodpressuretracker/data/HealthConnectManager.kt)).
- Mandatory permissions: `READ_BLOOD_PRESSURE` and `WRITE_BLOOD_PRESSURE`.
- In Android 14+ (API 34+), Health Connect is integrated directly into the system settings. For pre-Android 14 devices, the app handles onboarding and permission rationale redirects as registered in `AndroidManifest.xml` via activity aliases.
- Readings include systolic and diastolic pressures (stored as `Pressure` in mmHg), body posture, measurement location, and timestamp (`Instant`).

### 3. Trend Graph (`BloodPressureChart`)
- Rendered using Compose `Canvas` in [MainScreen.kt](file:///Users/sumitgt/Projects/blood-pressure-health-connect/app/src/main/java/com/sumitgouthaman/bloodpressuretracker/ui/main/MainScreen.kt).
- Draws systolic (Primary theme color) and diastolic (Tertiary theme color) trend lines.
- Fills gradient paths beneath both curves.
- Dynamically scales Y-axis grid labels according to min/max BP values in the filtered time range.

### 4. On-Device AI Scanner (Gemini Nano via ML Kit)
- Implemented in `MainScreenViewModel.scanBloodPressure(bitmap)` ([MainScreenViewModel.kt](file:///Users/sumitgt/Projects/blood-pressure-health-connect/app/src/main/java/com/sumitgouthaman/bloodpressuretracker/ui/main/MainScreenViewModel.kt)).
- Uses **ML Kit GenAI Prompt SDK** to check on-device model status (`generativeModel.checkStatus()`), automatically download model components if downloadable, and query Gemini Nano with a multimodal input (image bitmap + prompt).
- The prompt instructs the model to return a structured JSON response like `{"systolic": 120, "diastolic": 80}` or `{"error": "..."}`.
- Outputs are parsed using robust regular expressions (`systolicRegex`, `diastolicRegex`) to ensure compatibility.

### 5. Diagnostics & Debugging (`DebugLogManager`)
- An in-memory logger `DebugLogManager` ([DebugLogManager.kt](file:///Users/sumitgt/Projects/blood-pressure-health-connect/app/src/main/java/com/sumitgouthaman/bloodpressuretracker/data/DebugLogManager.kt)) captures model checks, inference history (prompts, raw text output, errors), and system-level exceptions.
- Accessible via the "AI Debug Logs" menu in the toolbar of the dashboard screen, leading to `DebugMenuScreen` ([DebugMenuScreen.kt](file:///Users/sumitgt/Projects/blood-pressure-health-connect/app/src/main/java/com/sumitgouthaman/bloodpressuretracker/ui/debug/DebugMenuScreen.kt)).

---

## ⚠️ Important Rules for AI Agents

1. **Strict Offline & On-Device Rule**: Do **NOT** add any network client dependencies (like OkHttp, Retrofit, Ktor), analytic SDKs, or cloud LLM APIs. The app must remain 100% offline-first and privacy-focused.
2. **Health Connect Data Formatting**: When writing records, always retrieve the system's local timezone offset for the record's instant (e.g. `ZoneOffset.systemDefault().rules.getOffset(time)`).
3. **GenAI Status & Logging**: Whenever working with generative AI features, always call `DebugLogManager` to log status checks, inference results, and errors. This is crucial for maintaining readability in the AI Debug logs.
4. **Haptic Feedback & Interactions**: Maintain smooth UI interactions. For example, long pressing to enter multi-select mode triggers haptic feedback (`HapticFeedbackType.LongPress`).
5. **No Placeholders**: When modifying the UI, write complete layouts. If adding screens, ensure proper Navigation 3 keys and entry configurations.

---

## 🏃 Building & Running
1. Open the project in Android Studio.
2. Run gradle sync.
3. Ensure **Health Connect** is installed on the target device or emulator (built-in on Android 14+, downloadable from Play Store for older Android OS versions).
4. Run the `:app` configuration.
