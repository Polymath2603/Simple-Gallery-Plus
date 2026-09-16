# Gallery+ (Simple Gallery fork)

A fork of [SimpleMobileTools/Simple-Gallery](https://github.com/SimpleMobileTools/Simple-Gallery)
(Pro 6.28.1) with quality-of-life fixes, a modernised icon set, and upgraded
dependencies. No ads, no trackers — all media stays on-device.

## What this fork adds / fixes vs upstream

### UI improvements
- **Material Design icons** — 30+ vector drawables replaced (camera, delete, edit,
  share, sort, filter, lock, folders, star, etc.) with crisp Material icons from
  Google, matching the official Simple Gallery Pro look.
- **Editable folder lists** — the Excluded Folders and Included Folders screens now
  have a per-element Edit action (pencil icon + 3-dot menu), letting you change a
  folder path without removing and re-adding it.

### Playback fixes
- **ExoPlayer local-file fallback** — `VideoPlayerActivity` and `VideoFragment` now
  build the media source from a local `File` URI via `DefaultDataSource` instead of
  relying solely on content-URI resolution; fixes playback on paths the OS cannot
  resolve (e.g. OTG-mounted directories, custom storage).
- **ViewPager video launch** — `ViewPagerActivity` now resolves the play URI through
  `getFinalUriFromPath` for non-content schemes, and passes the real path + MIME type
  correctly to the player intent.

### Build / dependency upgrades
| Component | Upstream | Fork |
|---|---|---|
| Kotlin | 1.8.22 | **1.9.22** |
| KSP | 1.8.22-1.0.11 | **1.9.22-1.0.17** |
| Room | 2.6.0-beta01 | **2.5.2** (stable) |
| AGP | 7.4.0 | **8.6.0** |
| compileSdk | 34 | **35** |
| Simple-Commons | SNAPSHOT (`9e60e24790`) | **5.34.26** (mavenLocal) |
| Gradle wrapper | older | **8.7** |

- **Gradle memory tuning** — `Xmx2048m`, `MaxMetaspaceSize=512m`, parallel off,
  daemon off (fits constrained CI and low-RAM containers).
- **Maven mirrors** — Aliyun public/jcenter mirrors + `mavenLocal()` in
  `settings.gradle.kts`; jcenter/bintray gone from default resolution.
- **Room schema export** committed at `app/schemas/…/10.json`.

### Debug
- Debug launcher name is **Gallery+** (upstream uses `Gallery_debug`).

## Requirements

- Android 6.0 (API 23)+
- Android SDK 35 to build; JDK 17
- `com.simplemobiletools.commons:release:5.34.26` in `mavenLocal()`

## Build

```bash
# publish Simple-Commons to mavenLocal first (see ../Simple-Commons)
./gradlew assembleFossDebug      # foss flavor → app/build/outputs/apk/foss/debug
./gradlew assembleRelease        # requires signing config
```

## License

Apache-2.0 (same as upstream), see `LICENSE`.
