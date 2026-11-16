# Zyll Photo Helper

[English](#english) · [简体中文](#简体中文) · [繁體中文](#繁體中文) · [日本語](#日本語)

## English

A one-time Android tool that lets [Zyll Photo](https://play.google.com/store/apps/details?id=com.zyll.photo) appear as a **cloud media app** in Android's system photo picker. Run it once, then uninstall it. The setting stays. It can also undo the change in one tap.

- **Requires Android 15 or later** and Wi-Fi (for Wireless debugging).
- Talks only to your phone's own Wireless debugging service. No internet servers, no data collected.
- Runs only the fixed `device_config` commands in [`PickerAllowlist.kt`](app/src/main/kotlin/com/zyll/photo/helper/PickerAllowlist.kt), and shows each command and its result before and while it runs.
- Writes with `device_config override`, so server config syncs don't wipe it. Undo uses `clear_override`, which puts back exactly what the system had.

Step-by-step guide: <https://gaomou88.github.io/Zyll-photo-help/>

Download the signed APK from [Releases](../../releases) and check its SHA-256 against the `.sha256` file next to it.

### Every command it can run

These are all of them. They come from [`PickerAllowlist.kt`](app/src/main/kotlin/com/zyll/photo/helper/PickerAllowlist.kt) and nothing else, and [unit tests](app/src/test/kotlin/com/zyll/photo/helper/PickerAllowlistTest.kt) check that a package name read from the device can't turn into an extra command. `<list>` is the system's current list, validated as package names, with `com.zyll.photo` added or removed.

| When | Command | What it does |
|---|---|---|
| Connect, undo | `device_config get mediaprovider allowed_cloud_providers` | Reads the list of apps the photo picker accepts as a cloud media app. Changes nothing. |
| Connect | `device_config override mediaprovider allowed_cloud_providers <list>` | Adds Zyll Photo to that list and keeps the apps already on it (for example Google Photos). |
| Connect | `device_config override mediaprovider cloud_media_feature_enabled true` | Turns on the photo picker's cloud media option. |
| Undo | `device_config clear_override mediaprovider allowed_cloud_providers` | Removes the helper's change; the system's own list comes back. |
| Undo | `device_config clear_override mediaprovider cloud_media_feature_enabled` | Same for the cloud media option. |
| Undo, only if Zyll Photo is still listed | `device_config put mediaprovider allowed_cloud_providers <list>` or `device_config delete mediaprovider allowed_cloud_providers` | Removes Zyll Photo left behind by an older version; other apps on the list stay. |
| End (optional, on by default) | `settings put global adb_wifi_enabled 0` | Turns Wireless debugging off. The connection ends right away. |

### Permissions and network

- Android permissions: only `INTERNET` and `POST_NOTIFICATIONS`. `INTERNET` is what Android requires to open a socket at all; the helper connects only to the phone's own Wireless debugging service on the local network and never to an internet server. The notification is where you type the pairing code.
- No analytics, no ads, no accounts, nothing is uploaded.

### Check the APK

Each [release](../../releases) has the APK and a `.sha256` file, and is built by the [release workflow](.github/workflows/release.yml) from the tagged source. To check a download, compare `sha256sum zyll-photo-helper-vX.Y.Z.apk` (Windows: `certutil -hashfile <file> SHA256`) with the `.sha256` file. You can also build it yourself (below) and read every line that runs.

### Build

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The release APK is unsigned unless you provide a keystore, either in `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or through the `HELPER_STORE_FILE`, `HELPER_STORE_PASSWORD`, `HELPER_KEY_ALIAS` and `HELPER_KEY_PASSWORD` environment variables.

## 简体中文

一次性的安卓小工具：让 Zyll 相册出现在系统照片选择器的“云端媒体应用”里。运行一次即可卸载，设置会保留；也可以一键解除。

- **需要 Android 15 或更高版本**，并连接 Wi-Fi（用于无线调试）。
- 只和手机自身的无线调试服务通信，不连接任何服务器，不收集数据。
- 只执行 `PickerAllowlist.kt` 里固定的几条 `device_config` 命令，执行前和执行时都会显示命令原文和结果。
- 用 `device_config override` 写入，不会被系统配置同步覆盖；解除时用 `clear_override`，恢复成系统原来的值。

使用手册：<https://gaomou88.github.io/Zyll-photo-help/zh-Hans/>

### 它会执行的全部命令

下表就是全部命令，都写死在 [`PickerAllowlist.kt`](app/src/main/kotlin/com/zyll/photo/helper/PickerAllowlist.kt) 里，没有别的来源。[单元测试](app/src/test/kotlin/com/zyll/photo/helper/PickerAllowlistTest.kt)会检查从手机读回来的包名不会被拼成额外的命令。`<名单>` 是系统现有的名单，逐个按包名格式校验后，加上或去掉 `com.zyll.photo`。

| 什么时候 | 命令 | 作用 |
|---|---|---|
| 接入、解除 | `device_config get mediaprovider allowed_cloud_providers` | 读出照片选择器允许的云端媒体应用名单，不做任何修改 |
| 接入 | `device_config override mediaprovider allowed_cloud_providers <名单>` | 把 Zyll 相册加进名单，名单里原有的应用（比如 Google 相册）都保留 |
| 接入 | `device_config override mediaprovider cloud_media_feature_enabled true` | 打开照片选择器的云端媒体选项 |
| 解除 | `device_config clear_override mediaprovider allowed_cloud_providers` | 撤掉助手写入的值，名单恢复成系统原来的 |
| 解除 | `device_config clear_override mediaprovider cloud_media_feature_enabled` | 云端媒体选项同样恢复原值 |
| 解除，且名单里还有 Zyll 相册时 | `device_config put mediaprovider allowed_cloud_providers <名单>` 或 `device_config delete mediaprovider allowed_cloud_providers` | 去掉旧版本留下的 Zyll 相册，名单里的其他应用保留 |
| 结束时（可选，默认勾选） | `settings put global adb_wifi_enabled 0` | 关闭无线调试，连接随即断开 |

### 权限和网络

- 安卓权限只有 `INTERNET` 和 `POST_NOTIFICATIONS` 两个。`INTERNET` 是安卓打开任何网络连接都必须申请的，助手只连接手机自己在局域网里的无线调试服务，不连接任何互联网服务器。通知用来输入配对码。
- 没有统计、没有广告、没有账号，不上传任何数据。

### 核对安装包

每个[发布版本](../../releases)都附带 APK 和 `.sha256` 文件，由[发布流程](.github/workflows/release.yml)从对应标签的源码自动构建。下载后用 `sha256sum zyll-photo-helper-vX.Y.Z.apk`（Windows：`certutil -hashfile <文件> SHA256`）算出的值和 `.sha256` 文件比对。也可以按上面英文部分的 Build 自己从源码构建，执行的每一行都能看到。

## 繁體中文

一次性的 Android 小工具：讓 Zyll 相簿出現在系統相片挑選器的「雲端媒體應用程式」中。執行一次即可解除安裝，設定會保留；也可以一鍵解除。

- **需要 Android 15 或更新版本**，並連線 Wi-Fi（用於無線偵錯）。
- 只與手機本身的無線偵錯服務通訊，不連線至任何伺服器，不收集資料。
- 只執行 `PickerAllowlist.kt` 中固定的幾條 `device_config` 命令（完整清單見上方 [Every command it can run](#every-command-it-can-run)），執行前和執行時都會顯示命令原文和結果。
- Android 權限只有 `INTERNET`（只用來連線手機自己的無線偵錯服務）和 `POST_NOTIFICATIONS`（輸入配對碼）。

使用手冊：<https://gaomou88.github.io/Zyll-photo-help/zh-Hant/>

## 日本語

Zyll フォトを Android の写真選択ツールの「クラウドメディア アプリ」に表示させるための一回限りのツールです。一度実行すればアンインストールしても設定は残ります。ワンタップで元に戻すこともできます。

- **Android 15 以降**と Wi-Fi 接続（ワイヤレス デバッグ用）が必要です。
- 端末自身のワイヤレス デバッグとだけ通信し、サーバーには接続せず、データも収集しません。
- 実行するのは `PickerAllowlist.kt` に固定された数個の `device_config` コマンドだけです（全一覧は上の [Every command it can run](#every-command-it-can-run)）。実行前と実行中にコマンドと結果を表示します。
- Android の権限は `INTERNET`（端末自身のワイヤレス デバッグへの接続のみ）と `POST_NOTIFICATIONS`（ペアリング コードの入力）だけです。

使い方ガイド：<https://gaomou88.github.io/Zyll-photo-help/ja/>

## License

GPL-3.0. Uses [libadb-android](https://github.com/MuntashirAkon/libadb-android) (GPL-3.0 / Apache-2.0), Conscrypt (Apache-2.0) and Bouncy Castle (MIT).
