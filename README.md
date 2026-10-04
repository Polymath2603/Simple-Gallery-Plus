# Gallery+ (Simple Gallery fork)

A fork of [SimpleMobileTools/Simple-Gallery](https://github.com/SimpleMobileTools/Simple-Gallery)
(Pro 6.28.1, base upstream commit `a3564013b`) with quality-of-life fixes, new
features, a modernised icon set, and upgraded dependencies. No ads, no
trackers — all media stays on-device.

## Features added by this fork

### Tree mode — root folders in the main list, subfolders inside the media view
Tree mode reuses the **exact same UI and click handling as folder mode** —
same adapter, same tiles, same open behaviour. The only difference is the
list contents and where subfolders appear:

- **Main list shows root folders only.** Folders that are descendants of
  other listed folders are hidden, so the screen mirrors the top level of the
  storage hierarchy.
- **Child folders appear inside the media view.** Opening a folder lists its
  media with its child folders on top (recursive — each opened folder shows
  its own children again). Subfolders are scanned from the filesystem, only
  shown when they directly contain media and are not hidden, and are sorted
  above the files. Each child renders with the **same folder tile as the
  folders list** (shared renderer — same grid/list styles, thumbnail, media
  count, pin/lock badges) and behaves the same: tap opens it, long press
  starts selection.
- **Subfolders are selectable in the contextual menu.** Folder and file
  selections follow one rule: file-only operations (rotate, edit, share,
  favorites, …) act on the selected files, folder operations act on the
  selected folders, and in a mixed selection each operation applies only to
  the part of the selection it understands (rename/delete/hide/copy/move
  run both flows).
- **The toolbar shows a breadcrumb** of the path relative to the folder the
  screen was opened with, e.g. `Camera / 2024 / Trip`.
- **Back walks up like a file manager:** it always returns to the parent
  folder and scrolls back to the folder you came from, stopping at the root
  folders where it exits to the main list. Decrypted temp copies map back to
  their real path, so the hierarchy stays consistent.
- **Mode switch button.** The toolbar button always shows the *current*
  mode: folder icon (folders), double-folder icon (tree), image icon
  (all media). Tapping it cycles to the next mode **in place — no activity
  restart, so the app lock is only ever asked at launch, never on a mode
  switch.**
- **Locked/encrypted child folders** ask for their own credentials when
  tapped; encrypted ones decrypt into a private temp folder like on the main
  screen.

### Media-type filter applies to the main lists only
The Settings → *Filter media* types now scope the **main browsing lists**
(folders screen, folder screen, search). Places that must always show
everything are no longer affected by the filter:

- **The image/video viewer** (`ViewPagerActivity`) — all media types stay
  browsable/swipeable even if filtered out of the grid.
- **The media picker** (`PickMediumDialog`, e.g. "Select photo" from another
  app) — offers every supported type.
- **`PhotoVideoActivity`** (third-party "open with" single-file view) — never
  blocks a file because of the filter.
- **Database rescans** (`updateDirectoryPath`, `rescanFolderMediaSync`, boot
  rescan) — fetch unfiltered so items hidden by the filter are still rescanned
  and don't get wiped from the local DB.

Implementation: an `applyFilterMedia` flag is threaded through
`MediaFetcher` / `GetMediaAsynctask` / `getCachedMedia`; when false the fetcher
uses `getAllTypesFilter()` (every supported media type) instead of the user's
filter. Copy/move/delete operations are untouched.

### Editable folder lists (Excluded / Included folders)
The Excluded Folders and Included Folders screens now have a per-element
**Edit** action alongside Remove. Tap the 3-dot icon on any folder row →
"Edit folder" opens a dialog pre-filled with the full path; the folder is
updated in the config and the list refreshes immediately. Previously you had
to remove and re-add a folder to change its path.

**Path validation (new):** on save, the dialog checks the new path —

- if it exists and is a **file**, a toast ("This path is a file, not a
  folder") blocks the save;
- if it **doesn't exist yet**, a confirmation ("Save the path anyway?") lets
  you store paths for folders you're about to create;
- existing folders save straight away as before.

### Disable lock-screen vibration
New Security setting: **"Disable vibration on lock screen"**. Turns off the
haptic feedback of the pattern lock (per-dot and locked-out vibrations) and
the PIN lock keypresses. Implemented in the vendored Simple-Commons
(`DISABLE_LOCK_HAPTIC_FEEDBACK` config key, gating `PatternTab`/`PinTab`) and
exposed in the app's Settings → Security; the value is included in the
settings import/export.

### Folder encryption built into the lock flow
Encryption is a toggle inside the folder lock flow — no separate menu items.
Long-press a folder → **Lock folder** and check **"Also encrypt folder
contents"**: the pattern/PIN set in the lock dialog is used as the encryption
key material (`AES-256-GCM`, PBKDF2-derived key; files are renamed to
`<name>.enc`). **Unlocking the folder automatically decrypts it** with the
same secret. While a folder is encrypted:

- opening it asks for the lock pattern/PIN and decrypts on the fly into a
  private `cache/temp_decrypted/` folder (cleared on exit) — media plays
  exactly as usual;
- copying/moving files **into** an encrypted folder encrypts them, copying/
  moving **out** decrypts them automatically;
- in folder grids the `.enc` files are surfaced so encrypted folders don't
  appear empty.

### Menu customization
Settings → **Menu customization** lets you show/hide individual menu items
separately for each screen context:

1. main screen, no selection
2. menu inside a folder, no selection
3. selected folders (long-press CAB)
4. selected images (long-press CAB)
5. opened image in the viewer
6. opened video in the viewer

Hidden items are stored per context and included in settings import/export.
Backups from older versions (separate image/video folder-menu sets) are
migrated into the single inside-folder set automatically.

### Keep screen on (temporary)
The media screen menu has a **Keep screen on** toggle. It only lasts while
that screen is open — leaving the media view (back press or destroy) reverts
it automatically.

### Accurate video seek bar
The video players (`VideoFragment` and `VideoPlayerActivity`) now seek with
ExoPlayer's exact-frame `SeekParameters.DEFAULT` instead of
`CLOSEST_SYNC`, which snapped every seek to the nearest keyframe (dragging
to 10s could land at 8s). Seeks now land exactly where you point.

### Eye-icon hidden-files filter ignores Android system folders
The eye toggle ("temporarily show hidden items") shows hidden *user* folders
while keeping Android's restricted `/Android/data/...` system folders out of
the list, and no longer leaks excluded folders into the folder picker.

### Video playback: local-file ExoPlayer fix
`VideoPlayerActivity` and `VideoFragment` now build the ExoPlayer media source
from a local `File` URI via `DefaultDataSource` when the path is a plain
filesystem path (not a `content://` URI). This fixes video playback on
non-standard storage — OTG-mounted drives, custom `includedFolders` paths, or
any directory the Android content resolver cannot resolve. Previously these
scenarios produced a playback error or black screen.

`ViewPagerActivity` now resolves the play URI through `getFinalUriFromPath`
for non-content schemes and passes the real path and MIME type to the player
intent instead of relying on the content resolver alone.

### Material Design icons (30+ vector drawables replaced)
All major toolbar and menu icons replaced with crisp Google Material Icons
vectors: camera, delete, edit, share, sort, filter, copy, crop, check, undo,
undo outline, set-as, settings cog, star, star outline, info, hide, unhide,
label, lock, folder, folders, files, column count, drag handle, three dots,
orientation auto, play outline, pause outline, plus, pin, pin filled, place,
next outline, move, move to top, move to bottom, Google Play. Also added
`ic_set_as_vector` (not present upstream).

### Small fixes upstream hadn't applied
- **`FilterThumbnailsManager`**: fixed the bitmap assignment line
  (`filterItem.bitmap =`).
- **`PicassoRoundedCornersTransformation`**: rounding-calculation fix.
- **`SvgModule`**: SVG drawable loading fix for icons that need it.

### UI strings added
Save / Save copy / Overwrite / Resize / Random / Lock folder / Edit folder /
Move to top / Move to bottom / Create shortcut / Exclude / Unlock folder /
Unpin folder / Change cover image / Select photo / Use default / Editor /
Basic Editor / Advanced Editor / Rotate / Flip horizontally / Draw / Color
picker / Crop / Save as / Set as wallpaper / Image edited successfully / Video
editing failed / Switch to tree view / tree-mode strings / lock-vibration
string / edit-folder validation strings — and more. These support the features
above and are not present upstream.

### Debug
Debug launcher name is **Gallery+** (upstream uses `Gallery_debug`).

## Simple-Commons is vendored in this repo
The [Simple-Commons](https://github.com/SimpleMobileTools/Simple-Commons)
library (v5.34.26) is **included in this repository** under `Simple-Commons/`,
so the project builds standalone — no separate checkout, no `mavenLocal()`
publish step.

- Wired as a **Gradle composite build**: `settings.gradle.kts` uses
  `includeBuild("Simple-Commons")` with a dependency substitution
  `com.simplemobiletools.commons:release` → `:commons`, so Gradle builds the
  commons source on demand.
- Commons source changes made by this fork: the lock-haptic-feedback setting
  (see above) and the `rtl-viewpager` dependency coordinate fixed from the
  dead `com.github.duolingo:940f12724f` jitpack build to the live
  `com.github.naveensingh:rtl-viewpager:2.0.2` (same code lineage).
- Commons' AGP is aligned with the app (8.6.0) so the composite build works.
- Vendored under its original **GPL-3** license, see `Simple-Commons/LICENSE`.

## Build and dependency upgrades

| Component      | Upstream        | Fork        |
|---|---|---|
| Kotlin         | 1.8.22          | **1.9.22**  |
| KSP            | 1.8.22-1.0.11   | **1.9.22-1.0.17** |
| Room           | 2.6.0-beta01    | **2.5.2** (stable) |
| AGP            | 7.4.0           | **8.6.0**   |
| compileSdk     | 34              | **35**      |
| Simple-Commons | SNAPSHOT (`9e60e24790`) | **5.34.26** (vendored, composite build) |
| Gradle wrapper | older           | **8.7**     |

### Gradle and build config changes
- **`gradle.properties`**: JVM heap 2 GB, max metaspace 512 MB, parallel build
  off, daemon off — builds cleanly on constrained CI and low-RAM containers.
  (Upstream used 8 GB heap and kept parallel/daemon on.)
- **`settings.gradle.kts`**: added Aliyun public/jcenter mirrors, jitpack and
  `mavenLocal()` as fallback repositories, because jcenter.bintray.com is gone
  from default Gradle resolution; added the Simple-Commons composite build.
- **`gradle-wrapper.properties`**: updated to Gradle 8.7.
- **`build.gradle.kts`** (root): removed unused `id("com.android.application")`
  at root level (only the app module needs it).
- **`app/build.gradle.kts`**: `compileSdk 35`, `targetSdk 34`, debug signing
  uses `~/.android/debug.keystore` with apksigner v2+v3, removed the old
  `signingConfigs` block.
- **Room schema export** committed at `app/schemas/…/10.json` (used by
  `room.schemaLocation` in the build).

## Requirements

- Android 6.0 (API 23)+
- Android SDK 35 to build; JDK 17
- No external prerequisites — Simple-Commons is vendored in `Simple-Commons/`

## Build

```bash
./gradlew assembleFossDebug      # foss flavor → app/build/outputs/apk/foss/debug
./gradlew assembleRelease        # requires signing config
```

On the first run Gradle also compiles the vendored commons module, so the
first build is slower than subsequent ones.

## License

Same as upstream, see `LICENSE`. The vendored `Simple-Commons/` keeps its own
GPL-3 license, see `Simple-Commons/LICENSE`.
