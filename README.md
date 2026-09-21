# DailyHorse / dailyRecord

用于提醒牛马，及时考勤打卡。

![Main Picture](https://github.com/Ruogu7/DailyHorse/blob/main/Mainpicture.png)


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

## Reminder behavior (v0.2.0)

- Default morning window: 07:00 to 09:00. The first arrival within 80 m of any saved office triggers one alarm-and-vibration reminder per day.
- Default evening window: 17:30 to 23:30. Leaving an 80 m office geofence triggers an alarm-and-vibration reminder. Further evening reminders are suppressed until more than 30 minutes after the previous one.
- When monitoring is enabled during the evening window, the first valid location fix triggers a reminder if the phone is already more than 80 m from every saved office, subject to the same cooldown.
- The monitor restores after device boot and app update when monitoring was enabled. Grant precise and all-the-time background location, allow notifications, and on Huawei enable Auto-launch, Secondary launch, and Run in background for DailyRecord. Set battery management to unrestricted where available. Android and device power policies can still affect location update timing.
