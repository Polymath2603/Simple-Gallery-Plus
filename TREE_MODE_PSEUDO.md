# Gallery+ — Folder-in-Folder (Tree Mode) Pseudo

## Current architecture (for context)

```
MainActivity.getDirectories()
  → directoryDB.getAll()         // flat list of ALL folders with media, ever scanned
  → filter hidden/excluded
  → gotDirectories() → setupAdapter()

setupAdapter()
  → distinctBy path
  → sort
  → getDirsToShow()             // if groupDirectSubfolders: shows only top-level
  → DirectoryAdapter(dirsToShow)

User taps folder:
  if subfoldersCount == 1 || !groupDirectSubfolders:
    → itemClicked(path) → Intent(MediaActivity, path)
  else:
    → mCurrentPathPrefix = path  // drills down in the same grid

MediaActivity.getMedia(path)
  → GetMediaAsynctask(path)     // gets files at exact path only
  → displays flat media grid
```

Key: `directoryDB` has a flat `Directory` per folder that contains media.
No nesting info — just `path`, `mediaCnt`, `subfoldersCount`, `types`.

---

## New 3rd mode: Tree (folder-in-folder)

### Icon
Two overlapping folders (e.g. `ic_tree_vector` or `library_material_symbols` style).

### What it shows on main screen

Instead of a flat folder list, show a **hierarchical tree**.
Each visible node = a folder that either has media OR has descendants with media.

#### Algorithm: buildTree(rootDirs, showHidden)

```
INPUTS:
  rootDirs   = config.includedFolders          // the "roots" (user-chosen top-level)
  showHidden = config.shouldShowHidden

STEP 1 — Discover all folder paths with media (from directoryDB):
  allDirs = directoryDB.getAll()               // flat list of Directory objects
  allPaths = allDirs.map { it.path }

STEP 2 — For each root, walk its subtree and find "meaningful" folders:
  for each root in rootDirs:
      subtree = walkSubtree(root, allPaths, showHidden)
      result.addAll(subtree)

walkSubtree(folderPath, allPaths, showHidden):
  // Does this folder directly contain media?
  hasMediaHere = allPaths.contains(folderPath)

  // Recurse into immediate subdirectories
  children = allPaths.filter { it.getParentPath() == folderPath }
                      .filter { showHidden || !it.isHidden() }

  childResults = []
  for each child in children:
      childResults.addAll(walkSubtree(child, allPaths, showHidden))

  // DECISION: should this folder appear in the tree?

  CASE 1: hasMediaHere == true AND childResults is empty:
      → leaf node with media → ADD to tree

  CASE 2: hasMediaHere == true AND childResults is not empty:
      → has media AND has meaningful subfolders
      → ADD self to tree, with children underneath
      (user sees this folder AND can drill into subfolders)

  CASE 3: hasMediaHere == false AND childResults is empty:
      → empty folder → SKIP entirely

  CASE 4: hasMediaHere == false AND childResults is not empty:
      → no media here but descendants do
      → SKIP this folder, promote its children up one level
      (avoids showing "empty corridor" folders)

  // Edge case: deeply nested chain a/b/c where only c has media
  // → a: case 4, skip → b: case 4, skip → c: case 1, show
  // Result: c appears at root level (promoted past empty intermediates)
```

#### Example

```
Storage:
  DCIM/
    Camera/          → 50 photos
    .hidden/         → 10 photos    (hidden)
  Pictures/
    Screenshots/     → 30 photos
    Wallpapers/      → 0 media, but has subfolder Art/
      Art/           → 5 wallpapers
  Music/             → 0 media, no subfolders with media
  Documents/
    notes.txt        → not media
    Projects/
      app/           → 2 screenshots
      website/       → 0 media

Included folders: [DCIM, Pictures, Documents]
Show hidden: OFF

RESULTING TREE:
  DCIM/
    Camera/              50 photos
  Pictures/
    Screenshots/         30 photos
    Art/                 5 wallpapers     ← promoted past Wallpapers (case 4)
  Documents/
    Projects/
      app/               2 screenshots   ← promoted past empty intermediates

  Music → SKIPPED (not an included root, no media anywhere)
  Documents/notes.txt   → SKIPPED (not a folder)
  Documents/Projects/website → SKIPPED (case 3: no media, no children)
```

### Display format

Each tree node shows:
- Folder name (indented by depth)
- Media count badge
- Thumbnail preview (first media item in that folder)
- Subfolder count (if any)

Tapping a node:
- If it has media → opens MediaActivity for that path (with the new feature below)
- If it only has subfolders → expands/collapses inline (accordion style)

---

## New feature: MediaActivity shows subfolders too

Currently MediaActivity shows ONLY media files at the exact path.
The tree mode needs it to also show **subfolders as clickable items at the top**.

### Algorithm: enhance MediaActivity

```
On open(path):
  1. Load media files at path (existing behavior — unchanged)
  2. NEW: Scan for immediate subfolders that have media:
       subfolders = allDirs.filter { it.path.getParentPath() == path }
                            .filter { showHidden || !it.isHidden() }
       sort subfolders by name
  3. Display:
       ┌─────────────────────────┐
       │  SubfolderA    (12)     │  ← subfolder row, tappable, opens that subfolder
       │  SubfolderB    (5)      │
       ├─────────────────────────┤
       │  photo1.jpg             │  ← media items (existing grid/list)
       │  video2.mp4             │
       │  ...                    │
       └─────────────────────────┘

Back button:
  - If path == root of an included folder → finish() (normal back)
  - If path is nested → go up to parent folder
    (path = path.getParentPath())
    reload MediaActivity with parent path
```

### Back navigation for tree

```
User path stack: [DCIM, Projects, app]

Back from app/ → Projects/  (reload with parent path)
Back from Projects/ → Documents/  (reload with parent path)
Back from Documents/ → finish()  (root of included folder)
```

### How the two features connect

```
MainActivity (tree view)
  │
  ├── tap "Camera/"          → MediaActivity(path="DCIM/Camera/")
  │                              shows: (no subfolders) + 50 photos
  │
  ├── tap "Art/"             → MediaActivity(path="Pictures/Wallpapers/Art/")
  │                              shows: (no subfolders) + 5 wallpapers
  │
  └── tap "Projects/"        → MediaActivity(path="Documents/Projects/")
                                 shows: [app/ (2)] + (no direct media)
                                 tap "app/" → MediaActivity(path="Documents/Projects/app/")
                                              shows: (no subfolders) + 2 screenshots
                                 back → Documents/Projects/
```
