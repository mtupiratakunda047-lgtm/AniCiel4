# AniCiel MediaHub (NEW)

Fresh AniCiel lineage from **MediaHub UI + Google Drive base.apk** only.

- No account / sign-in UI
- No VIP / coins / tokens
- No ads surfaces
- Adult section age-gated (18+)
- Downloads → `/storage/emulated/0/Download/AniCiel`

## Build the APK

1. Push this repo to GitHub (or use the existing **AniCiel4** repo)
2. Go to **Actions** → run the **build** workflow (or just push)
3. Download the artifact **AniCiel-MediaHub-debug**

Or build locally with Android Studio / JDK 17 + Android SDK 36:

```bash
gradle assembleDebug
# APK at app/build/outputs/apk/debug/
```

## Engine

Pure-Kotlin MovieBox client (OkHttp + base.apk signing).  
No native Rust `.so` required for this build.

## Layout

```
app/          # Compose UI + Kotlin engine
engine/       # Reference Rust paths (optional, not required to build)
sources/      # Allowed / excluded API lists from base.apk
```
