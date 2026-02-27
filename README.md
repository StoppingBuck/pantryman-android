# Pantryman (Android)

A cross-platform recipe and pantry manager built for neurodivergents and others who struggle with cooking. This is the Android frontend, calling [Janus Engine](https://github.com/StoppingBuck/janus-engine) via JNI through a Rust bridge.

***NOTE:** This project makes heavy use of AI in its development. See [AI.md](AI.md) for more information.*

---

## Vision

Pantryman was launched with four ambitions:

1. Made for neurodivergent people and others, who struggle in the kitchen to connect what they **have** (pantry) to what they **can do with it** (recipes).
2. Privacy by design (PbD) through allowing you to sync (or not) in any way you want.
3. Unified backend, freedom to frontend: Cram as much of the hard logic into a unified backend, and then have any number of frontends that can make use of it. I'm not an arbiter of fine UX. If you think my app is butt-ugly, you should be free to code a different frontend without having to fork the entire project. Having a strong backend (written in Rust, because of course it is) decoupled gracefully from the UI should make it easier to ensure that every platform has one (or more) app that looks just right for *it*. Alternative frontends are welcome for all platforms.
4. Maintain a knowledge base with information about ingredients, to engender familiarity with cooking as a (food) science and not just as an incomprehensible art form.

Pantryman is meant for people who relate to the quote "*I hate when I go to the kitchen looking for food, and all I find is ingredients.*" Its main purpose is to make it easy to keep an up-to-date overview of what you have in your pantry — and then use that information to show you what recipes you can make. Some people have the ability to look in the fridge and improvise — for everybody else, there is this app.

There's a gazillion cooking apps on the market already. Most of them attempt to tie you to an ecosystem or website of some kind — "create your CookWorld.com user to favorite your recipes, oops we leaked your personal info", etc. This app has nothing like that. No ecosystem, no website, no user creation, etc. Instead, it's BYOB — Bring Your Own Backend. Pantryman stores the ingredients, pantry and recipes as simple text files (YAML for ingredients and pantry, Markdown with YAML frontmatter for recipes). You can put the data directory containing these wherever you want — a local folder, a flash drive, your own self-hosted Nextcloud, a server, Dropbox or your cloud provider of choice... Pantryman doesn't care. It just needs to be able to read it. Your data stays yours by design.

---

## Features (v0.1.0)

- View and manage your pantry on the go
- Add new ingredients and update stock quantities
- Bidirectional sync with a cloud folder via Android's Storage Access Framework (SAF) — works with pCloud, Google Drive, Nextcloud, and any other SAF-compatible provider
- Automatic sync on app open (pull) and app close (push)
- Manual "Sync Now" button in settings

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
