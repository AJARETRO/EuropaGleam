# EuropaGleam

[![Release](https://img.shields.io/github/v/release/AJARETRO/EuropaGleam?style=for-the-badge&color=blue)](https://github.com/AJARETRO/EuropaGleam/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B%20(Target%20API%2031)-green?style=for-the-badge)](https://github.com/AJARETRO/EuropaGleam)
[![Package](https://img.shields.io/badge/Package-dev.ajaretro.europagleam-orange?style=for-the-badge)](https://github.com/AJARETRO/EuropaGleam)
[![License](https://img.shields.io/badge/License-GPLv3-blueviolet?style=for-the-badge)](LICENSE)

**EuropaGleam** is a cutting-edge, ultra-low-latency [Sunshine](https://github.com/LizardByte/Sunshine) / GameStream / [Apollo](https://github.com/ClassicOldSong/apollo) client for Android, engineered by **AJARETRO**. 

Designed for enthusiasts and competitive gamers, EuropaGleam brings native wireless DualSense capabilities, real-time multi-metric performance monitoring, universal controller button remapping, floating on-screen macro controls, and zero-black-screen clean session recovery to your Android device.

---

## 🌟 Key Features

### 📊 Real-Time Sleek Performance Top Bar
A modern, single-line translucent frosted pill anchored at the top of the stream providing real-time telemetry with zero distraction:
- **Incoming FPS**: Live render and stream framerate.
- **Ping (ms)**: Round-trip network latency to the host.
- **Host Encode Time (ms)**: Remote host PC GPU encoding duration.
- **Receiver Decode Time (ms)**: Local Android device hardware video decode latency.
- **Bitrate Monitor (Mbps)**: Real-time network throughput and stream bandwidth.
- **Device CPU Usage (%)**: Real-time multi-core processor utilization.
- **Device GPU Usage (%)**: Real-time graphics processor utilization across **Qualcomm Adreno**, **ARM Mali**, **Samsung Exynos**, and **MediaTek Dimensity** chipsets.
- **Live In-Game Customization**: Toggle individual metrics on the fly from the in-game quick menu (`Customize Overlay Stats...`) or via Settings.

### 🔄 Zero-Black-Screen Clean Auto-Reconnect
When Wi-Fi drops or network handoffs occur, traditional clients frequently desynchronize with host encoders, causing persistent black screens or video decoder crash loops.
- EuropaGleam cleanly stops the connection and transmits an instantaneous host-side session teardown (`quitApp`) before rejoining.
- Sunshine / Apollo cleanly resets its virtual display and encoder pipelines, delivering a fresh, pristine stream instantly with zero manual intervention.

### 🎮 Universal Controller Remapping & Button Flipping
Custom layout flipping and axis controls working across **all physical controllers** (Bluetooth, USB, Xbox, DualSense, Switch Pro, 8BitDo) and virtual controls:
- **Swap A / B**: Instant toggle between standard Xbox and Nintendo/PlayStation layouts.
- **Swap X / Y**: Instant toggle for top and left face buttons.
- **Swap Bumpers (LB / RB)**: Swap L1 and R1 bumpers.
- **Swap Triggers (LT / RT)**: Swap L2 and R2 analog triggers.
- **Swap Sticks**: Swap Left Stick (movement) and Right Stick (camera), including L3 and R3 stick clicks.
- **Invert Stick Y-Axes**: Independent vertical axis inversion for Left Stick and Right Stick.
- Configurable in Settings and directly inside running games via the in-game menu (`Controller Remapping & Macros...`).

### ⚡ On-Screen Floating Macro Dock (Mantis-Style Overlay)
Never put your physical controller down to hit a keyboard shortcut. EuropaGleam features a floating, draggable, collapsible Macro Dock that works **simultaneously with physical gamepads**:
- **ESC**: Instant PC Escape key.
- **WIN**: Windows Start key for launcher access.
- **TAB & ALT+TAB**: Seamless Windows task switcher.
- **TASKMGR**: Ctrl+Shift+Esc instant shortcut.
- **F5 / F9**: Quicksave and Quickload shortcuts for PC games.
- **L3 / R3**: Touch stick-click buttons (ideal for controllers with stiff physical stick clicks).
- **GUIDE**: Xbox / PlayStation Home menu trigger.
- **TURBO**: Built-in rapid-fire mode toggle (rapidly pulses inputs for automated actions).
- **REMAP & MENU**: Instant access to live controller remapping and the EuropaGleam menu.
- **Draggable & Persistent**: Move the handle anywhere on screen; position is automatically remembered across sessions.

### 🖱️ Natural Trackpad Mouse by Default
- Default **Trackpad Natural** mode provides intuitive two-finger scrolling, pinch zooming, and precision cursor movement.
- Configurable mouse modes (Trackpad Natural, Trackpad Classic, Direct Touch, Mouse Emulation) with customizable pointer speed and acceleration.

### 🎮 Full Native DualSense Wireless & Wired Streaming
EuropaGleam provides native end-to-end DualSense features:
- **Adaptive Triggers**: Resistance, vibration, and weapon feedback.
- **Native HD Haptics**: High-definition dual-actuator tactile feedback.
- **Direct Bluetooth (Android 12+)**: Full DualSense functionality over wireless Bluetooth without external dongles.
- **Motion & Gyroscope**: Full 6-axis gyro steering and motion aiming.
- **Controller Audio & Headset Detection**: Audio routed to the DualSense controller speaker or 3.5mm headset jack with automatic fallback.
- **Mute Button**: Physical mute button controls client microphone forwarding.

### 🖼️ Picture-in-Picture (PiP) Floating Stream
Multi-task effortlessly without losing sight of your PC game:
- **Instant Floating Window**: Launch PiP directly from the persistent background notification ("Open PiP"), the in-game quick menu, or by pressing Home.
- **View-Only Mode**: Input capture and touch gestures are automatically paused while in PiP to prevent accidental inputs on your PC.
- **One-Tap Fullscreen**: A simple tap brings up the clean Android maximize control to return to fullscreen instantly.
- **Swipe-Down to Background Stream**: Dragging the floating window to the bottom of your screen seamlessly transitions to background streaming (audio stays active and keepalive runs) without disconnecting your cloud PC session.
- **Crash-Free Decoder Protection**: Decoder surface rendering remains fully foregrounded during window resizes and PiP transitions, completely eliminating the black-screen crashes common in other clients.

### 🔊 Prioritized Real-Time Audio Pipeline
Crystal-clear, stutter-free game audio even when video or network spikes occur:
- **Urgent Kernel Thread Priority**: Both native Opus decoder threads and the Android AudioTrack writer run with maximum real-time priority (`THREAD_PRIORITY_URGENT_AUDIO` nice `-19`).
- **Wi-Fi WMM Voice QoS & DSCP 46 (EF)**: Real-time audio RTP UDP packets are tagged with DSCP 46 (Expedited Forwarding) and Linux socket priority 6 (WMM AC_VO), ensuring home routers and phone network stacks prioritize audio packets ahead of heavy video payloads.
- **Expanded Jitter Resilience**: Audio buffer pool expanded to 24 frames and 80ms tolerance, completely preventing crackles, audio drops, and young-GC latency gaps.

### 🔋 Uninterrupted Background Streaming
- Backed by a persistent foreground service (`dev.ajaretro.europagleam`).
- Stream audio and connection remain alive when answering a notification, minimizing the app, or switching tasks.

### 🚀 Ultra-Low Latency & High Refresh Displays
- Support for **120Hz, 144Hz, and 165Hz** high-refresh displays.
- Hardware decoding for **AV1, HEVC (H.265), and AVC (H.264)** with HDR10 support.
- Native performance triggers for **Vivo / Funtouch OS Ultra Game Mode & Esports Mode**.

---

## 📥 Installation

Download the latest release from the [GitHub Releases](https://github.com/AJARETRO/EuropaGleam/releases) page.

| Variant | Recommended For | Description |
| :--- | :--- | :--- |
| **NonRoot Universal** | Most Users | Standard user APK compatible with all Android devices (`arm64-v8a`, `armeabi-v7a`, `x86_64`). |
| **NonRoot arm64-v8a** | Modern Phones / Tablets | Optimized 64-bit build for modern smartphones and gaming handhelds. |
| **Root** | Rooted Devices | Special build with low-level direct kernel input injection. |

### Standalone Package ID
EuropaGleam uses the package identifier **`dev.ajaretro.europagleam`**. You can install and use it alongside vanilla Moonlight or other clients with zero package or provider conflicts.

---

## 🛠️ Requirements & Host Compatibility

- **Android Client**: Android 5.0 (Lollipop) or newer (Android 12+ recommended for direct wireless DualSense).
- **Host Software**:
  - [Sunshine](https://github.com/LizardByte/Sunshine) (v0.21.0 or newer recommended)
  - [Apollo](https://github.com/ClassicOldSong/apollo) / [Apollo Extended](https://github.com/Taveszfito/Apollo-Extended)
  - NVIDIA GeForce Experience / GameStream

---

## 📜 License & Credits

EuropaGleam is free and open-source software released under the **GNU General Public License v3.0 (GPLv3)**.

- **Developer**: [AJARETRO](https://github.com/AJARETRO)
- **Lineage**: Built upon Moonlight and Artemis open-source foundations. Special thanks to the Moonlight GameStream community, the Sunshine project, and the open-source contributors who pushed the boundaries of low-latency game streaming on Android.
