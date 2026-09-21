# DailyHorse / dailyRecord

用于提醒牛马，及时考勤打卡。

![Main Picture](https://github.com/Ruogu7/DailyHorse/blob/main/Mainpicture.png)/to/image "MainPicture"

![GitHub Logo](https://githubassets.com)




Android location check-in reminder app (`com.dailyrecord.app`).

## Included

- `dailyRecord/` — Android Studio project source.
- `dailyRecord-build/outputs/apk/release/app-release.apk` — latest release APK.
- `dailyRecord-build/image/` — app artwork source files.

## Local setup

1. Open `dailyRecord/` in Android Studio.
2. Copy `dailyRecord/local.properties.example` to `dailyRecord/local.properties`.
3. Set `sdk.dir` and your own AMap Android key in `local.properties`.
4. Build with Android Studio or Gradle.

Local SDK paths, AMap keys, signing credentials, and Gradle intermediates are excluded from Git. Release signing requires the owner's private keystore and local signing configuration; never commit those files.

The checked-in release APK is signed with the project's release certificate. Keep that certificate backed up to publish compatible updates.
