# Noor – نور (Android starter app)

A dark navy-and-gold Android prayer companion built with Kotlin and Jetpack Compose.

## Included in this project

- Five daily prayer times using the Egyptian General Authority of Survey calculation method.
- A next-prayer panel and simple countdown.
- Automatic scheduled Adhan playback: the supplied Fajr recording is used for Fajr; the supplied general recording is used for Dhuhr, Asr, Maghrib and Isha.
- Morning and evening Azkar screens with repeat counters.
- Settings to enable/disable Adhan and change latitude/longitude.
- Alarm rescheduling after device restart.

## Your audio files

The two recordings you uploaded have been copied into the Android app:

- `app/src/main/res/raw/adhan.mp3` — uploaded file `168409.mp3` (used for Dhuhr, Asr, Maghrib and Isha).
- `app/src/main/res/raw/fajr_adhan.mp3` — uploaded file `168411.mp3` (used for Fajr).

## Open and build

1. Install Android Studio on a computer.
2. Extract this ZIP.
3. In Android Studio choose **Open** and select the `Noor` folder (the folder containing `settings.gradle.kts`).
4. Allow Gradle sync and install the Android SDK components Android Studio requests.
5. Connect an Android phone with USB debugging enabled, or create an emulator, then press **Run**.
6. On the phone, allow notifications and grant **Alarms & reminders / exact alarms** access when Android asks. Without exact-alarm access, Android may not deliver the Adhan at the precise scheduled time.

The project uses Android Gradle Plugin 8.7.3 / Gradle 8.9 and requires JDK 17. Android Studio may download Gradle and dependencies during the first sync.

## Location setup

The app starts with approximate Giza coordinates (30.0444, 31.2357). Open **الإعدادات** and replace the latitude and longitude with the coordinates for the place where you want prayer times calculated. These are manually entered coordinates; automatic GPS location is not implemented yet.

## Important notes

- This is the Android Studio source project, not a prebuilt APK. This workspace does not have the Android SDK or Gradle installed, so an APK could not be compiled or device-tested here.
- Android battery restrictions, notification permissions, exact-alarm permissions, and some device-manufacturer power settings can affect background playback. Check these settings on the phone after installing.
- The included Azkar list is a starter collection. Review the wording and repetition counts with a trusted Islamic reference before relying on it as a complete collection.
- Quran, Qibla compass, Hijri calendar, GPS auto-location, and a full settings page for Asr madhhab are not included in this first build.

## Main technologies

- Kotlin
- Jetpack Compose / Material 3
- Adhan prayer-time calculation library (`com.batoulapps.adhan:adhan:1.2.1`)

## Build an APK without installing Android Studio

A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`.

1. Create a new GitHub repository and upload the contents of this folder (keep the `.github` folder).
2. Open the repository's **Actions** tab. The build runs automatically on push, or press **Run workflow**.
3. When it finishes (about 5-10 minutes), open the run and download **Noor-debug-apk** from *Artifacts*.
4. Unzip it, copy `app-debug.apk` to your phone and install it (allow "Install unknown apps" when asked).

The result is a debug-signed APK, fine for installing on your own phone. A Play Store release needs a signed release build.
