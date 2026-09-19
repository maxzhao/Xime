---
title: Xime 手动 GitHub Release 发布规范
created: 2026-09-19
updated: 2026-09-19
type: pattern
tags: [release, android, github, signing]
sources:
  - "app/build.gradle.kts"
  - "justfile"
  - ".github/workflows/release.yml"
confidence: high
aliases: [Xime Release, APK 发布流程, 手动发布]
---

> **TLDR**: Xime 的手动 Release 必须禁用自动发布路径，使用测试签名一次构建并验证四种 ABI 与 universal 共 5 个 APK；先创建 draft、完整上传并核对资产集合，再公开发布，禁止只发布单一 ABI。

## 目的与适用范围

- 适用于 `maxzhao/Xime` 的手动 GitHub Release。
- 所有路径相对 Git 顶层目录；构建产物位于 `app/build/outputs/apk/release/`。
- 构建配置归 `app/build.gradle.kts` 所有；命令发现归 `justfile` 所有；远端 Release 归 `maxzhao/Xime` 所有。
- 本规范来自 2026-09-19 用户明确要求：禁止自动发布 Action，手动发布，使用与 `justfile` 相同的 Android 测试签名，并包含全部 ABI 与 universal APK。

## 不可违反的发布契约

1. 不使用 `.github/workflows/release.yml` 或其他 GitHub Actions 自动发布。
2. 发布签名使用 `-Pxime.localInstall=true` 选择的 Android debug/test key；Release 正文必须明确警告：该 APK 不能覆盖安装由其他密钥签名的版本。
3. 每个 Release 必须同时包含以下 5 个资产，版本号取当前 `app/build.gradle.kts` 的 `versionName`：
   - `Xime-<version>-arm64-v8a.apk`
   - `Xime-<version>-armeabi-v7a.apk`
   - `Xime-<version>-universal.apk`
   - `Xime-<version>-x86.apk`
   - `Xime-<version>-x86_64.apk`
4. 任一资产缺失、签名验证失败或上传失败时，不得公开部分 Release。
5. 上游基准 tag（例如 `v2.8.0`）不得移动或覆盖。fork 的发布 tag 必须使用独立名称；先检查远端 tags/Releases，并让用户确认最终 tag，例如 `v2.8.0-maxzhao.1`。
6. 删除 GitHub Release 不会自动删除 Git tag。只在用户明确要求时删除 tag。

## 源码事实

- `app/build.gradle.kts` 的 `supportedAbis` 包含 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。
- 未传 `-Pxime.abi=...` 时，`splits.abi` 会为四种 ABI 构建独立 APK，并因 `isUniversalApk = true` 同时构建 universal APK。
- 传入 `-Pxime.abi=arm64-v8a` 的现有 `just build-release-arm64` 只适合单架构本地安装验证，不能满足完整 Release 资产契约。
- `-Pxime.localInstall=true` 会为 release build 选择 debug/test signing config。虽然源码注释说明它通常不用于正式发布，但本 fork 的当前手动发布策略由用户明确要求使用测试签名；策略变更必须重新确认并更新本规范。

## 发布前检查

1. 运行 `just --summary`；若仍无全架构 Release recipe，使用下述 Gradle 命令，不要误用 `just build-release-arm64` 作为最终发布构建。
2. 检查 `git status --short --branch`，确认目标提交已推送且工作区没有非预期改动。
3. 从 `app/build.gradle.kts` 核实 `versionName`、`versionCode`、ABI splits 和输出命名；不得根据旧 Release 猜版本。
4. 检查目标 tag 是否已存在以及指向哪个提交。新 tag 必须指向待发布提交；不得移动上游基准 tag。
5. 检查 `.github/workflows/release.yml` 是否处于可触发状态。若推送 `v*.*.*` tag 会启动 Action，停止并让用户决定如何禁用该自动路径；禁止一边触发 Action 一边手动发布。

## 一次构建全部 APK

使用 Bash；不要在 zsh 中把变量命名为 `path`，因为它会覆盖特殊数组并破坏 `PATH`。

````bash
set -euo pipefail

if [[ -z "${JAVA_HOME:-}" ]]; then
  java_bin="$(readlink -f "$(command -v java)")"
  export JAVA_HOME="${java_bin%/bin/java}"
fi

android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[[ -d "$android_sdk" ]]
export ANDROID_HOME="$android_sdk"
export ANDROID_SDK_ROOT="$android_sdk"

output_dir="app/build/outputs/apk/release"
if [[ -d "$output_dir" ]]; then
  find "$output_dir" -maxdepth 1 -type f -name 'Xime-*.apk' -delete
fi

./gradlew :app:assembleRelease \
  -Pxime.localInstall=true \
  --console=plain
````

关键点：不要传 `-Pxime.abi`，否则只能构建指定 ABI 与对应受限产物，不能可靠满足 5 资产契约。

## 产物与签名验证

将 `<version>` 替换为刚从 `app/build.gradle.kts` 核实的版本。任何命令失败都必须停止发布。

````bash
set -euo pipefail

version='<version>'
output_dir='app/build/outputs/apk/release'
android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
build_tools_version="$(find "$android_sdk/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V | tail -n 1)"
apksigner="$android_sdk/build-tools/$build_tools_version/apksigner"

expected=(
  "Xime-$version-arm64-v8a.apk"
  "Xime-$version-armeabi-v7a.apk"
  "Xime-$version-universal.apk"
  "Xime-$version-x86.apk"
  "Xime-$version-x86_64.apk"
)

for name in "${expected[@]}"; do
  apk_file="$output_dir/$name"
  [[ -f "$apk_file" ]]
  certificate="$($apksigner verify --print-certs "$apk_file")"
  grep -F 'CN=Android Debug' <<<"$certificate" >/dev/null
  sha256sum "$apk_file"
done
````

验证输出必须保存到当前执行报告或 Release 正文摘要中；不要把一次性校验值固化为长期规则。

## 手动 GitHub Release 流程

1. 获得用户对新 tag 的明确确认。
2. 确保自动 Release workflow 不会运行，再创建并推送指向目标提交的 tag。
3. 使用已认证的 GitHub 客户端或 REST API 创建 **draft Release**；不得在上传前直接公开。
4. 上传 `expected` 数组中的全部 5 个 APK。不要依赖宽泛 glob 将旧产物或不相关文件带入 Release。
5. 读取 GitHub Release API，比较实际资产名称集合与预期集合；同时核对每个资产 `state=uploaded`、非零大小及本地 SHA-256。
6. Release 正文至少说明：基准版本、目标提交、支持架构、测试签名限制、五个 APK 的 SHA-256。
7. 仅在全部验证通过后，将 draft 改为公开 Release。
8. 发布后再次验证：tag 目标提交、Release URL、`draft=false`、5 个资产名称与大小、下载 URL，以及该提交/tag 没有自动发布 Action 运行。
9. 认证信息只能从现有凭据助手或安全环境注入并保留在进程内存；不得输出、写入脚本、知识库或 Git 跟踪文件。

## 失败处理

- **构建或签名失败**：停止；修复后重新生成全部产物，不上传旧文件。
- **资产不全或上传失败**：保持 draft；补齐并重新验证。若错误 Release 已公开，先删除 Release，再重建 draft。除非用户明确要求，不删除 tag。
- **自动 Action 被触发**：立即停止手动发布并取消该运行；查明并禁用自动路径后再继续，避免两个发布者竞争同一 tag/Release。
- **tag 冲突**：不得 force-push 或移动现有 tag；选择新 tag 并重新取得用户确认。
- **签名策略冲突**：以用户当前明确指示为准，并在 Release 正文中披露；不得偷偷切换生产签名或读取/输出签名凭据。

## 本次经验

- 错误模式：使用 `just build-release-arm64` 后只上传 `Xime-2.8.0-arm64-v8a.apk`，导致 Release 缺少另外四个必需资产。
- 正确边界：完整发布必须省略 `-Pxime.abi`，一次构建四种 ABI 与 universal，并在公开前验证精确资产集合。
- 2026-09-19 已实际验证上述全架构 Gradle 命令生成并通过 `apksigner verify` 的 5 个目标 APK。

## 待解问题

- [TODO: 是否新增正式的 `just build-release-all` recipe，以消除手写环境初始化与验证命令。]
- [TODO: 若未来改用生产签名，需要用户明确授权，并同步更新构建命令、签名披露和升级兼容性说明。]

## 反论与数据空白

- 测试签名不适合需要稳定升级链的广泛正式分发；本规范记录的是当前用户明确指定的 fork 发布策略，不代表 Android 通用最佳实践。
- 当前没有专用全架构 Just recipe；因此仍需在每次发布前重新核对 `app/build.gradle.kts`，避免构建逻辑变化后继续沿用失效命令。
