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

### 🎬 Player & Iconic MX Gestures
- **Horizontal Swipe Seek**: Real-time seek scrub HUD displaying target timestamp, `[±offset]`, and mini scrubber. Smooth seek upon finger release.
- **Vertical Left Swipe**: Real-time smooth brightness control (0% to 100%) with visual HUD indicator.
- **Vertical Right Swipe & Volume Boost**: Real-time volume control from 0% to 100% plus **200% Volume Boost** using Android's `LoudnessEnhancer`.
- **Double Tap Actions**:
  - Left side: Rewind 10s (`⟲ 10s`)
  - Right side: Fast-forward 10s (`10s ⟳`)
  - Center: Play / Pause toggle
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
- **Playback Speed**: Fine-tuned playback speeds from 0.25× to 4.0× with Sonic pitch preservation.
- **Sleep Timer**: Auto-pause playback after 15, 30, 45, 60 minutes or at the end of the video.
- **Background Audio Play**: Option to keep playing audio when minimized or screen turned off.
- **Picture-in-Picture (PiP)**: Dedicated PiP button and automatic PiP on home button navigation.
- **In-Player Playlist**: Quick dialog listing all videos in the current folder with loop modes (Repeat All, Repeat One, Off).
- **System Video Launcher**: Integrated with Android `VIEW` intent filter to play videos from file managers and browsers.

### 🔒 Privacy & 100% Offline Security
- **No Internet Access**: Zero internet permissions (`android.permission.INTERNET` removed and blocked across all dependencies).
- **No Open Ports or Sockets**: Strictly no servers, background sockets, or open listening ports.
- **Air-Gapped Network Configuration**: Network security config disallows cleartext traffic and strips all CA trust anchors, preventing outbound/inbound connections at the OS sandbox level.
