# List available project commands.
default:
    @just --list

# Build an installable arm64-v8a release APK with the standard local Android debug key.
build-release-arm64:
    #!/usr/bin/env bash
    set -euo pipefail

    if [[ -z "${JAVA_HOME:-}" ]]; then
        java_bin="$(readlink -f "$(command -v java)")"
        export JAVA_HOME="${java_bin%/bin/java}"
    fi

    android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
    [[ -d "$android_sdk" ]] || {
        echo "Android SDK not found: $android_sdk" >&2
        echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to a valid SDK directory." >&2
        exit 1
    }
    export ANDROID_HOME="$android_sdk"
    export ANDROID_SDK_ROOT="$android_sdk"

    rm -f app/build/outputs/apk/release/Xime-*-arm64-v8a.apk

    ./gradlew :app:assembleRelease \
        -Pxime.abi=arm64-v8a \
        -Pxime.localInstall=true \
        --console=plain

    apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f \
        -name 'Xime-*-arm64-v8a.apk' -print -quit)"
    [[ -f "$apk" ]] || {
        echo "arm64-v8a release APK was not produced: $apk" >&2
        exit 1
    }

    build_tools_version="$(find "$android_sdk/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V | tail -n 1)"
    apksigner="$android_sdk/build-tools/$build_tools_version/apksigner"
    [[ -x "$apksigner" ]] || {
        echo "apksigner is unavailable in build-tools $build_tools_version." >&2
        exit 1
    }
    "$apksigner" verify --verbose "$apk"

    echo "Installable APK: $apk"
    sha256sum "$apk"
