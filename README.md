# Comment Blocker for Instagram Reels

An Android app that hides the comment section on Instagram Reels using Android's Accessibility Service API.

## How it works

The app registers an Accessibility Service that monitors Instagram. When you're watching Reels, it draws a solid overlay over the comment/action button area on the right side of the screen — hiding likes, comments, and share counts.

## Build the APK (free, no Android Studio needed)

### Option 1 — GitHub Actions (easiest)

1. Create a free account at [github.com](https://github.com)
2. Create a new repository (can be private)
3. Upload all these files to the repo (drag & drop the zip, or use GitHub Desktop)
4. Go to the **Actions** tab in your repo
5. The build starts automatically. Wait ~3 minutes.
6. Click the finished workflow run → scroll to **Artifacts** → download `comment-blocker-debug`
7. You get a `.zip` — unzip it to get `app-debug.apk`

### Option 2 — Android Studio (if you have it)

1. Open Android Studio → Open Project → select this folder
2. Build → Build Bundle(s)/APK(s) → Build APK(s)
3. APK is at `app/build/outputs/apk/debug/app-debug.apk`

## Install the APK on your phone

1. Transfer the `.apk` file to your Android phone (via USB, Google Drive, email, etc.)
2. On your phone: **Settings → Security → Install unknown apps** → allow your file manager / browser
3. Open the APK file and tap **Install**

## Set up the app

1. Open **Comment Blocker** on your phone
2. Tap **"Open Accessibility Settings"**
3. Find **"Comment Blocker"** in the list and tap it
4. Toggle it **ON** and confirm
5. Come back to the app and enable the **"Block Comments"** switch
6. Open Instagram and scroll Reels — comments are now hidden!

## Permissions used

- `BIND_ACCESSIBILITY_SERVICE` — required to monitor the screen and draw overlays
- `SYSTEM_ALERT_WINDOW` — used internally by the accessibility overlay mechanism

No internet permission is used. The app works fully offline and collects no data.
