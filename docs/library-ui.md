# Library interaction conventions

The original Keyflow logo and shared green light/dark palette are retained.
Library pages use a compact header, 44-point labeled icon controls, restrained
surface grouping, consistent rounded corners and visible pressed states.
Navigation tabs keep labels alongside icons. File picking is an icon in the
library header; the full player contains playback controls.

Sort menus dismiss with outside taps or Android Back. Selecting a different
field starts ascending; selecting the active field reverses direction. An arrow
indicates the selected direction. There are no Done or direction-toggle buttons.

Library settings separates scanning, source access, visibility and appearance.
Automatic scans remain opt-in and saved, default off. Destructive-looking source
removal explains that original audio stays untouched.

Folder visibility applies library-wide, including overlapping sources. Switches
control inclusion and reset icons remove explicit overrides. Changes take effect
through native queries immediately and do not request a scan. The + icon uses
Android's local folder picker to add an exclusion. Folder search matches folder
names/paths. Source-specific filters remain available through each source's
filter icon; global exclusions continue to apply to those sources.

Changing visibility never modifies/deletes audio or erases a track's identity.
Missing files can also be excluded and later restored to view. Current playback
continues independently of library visibility. Child rules override parent rules
within the same scope. Global inclusion does not enable a disabled source or
remove an existing source-specific exclusion.
