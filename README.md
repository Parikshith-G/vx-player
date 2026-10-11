# VX Player (Android)

A powerful, native Android video player focused entirely on local and offline playback, engineered with Jetpack Media3 (ExoPlayer), hardware acceleration, and privacy-first architecture.

---

## Features

### 📁 Media Library & Discovery
- **Automatic Device Video Scanning**: Discovers and indexes all videos on device storage (internal and SD card) via the Android MediaStore API.
- **Folder Grouping & Seen Tab**: Clean 3-tab library layout:
  - **Folders**: Videos automatically grouped by folder (Camera, Download, Movies, etc.) with video count badges.
  - **All Videos**: Flat library list with duration, resolution, and file size details.
  - **Seen**: Dedicated tab tracking completed videos with single-tap "Delete All Seen" and "Clear History" batch actions.
- **Horizontal Recently Played Carousel**: Recently watched videos are presented as a clean horizontal scrolling carousel at the top of the Folders tab, showcasing video thumbnails with watch progress indicators and duration badges. Seamlessly tracks videos opened from device storage, SAF folders, and external file pickers.
- **Asynchronous Thumbnail Cache**: Dual-layer LRU memory cache and background generator with strict view-tag binding to eliminate recycling race conditions during fast scrolling.
- **Duration & Resolution Badges**: Displays duration (`04:15`) on thumbnails and resolution indicators (4K, 1080p, 720p).
- **Playback Progress Tracking**: Visual progress bars under thumbnails showing watched percentage.
- **Broad Format Support**: Plays `.mp4`, `.mkv`, `.webm`, `.avi`, `.mov`, `.3gp`, `.m4v`, `.ts`, `.flv`, `.wmv`, `.vob`, `.ogv`, `.mpg`, and more.
- **SAF & File Picker**: Grant folder access via system picker (Storage Access Framework) or pick individual videos.
- **Real-Time Search & Natural Sorting**: Search by video/folder name; sort by Name (with natural alphanumeric ordering), Date, Size, or Duration.

### 🎬 Player & Playback Controls
- **Always-Visible Top Status Header**:
  - **Top Left**: Real-time elapsed time and total / remaining time display (`04:15 / 1:20:00` or `04:15 (-1:15:45)`). Tap to toggle between total and remaining time. **Always visible**, even when playback controls are hidden or screen is locked.
  - **Top Right**: Real-time battery percentage indicator (`🔋 85%`) and current wall-clock time (`10:30 PM`). **Always visible**, even when controls are hidden or screen is locked.
- **Top Controls & Circular Quick Action Buttons**:
  - Full title and hamburger menu (`☰`) appear when controls are toggled.
  - Horizontal scrolling row of customizable circular quick action buttons:
    - **Playback Speed** (`0.25×` to `8.0×` with fine slider, presets, and Sonic pitch preservation)
    - **Orientation Lock** (`Auto / Land / Port / Video`)
    - **Fit / Aspect Ratio** (`Fit / Fill / Zoom`)
    - **In-Player Playlist** (`List` dialog with Repeat All, Repeat One, and Off modes)
    - **Delete Video** (Quick 4-tap safety deletion with auto-progression to next item)
    - **Audio Tracks** (`Audio` language/channel selector and instant mute)
    - **Subtitles** (`Sub` embedded subtitle selector and external `.srt`/`.vtt`/`.ass`/`.ssa` subtitle loader)
    - **HW / SW Decoder** (Hardware-accelerated decoding vs. software fallback)
    - **Sleep Timer** (`Timer` auto-pause after 10, 15, 30, 45, 60 minutes or at video end)
  - **Fully Customizable**: Tap the hamburger menu (`☰`) -> "Customize Quick Buttons" to select which circular buttons appear.
  - **Interactive Feature & Controls Guide**: Tap the `ⓘ` button in the top bar or settings menu for a step-by-step walkthrough highlighting controls and gestures.
- **VX Player Bottom Action Controls**:
  - Exact sequence: `[🔒 Lock]` `[⟲ 5s Seek Back]` `[⏮ Prev Video]` `[▶/Ⅱ Play/Pause]` `[⏭ Next Video]` `[✔ Mark Seen]` `[5s ⟳ Seek Forward]` `[⧉ PiP]`.
  - Next video button automatically marks the current video as seen before transitioning.
  - **Skip Anime Opening**: Dedicated `+90s OP` button on the bottom left (above timeline) to skip 90s intro (80s seek duration) with one tap.
  - Hold-to-continuous-seek supported on `±5s` seek and prev/next skip buttons, with automatic dismissal of seek HUD when released.
- **Touch Gestures**:
  - **Corner Double-Tap Seek (5 Seconds)**:
    - Double-tap the **top-left 1/4th corner** of the screen to seek backward 5s (`⟲ 5s`).
    - Double-tap the **top-right 1/4th corner** of the screen to seek forward 5s (`5s ⟳`).
    - Double-tap anywhere else (center) to toggle **Play / Pause**.
  - **Vertical Left Swipe**: Real-time smooth brightness control (0% to 100%) with visual HUD indicator. Top 35dp edge exclusion prevents accidental adjustments when pulling down the system notification shade.
  - **Vertical Right Swipe & Volume Boost**: Real-time volume control from 0% to 100% plus **200% Volume Boost** using Android's `LoudnessEnhancer`.
  - **Hold to Boost & Scrub (0.25× – 8.0×)**: Long-press anywhere on the video accelerates playback to 2.0× speed (or remembered custom speed); slide horizontally while holding to adjust speed linearly from 0.25× up to 8.0×; release to restore.
  - **Single Tap**: Toggle immersive playback controls overlay (auto-hides after 4.5s of inactivity).
  - **Screen Lock (Touch Lock with Auto-Hiding Unlock Icon)**: Tapping Lock locks the screen and displays the floating unlock icon briefly (3s) before auto-hiding for completely unobstructed playback. Tapping the screen while locked brings the unlock button back for 3s.
- **Bluetooth & AVRCP MediaSession**:
  - Native Android `MediaSession` integration with support for Bluetooth headset single-tap and double-tap play/pause events.

### 🔊 Audio & 💬 Subtitles
- **Hardware (HW) & Software (SW) Decoder**: Switch between hardware-accelerated playback and software decoding.
- **Multi-Track Audio**: Switch between audio tracks (languages/channels) and toggle mute.
- **Embedded & External Subtitles**: Load embedded subtitle tracks or open external subtitle files (`.srt`, `.vtt`, `.ass`, `.ssa`).
- **Subtitle Styling**: Change subtitle text size (Small, Normal, Large, Huge).

### ⚙️ Playback Features
- **Smart Resume**: Automatically prompts and resumes partially watched videos with a floating bottom pill (`Resumed from 03:45 [Restart]`).
- **Aspect Ratio Control**: Toggle between Fit (Letterbox), Fill (Stretch), and Zoom (Crop).
- **Orientation Modes**: Auto-rotate (Sensor), Landscape, Portrait, and Match Video Aspect Ratio.
- **Playback Speed Persistence**: Fine-tuned playback speeds from 0.25× to 8.0× remembered across app restarts.
- **Sleep Timer**: Auto-pause playback after 10, 15, 30, 45, 60 minutes or at the end of the video.
- **Background Audio Play**: Option to keep playing audio when minimized or screen turned off.
- **Picture-in-Picture (PiP)**: Dedicated PiP button and auto-PiP with persistent uninterrupted playback and interactive window controls (Play/Pause, Previous Video, Next Video).
- **System Video Launcher**: Integrated with Android `VIEW` intent filter to play videos from file managers and browsers.

### 🔒 Privacy & 100% Offline Security
- **No Internet Access**: Zero internet permissions (`android.permission.INTERNET` removed and blocked across all dependencies).
- **No Open Ports or Sockets**: Strictly no servers, background sockets, or open listening ports.
- **Air-Gapped Network Configuration**: Network security config disallows cleartext traffic and strips all CA trust anchors, preventing outbound/inbound connections at the OS sandbox level.

---

## Building & Testing

### Prerequisites
- Android Studio Jellyfish or later
- Android SDK (API 26 to 34+)
- JDK 17+

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
