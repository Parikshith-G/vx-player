# VX Player

A powerful, native Android video player focused entirely on local and offline playback.

## Features

### 📁 Media Library & Discovery
- **Automatic Device Video Scanning**: Discovers and indexes all videos on device storage (internal and SD card) via MediaStore API.
- **Folder Grouping**: Automatically groups videos by folder (e.g., Camera, Download, Movies, WhatsApp) with video count badges.
- **Horizontal Recently Played Carousel**: Recently watched videos are presented as a clean horizontal scrolling carousel at the top of the Folders tab, showcasing video thumbnails with watch progress indicators and duration badges (without cluttering title text).
- **Folders & All Videos Tabs**: Clean 2-tab navigation between folder grouping and flat library list.
- **Asynchronous Thumbnail Loading**: Fast, smooth scrolling with custom LRU memory cache and background thumbnail generator.
- **Duration & Resolution Badges**: Displays duration (`04:15`) on thumbnails and resolution indicators (4K, 1080p, 720p).
- **Playback Progress Tracking**: Visual progress bars under thumbnails showing watched percentage.
- **Broad Format Support**: Plays `.mp4`, `.mkv`, `.webm`, `.avi`, `.mov`, `.3gp`, `.m4v`, `.ts`, `.flv`, `.wmv`, `.vob`, `.ogv`, `.mpg`, etc.
- **SAF & File Picker**: Grant folder access via system picker (Storage Access Framework) or pick single videos.
- **Real-Time Search & Sorting**: Search by video/folder name; sort by Name, Date, Size, or Duration.

### 🎬 Player & Iconic MX Controls
- **Top Status Header**:
  - **Top Left**: Real-time elapsed time and total / remaining time display (`04:15 / 1:20:00` or `04:15 (-1:15:45)`). Tap to toggle between total and remaining time.
  - **Top Right**: Real-time battery percentage indicator (`🔋 85%`), current wall-clock time (`10:30 PM`), and hamburger menu (`☰`).
- **Top Circular Quick Action Buttons**:
  - Horizontal scrolling row of customizable quick action buttons:
    - Playback Speed (`1.0×`)
    - Orientation Lock (`🔄 Auto / Land / Port / Video`)
    - Fit / Aspect Ratio (`📐 Fit / Fill / Zoom`)
    - In-Player Playlist (`📑 List`)
    - Audio Tracks (`🎵 Audio`)
    - Subtitles (`💬 Sub`)
    - HW / SW Decoder (`HW / SW`)
    - Sleep Timer (`⏱ Timer`)
  - **Fully Customizable**: Tap the hamburger menu (`☰`) -> "Customize Quick Buttons" to select which circular buttons appear.
- **MX Player Bottom Action Controls**:
  - Exact sequence: `[🔒 Lock]` `[⟲ 5s Seek Back]` `[⏮ Prev Video]` `[▶/Ⅱ Play/Pause]` `[⏭ Next Video]` `[5s ⟳ Seek Forward]` `[⧉ PiP]`.
- **Horizontal Swipe Seek**: Real-time seek scrub HUD displaying target timestamp, `[±offset]`, and mini scrubber. Smooth seek upon finger release.
- **Vertical Left Swipe**: Real-time smooth brightness control (0% to 100%) with visual HUD indicator.
- **Vertical Right Swipe & Volume Boost**: Real-time volume control from 0% to 100% plus **200% Volume Boost** using Android's `LoudnessEnhancer`.
- **Top Edge Gesture Exclusion**: Swipes starting near the top of the screen (top 35dp) are excluded, allowing the system notification drawer to be pulled down without unintentionally scrubbing brightness or volume.
- **Double Tap Action**: Double tap toggles Play / Pause. Seeking is restricted to the dedicated `±5s` buttons and seekbar to prevent accidental seeks.
- **Hold to Boost (2.0×)**: Long-press anywhere on the screen temporarily accelerates playback to 2.0× speed; release to restore.
- **Single Tap**: Toggle immersive playback controls overlay (auto-hides after 4.5s of inactivity).
- **Screen Lock (Touch Lock)**: Lock button in controls hides all overlays and prevents accidental touches. A floating lock icon allows unlocking.

### 🔊 Audio & 💬 Subtitles
- **Hardware (HW) & Software (SW) Decoder**: Switch between hardware-accelerated playback and software decoding.
- **Multi-Track Audio**: Switch between audio tracks (languages/channels) and toggle mute.
- **Embedded & External Subtitles**: Load embedded subtitle tracks or open external subtitle files (`.srt`, `.vtt`, `.ass`, `.ssa`).
- **Subtitle Styling**: Change subtitle text size (Small, Normal, Large, Huge).

### ⚙️ Playback Features
- **Smart Resume**: Automatically prompts and resumes partially watched videos with a floating bottom pill (`Resumed from 03:45 [Restart]`).
- **Aspect Ratio Control**: Toggle between Fit (Letterbox), Stretch (Fill), and Crop (Zoom).
- **Orientation Modes**: Auto-rotate (Sensor), Landscape, Portrait, and Match Video Aspect Ratio.
- **Playback Speed Persistence**: Fine-tuned playback speeds from 0.25× to 4.0× with Sonic pitch preservation; your chosen speed is remembered and restored across app restarts.
- **Sleep Timer**: Auto-pause playback after 15, 30, 45, 60 minutes or at the end of the video.
- **Background Audio Play**: Option to keep playing audio when minimized or screen turned off.
- **Picture-in-Picture (PiP)**: Dedicated PiP button and auto-PiP with persistent uninterrupted playback, interactive window controls (Play/Pause, Rewind 10s, Fast-Forward 10s), and seamless resize.
- **In-Player Playlist**: Quick dialog listing all videos in the current folder with loop modes (Repeat All, Repeat One, Off).
- **System Video Launcher**: Integrated with Android `VIEW` intent filter to play videos from file managers and browsers.

### 🔒 Privacy & 100% Offline Security
- **No Internet Access**: Zero internet permissions (`android.permission.INTERNET` removed and blocked across all dependencies).
- **No Open Ports or Sockets**: Strictly no servers, background sockets, or open listening ports.
- **Air-Gapped Network Configuration**: Network security config disallows cleartext traffic and strips all CA trust anchors, preventing outbound/inbound connections at the OS sandbox level.
