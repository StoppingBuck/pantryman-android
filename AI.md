# AI Development Notes

This codebase is developed with significant AI assistance (Claude Code).

## How AI is used

- Kotlin UI implementation and Jetpack library wiring
- JNI bridge boilerplate
- SAF sync logic design and implementation
- Bug diagnosis from logcat output
- Documentation drafting

## What AI does well here

- SAF/DocumentFile API boilerplate
- Android lifecycle integration (onResume/onPause)
- Diagnosing crashes from stack traces
- Generating JNI signatures

## What AI doesn't replace

- Device-specific testing (emulation is not used)
- UX decisions about sync conflict resolution
- Knowledge of specific cloud provider quirks

## Trust boundary

All AI-generated code is compiled and tested on a physical device before committing.
Sync logic in particular was verified end-to-end (Android ↔ desktop ↔ pCloud).

## Notes for AI assistants working on this repo

- Physical device only — do not add emulator support
- SAF operations must run on background threads, never the main thread
- `syncInProgress` flag prevents concurrent sync runs — always check it
- Engine reinitialization is required after `syncFromSAF` because DataManager loads on construction
- Rust bridge is at `rust-bridge/` and is a separate crate (excluded from workspace)
- Build order: Rust bridge first (`cargo ndk`), then Gradle
