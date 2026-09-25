# Publishing Eddy on F-Droid

F-Droid builds every app from source on its own servers and signs the result itself. Getting Eddy in
takes three things: a clean source tree, a tagged release, and a merge request against the metadata
repository.

## 1. Keep the tree buildable from source

F-Droid rejects prebuilt binaries and minified bundles committed to the repository.

* `app/src/main/assets/eruda.min.js` is a minified bundle. The F-Droid build removes it (see the
  `prebuild` line in `app.eddy.browser.yml`), and `DevTools.available()` hides the developer-tools
  toggle when the asset is absent. Nothing else needs to change.
* `app/src/main/assets/readability.js` is unminified upstream source (Apache-2.0) and is fine as is.
* Every dependency is AndroidX, Kotlin or Compose. There are no Google Play services, so no
  anti-features apply.

## 2. Tag a release

```
git tag -a v0.5.1-beta -m "0.5.1-beta"
git push origin v0.5.1-beta
```

The tag name must match `UpdateCheckMode: Tags` in the recipe, and `versionCode` in
`app/build.gradle.kts` must go up with every release.

## 3. Open the merge request

1. Fork https://gitlab.com/fdroid/fdroiddata
2. Copy `fdroid/app.eddy.browser.yml` to `metadata/app.eddy.browser.yml` in that fork.
3. Test it locally if you can: `fdroid build -v -l app.eddy.browser` (needs the fdroidserver tools).
4. Open a merge request. Reviews take weeks, not days.

Store text and screenshots live in `fastlane/metadata/android/en-US/`; F-Droid reads them straight
from this repository, so changes there show up on the listing without touching the recipe.

## Faster alternative: IzzyOnDroid

The IzzyOnDroid repository takes apps within days and reads the same fastlane metadata. It needs a
GitHub release with an APK attached, then a request at https://gitlab.com/IzzyOnDroid/repo/-/issues
