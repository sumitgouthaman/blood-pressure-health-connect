# Blood Pressure Tracker

A fast, privacy-focused Android application for logging blood pressure readings. Built to integrate directly and exclusively with Android's Health Connect API.

## What it does

Blood Pressure Tracker allows you to quickly log your systolic and diastolic measurements along with contextual data (body posture and measurement location). It provides a glanceable dashboard of your recent history. 

## Privacy

This app requests explicit read/write permissions to Health Connect. It does not contain any networking code or tracking SDKs. Data never leaves your device.

## Building

Standard Android Gradle project. Open in Android Studio, sync, and run.

### Requirements
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 36
- To test the app on an emulator or physical device, the **Health Connect** app must be installed and initialized. Health Connect is built-in on newer Android versions, but may require a manual download from the Play Store on older devices.
