# Contributing to Pantryman (Android)

## Setup

```bash
# Install Rust Android tooling
rustup target add aarch64-linux-android
cargo install cargo-ndk

# Clone engine as sibling
git clone https://github.com/StoppingBuck/janus-engine ../janus-engine

# Connect a physical Android device, then:
./dev.sh android
```

Emulation is not used — it's too slow for productive development. Use a physical device.

## Compile loop

The build has two stages:
1. `./dev.sh bridge` — compile the Rust JNI bridge (`librust_bridge.so`)
2. `./gradlew installDebug` — build and install the APK

`./dev.sh android` does both plus launch and log streaming.

## Architecture rules

- **JNI boundary is the only Kotlin↔Rust seam.** `CookbookEngine.kt` wraps all JNI calls — nothing else should call native methods directly.
- **Engine logic belongs in janus-engine.** The Rust bridge is glue, not logic.
- **SAF sync is lifecycle-bound.** `syncFromSAF` runs in `onResume`, `syncToSAF` runs in `onPause`. Both run on background threads.
- **Idiomatic Kotlin.** Use Jetpack libraries, coroutines-friendly patterns, and Android resource system.

## Sync behaviour

See [SYNC.md](SYNC.md) for a full description of the sync model, edge cases, and verification steps.

## Logs

```bash
adb logcat "*:W" "Pantryman:V" "RustBridge:V"
```

Or just use `./dev.sh android` which streams them automatically after launch.
