# Phase 4 — Branding, transitions and audio controls

## Checkpoint rule

Owner approved four checkpoints, each ending with a standalone test APK and phone
review before the next begins. All four checkpoints are accepted; Phase 4 is complete. No commit or public release
without the owner's instruction. Audio originals remain untouched.

## 4A — Play K / cobalt / atmosphere

Vector masters and reproducible PNG exports live in assets/branding. The Play K is
the first letter of the wordmark, followed by eyflow. Light/dark wordmarks, launcher,
adaptive foreground/monochrome, and the standalone symbol are supplied. The launcher
has safe padding for Android masks. SVG wordmark text is editable Segoe UI Bold;
PNG exports retain the rendered typography without runtime font dependencies.
Run scripts/generate-branding.cjs with KEYFLOW_ASSET_NODE_MODULES pointing to a
local sharp installation; the asset tooling adds no app dependency.

Dark background #0B1020, surface #18233B, accent #6D91FF. Light background #F4F7FD,
white surfaces, accent #1E40AF. Saved System/Light/Dark appearance is retained.
Now Playing retains the restrained artwork background tint. Following owner review,
playback controls now follow the artwork hue, adjusted for readable contrast in both
themes. Missing/unreadable artwork retains cobalt controls. The rest of the app
uses the cobalt palette.
A steady rounded artwork glow changes with the cover; no breathing or pulsing.
Native generation runs off playback on IO, draws a 256px PNG and bounds its disk
cache to 32 colors. Missing/unreadable/monochrome artwork uses the theme glow.
Artwork sampling stays limited to cached image files. Requests are guarded against
late completion; modified file timestamps participate in the color-cache key.
Background/glow transitions last 400ms on the UI thread, immediate with reduced
motion. The glow never intercepts gestures or appears to accessibility services.

Native cache/alpha/fallback phone tests, typecheck/lint, six JS regressions, twenty
native unit tests, standalone build/signature and installation passed. Now Playing
was visually inspected on the phone with a red cover: ambient tint/glow, blue controls
and stable layout are visible. Owner launcher/splash, light/dark, changing-cover,
missing-art and gesture review remains pending. Phase 4B has not begun.

## 4B — Agreed transitions

Native two-player coordinator exposed as one authoritative logical player to the
bridge and system controls. At most two songs overlap; native preload invalidates
on queue changes before the target starts. Preserve UUID queue identities, full
shuffle/underlying order, FIFO Play next, paginated reads and paused recovery.

Dedicated Audio route accessible through Now Playing menu and Library settings,
with Transitions/Equalizer tabs. Persist independent auto/manual switches (off by
default), durations auto 0–12s (remember 5s), manual 0–2s (remember 300ms), and separate
Linear/Equal-power selectors (auto equal-power, manual linear). Editing a duration
never enables its switch. Gapless and automatic crossfade are mutually exclusive;
gapless allows manual fades.

- Automatic fade applies to all queued transitions including albums and Repeat one.
- Every deliberate song change uses the configured manual fade, across app/system
  buttons, gestures and list selection. Rapid skips retarget immediately, never queue.
- Incoming starts audibly: hand off app/system metadata, seek position and queue highlight.
- Pause freezes both positions/envelope; Play resumes that overlap.
- Seeking stops outgoing and seeks displayed incoming, retaining paused/playing state.
- Reorder/Play next preserve overlap and affect the upcoming sequence after incoming.
  Removing outgoing stops only its tail; removing incoming follows current removal rules.
- End timer suppresses all automatic overlaps including repeat. If already overlapping,
  end outgoing tail and finish displayed song alone. Timed/noisy/focus interruptions
  apply consistently to both players. Restore only current song/queue paused, no overlap.
- Clamp auto overlap to half the shorter participating duration. Unknown durations or
  unready targets use sequential playback; unavailable files retain bounded skipping.
- Duration/curve edits apply next transition; disabling auto/enabling gapless ends the
  old tail immediately and retains incoming.

## 4C — Agreed sound controls

Global saved settings (no output-specific profiles), software processing independently
applied on each player's native PCM chain. One master switch, initially off. Bands
31/62/125/250/500/1000/2000/4000/8000/16000 Hz, each −12…+12dB; independent 100Hz bass
shelf −12…+12dB, preamp −24…+12dB; all initially zero. Live native smoothing, no decoder
reload. Accessible sliders/values; all ten bands fit together following owner review. Individual
bass/preamp resets and reset-all. Automatic headroom accounts for effects/overlap and
shows attenuation; no loudness-normalization claim.

Read-only Flat/Bass boost/Vocal/Treble boost built-ins; specify their exact curves
before implementing 4C. Multiple named custom presets with save/update/rename/delete.
Save EQ+bass+preamp together, excluding transition settings. Live temporary edits
require explicit Update/Save as new to alter a preset.

## 4D — Acceptance

Run relevant JS/native regressions, typecheck/lint, standalone build/signature, real
decoders and physical listening. Cover curves/clamps/repeat/preload failures, mixed
sample rates/formats, rapid commands and queue edits, timers/noisy/focus/system/locked
playback, process recovery, DSP frequency/neutral/clipping/smoothing/preset fixtures,
missing art, contrast, reduced motion, large text and gestures. Existing physical
storage/accessibility/10k scrolling acceptance remains tracked separately.

Analysis, normalization, silence trimming and Practice Console remain later phases.


4A validation: typecheck and clean lint pass, six JS checks pass, twenty native unit
tests pass. Both focused phone artwork tests pass (0.758s), covering cached color,
transparent 256px glow, blue fallback, invalid color rejection and 32-file bounds.
Base text/secondary/accent contrast exceeds 4.5:1 in both themes. Final standalone
build (2m15s), APK signature verification and installation pass. Keyflow opened
without app-scoped AndroidRuntime/ReactNativeJS/Reanimated/Worklets errors.
Latest APK: artifacts/keyflow-phase-4a.apk (also keyflow-test.apk), 47,804,342 bytes,
SHA256 9CDFFA93A16614862EC69AB71CA63FEDDC3CE95C35EFA5C9DEDEAF499B6B8902.
Native playback/queue/database behavior is unchanged. Owner launcher/splash/themes
and glow/fallback/gesture review requested. Phase 4B is awaiting that review.
No commit or release.

Owner accepted launcher, wordmark, both themes, glow and gestures. Requested
artwork-derived control colors instead of the initial fixed-blue choice. Added
contrast-adjusted controls, 400ms UI-thread button/icon color changes and immediate
reduced-motion updates. A 432-color/theme fixture checks 4.5:1 contrast against
player backgrounds, menu surfaces and play-button symbols; all seven JS tests,
typecheck and lint pass. Amended 4A awaits phone review before 4B.

Amended standalone APK builds successfully (1m33s), signature verifies and installs.
artifacts/keyflow-phase-4a.apk / keyflow-test.apk: 47,806,526 bytes; SHA256
6D4C5A5E9C15F3DB163CC058338966BE26D6327F5BA303C9D36803BAA35682A8.


Owner approved amended 4A control colors/readability. 4A checkpoint accepted.

## 4B implementation

A stable Media3 1.8.0 ForwardingSimpleBasePlayer owns two ExoPlayer decks, one
session and one audio-focus owner. Incoming audio swaps the forwarded deck;
outgoing events cannot advance the displayed queue or overwrite recovery.
Native envelopes use monotonic elapsed time, freeze on pause, and discard tails
on seeking. Repeat one uses a second instance of the same occurrence. Automatic
overlap clamps to half each known duration; unready preloads remain sequential.
Queue edits invalidate preloads, keep active overlap, and stop explicitly removed
tails. Manual decoding waits at the outgoing end if needed. Recovery still stores
the displayed entry and restores it paused, without reconstructing overlaps.
Settings are separately versioned SharedPreferences v1, initially disabled;
library/collections/recovery schemas are unchanged. Equal-power fades reserve
3 dB headroom. The Audio route is reachable from Now Playing and Library settings;
it exposes transitions now. The Equalizer tab arrives with checkpoint 4C.

Validation in progress: four envelope/settings tests plus existing 15 playback
unit tests pass; first three real-decoder phone transition tests pass (2.778s).
These cover pause/frozen progress/seek, rapid retarget, overlap queue edits and
outgoing removal, automatic Repeat one and end-timer suppression. Final native
format regression and standalone installation/listening review remain pending.
4C has not started. No commit or public release.

4B final validation: typecheck, clean lint, seven JS tests and 19 playback unit
checks pass. Seven physical-phone decoder/transition/timer tests pass (18.106s),
including all five audio formats as incoming songs. Final standalone build passes
(2m38s), signature verifies and update installation succeeds. APK:
artifacts/keyflow-phase-4b.apk / keyflow-test.apk, 47,829,958 bytes,
SHA256 ED171B0B7F0B796049B0016C3F79966D2A98FC4D52DC7B4B79FCFDC3CC961C47.
Owner audible-curve/duration, system/locked controls, focus/headphones and paused
termination recovery review remains pending. Mixed-sample-rate combinations and
preloading/corruption failure coverage still need focused expansion in 4D.

Owner 4B listening review: automatic crossfade and Repeat one pass; manual fades
from buttons/gestures/library selection and overlap pause/resume/seek/rapid skips
accepted ("perfect... everything works"). Locked/system controls, interruption of
both decks, persisted settings and paused termination recovery are the remaining
checkpoint review questions. Keep 4C behind this review gate.

Owner confirmed locked/system/earbud controls, focus/headphone interruptions,
paused termination recovery and saved transition settings. Requested two 4B
amendments before 4C: Previous restarting the same song should use the manual
fade; play/pause should fade too. Owner selected the existing manual-fade switch,
duration and curve for play/pause. Restart now transitions to a second instance
of the same occurrence. A reversible master envelope fades both decks and freezes
overlap progress until transport fade completion. Logical play intent updates
immediately; interruptions and timers use an immediate-pause path. A focus gain
cannot resume after a later headphone/timer stop. Amended listening review pending.

Amended 4B validation: typecheck/lint/seven JS regressions pass; 20 playback unit
checks pass. Nine phone transition/timer tests pass (9.851s), including same-entry
restart, pause master-envelope/frozen overlap, immediate interruptions, and a
near-end pause that must not advance to the next song. Final standalone build
passes (2m4s), signature verifies and installation succeeds after owner dismissed
Play Protect. APK artifacts/keyflow-phase-4b.apk / keyflow-test.apk: 47,829,962 bytes,
SHA256 B4BC279EBD2BDDD4BB499DE32000CAC38C27182C812275869F76560577692649.
Owner amended restart/play-pause listening review pending. No commit or release.

Owner confirmed restart and play/pause fades work. Amended 4B accepted.

## 4C fixed preset definitions (before implementation)

Bands, in order: 31, 62, 125, 250, 500 Hz, 1, 2, 4, 8, 16 kHz.
All values are dB. Presets store sound values, not the processing switch.

| Preset | Ten band gains | 100 Hz bass shelf | Preamp |
| --- | --- | --- | --- |
| Flat | 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 | 0 | 0 |
| Bass boost | 4, 3, 1, 0, 0, 0, 0, 0, 0, 0 | 2 | 0 |
| Vocal | 0, 0, -2, -1, 1, 2, 3, 1, 0, 0 | 0 | 0 |
| Treble boost | 0, 0, 0, 0, 0, 0, 0, 2, 3, 4 | 0 | 0 |

Temporary edits do not overwrite presets. Selecting/editing a preset does not
enable processing. Per-deck PCM filters use 20 ms output smoothing. Automatic
headroom uses the cascade response with a safety margin, plus the coordinator's
equal-power overlap reserve. This reduces volume; it is not loudness normalization.

### 4C implementation and validation

Added independent software PCM processors to both native decks. The pinned Media3
1.8.0 sink accepts custom processors; float output and hardware offload are disabled
so playback always uses this path. Double-precision peaking filters (Q 1.4) and a
100 Hz shelf run before Media3's existing tempo processor. A 20 ms output blend
smooths live settings/bypass changes without rebuilding decoders. Filter history
is isolated per channel/deck and reset on flush. Above-Nyquist bands bypass.
The cascade's 48 kHz response estimate plus 1 dB safety margin establishes one
shared boost reserve across both decks; final PCM conversion bounds output.
The coordinator retains its separate equal-power reserve. Expanded transient,
mixed-rate and listening acceptance remains tracked for 4D.

Sound preferences/custom presets use a separate v1 SharedPreferences record.
Temporary values are independent of saved presets. Built-ins cannot be modified;
custom Save/Update/Rename/Delete are explicit. The Audio route now has Transitions
and Equalizer tabs, a horizontally scrolling band bank, bass/preamp resets,
reset-all and an output-headroom explanation. Sound processing starts disabled.

Typecheck, clean lint, seven existing JS checks and 24 native unit checks pass.
Twelve physical-phone tests pass (17.862s), including persistent/explicit presets,
PCM smoothing/exact bypass, two independent processors, and real MP3/M4A/FLAC/WAV/
Ogg decoding through the software chain with live edits and no decoder reload.
Transition and sleep-timer phone regressions pass. Test log:
artifacts/phase-4c-device-tests.txt. Standalone build passes (2m30s); APK signature
verifies. artifacts/keyflow-phase-4c.apk / keyflow-test.apk: 47,856,634 bytes,
SHA256 1C30F6EFCF9F2A26646E493996F1900A6EADAB5BB8D6C191EA9EE9E04ACA1429.
Installation and owner checkpoint review pending. No commit or public release.

Update installation succeeds and the app opens directly (cold start 2.004s).
No app-scoped startup errors detected. Owner 4C listening/preset/UI and locked
playback/interruption review requested; 4D remains behind that checkpoint review.

Owner accepted audible effects, smoothing, bypass/resets, preset save/update/
rename/delete/persistence and locked playback/headphone interruption. Reported
that the rotated 10-band sliders let the page scroll instead of adjusting, and
requested all ten bands visible without horizontal scrolling. Replaced these
with a single flexible bank of vertical UI-thread gestures. Each band blocks
the parent scroll gesture while held; page scrolling resumes on release/cancel.
Thumb movement is direct (no decorative animation), with half-dB accessible
increment/decrement actions. Native sound processing/storage unchanged. Typecheck
and lint pass. Corrected APK build and owner gesture/layout review pending.

Corrected standalone build passes (1m3s), signature verifies and installation
succeeds. APK artifacts/keyflow-phase-4c.apk / keyflow-test.apk: 47,860,922 bytes,
SHA256 4369EB7AD6017FB329DDEF62955E2AF4A8EBF5B6E6DA28179F872D8F7F305BE1.
Awaiting owner's vertical-drag/all-bands-visible check before 4D. No commit.

Owner confirmed all ten bands fit, dragging adjusts smoothly without scrolling,
and normal page scrolling works elsewhere. 4C accepted; 4D started.
Expanded transient/impulse/square/multitone headroom fixtures pass at 22.05/44.1/
48/96 kHz. Added real mixed-rate/channel transition and corrupt-preload/manual
failure tests. Found/fixed settings edits while gapless was already enabled
ending a manual tail: only enabling gapless ends it now. Added reduced-motion
support to preset dialogs and full-frequency accessibility labels for bands.
Final regressions/standalone install and accessibility owner review pending.

### Final 4D automated acceptance

Typecheck, clean lint and seven JS regressions pass. Native units: 25 playback
and five library tests pass, including transient/impulse/square/multitone PCM
fixtures across 22.05/44.1/48/96 kHz. All 26 playback phone tests pass (36.085s),
covering the complete queue/recovery/collections/artwork/timer regressions plus
mixed rates/channels with EQ, corrupt preload/manual error propagation and
gapless/settings edits during overlap. Phone log:
artifacts/phase-4-full-device-tests.txt. Final standalone build passes (1m35s),
signature verifies. artifacts/keyflow-phase-4.apk / keyflow-test.apk: 47,861,054
bytes; SHA256 7FE1E4643D9EA6D10209D6C069126E55083059222EC1D143F89ECF1C4D7DB925.
Final installation and owner enlarged-text/reduced-motion check pending. Actual
listening, settings/presets, gestures, locked controls, focus/headphones and paused
recovery were accepted in checkpoints 4A–4C. This validation uses one Redmi Android
16 device; broader Android/version/accessibility/storage/10k scrolling checks
remain tracked for the applicable later acceptance work. No commit or release.

Final update installs successfully and cold-starts without app-scoped errors.
Owner final larger-text/reduced-motion review requested. No commit or release.

Owner confirmed text/controls usable at 110% font size and gestures work with
Remove animations enabled. Found a separate navigation issue: grey flashes on
returning from Playlists/Library settings/Audio and inconsistent detail transitions.
The navigator now receives Keyflow's theme (including native scene backgrounds)
and sits on a themed root surface. All detail routes share slide-from-right;
Now Playing/Queue retain their existing downward sheet motion. Reduced motion
disables detail animations. Replaced Android Alert confirmations with a shared
themed modal for clearing the queue/history, deleting presets/playlists and
removing library sources. Cancel/outside/Back dismiss without performing actions;
only explicit confirmation invokes the existing operation. Folder-rule modals
also respect reduced motion. Typecheck/lint pass. Build/phone review pending.

Navigation/dialog standalone build passes (1m24s); signature verifies and update
installation succeeds. App starts without app-scoped startup errors. APK:
artifacts/keyflow-phase-4.apk / keyflow-test.apk, 47,865,010 bytes; SHA256
B506582D9EDF0C37AC653AA7395DC57AF6034917F5CAAD89F727A2C7B496D143.
Native playback/DSP/data unchanged from the full 26-phone-test pass. Owner review
of back transitions and themed confirmation dismissal/actions pending. No commit.

Owner accepted consistent back transitions/no grey flash in themes and confirmed
dialog dismissal/actions work. Requested a simpler confirmation appearance.
Consulted Material UI's official dialog examples (mui.com/material-ui/react-dialog/).
Removed the decorative trash tile and filled button blocks; use a compact 340px
maximum-width surface, clear title/body spacing and right-aligned text actions.
Queue confirmation uses cobalt Clear and a concise preservation message; deletion
actions retain danger text. Accessible 48px action targets and reduced-motion/
outside/Back behavior stay intact. Typecheck/lint pass; APK/review pending.

Simplified-dialog standalone build passes (1m19s), signature/update installation
and startup pass. artifacts/keyflow-phase-4.apk / keyflow-test.apk: 47,864,810 bytes;
SHA256 E5C81AFCB52DDF946605C077EB22C16F092BB0D319143366488FC1BB01B34C45.
Owner final dialog appearance review pending. No commit or release.

## Phase 4 accepted — 2026-10-12

Owner approved the simplified confirmation dialog ("much better"). All checkpoint
reviews are accepted, including branding/themes, artwork colors/glow, fades and
Repeat one, locked/system controls, focus/headphones, recovery, EQ/presets,
vertical band gestures, text at 110%, reduced motion, consistent back navigation
and confirmation behavior/appearance. Phase 4 is complete. Final installed APK:
artifacts/keyflow-phase-4.apk, SHA256
E5C81AFCB52DDF946605C077EB22C16F092BB0D319143366488FC1BB01B34C45.
Typecheck/lint/build/signature/startup pass; latest native coverage is 30 unit
checks and 26 phone tests, with seven JavaScript regressions. Broader hardware,
storage/accessibility and actual 10k scrolling checks remain explicitly tracked;
this acceptance is on the connected Redmi Android 16 device. Phase 5 has not
started. No commit, tag or public release was created.
