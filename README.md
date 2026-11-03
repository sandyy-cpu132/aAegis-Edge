# Aegis Edge — Secure Android 16 / OriginOS 6 Gesture, Privacy & Utility Vault

**Aegis Edge** is an ultra-lightweight, strictly offline, security-first native Android utility built in Kotlin and Jetpack Compose for personal use on the **iQOO Neo10R (Android 16, OriginOS 6)**.

---

## 1. Non-Negotiable Security Architecture

- **Zero Network Footprint**: `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` are explicitly stripped via `tools:node="remove"` in `AndroidManifest.xml`. Zero Firebase, zero analytics, zero telemetry, zero OkHttp/Retrofit dependencies.
- **Hardware-Backed Encryption at Rest**: All configuration files (`aegis_config_v1.enc`) and autofill profiles (`aegis_autofill_v1.enc`) are stored in private `noBackupFilesDir` encrypted with **Android Keystore 256-bit AES-GCM** (`AES/GCM/NoPadding`, 12-byte IV, 128-bit authentication tag) and distinct Associated Authenticated Data (`AAD`) labels.
- **StrongBox & TEE Honesty**: Automatically requests `StrongBox` hardware key backing when supported by the device (`PackageManager.FEATURE_STRONGBOX_KEYSTORE`), falls back to Trusted Execution Environment (`TEE`), and inspects `KeyInfo.securityLevel` at runtime to report the exact hardware backing level without ever falsely claiming software keys are hardware-backed.
- **Cloud & Device-Transfer Backup Disabled**: `android:allowBackup="false"`, `backup_rules.xml`, and `data_extraction_rules.xml` exclude all storage domains.
- **Protected System Services**:
  - `AegisAccessibilityService` requires `android.permission.BIND_ACCESSIBILITY_SERVICE`, sets `canRetrieveWindowContent="false"`, `canRequestTouchExplorationMode="false"`, and listens only to `typeWindowStateChanged`.
  - `AegisAutofillService` requires `android.permission.BIND_AUTOFILL_SERVICE` and processes only genuine system `onFillRequest` calls without harvesting form data in `onSaveRequest`.
  - `BiometricAuthActivity` is strictly `android:exported="false"` and verifies an in-memory one-time UUID session token.
- **Sensitive UI Protection**: Applies `View.ACCESSIBILITY_DATA_SENSITIVE_YES` on Android 14+ (API 34+) to the Encrypted Autofill and Security Audit screens.
- **Documented Security Boundary**: Aegis Edge resists ordinary third-party Android applications attempting to read private data, inject commands, or abuse exported components. No user-space Android application can defeat a compromised operating system kernel, unlocked/rooted bootloader attacker, or privileged system malware.

---

## 2. Implemented Features & Verification Classification

| Module | Status | Details |
| :--- | :--- | :--- |
| **Core Security & AES-256-GCM Vault** | **Implemented & Build-Verified** | Keystore AES-256-GCM + AAD binding, PII-redacting `SecureLogger`, cryptographic wipe, and Robolectric manifest/tamper tests passing. |
| **Edge Gesture Engine** | **Implemented & Build-Verified (Requires Physical Device Verification)** | Independent Left & Right `TYPE_ACCESSIBILITY_OVERLAY` vertical trigger strips, Short Swipe, Long Swipe, Swipe-and-Hold, strict vertical angle filtering (preserving horizontal Android back navigation), 4-tier context priority, per-app exclusions, landscape auto-disable, and live interactive gesture preview canvas. |
| **Privacy Shield (Mode A & Mode B)** | **Implemented & Build-Verified (Requires Physical Device Verification)** | **Mode A**: Native Android Lock (`GLOBAL_ACTION_LOCK_SCREEN`). **Mode B**: Full-screen Frozen Snapshot via `AccessibilityService.takeScreenshot()` with a strict watchdog timer and **mandatory immediate fallback to Native Android Lock** if capture fails (`FLAG_SECURE`), bitmap is invalid, or overlay attachment fails. Bidirectional upper-corner horizontal exit gesture + `BiometricPrompt` verification. |
| **Feedback Engine** | **Implemented & Build-Verified** | Per-action Haptics, temporary punch-hole/notch ring glow (respecting `DisplayCutout` geometry), temporary edge lighting pulse, and Silent mode. Zero permanent overlay when idle. |
| **Local Clipboard & URL Cleaner** | **Implemented & Build-Verified** | Strips 35+ tracking parameters (`utm_*`, `fbclid`, `gclid`, `igshid`, `si`, `msclkid`, etc.), custom query keys, and scheme-validated regex rules. Supports System Share Sheet (`ACTION_SEND`) and one-tap Paste & Clean without background clipboard polling. |
| **Encrypted Autofill Assistant** | **Implemented & Build-Verified (Requires Physical Device Verification)** | Native `AutofillService` supporting First/Last Name, Email, Phone, Address, Postal Code, and custom field keys, protected behind biometric unlock in the app. |
| **Screenshot Trash Utility** | **Omitted / Deferred by Design (Part 10 Rule)** | OriginOS 6 Gallery's proprietary Recently Deleted sync with `MediaStore.createTrashRequest()` cannot be guaranteed without broad media permissions (`READ_MEDIA_IMAGES`) and background `ContentObserver` polling. Omitted per project rules. |

---

## 3. Phone-Only Build & APK Delivery Instructions (Termux & GitHub Actions)

You do not need a PC or Android Studio.

### Option A — Build & Download APK via GitHub Actions on Your Phone Browser
1. Push this repository to your private GitHub repository from Termux:
   ```bash
   pkg update && pkg install git -y
   git init
   git add .
   git commit -m "Complete Aegis Edge implementation"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
2. Open your repository in your phone browser → tap **Actions** → select **Aegis Edge Android CI & APK Delivery**.
3. When the workflow finishes (green checkmark), scroll down to **Artifacts** and tap **`aegis-edge-debug-apk`** to download the installable `.apk` to your iQOO Neo10R.
4. *(Optional Release Signing)*: To produce signed release APKs in GitHub Actions, go to **Settings → Secrets and variables → Actions** and add:
   - `KEYSTORE_BASE64` (`base64 -w 0 your-release-key.jks` in Termux)
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`

---

## 4. iQOO Neo10R (Android 16 / OriginOS 6) Device Setup Instructions

After installing the APK on your iQOO Neo10R:

1. **Allow Restricted Accessibility Setting (Android 16 Requirement for Sideloaded APKs)**:
   - Open Android **Settings → Apps → See all apps → Aegis Edge**.
   - Tap the top-right **`⋮`** menu → tap **Allow restricted settings** and verify with your fingerprint.
2. **Enable Aegis Accessibility Engine**:
   - Open Aegis Edge → tap **Enable** next to *Accessibility Engine* on the Dashboard (or go to **Settings → Accessibility → Downloaded Services → Aegis Edge** and toggle it **On**).
3. **Configure OriginOS 6 Background Persistence**:
   - **Settings → Battery → Background Power Consumption Management → Aegis Edge** → select **High Background Power Usage** (so OriginOS does not kill the Accessibility overlay during deep idle).
   - **Settings → Apps → App Manager → Aegis Edge → Permissions → Autostart** → **Enable**.
4. **Enable Native Encrypted Autofill (Optional)**:
   - Open Aegis Edge → tap **Configure** next to *Native Autofill Service* on the Dashboard and select **Aegis Encrypted Autofill**.

---

## 5. Real-Device Verification & 24-Hour Battery Test Checklist

### Gesture & Privacy Shield Checklist on iQOO Neo10R
- [ ] **Vertical Left/Right Short Swipe, Long Swipe, Swipe & Hold**: Verify each triggers its assigned action without interfering with normal inward horizontal Android Back gestures.
- [ ] **Excluded App Priority**: Add an app (e.g., Camera or a game) to *Per-App Exclusions*, open that app, and confirm edge overlays are completely suspended.
- [ ] **Privacy Shield Mode B (Normal App)**: Trigger *Privacy Shield: Frozen Snapshot* on a normal screen; confirm underlying taps and scrolling are blocked.
- [ ] **Privacy Shield Mandatory Fallback (`FLAG_SECURE` Window)**: Trigger *Privacy Shield: Frozen Snapshot* on a banking/incognito window with `FLAG_SECURE`; confirm the phone immediately locks via the real Android lock screen without showing a black screen or blur.
- [ ] **Privacy Shield Upper-Corner Exit**: While frozen, swipe horizontally from top-left clock area to top-right (or reverse); confirm `BiometricPrompt` requests your fingerprint and only unfreezes after authentication succeeds.

### Reproducible 24-Hour Battery Measurement Procedure
1. Charge your iQOO Neo10R to >= 90% and clear Android battery stats.
2. Leave **Aegis Edge** enabled with your normal daily Edge Gestures and Feedback configuration for 24 hours.
3. Open **Settings → Battery → Battery Usage by App → Aegis Edge** and record:
   - Total 24-hour battery percentage attributed to `Aegis Edge` (Target: `0–0.5%` idle / `1–2%` active daily use).
   - Background active time (should remain near zero when screen is off).

---

## 6. Known Platform Limitations

1. **System Notification Shade / Quick Settings / Home Gesture Overlays**: On Android 16 / OriginOS 6, `TYPE_ACCESSIBILITY_OVERLAY` blocks all application touches, scrolling, and `KEYCODE_BACK`, but the system window manager reserves top-edge status-bar swipe-down and bottom system home gesture for the OS shell. If unconditional system-UI locking is required when handing the phone to an untrusted person, use **Mode A (Real Android Lock Screen)**.
2. **Android 10–16 Background Clipboard Access**: Non-IME apps cannot silently read the clipboard in the background. Aegis Edge provides instant URL cleaning via the Android **Share Sheet (`Clean URL (Aegis)`)**, **Edge Gesture shortcut**, and **Paste & Clean**.
3. **Biometric Class 3 Selection**: Android's `BiometricPrompt` selects authenticators by security class (`BIOMETRIC_STRONG` = Class 3) rather than hardware type enum. On the iQOO Neo10R, the under-display fingerprint sensor is the Class 3 biometric authenticator.
4. **Third-Party Autofill Metadata**: Apps or custom WebViews that omit standard Android `AutofillHints` and view IDs may not trigger system `onFillRequest` callbacks.
