# Terminal
A real interactive Android shell app using a persistent `/system/bin/sh` process.

## Build APK
Push to `main` or run **Build Terminal APK** from Actions. Download the `terminal-debug-apk` artifact.

## Limitations
This runs Android's restricted shell, not Debian/Ubuntu. It does not ship `apt`, `sudo`, or a complete Linux userspace. On a Chromebook, Google's Linux development environment provides a real Debian container when enabled and permitted by the device administrator.
