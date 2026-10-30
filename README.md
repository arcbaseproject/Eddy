> [!CAUTION]
> **Eddy 0.6.25 and later need a one-time reinstall.**
>
> The key that signed Eddy 0.6.15 through 0.6.24 has been lost, so those versions and every version
> from 0.6.25 on are signed with different keys. Android will not update an older Eddy to the new
> ones: it shows **"App not installed"**. To switch:
>
> 1. **Save your passwords.** In Eddy, open **Menu > Passwords**, tap **⋮** and choose **Export to CSV**.
> 2. **Uninstall Eddy.** This deletes your tabs, history and bookmarks.
> 3. **Install the new version** from the [releases page](https://github.com/arcbaseproject/Eddy/releases).
> 4. **Restore your passwords.** Open **Menu > Passwords**, tap **⋮**, choose **Import from CSV** and
>    pick the file you exported. Then delete that file: it is not encrypted.
>
> You only need to do this once. Later updates install normally.
>
> <details><summary>How the key was lost</summary>
>
> The keystore lived in a `.tar.zst` archive rather than in a real backup. While migrating this
> machine from Void Linux to NixOS, that archive turned out to be corrupt: extracting it on the new
> system produced everything except the keystore, and the Void install was already gone. There was no
> second copy anywhere, so the key is unrecoverable.
>
> This is the second time an Eddy signing key has been lost. The new key is held in a password manager
> as well as on disk.
>
> </details>

<p align="center"><img src="logo.png" alt="Eddy logo" width="112"></p>

# Eddy

<p align="center"><a href="https://discord.gg/jQDnjD3KE"><img src="https://img.shields.io/badge/Discord-Join%20chat-5865F2?logo=discord&logoColor=white" alt="Join the Eddy Discord"></a></p>

> **Beta.** Eddy is in active development. Expect rough edges.

A fast, lightweight, privacy-focused Android browser built on the system WebView, with a Material 3
Expressive interface. Kotlin, Jetpack Compose, no ads, no analytics, no telemetry.

Eddy is an original app. It takes its *feel* (floating one-hand chrome, expressive shapes, springy
motion, a calm start page) from the lightweight-browser school of design, but shares no code, name,
icons or assets with any other browser.

## Community

Questions, bug reports and feedback are welcome on the [Eddy Discord](https://discord.gg/YOUR_INVITE_CODE).
Bugs can also be reported as [GitHub issues](https://github.com/arcbaseproject/Eddy/issues).

## Screenshots

<p align="center">
  <img src="docs/screenshots/home.png" width="180" alt="New-tab page">
  <img src="docs/screenshots/browsing.png" width="180" alt="Browsing with the single bottom bar">
  <img src="docs/screenshots/tabs.png" width="180" alt="Tab switcher">
</p>
<p align="center">
  <img src="docs/screenshots/menu.png" width="180" alt="Page menu">
  <img src="docs/screenshots/incognito.png" width="180" alt="Incognito tab">
  <img src="docs/screenshots/settings.png" width="180" alt="Settings">
</p>

The welcome tour on first launch:

<p align="center">
  <img src="docs/screenshots/tour-welcome.png" width="180" alt="Welcome tour, first slide">
  <img src="docs/screenshots/tour-private.png" width="180" alt="Welcome tour, privacy slide">
  <img src="docs/screenshots/tour-customize.png" width="180" alt="Welcome tour, theme and search engine picker">
</p>

## Install and update

Download the APK from [GitHub releases](https://github.com/arcbaseproject/Eddy/releases). Eddy does
not update itself. To get updates automatically, use [Obtainium](https://obtainium.imranr.dev):

1. In Obtainium, tap **Add App**.
2. Paste `https://github.com/arcbaseproject/Eddy` as the source URL.
3. Turn on **Include prereleases**. Every Eddy release is a beta published as a GitHub pre-release,
   so Obtainium finds no updates without this setting.
4. Tap **Add**, then install.

## Build

Requirements: JDK 21+ (tested on 26), Android SDK with platform 37 and build-tools 37.

```
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # R8-shrunk release APK (~3.5 MB, unsigned)
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # lint
```

`minSdk 29`, `targetSdk 37`. Stack: AGP 9.4, Kotlin 2.4, Compose BOM 2026.09 with Material 3
1.5 (Expressive), Room, DataStore, androidx.webkit, androidx.graphics-shapes.
No image-loading library: favicons and thumbnails are handled by small dedicated caches.

## Packaging

Store listing text and screenshots live in `fastlane/metadata/android/en-US/`, the layout
F-Droid and IzzyOnDroid read directly from the repository. The F-Droid build recipe and submission
steps are in [`fdroid/`](fdroid/README.md); that build drops the prebuilt `eruda.min.js`, and the app
hides the developer-tools toggle when the asset is missing. Each release is a signed APK attached to a
[GitHub release](https://github.com/arcbaseproject/Eddy/releases), with the changelog under
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## Architecture

```
app/src/main/java/app/eddy/browser/
  EddyApp, AppContainer      manual DI; everything heavy is lazy
  MainActivity               edge-to-edge host, file chooser, runtime permissions, fullscreen/PiP
  browser/
    BrowserViewModel         single source of UI state (screens, omnibox, prompts, find, chrome)
    TabManager               tab list, live-WebView budget, eviction, persistence, closed-tab stack
    BrowserTab               per-tab snapshot state
    WebViewFactory           builds WebViews, applies global + per-site policy (JS, UA, cookies, blocking)
    EddyWebViewClient/ChromeClient, EddyWebView (scroll + pull-to-refresh callbacks)
    Prompts                  permission coordinator, prompt/effect models
    BrowserScreen, Omnibox, MenuSheet, SiteInfoSheet, ErrorPage, ...   Compose UI
  home/  tabs/  bookmarks/  history/  downloads/  settings/           feature screens
  privacy/                   ContentBlocker, SitePermissions, FilterUpdateService
  data/                      models, DataStore settings, Room database
  ui/                        theme (palettes, tokens), shapes (morphing), components, animation
```

Browsing logic (tabs, WebView policy, blocking, downloads) is independent of Compose; the UI only
observes snapshot state and calls ViewModel functions. Anything needing an Activity (file picker,
runtime permissions, external apps, fullscreen video) leaves the ViewModel as a `UiEffect`.

### Notable behaviours

- **Tabs and memory.** At most four WebViews stay alive. Older tabs are serialised (full back/forward
  history) and rebuilt on demand; `onTrimMemory` drops all but the visible one. Tab state survives
  process death. Previews live in a byte-bounded LRU and on disk.
- **Incognito.** Uses a separate WebView *profile* (androidx.webkit multi-profile) whose cookies and
  storage are deleted when the last incognito tab closes. On WebViews without multi-profile support
  incognito still skips history/cache/thumbnails but shares cookies (Settings > Privacy says so).
- **Blocking.** Lists are parsed once and cached on disk, then matched on WebView's IO thread in
  `shouldInterceptRequest`. Domain rules (hosts files, domain lists, `||domain^`) block third-party
  requests only. URL rules with paths can also block a site's own ad scripts, and they honour `@@`
  exceptions, `$domain=` and `$third-party`. A built-in stylesheet hides common ad slots, and YouTube
  gets its own ad-stripping script. Per-site switch, custom lists, daily background refresh via
  `JobScheduler`.
- **Downloads.** Own resumable HTTP engine (Range/ETag) with a foreground service; finished files are
  published to `Downloads` through MediaStore. Pause/resume, retry, speed, notifications.
- **Welcome tour.** First launch shows a five-step tour with a hero shape that morphs as you swipe. Theme, colours, search engine and toolbar position apply to the real settings live. It is skipped when the app opens from a link, and Settings > About can replay it.
- **Passwords.** Sign-in and sign-up forms are detected by a script injected through `addWebMessageListener`; Eddy asks to save or update only after the sign-in succeeds. Passwords are AES-256-GCM encrypted with an Android Keystore key, autofill needs a tap and matches the exact origin, and the manager sits behind the device screen lock with `FLAG_SECURE`. Passwords import and export as Chrome-style CSV (`name,url,username,password,note`); export warns that the file is plain text.
- **Safety.** Certificate errors show a native page; proceeding requires an explicit confirmation and
  sub-resource errors are always cancelled. Background-triggered external-app launches are ignored.

## Known limitations

- WebView does not implement the Web Notifications API, so there is no per-site notification switch.
- `blob:` downloads and password autofill need a recent Android System WebView. On older ones Eddy says so and turns the password switches off.
- Element hiding uses one built-in stylesheet; `##` element-hiding rules in filter lists are ignored.
- The app is English only. Its text is in `res/values/strings.xml`, ready for translations.

## Privacy and legal

Eddy has no advertising, analytics or telemetry. The full text is in [PRIVACY.md](PRIVACY.md) and
[TERMS.md](TERMS.md); both mirror what the app shows under Settings > Legal.

## License

Licensed under the [Apache License 2.0](LICENSE).
