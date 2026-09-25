# Contributing to Eddy

Thanks for helping. Bug reports, fixes and small focused features are all welcome.

## Reporting bugs

Open an issue with:

- Eddy version (Settings > About) and Android version
- Android System WebView version (Settings > Apps > Android System WebView)
- Steps to reproduce, what you expected, and what happened
- The URL, if the bug is site-specific and the site is public

For security issues, do not open a public issue. Contact the maintainer privately through GitHub.

## Setup

Build requirements and the architecture overview are in the [README](README.md). Before you open a
pull request, run the same checks CI runs:

```
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Pull requests

- Keep each PR to one change. Open an issue first for large features, so we can agree on the approach.
- Add a unit test in `app/src/test/` when you change logic that can be tested without a device
  (URL handling, parsing, blocking, download names). Plain JUnit, no extra frameworks.
- Test UI changes on a real device or emulator. Include screenshots for visible changes.
- Write commit messages as a short imperative summary, for example `Fix zero-height viewport units`.
- Do not bump `versionCode` or `versionName`. The maintainer does this at release time.

## Project rules

- **Privacy first.** No analytics, telemetry, crash reporters, ads or third-party network calls.
  If a change affects what data Eddy stores or sends, update [PRIVACY.md](PRIVACY.md) and the
  in-app text under Settings > Legal.
- **Stay lightweight.** Avoid new dependencies. If one is truly needed, explain why in the PR.
  The release APK is about 3.5 MB; keep it small.
- **Keep browsing logic out of Compose.** Tab, WebView, blocking and download logic stays
  independent of the UI. Anything that needs an Activity goes through a `UiEffect`.
- **Database changes.** If you change a Room entity, bump the database version in
  `EddyDatabase.kt`, add a migration, and commit the generated schema JSON in `app/schemas/`.
- **Original assets only.** Do not copy code, icons or assets from other browsers.
- **Secrets.** Never commit `keystore.properties`, `local.properties` or signing keys.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
