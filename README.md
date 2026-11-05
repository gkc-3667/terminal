# Terminal Pro for Android / Chromebook

A native Android terminal interface that runs a persistent Android shell process.

## What works
- Real commands executed by Android's `/system/bin/sh`
- Persistent shell session, so `cd` is retained between commands
- Command history (hardware keyboard Up/Down and `history`)
- Quick command shortcuts, copy output, clear screen
- Files and directories inside the app's private storage

## Important limitation
This build is an **Android shell**, not a Debian/Ubuntu Linux distribution. It does not provide `apt`, `sudo`, a Linux package manager, or a complete Linux filesystem. Commands available depend on the Android/ChromeOS runtime.

The app cannot bypass Android or ChromeOS security isolation, gain root access, or directly control the Chromebook's ChromeOS Linux container. A full Debian environment would require a separately packaged and maintained userspace/runtime (for example, a PRoot-based distribution), additional downloads and storage, and device-architecture testing.

## Build
GitHub Actions builds a debug APK on every push to `main`.

1. Open the Actions tab.
2. Select the latest successful **Build Terminal APK** run.
3. Download the `terminal-debug-apk` artifact.
4. Extract the ZIP and install `app-debug.apk` on a compatible Chromebook/Android environment.
