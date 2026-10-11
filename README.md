# Keyflow

An offline Android music player with a musician's practice console planned in eight phases.
Phase 1 playback is verified; Phase 2 adds a persistent local music library and is
undergoing phone acceptance testing. Public releases are deferred until 1.0.0.

- Expo SDK 54, React Native, TypeScript, and Expo Router
- Android Media3/ExoPlayer service in a local Kotlin Expo module
- Keyflow branding, light/dark appearance, local file selection, play/pause, and seeking
- Notification/lock-screen integration and development-only independent tempo/pitch checks
- Separate Room library module with native pagination, indexed search and versioned migrations
- Device audio and saved local folder access, cancellable scans, folder exclusions
- Songs, Albums, Artists and Folders, song sorting, shared full player and mini-player

## Start

```powershell
npm install
npm run build:android
npm start
```

Install the generated `artifacts/keyflow-development.apk` on your Android phone.
Expo Go can preview the interface with `npm run start:go`, but cannot load native
playback or scanning. The installed app is required to use real music.

For an internal test APK that works without Metro or Expo Go, build with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Standalone
```

Install `artifacts/keyflow-test.apk`. This uses a test signing key; production signing
is scheduled for Phase 8. Use the development APK for the tempo/pitch check panel.

See [Phase 1 setup and device checks](docs/phase-1.md) for build requirements and acceptance tests.
See [Phase 2 validation](docs/phase-2.md) and [implementation status](docs/implementation-status.md)
for checkpoint results and outstanding phone checks.

## Local library

On the library home, choose **Scan device audio** and allow Android audio access.
Use library settings to add local folders (including accessible SD-card folders),
include/exclude scanned folders, cancel or rescan, and renew access. Android limits
which directories its folder picker permits. Original files are never changed.
Automatic rescanning is optional and off by default. Enable it in library settings
to refresh on reopening or device-audio changes; otherwise use **Rescan sources**.
An interrupted scan you explicitly started resumes to finish safely.
Missing or disconnected files retain their library references. An incomplete or
cancelled scan does not reconcile the whole source as missing.

Library data is stored in `keyflow-library.db`. Schemas are exported under
`modules/keyflow-library/android/schemas`; upgrades require explicit migrations.
The background playback service remains the sole owner of playback. Tapping a
song replaces the current song; queues and playlists belong to Phase 3.

## Validate

```powershell
npm run typecheck
npm run lint
npm test
```

Native tests run as part of `npm run build:android`. Physical-device audio checks are
required before marking each phase complete. Test fixtures use isolated databases
and do not populate the real library. Native instrumentation sources cover actual
decoders, persistence/migrations, queries and scan behavior.
