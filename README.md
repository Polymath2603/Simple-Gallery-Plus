# Gallery+ (Simple Gallery fork)

A fork of [SimpleMobileTools/Simple-Gallery](https://github.com/SimpleMobileTools/Simple-Gallery)
(Pro 6.28.1, base upstream commit `a3564013b`) with quality-of-life fixes, a
modernised icon set, and upgraded dependencies. No ads, no trackers — all media
stays on-device.

## What this fork adds / fixes vs upstream

### Folder management: edit button
The Excluded Folders and Included Folders screens (`ExcludedFoldersActivity` /
`IncludedFoldersActivity`) now have a per-element **Edit** action alongside the
existing Remove. Tap the 3-dot icon on any folder row → "Edit folder" opens a
dialog pre-filled with the full path. Change the path and press OK — the folder
is updated in the config (excluded or included list) and the list refreshes
immediately. Previously you had to remove and re-add a folder to change its path.

### Video playback: local-file ExoPlayer fix
`VideoPlayerActivity` and `VideoFragment` now build the ExoPlayer media source
from a local `File` URI via `DefaultDataSource` when the path is a plain
filesystem path (not a `content://` URI). This fixes video playback on
non-standard storage — OTG-mounted drives, custom `includedFolders` paths, or
any directory the Android content resolver cannot resolve. Previously these
scenarios produced a playback error or black screen.

`ViewPagerActivity` now resolves the play URI through `getFinalUriFromPath` for
non-content schemes, and passes the real path and MIME type correctly to the
video player intent instead of relying on the content resolver alone.

### Material Design icons (30+ vector drawables replaced)
All major toolbar and menu icons have been replaced with crisp Google Material
Icons vectors, matching the official Simple Gallery Pro look. Changed icons:

camera, delete, edit, share, sort, filter, copy, crop, check, undo, undo
outline, set-as, settings cog, star, star outline, info, hide, unhide, label,
lock, folder, folders, files, column count, drag handle, three dots,
orientation auto, play outline, pause outline, plus, pin, pin filled, place,
next outline, move, move to top, move to bottom, Google Play.

Also added: `ic_set_as_vector` (set-as menu icon, not present in upstream).

### Build and dependency upgrades

| Component      | Upstream        | Fork        |
|---|---|---|
| Kotlin         | 1.8.22          | **1.9.22**  |
| KSP            | 1.8.22-1.0.11   | **1.9.22-1.0.17** |
| Room           | 2.6.0-beta01    | **2.5.2** (stable) |
| AGP            | 7.4.0           | **8.6.0**   |
| compileSdk     | 34              | **35**      |
| Simple-Commons | SNAPSHOT (`9e60e24790`) | **5.34.26** (mavenLocal) |
| Gradle wrapper | older           | **8.7**     |

### Gradle and build config changes
- **`gradle.properties`**: JVM heap 2 GB, max metaspace 512 MB, parallel build
  off, daemon off — builds cleanly on constrained CI and low-RAM containers.
  (Upstream used 8 GB heap and kept parallel/daemon on.)
- **`settings.gradle.kts`**: added Aliyun public/jcenter mirrors and
  `mavenLocal()` as fallback repositories, because jcenter.bintray.com is gone
  from default Gradle resolution.
- **`gradle-wrapper.properties`**: updated to Gradle 8.7.
- **`build.gradle.kts`** (root): removed unused `id("com.android.application")`
  at root level (only the app module needs it).
- **`app/build.gradle.kts`**: `compileSdk 35`, `targetSdk 34` (was 34/33),
  debug signing uses `~/.android/debug.keystore` with apksigner v2+v3, removed
  the old `signingConfigs` block.
- **Room schema export** committed at `app/schemas/…/10.json` (used by
  `room.schemaLocation` in the build).

### Minor fixes upstream had not applied
- **`FilterThumbnailsManager`**: fixed the bitmap assignment line (`filterItem.bitmap =`).
- **`PicassoRoundedCornersTransformation`**: minor fix in the rounding calculation.
- **`SvgModule`**: SVG drawable loading fix for icons that need it.

### UI string additions
The fork adds strings for: Save / Save copy / Overwrite / Resize / Random /
Lock folder / Edit folder / Move to top / Move to bottom / Create shortcut /
Exclude / Unlock folder / Unpin folder / Change cover image / Select photo /
Use default / Editor / Basic Editor / Advanced Editor / Rotate / Flip
horizontally / Draw / Color picker / Crop / Save as / Set as wallpaper /
Image edited successfully / Video editing failed / many more.

These strings support the above features (edit folder, video playback, icon
labels) and are not present in upstream.

### Debug
- Debug launcher name is **Gallery+** (upstream uses `Gallery_debug`).

## Requirements

- Android 6.0 (API 23)+
- Android SDK 35 to build; JDK 17
- `com.simplemobiletools.commons:release:5.34.26` in `mavenLocal()`
  (build `../Simple-Commons` first, or publish the artifact)

## Build

```bash
./gradlew assembleFossDebug      # foss flavor → app/build/outputs/apk/foss/debug
./gradlew assembleRelease        # requires signing config
```

## License

Apache-2.0 (same as upstream), see `LICENSE`.
