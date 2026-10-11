# Phase 3 — Everyday listening

## Checkpoints

3A adds the authoritative native queue, next/previous, repeat, shuffle and paused
recovery. 3B replaces the player header and adds artwork tint and swipe gestures.
3C adds queue editing, playlists, favorites and recent history. 3D adds native
sleep timers and completes acceptance. Each checkpoint ends with a phone review
before the following checkpoint starts. Public releases remain on hold.

## 3A implementation

Library taps capture the complete filtered/sorted native query, including rows
beyond the currently loaded page. Queue occurrences use independent UUIDs.
Source selection respects membership exclusions and prefers accessible locations.
The player service owns ordering and keeps the current song when shuffle changes;
disabling shuffle restores the underlying order. Repeat cycles off/all/one.
Previous restarts after 10 seconds, otherwise selects the previous entry. System
controls use the same rule. Repeat one affects automatic endings, not manual skips.
Picker playback replaces the queue with one entry.

Room schema 4 adds playback recovery through explicit migration 3-to-4. Serialized
recovery contains all entries, underlying/displayed order, current ID, position,
repeat and shuffle. Writes run serially off the playback thread, after commands
and every five seconds. Restart restores paused and prepares only on explicit Play.
Unavailable/corrupt entries are skipped with a bounded attempt set and an explanation.
A paused queue cannot start automatically while resolving playback errors.

The shared typed bridge provides queue creation from library queries, next,
previous, shuffle/repeat commands, paginated queue reads and queue summary state.
The first checkpoint exposes transport buttons and a queue position count;
the requested header removal and gestures are deliberately checkpoint 3B.

## Acceptance still needed

Checkpoint 3A transport/repeat/full-queue shuffle, system controls, artwork and paused
recovery have passed owner review. Checkpoint 3B layout and gestures also passed
owner review. Phase 3C queue editing and saved collections passed owner review;
Phase 3D sleep timers are installed for locked-screen listening acceptance.
Existing Phase 2 permission changes, unavailable storage, playback during
scanning and large-library scrolling checks remain open; no device-dependent
acceptance is inferred from compilation. The 10,000-entry queue construction/recovery
stress check passed with bounded transactional recovery rows, including database
reopening and Unicode boundaries. Real 10,000-track UI scrolling still needs acceptance.

Original audio remains untouched. Crossfade, analysis, musical controls and matching
remain in their previously scheduled phases. The owner's .vscode deletions are
reserved for a later commit on their instruction.

## 3A verification results

- Type checking, lint and all three JavaScript regressions pass.
- All 14 native unit tests pass (six playback math, three queue ordering, five library).
- Standalone build and APK signature verification pass.
- Phone instrumentation: 13 library tests and four playback tests pass. Coverage
  includes migration 3-to-4, queue projection order across pages, accessible source
  selection, duplicate-entry recovery serialization, malformed saved state and
  MP3/M4A/FLAC/WAV/Ogg decoder regressions.
- Updated app opens over the existing installation with real library data intact.
- APK: artifacts/keyflow-phase-3a.apk; SHA256
  BFC1FB29250E6954001B259F3EDBA70E31DE9028E4BAAACF6421A99B3200AFAF.

Owner checks requested for queue transport, the 10-second Previous rule and locked
system controls. Repeat/shuffle, actual process termination recovery and Phase 2
remaining acceptance still require results. No checkpoint completion is inferred
from serialization tests. Before completing Phase 3, stress-test 10,000-entry queue
construction and recovery; the current recovery JSON uses one Room row, so replace
it with bounded records if its measured size exceeds SQLite cursor capacity.

Owner confirmed queue counts, next/previous, the 10-second restart rule, repeat all,
repeat one, shuffle, lock-screen transport and earbud gestures on the physical phone.
The initial shuffle permuted remaining entries and retained the played prefix;
this behavior is superseded by the full-queue correction recorded below.
Intentional duplicate queue entries remain independent occurrences.

Artwork issue reported during skips: queued metadata only contained artwork cached
when the queue was created; the view could retain a prior image during transitions.
Correction in progress: native current-entry artwork extraction using the shared
bounded cache, cancelled/guarded late results, entry-specific metadata for app and
system controls, and image recycling keys. Added a real embedded-cover fixture test
that also verifies an untagged track clears stale cached artwork. Actual termination
recovery and the artwork correction still await owner acceptance.

Owner confirmed actual Redmi Close all recovery: same song, queue and position
returned paused. Transport/repeat/shuffle/system/earbud acceptance is now recorded.

Artwork correction builds and passes type checking, lint and 14 native unit tests.
Both metadata phone tests passed, including generated embedded APIC cover extraction
and stale-artwork clearing on an untagged track. Updated standalone APK verified,
installed and opened: artifacts/keyflow-phase-3a-artwork.apk (also keyflow-test.apk),
SHA256 E20A106749F8A11C640B57DCBD39F888691AD58F697612636EA1E243EB4921DC.
Owner verification of rapid skips, full/mini/system cover synchronization is pending.
No Phase 3B UI work has begun while this correction is under review.

Owner confirmed artwork now follows current songs correctly. A new issue was
reported: toggling shuffle briefly reloaded playback. Cause: setMediaItems/prepare
rebuilt the live timeline. Correction uses moveMediaItem on existing periods,
preserving current entry and position without preparing the decoder again. Added
an actual muted-decoder test with a 350-entry queue, covering shuffle-on and
restoration after selecting a shuffled entry; buffering/state/position assertions
are measured independently of the intentional track seek.

Shuffle continuity correction: standalone build/signature check and all nine native
playback unit tests pass. New real-decoder phone test passed for a 350-entry queue:
shuffle-on and restoration after changing shuffled entries preserve the current
period/position, stay READY/playing, and produce zero shuffle-related buffering events.
Latest installed APK: artifacts/keyflow-phase-3a-shuffle.apk (also keyflow-test.apk),
SHA256 B61F032B5F2D718D8EAA9A874029FC594464E40AA53156CF052858252960E312.
Owner listening confirmation for repeated shuffle toggles is pending.

Owner reported uninterrupted sound but a freeze after repeated toggles, and requested
full-queue shuffle even when playing near the end. Android exit records showed two
input-dispatch ANRs. Repeated physical playlist moves generated excessive updates;
the earlier single-toggle regression did not cover this failure.

Shuffle now uses Media3 ShuffleOrder with the physical playlist kept in base order.
Enabling shuffle places the current occurrence first and randomly permutes every
other occurrence, including previously played entries. Disabling restores base order
and keeps the current occurrence and playback position. Display pagination, transport,
system commands, error skipping and recovery use the corresponding logical order.

Full-queue correction validation: standalone build/signature verification and ten
playback unit tests pass. The updated real-decoder phone regression passed 100 on/off
cycles on 350 entries starting at physical index 344, asserting a complete permutation,
correct native traversal, unchanged physical playlist/current entry, READY/playing
state and zero buffering. Unit coverage also checks a late entry in a 10,000-entry queue.
Installed APK: artifacts/keyflow-phase-3a-full-shuffle.apk (also keyflow-test.apk),
SHA256 DC900ED0366640000124711CC364362F7ECD278761C24AB5D4A09A43D555692A.
Owner late-entry ordering and repeated-toggle acceptance is pending. Phase 3B has
not begun. No new commit or public release was created.

Owner accepted the full-queue correction: enabling shuffle on a late song makes it
first, disabling returns its original index, and repeated toggles remain responsive.
Checkpoint 3A phone review is complete. Phase 3B is now in progress.

## 3B implementation

Now Playing replaces the branded header/status block with collapse and overflow
icons. Appearance and complete file/title details live in overflow. Artwork adapts
to viewport height, with a restrained tint from cached cover images. Native tint
sampling runs on IO, limits decoded images to roughly 64 pixels per side, rejects
non-cache paths, and keeps a 32-entry color cache. Missing/monochrome art falls back
to the selected theme; stale requests cannot color a later track.

Title space is fixed at two scaled lines and artist at one scaled line. Long titles
truncate without moving the seek bar or transport controls; overflow and accessibility
retain the full title. Compact/small or enlarged-text screens can still scroll.

Artwork swipes left/right invoke native next/previous (including the existing
10-second restart rule); downward swipes collapse to the library. The mini-player
supports left/right transport and upward opening, plus an explicit Next button.
Buttons remain the accessible alternatives. Gestures are limited to their surfaces,
not seek sliders or library lists. Small drag offsets return using a damped spring;
reduced motion disables both drag feedback and option-sheet fades.

Build, artwork-sampling instrumentation, physical layout and gesture acceptance
are pending. Queue editing, playlists/favorites and sleep timers remain 3C/3D.

3B validation: type checking, lint, three JavaScript regressions and ten playback
unit tests pass. The Android standalone build and APK signature verification pass.
ArtworkTintDeviceTest passed on the phone, covering sampled color, cache reuse,
missing files, monochrome fallback and refusal to sample original audio locations.
The first instrumentation attempt used the previous test APK before the build
finished; it was rerun successfully with the newly assembled test package.
Installed APK: artifacts/keyflow-phase-3b.apk (also keyflow-test.apk), SHA256
E81AACCE862672A7FB28222E266129E592C90456B1F99253CB0E183920B9D4E2.
Owner fixed-title layout, gestures and slider/list interaction checks are pending.
Actual reduced-motion and enlarged-text phone checks remain pending as well.

Owner accepted the first 3B layout/gesture functionality, then requested a single-line
scrolling title, complete player/artwork transitions and library tab swiping. This
supersedes the two-line title treatment above. Overflowing titles pause at both ends
and scroll slowly; tapping pauses scrolling. Short titles stay still. Title motion
stops when backgrounded/unfocused and uses a static ellipsis under reduced motion.

The player is now a transparent modal over the retained library. Whole-page drag and
settling provide opening/downward dismissal; collapse and Android Back use the same
exit. Artwork follows horizontal drags, slides out, waits for the native command's
snapshot, then brings in the current cover. A previous-button restart keeps the same
cover. Transition guards prevent repeated gestures from queuing animations. Library
horizontal gestures switch adjacent sections without wrapping, reset group scope,
and retain native pagination/vertical list scrolling. All gesture settling uses the
native animation driver; reduced motion performs immediate actions.

Refinement build and owner motion/title/tab acceptance are pending.

Refinement validation: type checking, lint and three JS regression tests pass.
Standalone build/signature verification passes. Updated APK installed and launched
successfully: artifacts/keyflow-phase-3b-motion.apk (also keyflow-test.apk), SHA256
7153CB7A6502103A80D2D67E11D78B16D452DB99C1D2750EF2208724BAD632ED.
No native playback/DB changes were needed for these refinements. Owner scrolling
title, complete transitions, library tab swipes and touch regression checks requested.

Owner confirmed title scrolling and the complete player/artwork/library transitions.
Requested one title tap to stop/reset to the beginning, with the next restarting.
Removed the paused-position preservation so each stop returns immediately to zero.

Owner also reported the mini-player remaining above its bottom anchor after closing
the player, until another song swipe. Its persistent hook can outlive the animated
native view, retaining an upward expansion gesture's offset. Expansion now resets
before navigation; explicit visibility changes also stop/reset native-driver offsets.
Settling while hidden uses an immediate reset. Type checking, lint and all three JS
regressions pass; standalone build and owner repeated open/collapse checks pending.

Correction standalone build/signature verification passes. Installed and launched
artifacts/keyflow-phase-3b-reset.apk (also keyflow-test.apk), SHA256
440293737E110AFDA111E9E492843F9598D7392C0828567B32FD1BB53CA0F3CC.
Owner one-tap stop/reset, restart and repeated mini-player open/collapse acceptance
requested. No 3C work or new commit started during this correction.

Owner confirmed the mini-player positioning fix. Title stop still held its offset;
the next tap reset it. A zero-value update on the same native animation node was
insufficient in the phone test. Paused titles now render an ordinary, untransformed
Text view at the beginning; restarting mounts a scrolling view with a fresh zeroed
animation node. Late stop callbacks cannot update the replacement node. This is a
UI-only correction; build and one-tap phone acceptance are pending.

Static-title correction: type checking, clean lint, three JS regressions, standalone
build and signature verification pass. Installed/launched artifacts/keyflow-phase-3b-title.apk
(also keyflow-test.apk), SHA256
7267A8DC05B0EA5E54653CE928B289502F1395F5FBFFBBB74C188C0E819E668F.
One-tap beginning/reset and restart phone acceptance requested. Mini-player acceptance
is complete; Phase 3C remains unstarted while this title refinement is under review.

Owner confirmed one-tap title beginning/reset and restart. Checkpoint 3B functional
review is complete, including mini-player positioning. Accessibility checks remain
part of final acceptance. Phase 3C starts with queue editing, followed by saved
collections; no completion of playlists/favorites/history is implied by this first build.

## 3C queue editing implementation

New Queue route reads revision-consistent native pages of 50 entries, starting with
the current entry; earlier pages and a From beginning option expose played history.
Queue rows have independent occurrence IDs, tap-to-play and menus for Play next,
Move up/down and removal. Library song menus insert new occurrences via Play next
or Add to queue. Clearing upcoming requires a queue-only confirmation and preserves
the current song and earlier displayed entries. Full-player queue count and upward
artwork swipe open Queue; the library header also provides direct access.

Physical playlist edits use Media3 insertion, single-item moves and range removals,
preserving the current period instead of rebuilding/preparing the queue. Shuffled
reorders affect display order; disabling shuffle restores base order. Play next stores
explicit pending priority, includes it ahead of randomized entries when enabling
shuffle, and consumes it when the occurrence becomes current. Pending priority is
serialized compatibly in version-1 recovery JSON; old snapshots default to no priority.
Sparse clears group removals and yield between batches to keep main responsive.

Type checking, lint and JS regressions pass. Focused native mutation/priority/recovery
and real-decoder continuity checks, standalone build and owner review are in progress.
Playlists, Save queue as playlist, favorites and history remain outstanding 3C work.

Queue editing validation: type checking, lint, three JS regressions and 14 playback
unit tests pass. Real-decoder edits and three recovery tests passed on the phone.
The existing 100-cycle shuffle stress test exceeded its 2-second operation budget
in the combined run (4277 ms); isolated rerun passed without changing its threshold.
The 350-entry edit test preserves READY/playing/current position through insertion,
reordering, removal and clearing, with zero buffering events. Build/signature checks
pass; installed/launched artifacts/keyflow-phase-3c-queue.apk, SHA256
2D0A261B3B397842D54508A5D9D5EF140FA9ACFA3B39D6CCB5C915F92F4CD986.
Owner queue editing and duplicate/Play next acceptance is pending. Collection storage
preparation continues independently while the owner reviews this first build.

## 3C saved collections and queue interaction refinement

Owner accepted queue insertion, duplicates, removal, jumping and uninterrupted edits.
Requested FIFO Play next: successive A/B/C additions now stay in that addition order,
including across shuffle toggles and recovery. Pending occurrence IDs are consumed
when played; manually moving one removes its pending priority. Unit coverage includes
successive insertion, an existing-entry move, consumption and recovery.

Queue row menus are replaced with bilateral swipe removal and reorder handles.
The screen owns the drag responder and floating preview, so virtualization can remove
the source row without ending a drag. Edge scrolling operates within the loaded window
and loads further pages; Show earlier/From beginning expose earlier entries. Handles
also provide accessibility move/remove actions. Downward dismissal is available on
the header and at the top of the list, preserving normal scrolling elsewhere. Collapse
returns to the library mini-player; its upward swipe reopens the last collapsed queue
or player, while tapping the song continues to open Now Playing.

Room schema 5 adds normalized playlists/independent entries, song favorites and recent
history, with an explicit 4→5 migration and exported schema. Saved entries retain audio
references when storage is unavailable. Playlists preserve duplicate entries and the
entire captured displayed queue order, including played/current/upcoming entries and
shuffle ordering. Temporary musical presets are not part of this checkpoint.

Saved screens query native pages of 50 items. Playlists support creation, rename,
deletion, adding from library/player, entry reorder/removal and native queue playback.
Editing a saved playlist does not rewrite the active queue. Favorites are available
from the player heart and library menus. Recently played records actual native playing
events (including background playback), deduplicates songs by identity, retains the
latest 500 songs and supports clearing. Room invalidation publishes separate collection
events. Selected saved audio is validated before replacing playback; upcoming library
entries choose current grants without opening every audio file in advance.

Type checking, lint and three JS regressions pass. Twenty native unit tests pass
(five library, fifteen playback). Nine phone collection/scan/migration tests and four
decoder/recovery tests pass. The 10,000-entry playlist test validates bounded query
pages; it does not establish 10,000-row physical UI scrolling performance. A separate
saved-shuffle/duplicate/selected-occurrence playback test and final standalone build
are in progress. Owner gesture and collection review remain required. Phase 3D sleep
timers and final accessibility/storage/large-queue acceptance are not started here.

Final checkpoint build succeeds and signature verification passes. The additional
saved-display-order/duplicate/selected-occurrence test passes together with decoder
edits and recovery (five playback device tests); the nine library device tests also
passed. Standalone artifacts/keyflow-phase-3c.apk (also keyflow-test.apk) is built;
USB installation is waiting at the phone's Play Protect dialog. APK size:
47,459,403 bytes, SHA256
BCBB193CE76913F601FA7E9FD109D3758461DFA5E0775F9949345354EA5EF2EB.
Owner FIFO order, drag/swipe/collapse, saved-collection persistence and background
history acceptance remain pending installation. No commit or public release was created.

## 3C interaction polish after owner review

Owner reported working queue edits but lagging drag/swipe motion, missing neighboring
row displacement, distracting position numbers, queue dismissal dropping Now Playing,
and unclear saved-collection discovery. Requested mini-player transport simplification.

Queue gestures now use Gesture Handler and Reanimated UI-thread worklets. A floating
song follows the finger; surrounding rows spring out of its way as the insertion gap
changes. Edge scrolling runs on the UI frame callback. React changes only at drag
start/commit, and memoized rows receive stable transport callbacks. Swipe translation
and dismissal also run on the UI thread; removal uses a short list layout transition.
Loaded native pages are preserved during refresh, avoiding truncation of a scrolled
window after edits. Reduced-motion alternatives and accessible reorder/remove actions
are retained. Position numbers and drag-target labels are removed; Queue shows its
total song count, and Now Playing exposes an icon-only queue action.

Queue back/down now pops only Queue, preserving an underlying Now Playing screen.
Library includes explicit Favorites, Playlists and Recent shortcuts. The mini-player
keeps only play/pause as a visible transport control; skip gestures remain available.

Reference patterns: Spotify's documented hold/drag queue handles
https://support.spotify.com/xk-en/article/play-queue/ and YouTube Music's Library
playlist access https://support.google.com/youtubemusic/answer/7205933?co=GENIE.Platform%3DAndroid&hl=en.
Type checking, lint and three JS regressions pass. First packaging attempt failed
after bundling; the diagnostic retry and final build both succeeded. Final standalone
artifacts/keyflow-phase-3c-motion.apk (also keyflow-test.apk) passes signature verification.
Size: 47,592,575 bytes; SHA256:
29630F64FFE891AA324ACA9973D7DA13A78822C803470BFE018DDCBEAF472F74.
USB installation succeeded after owner closed Play Protect. Keyflow and the Queue
route were opened; the app remained foreground/running with no app-scoped JavaScript,
Worklets, Reanimated or AndroidRuntime errors in the smoke check. Owner drag/swipe
smoothness, navigation and collection-discovery checks remain pending. No checkpoint
completion, commit or release is implied.

## 3C resumed drop handoff and compact Library — 2026-10-12

Owner resumed after a break and reconnected the phone. Previous drag movement was
better, but release still lagged: the floating row waited for a sequential native
move, a full loaded-window refresh and two JS frames after its settle animation.
The display window now reorders at drop while the native command runs concurrently
with the 160 ms settle. Successful revision events confirm the order; rejected
commands reload native state. Pending old page responses are invalidated at the
presentation update. Duplicate occurrence IDs and global indices are preserved.
The floating row no longer waits for full-page reconciliation to disappear.

Library uses logo + Library in one top bar, search, then Songs/Albums/Artists/Folders/
Favorites tabs. The separate heading/Saved row and three collection shortcuts are
removed. A top-bar playlist icon opens Playlists, which retains Favorites and Recent
access. Favorites is also directly browsable and playable in the new tab. Its search
uses native paginated queries, including songs beyond the first page; LIKE wildcard
characters are escaped. This query-only change requires no database migration.

Native confirmations now retain unchanged row objects and the unchanged display
array, avoiding unnecessary memoized-row redraws. Display moves also preserve row
objects outside the moved range. Metadata/priority changes still update correctly.

Type checking, lint and five JS checks pass, including display reorder with duplicate
song occurrences, a loaded window starting at index 300, and native confirmation
reference reuse without losing metadata/priority changes. Twenty native unit tests
pass. All four CollectionsDeviceTest checks pass on the phone, including native
Favorites search beyond the first page, literal wildcard characters, persistence,
migration and the existing 10,000-entry playlist query test. Both APK builds succeed;
the final row-reuse APK passes signature verification. Artifact:
artifacts/keyflow-phase-3c-drop.apk (also keyflow-test.apk), 47,819,995 bytes;
SHA256 DD02C49FAD7BC7FDD5A6BF981C5B73FE9F4FD9DEFA897A5E209ACB1CCABF732F.
USB installation succeeded after owner closed Play Protect. The updated app opened
with Queue visible and remained running without app-scoped AndroidRuntime,
ReactNativeJS, Reanimated or Worklets errors. A Keyflow frame baseline was reset
for the owner gesture review. Owner physical motion/layout review remains required;
the runtime smoke check does not establish animation smoothness. No commit or release.

## 3C dedicated queue interaction component — 2026-10-12

Owner accepted Library/Favorites/Playlists layout but reported a remaining queue
pause/jump/gap and suggested using a suitable UI library. The custom floating drag
overlay and hook are removed. Exact react-native-reorderable-list 0.18.1 now owns
animated cells, drop settlement, measured displacement and edge scrolling. Library
reference: https://github.com/omahili/react-native-reorderable-list . It uses the
existing Gesture Handler/Reanimated peer dependencies and adds no further packages.

Hold a stack handle for 140 ms to start dragging; list pan activation is delayed to
160 ms so normal scrolling and row swipes remain available. Settlement is 180 ms,
zero under reduced motion; default scale/opacity decoration is disabled. Native
commands remain authoritative. Local presentation updates and native confirmation
reuse continue; rejected edits refresh the native window. Swipe removal now deletes
the presentation row when its slide finishes instead of leaving an empty cell until
native page reload. Stable removal callbacks and animation configuration avoid
rebuilding row interactions on playback ticks. Accessibility actions are retained.

The package's full MIT notice is bundled in native assets/licenses/
react-native-reorderable-list.txt. Type checking and clean lint pass, and six JS
checks pass, including duplicate-occurrence swipe removal in a paged window.
Standalone build/signature verification pass and the update is installed. Queue
mounts without app-scoped AndroidRuntime/ReactNativeJS/Reanimated/Worklets errors.
Artifact: artifacts/keyflow-phase-3c-reorder-library.apk (also keyflow-test.apk),
47,868,982 bytes, SHA256
8470F4562170795516FCB62A37EB979D2F91DCF7B1CFF9D3C00A27B83425017C.
The license notice is verified inside the APK. Owner drag/swipe/edge-scroll and
navigation acceptance remain pending; frame baseline reset for that review. The previous
Keyflow-wide frame sample (7,148 frames, 261 deadline misses / 3.65%, 99th percentile
61 ms) includes multiple screens and is not an isolated queue benchmark.

Owner phone review accepted smooth drag releases and swipe gap closing, with no
remaining pause/jump/gap; normal scrolling is confirmed. The follow-up noted thin
row outlines. Removed the hairline separator and made the removal-action background
transparent at rest, preventing rounded antialiased edges from revealing it. The
current-song highlight remains. Borderless styling APK/checks are in progress. No
new tests are added for this cosmetic change; the six interaction regression checks
and native tests from the previous build remain applicable. Closing from Now Playing
was not explicitly answered in the latest reply.

Borderless refinement: typecheck/lint, standalone build and APK signature checks pass.
Installed artifacts/keyflow-phase-3c-borderless.apk (also keyflow-test.apk),
47,869,346 bytes, SHA256
3BFB0C907D461D1B1CD0CA1082E626891AE9396D0D9EA7CC0EB0CF9C257782F8.
App reopened without app-scoped runtime errors; owner active navigation prevented
an isolated queue screenshot check. No commit or release.


## 3D — Sleep timers and final checks (2026-10-12)

Owner confirmed Queue closes to the retained Now Playing screen with both Back and
swipe-down. Playlists/favorites survive reopening, and Recently played can start
playback. Combined with accepted queue motion/scrolling and saved collection layout,
checkpoint 3C functional phone review is complete.

Native SleepTimer belongs to the playback service and exposes typed authoritative
mode/remaining-time state. Player options offer 5/15/30/45/60 minutes, end of current
song, and cancellation; the footer shows the active timer. Timed deadlines use Android
elapsed realtime and continue while paused. End timers use Media3 pause-at-end,
overriding automatic repeat/advance without altering repeat settings. Manual skips or
replacing the selected song cancel end timers; timed timers continue across tracks.
Timers expire once and are cleared on service/process termination, while the queue
restores paused as before. Original audio is unchanged.

Recovery previously serialized the entire queue into a single SQLite row. It now
writes numbered 128 Ki-character chunks in one transaction and joins ordered rows
when restoring; UTF-16 surrogate pairs stay together. This uses the existing table
and schema, reads legacy single-row snapshots, and removes obsolete chunks atomically.
A phone regression uses an isolated database with 10,000 entries and a snapshot over
2 MiB, closes/reopens it, verifies order/current/position/repeat/shuffle and replaces it
with a short snapshot. It also tests a Unicode pair exactly at a chunk boundary.
No fixture songs are added to the actual library.

Typecheck/lint and six JS checks passed; all twenty native unit tests passed. First
standalone build passed. Final Unicode-safe build and phone timer/recovery tests are
in progress. No commit/tag/release, and the owner's .vscode deletions are untouched.
Remaining acceptance: actual screen-locked timer listening, reduced motion/enlarged
text, physical permission revocation/storage reinsertion, real scanning while playing,
and 10,000-track scrolling (distinct from database/query/serialization fixtures).


3D final build/signature verification passed (2m18s). All seven timer/recovery phone
tests passed in 7.479s, including repeat off/all/one end timers, timed one-shot expiry,
cancellation/replacement/invalid input, legacy snapshots and the 10,000-entry SQLite
reopen/Unicode boundary fixture. Updated standalone APK installed and launched without
app-scoped runtime errors: artifacts/keyflow-phase-3d.apk (also keyflow-test.apk),
47,888,074 bytes, SHA256
C8358F996690AD08481251B5D4B25C0414B3A68DACBF9DB387B44466E76BA5A2.
Owner locked end/timed timer, cancellation and scanning/playback acceptance requested.
Decoder/shuffle/queue-edit regression phone tests are running. No completion of
outstanding hardware/accessibility checks is inferred from fixtures.


All four additional physical-phone regression tests passed (16.59s): five supported
audio formats/end-replay/corrupt errors, 100 full-queue shuffle toggles, and native
queue insert/move/remove/clear continuity. Eleven phone checks passed in total for
this 3D update. Owner listening results remain pending; no commit or release.


### 3D owner acceptance and notice correction — 2026-10-12

Owner confirmed end-of-song timer stops while locked even with Repeat one, normal
resume afterward, five-minute locked timer expiry, cancellation, and uninterrupted
manual scanning with locked playback/system controls. Phase 3C/3D functional owner
checks have passed. Owner reported the prior "Sleep timer finished" notice surviving
when setting another timer. The native service now clears that specific notice only
after a successful timer command, so invalid commands preserve existing state and
unrelated playback warnings remain visible. The correction build is in progress.

No new tests for this small notice lifecycle fix; timer/decoder/recovery checks from
the previous APK remain applicable. Remaining wider acceptance includes reduced
motion/enlarged text, actual permission revocation/SD removal and reinsertion, and
10,000-track UI scrolling. No commit/tag/public release.


Notice correction build/signature checks passed (1m36s). Installation succeeded after
Play Protect closed; Keyflow opened without app-scoped runtime errors. Latest APK:
artifacts/keyflow-phase-3d-notice.apk (also keyflow-test.apk), 47,888,074 bytes,
SHA256 45357A92365F85DF99A5C46A2F3CC5960C1EC1F4481420EEB656316CF95080EA.
Owner functional timer/scanning results are accepted; visual notice reset awaits
confirmation if reported again. No commit or release.


### Phase 3 implementation milestone — 2026-10-12

The everyday-listening feature work and functional phone reviews are complete:
authoritative native queues, repeat/shuffle, paused recovery, queue edits/FIFO Play
next, saved playlists/full displayed queue, favorites/recent history, sleep timers,
and owner-approved player/library/queue interaction improvements. Automated checks
and standalone APK verification passed as recorded above. Wider physical storage,
accessibility and 10,000-track UI scrolling checks remain explicitly open.

Owner authorized committing the Phase 3 milestone and both .vscode file deletions.
No push, tag or public release is part of this commit request.
