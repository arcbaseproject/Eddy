# Notes for coding agents

Follow [CONTRIBUTING.md](CONTRIBUTING.md) for checks, commit style and project rules.

## Every release needs a changelog

Each version bump must come with changelog files for the new `versionCode`, committed in the release commit:

- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
- `fastlane/metadata/android/es-ES/changelogs/<versionCode>.txt` (the same notes in Spanish)

Eddy downloads these files from `main` on GitHub the first time it opens after an update and shows them in the
"What's new" panel. If the file for the new `versionCode` is missing on `main`, users see no changelog for that
version, so push the release commit before the APK goes out. F-Droid shows the same files.

Format: one change per line, each starting with `* `, written for users rather than developers. Keep it short.
