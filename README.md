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

## 繁體中文

一次性的 Android 小工具：讓 Zyll 相簿出現在系統相片挑選器的「雲端媒體應用程式」中。執行一次即可解除安裝，設定會保留；也可以一鍵解除。

- **需要 Android 15 或更新版本**，並連線 Wi-Fi（用於無線偵錯）。
- 只與手機本身的無線偵錯服務通訊，不連線至任何伺服器，不收集資料。

使用手冊：<https://gaomou88.github.io/Zyll-photo-help/zh-Hant/>

## 日本語

Zyll フォトを Android の写真選択ツールの「クラウドメディア アプリ」に表示させるための一回限りのツールです。一度実行すればアンインストールしても設定は残ります。ワンタップで元に戻すこともできます。

- **Android 15 以降**と Wi-Fi 接続（ワイヤレス デバッグ用）が必要です。
- 端末自身のワイヤレス デバッグとだけ通信し、サーバーには接続せず、データも収集しません。

使い方ガイド：<https://gaomou88.github.io/Zyll-photo-help/ja/>

## License

GPL-3.0. Uses [libadb-android](https://github.com/MuntashirAkon/libadb-android) (GPL-3.0 / Apache-2.0), Conscrypt (Apache-2.0) and Bouncy Castle (MIT).
