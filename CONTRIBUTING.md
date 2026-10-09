# Contributing to Commune Sky

Thanks for helping. A few rules keep the app honest and the store listing correct.

## Privacy policy

- The privacy policy lives on the website, not in this repository: [alihaydarsayar.com/communesky/privacy](https://alihaydarsayar.com/communesky/privacy/).
- Its source is `webapp/communesky/privacy/index.html` in the `alihaydarsayar.com` website repository. The site is published automatically when `main` is pushed.
- Any change that collects, sends or stores data must update that page in **both English and Turkish**, and change its "Last updated" date. This includes a new service the app talks to, new data sent to an existing service, and new data kept on the device.
- After such a change, check the **Data safety** form in Google Play Console and update it if needed.
- Keep the privacy section in [README.md](README.md) in line with the website.

## Releases

- Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
- Add a section for the new version to [CHANGELOG.md](CHANGELOG.md), written for users in plain language.

## Code

- All user-facing text goes in `strings.xml`, in English (`values`) and Turkish (`values-tr`).
- Run the unit tests before committing: `./gradlew :app:testDebugUnitTest`.
- Database changes need a migration (see `WeatherDatabase`), never a destructive reset: saved places live there.
