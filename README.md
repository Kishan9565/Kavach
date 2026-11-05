# 🛡️ Draivo (com.kishan.kavach) — On-Device AI Phone Agent

**Draivo** is a fully offline, privacy-first, on-device Android phone agent powered exclusively by Google's **Gemma** model (`.litertlm` format running via Google LiteRT-LM).

It operates Android applications via Accessibility Services, processes notifications, handles voice and chat requests in **Hinglish, English, and Hindi**, and executes complex multi-step automations directly on your device.

---

## 🔒 Safety & Privacy Invariants

- **Zero Cloud / No Network**: The Android Manifest deliberately contains **NO `android.permission.INTERNET`**. Draivo never transmits screen contents, text, or data to external servers or cloud models.
- **Code-Level Safety Guard (`Guard.kt`)**: Strict whole-word matching for dangerous actions across English, Hinglish, and Hindi (e.g., *send, pay, bhejo, delete, remove, install, checkout*).
- **Financial & OTP Protection**: Money/banking apps, UPI, OTP fields, passwords, CVVs, and credit card inputs are strictly blocked at the code level.
- **Untrusted Data Fencing**: Text from screens, notifications, contacts, and messages is strictly treated as untrusted data and fenced to prevent prompt injection attacks.
- **Mechanical Policy Matrix (`ToolPolicy.kt`)**: Untrusted origins (notifications, external triggers) are mechanically barred from executing high-risk tools.
- **User Confirmation (`ConfirmationBus`)**: All high-risk tools mandate explicit UI confirmation with a 60-second timeout that defaults to "No / Denied".

---

## ✨ Core Features & Architecture

### 📱 1. Screen Perception & Element ID Tapping
- **Outline Builder (`OutlineBuilder`)**: Translates complex Android accessibility trees into compact 60-row text outlines with contiguous `e0..eN` IDs and normalized `(0-1000)` coordinates.
- **Smart Relocation (`Relocator` & `EidRegistry`)**: 4-tier element tracking ensures accurate tapping even when UI elements shift or scroll.

### 🔄 2. Post-Action Verification & Repeat Guard
- **Fingerprinting (`ScreenDiff` & `awaitSettled`)**: Compares screen state before and after every gesture to verify screen changes, preventing hallucinated success steps.
- **Repeat Guard (`RepeatGuard`)**: Refuses repeated coordinate taps on the same spot within a 20-second window to prevent infinite loops.

### ⌨️ 3. Keyboard-Level Input & Submission
- **A11y Input Adapter (`A11yInput`)**: Uses Android 13+ `AccessibilityInputConnection` to commit text directly through focused editor connections, keeping web forms and native editors responsive.
- **IME Action Submission (`A11ySubmit`)**: Triggers editor-specific IME actions (`Search`, `Go`, `Send`, `Done`) without breaking form flows.

### 🔔 4. Intelligent Notification Triage
- **Notification Gate (`NotificationGate`)**: Filters structural, ongoing, or stale notifications and enforces hourly rate-limiting (max 20/hr).
- **On-Device Triage (`TriagePrompt` & `TriageParser`)**: Gemma evaluates incoming notifications into `TELL`, `ACT`, or `IGNORE` with zero network dependency.

---

## 🛠️ Architecture & Tech Stack

- **Package**: `com.kishan.kavach`
- **Language**: 100% Modern Idiomatic Kotlin
- **UI Framework**: Jetpack Compose Material 3 (MVI Pattern)
- **Architecture**: Clean Architecture + Manual Dependency Injection (`AppContainer`)
- **Database & Storage**: Room DB 2.6.1 + SharedPreferences
- **Background Tasks**: WorkManager 2.10.0
- **AI Runtime**: Google LiteRT-LM 0.10.0 (`.litertlm` Gemma model)
- **Min SDK**: 30 (Android 11) | **Target SDK**: 35 (Android 15)

---

## 🧪 Testing & Verification

Draivo includes a comprehensive pure-Kotlin unit test suite covering domain logic, tool registries, safety guards, relocation, fingerprinting, and triage parsing.

To run unit tests via Gradle:
```bash
./gradlew testDebugUnitTest
```

All **90+ unit tests** pass deterministically without needing an emulator or connected device.

---

## 🚀 Getting Started

1. **Open in Android Studio**: Open the project folder in Android Studio (Ladybug+ recommended).
2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
3. **Import Gemma Model**:
   - Open Draivo -> **Models** screen.
   - Import your local `.litertlm` Gemma model file using the file picker.
4. **Enable Accessibility Service**:
   - Go to System Settings -> Accessibility -> Enable **Draivo Agent** service.
   - (Optional) Enable Notification Access for notification summaries and triage.

---

## 📜 License & Compliance

Draivo is developed strictly following Google Play Policy domains, local privacy laws, and Android security best practices.
