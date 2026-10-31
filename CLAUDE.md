# Fire Assistant

Android app (Kotlin, package `net.fireassistant`) that exposes a small HTTP API on a Fire tablet for Home Assistant. Sources are in `app/src/main/java/net/fireassistant/`.

## Workflow rules

- **Never push directly to `main`.** Every change goes through a pull request.
- **Build before merging.** If you can't build locally (e.g. no Android SDK), open the PR and wait for its CI build (`.github/workflows/ci.yml`: `./gradlew assembleDebug lintDebug`) to pass. Merge only once CI is green; if it fails, fix it on the PR branch first.
- Merging to `main` publishes a release automatically (`.github/workflows/release.yml`, tag `v1.0.<run number>`, which is also the versionCode). Changes to Markdown files only don't start a release.

## Updates

The in-app update button and `/api/update` share `Updater`. Both must call `GestureService.armInstall()` right before `Updater.install()` so the accessibility service taps Install automatically.
