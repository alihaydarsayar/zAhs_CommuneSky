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
- **Your places** — search any city, district or neighbourhood worldwide, save it, drag to reorder and mark one as Home. Swipe between places on the home screen.
- **Checked against real measurements** — current conditions are compared with the nearest weather station (MGM in Türkiye, airport reports elsewhere). A nearby station sets the current temperature and sky; a farther one warns about storms on the way.
- **Home screen widgets** — weather, clock and "Home and my location" widgets in several sizes, each with its own place and a sky, glass or clear background.
- **Your location, if you want it** — approximate location shows your district; optional precise location shows your neighbourhood and picks the closest station. Without permission it shows your saved places, or Istanbul.
- **Units and theme** — °C or °F, four wind units, light or dark.
- **English and Turkish**, with per-app language support.
- **Fast** — R8-optimised release build, Baseline Profiles for quick start-up, and Compose stability tuning for smooth animations.

See the [changelog](CHANGELOG.md) for what's new in each version. Contributors: please read [CONTRIBUTING.md](CONTRIBUTING.md).

## Privacy

Commune Sky has no ads, no analytics and no accounts, and the developer never receives your location, searches or saved places. To show the weather the app talks directly to:

- **Open-Meteo** (forecast and place search): coordinates of your location and saved places, rounded to about 1 km; the text you search for
- **Photon by komoot** (place search): the text you search for
- **MGM**, Turkish State Meteorological Service (current conditions in Türkiye): rounded coordinates and the province name once to find nearby stations, then station numbers
- **NOAA Aviation Weather Center** (airport reports, everywhere): an area of about 50 km around the place
- **Android Geocoder** (district and neighbourhood names, and searches as a last resort): your coordinates; the search text

Forecasts, saved places, measurements and settings are stored only on your device. The full policy, in English and Turkish, is at [alihaydarsayar.com/communesky/privacy](https://alihaydarsayar.com/communesky/privacy/).

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

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Retrofit + kotlinx.serialization · Room · DataStore · WorkManager · Jetpack Glance · Navigation Compose · Fused Location Provider · Baseline Profiles

## Credits

- Weather data by [Open-Meteo.com](https://open-meteo.com/), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Station measurements from the [Turkish State Meteorological Service (MGM)](https://www.mgm.gov.tr/) and airport reports from the [NOAA Aviation Weather Center](https://aviationweather.gov/).
- Place search by [Photon](https://photon.komoot.io/) (komoot), using data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, and [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api) (GeoNames).
- [Outfit](https://github.com/Outfitio/Outfit-Fonts) typeface, licensed under the [SIL Open Font License 1.1](third_party/Outfit-OFL.txt).

## License

Commune Sky is free software, released under the [GNU General Public License v3.0](LICENSE). You are free to use, study, share and improve it — and anything built from it must stay free too.

Copyright © 2026 Ali Haydar Sayar
