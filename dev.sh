#!/bin/bash
set -e
GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JANUS_ENGINE_PATH="$(cd "$PROJECT_ROOT/../janus-engine" 2>/dev/null && pwd || echo "")"
RUST_BRIDGE_DIR="$PROJECT_ROOT/rust-bridge"
APP_ID="com.example.pantryman"

show_help() {
	echo "Usage: $0 <command>"
	echo "  android  - Build, install, run, and stream logs (requires device)"
	echo "  bridge   - Build only the Rust JNI bridge"
	echo "  check    - cargo check on the Rust bridge"
	echo "  help     - Show this help"
}

check_device() {
	if ! adb devices | grep -q "device$"; then
		echo -e "${RED}❌ No device connected. Plug in your Android device and try again.${NC}"
		exit 1
	fi
}

check_engine() {
	if [ -z "$JANUS_ENGINE_PATH" ]; then
		echo -e "${RED}❌ janus-engine not found at ../janus-engine${NC}"
		echo "   Clone it: git clone <url> $PROJECT_ROOT/../janus-engine"
		exit 1
	fi
}

case "${1:-help}" in
"android")
	check_device
	check_engine
	echo -e "${CYAN}🦀 Building Rust JNI bridge...${NC}"
	cd "$RUST_BRIDGE_DIR"
	cargo ndk -t arm64-v8a -o "$PROJECT_ROOT/app/src/main/jniLibs" build --release

	echo -e "${CYAN}🤖 Building and installing APK...${NC}"
	cd "$PROJECT_ROOT"
	./gradlew installDebug

	echo -e "${CYAN}🚀 Launching Pantryman...${NC}"
	adb shell am start -n "$APP_ID/.MainActivity"

	echo -e "${GREEN}✅ App launched. Streaming logs (Ctrl+C to stop)...${NC}"
	adb logcat -c
	adb logcat \
		"MainActivity:D" \
		"JanusEngine:D" \
		"PantrymanRust:D" \
		"ShoppingModeActivity:D" \
		"PantrySwipeCallback:D" \
		"*:S"
	;;
"bridge")
	check_engine
	echo -e "${CYAN}🦀 Building Rust JNI bridge...${NC}"
	cd "$RUST_BRIDGE_DIR"
	cargo ndk -t arm64-v8a -o "$PROJECT_ROOT/app/src/main/jniLibs" build --release
	echo -e "${GREEN}✅ Bridge build complete${NC}"
	;;
"check")
	check_engine
	echo -e "${CYAN}🔍 Running cargo check on Rust bridge...${NC}"
	cd "$RUST_BRIDGE_DIR"
	cargo check
	echo -e "${GREEN}✅ Check complete${NC}"
	;;
"help" | "--help" | "-h") show_help ;;
*)
	echo -e "${RED}❌ Unknown command: $1${NC}"
	show_help
	exit 1
	;;
esac
