# Implementation status

Updated 2026-10-11. Phase 1 is implemented and undergoing physical-device
acceptance testing. Later phases have not started.

## Implemented

Keyflow branding using the original logo, reusable light/dark themes with saved
appearance, a typed Kotlin Expo bridge, Media3 playback owned by a background
media service, local audio selection, metadata/artwork, play/pause, seeking,
duration/status/error events, audio-focus handling and headphone-disconnection
pause. Development-only checks exercise independent tempo and pitch.

At the owner's request, the prominent 15-second seek buttons were removed;
seeking remains available through the slider. Next/previous controls belong to
Phase 3 with its queue, rather than inactive buttons in Phase 1.

## Validation recorded

- Type checking, linting, three JavaScript tests, and six native unit tests pass.
- Development and standalone internal Android APKs built successfully (arm64,
  minimum API 24, target API 36). The standalone APK includes the updated controls and its APK signature and
  embedded JavaScript bundle have been verified.
- Development and standalone APKs were installed on the owner's authorized Android 16 phone. The standalone app was launched after stopping Metro and removing USB port forwarding.
- The owner confirmed audible playback of a selected local song, continued
  playback in the background and while locked, and notification/lock-screen
  play/pause controls.
- System media commands paused, advanced the position by 15 seconds, and resumed
  native playback. A foreground media service and PLAYING state while the phone
  was dozing were observed.
- The owner confirmed that 75% tempo, +2 semitones, both together, and reset
  behave independently as described in the development checks.
- Both themes rendered in the web interface preview. Audio validation used the
  real installed Android app, not that preview.

The owner also confirmed slider seeking and uninterrupted playback throughout
file-picker cancellation.

The standalone player screen rendered correctly with real embedded artwork and
progress; its native media session reported PLAYING with Metro stopped.

The owner confirmed audible standalone playback, earbud gesture controls, and
automatic pause when disconnecting the earbuds.

Remaining checks include format/error fixtures, end/replay, appearance
persistence on the phone, and audio-focus interruptions.
See docs/phase-1.md. Do not mark the full acceptance checklist complete yet.

## Build and resume notes

Workspace-local JDK 17, Android SDK 36, build tools, platform tools, NDK
27.1.12297006, CMake 3.22.1 and Gradle 8.14.3 are cached under `.tools`.
Keep these caches. The initial download pause for a metered connection was
respected; work resumed at the owner's request.

Use `scripts/build-android.ps1` for the development APK; add `-Standalone` for
`artifacts/keyflow-test.apk`, which embeds JavaScript and local assets. This
internal test package uses the generated debug signing key, not a production key.
Development parameter controls are absent from standalone production bundles.

Metro excludes tools/artifacts paths with Windows and normalized separators and
uses separate development/production transform caches. Gradle uses no persistent
daemon or file-system watcher and limits workers to four, after Windows cache
rename failures were recovered inside the workspace.

The test tone/click WAV is available in `artifacts` and was copied to the phone's
`Music/KeyflowTest` folder. Development testing used USB-reversed port
8082; Metro is now stopped and the forwarding has been removed. The standalone version needs no Metro connection.

Changes remain in the working tree; generated tools/build files are ignored by
Git. No cloud build or app publication has been initiated.




