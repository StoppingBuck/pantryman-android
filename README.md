# Pantryman (Android)

An Android app for managing your pantry and recipes. Calls [Janus Engine](https://github.com/StoppingBuck/janus-engine) via JNI through a Rust bridge.

## Features

- Browse and manage ingredients and pantry stock
- Cloud sync via any SAF-compatible provider (pCloud, Google Drive, etc.)
- Bidirectional sync: SAF folder ↔ local app storage
- Syncs on open and close, plus a manual "Sync Now" button

For a full description of the sync architecture, see [SYNC.md](SYNC.md).

## Requirements

- Android Studio or the Android SDK command-line tools
- Rust with `cargo-ndk` and the `aarch64-linux-android` target
- A physical Android device (emulation is not supported for development)

Install Rust Android tooling:
```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk
```

## Getting started

```bash
# Clone janus-engine as a sibling directory first
git clone https://github.com/StoppingBuck/janus-engine ../janus-engine

# Build bridge + install + launch
./dev.sh android
```

## Commands

```bash
./dev.sh android   # Build Rust bridge, install APK, launch, stream logs
./dev.sh bridge    # Build only the Rust JNI bridge
./dev.sh check     # cargo check on the Rust bridge
```

## Architecture

```
rust-bridge/
  src/lib.rs     — JNI bindings (CookbookEngine exposed as Java class)
  Cargo.toml     — cdylib crate, depends on janus-engine

app/
  src/main/java/com/example/pantryman/
    MainActivity.kt     — main activity: UI, SAF sync lifecycle
    CookbookEngine.kt   — Kotlin wrapper for JNI calls
    IngredientsAdapter.kt
    PantryAdapter.kt
```

The Rust bridge compiles to `librust_bridge.so` and is loaded by the Kotlin layer via `System.loadLibrary("rust_bridge")`. The engine operates on files in the app's internal storage (`filesDir/cookbook_data/`). The SAF sync layer copies data between that directory and a user-chosen cloud folder.

## License

MIT
