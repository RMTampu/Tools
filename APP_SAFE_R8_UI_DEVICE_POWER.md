# APP_SAFE_R8_UI_DEVICE_POWER.md

> Aturan aktif: pengembangan dan pematangan dilakukan di repo publik utama. Secret, signing, credential, final signed build, Firebase/final runtime test, dan release sensitif tetap di jalur private.

## 1. Status
Korpus aktif R8 — UI, Rendering, WebView, Hardware, Vendor & Power Safety. Target produk Android 11/API30/arm64-v8a.

## 2. Scope
R8 mencakup UI interaction/navigation, rendering, screen/configuration, accessibility, startup/frame performance, WebView, hardware capability, vendor/device variation, Doze/background/power, interruption dan graceful degradation.

## 3. Metode Aktif

### R8-M01 — UI Surface / Flow / Interaction Universe Closure
Inventaris seluruh screen, dialog, menu, overlay, navigation, input, gesture, accessibility action, system handoff, dan recovery state.

### R8-M02 — User-Flow Model & Behavioral UI Testing
Bentuk state/flow graph dan test observable semantics untuk action, navigation, back, validation, repeat action, cancel, dan recovery.

### R8-M03 — Screenshot / Structural / Semantic Visual Oracle
Gunakan screenshot, structural layout, semantic tree, atau kombinasi sesuai stability contract.

### R8-M04 — Screen / Window / Density / Font-Scale Matrix
Tutup equivalence classes size, orientation, density, font scale, system bars, locale/RTL, theme, resize/multi-window yang relevan.

### R8-M05 — Accessibility Closure
Verifikasi label, role/state, focus order, touch target, contrast bila diwajibkan, traversal, announcement, dan alternative interaction.

### R8-M06 — Input / Touch / IME / Gesture Robustness
Uji rapid tap, duplicate action, drag/scroll, cancellation, keyboard, focus loss, back, outside touch, gesture navigation, dan invalid input ordering.

### R8-M07 — Startup & First-Usable-Frame Budget
Ukur startup dan time-to-usable-state dengan environment/budget yang dicatat.

### R8-M08 — Frame/Jank/Rendering Performance Budget
Ukur frame timing pada flow representative dan worst supported path.

### R8-M09 — GPU / Renderer / Visual Stress Challenge
Stress list, animation, clipping, transformation, overlay, text, image, attach/detach, dan rapid navigation.

### R8-M10 — WebView Version / Configuration Contract
Jika WebView ada, deklarasikan setting, storage, mixed content, access, cookie, debugging, safe browsing, navigation, SSL/error contract.

### R8-M11 — WebView Navigation / Error / Process Failure Testing
Uji URL, redirect, offline, TLS/HTTP error, renderer termination, history/back, external intent, dan recreation.

### R8-M12 — JavaScript Bridge / Untrusted Web Content Boundary
JS/native bridge harus membatasi origin/content trust, exposed method, input validation, dan local resource exposure.

### R8-M13 — Hardware Capability Contract
Required/optional hardware feature harus memiliki capability check dan deterministic fallback/rejection.

### R8-M14 — Hardware Lifecycle / Interruption Testing
Uji unavailable/disabled/denied/revoked/busy/disconnect/suspend/resume/error/repeated acquire-release.

### R8-M15 — Vendor / Device Compatibility Equivalence Classes
Bangun matrix faktor API, renderer/GPU, RAM class, screen metrics, hardware feature, WebView, dan OEM policy yang material.

### R8-M16 — Real-Device Witnesses for Hardware/Vendor Claims
Claim yang tidak dapat direpresentasikan emulator membutuhkan real-device/equivalent authoritative evidence.

### R8-M17 — Doze / App Standby / Background Restriction Contract
Definisikan behavior saat background restriction, Doze, standby, scheduling delay, network restriction, dan process pressure.

### R8-M18 — WakeLock / Long-Running Work / Power Budget
Long-running work, wakelock, sensor scan, persistent connection, retry, dan scheduling harus memiliki necessity, duration, release, dan budget.

### R8-M19 — System-Interruption Resilience
Uji background/foreground, lock/unlock, connectivity toggle, permission UI, process pressure, hardware toggle, dan user leaving during operation.

### R8-M20 — Graceful Degradation / Capability Fallback
Optional capability yang hilang harus menghasilkan degraded behavior yang tetap deterministik dan tidak dead/crash/stale.

### R8-M21 — Compatibility / UI Regression Matrix in CI
Automatiskan UI/configuration regression pada Android environment CI yang tersedia dan relevan; catat scope environment.

### R8-M22 — Change-Impact & Mutation Adequacy
Perubahan UI/navigation/WebView/hardware/permission/background/rendering membatalkan evidence terdampak dan harus diuji dengan mutation/negative challenge.

## 4. Development vs Final R8 Status
`R8_DEVELOPMENT_PASS` hanya mencakup evidence publik pada environment yang dicatat. Target-specific/physical claims tetap pending sampai authoritative witness tersedia.

## 5. Fault Model Minimum
```text
UI_ACTION_WRONG_RESULT
NAVIGATION_UI_MISMATCH
DOUBLE_ACTION_SIDE_EFFECT
FOCUS_INPUT_ERROR
IME_GESTURE_ERROR
VISUAL_LAYOUT_REGRESSION
SCREEN_SIZE_CONFIG_ERROR
ACCESSIBILITY_SEMANTIC_ERROR
STARTUP_BUDGET_BREACH
FRAME_JANK_BUDGET_BREACH
GPU_RENDERING_DEVICE_ERROR
WEBVIEW_CONFIG_ERROR
WEBVIEW_NAVIGATION_ERROR
WEBVIEW_RENDERER_FAILURE_UNHANDLED
WEB_NATIVE_BRIDGE_TRUST_ERROR
HARDWARE_CAPABILITY_UNCHECKED
HARDWARE_UNAVAILABLE_CRASH
HARDWARE_LIFECYCLE_LEAK
VENDOR_DEVICE_INCOMPATIBILITY
DOZE_STANDBY_ASSUMPTION_ERROR
BACKGROUND_RESTRICTION_ERROR
WAKELOCK_POWER_LEAK
SYSTEM_INTERRUPTION_STATE_ERROR
MISSING_OPTIONAL_CAPABILITY_FALLBACK
```

## 6. PASS Formula
`APP_SAFE_R8_PASS` hanya jika:
```text
REQUIRED_UI_FLOW_UNKNOWN = 0
SUPPORTED_UI_CONFIG_UNPROVEN = 0
REQUIRED_ACCESSIBILITY_STATE_UNPROVEN = 0
STARTUP_RENDER_BUDGET_UNKNOWN = 0
REQUIRED_WEBVIEW_PATH_UNPROVEN = 0
HARDWARE_CAPABILITY_UNKNOWN = 0
REQUIRED_DEVICE_CLASS_WITHOUT_WITNESS = 0
POWER_RESTRICTION_PATH_UNPROVEN = 0
OPTIONAL_CAPABILITY_WITHOUT_FALLBACK = 0
UI_DEVICE_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```
