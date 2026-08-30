# APP_SAFE_R8_UI_DEVICE_POWER.md

## 1. Status

Korpus metode aktif untuk **R8 — UI, Rendering, WebView, Hardware, Vendor & Power Safety** dalam framework `APPLICATION_SAFE_100`.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

---

## 2. Scope

R8 menutup:

- UI interaction and navigation behavior at runtime;
- rendering correctness beyond asset correctness;
- screen/window/configuration/device compatibility;
- accessibility behavior;
- startup/frame/jank performance;
- WebView runtime and web-content boundary;
- hardware capability detection and degradation;
- camera/sensor/Bluetooth/NFC-like subsystem interruption;
- vendor/device variation;
- Doze/App Standby/background/power restriction;
- system interruption and resume behavior.

Logic is owned by R1; asset visual correctness by `ASSET_SAFE_100`; lifecycle by R3; resource budgets by R2.

---

## 3. Metode Aktif

### R8-M01 — UI Surface / Flow / Interaction Universe Closure
Inventaris seluruh screen, dialog, sheet, menu, overlay, notification-facing flow, navigation destination, input control, gesture, IME path, accessibility action, system handoff, and user-visible error/recovery state.

### R8-M02 — User-Flow Model & Behavioral UI Testing
Bentuk user-flow/state graph dan automated tests untuk required actions, navigation, back behavior, enabled/disabled state, validation, repeated tap, long press/gesture where relevant, cancellation, and recovery. Test must assert observable semantics, not only element presence.

### R8-M03 — Screenshot / Structural / Semantic Visual Oracle
Untuk required visual states gunakan screenshot golden, structural layout assertions, semantic UI tree assertions, or combination. Exact pixels only where stable contract requires them; otherwise use tolerances/structural oracle without hiding material regressions.

### R8-M04 — Screen / Window / Density / Font-Scale Matrix
Close supported equivalence classes for size, orientation, density, font scale, navigation mode/system bars, locale/RTL, day/night, and resizable/multi-window behavior relevant to target. State preservation during transitions is cross-checked with R3.

### R8-M05 — Accessibility Closure
Verify content descriptions/labels, role/state semantics, focus order, touch target, contrast where contract requires, TalkBack/screen-reader traversal, keyboard/switch-like navigation where supported, dynamic announcements, and no interaction that is only visually discoverable.

### R8-M06 — Input / Touch / IME / Gesture Robustness
Exercise rapid taps, double actions, drag/scroll, pointer cancellation, keyboard show/hide, submit/action keys, focus loss, back press, outside touch, gesture navigation, and invalid/reordered input events. Duplicate destructive actions must be idempotently guarded.

### R8-M07 — Startup & First-Usable-Frame Budget
Measure cold/warm/hot startup as relevant and time to usable state, not merely process creation. Critical initialization must not regress beyond defined budget.

### R8-M08 — Frame/Jank/Rendering Performance Budget
Measure frame timing during representative and worst supported UI flows. Track tail percentiles and regression. Device/emulator measurement limitations must be declared; performance claim requiring hardware characteristics needs representative real device evidence.

### R8-M09 — GPU / Renderer / Visual Stress Challenge
Exercise large lists, animations, clipping, transformations, overlays, text, image-heavy screens, rapid navigation, and repeated attach/detach to reveal renderer-specific artifact, jank, or crash. Vendor/GPU-specific findings enter device fault model.

### R8-M10 — WebView Version / Configuration Contract
If WebView exists, declare required settings: JavaScript, DOM/storage, mixed content, file/content access, cookies, debugging, safe browsing, user agent assumptions, navigation interception, SSL/error behavior, and minimum supported WebView behavior.

### R8-M11 — WebView Navigation / Error / Process Failure Testing
Test valid/invalid URL, redirect, offline, TLS error, HTTP error, renderer crash/termination where observable, history/back, external intent handoff, download/file chooser if used, and recreation. App must not trust page-loaded as proof of correct semantic result.

### R8-M12 — JavaScript Bridge / Untrusted Web Content Boundary
Any JS bridge or native-web interface must have explicit origin/content trust contract, exposed-method minimization, input validation, and no unsafe local resource exposure. Security authority remains R5.

### R8-M13 — Hardware Capability Contract
For every required/optional hardware feature declare manifest requirement where appropriate and runtime capability check. Required feature absent must prevent unsupported use; optional feature absent must follow deterministic degradation/fallback.

### R8-M14 — Hardware Lifecycle / Interruption Testing
For camera/sensor/Bluetooth/NFC/location/audio or similar subsystem: test unavailable, disabled, permission denied/revoked, busy/in-use, connection loss, suspend/resume, device disconnect, error callback, and repeated acquire/release.

### R8-M15 — Vendor / Device Compatibility Equivalence Classes
Build device matrix from factors that can materially affect behavior: API30 implementation/vendor, GPU/rendering stack, RAM class, screen metrics, hardware feature, WebView version, OEM background policy where applicable. One device cannot prove all classes.

### R8-M16 — Real-Device Witnesses for Hardware/Vendor Claims
Emulator evidence is insufficient for physical sensor, GPU timing, Bluetooth/NFC/camera quirks, thermal/power, and OEM behavior where emulation cannot reproduce them. Required class needs real-device or equivalent authoritative lab evidence.

### R8-M17 — Doze / App Standby / Restricted Background Testing
Exercise idle/power modes and user/OEM background restriction states applicable to target. Verify deferred work, alarms/jobs/network assumptions, resumption, deadline semantics, duplicate execution, and user-visible degradation.

### R8-M18 — Power / Wake-Lock / Background Resource Contract
Every wake lock, alarm, periodic job, foreground/background work, sensor scan, and persistent connection needs necessity, duration, release, scheduling and retry budget. Excessive work is R2 resource failure plus R8 platform-power incompatibility.

### R8-M19 — System-Interruption Resilience
Test incoming notification/call-like interruption where feasible, app background/foreground, screen lock/unlock, connectivity toggles, permission UI, process pressure, hardware state toggle, and user leaving during operation. Resume behavior must preserve contract.

### R8-M20 — Graceful Degradation / Capability Fallback
For optional device/web/hardware capability, define degraded behavior that remains functional and does not expose dead controls, infinite spinners, hidden crash path, or stale state.

### R8-M21 — Compatibility / UI Regression Matrix in CI
Automate repeatable UI behavior/screenshot/configuration tests across representative API30 screen/config classes and use physical-device jobs for claims not emulatable. Device matrix changes invalidate evidence.

### R8-M22 — Change-Impact & Mutation Adequacy
Changes to UI toolkit, navigation, screen config, WebView settings, hardware use, permissions, background schedule, vendor workaround, animation/rendering, or device support invalidate relevant proof. Mutations: wrong layout on size class, missing accessibility label, jank budget breach, WebView unsafe setting, hardware capability unchecked, Doze work assumption, fallback removed.

---

## 4. Fault Model Minimum

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

---

## 5. PASS Formula

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
