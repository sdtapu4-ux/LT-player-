# LT PLAYER — Build & APK Guide

## 1. Build APK Automatically on GitHub
1. Push this repository to GitHub (via AI Studio **Export / Push to GitHub** or `git push`).
2. Open your GitHub repository and go to the **Actions** tab.
3. The **Build LT PLAYER APK** workflow (`.github/workflows/build-apk.yml`) runs automatically on pushes to `main`/`master` or manually via **Run workflow**.
4. Once finished, download **LT-PLAYER-debug-apk** from the workflow run's **Artifacts** section to get `app-debug.apk`.

### Optional: Signed Release APK & Android App Bundle (AAB) on GitHub
Add these three repository secrets in GitHub (**Settings → Secrets and variables → Actions**):
- `KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password for alias `upload`

When those secrets are present, GitHub Actions will also build and upload **LT-PLAYER-release-outputs** (`app-release.apk` and `app-release.aab`).

## 2. Local Build Commands
- **Debug APK**: `gradle :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **Unit & Robolectric Tests**: `gradle :app:testDebugUnitTest`
- **Release APK**: `gradle :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- **Release AAB**: `gradle :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
