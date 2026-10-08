# Commune Sky

**The sky belongs to everyone.** Commune Sky is a fast, beautiful, ad-free weather app for Android. No accounts, no trackers, no ads — just the weather, drawn with care.

> *Commune* — a community that shares what it has, and *to commune* — to feel close to something larger than yourself. Both fit the sky.

<!-- Screenshots: add images to docs/screenshots/ and uncomment
<p align="center">
  <img src="docs/screenshots/home.png" width="260">
  <img src="docs/screenshots/forecast.png" width="260">
  <img src="docs/screenshots/widget.png" width="260">
</p>
-->

## Features

- **A living sky** — an animated background that follows the weather and the time of day: sun, moon and stars, drifting clouds, rain, snow, fog and lightning, with sunrise and sunset colours.
- **Hourly and 7-day forecast** — a temperature curve for the next 24 hours, temperature-coloured daily range bars, and precipitation chance on every row.
- **Details at a glance** — feels-like, humidity, wind with compass, UV index, sun path, visibility, pressure, precipitation and cloud cover.
- **Honest skies** — clear/cloudy conditions are recomputed from sunshine duration and cloud layers, so thin high cirrus doesn't turn a sunny day "overcast".
- **Offline-first** — the last forecast appears instantly from the local cache, then refreshes in the background. Pull to refresh any time.
- **Home screen widgets** — compact, wide, hourly and forecast layouts that share the app's sky colours and icons.
- **Your location, if you want it** — uses approximate location only; without permission it falls back to Istanbul.
- **English and Turkish**, with per-app language support.
- **Fast** — R8-optimised release build, Baseline Profiles for quick start-up, and Compose stability tuning for smooth animations.

See the [changelog](CHANGELOG.md) for what's new in each version.

## Privacy

Commune Sky has no ads, no analytics and no accounts. Your approximate location is sent only to Open-Meteo to get the forecast for where you are, and is never collected by the developer. See the [Privacy Policy](PRIVACY.md).

## Building

Requirements: Android Studio (latest stable), JDK 17+, an Android 8.0+ (API 26) device or emulator.

```bash
git clone https://github.com/alihaydarsayar/zAhs_CommuneSky.git
```

Open the folder in Android Studio, let Gradle sync, then press **Run**. No API key is needed.

To build a release:

```bash
./gradlew :app:assembleRelease
```

## Tech stack

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Retrofit + kotlinx.serialization · Room · WorkManager · Jetpack Glance · Fused Location Provider · Baseline Profiles

## Credits

- Weather data by [Open-Meteo.com](https://open-meteo.com/), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- [Outfit](https://github.com/Outfitio/Outfit-Fonts) typeface, licensed under the [SIL Open Font License 1.1](third_party/Outfit-OFL.txt).

## License

Commune Sky is free software, released under the [GNU General Public License v3.0](LICENSE). You are free to use, study, share and improve it — and anything built from it must stay free too.

Copyright © 2026 Ali Haydar Sayar
