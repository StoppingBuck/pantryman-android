# Changelog

All notable changes to Pantryman (Android) will be documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

## [0.1.0] — 2026-02-27

Initial release. Extracted from the Pantryman monorepo.

### Added

- Android app (Kotlin + Jetpack) backed by Janus Engine via JNI
- Browse and manage ingredients and pantry stock
- Cloud sync via SAF (Storage Access Framework): any SAF DocumentProvider (pCloud, Google Drive, etc.)
- Bidirectional sync with mirror semantics including deletion propagation
- Sync on `onResume` (SAF → local, cloud is authoritative on open)
- Sync on `onPause` (local → SAF, device is authoritative on close)
- Manual "Sync Now" button in settings
- Persistable URI permissions so sync survives reboots
- Race condition guard (`syncInProgress` flag) preventing concurrent sync runs

### Fixed

- Android 15 edge-to-edge status bar bleed (fitsSystemWindows + values-v35 opt-out)
