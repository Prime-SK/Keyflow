# Keyflow Phase 1

Phase 1 implements an Android local-file player. Library scanning, queues, playlists,
crossfade, audio analysis, and the Practice Console remain assigned to later phases.

## Expo Go versus the Keyflow development app

Expo Go can show the interface and appearance choices. It does not contain the custom
Keyflow playback module, so the app displays an explicit preview notice and hides
playback actions. No simulated song or JavaScript audio fallback is provided.

For playback, install **Keyflow's development APK**, then start Metro with `npm start`.
The phone and PC should share a network for loading the development bundle. This is
development tooling; audio is read locally, without uploading files. A standalone
offline release build and release network-permission checks belong to Phase 8.

## Build and install on Windows

Requirements: Node/npm, JDK 17, Android SDK platform 36, build-tools 36.0.0, platform-tools,
and the NDK/CMake versions requested by the generated Expo/React Native project.
The SDK licenses must be accepted for automatic component installation.

The helper uses project-local tools under `.tools` when present. Otherwise set
`JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your Android SDK installation.

```powershell
npm install
npm run build:android
# Optional: install on a USB-connected, debugging-authorized arm64 Android phone
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Install
npm start
```

The build helper runs native unit tests and copies the arm64 development APK to
`artifacts/keyflow-development.apk`. It does not commit generated Android files or
downloaded tools. For emulators/other architectures, use `npm run android` with an
Android Studio SDK and the desired device.

For a standalone internal test APK that opens without Metro or Expo Go:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Standalone
```

This produces `artifacts/keyflow-test.apk` with bundled JavaScript using the
generated debug signing key. It is intended for local testing; production signing
and the release audit remain Phase 8 work. Development parameter-check controls
are absent from this variant, so use the development APK for those checks.

USB development also works without putting the phone and PC on the same Wi-Fi:
start Metro, run `adb reverse tcp:8081 tcp:8081`, and open
`keyflow://expo-development-client/?url=http%3A%2F%2F127.0.0.1%3A8081`
on the phone. Substitute the port if Metro uses another one. USB debugging must
remain authorized. This connection loads development code; it does not upload music.

For an interface-only Expo Go preview:

```powershell
npm run start:go
```

An optional EAS development profile is included in `eas.json`; using it requires the
owner's Expo account/project setup. No cloud build or upload is initiated automatically.

## Playback implementation

- Local Expo module: `modules/keyflow-playback`; Android-only and automatically linked.
- `KeyflowPlaybackService` owns ExoPlayer, audio focus, the media session, wake lock,
  and foreground playback notification. Closing the React activity does not release it.
- The file picker uses `ACTION_OPEN_DOCUMENT`, `audio/*`, local-only selection, and
  read grants. It preserves grants where the provider permits, reads tags/artwork off
  the UI thread, and does not copy, rewrite, or delete original audio files.
- `KeyflowPlaybackModule` connects through a MediaController. Typed state/error events
  drive the UI; sequence numbers prevent late command responses from overwriting newer
  native state. Position events are sampled at 250 ms only while the UI is visible.
- Play, pause, seeking, and independent speed/pitch parameters are native commands.
  The current phase plays one selected song; opening another replaces it and resets
  speed/pitch. Persistent library and playback recovery arrive in subsequent phases.
- Theme preference is saved locally in the native development app. In Expo Go/web,
  appearance overrides last for the current preview session.
- Individual-file access does not need broad audio/storage permissions. Foreground
  media playback and wake-lock permissions come from the native library manifest.

## Checks

```powershell
npm run typecheck
npm run lint
npm test
npm run build:android
```

Native unit tests cover octave/semitone/cents ratios, independent tempo values,
finite-input validation, and seek boundaries. JS tests cover out-of-order native
snapshots and user-facing time/error handling. These do not replace device audio tests.

## Physical-device acceptance checklist

- Open a local MP3, M4A/AAC, FLAC, WAV, and Ogg file. Verify real metadata, artwork
  where embedded, duration, seeking, pause, and end-of-track replay.
- Cancel the picker while a song is playing: the current song should continue.
- Select a different song: it should replace the current song, with normal tempo/pitch.
- Lock the screen and leave it locked for several minutes: playback should continue.
- Use notification/lock-screen play, pause, and seeking. Tapping the notification
  should reopen Keyflow with current native state.
- Dismiss Keyflow from recent apps while playing: music should continue. Force-stop
  is different: Android intentionally stops the service.
- Test Bluetooth/wired-headset controls, an audio-focus interruption, and headphone
  disconnection (which should pause music).
- Choose an unavailable/corrupt file: show a useful error rather than crash or fake playback.
- Switch between device/light/dark appearance; reopen the native app to verify the choice.
- Expand **Development audio checks** after loading a song. Test 75% tempo alone
  (unchanged pitch), +2 semitones alone (unchanged tempo), both, and Reset. The panel
  displays the native speed/pitch values and is absent from production bundles.
- Listen as well as checking values: a 120 BPM fixture at 75% should be about 90 BPM;
  a 440 Hz fixture shifted +2 semitones should be about 493.88 Hz. Test combinations
  while locked. Report audible dropouts or artifacts; this is a feasibility check,
  not a claim of professional pitch-processing quality.

Do not mark Phase 1's physical-device gate passed without performing these checks.

For a controlled starting fixture, run `node scripts/create-audio-fixture.cjs`.
It generates a quiet two-minute WAV tone/click track in `artifacts`, at 440 Hz and
120 BPM. Copy it to the phone's `Music/KeyflowTest` folder and select it through
the normal file picker. It supplements real-song and headset testing.
