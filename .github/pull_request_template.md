## What and why

<!-- One or two sentences in plain words. -->

## Checklist

- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` passes
- [ ] New logic has a unit test; a bug fix has a test that failed before the fix
- [ ] New texts are in English, Turkish and French (`values`, `values-tr`, `values-fr`)
- [ ] Widgets: tried at small and large sizes (and `WidgetSizeAuditTest` for the changed style)
- [ ] Anything collected, sent or stored? Then the privacy page (English and Turkish) and the README privacy section are updated
- [ ] User-visible change: one short line added to `CHANGELOG.md`
- [ ] No build outputs, keys or personal locations in the commit
