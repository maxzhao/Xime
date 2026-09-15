# List available project commands.
default:
    @just --list

# Build the arm64-v8a release APK and sign it with a persistent local test key.
build-release-arm64-test-signed:
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

    rm -f app/build/outputs/apk/release/Xime-*-arm64-v8a-test-signed.apk \
        app/build/outputs/apk/release/Xime-*-arm64-v8a-test-signed.apk.idsig

    ./gradlew :app:assembleRelease -Pandroid.injected.build.abi=arm64-v8a --console=plain

    unsigned_apk="$(find app/build/intermediates/apk/release -maxdepth 1 -type f \
        -name 'Xime-*-arm64-v8a.apk' -print -quit)"
    [[ -f "$unsigned_apk" ]] || {
        echo "arm64-v8a release APK was not produced: $unsigned_apk" >&2
        exit 1
    }
    signed_apk="app/build/outputs/apk/release/$(basename "${unsigned_apk%.apk}")-test-signed.apk"

    build_tools_version="$(find "$android_sdk/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V | tail -n 1)"
    [[ -n "$build_tools_version" ]] || {
        echo "No Android SDK build-tools installation found." >&2
        exit 1
    }
    apksigner="$android_sdk/build-tools/$build_tools_version/apksigner"
    zipalign="$android_sdk/build-tools/$build_tools_version/zipalign"
    [[ -x "$apksigner" && -x "$zipalign" ]] || {
        echo "apksigner or zipalign is unavailable in build-tools $build_tools_version." >&2
        exit 1
    }

    test_keystore="${XIME_TEST_KEYSTORE:-$HOME/.android/xime-test.keystore}"
    if [[ ! -f "$test_keystore" ]]; then
        mkdir -p "$(dirname "$test_keystore")"
        "$JAVA_HOME/bin/keytool" -genkeypair \
            -keystore "$test_keystore" \
            -storepass android \
            -keypass android \
            -alias xime-test \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -dname 'CN=Xime Test, OU=Test, O=Xime, C=CN' \
            >/dev/null
        echo "Created local test key: $test_keystore"
    fi

    temp_dir="$(mktemp -d)"
    trap 'rm -rf "$temp_dir"' EXIT
    "$zipalign" -f -p 4 "$unsigned_apk" "$temp_dir/aligned.apk"
    "$apksigner" sign \
        --ks "$test_keystore" \
        --ks-key-alias xime-test \
        --ks-pass pass:android \
        --key-pass pass:android \
        --out "$signed_apk" \
        "$temp_dir/aligned.apk"
    "$apksigner" verify --verbose --print-certs "$signed_apk"

    echo "APK: $signed_apk"
    sha256sum "$signed_apk"
