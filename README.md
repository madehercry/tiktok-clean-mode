# TikTok Clean Mode 

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen)
![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

> **An experimental Android tool that activates TikTok's built-in "Clear Display" mode
> with a single tap — no root required.**

---

## ⚠️ Disclaimer

> **This project is for educational purposes only.**  
> It is not affiliated with, endorsed by, or associated with TikTok, ByteDance, or any
> of their subsidiaries.  
> Use this tool responsibly and in accordance with TikTok's Terms of Service.  
> The authors accept no liability for any consequences of using this software.

---

## What It Does

TikTok's video player includes a hidden "Clear Display" mode that hides the UI overlays
(comments, like buttons, share icons, sound attribution, etc.) and shows just the video.
Normally, reaching it requires:

1. Long-pressing the video
2. Waiting for a context menu to pop up
3. Finding and tapping "Clear display"

**TikTok Clean Mode** automates all three steps with one tap on a floating button that
sits on top of the TikTok app.

```
User taps FAB
      │
      ▼
AccessibilityService dispatches a synthetic long-press
gesture at the centre of the screen
      │
      ▼
TikTok's context menu appears
      │
      ▼
Service traverses the AccessibilityNodeInfo tree to find
the "Clear display" button (any supported language)
      │
      ▼
ACTION_CLICK is performed on the node → Clean mode ✓
```

---

## Features

-  **Zero-root** — uses only public Android APIs
-  **Multilingual** — recognises the "Clear display" button in 10+ languages
-  **Targeted** — Accessibility Service listens only to TikTok packages (no battery drain)
-  **Draggable FAB** — the floating button snaps to either screen edge; stays out of the way
-  **Live status** — setup screen updates permission indicators in real time
-  **No third-party SDKs** — only `androidx.core`, `appcompat`, and `material`

---

## Supported TikTok Packages

| Package name | Variant |
|---|---|
| `com.zhiliaoapp.musically` | TikTok — global / US |
| `com.ss.android.ugc.trill` | TikTok — some markets |
| `com.ss.android.ugc.aweme` | Douyin — China |

---

## How It Works (Technical Details)

### Permissions required

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw our floating button over other apps |
| `BIND_ACCESSIBILITY_SERVICE` | Dispatch gestures and read the view hierarchy |

### Component overview

```
com.example.tiktokcleanmode/
├── MainActivity.kt          — Setup screen; guides user through permissions
├── TikTokCleanService.kt    — AccessibilityService; FAB + gesture injection + node search
└── PermissionHelper.kt      — Stateless utility for permission state checks
```

### Node search strategy

After the long-press gesture completes, the service performs a depth-first traversal of the
`AccessibilityNodeInfo` tree, matching nodes by:

1. `node.text` — visible label text (case-insensitive, partial match)
2. `node.contentDescription` — accessibility label for icon-only buttons
3. `node.viewIdResourceName` — heuristic match on resource-ID suffixes like
   `clear_screen`, `clean_mode`, `clear_display`

---

## Build Instructions

### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or later |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | API 34 |
| Gradle | 8.6 (wrapper included) |

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/tiktok-clean-mode.git
cd tiktok-clean-mode

# 2. Open in Android Studio
#    File → Open → select the project folder

# 3. Let Gradle sync

# 4. Build a debug APK
./gradlew assembleDebug

# 5. Install on a connected device / emulator
./gradlew installDebug
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Granting Permissions

After installing, open the **TikTok Clean Mode** app. You will see two setup cards:

### Step 1 — Draw Over Other Apps

1. Tap **Grant Permission** on the first card.
2. You are taken to **Settings › Apps › TikTok Clean Mode**.
3. Enable **Allow display over other apps**.
4. Press Back — the card's status icon turns green ✓.

### Step 2 — Accessibility Service

1. Tap **Grant Permission** on the second card.
2. You are taken to **Settings › Accessibility**.
3. Scroll to find **Downloaded apps** (or **Installed services**).
4. Tap **TikTok Clean Mode Service** → **Use TikTok Clean Mode**.
5. Confirm the dialog by tapping **Allow**.
6. Press Back — the card's status icon turns green ✓.

> **Note:** Some OEM ROMs (MIUI, One UI, ColorOS) place the Accessibility section
> in a non-standard location. Try **Settings › Special Functions** or use the
> system-wide search for "Accessibility".

### Using the floating button

1. Open **TikTok** and start playing any video.
2. The semi-transparent eye button appears on the left edge of the screen.
3. Tap it once — the service will long-press the video and click "Clear display".
4. Drag it to either edge if it obscures the video.
5. Tap again to re-enable UI (same gesture cycle).

---

## Limitations & Known Issues

- **TikTok updates may rename the "Clear display" button** or change its position in
  the node tree. If the button is not found, open an issue with your TikTok version
  and region — a keyword can be added quickly.
- **Emulators**: `dispatchGesture` does not work reliably on AVD instances; test on a
  physical device.
- **Gesture timing**: On very slow devices the 600 ms post-gesture delay may not be
  long enough for the context menu to render. The 4-second event-driven timeout is
  a fallback.
- **Package scope**: The Accessibility Service targets specific package names. Side-loaded
  or region-specific APKs with different package names will not be detected automatically.

---

## Project Structure

```
tiktok-clean-mode/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/tiktokcleanmode/
│       │   ├── MainActivity.kt
│       │   ├── TikTokCleanService.kt
│       │   └── PermissionHelper.kt
│       └── res/
│           ├── drawable/         ← Vector icons + shape backgrounds
│           ├── layout/           ← activity_main.xml, layout_fab.xml
│           ├── values/           ← strings, colors, themes
│           └── xml/              ← accessibility_service_config.xml
├── build.gradle.kts              ← Root build script
├── settings.gradle.kts
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

---

## Contributing

Contributions are welcome! Here's how to get involved:

1. **Fork** this repository.
2. **Create a branch**: `git checkout -b feat/your-feature-name`
3. **Commit your changes** with a clear message following
   [Conventional Commits](https://www.conventionalcommits.org/).
4. **Push** to your fork and open a **Pull Request**.

### Areas where help is needed

- [ ] Adding translations for more languages (see `CLEAR_DISPLAY_KEYWORDS` in
      `TikTokCleanService.kt`)
- [ ] Testing on additional TikTok APK versions and documenting which node IDs are used
- [ ] Adding a toggle to auto-dismiss the FAB when clean mode is active
- [ ] Unit tests for `PermissionHelper` and the node-search logic
- [ ] Dark-mode launcher icon

### Code style

This project follows the
[Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
All public declarations must have KDoc comments.

---

## License

```
MIT License

Copyright (c) 2024 TikTok Clean Mode Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

*Made with ❤️ and Kotlin.*
