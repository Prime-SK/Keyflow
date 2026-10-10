# Keyflow

An offline Android music player with a musician's practice console planned in eight phases.
This implementation covers **Phase 1: local-file selection and native background playback**.

- Expo SDK 54, React Native, TypeScript, and Expo Router
- Android Media3/ExoPlayer service in a local Kotlin Expo module
- Keyflow branding, light/dark appearance, local file selection, play/pause, and seeking
- Notification/lock-screen integration and development-only independent tempo/pitch checks

## Start

```powershell
npm install
npm run build:android
npm start
```

Install the generated `artifacts/keyflow-development.apk` on your Android phone.
Expo Go can preview the interface with `npm run start:go`, but cannot load the native playback service.

For an internal test APK that works without Metro or Expo Go, build with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Standalone
```

Install `artifacts/keyflow-test.apk`. This uses a test signing key; production signing
is scheduled for Phase 8. Use the development APK for the tempo/pitch check panel.

See [Phase 1 setup and device checks](docs/phase-1.md) for build requirements and acceptance tests.

## Validate

```powershell
npm run typecheck
npm run lint
npm test
```

Native tests run as part of `npm run build:android`. Physical-device audio checks are
required before calling Phase 1 fully verified.
