# Phase 2 implementation and validation

Updated 2026-10-11. Internal checkpoint APKs are under artifacts. No public
release or tag is planned before 1.0.0. Original audio files remain untouched.

## 2A — database and interfaces: verified

Separate Kotlin Expo module; Room tracks, physical identities, source-specific
locations, folder rules and scan checkpoints. Exported schemas and explicit
migrations; no destructive fallback. Native FTS4 search, group/track queries
(default 50, maximum 100), stable ordering, total counts and access-checked
playback resolution. Playback remains authoritative in its background service.

Three phone database tests and two query unit tests passed. An isolated
10,000-track fixture returned its final 50-row page in 134 ms on the owner's
Android 16 phone in one measured run. Both single and multi-term prefix searches
passed. These fixtures are never inserted into the production library.
Standalone checkpoint: artifacts/keyflow-phase-2a.apk.

## 2B — scanning: native checks passed

Version-appropriate audio permissions, MediaStore volume scanning and persistent
local folder grants. One native scan, transactional batches of 25, throttled
progress, unchanged-file metadata reuse, include/exclude rules, cancellation and
successful-source-only missing-file reconciliation. Stable IDs and unavailable
references are preserved. Interrupted scan state restarts reconciliation.
Room version 2 adds saved scan state through explicit migration 1-to-2.

Five library unit tests and five initial device tests passed, including migration
and real MediaStore scan/rescan/cancel. Audio access was allowed through Android's
normal dialog. Permission-unavailable state was modeled in a test DB; actual OS
revocation and folder/card behavior remain manual acceptance checks.
Standalone checkpoint: artifacts/keyflow-phase-2b.apk.

## 2C — library screens: installed, owner feedback pending

Library home has paginated Songs/Albums/Artists/Folders, debounced indexed search,
six song sorts and direction, group drilldown and fallback file picking. Shared
playback spans navigation; the persistent mini-player opens the full player.
Lists are virtualized and avoid playback position-tick updates. Artwork requests
use two interface slots, a 300-entry memory cache, two native slots and a 64 MB
disk cache with bounded absence markers.

Settings manage sources, access renewal, scans/cancellation, folder rules and
saved appearance. The installed screen was inspected with 381 actual local songs
and embedded artwork. Type checking, lint, JS playback regression tests and
standalone build passed. APK signature, minimum API 24 and target API 36 verified.
Standalone checkpoint: artifacts/keyflow-phase-2c.apk.
Owner browsing/search/navigation/playback feedback is pending.

## 2D — validation in progress

Eight extended device tests passed: persistence, overlap, search/pagination,
10,000-track query benchmark, local-file availability, sorting/album grouping,
disabled-source reference preservation, migration and real scan/rescan/cancel.
Metadata/artwork fallback checks for MP3, M4A, FLAC, WAV, Ogg and corrupt files passed. Persisted interrupted-scan recovery preserved track IDs. Automatic MediaStore index-change refresh is included in the updated standalone candidate, artifacts/keyflow-phase-2d.apk. The build and signature checks passed; manual acceptance remains pending.

## Remaining physical acceptance

- Browse/search/play from all groups; mini-player stays synchronized.
- Restart and check persistence, folder grants and picker cancellation.
- Add overlapping folder sources; exclude/include; confirm counts and identities.
- Deny and revoke OS audio access; renew a folder grant safely.
- SD-card removal/reinsertion and disconnected-source reconciliation.
- Change/add a file, rescan, and verify metadata/identity refresh.
- Cancel or interrupt a scan without marking the collection missing.
- Playback, seeking, lock-screen/system controls and earbud disconnect while scanning.
- 10,000-track UI scrolling (query benchmark alone does not establish this).

Additional Android versions and removable storage require corresponding hardware.
Do not mark Phase 2 complete until outstanding acceptance checks are recorded.


The updated standalone candidate was installed successfully on the connected
phone and launched after its Play Protect consent dialog closed. Manual
acceptance feedback remains pending.


## Owner feedback incorporated

The owner confirmed Songs/Albums/Artists/Folders, search, library playback and
mini-player navigation work. Automatic rescanning is now an opt-in, saved setting
(default off); returning to the app checks access without rescanning. Manual
scans, source/rule changes, and interrupted requested-scan recovery remain.
Removed file selection from the full player; Open a song remains on the library.
Preference regression validation and updated installation are in progress.


The focused phone regression for opt-in scanning passed (default off, persisted
preference, and disabling pending refresh). Typecheck, lint, JS regressions,
native unit tests, standalone build and APK signature verification passed.
Updated installation is in progress.


Updated APK installed successfully. Requested owner acceptance for default-off
rescan behavior, simplified player, overlapping folder scans and saved folder
access. These new manual checks remain pending.


## UI/UX and folder-rule correction in progress

Owner confirmed default-off automatic scanning, duplicate-free overlapping folder
access and saved folder grants. Requested compact icon controls and corrected
folder exclusions before advancing phases. Library header/sorting/settings are
redesigned, with outside-tap dismissal and selected-sort direction reversal.

Folder rules now filter native queries immediately rather than forcing rescans.
Library-wide folder visibility uses Room schema 3 with explicit 2-to-3 migration;
source-specific rules remain available. Library-wide rules prevent overlapping
sources from bypassing exclusions, preserve missing-track references, and support
child overrides. A local folder picker adds exclusions independently of sources.
Native change events invalidate visible library pages after rule edits. Folder
controls preserve their list while refreshing saved rule state.
Build, migration/exclusion regressions and visual phone checks are pending.


UI update built and installed successfully. All eleven extended native library
phone tests passed, including new exclusion/migration regressions and the
10,000-track query benchmark. Typecheck, lint (no warnings), JS tests, five native
unit tests and APK signature checks passed. Real library screen inspected with
381 songs. Owner touch checks requested for sorting and folder visibility; Redmi
denied adb input injection, so no automated touch pass is claimed.


The refreshed library and settings screens were both visually inspected on the
phone in Dark appearance. Library still reports 381 actual songs after migration;
existing device/folder sources and their saved grants remain visible. Owner
interaction acceptance is pending; no phase advancement has been started.


## Owner UI acceptance passed

The owner confirmed outside-tap sort dismissal, reversing the active sort field,
and folder exclusion/re-inclusion without scanning all work. The owner also
confirmed the revised layout is better. These UI corrections are verified on the
physical phone. Outstanding Phase 2 acceptance remains actual permission
denial/revocation, unavailable/removable storage, changed-file refresh, playback
regressions while scanning, and 10,000-track scrolling. Phase 3 has not started.
