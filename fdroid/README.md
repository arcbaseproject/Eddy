# Publishing Eddy on F-Droid

Eddy was submitted in [fdroiddata!50152](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50152).
The recipe there is copied in `app.eddy.browser.yml`.

F-Droid builds the app from source with `eruda.min.js` removed, since it does not accept prebuilt
minified bundles. `DevTools.available()` hides the developer-tools toggle when that asset is missing.

## Reproducible builds

F-Droid ships the APK signed with our key rather than its own, so people can move between the
F-Droid, GitHub and IzzyOnDroid versions without reinstalling. For that to work, F-Droid's build must
match an APK we publish byte for byte. Each release therefore gets two APKs:

* `eddy-<version>.apk`: the normal build, with developer tools.
* `eddy-<version>-fdroid.apk`: built without `eruda.min.js`. F-Droid downloads this one (see
  `Binaries` in the recipe), rebuilds it from source and checks that they match.

## Releasing

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`, and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Commit, then tag and push: `git tag -a v<version> -m "<version>" && git push origin main v<version>`.
   The tag must match `UpdateCheckMode: Tags` in the recipe.
3. Build `eddy-<version>.apk` as usual with `./gradlew :app:assembleRelease`.
4. Build the F-Droid APK from a clean clone of the tag, so nothing untracked gets in:

   ```
   git clone --branch v<version> https://github.com/arcbaseproject/Eddy.git /tmp/eddy-fdroid
   cd /tmp/eddy-fdroid
   rm app/src/main/assets/eruda.min.js
   cp <path to>/keystore.properties <path to>/local.properties .
   ./gradlew :app:assembleRelease
   ```

   Rename `app/build/outputs/apk/release/app-release.apk` to `eddy-<version>-fdroid.apk`.
5. Attach both APKs to the GitHub release.

F-Droid's bot picks up the new tag and updates its recipe on its own. If its build doesn't match,
F-Droid doesn't publish that version and reports the difference on its build log.
