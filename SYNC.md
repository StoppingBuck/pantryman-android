# Pantryman SAF Sync

Pantryman syncs its data (ingredients + pantry) with a cloud-backed folder via Android's
Storage Access Framework (SAF). This allows the Android app and the GTK desktop app to stay
in sync through a shared cloud folder (e.g. pCloud via the Autosync app).

## Architecture: Mirror Approach

```
SAF folder (pCloud/cloud)         Local app storage (internal)
  pantry.yaml          ←→          filesDir/cookbook_data/pantry.yaml
  ingredients/                     filesDir/cookbook_data/ingredients/
    potato.yaml        ←→            potato.yaml
    tomato.yaml        ←→            tomato.yaml
```

- **Sync FROM SAF → local** on `onResume` (SAF is authoritative on open)
- **Sync TO local → SAF** on `onPause` (local is authoritative on close)

The Rust engine (CookbookEngine JNI) operates on local storage only and is reinitialized
after every sync-in, because DataManager loads data at creation time.

Synced: `ingredients/*.yaml`, `pantry.yaml`
Not synced: recipes (Android-only for now)

## Setup

1. Open the app → tap the ⋮ menu → **Settings**
2. Tap **Choose Sync Folder…** and pick your pCloud (or other cloud) folder
3. The URI is saved with a persistable permission — you only need to do this once

## Manual sync

Settings → **Sync Now** — runs a full bidirectional sync immediately.

## Verification checklist

1. Build and install: `./dev.sh android`
2. First run: Settings → Choose Sync Folder → pick a pCloud folder → confirm URI shown in dialog
3. Sync out: Add an ingredient to pantry → press Home → check pCloud folder contains updated `pantry.yaml`
4. Sync in: Modify `pantry.yaml` in the cloud folder via desktop → open app → verify Android shows updated data
5. Deletion sync: Delete an ingredient YAML from cloud folder → open app → verify ingredient is gone on Android
6. Manual sync: Settings → Sync Now → verify sync runs and UI updates
7. No sync folder: Fresh install with no sync folder set → app works normally with local data

## Known risks / gotchas

- **`onPause` background thread**: Android may kill background threads quickly after `onPause`.
  Works for fast SAF providers; if data loss on close is observed, migrate to `WorkManager`.
- **`"wt"` output mode**: Not all SAF providers support write-truncate mode. If sync-to fails
  silently, try `"w"` instead of `"wt"` in `contentResolver.openOutputStream(uri, "wt")`.
- **SAF filename quirks**: Some providers rename files created via `createFile()`. Verify that
  `pantry.yaml` appears with the correct name in pCloud after the first sync-out.
- **Double sync on picker return**: `onResume` fires after the folder picker closes, causing a
  second sync on top of the picker callback's sync. Harmless but redundant.

## Desktop sync (future)

GTK desktop sync is a separate task. The plan is for GTK to read/write directly to the
cloud-synced folder path (since pCloud has a real filesystem path on Linux), while Android
uses the mirror approach above.
