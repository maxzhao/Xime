---
title: ARM64 本地发布构建与 APK 校验规格
created: 2026-09-19
updated: 2026-09-19
type: source
doc_role: spec
authority: draft
status: experimental
taskadmin_tag: master
taskadmin_id: 3
sources:
  - git:19d41aaa96fabd9002d7ccbacaa583d328604588
  - git:8dd2b3a1078b81d4781a0e6d769b10c286336522
  - justfile
  - app/build.gradle.kts
derived_to: []
confidence: high
---

> **TLDR**: `just build-release-arm64` SHALL build the `arm64-v8a` release split with explicit local-install debug signing, locate the versioned APK, verify its signature with the latest Android build-tools `apksigner`, and print its path and SHA-256 digest.

## Purpose

Define the engineering behavior contract introduced for a locally installable ARM64 release build. This is a build/release contract, not an end-user input feature.

## Scope

### In Scope

- Gradle property `xime.abi` and ABI filtering/splits.
- Gradle property `xime.localInstall` and its signing boundary.
- `just build-release-arm64` environment resolution, assembly and artifact discovery.
- Signature verification and artifact digest reporting.

### Out Of Scope

- Production signing credentials and release publication.
- Google Play/App Bundle workflows and CI release policy.
- Runtime behavior of packaged native libraries.
- Independent ZIP-content inspection of the APK; the current recipe relies on Gradle's ABI filtering and split output.
- Manifest/package/version reporting beyond the version encoded in the output filename.

## Actors And Triggers

- Actor: a developer or Agent running `just build-release-arm64` at the repository root.
- Tool actors: Just, Gradle, Java, the Android SDK/build-tools and `sha256sum`.

## Requirements

### Requirement: Gradle Accepts One Valid `xime.abi`

The Android build SHALL accept optional Gradle property `xime.abi`. If present, it SHALL be one of `armeabi-v7a`, `arm64-v8a`, `x86` or `x86_64`; otherwise configuration SHALL fail with an explicit unsupported-value error. If absent, the build SHALL retain all four supported ABIs.

#### Scenario: Valid ARM64 property

- GIVEN `-Pxime.abi=arm64-v8a`
- WHEN Gradle configures `:app`
- THEN NDK filters and ABI splits SHALL include only `arm64-v8a`

#### Scenario: Property is absent

- GIVEN no `xime.abi` property
- WHEN Gradle configures `:app`
- THEN NDK filters and ABI splits SHALL include all four supported ABIs

#### Scenario: Property is invalid

- GIVEN `xime.abi` is not in the supported set
- WHEN Gradle configuration runs
- THEN configuration SHALL fail and identify the unsupported value and expected set

### Requirement: Split Outputs Have Deterministic Names

The application build SHALL name each APK `Xime-<versionName>-<abi>.apk`, using `universal` when the output has no ABI filter. ABI splits SHALL be enabled and a universal APK SHALL also be generated from the currently selected ABI set.

#### Scenario: ARM64 release split is built

- GIVEN `versionName=2.8.0` and `xime.abi=arm64-v8a`
- WHEN the release variant is packaged
- THEN the ARM64 split filename SHALL be `Xime-2.8.0-arm64-v8a.apk`

### Requirement: Local-Install Signing Is Explicit

Gradle property `xime.localInstall=true` SHALL force the release build to use Android's debug signing configuration so the APK can be installed for local testing. Without that property, release signing SHALL use `app/keystore.properties` only when the file exists; otherwise the release variant SHALL not silently substitute local-install signing.

#### Scenario: Local ARM64 recipe builds

- GIVEN the recipe passes `-Pxime.localInstall=true`
- WHEN Gradle configures the release build
- THEN it SHALL sign that local artifact with the debug signing configuration

#### Scenario: Ordinary release build uses configured release identity

- GIVEN `xime.localInstall` is absent/false and `app/keystore.properties` exists
- WHEN release signing is configured
- THEN Gradle SHALL use the release signing configuration loaded from that local file

#### Scenario: Release properties are absent

- GIVEN `xime.localInstall` is absent/false and no local release properties exist
- WHEN release assembly requires a signed artifact
- THEN the build SHALL not claim that local-install debug signing was selected

### Requirement: Signing Secrets Stay Outside Shared Specifications

Release signing values SHALL remain local inputs from `app/keystore.properties`, including either an existing keystore path or a Base64-provided keystore materialization path. Shared specifications, logs and task artifacts SHALL not record credential values.

#### Scenario: Base64 keystore input is configured locally

- GIVEN local signing properties provide `keyBase64`
- WHEN Gradle configures release signing
- THEN it MAY decode the keystore under the app build directory without exposing the encoded value in shared artifacts

### Requirement: Recipe Resolves Java And Android SDK Prerequisites

The recipe SHALL derive `JAVA_HOME` from the active `java` executable only when `JAVA_HOME` is unset. It SHALL resolve the Android SDK from `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then `$HOME/Android/Sdk`. If the resolved SDK directory does not exist, it SHALL fail with guidance to set a valid SDK environment variable.

#### Scenario: SDK environment is unset but default exists

- GIVEN neither SDK environment variable is set and `$HOME/Android/Sdk` exists
- WHEN the recipe starts
- THEN it SHALL use that directory for both `ANDROID_HOME` and `ANDROID_SDK_ROOT`

#### Scenario: SDK cannot be resolved

- GIVEN the resolved SDK path is not a directory
- WHEN the recipe starts
- THEN it SHALL exit before Gradle assembly with an explicit SDK-not-found message

### Requirement: Recipe Removes Only The Prior Matching ARM64 Artifact

Before assembly, the recipe SHALL remove stale `app/build/outputs/apk/release/Xime-*-arm64-v8a.apk` files. It SHALL not require a full Gradle clean; Gradle remains responsible for incremental build correctness.

#### Scenario: A prior ARM64 output exists

- GIVEN the release output directory contains an older matching ARM64 APK
- WHEN the recipe starts
- THEN it SHALL remove that matching file so later discovery cannot select stale output

### Requirement: Recipe Builds The ARM64 Local-Install Release

The recipe SHALL invoke `./gradlew :app:assembleRelease -Pxime.abi=arm64-v8a -Pxime.localInstall=true --console=plain`. Any non-zero Gradle result SHALL fail the recipe.

#### Scenario: Gradle assembly succeeds

- GIVEN Java, SDK and project dependencies are available
- WHEN the recipe invokes Gradle
- THEN it SHALL build the release variant constrained to ARM64 with explicit local-install signing

### Requirement: Artifact Discovery Requires The Versioned ARM64 Split

After assembly, the recipe SHALL find the first regular file matching `app/build/outputs/apk/release/Xime-*-arm64-v8a.apk` at that directory level. If no such file exists, the recipe SHALL fail and SHALL NOT validate a universal or different-ABI artifact instead.

#### Scenario: Expected ARM64 split exists

- GIVEN Gradle produced the versioned ARM64 split
- WHEN artifact discovery runs
- THEN that file SHALL become the selected installable APK

#### Scenario: Only another output exists

- GIVEN only a universal or non-ARM64 APK exists
- WHEN artifact discovery runs
- THEN the recipe SHALL fail with `arm64-v8a release APK was not produced`

### Requirement: Latest Installed `apksigner` Verifies The Artifact

The recipe SHALL find the highest versioned directory under `<sdk>/build-tools`, require an executable `apksigner` there, and run `apksigner verify --verbose` against the selected APK. Missing or failed signature verification SHALL fail the recipe; there is no alternate verifier in the current contract.

#### Scenario: Signed APK is valid

- GIVEN the latest installed build-tools contains executable `apksigner`
- WHEN verification runs against the local-install APK
- THEN verification SHALL succeed before the recipe reports the artifact

#### Scenario: `apksigner` is missing

- GIVEN no executable exists at the resolved latest build-tools path
- WHEN validation runs
- THEN the recipe SHALL fail with an unavailable-`apksigner` message

### Requirement: Successful Recipe Reports Path And Digest

The recipe SHALL report success only after assembly, exact artifact discovery and signature verification pass. It SHALL print `Installable APK: <path>` followed by the artifact's SHA-256 digest and path.

#### Scenario: Every stage passes

- GIVEN the ARM64 APK is produced and signature verification succeeds
- WHEN the recipe completes
- THEN its exit status SHALL be zero and its final output SHALL identify the APK and SHA-256 digest

## Edge Cases And Failure Behavior

- Missing Java, Android SDK, build-tools, `apksigner`, expected output or a valid signature SHALL produce non-zero completion through explicit checks or shell/Gradle failure.
- The recipe deletes only the prior matching ARM64 split; it does not promise a full clean build.
- The output filename does not independently prove ZIP ABI contents; the current contract relies on `xime.abi`, NDK filters and ABI split configuration.
- Production signing credentials are optional local inputs and SHALL never be persisted by this spec.

## Data / Entity Constraints

- Supported `xime.abi` values: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- ARM64 recipe properties: `-Pxime.abi=arm64-v8a` and `-Pxime.localInstall=true`.
- Output naming: `Xime-<versionName>-<abi>.apk`.
- Output discovery root: `app/build/outputs/apk/release`.
- Current application ID and version remain owned by `app/build.gradle.kts`; the recipe does not print them from the manifest.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Gradle ABI selection/output | covered | developer/build Agent | build configuration/artifacts | covered | Gradle, NDK/CMake | prior ARM64 split removed | source + successful recipe |
| Signing selection | covered | developer/build Agent | local signing choice | covered | debug config or local release properties | configuration-derived | source + `apksigner` |
| Recipe environment/discovery | covered | developer/build Agent | environment exports/artifact read | covered | Java, Android SDK | matching prior artifact removed | successful recipe |
| Signature/digest reporting | covered | developer/build Agent | read-only APK validation | covered | latest build-tools, `sha256sum` | one exact artifact | successful recipe |

## Coverage Gaps

- The recipe does not inspect APK ZIP entries to independently assert that only ARM64 native libraries are present.
- CI, official production signing and release publication are intentionally unspecified.
- Reproducible byte-for-byte APK output is not claimed.

## Acceptance Criteria

- Invalid `xime.abi` values fail Gradle configuration.
- `just build-release-arm64` selects explicit ARM64/local-install properties and cannot succeed without the expected versioned ARM64 split and valid APK signature.
- A successful recipe reports the exact artifact path and SHA-256 digest.
- No production signing credential is required or exposed by this specification.

## Validation Plan

- Run `just build-release-arm64` from the repository root and require exit code 0, successful `apksigner` output and a final `Installable APK` path/digest.
- Run `just --summary` and confirm `build-release-arm64` remains discoverable.
- Run `./gradlew :app:assembleDebug` without `xime.abi` to exercise the default multi-ABI configuration.
- Optionally run Gradle with an invalid `-Pxime.abi` and require configuration failure.

## Assumptions And Open Questions

- Assumption: `xime.localInstall=true` is exclusively a local testing mechanism and its debug signature is not a production release identity.
- Open question: ZIP-level ABI-content validation may be added later; it is not current behavior and is therefore not claimed.

## Source Trace

- `justfile`: `default` and `build-release-arm64`, Java/SDK resolution, stale-artifact removal, exact Gradle properties, artifact discovery, `apksigner` and SHA-256 output.
- `app/build.gradle.kts`: supported ABI set, `xime.abi`, `xime.localInstall`, NDK/split configuration, signing selection and versioned output filenames.
- Commit evidence: `19d41aaa`, `8dd2b3a1`.
- Validation evidence: `just build-release-arm64` passed on 2026-09-19 and produced a verified versioned ARM64 APK; artifact hash is intentionally not persisted here.
- TaskAdmin task: `master/3`.
