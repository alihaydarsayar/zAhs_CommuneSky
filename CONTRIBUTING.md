# Contributing to Commune Sky

Thanks for helping. A few rules keep the app honest, the store listing correct and the code easy to change.

- [Ways to help](#ways-to-help)
- [Getting set up](#getting-set-up)
- [How the code is organised](#how-the-code-is-organised)
- [Making a change](#making-a-change)
- [Testing](#testing)
- [Debug-only helpers](#debug-only-helpers)
- [Privacy rules](#privacy-rules)
- [Releases](#releases)

## Ways to help

- **Report a bug** with the [bug report form](https://github.com/alihaydarsayar/zAhs_CommuneSky/issues/new?template=bug_report.md). Screenshots help most; for a widget problem, say which widget and style, its size and your launcher.
- **Suggest an idea** with the [feature request form](https://github.com/alihaydarsayar/zAhs_CommuneSky/issues/new?template=feature_request.md).
- **Translate or fix wording.** The app is in English, Turkish and French.
- **Send a pull request.** For anything bigger than a small fix, open an issue first so we can agree on the direction.

By contributing you agree that your work is released under the [GPL-3.0](LICENSE) like the rest of the project.

## Getting set up

1. Install Android Studio (latest stable) and JDK 17+.
2. Clone the repository and open the folder in Android Studio; let Gradle sync.
3. Run the **app** configuration on a device or emulator with Android 8.0+ (API 26). No API key is needed.

Handy command line checks:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug   # what CI-style checks should pass before a commit
./gradlew :app:assembleRelease                    # R8-optimised build
```

## How the code is organised

All app code is under `app/src/main/java/com/alihaydarsayar/communesky/`:

| Folder | What lives there |
|---|---|
| `data/` | Repositories and sources: `remote` (Open-Meteo), `mgm` (Turkish State Meteorological Service), `observation` (stations and METAR), `places`, `location`, `settings`, `accuracy`, `local` (Room database and migrations). |
| `model/` | Plain data and the pure logic around it (forecast, moon, warnings, naming of places, home detection…). Most unit tests live here. |
| `ui/` | Compose screens: `home`, `detail`, `today`, `places`, `settings`, the shared `common` controls, the animated `sky` and the `theme`. |
| `widget/` | Jetpack Glance widgets: `WidgetKind` (10 widgets, 39 styles), the styles per kind, colours, the bitmaps of the radial clock, and the configuration screens in `config/`. |
| `work/` | WorkManager jobs and the notification code: refreshes, warnings, summaries, air quality, channels. |
| `di/` | Hilt modules. |

Debug-only code is in `app/src/debug/`, with a no-op twin in `app/src/release/`.

## Making a change

- **Keep changes small and focused**, one idea per commit, with a message that says *what* and *why* in plain words ("Keep a fresh station reading when a refresh fails"). Do not add generated-by or co-author trailers.
- **Text in the app** goes in `strings.xml` in English (`values`), Turkish (`values-tr`) and French (`values-fr`), all three in the same change. No hard-coded strings in the code.
- **Logic first, UI second.** Put decisions in a small pure function or object and test it on the JVM; keep composables thin.
- **Database changes need a migration** (see `WeatherDatabase`), never a destructive reset: saved places live there.
- **Widgets** must work at every size a launcher can report and in all three languages. After a change to a widget style, run the size audit (below) for that style.
- **Do not commit** build outputs (`/app/release/`, `*.aab`, `*.apk`), signing keys or local notes. The `.gitignore` already covers them.
- Run `./gradlew :app:testDebugUnitTest :app:lintDebug` before you commit and fix what it reports.

## Testing

```bash
./gradlew :app:testDebugUnitTest          # unit tests (JVM), a few hundred, under a minute
./gradlew :app:connectedDebugAndroidTest  # on-device tests: database migrations, widgets, notifications, background work
```

- **New logic gets a unit test**, and a bug fix starts with a test that fails for the bug.
- **Examples in tests** use fictional or generic places (Beşiktaş, Liverpool, Limerick). Never put a real personal location in a test or a commit.
- `WidgetSizeAuditTest` renders every widget style at many launcher sizes (about 40 minutes) and is skipped by default. Run it on its own, or only for some styles, with:

  ```bash
  ./gradlew :app:connectedDebugAndroidTest -PslowAudit \
    -Pandroid.testInstrumentationRunnerArguments.class=com.alihaydarsayar.communesky.widget.WidgetSizeAuditTest \
    -Pandroid.testInstrumentationRunnerArguments.style=ClockRadial,WeeklyBars
  ```

  It writes an image of every drawing and an `olcu.csv` report (which layout was used, whether any text was cut, how much of the area is filled) under `Android/data/com.alihaydarsayar.communesky/files/audit/`.
- Before a release also try: a fresh install, an update from the previous version, airplane mode, a different time zone, large text and display size, and light and dark themes.

## Debug-only helpers

The debug build has helpers to try situations that are hard to produce. They are not compiled into release builds (the `release` source set has empty twins); check this when you add one.

- **Fake network failure** — `adb shell am broadcast -a com.alihaydarsayar.communesky.debug.FAULT -n com.alihaydarsayar.communesky/.debug.NetworkFaultReceiver --es mode offline` (modes: `offline`, `slow`, `cut`, `bad`, `error`, `none`; optional `--es host open-meteo.com`).
- **Fake warnings** — rows at the bottom of the Settings screen send a fake orange or red warning, raise it, lift it, and send the morning or evening summary at once.

## Privacy rules

- The privacy policy lives on the website, not in this repository: [alihaydarsayar.com/communesky/privacy](https://alihaydarsayar.com/communesky/privacy/).
- Its source is `webapp/communesky/privacy/index.html` in the `alihaydarsayar.com` website repository. The site is published automatically when `main` is pushed.
- Any change that collects, sends or stores data must update that page in **both English and Turkish**, and change its "Last updated" date. This includes a new service the app talks to, new data sent to an existing service, and new data kept on the device.
- The app keeps nothing on the phone only for troubleshooting. Do not add logs, counters or usage records.
- After such a change, check the **Data safety** form in Google Play Console and update it if needed.
- Keep the privacy section in [README.md](README.md) and [README.tr.md](README.tr.md) in line with the website.

## Releases

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`. A version that has been uploaded to Google Play is never reused: the next change is the next version.
2. Add a section for the new version to [CHANGELOG.md](CHANGELOG.md), written for users in plain language, one short line per change.
3. Run the unit tests, lint and the on-device tests, and install the **release** build once to check the main flows (R8 can change behaviour that debug builds hide).
4. Build the bundle (`./gradlew :app:bundleRelease`) with your own signing key, upload it, and paste the changelog lines into the store listing in English and Turkish.
